package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapRawOperationDecoderTest.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics;

class PumpSwapPoolMetricsTest {
    final PumpSwapPoolMetricsCalculator calculator = new PumpSwapPoolMetricsCalculator();
    final PumpSwapOperationCanonicalizer canonicalizer = new PumpSwapOperationCanonicalizer();
    @Test void exactGrossActivityAndReducedPriceAreDerivedFromOneRecordedOperation() {
        var result = new DecodePumpSwapOperationsUseCase().decode(new PumpSwapOperations.RecordedBatch(
                List.of(source(fixture("1", "buy", true, 1), "metrics-first-red")), List.of("GAPS_UNKNOWN")));
        var batch = stored(result);
        var received = result.transactions().getFirst().source().receivedAt();
        var start = Instant.ofEpochSecond(Math.floorDiv(received.getEpochSecond(), 60) * 60);
        var request = new PumpSwapPoolMetrics.Request(batch.batchKey(), start, start.plusSeconds(60), start.plusSeconds(60), 60);
        assertThatCode(() -> calculator.calculate(request, batch))
                .as("one persisted v4 operation must produce exact historical diagnostics, not an unsupported placeholder")
                .doesNotThrowAnyException();
        var metrics = calculator.calculate(request, batch);
        var op = result.operations().getFirst();
        assertThat(metrics.pools()).hasSize(1);
        assertThat(metrics.pools().getFirst().activity().baseRawUnits()).isEqualTo(new BigInteger("18446744073709551615"));
        assertThat(metrics.pools().getFirst().activity().buyCount()).isEqualTo(1);
        assertThat(metrics.candles().getFirst().ohlc().orElseThrow().open().value()).isEqualTo(
                new PumpSwapPoolMetrics.Rational(op.quoteAmount().multiply(BigInteger.TEN.pow(op.baseDecimals().orElseThrow())),
                        op.baseAmount().multiply(BigInteger.TEN.pow(op.quoteDecimals().orElseThrow()))));
        assertThat(metrics.reconciliation().selectedOperations()).isEqualTo(1);
    }
    PumpSwapOperationPersistence.StoredBatch stored(PumpSwapOperations.Result result) {
        var prepared = canonicalizer.prepare(result); var time = Instant.parse("2026-10-11T00:00:00Z");
        return new PumpSwapOperationPersistence.StoredBatch(PumpSwapOperationCanonicalizer.STORAGE_VERSION, prepared.key(), time, result,
                prepared.transactions().stream().map(t -> new PumpSwapOperationPersistence.TransactionRevision(t.key(),t.digest(),time,t.operationKeys())).toList(),
                prepared.operations().stream().map(o -> new PumpSwapOperationPersistence.OperationRevision(o.canonicalKey(),o.key(),o.transactionKey(),o.digest(),time)).toList());
    }
    PumpSwapOperations.Result original() {
        return new DecodePumpSwapOperationsUseCase().decode(new PumpSwapOperations.RecordedBatch(List.of(source(fixture("1","buy",true,1),"metrics-pure")),List.of("GAPS_UNKNOWN")));
    }
    PumpSwapOperations.Result operations(PumpSwapOperations.Result r,List<PumpSwapOperations.Operation> ops) {
        return new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),r.transactions(),ops,r.limitations(),r.fingerprint());
    }
    PumpSwapOperations.Operation op(PumpSwapOperations.Operation o,int outer,List<Integer> inner,int leg,String variant,String pool,String baseMint,String quoteMint,
            BigInteger base,BigInteger quote,Optional<Integer> bd,Optional<Integer> qd) {
        return new PumpSwapOperations.Operation(o.chain(),o.signature(),new PumpSwapOperations.Position(outer,inner,leg),variant,o.trader()+leg,pool,baseMint,quoteMint,base,quote,bd,qd,o.eventPath(),o.transferPaths(),o.lifecyclePaths(),o.eventDeclaredFees(),o.attributedFees(),o.eventFacts(),o.feeStatus(),o.feeReasons(),o.rawDigest(),o.eventDigest(),o.sourceReference());
    }
    PumpSwapPoolMetrics.Request request(PumpSwapOperationPersistence.StoredBatch b) {
        var t=b.result().transactions().getFirst().source().receivedAt();var start=Instant.ofEpochSecond(Math.floorDiv(t.getEpochSecond(),60)*60);
        return new PumpSwapPoolMetrics.Request(b.batchKey(),start,start.plusSeconds(60),start.plusSeconds(60),60);
    }
    @Test void extremelyClosePricesUseExactCrossProductsAndNumericPathsBreakTies() {
        var r=original();var o=r.operations().getFirst();var huge=BigInteger.TEN.pow(80);
        var ops=List.of(op(o,10,List.of(1,10),0,"sell",o.pool(),o.baseMint(),o.quoteMint(),huge,huge.add(BigInteger.ONE),Optional.of(0),Optional.of(0)),
            op(o,2,List.of(1,2),0,"buy",o.pool(),o.baseMint(),o.quoteMint(),huge,huge,Optional.of(0),Optional.of(0)),
            op(o,10,List.of(1,2),0,"buy_v2",o.pool(),o.baseMint(),o.quoteMint(),huge,huge,Optional.of(0),Optional.of(0)));
        var b=stored(operations(r,ops));var m=calculator.calculate(request(b),b);var price=m.candles().getFirst().ohlc().orElseThrow();
        assertThat(price.open().operation().position().outerIndex()).isEqualTo(2);
        assertThat(price.high().value()).isEqualTo(new PumpSwapPoolMetrics.Rational(huge.add(BigInteger.ONE),huge));
        assertThat(price.low().operation()).isEqualTo(price.open().operation());
        assertThat(price.close().operation().position().innerPath()).containsExactly(1,10);
        assertThat(m.pools().getFirst().activity().baseRawUnits()).isEqualTo(huge.multiply(BigInteger.valueOf(3)));
    }
    @Test void everyFixedVariantAndLegIsSeparateGrossActivityNotFeeAllocation() {
        var r=original();var o=r.operations().getFirst();var variants=List.of("buy","buy_exact_quote_in","buy_v2","buy_exact_quote_in_v2","sell","sell_v2");
        var ops=java.util.stream.IntStream.range(0,6).mapToObj(i->op(o,0,List.of(0),i,variants.get(i),o.pool(),o.baseMint(),o.quoteMint(),BigInteger.valueOf(7),BigInteger.valueOf(11),Optional.of(0),Optional.of(255))).toList();
        var b=stored(operations(r,ops));var m=calculator.calculate(request(b),b);var a=m.pools().getFirst().activity();
        assertThat(a.operationCount()).isEqualTo(6);assertThat(a.buyCount()).isEqualTo(4);assertThat(a.sellCount()).isEqualTo(2);
        assertThat(a.baseRawUnits()).isEqualTo(BigInteger.valueOf(42));assertThat(a.quoteRawUnits()).isEqualTo(BigInteger.valueOf(66));
        assertThat(m.candles().getFirst().ohlc().orElseThrow().open().value()).isEqualTo(new PumpSwapPoolMetrics.Rational(BigInteger.valueOf(11),BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(255))));
        assertThat(m.selectedOperations()).allSatisfy(ref->{assertThat(ref.fees().eventDeclared()).isEqualTo(o.eventDeclaredFees());assertThat(ref.fees().attributed()).isEqualTo(o.attributedFees());});
    }
    @Test void missingDecimalsKeepAmountsAndSparseUnpricedCandleWithoutBorrowingKnownDecimals() {
        var r=original();var o=r.operations().getFirst();var ops=new ArrayList<PumpSwapOperations.Operation>();
        ops.add(op(o,0,List.of(),0,"buy",o.pool(),o.baseMint(),o.quoteMint(),BigInteger.ONE,BigInteger.TWO,Optional.empty(),Optional.empty()));
        ops.add(op(o,1,List.of(),0,"sell",o.pool(),o.baseMint(),o.quoteMint(),BigInteger.ONE,BigInteger.TWO,Optional.empty(),Optional.of(9)));
        ops.add(op(o,2,List.of(),0,"buy",o.pool(),o.baseMint(),o.quoteMint(),BigInteger.ONE,BigInteger.TWO,Optional.of(6),Optional.empty()));
        var b=stored(operations(r,ops));var q=request(b);var m=calculator.calculate(new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive(),q.toExclusive().plusSeconds(600),q.toExclusive().plusSeconds(600),60),b);
        assertThat(m.candles()).hasSize(1);assertThat(m.candles().getFirst().ohlc()).isEmpty();
        var a=m.pools().getFirst().activity();assertThat(a.baseRawUnits()).isEqualTo(BigInteger.valueOf(3));assertThat(a.priceEligibleCount()).isZero();
        assertThat(a.unpricedOperations()).extracting(PumpSwapPoolMetrics.UnpricedOperation::reason).containsExactly("MISSING_BOTH_DECIMALS","MISSING_BASE_DECIMALS","MISSING_QUOTE_DECIMALS");
    }
    @Test void conflictingIncludedDecimalsFailClosed() {
        var r=original();var o=r.operations().getFirst();var b=stored(operations(r,List.of(o,op(o,1,List.of(),0,"sell",o.pool(),o.baseMint(),o.quoteMint(),BigInteger.ONE,BigInteger.TWO,Optional.of(o.baseDecimals().orElseThrow()+1),o.quoteDecimals()))));
        assertThatThrownBy(()->calculator.calculate(request(b),b)).hasMessage("METRICS_DECIMALS_CONFLICT");
    }
    @Test void componentTuplesNeverDelimiterCollideAndUnsignedUtf8OrderingIsExplicit() {
        var r=original();var o=r.operations().getFirst();var names=List.of("a/b","a","\uE000","\uD800\uDC00");var ops=java.util.stream.IntStream.range(0,4).mapToObj(i->op(o,i,List.of(),0,"buy",names.get(i),i==0?"c":"b/c","q",BigInteger.ONE,BigInteger.ONE,Optional.of(0),Optional.of(0))).toList();
        var b=stored(operations(r,ops));var m=calculator.calculate(request(b),b);
        assertThat(m.pools()).extracting(t->t.pool().pool()).containsExactly("a","a/b","\uE000","\uD800\uDC00");assertThat(m.pools()).hasSize(4);
    }
    @Test void requestValidationAndMicrosecondCutoffAreExact() {
        var b=stored(original());var q=request(b);
        assertThat(PumpSwapPoolMetricsCalculator.normalize(new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive(),q.toExclusive(),q.observationCutoff().plusNanos(999),60)).observationCutoff()).isEqualTo(q.observationCutoff());
        for(var bad:List.of(new PumpSwapPoolMetrics.Request("bad",q.fromInclusive(),q.toExclusive(),q.observationCutoff(),60),
            new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive().plusNanos(1),q.toExclusive(),q.observationCutoff(),60),
            new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive(),q.toExclusive(),q.observationCutoff().minusNanos(1),60),
            new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive(),q.toExclusive(),q.observationCutoff(),61),
            new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive(),q.fromInclusive().plusSeconds(60060),q.fromInclusive().plusSeconds(60060),60)))
            assertThatThrownBy(()->calculator.calculate(bad,b)).hasMessage("METRICS_INVALID_REQUEST");
        assertThat(calculator.calculate(new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive(),q.fromInclusive().plusSeconds(60000),q.fromInclusive().plusSeconds(60000),60),b)).isNotNull();
    }
    @Test void retryPermutationAndSourceRequestLimitationChangesHaveCorrectFingerprintIdentity() {
        var r=original();var o=r.operations().getFirst();var many=operations(r,List.of(o,op(o,10,List.of(1,2),0,"sell",o.pool(),o.baseMint(),o.quoteMint(),o.baseAmount(),o.quoteAmount(),o.baseDecimals(),o.quoteDecimals())));
        var b=stored(many);var q=request(b);var m=calculator.calculate(q,b);var rev=new ArrayList<>(many.operations());Collections.reverse(rev);
        var permuted=stored(operations(many,rev));
        // Persisted batch identity itself is fixed here; pure output ordering is independent of hydration order.
        permuted=new PumpSwapOperationPersistence.StoredBatch(b.storageVersion(),b.batchKey(),b.persistedAt(),permuted.result(),permuted.transactions(),permuted.operations());
        assertThat(calculator.calculate(q,permuted)).isEqualTo(m);assertThat(calculator.calculate(q,b)).isEqualTo(m);
        assertThat(calculator.calculate(new PumpSwapPoolMetrics.Request(q.batchKey(),q.fromInclusive(),q.toExclusive().plusSeconds(60),q.observationCutoff().plusSeconds(60),60),b).fingerprint()).isNotEqualTo(m.fingerprint());
        var changed=new PumpSwapOperations.Result(many.decoderVersion(),many.schemaVersion(),many.canonicalizationVersion(),many.pinnedSourceHashes(),many.transactions(),many.operations(),List.of("OTHER_LIMITATION"),many.fingerprint());
        var labels=stored(changed);
        assertThat(labels.batchKey()).isNotEqualTo(b.batchKey());
        assertThat(calculator.calculate(new PumpSwapPoolMetrics.Request(labels.batchKey(),q.fromInclusive(),q.toExclusive(),q.observationCutoff(),60),labels).fingerprint()).isNotEqualTo(m.fingerprint());
        assertThat(m.interpretation().signalReadiness()).isEqualTo("NOT_ESTABLISHED");assertThat(m.interpretation().coverage()).isEqualTo("INCOMPLETE");
        assertThatThrownBy(()->m.candles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void originalReceivedClockClosedWindowAndEveryStatusReconcileWithoutStatusPromotion() {
        var r=original();var tx=r.transactions().getFirst();var o=r.operations().getFirst();var from=Instant.parse("2026-10-11T00:00:00Z");
        var txs=new ArrayList<PumpSwapOperations.TransactionResult>();var ops=new ArrayList<PumpSwapOperations.Operation>();
        int i=0;for(var status:PumpSwapOperations.Status.values()) {
            var s=tx.source();var received=i==0?from.minusNanos(1000):i==1?from:i==2?from.plusSeconds(60):from.plusSeconds(30);
            var source=new PumpSwapOperations.RecordedTransaction(new UUID(0,i+1),s.chain(),"clock-status-"+i,s.provider(),s.slot(),s.finality(),s.captureDisposition(),"clock-ref-"+i,Optional.of(from.minusSeconds(99999)),received,received.plusSeconds(100),received.plusSeconds(200),s.rawResult(),s.rawDigest());
            txs.add(new PumpSwapOperations.TransactionResult(source,tx.transactionVersion(),status,"retained-"+status,tx.networkFee(),List.of()));
            if(status==PumpSwapOperations.Status.DECODED || status==PumpSwapOperations.Status.PARTIAL)
                ops.add(new PumpSwapOperations.Operation(source.chain(),source.signature(),o.position(),o.variant(),o.trader(),o.pool(),o.baseMint(),o.quoteMint(),o.baseAmount(),o.quoteAmount(),o.baseDecimals(),o.quoteDecimals(),o.eventPath(),o.transferPaths(),o.lifecyclePaths(),o.eventDeclaredFees(),o.attributedFees(),o.eventFacts(),o.feeStatus(),o.feeReasons(),source.rawDigest(),o.eventDigest(),source.sourceReference()));
            i++;
        }
        var all=new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),txs,ops,r.limitations(),r.fingerprint());var b=stored(all);
        var m=calculator.calculate(new PumpSwapPoolMetrics.Request(b.batchKey(),from,from.plusSeconds(60),from.plusSeconds(60),60),b);
        assertThat(m.reconciliation().transactionCount()).isEqualTo(7);assertThat(m.reconciliation().insideTransactions()).isEqualTo(5);assertThat(m.reconciliation().outsideTransactions()).isEqualTo(2);
        assertThat(m.reconciliation().operationCount()).isEqualTo(2);assertThat(m.reconciliation().selectedOperations()).isEqualTo(1);assertThat(m.reconciliation().outsideOperations()).isEqualTo(1);
        assertThat(m.reconciliation().statuses()).allSatisfy(s->assertThat(s.count()).isEqualTo(1));
        assertThat(m.transactions()).filteredOn(t->t.status()==PumpSwapOperations.Status.PARTIAL).singleElement().satisfies(t->{assertThat(t.status()).isEqualTo(PumpSwapOperations.Status.PARTIAL);assertThat(t.insideWindow()).isTrue();});
        assertThat(m.selectedOperations().getFirst().receivedAt()).isEqualTo(from);
        var reverse=new ArrayList<>(all.transactions());Collections.reverse(reverse);
        var reordered=new PumpSwapOperations.Result(all.decoderVersion(),all.schemaVersion(),all.canonicalizationVersion(),all.pinnedSourceHashes(),reverse,all.operations(),all.limitations(),all.fingerprint());
        assertThat(calculator.calculate(m.request(),new PumpSwapOperationPersistence.StoredBatch(b.storageVersion(),b.batchKey(),b.persistedAt(),reordered,b.transactions(),b.operations())))
            .as("parents are resolved by exact revision membership, never zipped against independently ordered source transactions").isEqualTo(m);
    }
    @Test void canonicalAmbiguityRejectsEntireBatchBeforeWindowFilteringEvenEqualAmounts() {
        var r=original();var s=r.transactions().getFirst().source();
        var other=new PumpSwapOperations.RecordedTransaction(new UUID(1,1),s.chain(),s.signature(),"other-provider",s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),s.sourceTime(),s.receivedAt().plusSeconds(600),s.admittedAt().plusSeconds(600),s.ingestedAt().plusSeconds(600),s.rawResult(),s.rawDigest());
        var both=new DecodePumpSwapOperationsUseCase().decode(new PumpSwapOperations.RecordedBatch(List.of(s,other),List.of()));var b=stored(both);
        assertThatThrownBy(()->calculator.calculate(request(b),b)).hasMessage("METRICS_AMBIGUOUS_OPERATION_REVISION");
    }
    @Test void transactionAndOperationBoundsRejectBeforePartialMaterialization() {
        var r=original();var o=r.operations().getFirst();var b=stored(r);
        var many=java.util.stream.IntStream.range(0,10000).mapToObj(i->op(o,i,List.of(),0,"buy",o.pool(),o.baseMint(),o.quoteMint(),BigInteger.ONE,BigInteger.ONE,Optional.empty(),Optional.empty())).toList();
        var atLimit=stored(operations(r,many));assertThat(calculator.calculate(request(atLimit),atLimit).reconciliation().operationCount()).isEqualTo(10000);
        var overflow=new ArrayList<>(many);overflow.add(op(o,10000,List.of(),0,"buy",o.pool(),o.baseMint(),o.quoteMint(),BigInteger.ONE,BigInteger.ONE,Optional.empty(),Optional.empty()));
        var above=new PumpSwapOperationPersistence.StoredBatch(b.storageVersion(),b.batchKey(),b.persistedAt(),operations(r,overflow),b.transactions(),b.operations());
        assertThatThrownBy(()->calculator.calculate(request(b),above)).hasMessage("METRICS_LIMIT");
        var source=r.transactions().getFirst().source();var txs=new ArrayList<PumpSwapOperations.TransactionResult>();
        for(int i=0;i<128;i++){var s=new PumpSwapOperations.RecordedTransaction(new UUID(0,i),source.chain(),"bounded-"+i,source.provider(),source.slot(),source.finality(),source.captureDisposition(),"bounded-"+i,source.sourceTime(),source.receivedAt(),source.admittedAt(),source.ingestedAt(),source.rawResult(),source.rawDigest());txs.add(new PumpSwapOperations.TransactionResult(s,"1",PumpSwapOperations.Status.FAILED,"failed",Optional.empty(),List.of()));}
        var exact=new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),txs,List.of(),r.limitations(),r.fingerprint());var bounded=stored(exact);
        assertThat(calculator.calculate(request(bounded),bounded).reconciliation().transactionCount()).isEqualTo(128);
        txs.add(txs.getFirst());var excessive=new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),txs,List.of(),r.limitations(),r.fingerprint());
        assertThatThrownBy(()->calculator.calculate(request(b),new PumpSwapOperationPersistence.StoredBatch(b.storageVersion(),b.batchKey(),b.persistedAt(),excessive,b.transactions(),List.of()))).hasMessage("METRICS_LIMIT");
    }
    @Test void actualCanonicalOutputUtf8BytesAreBoundedAndSourceVersionsFailClosed() {
        var r=original();var b=stored(r);var huge="x".repeat(64_000_000);
        var expanded=new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),r.transactions(),r.operations(),List.of(huge),r.fingerprint());
        assertThatThrownBy(()->calculator.calculate(request(b),new PumpSwapOperationPersistence.StoredBatch(b.storageVersion(),b.batchKey(),b.persistedAt(),expanded,b.transactions(),b.operations()))).hasMessage("METRICS_OUTPUT_LIMIT");
        var unsupported=new PumpSwapOperations.Result("future",r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),r.transactions(),r.operations(),r.limitations(),r.fingerprint());
        assertThatThrownBy(()->calculator.calculate(request(b),new PumpSwapOperationPersistence.StoredBatch(b.storageVersion(),b.batchKey(),b.persistedAt(),unsupported,b.transactions(),b.operations()))).hasMessage("METRICS_UNSUPPORTED_SOURCE_VERSION");
    }
    @Test void receiptAtStartIsIncludedButEndAndEarlierReceiptStayOutsideRegardlessOfOtherClocks() {
        var r=original();var old=r.transactions().getFirst();var o=r.operations().getFirst();var from=Instant.parse("2026-10-11T00:00:00Z");
        var times=List.of(from.minusNanos(1000),from,from.plusSeconds(60));var txs=new ArrayList<PumpSwapOperations.TransactionResult>();var ops=new ArrayList<PumpSwapOperations.Operation>();
        for(int i=0;i<times.size();i++) {
            var s=old.source();var t=times.get(i);var parent=new PumpSwapOperations.RecordedTransaction(new UUID(4,i),s.chain(),"boundary-"+i,s.provider(),s.slot(),s.finality(),s.captureDisposition(),"boundary-ref-"+i,Optional.of(from.plusSeconds(1)),t,from.plusSeconds(600),from.plusSeconds(1200),s.rawResult(),s.rawDigest());
            txs.add(new PumpSwapOperations.TransactionResult(parent,"1",PumpSwapOperations.Status.DECODED,"same",Optional.empty(),List.of()));
            ops.add(new PumpSwapOperations.Operation(parent.chain(),parent.signature(),o.position(),o.variant(),o.trader(),o.pool(),o.baseMint(),o.quoteMint(),o.baseAmount(),o.quoteAmount(),o.baseDecimals(),o.quoteDecimals(),o.eventPath(),o.transferPaths(),o.lifecyclePaths(),o.eventDeclaredFees(),o.attributedFees(),o.eventFacts(),o.feeStatus(),o.feeReasons(),parent.rawDigest(),o.eventDigest(),parent.sourceReference()));
        }
        var data=new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),txs,ops,r.limitations(),r.fingerprint());var b=stored(data);
        var m=calculator.calculate(new PumpSwapPoolMetrics.Request(b.batchKey(),from,from.plusSeconds(60),from.plusSeconds(60),60),b);
        assertThat(m.selectedOperations()).extracting(PumpSwapPoolMetrics.OperationReference::signature).containsExactly("boundary-1");
        assertThat(m.outsideOperations()).extracting(PumpSwapPoolMetrics.OperationReference::signature).containsExactly("boundary-0","boundary-2");
        assertThat(m.candles().getFirst().fromInclusive()).isEqualTo(from);assertThat(m.candles().getFirst().toExclusive()).isEqualTo(from.plusSeconds(60));
    }
}
