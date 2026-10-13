package io.cryptoresearch.signal.application;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.PumpSwapRetainedNativePriceSignal.*;

/** Additive source acquisition/delegation; no signal persistence or provider work. */
@Service
public class RetainedNativeSolPriceRiseDiagnostic {
    private final MarketDataApi marketdata;
    private final VersionedNativeSolPriceRiseDiagnostic pure;
    private final Clock clock;
    public RetainedNativeSolPriceRiseDiagnostic(MarketDataApi marketdata,VersionedNativeSolPriceRiseDiagnostic pure,Clock clock){this.marketdata=marketdata;this.pure=pure;this.clock=clock;}
    public Result diagnose(Request request){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("RETAINED_DIAGNOSTIC_REQUIRES_NO_TRANSACTION");
        var q=normalize(request);var snapshot=marketdata.retainedPumpSwapWindow(q.source());
        if(snapshot==null||!snapshot.version().equals("pumpswap-retained-window-v1")||!snapshot.request().equals(q.source())
            ||!q.pool().chain().equals(io.cryptoresearch.kernel.api.ChainId.SOLANA_MAINNET.value())||snapshot.pools().stream().noneMatch(p->p.pool().equals(q.pool().pool())&&p.baseMint().equals(q.pool().baseMint())&&p.quoteMint().equals(q.pool().quoteMint())))throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_SOURCE_MISMATCH");
        var r=snapshot.readObservedAt();
        if(q.eventEnd().isAfter(r))throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_EVENT_AFTER_READ");
        for(var tx:snapshot.batch().transactions())if(tx.receivedAt().isAfter(r)||tx.admittedAt().isAfter(r)||tx.ingestedAt().isAfter(r))throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_CLOCK_BEHIND_SOURCE");
        for(var proof:snapshot.manifest().proofs())if(proof.receivedAt().isAfter(r))throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_CLOCK_BEHIND_SOURCE");
        var preliminary=new PumpSwapVersionedPoolCandles.Request(snapshot.batch(),snapshot.pools(),"pumpswap-pool-legs-v5",q.source().eventFromInclusive(),q.source().eventToExclusive(),r,r);
        var calculated=marketdata.pumpSwapVersionedPoolCandles(preliminary);
        var expected=new PumpSwapVersionedPoolCandles.Descriptor(preliminary.decoderVersion(),preliminary.pools().stream().sorted(Comparator.comparing(PumpSwapPoolLegs.PoolBinding::pool)).toList(),preliminary.fromInclusive(),preliminary.toExclusive(),r,r);
        if(calculated==null||!calculated.algorithmVersion().equals("pumpswap-pool-net-candles-v1")||!calculated.request().equals(expected))throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_SOURCE_MISMATCH");
        var d=clock.instant().truncatedTo(ChronoUnit.MICROS);if(d.isBefore(r))throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_CLOCK_BEHIND_SOURCE");
        var source=new PumpSwapVersionedPoolCandles.Request(snapshot.batch(),snapshot.pools(),"pumpswap-pool-legs-v5",q.source().eventFromInclusive(),q.source().eventToExclusive(),d,d);
        var result=pure.diagnose(new io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.Request(source,q.pool(),q.eventEnd()));
        var reasons=new ArrayList<String>();long nonraw=snapshot.manifest().dispositions().stream().filter(b->b.rawDigest().isEmpty()||!List.of("CAPTURED","FAILED_TRANSACTION").contains(b.status())).count();
        if(nonraw>0)reasons.add("NON_RAW_LINKED_DISPOSITIONS:"+nonraw);
        long conflicts=snapshot.manifest().dispositions().stream().filter(b->b.conflicts()>0||b.status().equals("CONFLICT")).count();if(conflicts>0)reasons.add("CAPTURE_CONFLICTS:"+conflicts);
        long txGaps=snapshot.manifest().gaps().stream().filter(g->g.kind().equals("TRANSACTION")&&!g.status().equals("RECOVERED_TRANSACTIONS")).count();if(txGaps>0)reasons.add("UNRESOLVED_TRANSACTION_GAPS:"+txGaps);
        long stateGaps=snapshot.manifest().gaps().stream().filter(g->g.kind().equals("STATE")).count();if(stateGaps>0)reasons.add("UNRESOLVED_STATE_GAPS:"+stateGaps);
        long unknownGaps=snapshot.manifest().gaps().stream().filter(g->!List.of("TRANSACTION","STATE").contains(g.kind())).count();if(unknownGaps>0)reasons.add("UNKNOWN_GAP_CONTEXT:"+unknownGaps);
        var effective=reasons.isEmpty()?result.status():io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.Status.INSUFFICIENT;
        var limitations=List.of("DB_VISIBLE_RECEIVED_SCOPE_ONLY","CAPTURE_COVERAGE_UNKNOWN","DIAGNOSTIC_ONLY_NOT_ADMITTED");
        var hash=new SignalFingerprint().field("version","retained-native-sol-diagnostic-v1").field("configuration",snapshot.configurationDigest()).field("source",snapshot.sourceFingerprint()).field("manifest",snapshot.manifest().fingerprint()).field("R",snapshot.readObservedAt().toString()).field("D",d.toString()).field("K",d.toString()).field("pure",result.fingerprint()).field("status",effective.name());for(var reason:reasons)hash.field("reason",reason);for(var limitation:limitations)hash.field("limitation",limitation);
        var output=new Result("retained-native-sol-diagnostic-v1",q.source(),snapshot.configurationDigest(),snapshot.readObservedAt(),d,snapshot.manifest(),snapshot.sourceFingerprint(),result,effective,reasons,limitations,hash.finish());
        var stream=new java.io.OutputStream(){long bytes;private void admit(int count){bytes=Math.addExact(bytes,count);if(bytes>64000000)throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_OUTPUT_LIMIT");}public void write(int b){admit(1);}public void write(byte[] b,int offset,int count){admit(count);}};
        try{tools.jackson.databind.json.JsonMapper.builder().build().writeValue(stream,output);}catch(RuntimeException failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause())if("RETAINED_DIAGNOSTIC_OUTPUT_LIMIT".equals(cause.getMessage()))throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_OUTPUT_LIMIT");throw failure;}return output;
    }
    private static Request normalize(Request q){
        try{Objects.requireNonNull(q.pool());var s=q.source();var f=s.receivedFromInclusive().truncatedTo(ChronoUnit.MICROS);var t=s.receivedToExclusive().truncatedTo(ChronoUnit.MICROS);var a=s.eventFromInclusive().truncatedTo(ChronoUnit.MICROS);var b=s.eventToExclusive().truncatedTo(ChronoUnit.MICROS);var e=q.eventEnd().truncatedTo(ChronoUnit.MICROS);Objects.requireNonNull(s.session());
            if(!f.isBefore(t)||Duration.between(f,t).compareTo(Duration.ofHours(24))>0||!a.isBefore(b)||Duration.between(a,b).getSeconds()<600||Duration.between(a,b).getSeconds()>300000||a.getNano()!=0||b.getNano()!=0||e.getNano()!=0||Math.floorMod(a.getEpochSecond(),300)!=0||Math.floorMod(b.getEpochSecond(),300)!=0||Math.floorMod(e.getEpochSecond(),300)!=0||e.isAfter(b)||e.minusSeconds(600).isBefore(a))throw new IllegalArgumentException();
            return new Request(new PumpSwapRetainedWindow.Request(s.session(),f,t,a,b),q.pool(),e);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("RETAINED_DIAGNOSTIC_INVALID_REQUEST");}
    }
}
