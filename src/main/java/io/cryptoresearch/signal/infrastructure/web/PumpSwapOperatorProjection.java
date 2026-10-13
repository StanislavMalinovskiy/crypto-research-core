package io.cryptoresearch.signal.infrastructure.web;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.PumpSwapRetainedNativePriceSignal;
import tools.jackson.databind.json.JsonMapper;

/** Explicit payload-free whitelist; domain records are never reflectively exported. */
final class PumpSwapOperatorProjection {
    static final String VERSION = "pumpswap-local-operator-screen-v1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    Map<String,Object> monitor(UUID session, Optional<PumpSwapCaptureMonitor.Snapshot> snapshot) {
        return object("version",VERSION,"panels","INDEPENDENT_PANELS","requestedSession",session.toString(),
                "status",snapshot.isPresent()?"MONITOR_PRESENT":"MONITOR_ABSENT","monitor",snapshot.map(this::capture).orElse(null));
    }

    private Map<String,Object> capture(PumpSwapCaptureMonitor.Snapshot s) {
        var c=s.counters();
        var counters=object("requests",number(c.requests()),"receivedBytes",number(c.receivedBytes()),
            "subscriptionBytes",number(c.subscriptionBytes()),"retainedBytes",number(c.retainedBytes()),
            "rpcHundredths",number(c.rpcHundredths()),"reservedHundredths",number(c.reservedHundredths()),
            "transactions",number(c.transactions()),"states",number(c.states()),"duplicates",number(c.duplicates()),
            "conflicts",number(c.conflicts()),"failures",number(c.failures()));
        return object("session",s.sessionId().toString(),"configurationDigest",s.configurationDigest(),"recordedState",s.recordedState(),"reason",s.reason(),
            "observedAt",instant(s.observedAt()),"startedAt",instant(s.startedAt()),"endsAt",instant(s.endsAt()),"lockedUntil",instant(s.lockedUntil()),
            "leaseValid",s.leaseValid(),"windowOpen",s.windowOpen(),"counters",counters,
            "dispositions",s.dispositionCounts().stream().map(d->object("status",d.status(),"label",d.status().equals("PENDING")?"DURABLE_PENDING":d.status(),"count",number(d.count()))).toList(),
            "positions",s.committedPositions().stream().map(p->object("pool",p.pool(),"position",p.position().map(v->object("signature",v.signature(),"slot",number(v.slot()))).orElse(null))).toList(),
            "unresolvedCounts",s.unresolvedCounts().stream().map(g->object("pool",g.pool(),"kind",g.kind(),"status",g.status(),"count",number(g.count()))).toList(),
            "totalUnresolved",number(s.totalUnresolved()),"inconsistentGapStatus",s.inconsistentGapStatus(),"detailsTruncated",s.detailsTruncated(),
            "intervals",s.intervals().stream().map(g->object("id",number(g.id()),"pool",g.pool(),"kind",g.kind(),"reason",g.reason(),"fromSlot",nullableNumber(g.fromSlot()),"toSlot",nullableNumber(g.toSlot()),"openedAt",instant(g.openedAt()),"closedAt",instant(g.closedAt()),"boundarySignature",g.boundarySignature(),"status",g.status())).toList(),
            "processActivity",s.processActivity(),"transportActivity",s.transportActivity(),"coverage",s.coverage(),"unreceivedLoss",s.unreceivedLoss(),"actualBilling",s.actualBilling(),
            "memoryQueueOccupancy","UNKNOWN","modelCost","UNKNOWN","actualInvoice","UNKNOWN","costInterpretation","LOCAL_COUNTERS_AND_RESERVATIONS_NOT_CHARGES");
    }

    Map<String,Object> diagnostic(PumpSwapRetainedNativePriceSignal.Result s) {
        var p=s.pure();var c=p.candles();var m=s.manifest();var r=c.reconciliation();
        var gapCounts=new TreeMap<String,Long>();
        for(var gap:m.gaps()) if(!gap.kind().equals("TRANSACTION")||!gap.status().equals("RECOVERED_TRANSACTIONS")) gapCounts.merge(gap.kind()+":"+gap.status(),1L,Math::addExact);
        var q=s.request();
        var scope=object("session",q.session().toString(),"receivedFrom",instant(q.receivedFromInclusive()),"receivedTo",instant(q.receivedToExclusive()),"eventFrom",instant(q.eventFromInclusive()),"eventTo",instant(q.eventToExclusive()),"pool",pool(p.pool()));
        var facts=object("scope",scope,"R",instant(s.readObservedAt()),"D",instant(s.derivationObservedAt()),"K",instant(p.decisionKnowledgeAt()),"E",instant(p.eventEnd()),
            "effectiveStatus",s.effectiveStatus().name(),"effectiveReasons",s.reasons(),"pureStatus",p.status().name(),"pureReasons",p.reasons(),
            "bridgeVersion",s.version(),"algorithmVersion",p.algorithmVersion(),"configurationVersion",p.configurationVersion(),"decoderVersion",c.request().decoderVersion(),"candleAlgorithmVersion",c.algorithmVersion(),"canonicalizationVersion",c.canonicalizationVersion(),
            "configurationDigest",s.configurationDigest(),"sourceFingerprint",s.sourceFingerprint(),"manifestFingerprint",m.fingerprint(),"wrapperFingerprint",s.fingerprint(),"pureFingerprint",p.fingerprint(),"candleFingerprint",c.fingerprint(),"selectedInputFingerprint",p.selectedInputFingerprint(),
            "distinctSignatures",observation(PumpSwapRetainedWindow.Observation.exact(m.distinctSignatures())),"sourceProofBytes",observation(PumpSwapRetainedWindow.Observation.exact(m.sourceProofBytes())),"statuses",counts(m.statuses()),"conservativeGaps",counts(gapCounts),
            "reconciliation",object("transactions",number(r.transactions()),"operations",number(r.operations()),"included",number(r.included()),"excluded",number(r.excluded()),"priced",number(r.priced()),"unpriced",number(r.unpriced()),"statuses",counts(r.statuses())),
            "candles",c.candles().stream().map(this::candle).toList(),"prior",p.prior().map(this::candle).orElse(null),"current",p.current().map(this::candle).orElse(null),
            "analysis",p.analysis(),"historicalAtEventEnd",p.historicalAtEventEnd(),"readiness",p.readiness(),"priceBasis","POOL_NET_SOL_PER_BASE", "sourceLimitations",m.limitations(),"candleLimitations",c.limitations(),"limitations",s.limitations());
        return object("version",VERSION,"panels","INDEPENDENT_PANELS","status","DIAGNOSTIC_PRESENT","diagnostic",facts);
    }

    private Map<String,Object> candle(PumpSwapVersionedPoolCandles.Candle c) {
        return object("pool",pool(c.pool()),"fromInclusive",instant(c.fromInclusive()),"toExclusive",instant(c.toExclusive()),"operationCount",number(c.operationCount()),"ordinaryCount",number(c.ordinaryCount()),"multiHopCount",number(c.multiHopCount()),"pricedCount",number(c.pricedCount()),"unpricedCount",number(c.unpricedCount()),"missingIndexCount",number(c.missingIndexCount()),"baseAmount",c.baseAmount().toString(),"poolNetQuoteLamports",c.poolNetQuoteLamports().map(Object::toString).orElse(null),"operationIdentities",c.operationIdentities(),"ohlc",c.ohlc().map(o->object("open",price(o.open()),"high",price(o.high()),"low",price(o.low()),"close",price(o.close()))).orElse(null));
    }
    private static Map<String,Object> price(PumpSwapVersionedPoolCandles.Price p) {
        return object("value",object("numerator",p.value().numerator().toString(),"denominator",p.value().denominator().toString()),"operationIdentity",p.operationIdentity());
    }
    private static Map<String,Object> pool(PumpSwapPoolMetrics.Pool p) {return object("chain",p.chain(),"pool",p.pool(),"baseMint",p.baseMint(),"quoteMint",p.quoteMint());}
    Map<String,Object> error(String code,PumpSwapRetainedWindow.Diagnostic diagnostic) {
        var d=diagnostic==null?new PumpSwapRetainedWindow.Diagnostic(PumpSwapRetainedWindow.Observation.unknown(),PumpSwapRetainedWindow.Observation.unknown()):diagnostic;
        return object("version",VERSION,"status","ERROR","code",code,"observations",object("signatures",observation(d.observedDistinctSignatures()),"sourceProofBytes",observation(d.observedSourceProofBytes())));
    }
    private static Map<String,Object> observation(PumpSwapRetainedWindow.Observation o) {return object("value",o.value().map(Object::toString).orElse(null),"qualification",o.qualification().name());}
    private static Map<String,Object> counts(Map<String,? extends Number> counts) {var result=new TreeMap<String,Object>();counts.forEach((key,value)->result.put(key,value.toString()));return result;}
    private static String instant(Instant t) {return t==null?null:t.toString();}
    private static String number(long n) {return Long.toString(n);}
    private static String nullableNumber(Long n) {return n==null?null:Long.toString(n);}
    private static Map<String,Object> object(Object... pairs) {var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}

    byte[] encode(Map<String,Object> projection) {
        validate(projection);
        var output=new ByteArrayOutputStream(){private void admit(int bytes){if((long)count+bytes>8000000)throw new IllegalArgumentException("OUTPUT_LIMIT");}public synchronized void write(int b){admit(1);super.write(b);}public synchronized void write(byte[] b,int offset,int length){admit(length);super.write(b,offset,length);}};
        try{JSON.writeValue(output,projection);}catch(RuntimeException failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause())if("OUTPUT_LIMIT".equals(cause.getMessage()))throw new IllegalArgumentException("OUTPUT_LIMIT");throw failure;}
        return output.toByteArray();
    }
    private static void validate(Object value) {
        if(value==null||value instanceof Boolean)return;
        if(value instanceof String text){if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4096)throw new IllegalArgumentException("OUTPUT_LIMIT");return;}
        if(value instanceof Map<?,?> map){map.forEach((key,item)->{validate(key);validate(item);});return;}
        if(value instanceof Collection<?> collection){collection.forEach(PumpSwapOperatorProjection::validate);return;}
        throw new IllegalArgumentException("INVALID_PROJECTION");
    }
}
