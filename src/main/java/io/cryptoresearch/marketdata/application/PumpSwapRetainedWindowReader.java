package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.api.PumpSwapRetainedWindow.*;
import io.cryptoresearch.marketdata.api.PumpSwapCapture;
import io.cryptoresearch.marketdata.api.PumpSwapOperations.*;
import io.cryptoresearch.marketdata.api.PumpSwapPoolLegs;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcPumpSwapRetainedWindowStore;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcPumpSwapRetainedWindowStore.*;
import tools.jackson.databind.json.JsonMapper;

/** Application-owned snapshot; materialization finishes before any decoding. */
@Service
public class PumpSwapRetainedWindowReader {
    private static final JsonMapper JSON=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final List<String> LIMITATIONS=List.of("DB_VISIBLE_RECEIVED_SCOPE_ONLY","RUNNING_PREFIX_NOT_SEALED","CAPTURE_COVERAGE_UNKNOWN","DIAGNOSTIC_ONLY_NOT_ADMITTED");
    private final JdbcPumpSwapRetainedWindowStore store;
    private final CaptureLedgerUseCase capture;
    public PumpSwapRetainedWindowReader(JdbcPumpSwapRetainedWindowStore store,CaptureLedgerUseCase capture){this.store=store;this.capture=capture;}
    private static final class Progress {
        Observation signatures=Observation.unknown(),bytes=Observation.unknown();long total;
        final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        void time(){if(System.nanoTime()>=deadline)throw failure("RETAINED_READ_FAILED_OR_TIMEOUT");}
        Diagnostic diagnostic(){return new Diagnostic(signatures,bytes);}
        ReadFailure failure(String reason){return new ReadFailure(reason,diagnostic());}
        void add(long n){if(n<0)throw failure("RETAINED_SOURCE_MISSING");total=Math.addExact(total,n);bytes=Observation.atLeast(total);if(n>8000000||total>64000000)throw failure("RETAINED_BYTE_LIMIT");}
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,readOnly=true,timeout=10)
    public Snapshot read(Request request){
        var p=new Progress();
        try{return materialize(request,p);}
        catch(ReadFailure failure){throw failure;}
        catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException failure){throw p.failure("RETAINED_READ_FAILED_OR_TIMEOUT");}
        catch(RuntimeException invalid){throw p.failure(invalid.getMessage()!=null&&invalid.getMessage().matches("[A-Z][A-Z0-9_]{1,80}")?invalid.getMessage():"RETAINED_SOURCE_CONFLICT");}
    }
    private Snapshot materialize(Request q,Progress progress){
        var header=store.header(q.session());var r=header.observedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(header.bytes()>1000000)throw progress.failure("RETAINED_CONFIGURATION_LIMIT");
        var text=store.configuration(q.session());if(!new CanonicalFingerprint().field("contract","pumpswap-capture-configuration-v1").field("configuration",text).finish().equals(header.digest()))throw progress.failure("RETAINED_CONFIGURATION_CONFLICT");
        var c=JSON.readValue(text,PumpSwapCapture.Configuration.class);
        if(!c.sessionId().equals(q.session())||c.pools().isEmpty()||c.pools().size()>3)throw progress.failure("RETAINED_CONFIGURATION_CONFLICT");
        var addresses=new HashSet<String>();var pools=new ArrayList<PumpSwapPoolLegs.PoolBinding>();
        for(var b:c.pools()){
            if(!addresses.add(b.address()))throw progress.failure("RETAINED_CONFIGURATION_CONFLICT");
            CaptureBudget.address(b.address());CaptureBudget.address(b.baseVault());CaptureBudget.address(b.quoteVault());CaptureBudget.address(b.baseMint());CaptureBudget.address(b.quoteMint());CaptureBudget.address(b.baseTokenProgram());CaptureBudget.address(b.quoteTokenProgram());
            if(!PayloadFingerprint.sha256(b.proof().payload()).equals(b.proof().digest()))throw progress.failure("RETAINED_CONFIGURATION_CONFLICT");
            var proof=JSON.readTree(b.proof().payload());
            var fields=Map.of("chain",ChainId.SOLANA_MAINNET.value(),"pool",b.address(),"program",b.program(),"baseVault",b.baseVault(),"quoteVault",b.quoteVault(),"baseMint",b.baseMint(),"quoteMint",b.quoteMint(),"baseTokenProgram",b.baseTokenProgram(),"quoteTokenProgram",b.quoteTokenProgram());
            if(fields.entrySet().stream().anyMatch(e->!e.getValue().equals(proof.path(e.getKey()).asText()))||!proof.path("contextSlot").isIntegralNumber()||!proof.path("contextSlot").bigIntegerValue().equals(java.math.BigInteger.valueOf(b.proof().contextSlot()))||b.proof().contextSlot()<0||!"finalized".equals(b.proof().commitment())||!"finalized".equals(proof.path("commitment").asText()))throw progress.failure("RETAINED_CONFIGURATION_CONFLICT");
            pools.add(new PumpSwapPoolLegs.PoolBinding(b.address(),b.baseVault(),b.quoteVault(),b.baseMint(),b.quoteMint(),b.baseTokenProgram(),b.quoteTokenProgram()));
        }
        pools.sort(Comparator.comparing(PumpSwapPoolLegs.PoolBinding::pool));
        progress.time();var signatures=store.signatures(q);
        progress.signatures=signatures.size()>4096?Observation.atLeast(signatures.size()):Observation.exact(signatures.size());
        if(signatures.size()>4096)throw progress.failure("RETAINED_SIGNATURE_LIMIT");
        var metadata=store.metadata(q.session(),c.provider(),signatures);if(metadata.size()>12288)throw progress.failure("RETAINED_BINDING_LIMIT");
        var bySignature=new HashMap<String,List<Meta>>();var countedRaw=new HashSet<String>();
        for(var m:metadata){
            if(!m.fits())throw progress.failure("RETAINED_METADATA_LIMIT");
            CaptureBudget.signature(m.signature());if(!addresses.contains(m.pool())||m.status().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>131072)throw progress.failure("RETAINED_BINDING_CONFLICT");
            bySignature.computeIfAbsent(m.signature(),unused->new ArrayList<>()).add(m);
            progress.add(m.notificationBytes());progress.add(m.responseBytes());if(m.digest()!=null&&countedRaw.add(m.signature()))progress.add(m.rawBytes());
        }
        if(bySignature.size()!=signatures.size()||bySignature.values().stream().anyMatch(v->v.size()>3))throw progress.failure("RETAINED_BINDING_CONFLICT");
        var gaps=store.gaps(q.session());if(gaps.size()>1000||gaps.stream().anyMatch(g->g.pool()==null||g.kind()==null||g.reason()==null||g.status()==null))throw progress.failure("RETAINED_GAP_LIMIT");
        var allBindings=new ArrayList<Binding>();var transactions=new ArrayList<RecordedTransaction>();
        var retainedProofs=new TreeMap<List<String>,PumpSwapCapture.Envelope>(Comparator.<List<String>,String>comparing(k->k.get(0)).thenComparing(k->k.get(1)));
        for(int first=0;first<signatures.size();first+=128){
            progress.time();
            var page=signatures.subList(first,Math.min(first+128,signatures.size()));var bindings=store.bindings(q.session(),page);
            if(bindings.size()>384||bindings.size()!=page.stream().mapToInt(s->bySignature.get(s).size()).sum())throw progress.failure("RETAINED_PAGE_INCOMPLETE");
            var refs=new TreeSet<String>();for(var b:bindings){var n=JSON.readTree(b.notification());if(n.has("pageDigest"))refs.add(n.path("pageDigest").asText());if(!b.response().isEmpty()){var response=JSON.readTree(b.response());if(response.has("reference"))refs.add(response.path("reference").path("pageDigest").asText());}}
            var proofMeta=store.proofMetadata(q.session(),refs);if(proofMeta.size()>768)throw progress.failure("RETAINED_PROOF_LIMIT");
            var needed=new ArrayList<ProofMeta>();for(var m:proofMeta)if(!retainedProofs.containsKey(List.of(m.kind(),m.digest()))){progress.add(m.bytes());needed.add(m);}
            if(retainedProofs.size()+needed.size()>24576)throw progress.failure("RETAINED_PROOF_LIMIT");
            var hydrated=store.proofs(q.session(),needed);if(hydrated.size()!=needed.size())throw progress.failure("RETAINED_PAGE_INCOMPLETE");
            for(var proof:hydrated){progress.time();if(!PayloadFingerprint.sha256(proof.payload()).equals(proof.digest())||proof.receivedAt().isAfter(r))throw progress.failure("RETAINED_PROOF_CONFLICT");retainedProofs.put(List.of(proof.kind(),proof.digest()),proof);}
            var rawKeys=page.stream().filter(s->bySignature.get(s).stream().anyMatch(m->m.digest()!=null)).toList();var raw=store.raw(c.provider(),rawKeys);
            if(raw.size()!=rawKeys.size()||raw.size()>128)throw progress.failure("RETAINED_PAGE_INCOMPLETE");
            for(var row:raw){
                progress.time();
                var bs=bindings.stream().filter(b->b.signature().equals(row.signature())).toList();var n=JSON.readTree(row.payload());
                if(!PayloadFingerprint.sha256(row.payload()).equals(row.digest())||!"pumpswap-capture-v1".equals(row.parser())||row.blockHash()!=null
                    ||!n.path("slot").isIntegralNumber()||n.path("slot").longValue()!=row.slot()||!row.signature().equals(n.path("transaction").path("signatures").path(0).asText())
                    ||!n.path("meta").has("err")||row.received().isAfter(row.admitted())||row.admitted().isAfter(row.ingested())||row.ingested().isAfter(r))throw progress.failure("RETAINED_RAW_CONFLICT");
                var source=n.path("blockTime").isIntegralNumber()?Instant.ofEpochSecond(n.path("blockTime").longValue()):null;if(!Objects.equals(source,row.source()))throw progress.failure("RETAINED_RAW_CONFLICT");
                String status=n.path("meta").path("err").isNull()?"CAPTURED":"FAILED_TRANSACTION";boolean anchor=false;
                var identity=scope(q,new CanonicalFingerprint().field("version","retained-window-source-v1").field("configuration",header.digest()).field("provider",c.provider()).field("signature",row.signature()));
                for(var b:bs){
                    if(b.digest()!=null){
                        if(!status.equals(b.status())||!row.digest().equals(b.digest())||row.slot()!=b.slot()||b.admitted()==null||b.received().isAfter(b.admitted())||b.admitted().isAfter(r))throw progress.failure("RETAINED_BINDING_CONFLICT");
                        anchor|=verifyBinding(c,b,n,row,r,retainedProofs.values());
                    }
                    identity.field("pool",b.pool()).field("status",b.status()).field("slot",Long.toString(b.slot())).field("notification",PayloadFingerprint.sha256(b.notification())).field("response",responseDigest(b.response())).field("received",b.received().toString()).field("admitted",b.admitted()==null?"ABSENT":b.admitted().toString());
                }
                if(!anchor)throw progress.failure("RETAINED_PROOF_MISSING");
                transactions.add(new RecordedTransaction(q.session(),ChainId.SOLANA_MAINNET,row.signature(),c.provider(),row.slot(),"finalized",status,identity.finish(),Optional.ofNullable(row.source()),row.received(),row.admitted(),row.ingested(),row.payload(),row.digest()));
            }
            for(var b:bindings)if(b.digest()==null){if(b.status().equals("CAPTURED")||b.status().equals("FAILED_TRANSACTION")||b.received().isAfter(r)||b.admitted()!=null&&b.admitted().isAfter(r))throw progress.failure("RETAINED_BINDING_CONFLICT");}
            allBindings.addAll(bindings);
        }
        progress.bytes=Observation.exact(progress.total);
        var dispositions=allBindings.stream().map(b->new Disposition(b.signature(),b.pool(),b.slot(),b.status(),Optional.ofNullable(b.digest()),PayloadFingerprint.sha256(b.notification()),responseDigest(b.response()),b.received(),Optional.ofNullable(b.admitted()),b.duplicates(),b.conflicts())).toList();
        var proofs=retainedProofs.values().stream().map(e->new Proof(e.kind(),e.digest(),e.receivedAt())).toList();var statuses=new TreeMap<String,Long>();for(var d:dispositions)statuses.merge(d.status(),1L,Math::addExact);
        var content=new Object[]{q,header.digest(),r,transactions,pools,dispositions,proofs,gaps,statuses,signatures.size(),progress.total,LIMITATIONS};
        var canonical=canonicalDigest(content,progress);
        var fingerprint=scope(q,new CanonicalFingerprint().field("version","retained-window-source-v1").field("contentDigest",canonical)).finish();
        var manifest=new Manifest(dispositions,proofs,gaps,statuses,signatures.size(),progress.total,fingerprint,LIMITATIONS);
        var result=new Snapshot("pumpswap-retained-window-v1",q,header.digest(),r,new RecordedBatch(transactions,LIMITATIONS),pools,manifest,fingerprint);
        canonicalDigest(result,progress);progress.time();return result;
    }
    private static String canonicalDigest(Object value,Progress progress){
        try{
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            var stream=new java.io.OutputStream(){long bytes;private void admit(int count){progress.time();bytes=Math.addExact(bytes,count);if(bytes>64000000)throw progress.failure("RETAINED_OUTPUT_LIMIT");}public void write(int b){admit(1);digest.update((byte)b);}public void write(byte[] b,int offset,int count){admit(count);digest.update(b,offset,count);}};
            try{JSON.writeValue(stream,value);}catch(RuntimeException failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof ReadFailure bound)throw bound;throw failure;}
            return "sha256:"+java.util.HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA_256_UNAVAILABLE");}
    }
    private static String responseDigest(String text){
        // Empty response is an original non-raw disposition fact, not an empty raw payload.
        try{return "sha256:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(text).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA_256_UNAVAILABLE");}
    }
    private static CanonicalFingerprint scope(Request q,CanonicalFingerprint f){return f.field("session",q.session().toString()).field("receivedFrom",q.receivedFromInclusive().toString()).field("receivedTo",q.receivedToExclusive().toString()).field("eventFrom",q.eventFromInclusive().toString()).field("eventTo",q.eventToExclusive().toString());}
    private boolean verifyBinding(PumpSwapCapture.Configuration c,Binding b,tools.jackson.databind.JsonNode raw,Raw row,Instant r,Collection<PumpSwapCapture.Envelope> proofs){
        var notification=JSON.readTree(b.notification());
        if(notification.has("pageDigest"))history(c,b,b.notification(),proofs);
        else if(!b.signature().equals(notification.path("params").path("result").path("value").path("signature").asText())||!notification.path("params").path("result").path("context").path("slot").isIntegralNumber()||notification.path("params").path("result").path("context").path("slot").longValue()!=b.slot())throw new IllegalStateException("RETAINED_NOTIFICATION_CONFLICT");
        var response=JSON.readTree(b.response());
        if("2.0".equals(response.path("jsonrpc").asText())&&response.path("id").isIntegralNumber()&&!response.has("error")&&response.path("result").equals(raw))return true;
        if(!"capture-transaction-source-v2".equals(response.path("version").asText())||!c.version().equals(response.path("sourcePolicy").asText())||!c.provider().equals(response.path("provider").asText())||!c.sessionId().toString().equals(response.path("session").asText())||!b.pool().equals(response.path("pool").asText())||!b.signature().equals(response.path("signature").asText())||!response.path("slot").isIntegralNumber()||response.path("slot").longValue()!=b.slot()||!row.digest().equals(response.path("rawDigest").asText())||!"REUSED".equals(response.path("result").asText()))throw new IllegalStateException("RETAINED_PROOF_CONFLICT");
        if("COMMITTED_CAPTURE_RAW".equals(response.path("method").asText()))return false;
        if(!"getTransactionsForAddress".equals(response.path("method").asText()))throw new IllegalStateException("RETAINED_PROOF_CONFLICT");
        var page=history(c,b,response.path("reference").toString(),proofs);var index=response.path("reference").path("rowIndex").intValue();
        var historyRow=JSON.readTree(page.rows().get(index));
        if(!CaptureTransactionSource.rejection(historyRow,b.signature(),b.slot()).isEmpty()||!historyRow.equals(raw)||!JSON.readTree(page.context()).equals(response.path("sourceContext"))||!page.envelope().receivedAt().toString().equals(response.path("receivedAt").asText()))throw new IllegalStateException("RETAINED_PROOF_CONFLICT");return true;
    }
    private CaptureLedgerUseCase.HistoryPage history(PumpSwapCapture.Configuration c,Binding b,String reference,Collection<PumpSwapCapture.Envelope> proofs){
        var ref=JSON.readTree(reference);var digest=ref.path("pageDigest").asText();var cursor=ref.path("cursor").asText();
        if(!ref.path("cursor").isString()||!cursor.isEmpty()&&!cursor.matches("[A-Za-z0-9_:-]{1,512}"))throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
        var envelope=proofs.stream().filter(e->e.kind().equals("HTTP_getTransactionsForAddress")&&e.digest().equals(digest)).findFirst().orElseThrow(()->new IllegalStateException("RECOVERY_PROVENANCE_MISSING"));
        var contexts=proofs.stream().filter(e->{if(!e.kind().equals("CAPTURE_HISTORY_CONTEXT"))return false;var n=JSON.readTree(e.payload());return n.path("pageDigest").asText().equals(digest)&&n.path("pool").asText().equals(b.pool())&&n.path("cursor").asText().equals(cursor);}).toList();
        if(contexts.size()!=1)throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");var context=contexts.getFirst();var n=JSON.readTree(context.payload());
        if(!n.path("version").asText().equals("capture-history-context-v1")||!n.path("session").asText().equals(c.sessionId().toString())||!n.path("method").asText().equals("getTransactionsForAddress")||!n.path("receivedAt").asText().equals(envelope.receivedAt().toString())||!context.receivedAt().equals(envelope.receivedAt()))throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
        if(CaptureBudget.reuse(c)&&(!c.version().equals(n.path("sourcePolicy").asText())||!"finalized".equals(n.path("commitment").asText())||!"json".equals(n.path("encoding").asText())||!"full".equals(n.path("transactionDetails").asText())||!n.path("maxSupportedTransactionVersion").isIntegralNumber()||!n.path("maxSupportedTransactionVersion").bigIntegerValue().equals(java.math.BigInteger.ONE)))throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
        var rows=io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.exactElements(envelope.payload(),"/result/data");if(rows.size()>100)throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
        var page=new CaptureLedgerUseCase.HistoryPage(c.sessionId(),b.pool(),cursor,envelope,rows,context.payload());capture.verifyHistoryReference(c,page,b.pool(),b.signature(),b.slot(),reference,b.received());return page;
    }
}
