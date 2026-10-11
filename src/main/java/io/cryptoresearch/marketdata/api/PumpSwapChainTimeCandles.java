package io.cryptoresearch.marketdata.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.*;

/** Observed provider-event-time evidence, never an admitted market snapshot. */
public final class PumpSwapChainTimeCandles {
    private PumpSwapChainTimeCandles() {}
    public static final String WSOL = "So11111111111111111111111111111111111111112";
    public record Request(String batchKey, Instant fromInclusive, Instant toExclusive, Instant knowledgeCutoff) {}
    public record Clocks(Instant receivedAt, Instant admittedAt, Instant ingestedAt,
            Instant transactionPersistedAt, Instant operationPersistedAt, Instant batchPersistedAt, Instant knownAt) {}
    public record ExecutionProof(String version, String eventDigest, List<Integer> eventPath,
            List<List<Integer>> transferPaths, List<List<Integer>> lifecyclePaths) {
        public ExecutionProof { eventPath=List.copyOf(eventPath); transferPaths=transferPaths.stream().map(List::copyOf).toList(); lifecyclePaths=lifecyclePaths.stream().map(List::copyOf).toList(); }
    }
    public record OperationReference(String canonicalKey, String revisionKey, String transactionRevisionKey,
            String contentDigest, String rawDigest, String sourceReference, Pool pool, String signature,
            long slot, Optional<Long> transactionIndex, Optional<Instant> eventTime, PumpSwapOperations.Position position,
            Clocks clocks, String variant, BigInteger baseRawUnits, BigInteger quoteRawUnits,
            Optional<Integer> baseDecimals, Optional<Integer> quoteDecimals, FeeFacts fees,
            String disposition, String priceReason, ExecutionProof proof) {}
    public record Price(Rational value, OperationReference operation) {}
    public record Ohlc(Price open, Price high, Price low, Price close) {}
    public record Activity(int operationCount, int buyCount, int sellCount, int priceEligibleCount,
            int unpricedCount, int missingIndexCount, BigInteger baseRawUnits, BigInteger quoteRawUnits,
            Optional<BigDecimal> quoteSolVolume, List<String> operationRevisionKeys) {
        public Activity { operationRevisionKeys=List.copyOf(operationRevisionKeys); }
    }
    public record Candle(Pool pool, Instant fromInclusive, Instant toExclusive, Activity activity, Optional<Ohlc> ohlc) {}
    public record TransactionDisposition(String revisionKey, String contentDigest, String chain, String signature,
            String provider, long slot, Optional<Long> transactionIndex, Optional<Instant> eventTime,
            Optional<Instant> sourceTime, Instant receivedAt, Instant admittedAt, Instant ingestedAt,
            Instant persistedAt, PumpSwapOperations.Status status, String reason, Optional<BigInteger> networkFee,
            List<String> operationRevisionKeys) {
        public TransactionDisposition { operationRevisionKeys=List.copyOf(operationRevisionKeys); }
    }
    public record Reconciliation(int transactionCount, int operationCount, int includedOperations,
            int excludedOperations, int priceEligibleOperations, int unpricedOperations, List<StatusCount> statuses) {
        public Reconciliation { statuses=List.copyOf(statuses); }
    }
    public record Interpretation(String population, String coverage, String unseenLoss, String fees,
            String readiness, String gapCoverage, String ordering, String buckets, String availability) {}
    public record Result(String algorithmVersion, String canonicalizationVersion, Request request, Source source,
            Instant batchPersistedAt, List<Candle> candles, List<TransactionDisposition> transactions,
            List<OperationReference> operations, Reconciliation reconciliation, Interpretation interpretation,
            String fingerprint) {
        public Result { candles=List.copyOf(candles); transactions=List.copyOf(transactions); operations=List.copyOf(operations); }
    }
}
