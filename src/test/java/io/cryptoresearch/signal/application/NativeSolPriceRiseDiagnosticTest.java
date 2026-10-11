package io.cryptoresearch.signal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.PumpSwapNativePriceSignal.*;

class NativeSolPriceRiseDiagnosticTest {
    static final Instant E=Instant.parse("2026-10-10T06:10:00Z"), K=Instant.parse("2026-10-11T00:00:00Z");
    static final String KEY="sha256:"+"1".repeat(64);
    static final PumpSwapPoolMetrics.Pool POOL=new PumpSwapPoolMetrics.Pool("solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp","pool","base",PumpSwapChainTimeCandles.WSOL);
    final MarketDataApi marketdata=mock(MarketDataApi.class);final NativeSolPriceRiseDiagnostic diagnostic=new NativeSolPriceRiseDiagnostic(marketdata);
    @ParameterizedTest @CsvSource({"105,100,TRIGGER","104999999999999999999,100000000000000000000,NO_TRIGGER","105000000000000000001,100000000000000000000,TRIGGER"})
    void exactInclusiveFivePercentBoundaryUsesRationals(String numerator,String denominator,Status expected) {
        var report=report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,0,0),candle(E.minusSeconds(300),new BigInteger(numerator),new BigInteger(denominator),0,0)),List.of());
        when(marketdata.pumpSwapChainTimeCandles(any())).thenReturn(Optional.of(report));
        assertThatCode(()->diagnostic.diagnose(request())).as("adequate exact native endpoints must produce a diagnostic").doesNotThrowAnyException();
        var r=diagnostic.diagnose(request());assertThat(r.status()).isEqualTo(expected);assertThat(r.decisionKnowledgeAt()).isEqualTo(K);
        assertThat(r.analysis()).isEqualTo("RETROSPECTIVE_AT_K");assertThat(r.historicalAtEventEnd()).isEqualTo("UNAVAILABLE");
        assertThat(r.readiness()).isEqualTo("DIAGNOSTIC_ONLY_NOT_ADMITTED");
        verify(marketdata,times(2)).pumpSwapChainTimeCandles(new PumpSwapChainTimeCandles.Request(KEY,E.minusSeconds(600),E,K));verifyNoMoreInteractions(marketdata);
    }
    @Test void unknownBatchAndMissingBarAreInsufficientNeverNegative() {
        when(marketdata.pumpSwapChainTimeCandles(any())).thenReturn(Optional.empty());
        assertThat(diagnostic.diagnose(request()).status()).isEqualTo(Status.INSUFFICIENT);
        when(marketdata.pumpSwapChainTimeCandles(any())).thenReturn(Optional.of(report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,0,0)),List.of())));
        assertThat(diagnostic.diagnose(request()).reasons()).contains("MISSING_CURRENT_BUCKET");
    }
    @ParameterizedTest @CsvSource({"1,0,UNPRICED_OPERATION","0,1,MISSING_TRANSACTION_INDEX"})
    void selectedMissingMetadataOrOrderIsInsufficient(int unpriced,int missingIndex,String reason) {
        when(marketdata.pumpSwapChainTimeCandles(any())).thenReturn(Optional.of(report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,unpriced,missingIndex),candle(E.minusSeconds(300),BigInteger.ONE,BigInteger.ONE,0,0)),List.of())));
        var r=diagnostic.diagnose(request());assertThat(r.status()).isEqualTo(Status.INSUFFICIENT);assertThat(r.reasons()).contains(reason);
    }
    @Test void invalidRequestRejectsBeforeMarketdataRead() {
        assertThatThrownBy(()->diagnostic.diagnose(new Request(KEY,POOL,E.plusSeconds(1),K))).hasMessage("NATIVE_DIAGNOSTIC_INVALID_REQUEST");verifyNoInteractions(marketdata);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"FUTURE_KNOWLEDGE","MISSING_EVENT_TIME"})
    void relevantExcludedEvidencePreventsConfidentNegativeDespiteTwoPricedCloses(String disposition) {
        var clocks=new PumpSwapChainTimeCandles.Clocks(K,K,K,K,K,K,K.plusSeconds(1));
        var op=new PumpSwapChainTimeCandles.OperationReference(KEY,KEY,KEY,KEY,KEY,"raw-ref",POOL,"late",1,Optional.of(1L),disposition.equals("MISSING_EVENT_TIME")?Optional.empty():Optional.of(E.minusSeconds(30)),new PumpSwapOperations.Position(0,List.of(),0),clocks,"buy",BigInteger.ONE,BigInteger.ONE,Optional.of(6),Optional.of(9),new PumpSwapPoolMetrics.FeeFacts(Map.of(),Map.of(),Map.of(),"INCOMPLETE",List.of()),disposition,"PRICED",new PumpSwapChainTimeCandles.ExecutionProof("may18-oct07-endpoint-proof-v4",KEY,List.of(0,2),List.of(List.of(0,0)),List.of()));
        when(marketdata.pumpSwapChainTimeCandles(any())).thenReturn(Optional.of(report(List.of(candle(E.minusSeconds(600),BigInteger.ONE,BigInteger.ONE,0,0),candle(E.minusSeconds(300),BigInteger.ONE,BigInteger.ONE,0,0)),List.of(op))));
        var r=diagnostic.diagnose(request());assertThat(r.status()).isEqualTo(Status.INSUFFICIENT);assertThat(r.reasons()).contains(disposition);
    }
    static Request request(){return new Request(KEY,POOL,E,K);}
    static PumpSwapChainTimeCandles.Candle candle(Instant start,BigInteger n,BigInteger d,int unpriced,int index) {
        var p=new PumpSwapChainTimeCandles.Price(new PumpSwapPoolMetrics.Rational(n,d),null);
        return new PumpSwapChainTimeCandles.Candle(POOL,start,start.plusSeconds(300),new PumpSwapChainTimeCandles.Activity(1+unpriced,1,unpriced,1,unpriced,index,BigInteger.ONE,BigInteger.ONE,Optional.empty(),List.of()),Optional.of(new PumpSwapChainTimeCandles.Ohlc(p,p,p,p)));
    }
    static PumpSwapChainTimeCandles.Result report(List<PumpSwapChainTimeCandles.Candle> candles,List<PumpSwapChainTimeCandles.OperationReference> ops) {
        return new PumpSwapChainTimeCandles.Result("pumpswap-chain-time-candles-v1","length-prefixed-v1",new PumpSwapChainTimeCandles.Request(KEY,E.minusSeconds(600),E,K),
            new PumpSwapPoolMetrics.Source(KEY,"pumpswap-storage-v1","pumpswap-operations-v4","may18-oct07-endpoint-proof-v4","length-prefixed-v1",KEY,Map.of(),List.of("GAPS_UNKNOWN")),K,candles,List.of(),ops,
            new PumpSwapChainTimeCandles.Reconciliation(0,0,0,0,0,0,List.of()),new PumpSwapChainTimeCandles.Interpretation("OBSERVED_BATCH_ONLY","INCOMPLETE","UNKNOWN","INCOMPLETE","DIAGNOSTIC_ONLY_NOT_ADMITTED","GAP_COVERAGE_NOT_BOUND","PROVIDER_REPORTED","SPARSE","CONSERVATIVE"),KEY);
    }
}
