package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;

class PumpSwapOperatorReportTest {
    static final Instant AT=Instant.parse("2026-10-11T00:00:00Z");
    static final UUID SESSION=new UUID(0,7);
    static final String KEY="sha256:"+"a".repeat(64);
    final PumpSwapOperatorReportFormatter formatter=new PumpSwapOperatorReportFormatter();
    static PumpSwapOperatorReport.Request request() {
        return new PumpSwapOperatorReport.Request(SESSION,new PumpSwapPoolMetrics.Request(KEY,AT,AT.plusSeconds(60),AT.plusSeconds(60),60));
    }
    static PumpSwapPoolMetrics.Result metrics(String limitation) {
        var amount=new BigInteger("18446744073709551615");
        var ref=new PumpSwapPoolMetrics.OperationReference(KEY,KEY,KEY,KEY,"solana:mainnet","signature",new PumpSwapOperations.Position(10,List.of(2,11),3),AT,
            "buy","trader",amount,BigInteger.valueOf(9007199254740993L),Optional.of(6),Optional.of(9),
            new PumpSwapPoolMetrics.FeeFacts(Map.of("declared",amount),Map.of("attributed",BigInteger.valueOf(-9007199254740993L)),Map.of(),"INCOMPLETE",List.of("unknown")));
        var price=new PumpSwapPoolMetrics.Price(new PumpSwapPoolMetrics.Rational(BigInteger.valueOf(9007199254740993L),amount),ref);
        var activity=new PumpSwapPoolMetrics.Activity(1,1,0,1,0,amount,BigInteger.valueOf(9007199254740993L),List.of(KEY),List.of());
        var pool=new PumpSwapPoolMetrics.Pool("solana:mainnet","pool","base","quote");
        var tx=new PumpSwapPoolMetrics.TransactionDisposition(KEY,KEY,"solana:mainnet","signature","provider",9007199254740993L,Optional.empty(),AT,AT.plusSeconds(1),AT.plusSeconds(2),PumpSwapOperations.Status.PARTIAL,"retained",Optional.of(amount),true,List.of(KEY));
        return new PumpSwapPoolMetrics.Result("pumpswap-pool-metrics-v1","length-prefixed-v1",request().metrics(),
            new PumpSwapPoolMetrics.Source(KEY,"pumpswap-storage-v1","pumpswap-operations-v4","may18-oct07-endpoint-proof-v4","length-prefixed-v1",KEY,Map.of("pin",KEY),List.of(limitation)),
            List.of(new PumpSwapPoolMetrics.PoolTotal(pool,activity)),List.of(new PumpSwapPoolMetrics.Candle(pool,AT,AT.plusSeconds(60),activity,Optional.of(new PumpSwapPoolMetrics.Ohlc(price,price,price,price)))),List.of(tx),List.of(ref),List.of(),
            new PumpSwapPoolMetrics.Reconciliation(1,1,0,1,1,0,1,0,1,0,List.of(new PumpSwapPoolMetrics.StatusCount(PumpSwapOperations.Status.PARTIAL,1))),
            new PumpSwapPoolMetrics.Interpretation("HISTORICAL_DIAGNOSTIC","OBSERVED_BATCH_ONLY","INCOMPLETE","UNKNOWN","INCOMPLETE","NOT_ESTABLISHED","SPARSE_POPULATED_ONLY_NO_CARRY_FORWARD","DETERMINISTIC_NOT_ONCHAIN_CHRONOLOGY","HISTORICAL_AVAILABILITY_NOT_ESTABLISHED"),KEY);
    }
    @Test void exactNativeIntegersRationalsAndFeesRemainStringsWithIndependentPanelIdentity() {
        var metrics=metrics("unknown gaps");
        assertThatCode(()->formatter.format(request(),Optional.empty(),Optional.of(metrics)))
            .as("an explicit metrics panel must render lossless native evidence with an independently absent monitor").doesNotThrowAnyException();
        var report=formatter.format(request(),Optional.empty(),Optional.of(metrics));var json=JsonMapper.builder().build().readTree(report.json());
        assertThat(json.path("metrics").path("result").path("selectedOperations").get(0).path("baseRawUnits").asText()).isEqualTo("18446744073709551615");
        assertThat(json.path("metrics").path("result").path("selectedOperations").get(0).path("fees").path("attributed").path("attributed").asText()).isEqualTo("-9007199254740993");
        var rational=json.path("metrics").path("result").path("candles").get(0).path("ohlc").path("open").path("value");
        assertThat(rational.path("numerator").asText()).isEqualTo(metrics.candles().getFirst().ohlc().orElseThrow().open().value().numerator().toString());
        assertThat(rational.path("denominator").asText()).isEqualTo(metrics.candles().getFirst().ohlc().orElseThrow().open().value().denominator().toString());
        assertThat(json.path("monitor").path("snapshot").isNull()).isTrue();assertThat(report.monitorStatus()).isEqualTo("MONITOR_ABSENT");
        assertThat(report.association()).isEqualTo("REQUESTED_TOGETHER_UNVERIFIED");assertThat(report.consistency()).isEqualTo("INDEPENDENT_PANELS");
        assertThat(report.summary()).contains("observation-time activity","DETERMINISTIC_NOT_ONCHAIN_CHRONOLOGY","INDEPENDENT_PANELS");
    }
    static PumpSwapCaptureMonitor.Snapshot monitor(String reason) {
        var gaps=java.util.stream.LongStream.rangeClosed(1,100).mapToObj(i->new PumpSwapCapture.Gap(i,"pool","STATE","gap",null,null,AT,null,null,"UNKNOWN")).toList();
        return new PumpSwapCaptureMonitor.Snapshot(SESSION,KEY,"RUNNING",reason,AT,AT,AT.plusSeconds(60),AT.plusSeconds(30),true,true,
            new PumpSwapCaptureMonitor.Counters(9007199254740993L,2,3,4,5,95000000,7,8,9,10,11),
            List.of(new PumpSwapCaptureMonitor.DispositionCount("FAILED_TRANSACTION",49)),List.of(new PumpSwapCaptureMonitor.PoolPosition("pool",Optional.empty())),
            List.of(new PumpSwapCaptureMonitor.GapCount("pool","STATE","UNKNOWN",100),new PumpSwapCaptureMonitor.GapCount("pool","STATE","RECOVERED_TRANSACTIONS",1)),101,true,gaps,true,
            "UNVERIFIED","UNVERIFIED","UNVERIFIED","UNKNOWN","UNKNOWN");
    }
    JsonNode integralStrings(JsonNode input) {
        var json=JsonMapper.builder().build();
        if(input.isIntegralNumber())return json.getNodeFactory().textNode(input.bigIntegerValue().toString());
        if(input.isObject()){var object=json.createObjectNode();for(var key:input.propertyNames())object.set(key,integralStrings(input.get(key)));return object;}
        if(input.isArray()){var array=json.createArrayNode();for(var child:input)array.add(integralStrings(child));return array;}return input;
    }
    @Test void completePublicPanelsRoundTripModuloDeclaredIntegerStringsAndPreserveClocksNullsAndDetails() {
        var monitor=monitor("retained");var metrics=metrics("unknown");var report=formatter.format(request(),Optional.of(monitor),Optional.of(metrics));var mapper=JsonMapper.builder().build();var tree=mapper.readTree(report.json());
        assertThat(tree.path("monitor").path("snapshot")).isEqualTo(integralStrings(mapper.valueToTree(monitor)));
        assertThat(tree.path("metrics").path("result")).isEqualTo(integralStrings(mapper.valueToTree(metrics)));
        assertThat(tree.path("monitor").path("snapshot").path("intervals")).hasSize(100);
        assertThat(tree.path("monitor").path("snapshot").path("totalUnresolved").asText()).isEqualTo("101");
        assertThat(tree.path("monitor").path("snapshot").path("inconsistentGapStatus").booleanValue()).isTrue();
        assertThat(tree.path("metrics").path("result").path("transactions").get(0).path("sourceTime").isNull()).isTrue();
        assertThat(tree.path("metrics").path("result").path("transactions").get(0).path("slot").asText()).isEqualTo("9007199254740993");
        assertThat(report.summary()).contains("committed local measured usage","conservative reservation","actual billing (not invoice)=\"UNKNOWN\"","details truncated=true","inconsistent gap status=true","PARTIAL","NOT_ESTABLISHED","HISTORICAL_AVAILABILITY_NOT_ESTABLISHED");
        assertThat(tree.propertyNames()).containsExactly("association","consistency","formatVersion","metrics","monitor","request");
    }
    @Test void fourPresenceCombinationsRemainIndependentWithoutFabricatedPanels() {
        for(boolean hasMonitor:List.of(false,true))for(boolean hasMetrics:List.of(false,true)) {
            var report=formatter.format(request(),hasMonitor?Optional.of(monitor("")):Optional.empty(),hasMetrics?Optional.of(metrics("")):Optional.empty());var tree=JsonMapper.builder().build().readTree(report.json());
            assertThat(tree.path("monitor").path("snapshot").isNull()).isEqualTo(!hasMonitor);assertThat(tree.path("metrics").path("result").isNull()).isEqualTo(!hasMetrics);
            assertThat(report.monitorStatus()).isEqualTo(hasMonitor?"MONITOR_PRESENT":"MONITOR_ABSENT");assertThat(report.metricsStatus()).isEqualTo(hasMetrics?"METRICS_PRESENT":"METRICS_ABSENT");
            assertThat(report.summary()).contains("REQUESTED_TOGETHER_UNVERIFIED","INDEPENDENT_PANELS");
            assertThat(report.json()).doesNotContain("freezeTime","reportedAt","datasetFingerprint","leaseHolder","rawResult","configuration_json");
        }
    }
    @Test void controlsUnicodeAndInputStringsCannotInjectHumanSummaryLines() {
        var input="snow雪\"\\\n\r\t\u001b[31m\u0085\u2028\u2029\uD83D\uDE00";
        var report=formatter.format(request(),Optional.of(monitor(input)),Optional.of(metrics(input)));var tree=JsonMapper.builder().build().readTree(report.json());
        assertThat(tree.path("monitor").path("snapshot").path("reason").asText()).isEqualTo(input);
        assertThat(tree.path("metrics").path("result").path("source").path("limitations").get(0).asText()).isEqualTo(input);
        assertThat(report.summary()).doesNotContain("\n","\r","\t","\u001b","\u0085","\u2028","\u2029").contains("\\u001b","\\u2028","雪","😀");
        assertThat(formatter.format(request(),Optional.of(monitor(input)),Optional.of(metrics(input)))).isEqualTo(report);
    }
    PumpSwapPoolMetrics.Result withPins(PumpSwapPoolMetrics.Result m,Map<String,String> pins) {
        var s=m.source();return new PumpSwapPoolMetrics.Result(m.algorithmVersion(),m.canonicalizationVersion(),m.request(),new PumpSwapPoolMetrics.Source(s.batchKey(),s.storageVersion(),s.decoderVersion(),s.schemaVersion(),s.canonicalizationVersion(),s.resultFingerprint(),pins,s.limitations()),m.pools(),m.candles(),m.transactions(),m.selectedOperations(),m.outsideOperations(),m.reconciliation(),m.interpretation(),m.fingerprint());
    }
    @Test void equivalentMapsSortUnsignedUtf8ButSourceArraysAndReceiptPositionRemainUnchanged() {
        var names=List.of("\uD800\uDC00","z","\uE000","a");var first=new LinkedHashMap<String,String>();for(var name:names)first.put(name,name);var second=new LinkedHashMap<String,String>();var reverse=new ArrayList<>(names);Collections.reverse(reverse);for(var name:reverse)second.put(name,name);
        var a=formatter.format(request(),Optional.empty(),Optional.of(withPins(metrics(""),first)));var b=formatter.format(request(),Optional.empty(),Optional.of(withPins(metrics(""),second)));assertThat(b).isEqualTo(a);
        var tree=JsonMapper.builder().build().readTree(a.json());assertThat(tree.path("metrics").path("result").path("source").path("pinnedSourceHashes").propertyNames()).containsExactly("a","z","\uE000","\uD800\uDC00");
        var path=tree.path("metrics").path("result").path("selectedOperations").get(0).path("position");assertThat(path.path("innerPath")).extracting(JsonNode::asText).containsExactly("2","11");
        assertThat(path.path("outerIndex").asText()).isEqualTo("10");assertThat(path.path("legOrdinal").asText()).isEqualTo("3");
        assertThat(tree.path("metrics").path("result").path("selectedOperations").get(0).path("receivedAt").asText()).isEqualTo(AT.toString());
        assertThat(tree.path("metrics").path("result").path("selectedOperations").get(0).has("sourceTime")).isFalse();
        var fine=new PumpSwapOperatorReport.Request(SESSION,new PumpSwapPoolMetrics.Request(KEY,AT,AT.plusSeconds(60),AT.plusSeconds(60).plusNanos(999),60));
        assertThat(formatter.format(fine,Optional.empty(),Optional.empty()).request()).isEqualTo(request());
    }
    PumpSwapPoolMetrics.Result outsidePadding(String padding) {
        var m=metrics("");var r=m.selectedOperations().getFirst();var outside=new PumpSwapPoolMetrics.OperationReference(r.canonicalKey(),r.revisionKey()+"-outside",r.transactionRevisionKey(),r.contentDigest(),r.chain(),r.signature(),r.position(),r.receivedAt().minusSeconds(60),r.variant(),padding,r.baseRawUnits(),r.quoteRawUnits(),r.baseDecimals(),r.quoteDecimals(),r.fees());
        return new PumpSwapPoolMetrics.Result(m.algorithmVersion(),m.canonicalizationVersion(),m.request(),m.source(),m.pools(),m.candles(),m.transactions(),m.selectedOperations(),List.of(outside),new PumpSwapPoolMetrics.Reconciliation(1,1,0,2,1,1,1,0,1,0,m.reconciliation().statuses()),m.interpretation(),m.fingerprint());
    }
    @Test void exactJsonUtf8LimitSucceedsButOneByteOverFailsWithoutDroppingOutsideReference() {
        var baseline=formatter.format(request(),Optional.empty(),Optional.of(outsidePadding("")));int bytes=baseline.json().getBytes(StandardCharsets.UTF_8).length;
        int remaining=64_000_000-bytes;var padding="雪".repeat(remaining/3)+"x".repeat(remaining%3);
        var atLimit=formatter.format(request(),Optional.empty(),Optional.of(outsidePadding(padding)));
        assertThat(atLimit.json().getBytes(StandardCharsets.UTF_8)).hasSize(64_000_000);assertThat(atLimit.json()).contains("outsideOperations","-outside");
        assertThatThrownBy(()->formatter.format(request(),Optional.empty(),Optional.of(outsidePadding(padding+"x")))).hasMessage("OPERATOR_REPORT_OUTPUT_LIMIT").hasNoCause();
    }
    @Test void exactEscapedSummaryLimitSucceedsButOneByteOverRejectsBothRenderings() {
        int baseline=formatter.format(request(),Optional.of(monitor("")),Optional.empty()).summary().getBytes(StandardCharsets.UTF_8).length;
        var padding="x".repeat(16_384-baseline);var report=formatter.format(request(),Optional.of(monitor(padding)),Optional.empty());
        assertThat(report.summary().getBytes(StandardCharsets.UTF_8)).hasSize(16_384);
        assertThatThrownBy(()->formatter.format(request(),Optional.of(monitor(padding+"x")),Optional.empty())).hasMessage("OPERATOR_REPORT_OUTPUT_LIMIT").hasNoCause();
        assertThatThrownBy(()->formatter.format(request(),Optional.of(monitor("\n".repeat(3000))),Optional.empty())).hasMessage("OPERATOR_REPORT_OUTPUT_LIMIT");
    }
    @Test void malformedUnicodeCannotLeakRenderingFailureDetails() {
        assertThatThrownBy(()->formatter.format(request(),Optional.empty(),Optional.of(metrics("\uD800SECRET_VALUE"))))
            .hasMessage("OPERATOR_REPORT_FORMAT_FAILED").hasNoCause().hasStackTraceContaining("OPERATOR_REPORT_FORMAT_FAILED");
    }
    @Test void everyRetainedStatusAndCatchUpSourceTimeSurviveWithoutChangingObservationBuckets() {
        var m=metrics("");var original=m.transactions().getFirst();
        var transactions=Arrays.stream(PumpSwapOperations.Status.values()).map(status->new PumpSwapPoolMetrics.TransactionDisposition(
            original.revisionKey(),original.contentDigest(),original.chain(),original.signature(),original.provider(),original.slot(),
            Optional.of(AT.minusSeconds(86400)),original.receivedAt(),original.admittedAt(),original.ingestedAt(),status,
            original.reason(),original.networkFee(),original.insideWindow(),original.operationRevisionKeys())).toList();
        var statuses=Arrays.stream(PumpSwapOperations.Status.values()).map(status->new PumpSwapPoolMetrics.StatusCount(status,1)).toList();
        var r=m.reconciliation();var complete=new PumpSwapPoolMetrics.Result(m.algorithmVersion(),m.canonicalizationVersion(),m.request(),m.source(),m.pools(),m.candles(),transactions,m.selectedOperations(),m.outsideOperations(),
            new PumpSwapPoolMetrics.Reconciliation(transactions.size(),transactions.size(),0,r.operationCount(),r.selectedOperations(),r.outsideOperations(),r.buys(),r.sells(),r.priceEligibleOperations(),r.unpricedOperations(),statuses),m.interpretation(),m.fingerprint());
        var report=formatter.format(request(),Optional.empty(),Optional.of(complete));var result=JsonMapper.builder().build().readTree(report.json()).path("metrics").path("result");
        assertThat(result.path("transactions")).extracting(tx->tx.path("status").asText()).containsExactly(Arrays.stream(PumpSwapOperations.Status.values()).map(Enum::name).toArray(String[]::new));
        for(var tx:result.path("transactions")) {
            assertThat(tx.path("sourceTime").asText()).isEqualTo(AT.minusSeconds(86400).toString());
            assertThat(tx.path("receivedAt").asText()).isEqualTo(AT.toString());
        }
        assertThat(result.path("candles").get(0).path("fromInclusive").asText()).isEqualTo(AT.toString());
        assertThat(report.summary()).contains("observation-time activity","DETERMINISTIC_NOT_ONCHAIN_CHRONOLOGY");
        for(var status:PumpSwapOperations.Status.values())assertThat(report.summary()).contains(status.name());
    }
}
