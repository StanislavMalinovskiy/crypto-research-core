package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapRawOperationDecoderTest.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PumpSwapOperationPersistenceIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi api;
    @Autowired JdbcClient jdbc;
    @Autowired PumpSwapOperationCanonicalizer canonicalizer;
    @Autowired PublishPumpSwapOperationsUseCase publisher;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Test void publicationIsDurablyReadableWithCompleteDecodedContent() {
        var source = source(fixture("1", "buy", true, 1), "durable-red"); seed(source);
        var batch = new PumpSwapOperations.RecordedBatch(List.of(source), List.of("GAPS_UNKNOWN"));
        var expected = api.decodePumpSwapOperations(batch); var stored = api.persistPumpSwapOperations(batch);
        assertThat(api.persistedPumpSwapOperations(stored.batchKey()))
            .as("committed exact batch must remain addressable").isPresent();
        assertThat(api.persistedPumpSwapOperations(stored.batchKey()).orElseThrow().result()).isEqualTo(expected);
        assertThat(api.persistPumpSwapOperations(batch)).isEqualTo(stored);
    }
    @Test void inventedRawParentCannotPublishDerivations() {
        var source = source(fixture("1", "sell", true, 1), "missing-parent-red");
        assertThatThrownBy(() -> api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(source), List.of())))
            .as("missing retained raw source must reject publication").isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RAW");
    }
    @Test void allStatusesInvocationProofsAndExactIntegersSurviveWithoutOtherWrites() {
        var decoded=v2Fixture("buy_v2");var failed=fixture("1","buy",true,1);meta(failed).put("err",Map.of("InstructionError",List.of(0,"Custom")));
        var unsupported=fixture("2","buy",true,1);var invalid=fixture("1","buy",true,1);meta(invalid).remove("err");
        var empty=fixture("1","buy",true,0);var ancillary=ancillaryFixture("sweep_protocol_fee",false);
        var partial=fixture("1","buy",true,2);inner(partial,1).removeFirst();
        var sources=List.of(source(decoded,"status-decoded"),source(failed,"status-failed"),source(unsupported,"status-unsupported"),source(invalid,"status-invalid"),source(empty,"status-no-pump"),source(ancillary,"status-ancillary"),source(partial,"status-partial"));
        sources.forEach(this::seed);var before=unrelatedCounts();var batch=new PumpSwapOperations.RecordedBatch(sources,List.of("GAPS_UNKNOWN","FEES_UNKNOWN"));
        var expected=api.decodePumpSwapOperations(batch);
        assertThat(expected.transactions()).extracting(PumpSwapOperations.TransactionResult::status).containsExactlyInAnyOrder(PumpSwapOperations.Status.values());
        var stored=api.persistPumpSwapOperations(batch);assertThat(api.persistedPumpSwapOperations(stored.batchKey()).orElseThrow()).isEqualTo(stored);
        assertThat(stored.result()).isEqualTo(expected);assertThat(stored.transactions()).hasSize(7);
        assertThat(unrelatedCounts()).isEqualTo(before);
        var noMembers=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(),List.of("EMPTY")));
        assertThat(api.persistedPumpSwapOperations(noMembers.batchKey()).orElseThrow()).isEqualTo(noMembers);
        assertThat(api.persistedPumpSwapOperations("sha256:"+"0".repeat(64))).isEmpty();
    }
    @Test void everyImmutableRawFieldMismatchRejectsWithoutPartialPublication() {
        var s=source(fixture("1","buy",true,1),"raw-fields");seed(s);var before=derivedCounts();
        for(String field:List.of("payload","digest","slot","sourceTime","received","admitted","ingested","provider")) {
            String raw=field.equals("payload")?s.rawResult()+" ":s.rawResult();String digest=field.equals("digest")?"sha256:"+"a".repeat(64):hash(raw);
            var t=s.receivedAt().plusSeconds(1);
            var mismatch=new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),field.equals("provider")?"missing":s.provider(),field.equals("slot")?101:s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),field.equals("sourceTime")?Optional.of(t):s.sourceTime(),
                field.equals("received")?s.receivedAt().minusSeconds(1):s.receivedAt(),field.equals("admitted")?s.admittedAt().minusNanos(1000):s.admittedAt(),field.equals("ingested")?t:s.ingestedAt(),raw,digest);
            assertThatThrownBy(()->api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(mismatch),List.of()))).isInstanceOf(IllegalArgumentException.class);
            assertThat(derivedCounts()).isEqualTo(before);
        }
    }
    @Test void concurrentEqualPublicationKeepsOneCopyAndFirstTimes() throws Exception {
        var s=source(fixture("1","buy",true,1),"concurrent");seed(s);var batch=new PumpSwapOperations.RecordedBatch(List.of(s),List.of("CONCURRENT"));
        var before=derivedCounts();var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.StoredBatch> action=()->{start.await(5,java.util.concurrent.TimeUnit.SECONDS);return api.persistPumpSwapOperations(batch);};
            var a=executor.submit(action);var b=executor.submit(action);start.countDown();var stored=a.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(b.get(30,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(stored);assertThat(api.persistPumpSwapOperations(batch)).isEqualTo(stored);
            assertThat(derivedCounts()).containsEntry("pumpswap_transaction_derivations",before.get("pumpswap_transaction_derivations")+1).containsEntry("pumpswap_operation_derivations",before.get("pumpswap_operation_derivations")+1);
        }
    }
    @Test void completeContentConflictsEvenWhenSuppliedDigestsMatchAndOldVersionsRemain() {
        var s=source(fixture("1","buy",true,1),"conflict");seed(s);var result=api.decodePumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of("CONFLICT")));
        var p=canonicalizer.prepare(result);var stored=publisher.publish(p);var before=derivedCounts();var t=p.transactions().getFirst();
        var badTx=new PumpSwapOperationCanonicalizer.TransactionRow(t.key(),t.digest(),t.content().replace("DECODED","INVALID"),t.transaction(),t.operationKeys());
        var conflict=new PumpSwapOperationCanonicalizer.Prepared(p.key(),p.digest(),p.content(),p.result(),List.of(badTx),p.operations());
        assertThatThrownBy(()->publisher.publish(conflict)).hasMessageContaining("CONFLICT");assertThat(derivedCounts()).isEqualTo(before);
        var op=p.operations().getFirst();var badOp=new PumpSwapOperationCanonicalizer.OperationRow(op.canonicalKey(),op.key(),op.transactionKey(),op.digest(),op.content().replace("18446744073709551615","18446744073709551614"),op.operation());
        assertThatThrownBy(()->publisher.publish(new PumpSwapOperationCanonicalizer.Prepared(p.key(),p.digest(),p.content(),p.result(),p.transactions(),List.of(badOp)))).hasMessageContaining("CONFLICT");
        var future=new PumpSwapOperations.Result("synthetic-future",result.schemaVersion(),result.canonicalizationVersion(),result.pinnedSourceHashes(),result.transactions(),result.operations(),result.limitations(),result.fingerprint());
        var later=publisher.publish(canonicalizer.prepare(future));assertThat(later.operations().getFirst().canonicalKey()).isEqualTo(stored.operations().getFirst().canonicalKey());
        assertThat(later.operations().getFirst().revisionKey()).isNotEqualTo(stored.operations().getFirst().revisionKey());
        assertThat(api.persistedPumpSwapOperations(stored.batchKey()).orElseThrow()).isEqualTo(stored);assertThat(api.persistedPumpSwapOperations(later.batchKey()).orElseThrow()).isEqualTo(later);
    }
    @Test void sealedOperationMembershipRejectsOmissionAndNeverRepairsItOnRetry() {
        var s=source(fixture("1","buy",true,1),"sealed-omission");seed(s);var batch=new PumpSwapOperations.RecordedBatch(List.of(s),List.of("SEALED"));var stored=api.persistPumpSwapOperations(batch);
        jdbc.sql("delete from marketdata.pumpswap_operation_derivations where revision_key=:key").param("key",stored.operations().getFirst().revisionKey()).update();
        assertThatThrownBy(()->api.persistedPumpSwapOperations(stored.batchKey())).hasMessageContaining("CONFLICT");
        assertThatThrownBy(()->api.persistPumpSwapOperations(batch)).as("sealed existing transaction cannot acquire a missing operation on retry").hasMessageContaining("CONFLICT");
    }
    @Test void numericOrderedSecondChunkFailureRollsBackAllNewRows() {
        var s=source(fixture("1","buy",true,1),"late-chunk");seed(s);var pure=api.decodePumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of("CHUNK")));
        var ops=java.util.stream.IntStream.range(0,1001).mapToObj(i->PumpSwapOperationPersistenceTest.position(pure.operations().getFirst(),new PumpSwapOperations.Position(i,List.of(),0))).toList();
        var result=new PumpSwapOperations.Result("synthetic-chunk",pure.schemaVersion(),pure.canonicalizationVersion(),pure.pinnedSourceHashes(),pure.transactions(),ops,pure.limitations(),pure.fingerprint());
        var prepared=canonicalizer.prepare(result);var before=derivedCounts();
        jdbc.sql("create sequence marketdata.operation_attempt_seq").update();
        jdbc.sql("create function marketdata.fail_late_operation() returns trigger language plpgsql as 'begin perform nextval(''marketdata.operation_attempt_seq''); if NEW.outer_index=1000 then raise exception ''late chunk''; end if; return NEW; end'").update();
        jdbc.sql("create trigger operation_late_failure before insert on marketdata.pumpswap_operation_derivations for each row execute function marketdata.fail_late_operation()").update();
        try {
            assertThatThrownBy(()->publisher.publish(prepared)).hasStackTraceContaining("late chunk");
            assertThat(jdbc.sql("select last_value from marketdata.operation_attempt_seq").query(Long.class).single()).as("numeric position ordering reaches failure only after first 1000 operation writes").isEqualTo(1001);
            assertThat(derivedCounts()).isEqualTo(before);
        } finally {jdbc.sql("drop trigger operation_late_failure on marketdata.pumpswap_operation_derivations").update();jdbc.sql("drop function marketdata.fail_late_operation()").update();jdbc.sql("drop sequence marketdata.operation_attempt_seq").update();}
        var stored=publisher.publish(prepared);assertThat(stored.operations()).hasSize(1001);assertThat(api.persistedPumpSwapOperations(stored.batchKey()).orElseThrow()).isEqualTo(stored);
    }
    Map<String,Long> derivedCounts() {
        var result=new TreeMap<String,Long>();for(var table:List.of("pumpswap_transaction_derivations","pumpswap_operation_derivations","pumpswap_decode_batches","pumpswap_decode_batch_members")) result.put(table,jdbc.sql("select count(*) from marketdata."+table).query(Long.class).single());return result;
    }
    @Test void sameCanonicalPositionFromDifferentProviderPayloadsRetainsOriginalResultOrder() {
        var a=source(fixture("1","buy",true,1),"provider-order");var original=source(fixture("1","sell",true,1),"provider-order");
        var b=new PumpSwapOperations.RecordedTransaction(original.session(),original.chain(),original.signature(),"another-provider",original.slot(),original.finality(),original.captureDisposition(),original.sourceReference(),original.sourceTime(),original.receivedAt(),original.admittedAt(),original.ingestedAt(),original.rawResult(),original.rawDigest());
        seed(a);seed(b);var batch=new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("PROVIDER_ORDER"));var expected=api.decodePumpSwapOperations(batch);
        var stored=api.persistPumpSwapOperations(batch);
        assertThat(api.persistedPumpSwapOperations(stored.batchKey()).orElseThrow().result()).as("exact result order survives two source revisions of the same canonical position").isEqualTo(expected);
        assertThat(stored.operations()).extracting(io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.OperationRevision::canonicalKey).containsOnly(stored.operations().getFirst().canonicalKey());
    }
    @Test void transactionAndOperationBoundsRejectBeforePublishingAnyPrefix() {
        var sources=java.util.stream.IntStream.range(0,129).mapToObj(i->source(fixture("1","buy",true,1),"overflow-"+i)).toList();var before=derivedCounts();
        assertThatThrownBy(()->api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(sources,List.of()))).hasMessageContaining("LIMIT");
        var result=api.decodePumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(sources.getFirst()),List.of()));
        var excess=new PumpSwapOperations.Result(result.decoderVersion(),result.schemaVersion(),result.canonicalizationVersion(),result.pinnedSourceHashes(),result.transactions(),Collections.nCopies(10001,result.operations().getFirst()),result.limitations(),result.fingerprint());
        assertThatThrownBy(()->canonicalizer.prepare(excess)).hasMessageContaining("LIMIT");assertThat(derivedCounts()).isEqualTo(before);
    }
    @Test void sealedOperationMembershipRejectsAppendAndCompositeForeignKeyContradictions() {
        var s=source(fixture("1","buy",true,1),"sealed-append");seed(s);var batch=new PumpSwapOperations.RecordedBatch(List.of(s),List.of("APPEND"));
        var pure=api.decodePumpSwapOperations(batch);var p=canonicalizer.prepare(pure);var stored=publisher.publish(p);var original= pure.operations().getFirst();
        var extra=PumpSwapOperationPersistenceTest.position(original,new PumpSwapOperations.Position(0,List.of(),1));
        var changed=new PumpSwapOperations.Result(pure.decoderVersion(),pure.schemaVersion(),pure.canonicalizationVersion(),pure.pinnedSourceHashes(),pure.transactions(),List.of(original,extra),pure.limitations(),pure.fingerprint());
        var before=derivedCounts();assertThatThrownBy(()->publisher.publish(canonicalizer.prepare(changed))).hasMessageContaining("CONFLICT");assertThat(derivedCounts()).isEqualTo(before);
        var canonical=canonicalizer.operationKey(extra);var revision=canonicalizer.operationRevision(canonical,p.transactions().getFirst().key());var content=canonicalizer.encode(extra);
        var insert="insert into marketdata.pumpswap_operation_derivations(revision_key,canonical_key,transaction_revision_key,chain_id,transaction_value,outer_index,inner_path,leg_ordinal,content_digest,content) values(:revision,:canonical,:parent,:chain,:signature,0,'{}',1,:digest,:content)";
        assertThatThrownBy(()->jdbc.sql(insert).param("revision",revision).param("canonical",canonical).param("parent",p.transactions().getFirst().key()).param("chain",s.chain().value()).param("signature","wrong-parent").param("digest",canonicalizer.digest(content)).param("content",content).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.sql(insert).param("revision",revision).param("canonical",canonical).param("parent",p.transactions().getFirst().key()).param("chain",s.chain().value()).param("signature",s.signature()).param("digest",canonicalizer.digest(content)).param("content",content).update();
        assertThatThrownBy(()->api.persistedPumpSwapOperations(stored.batchKey())).hasMessageContaining("CONFLICT");
        assertThatThrownBy(()->api.persistPumpSwapOperations(batch)).hasMessageContaining("CONFLICT");
    }
    @Test void corruptBatchMembershipCannotReturnTruncatedSuccessAndDecodeCannotJoinAnAmbientTransaction() {
        var s=source(fixture("1","buy",true,1),"member-corruption");seed(s);var batch=new PumpSwapOperations.RecordedBatch(List.of(s),List.of("MEMBERS"));var stored=api.persistPumpSwapOperations(batch);
        jdbc.sql("delete from marketdata.pumpswap_decode_batch_members where batch_key=:key").param("key",stored.batchKey()).update();
        assertThatThrownBy(()->api.persistedPumpSwapOperations(stored.batchKey())).hasMessageContaining("CONFLICT");
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(()->transaction.execute(status->api.persistPumpSwapOperations(batch))).hasMessageContaining("DECODE_REQUIRES_NO_TRANSACTION");
        assertThat(io.cryptoresearch.marketdata.api.MarketDataApi.class.getDeclaredMethods()).filteredOn(m->m.getName().equals("persistPumpSwapOperations"))
            .allSatisfy(m->assertThat(m.getParameterTypes()).containsExactly(PumpSwapOperations.RecordedBatch.class));
    }
    Map<String,Long> unrelatedCounts() {
        var result=new TreeMap<String,Long>();for(var table:jdbc.sql("select schemaname||'.'||tablename from pg_tables where schemaname in('marketdata','signal','evaluation') and tablename not like 'pumpswap_%' order by schemaname,tablename").query(String.class).list())result.put(table,jdbc.sql("select count(*) from "+table).query(Long.class).single());return result;
    }
    @Test void operationReadOverflowRejectsBeforeAnyContentAggregationOrMaterialization() {
        var s=source(fixture("1","buy",true,1),"read-overflow-preflight");seed(s);
        var stored=api.persistPumpSwapOperations(new PumpSwapOperations.RecordedBatch(List.of(s),List.of("READ_BOUND")));
        var parent=stored.transactions().getFirst().revisionKey();
        jdbc.sql("""
            insert into marketdata.pumpswap_operation_derivations
            (revision_key,canonical_key,transaction_revision_key,chain_id,transaction_value,outer_index,inner_path,leg_ordinal,content_digest,content)
            select 'sha256:'||md5('overflow-'||i)||md5('overflow-'||i),'sha256:'||md5('canonical-'||i)||md5('canonical-'||i),
            :parent,:chain,:signature,i,'{}',0,'sha256:'||repeat('0',64),'{}' from generate_series(1,10001) i
            """).param("parent",parent).param("chain",s.chain().value()).param("signature",s.signature()).update();
        // A stable PostgreSQL projection guard detects content access without forcing unused columns.
        jdbc.sql("alter table marketdata.pumpswap_operation_derivations rename to monitor_overflow_operations").update();
        jdbc.sql("create function marketdata.reject_overflow_content(text) returns text language plpgsql stable as 'begin raise exception ''OVERFLOW_CONTENT_ACCESSED''; end'").update();
        jdbc.sql("""
            create view marketdata.pumpswap_operation_derivations as
            select revision_key,canonical_key,transaction_revision_key,chain_id,transaction_value,outer_index,inner_path,leg_ordinal,
            content_digest,marketdata.reject_overflow_content(content) as content,persisted_at from marketdata.monitor_overflow_operations
            """).update();
        try {
            assertThatThrownBy(()->api.persistedPumpSwapOperations(stored.batchKey()))
                .as("10001-row overflow preflight must reject before reading or aggregating operation content")
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PERSISTENCE_OUTPUT_LIMIT");
        } finally {
            jdbc.sql("drop view marketdata.pumpswap_operation_derivations").update();
            jdbc.sql("drop function marketdata.reject_overflow_content(text)").update();
            jdbc.sql("alter table marketdata.monitor_overflow_operations rename to pumpswap_operation_derivations").update();
            jdbc.sql("delete from marketdata.pumpswap_operation_derivations where transaction_revision_key=:parent and outer_index>0").param("parent",parent).update();
        }
        assertThat(api.persistedPumpSwapOperations(stored.batchKey()).orElseThrow()).isEqualTo(stored);
    }
    void seed(PumpSwapOperations.RecordedTransaction s) {
        jdbc.sql("""
            insert into marketdata.raw_transactions(chain_id,transaction_value,provider,payload,payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at)
            values(:chain,:signature,:provider,:payload,:digest,:slot,:sourceTime,:received,:admitted,:ingested) on conflict do nothing
            """).param("chain",s.chain().value()).param("signature",s.signature()).param("provider",s.provider())
            .param("payload",s.rawResult()).param("digest",s.rawDigest()).param("slot",s.slot())
            .param("sourceTime",s.sourceTime().map(t -> t.atOffset(java.time.ZoneOffset.UTC)).orElse(null),java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
            .param("received",s.receivedAt().atOffset(java.time.ZoneOffset.UTC)).param("admitted",s.admittedAt().atOffset(java.time.ZoneOffset.UTC))
            .param("ingested",s.ingestedAt().atOffset(java.time.ZoneOffset.UTC)).update();
    }
}
