package io.cryptoresearch.marketdata.infrastructure.persistence;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.cryptoresearch.marketdata.application.PumpSwapOperationStore;
import io.cryptoresearch.marketdata.application.PumpSwapOperationCanonicalizer;
import io.cryptoresearch.marketdata.application.PumpSwapOperationCanonicalizer.*;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.*;

/** Insert-first immutable publication; repositories never start business transactions. */
@Repository
public class JdbcPumpSwapOperationStore implements PumpSwapOperationStore {
    private final JdbcTemplate jdbc;
    private final PumpSwapOperationCanonicalizer canonicalizer;
    public JdbcPumpSwapOperationStore(DataSource dataSource, PumpSwapOperationCanonicalizer canonicalizer) {
        jdbc = new JdbcTemplate(dataSource); jdbc.setQueryTimeout(10); this.canonicalizer = canonicalizer;
    }
    private record RawKey(String chain, String signature, String provider) { }
    private record Raw(String payload, String digest, long slot, Optional<Instant> sourceTime, Instant received, Instant admitted, Instant ingested) { }
    private record TransactionStored(String key, String chain, String signature, String provider, String rawDigest,
            String digest, String content, int count, Instant persistedAt, String raw) { }
    private record OperationStored(String key, String canonicalKey, String parent, String chain, String signature,
            int outer, List<Integer> path, int leg, String digest, String content, Instant persistedAt) { }
    private record BatchStored(String key, String digest, String content, int transactions, int operations, Instant persistedAt) { }

    public void verifyRaw(Prepared prepared) {
        if (prepared.transactions().size() > 128) throw new IllegalArgumentException("PERSISTENCE_LIMIT");
        var sources = prepared.transactions().stream().map(t -> t.transaction().source()).toList();
        if (sources.isEmpty()) return;
        var args = new ArrayList<Object>();
        for (var s : sources) { args.add(s.chain().value()); args.add(s.signature()); args.add(s.provider()); }
        var sql = """
            select chain_id,transaction_value,provider,
            case when octet_length(payload)<=8000000 and sum(octet_length(payload)) over()<=64000000 then payload else null end as bounded_payload,
            payload_hash,observed_block_position,source_event_time,received_at,admitted_at,ingested_at
            from marketdata.raw_transactions where (chain_id,transaction_value,provider) in (values
            """ + String.join(",", Collections.nCopies(sources.size(), "(?,?,?)")) + ") order by chain_id,transaction_value,provider limit 129";
        var rows = jdbc.query(sql, (rs, n) -> Map.entry(new RawKey(rs.getString("chain_id"),rs.getString("transaction_value"),rs.getString("provider")),
            new Raw(rs.getString("bounded_payload"),rs.getString("payload_hash"),rs.getLong("observed_block_position"),Optional.ofNullable(time(rs,"source_event_time")),
                time(rs,"received_at"),time(rs,"admitted_at"),time(rs,"ingested_at"))), args.toArray());
        var byKey = new HashMap<RawKey,Raw>(); rows.forEach(e -> byKey.put(e.getKey(),e.getValue()));
        for (var s : sources) {
            var raw = byKey.get(new RawKey(s.chain().value(),s.signature(),s.provider()));
            if (raw == null || !Objects.equals(raw.payload(),s.rawResult()) || !raw.digest().equals(s.rawDigest()) || raw.slot()!=s.slot()
                || !raw.sourceTime().equals(s.sourceTime()) || !raw.received().equals(s.receivedAt()) || !raw.admitted().equals(s.admittedAt()) || !raw.ingested().equals(s.ingestedAt()))
                throw new IllegalArgumentException("RAW_LINEAGE_MISSING_OR_CONFLICTING");
            if (!io.cryptoresearch.marketdata.infrastructure.PumpSwapRawOperationParser.sha256(raw.payload().getBytes(StandardCharsets.UTF_8)).equals(raw.digest()))
                throw new IllegalArgumentException("RAW_DIGEST_CONFLICT");
        }
    }
    public StoredBatch publish(Prepared p) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("PUBLICATION_REQUIRES_TRANSACTION");
        jdbc.execute("set local statement_timeout='10s'"); jdbc.execute("set local lock_timeout='10s'");
        var inserted = batch("""
            insert into marketdata.pumpswap_transaction_derivations(revision_key,chain_id,transaction_value,provider,raw_digest,content_digest,content,operation_count)
            values(?,?,?,?,?,?,?,?) on conflict do nothing
            """, p.transactions().stream().map(t -> new Object[]{t.key(),t.transaction().source().chain().value(),t.transaction().source().signature(),t.transaction().source().provider(),t.transaction().source().rawDigest(),t.digest(),t.content(),t.operationKeys().size()}).toList());
        var txRows = transactions(p.transactions().stream().map(TransactionRow::key).toList(), false);
        verifyTransactions(p,txRows);
        var existingKeys = new HashSet<String>();
        for (int i=0;i<inserted.size();i++) {
            if(inserted.get(i)==0)existingKeys.add(p.transactions().get(i).key());
            else if(inserted.get(i)!=1)throw new IllegalStateException("UNRESOLVED_TRANSACTION_INSERT");
        }
        verifyOperations(p.operations().stream().filter(o->existingKeys.contains(o.transactionKey())).toList(),
            operations(p.transactions().stream().map(TransactionRow::key).filter(existingKeys::contains).toList()));
        var parentOrdinals = new HashMap<String,Integer>();
        for (int i=0;i<p.transactions().size();i++)parentOrdinals.put(p.transactions().get(i).key(),i);
        batch("""
            insert into marketdata.pumpswap_operation_derivations(revision_key,canonical_key,transaction_revision_key,chain_id,transaction_value,outer_index,inner_path,leg_ordinal,content_digest,content)
            values(?,?,?,?,?,?,?,?,?,?) on conflict do nothing
            """, p.operations().stream().sorted(Comparator.comparingInt((OperationRow o)->parentOrdinals.get(o.transactionKey()))
                .thenComparing(OperationRow::operation,PumpSwapOperationCanonicalizer.operationOrder()).thenComparing(OperationRow::key))
                .map(o -> new Object[]{o.key(),o.canonicalKey(),o.transactionKey(),o.operation().chain().value(),o.operation().signature(),o.operation().position().outerIndex(),
                    o.operation().position().innerPath().toArray(Integer[]::new),o.operation().position().legOrdinal(),o.digest(),o.content()}).toList());
        var opRows = operations(p.transactions().stream().map(TransactionRow::key).toList()); verifyOperations(p,opRows);
        jdbc.update("insert into marketdata.pumpswap_decode_batches(batch_key,content_digest,content,transaction_count,operation_count) values(?,?,?,?,?) on conflict do nothing",
            p.key(),p.digest(),p.content(),p.transactions().size(),p.operations().size());
        batch("insert into marketdata.pumpswap_decode_batch_members(batch_key,ordinal,transaction_revision_key) values(?,?,?) on conflict do nothing",
            java.util.stream.IntStream.range(0,p.transactions().size()).mapToObj(i -> new Object[]{p.key(),i,p.transactions().get(i).key()}).toList());
        var header = header(p.key()).orElseThrow(); verifyHeader(p,header); verifyMembers(p);
        return wrapper(p,header,txRows,opRows);
    }
    private List<Integer> batch(String sql,List<Object[]> rows) {
        var counts=new ArrayList<Integer>();
        for (int start=0;start<rows.size();start+=1000)for(int count:jdbc.batchUpdate(sql,rows.subList(start,Math.min(start+1000,rows.size()))))counts.add(count);
        return counts;
    }
    private String marks(int size) { return String.join(",",Collections.nCopies(size,"?")); }
    private Optional<BatchStored> header(String key) {
        return jdbc.query("select * from marketdata.pumpswap_decode_batches where batch_key=?", (rs,n) -> new BatchStored(rs.getString("batch_key"),rs.getString("content_digest"),rs.getString("content"),rs.getInt("transaction_count"),rs.getInt("operation_count"),time(rs,"persisted_at")),key).stream().findFirst();
    }
    private List<TransactionStored> transactions(List<String> keys,boolean raw) {
        if(keys.isEmpty()) return List.of();
        return jdbc.query("select t.*,"+(raw?"r.payload":"null::text")+" as raw from marketdata.pumpswap_transaction_derivations t "+
            (raw?"join marketdata.raw_transactions r using(chain_id,transaction_value,provider) ":"")+"where revision_key in ("+marks(keys.size())+") order by revision_key limit 129",
            (rs,n)->new TransactionStored(rs.getString("revision_key"),rs.getString("chain_id"),rs.getString("transaction_value"),rs.getString("provider"),rs.getString("raw_digest"),rs.getString("content_digest"),rs.getString("content"),rs.getInt("operation_count"),time(rs,"persisted_at"),rs.getString("raw")),keys.toArray());
    }
    private List<OperationStored> operations(List<String> keys) {
        if(keys.isEmpty())return List.of();
        return jdbc.query("select * from marketdata.pumpswap_operation_derivations where transaction_revision_key in ("+marks(keys.size())+") order by transaction_revision_key,outer_index,inner_path,leg_ordinal,revision_key limit 10001",
            (rs,n)->new OperationStored(rs.getString("revision_key"),rs.getString("canonical_key"),rs.getString("transaction_revision_key"),rs.getString("chain_id"),rs.getString("transaction_value"),rs.getInt("outer_index"),Arrays.asList((Integer[])rs.getArray("inner_path").getArray()),rs.getInt("leg_ordinal"),rs.getString("content_digest"),rs.getString("content"),time(rs,"persisted_at")),keys.toArray());
    }
    private void verifyTransactions(Prepared p,List<TransactionStored> rows) {
        if(rows.size()!=p.transactions().size())conflict();
        var byKey=new HashMap<String,TransactionStored>();rows.forEach(r->byKey.put(r.key(),r));
        for(var tx:p.transactions()) {
            var r=byKey.get(tx.key());var s=tx.transaction().source();
            if(r==null||!r.chain().equals(s.chain().value())||!r.signature().equals(s.signature())||!r.provider().equals(s.provider())||!r.rawDigest().equals(s.rawDigest())
                ||!r.digest().equals(tx.digest())||!r.content().equals(tx.content())||r.count()!=tx.operationKeys().size())conflict();
        }
    }
    private void verifyOperations(Prepared p,List<OperationStored> rows) {
        verifyOperations(p.operations(),rows);
    }
    private void verifyOperations(List<OperationRow> expected,List<OperationStored> rows) {
        if(rows.size()!=expected.size())conflict();
        var byKey=new HashMap<String,OperationStored>();rows.forEach(r->byKey.put(r.key(),r));
        for(var op:expected) {
            var r=byKey.get(op.key());var o=op.operation();
            if(r==null||!r.canonicalKey().equals(op.canonicalKey())||!r.parent().equals(op.transactionKey())||!r.chain().equals(o.chain().value())||!r.signature().equals(o.signature())
                ||r.outer()!=o.position().outerIndex()||!r.path().equals(o.position().innerPath())||r.leg()!=o.position().legOrdinal()||!r.digest().equals(op.digest())||!r.content().equals(op.content()))conflict();
        }
    }
    private void verifyHeader(Prepared p,BatchStored r) {
        if(!r.key().equals(p.key())||!r.digest().equals(p.digest())||!r.content().equals(p.content())||r.transactions()!=p.transactions().size()||r.operations()!=p.operations().size())conflict();
    }
    private List<String> members(String key) {
        var entries=jdbc.query("select ordinal,transaction_revision_key from marketdata.pumpswap_decode_batch_members where batch_key=? order by ordinal limit 129",(rs,n)->Map.entry(rs.getInt(1),rs.getString(2)),key);
        for(int i=0;i<entries.size();i++)if(entries.get(i).getKey()!=i)conflict();
        return entries.stream().map(Map.Entry::getValue).toList();
    }
    private void verifyMembers(Prepared p) { if(!members(p.key()).equals(p.transactions().stream().map(TransactionRow::key).toList()))conflict(); }
    private StoredBatch wrapper(Prepared p,BatchStored header,List<TransactionStored> txRows,List<OperationStored> opRows) {
        var txByKey=new HashMap<String,TransactionStored>();txRows.forEach(r->txByKey.put(r.key(),r));
        var opByKey=new HashMap<String,OperationStored>();opRows.forEach(r->opByKey.put(r.key(),r));
        return new StoredBatch(PumpSwapOperationCanonicalizer.STORAGE_VERSION,p.key(),header.persistedAt(),p.result(),
            p.transactions().stream().map(t->new TransactionRevision(t.key(),t.digest(),txByKey.get(t.key()).persistedAt(),t.operationKeys())).toList(),
            p.operations().stream().map(o->new OperationRevision(o.canonicalKey(),o.key(),o.transactionKey(),o.digest(),opByKey.get(o.key()).persistedAt())).toList());
    }
    public Optional<StoredBatch> find(String key) {
        var found=header(key);if(found.isEmpty())return Optional.empty();var header=found.get();var keys=members(key);
        if(keys.size()>128||keys.size()!=header.transactions())conflict();
        if(!keys.isEmpty()) {
            // Detect overflow using identities only, before evaluating any operation content.
            var operationCount=jdbc.queryForObject("select count(*) from (select 1 from marketdata.pumpswap_operation_derivations where transaction_revision_key in ("+marks(keys.size())+") limit 10001) bounded",Long.class,keys.toArray());
            if(operationCount>10000)throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");
            var bytes=jdbc.queryForObject("select coalesce(sum(octet_length(t.content)),0) from marketdata.pumpswap_transaction_derivations t where revision_key in ("+marks(keys.size())+")",Long.class,keys.toArray());
            var rawSizes=jdbc.queryForMap("select coalesce(sum(octet_length(r.payload)),0) as total, coalesce(max(octet_length(r.payload)),0) as maximum from marketdata.pumpswap_transaction_derivations t join marketdata.raw_transactions r using(chain_id,transaction_value,provider) where revision_key in ("+marks(keys.size())+")",keys.toArray());
            var opBytes=jdbc.queryForObject("select coalesce(sum(octet_length(content)),0) from (select content from marketdata.pumpswap_operation_derivations where transaction_revision_key in ("+marks(keys.size())+") limit 10001) bounded",Long.class,keys.toArray());
            if(bytes+opBytes+header.content().getBytes(StandardCharsets.UTF_8).length>PumpSwapOperationCanonicalizer.MAX_BYTES
                ||((Number)rawSizes.get("total")).longValue()>PumpSwapOperationCanonicalizer.MAX_BYTES||((Number)rawSizes.get("maximum")).longValue()>8_000_000)throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");
        }
        var txRows=transactions(keys,true);var opRows=operations(keys);
        if(opRows.size()>10000||opRows.size()!=header.operations()||txRows.size()!=keys.size())conflict();
        var byKey=new HashMap<String,TransactionStored>();txRows.forEach(r->byKey.put(r.key(),r));
        var txs=keys.stream().map(k->{var r=byKey.get(k);if(r==null)throw new IllegalArgumentException("DERIVATION_IMMUTABLE_CONFLICT");return canonicalizer.transaction(r.content(),r.raw());}).toList();
        var opByKey=new HashMap<String,OperationStored>();opRows.forEach(r->opByKey.put(r.key(),r));
        var manifestKeys=canonicalizer.operationKeys(header.content());
        if(manifestKeys.size()!=opRows.size())conflict();
        var ops=manifestKeys.stream().map(k->{var row=opByKey.get(k);if(row==null)throw new IllegalArgumentException("DERIVATION_IMMUTABLE_CONFLICT");return canonicalizer.decode(row.content(),PumpSwapOperations.Operation.class);}).toList();
        var result=canonicalizer.result(header.content(),txs,ops);var prepared=canonicalizer.prepare(result);
        verifyRaw(prepared);verifyHeader(prepared,header);verifyMembers(prepared);verifyTransactions(prepared,txRows);verifyOperations(prepared,opRows);
        return Optional.of(wrapper(prepared,header,txRows,opRows));
    }
    private static Instant time(ResultSet rs,String column) throws SQLException { var value=rs.getObject(column,OffsetDateTime.class);return value==null?null:value.toInstant(); }
    private static void conflict() { throw new IllegalArgumentException("DERIVATION_IMMUTABLE_CONFLICT"); }
}
