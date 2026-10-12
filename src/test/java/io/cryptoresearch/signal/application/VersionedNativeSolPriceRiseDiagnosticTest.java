package io.cryptoresearch.signal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.PumpSwapVersionedNativePriceSignal.*;

class VersionedNativeSolPriceRiseDiagnosticTest {
    static final Instant E=Instant.parse("2026-10-11T11:25:00Z"),D=Instant.parse("2026-10-12T14:00:00Z"),K=Instant.parse("2030-01-01T00:00:00Z");
    static final String KEY="sha256:"+"1".repeat(64), WSOL="So11111111111111111111111111111111111111112";
    static final PumpSwapPoolLegs.PoolBinding BINDING=new PumpSwapPoolLegs.PoolBinding("5iSVs31h7HMdGGmyUvzmHDsYwV7cydD6CZJTbqzCtLii","wSqnGS6eb9AdNiGJVstrgkh1Am1oA35AovnPrCg61FL","9Dx7EA7h6Bp2YmiEDVXtJvYrArtEWoBe5qPr3sgW6R2W","BsbsB3WLq7vbY5En3MBCAsTrcwaCxNKY2Mp5pGuLpump",WSOL,"TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb","TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    static final PumpSwapPoolMetrics.Pool POOL=new PumpSwapPoolMetrics.Pool("solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp",BINDING.pool(),BINDING.baseMint(),WSOL);
    final MarketDataApi api=mock(MarketDataApi.class);final VersionedNativeSolPriceRiseDiagnostic diagnostic=new VersionedNativeSolPriceRiseDiagnostic(api);
    @ParameterizedTest @CsvSource({"105,100,TRIGGER","104999999999999999999,100000000000000000000,NO_TRIGGER","105000000000000000001,100000000000000000000,TRIGGER"})
    void sameExactBoundaryAsOldDiagnosticButDifferentSourceVersion(String n,String d,Status expected) {
        var report=report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,0,0),candle(E.minusSeconds(300),new BigInteger(n),new BigInteger(d),0,0)),List.of());
        when(api.pumpSwapVersionedPoolCandles(any())).thenReturn(report);
        var result=diagnostic.diagnose(new Request(source(),POOL,E));assertThat(result.status()).isEqualTo(expected);
        assertThat(result.analysis()).isEqualTo("RETROSPECTIVE_AT_K");assertThat(result.historicalAtEventEnd()).isEqualTo("UNAVAILABLE");
        assertThat(result.algorithmVersion()).isEqualTo("native-sol-pool-net-price-rise-v1");
        var oldApi=mock(MarketDataApi.class);
        var old=NativeSolPriceRiseDiagnosticTest.report(List.of(NativeSolPriceRiseDiagnosticTest.candle(NativeSolPriceRiseDiagnosticTest.E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,0,0),NativeSolPriceRiseDiagnosticTest.candle(NativeSolPriceRiseDiagnosticTest.E.minusSeconds(300),new BigInteger(n),new BigInteger(d),0,0)),List.of());
        when(oldApi.pumpSwapChainTimeCandles(any())).thenReturn(Optional.of(old));
        assertThat(new NativeSolPriceRiseDiagnostic(oldApi).diagnose(NativeSolPriceRiseDiagnosticTest.request()).status().name()).isEqualTo(result.status().name());
    }
    @ParameterizedTest @ValueSource(strings={"UNRESOLVED_POOL_EVIDENCE","MISSING_EVENT_TIME","FUTURE_KNOWLEDGE","POOL_MEMBERSHIP_UNKNOWN"})
    void possibleUnknownCloseCannotBecomeConfidentSignal(String reason) {
        when(api.pumpSwapVersionedPoolCandles(any())).thenReturn(report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,0,0),candle(E.minusSeconds(300),BigInteger.TWO,BigInteger.ONE,0,0)),List.of(new PumpSwapVersionedPoolCandles.Uncertainty(POOL,reason.equals("MISSING_EVENT_TIME")?Optional.empty():Optional.of(E.minusSeconds(30)),"unresolved",reason))));
        var result=diagnostic.diagnose(new Request(source(),POOL,E));assertThat(result.status()).isEqualTo(Status.INSUFFICIENT);assertThat(result.reasons()).contains(reason);
    }
    @ParameterizedTest @CsvSource({"1,0,UNPRICED_OPERATION","0,1,MISSING_TRANSACTION_INDEX"})
    void missingPriceOrOrderIsInsufficient(int unpriced,int index,String reason) {
        when(api.pumpSwapVersionedPoolCandles(any())).thenReturn(report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,unpriced,index),candle(E.minusSeconds(300),BigInteger.TWO,BigInteger.ONE,0,0)),List.of()));
        var result=diagnostic.diagnose(new Request(source(),POOL,E));assertThat(result.status()).isEqualTo(Status.INSUFFICIENT);assertThat(result.reasons()).contains(reason);
    }
    @Test void absentCurrentBucketIsInsufficientNotNegative() {
        when(api.pumpSwapVersionedPoolCandles(any())).thenReturn(report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,0,0)),List.of()));
        assertThat(diagnostic.diagnose(new Request(source(),POOL,E)).reasons()).contains("MISSING_CURRENT_BUCKET");
    }
    @Test void invalidEndRejectsBeforeModuleRead() {
        assertThatThrownBy(()->diagnostic.diagnose(new Request(source(),POOL,E.plusSeconds(1)))).hasMessage("VERSIONED_NATIVE_INVALID_REQUEST");verifyNoInteractions(api);
    }
    @Test void foreignReturnedRequestCannotSubstituteForExactSource() {
        var r=report(List.of(),List.of());
        var wrong=new PumpSwapVersionedPoolCandles.Result(r.algorithmVersion(),r.canonicalizationVersion(),new PumpSwapVersionedPoolCandles.Descriptor("v4",r.request().pools(),E.minusSeconds(600),E,D,K),KEY,Map.of(),List.of(),List.of(),List.of(),List.of(),r.reconciliation(),List.of(),KEY);
        when(api.pumpSwapVersionedPoolCandles(any())).thenReturn(wrong);
        assertThatThrownBy(()->diagnostic.diagnose(new Request(source(),POOL,E))).hasMessage("VERSIONED_NATIVE_SOURCE_MISMATCH");
    }
    static PumpSwapVersionedPoolCandles.Request source(){return new PumpSwapVersionedPoolCandles.Request(new PumpSwapOperations.RecordedBatch(List.of(),List.of()),List.of(BINDING),"pumpswap-pool-legs-v5",E.minusSeconds(600),E,D,K);}
    static PumpSwapVersionedPoolCandles.Candle candle(Instant start,BigInteger n,BigInteger d,int unpriced,int index){var p=new PumpSwapVersionedPoolCandles.Price(new PumpSwapPoolMetrics.Rational(n,d),"operation");return new PumpSwapVersionedPoolCandles.Candle(POOL,start,start.plusSeconds(300),1+unpriced,1,0,1,unpriced,index,BigInteger.ONE,Optional.of(BigInteger.ONE),List.of("operation"),Optional.of(new PumpSwapVersionedPoolCandles.Ohlc(p,p,p,p)));}
    static PumpSwapVersionedPoolCandles.Result report(List<PumpSwapVersionedPoolCandles.Candle> candles,List<PumpSwapVersionedPoolCandles.Uncertainty> uncertainty){var s=source();return new PumpSwapVersionedPoolCandles.Result("pumpswap-pool-net-candles-v1","length-prefixed-v1",new PumpSwapVersionedPoolCandles.Descriptor(s.decoderVersion(),s.pools(),s.fromInclusive(),s.toExclusive(),D,K),KEY,Map.of(),List.of(),List.of(),candles,uncertainty,new PumpSwapVersionedPoolCandles.Reconciliation(0,0,0,0,0,0,Map.of()),List.of("INCOMPLETE","NOT_ADMITTED"),KEY);}
}
