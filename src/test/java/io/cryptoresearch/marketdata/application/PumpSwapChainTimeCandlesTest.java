package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.api.PumpSwapChainTimeCandles.WSOL;
import static io.cryptoresearch.marketdata.application.PumpSwapRawOperationDecoderTest.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.marketdata.api.PumpSwapChainTimeCandles.*;

class PumpSwapChainTimeCandlesTest {
    static final Instant FROM=Instant.parse("2026-10-10T06:00:00Z"), PUBLISHED=Instant.parse("2026-10-11T00:00:00Z");
    final PumpSwapChainTimeCandleCalculator calculator=new PumpSwapChainTimeCandleCalculator();
    static PumpSwapOperations.RecordedTransaction nativeSource(String signature,Instant event,Long index,long slot) {
        var raw=fixture("1","buy",true,1);var roles=roles("buy",true,0);
        var keys=(List<String>)message(raw).get("accountKeys");keys.set(keys.indexOf(roles.get(4)),WSOL);
        for(var side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)meta(raw).get(side))
            if(row.get("mint").equals(roles.get(4)))row.put("mint",WSOL);
        raw.put("slot",slot);raw.put("blockTime",event==null?null:event.getEpochSecond());if(index!=null)raw.put("transactionIndex",index);
        var s=source(raw,signature);
        return new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),s.provider(),slot,s.finality(),s.captureDisposition(),s.sourceReference(),Optional.ofNullable(event),FROM.plusSeconds(900),FROM.plusSeconds(901),FROM.plusSeconds(902),s.rawResult(),s.rawDigest());
    }
    static PumpSwapOperationPersistence.StoredBatch stored(List<PumpSwapOperations.RecordedTransaction> sources) {
        return stored(new DecodePumpSwapOperationsUseCase().decode(new PumpSwapOperations.RecordedBatch(sources,List.of("GAPS_UNKNOWN"))));
    }
    static PumpSwapOperationPersistence.StoredBatch stored(PumpSwapOperations.Result result) {
        var p=new PumpSwapOperationCanonicalizer().prepare(result);
        return new PumpSwapOperationPersistence.StoredBatch("pumpswap-storage-v1",p.key(),PUBLISHED,result,
            p.transactions().stream().map(t->new PumpSwapOperationPersistence.TransactionRevision(t.key(),t.digest(),PUBLISHED.minusSeconds(2),t.operationKeys())).toList(),
            p.operations().stream().map(o->new PumpSwapOperationPersistence.OperationRevision(o.canonicalKey(),o.key(),o.transactionKey(),o.digest(),PUBLISHED.minusSeconds(1))).toList());
    }
    static Request request(PumpSwapOperationPersistence.StoredBatch b) { return new Request(b.batchKey(),FROM,FROM.plusSeconds(600),PUBLISHED); }
    @Test void rawBlockTimeProducesNormalizedNativePriceAndRetainsEveryClock() {
        var b=stored(List.of(nativeSource("chain-first",FROM.plusSeconds(1),4L,100)));var q=request(b);
        assertThatCode(()->calculator.calculate(q,b)).as("supported v4 source must produce event candles").doesNotThrowAnyException();
        var result=calculator.calculate(q,b);assertThat(result.candles()).hasSize(1);
        var c=result.candles().getFirst();assertThat(c.fromInclusive()).isEqualTo(FROM);
        var op=b.result().operations().getFirst();var ref=result.operations().getFirst();
        assertThat(c.ohlc().orElseThrow().close().value()).isEqualTo(new PumpSwapPoolMetrics.Rational(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(6)),op.baseAmount().multiply(BigInteger.TEN.pow(9))));
        assertThat(c.activity().quoteSolVolume()).contains(new java.math.BigDecimal("0.000000100"));
        assertThat(ref.clocks().knownAt()).isEqualTo(PUBLISHED);assertThat(ref.clocks().receivedAt()).isEqualTo(FROM.plusSeconds(900));
        assertThat(ref.clocks().transactionPersistedAt()).isEqualTo(PUBLISHED.minusSeconds(2));
        assertThat(ref.clocks().operationPersistedAt()).isEqualTo(PUBLISHED.minusSeconds(1));
        assertThat(result.reconciliation().includedOperations()).isEqualTo(1);
        assertThat(result.interpretation().readiness()).isEqualTo("DIAGNOSTIC_ONLY_NOT_ADMITTED");
    }
    @Test void knowledgeBeforeAnyOfSixAvailabilityClocksExcludesRatherThanBackdates() {
        var b=stored(List.of(nativeSource("chain-late",FROM.plusSeconds(1),4L,100)));
        var r=calculator.calculate(new Request(b.batchKey(),FROM,FROM.plusSeconds(600),PUBLISHED.minusNanos(1000)),b);
        assertThat(r.candles()).isEmpty();assertThat(r.operations().getFirst().disposition()).isEqualTo("FUTURE_KNOWLEDGE");
        assertThat(r.reconciliation().excludedOperations()).isEqualTo(1);
        assertThat(calculator.calculate(new Request(b.batchKey(),FROM,FROM.plusSeconds(600),PUBLISHED.plusNanos(999)),b)).isEqualTo(calculator.calculate(request(b),b));
    }
    @Test void rawMissingTimeAndMissingIndexRemainObservableAndSparse() {
        var b=stored(List.of(nativeSource("missing-time",null,4L,100),nativeSource("missing-index",FROM.plusSeconds(1),null,101)));
        var r=calculator.calculate(request(b),b);assertThat(r.candles()).hasSize(1);
        assertThat(r.operations()).extracting(OperationReference::disposition).contains("MISSING_EVENT_TIME","INCLUDED");
        assertThat(r.candles().getFirst().activity().missingIndexCount()).isEqualTo(1);
        assertThat(r.operations()).filteredOn(o->o.signature().equals("missing-index")).singleElement().satisfies(o->assertThat(o.transactionIndex()).isEmpty());
    }
    @Test void numericSlotAndProviderIndexDetermineEndpointsRegardlessOfSignatureOrHydrationOrder() {
        var sources=List.of(nativeSource("a",FROM.plusSeconds(1),10L,10),nativeSource("z",FROM.plusSeconds(1),2L,2),nativeSource("b",FROM.plusSeconds(1),2L,10));
        var b=stored(sources);var r=calculator.calculate(request(b),b);
        assertThat(r.operations()).extracting(OperationReference::signature).containsExactly("z","b","a");
        var reversed=new ArrayList<>(b.result().transactions());Collections.reverse(reversed);
        var old=b.result();var changed=new PumpSwapOperations.Result(old.decoderVersion(),old.schemaVersion(),old.canonicalizationVersion(),old.pinnedSourceHashes(),reversed,old.operations(),old.limitations(),old.fingerprint());
        assertThat(calculator.calculate(request(b),new PumpSwapOperationPersistence.StoredBatch(b.storageVersion(),b.batchKey(),b.persistedAt(),changed,b.transactions(),b.operations()))).isEqualTo(r);
        assertThat(r.candles().getFirst().ohlc().orElseThrow().high().operation().signature()).isEqualTo("z");
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","9223372036854775808","\"4\""})
    void malformedSuppliedIndexFailsWholeBatchBeforeFiltering(String token) {
        var s=nativeSource("bad-index",FROM.minusSeconds(600),4L,100);var text=s.rawResult().replace("\"transactionIndex\":4","\"transactionIndex\":"+token);
        var bad=withRaw(s,text,s.sourceTime());var b=stored(List.of(bad));
        assertThatThrownBy(()->calculator.calculate(request(b),b)).hasMessage("CHAIN_TIME_INVALID_INDEX");
    }
    @Test void contradictorySlotIndexAndSourceTimeRejectWholeBatch() {
        var b=stored(List.of(nativeSource("one",FROM.plusSeconds(1),4L,100),nativeSource("two",FROM.minusSeconds(600),4L,100)));
        assertThatThrownBy(()->calculator.calculate(request(b),b)).hasMessage("CHAIN_TIME_POSITION_CONFLICT");
        var s=nativeSource("mismatch",FROM.plusSeconds(1),4L,100);var wrong=withRaw(s,s.rawResult(),Optional.of(FROM.plusSeconds(2)));var mismatch=stored(List.of(wrong));
        assertThatThrownBy(()->calculator.calculate(request(mismatch),mismatch)).hasMessage("CHAIN_TIME_SOURCE_TIME_MISMATCH");
    }
    @Test void eventWindowStartsInclusiveEndsExclusiveAndDoesNotCarryPrices() {
        var b=stored(List.of(nativeSource("before",FROM.minusSeconds(1),1L,99),nativeSource("start",FROM,2L,100),nativeSource("end",FROM.plusSeconds(600),3L,101)));
        var r=calculator.calculate(request(b),b);assertThat(r.candles()).hasSize(1);assertThat(r.reconciliation().includedOperations()).isEqualTo(1);
        assertThat(r.operations()).filteredOn(o->o.disposition().equals("INCLUDED")).extracting(OperationReference::signature).containsExactly("start");
        assertThat(r.reconciliation().transactionCount()).isEqualTo(3);
    }
    @Test void invalidRequestsAndUnsupportedSourceFailExplicitly() {
        var b=stored(List.of(nativeSource("request",FROM,1L,100)));var q=request(b);
        for(var bad:List.of(new Request("bad",FROM,FROM.plusSeconds(600),PUBLISHED),new Request(b.batchKey(),FROM.plusNanos(1000),FROM.plusSeconds(600),PUBLISHED),new Request(b.batchKey(),FROM,FROM.plusSeconds(300300),PUBLISHED)))
            assertThatThrownBy(()->calculator.calculate(bad,b)).hasMessage("CHAIN_TIME_INVALID_REQUEST");
        assertThatThrownBy(()->calculator.calculate(q,new PumpSwapOperationPersistence.StoredBatch("future",b.batchKey(),b.persistedAt(),b.result(),b.transactions(),b.operations()))).hasMessage("CHAIN_TIME_UNSUPPORTED_SOURCE_VERSION");
    }
    static PumpSwapOperations.RecordedTransaction withRaw(PumpSwapOperations.RecordedTransaction s,String raw,Optional<Instant> time) {
        return new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),s.provider(),s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),time,s.receivedAt(),s.admittedAt(),s.ingestedAt(),raw,hash(raw));
    }
    @Test void missingDecimalsNeverBorrowRawMetadataAndOtherQuoteNeverBecomesSol() {
        var b=stored(List.of(nativeSource("metadata",FROM.plusSeconds(1),1L,100)));var r=b.result();var o=r.operations().getFirst();
        var missing=withDecimals(o,Optional.empty(),Optional.empty(),o.quoteMint());
        var absent=stored(withOperations(r,List.of(missing)));var result=calculator.calculate(request(absent),absent);
        assertThat(result.candles().getFirst().ohlc()).isEmpty();assertThat(result.candles().getFirst().activity().quoteSolVolume()).isEmpty();
        assertThat(result.operations().getFirst().priceReason()).isEqualTo("MISSING_BOTH_DECIMALS");assertThat(result.candles().getFirst().activity().baseRawUnits()).isEqualTo(o.baseAmount());
        var foreign=stored(withOperations(r,List.of(withDecimals(o,o.baseDecimals(),o.quoteDecimals(),"other-quote"))));
        assertThat(calculator.calculate(request(foreign),foreign).operations().getFirst().priceReason()).isEqualTo("UNSUPPORTED_QUOTE");
    }
    @Test void contradictoryWsolDecimalsAndRawMetadataRejectInsteadOfAssumingNine() {
        var b=stored(List.of(nativeSource("wrong-decimals",FROM.plusSeconds(1),1L,100)));var r=b.result();var o=r.operations().getFirst();
        var bad=stored(withOperations(r,List.of(withDecimals(o,o.baseDecimals(),Optional.of(8),o.quoteMint()))));
        assertThatThrownBy(()->calculator.calculate(request(bad),bad)).hasMessage("CHAIN_TIME_DECIMALS_CONFLICT");
        var wrong=stored(withOperations(r,List.of(withDecimals(o,Optional.of(5),o.quoteDecimals(),o.quoteMint()))));
        assertThatThrownBy(()->calculator.calculate(request(wrong),wrong)).hasMessage("CHAIN_TIME_DECIMALS_CONFLICT");
    }
    @Test void eachRawOrRevisionClockIndependentlyControlsKnowledgeEligibility() {
        var b=stored(List.of(nativeSource("six-clocks",FROM.plusSeconds(1),1L,100)));var r=b.result();var s=r.transactions().getFirst().source();var late=PUBLISHED.plusSeconds(1);
        for(int clock=0;clock<6;clock++) {
            var source=new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),s.provider(),s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),s.sourceTime(),clock==0?late:s.receivedAt(),clock<=1?late:s.admittedAt(),clock<=2?late:s.ingestedAt(),s.rawResult(),s.rawDigest());
            var modified=stored(List.of(source));var tx=modified.transactions().getFirst();var op=modified.operations().getFirst();
            modified=new PumpSwapOperationPersistence.StoredBatch(modified.storageVersion(),modified.batchKey(),clock==5?late:PUBLISHED,modified.result(),List.of(new PumpSwapOperationPersistence.TransactionRevision(tx.revisionKey(),tx.contentDigest(),clock==3?late:tx.persistedAt(),tx.operationRevisionKeys())),List.of(new PumpSwapOperationPersistence.OperationRevision(op.canonicalKey(),op.revisionKey(),op.transactionRevisionKey(),op.contentDigest(),clock==4?late:op.persistedAt())));
            var result=calculator.calculate(request(modified),modified);assertThat(result.reconciliation().includedOperations()).as("clock %s",clock).isZero();assertThat(result.operations().getFirst().clocks().knownAt()).isEqualTo(late);
        }
    }
    static PumpSwapOperations.Result withOperations(PumpSwapOperations.Result r,List<PumpSwapOperations.Operation> ops) {
        return new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),r.transactions(),ops,r.limitations(),r.fingerprint());
    }
    static PumpSwapOperations.Operation withDecimals(PumpSwapOperations.Operation o,Optional<Integer> bd,Optional<Integer> qd,String quote) {
        return new PumpSwapOperations.Operation(o.chain(),o.signature(),o.position(),o.variant(),o.trader(),o.pool(),o.baseMint(),quote,o.baseAmount(),o.quoteAmount(),bd,qd,o.eventPath(),o.transferPaths(),o.lifecyclePaths(),o.eventDeclaredFees(),o.attributedFees(),o.eventFacts(),o.feeStatus(),o.feeReasons(),o.rawDigest(),o.eventDigest(),o.sourceReference());
    }
    @Test void malformedTimeDuplicateJsonAndRawDigestCannotBecomeMissingTime() {
        var s=nativeSource("bad-time",FROM,4L,100);
        for(String token:List.of("1.5","\"1791612000\"","9223372036854775808")) {
            var text=s.rawResult().replace("\"blockTime\":"+FROM.getEpochSecond(),"\"blockTime\":"+token);var b=stored(List.of(withRaw(s,text,Optional.empty())));
            assertThatThrownBy(()->calculator.calculate(request(b),b)).hasMessage("CHAIN_TIME_INVALID_EVENT_TIME");
        }
        var text=s.rawResult().replaceFirst("\\{","{\"blockTime\":1,");
        // Duplicate JSON is preserved in an INVALID decoder disposition, then rejected by this view.
        var duplicate=stored(List.of(withRaw(s,text,Optional.empty())));
        assertThatThrownBy(()->calculator.calculate(request(duplicate),duplicate)).hasMessage("CHAIN_TIME_INVALID_RAW_JSON");
    }
    @Test void actualSourceDerivedExtremeDecimalsAndVeryClosePricesRemainExact() {
        var a=amountSource("exact-a",FROM.plusSeconds(1),1L,100,BigInteger.valueOf(1_000_000),BigInteger.valueOf(100),255);
        var b=amountSource("exact-b",FROM.plusSeconds(2),2L,101,BigInteger.valueOf(1_000_000),BigInteger.valueOf(101),255);
        var batch=stored(List.of(a,b));assertThat(batch.result().operations()).hasSize(2);
        var result=calculator.calculate(request(batch),batch);var prices=result.candles().getFirst().ohlc().orElseThrow();
        assertThat(prices.open().value()).isEqualTo(new PumpSwapPoolMetrics.Rational(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(255)),BigInteger.valueOf(1_000_000).multiply(BigInteger.TEN.pow(9))));
        assertThat(prices.high().operation().signature()).isEqualTo("exact-b");assertThat(prices.low().operation().signature()).isEqualTo("exact-a");
        assertThat(result.candles().getFirst().activity().baseRawUnits()).isEqualTo(BigInteger.valueOf(2_000_000));
    }
    @Test void canonicalAmbiguityAndSourceBoundsRejectBeforeWindowFiltering() {
        var a=nativeSource("ambiguous",FROM.plusSeconds(1),1L,100);
        var other=new PumpSwapOperations.RecordedTransaction(new UUID(1,1),a.chain(),a.signature(),"other-provider",a.slot(),a.finality(),a.captureDisposition(),a.sourceReference(),a.sourceTime(),a.receivedAt(),a.admittedAt(),a.ingestedAt(),a.rawResult(),a.rawDigest());
        var b=stored(List.of(a,other));assertThatThrownBy(()->calculator.calculate(request(b),b)).hasMessage("CHAIN_TIME_AMBIGUOUS_OPERATION_REVISION");
        var one=stored(List.of(a));var r=one.result();var txs=new ArrayList<>(r.transactions());while(txs.size()<129)txs.add(txs.getFirst());
        var tooMany=new PumpSwapOperations.Result(r.decoderVersion(),r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),txs,r.operations(),r.limitations(),r.fingerprint());
        assertThatThrownBy(()->calculator.calculate(request(one),new PumpSwapOperationPersistence.StoredBatch(one.storageVersion(),one.batchKey(),one.persistedAt(),tooMany,one.transactions(),one.operations()))).hasMessage("CHAIN_TIME_LIMIT");
        assertThatThrownBy(()->calculator.calculate(new Request(one.batchKey(),FROM,FROM.plusSeconds(300300),K()),one)).hasMessage("CHAIN_TIME_INVALID_REQUEST");
    }
    static Instant K(){return Instant.parse("2030-01-01T00:00:00Z");}
    static PumpSwapOperations.RecordedTransaction amountSource(String signature,Instant event,Long index,long slot,BigInteger base,BigInteger quote,int baseDecimals) {
        var original=nativeSource(signature,event,index,slot);var raw=(Map<String,Object>)JSON.readValue(original.rawResult(),Map.class);
        var transfers=inner(raw,0);transfers.get(0).put("data",base58(checked(base,baseDecimals)));transfers.get(1).put("data",base58(checked(quote,9)));
        var bytes=unbase58((String)transfers.get(2).get("data"));putU64(bytes,24,base);for(int offset:List.of(72,112,120))putU64(bytes,offset,quote);transfers.get(2).put("data",base58(bytes));
        for(var side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)meta(raw).get(side))if(!row.get("mint").equals(WSOL)){var ui=new HashMap<>((Map<String,Object>)row.get("uiTokenAmount"));ui.put("decimals",baseDecimals);row.put("uiTokenAmount",ui);}
        ordinarySyntheticBalances(raw);return withRaw(original,JSON.writeValueAsString(raw),original.sourceTime());
    }
}
