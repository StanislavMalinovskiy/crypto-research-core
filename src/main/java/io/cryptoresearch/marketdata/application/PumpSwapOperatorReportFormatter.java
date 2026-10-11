package io.cryptoresearch.marketdata.application;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import io.cryptoresearch.marketdata.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PumpSwapOperatorReportFormatter {
    private static final String VERSION="pumpswap-operator-report-v1";
    private static final String ASSOCIATION="REQUESTED_TOGETHER_UNVERIFIED", CONSISTENCY="INDEPENDENT_PANELS";
    private static final Comparator<String> TEXT=(a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    private static final Set<Class<?>> RECORDS=Set.of(PumpSwapOperatorReport.Request.class,PumpSwapPoolMetrics.Request.class,
        PumpSwapCaptureMonitor.Snapshot.class,PumpSwapCaptureMonitor.Counters.class,PumpSwapCaptureMonitor.DispositionCount.class,
        PumpSwapCaptureMonitor.Position.class,PumpSwapCaptureMonitor.PoolPosition.class,PumpSwapCaptureMonitor.GapCount.class,PumpSwapCapture.Gap.class,
        PumpSwapPoolMetrics.Result.class,PumpSwapPoolMetrics.Pool.class,PumpSwapPoolMetrics.Rational.class,PumpSwapPoolMetrics.OperationReference.class,
        PumpSwapPoolMetrics.FeeFacts.class,PumpSwapPoolMetrics.UnpricedOperation.class,PumpSwapPoolMetrics.Price.class,PumpSwapPoolMetrics.Ohlc.class,
        PumpSwapPoolMetrics.Activity.class,PumpSwapPoolMetrics.Candle.class,PumpSwapPoolMetrics.PoolTotal.class,PumpSwapPoolMetrics.TransactionDisposition.class,
        PumpSwapPoolMetrics.StatusCount.class,PumpSwapPoolMetrics.Reconciliation.class,PumpSwapPoolMetrics.Source.class,PumpSwapPoolMetrics.Interpretation.class,
        PumpSwapOperations.Position.class);
    private final JsonMapper json=JsonMapper.builder().build();
    PumpSwapOperatorReport.Result format(PumpSwapOperatorReport.Request request,
            Optional<PumpSwapCaptureMonitor.Snapshot> monitor, Optional<PumpSwapPoolMetrics.Result> metrics) {
        try {
            request=new PumpSwapOperatorReport.Request(Objects.requireNonNull(request.sessionId()),PumpSwapPoolMetricsCalculator.normalize(request.metrics()));
            String monitorStatus=monitor.isPresent()?"MONITOR_PRESENT":"MONITOR_ABSENT", metricsStatus=metrics.isPresent()?"METRICS_PRESENT":"METRICS_ABSENT";
            var root=new HashMap<String,Object>();root.put("formatVersion",VERSION);root.put("request",request);root.put("association",ASSOCIATION);root.put("consistency",CONSISTENCY);
            var monitorPanel=new HashMap<String,Object>();monitorPanel.put("status",monitorStatus);monitorPanel.put("snapshot",monitor.orElse(null));root.put("monitor",monitorPanel);
            var metricsPanel=new HashMap<String,Object>();metricsPanel.put("status",metricsStatus);metricsPanel.put("result",metrics.orElse(null));root.put("metrics",metricsPanel);
            var output=new BoundedBytes(64_000_000);json.writeValue(output,project(root));
            var summary=new Summary();summary.field("format",VERSION);summary.field("requested session",request.sessionId());summary.field("requested metrics",request.metrics());
            summary.field("association",ASSOCIATION);summary.field("consistency",CONSISTENCY);summary.field("monitor status",monitorStatus);summary.field("metrics status",metricsStatus);
            if(monitor.isPresent()) {
                var m=monitor.orElseThrow();summary.field("monitor observedAt",m.observedAt());summary.field("recorded state",m.recordedState());summary.field("recorded reason",m.reason());
                summary.field("configured start",m.startedAt());summary.field("configured end",m.endsAt());summary.field("lease expiry",m.lockedUntil());summary.field("lease valid",m.leaseValid());summary.field("window open",m.windowOpen());
                summary.field("committed local counters",m.counters());summary.field("committed local measured usage rpcHundredths",m.counters().rpcHundredths());summary.field("conservative reservation reservedHundredths",m.counters().reservedHundredths());
                summary.field("unresolved total",m.totalUnresolved());summary.field("detail count",m.intervals().size());summary.field("details truncated",m.detailsTruncated());summary.field("inconsistent gap status",m.inconsistentGapStatus());
                summary.field("process activity",m.processActivity());summary.field("transport activity",m.transportActivity());summary.field("coverage",m.coverage());summary.field("unreceived loss",m.unreceivedLoss());summary.field("actual billing (not invoice)",m.actualBilling());
            }
            if(metrics.isPresent()) {
                var m=metrics.orElseThrow();summary.field("panel", "observation-time activity");summary.field("metrics fingerprint",m.fingerprint());summary.field("metrics window/cutoff/interval",m.request());
                summary.field("complete reconciliation",m.reconciliation());summary.field("populated observation-time buckets",m.candles().size());summary.field("pool/mint groups",m.pools().size());
                summary.field("interpretation",m.interpretation());summary.field("source limitations",m.source().limitations());
            }
            return new PumpSwapOperatorReport.Result(VERSION,request,ASSOCIATION,CONSISTENCY,monitorStatus,metricsStatus,output.text(),summary.output.text());
        } catch(RuntimeException failure) {
            for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof OutputLimit)throw new IllegalArgumentException("OPERATOR_REPORT_OUTPUT_LIMIT");
            throw new IllegalStateException("OPERATOR_REPORT_FORMAT_FAILED");
        }
    }
    /** Only the fixed public safe record types can enter this projection; no bean/configuration/raw/Throwable serialization. */
    private JsonNode project(Object value) {
        if(value==null)return json.nullNode();
        if(value instanceof Optional<?> optional)return project(optional.orElse(null));
        if(value instanceof String text){validText(text);return json.getNodeFactory().textNode(text);}
        if(value instanceof Instant || value instanceof UUID || value instanceof Enum<?>)return project(value.toString());
        if(value instanceof Boolean b)return json.getNodeFactory().booleanNode(b);
        if(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger)return project(value.toString());
        if(value instanceof List<?> list){var array=json.createArrayNode();for(var item:list)array.add(project(item));return array;}
        if(value instanceof Map<?,?> map){var object=json.createObjectNode();var keys=new ArrayList<String>();for(var key:map.keySet()){if(!(key instanceof String text))throw new IllegalArgumentException();validText(text);keys.add(text);}keys.sort(TEXT);for(var key:keys)object.set(key,project(map.get(key)));return object;}
        if(RECORDS.contains(value.getClass())) {
            var fields=new TreeMap<String,Object>(TEXT);
            for(var field:value.getClass().getRecordComponents())try{fields.put(field.getName(),field.getAccessor().invoke(value));}catch(ReflectiveOperationException failure){throw new IllegalArgumentException();}
            return project(fields);
        }
        throw new IllegalArgumentException("Unsupported report value");
    }
    private static void validText(String text) {
        for(int i=0;i<text.length();i++)if(Character.isSurrogate(text.charAt(i))){if(!Character.isHighSurrogate(text.charAt(i)) || i+1==text.length() || !Character.isLowSurrogate(text.charAt(++i)))throw new IllegalArgumentException();}
    }
    private static final class OutputLimit extends IllegalArgumentException {}
    private static final class BoundedBytes extends ByteArrayOutputStream {
        final int limit;
        BoundedBytes(int limit){super(4096);this.limit=limit;}
        @Override public synchronized void write(int value){if(count>=limit)throw new OutputLimit();super.write(value);}
        @Override public synchronized void write(byte[] bytes,int offset,int length){if(length>limit-count)throw new OutputLimit();super.write(bytes,offset,length);}
        void literal(String text){var bytes=text.getBytes(StandardCharsets.UTF_8);write(bytes,0,bytes.length);}
        String text(){return toString(StandardCharsets.UTF_8);}
    }
    /** Escaped single-line output is bounded while writing, including Unicode line/control separators. */
    private final class Summary {
        final BoundedBytes output=new BoundedBytes(16_384);
        void field(String label,Object value){if(output.size()>0)output.literal("; ");output.literal(label+"=");write(project(value));}
        void write(JsonNode node){
            if(node.isTextual()){quote(node.asText());}
            else if(node.isNull())output.literal("null");else if(node.isBoolean())output.literal(node.asText());
            else if(node.isArray()){output.literal("[");for(int i=0;i<node.size();i++){if(i>0)output.literal(",");write(node.get(i));}output.literal("]");}
            else if(node.isObject()){output.literal("{");boolean first=true;for(var key:node.propertyNames()){if(!first)output.literal(",");first=false;quote(key);output.literal(":");write(node.get(key));}output.literal("}");}
            else throw new IllegalArgumentException();
        }
        void quote(String text){
            output.literal("\"");for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                if(c=='\"' || c=='\\')output.literal("\\"+c);
                else if(Character.isISOControl(c) || c=='\u2028' || c=='\u2029')output.literal(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
                else if(Character.isHighSurrogate(c)){output.literal(text.substring(i,i+2));i++;}
                else output.literal(String.valueOf(c));
            }output.literal("\"");
        }
    }
}
