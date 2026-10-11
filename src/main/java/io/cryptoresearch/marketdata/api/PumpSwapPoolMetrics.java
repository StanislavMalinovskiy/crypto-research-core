package io.cryptoresearch.marketdata.api;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Exact historical diagnostics over one immutable recorded operation batch. */
public final class PumpSwapPoolMetrics {
    private PumpSwapPoolMetrics() {}
    public record Request(String batchKey, Instant fromInclusive, Instant toExclusive,
            Instant observationCutoff, int intervalSeconds) {}
    public record Pool(String chain, String pool, String baseMint, String quoteMint) {}
    public record Rational(BigInteger numerator, BigInteger denominator) {
        public Rational {
            if (numerator.signum() <= 0 || denominator.signum() <= 0) throw new IllegalArgumentException("METRICS_INVALID_PRICE");
            var gcd = numerator.gcd(denominator); numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        }
    }
    public record OperationReference(String canonicalKey, String revisionKey, String transactionRevisionKey,
            String contentDigest, String chain, String signature, PumpSwapOperations.Position position,
            Instant receivedAt, String variant, String trader, BigInteger baseRawUnits, BigInteger quoteRawUnits,
            Optional<Integer> baseDecimals, Optional<Integer> quoteDecimals, FeeFacts fees) {}
    public record FeeFacts(Map<String,BigInteger> eventDeclared, Map<String,BigInteger> attributed,
            Map<String,BigInteger> eventFacts, String status, List<String> reasons) {
        public FeeFacts { eventDeclared = Map.copyOf(eventDeclared); attributed = Map.copyOf(attributed);
            eventFacts = Map.copyOf(eventFacts); reasons = List.copyOf(reasons); }
    }
    public record UnpricedOperation(String operationRevisionKey, String reason) {}
    public record Price(Rational value, OperationReference operation) {}
    public record Ohlc(Price open, Price high, Price low, Price close) {}
    public record Activity(int operationCount, int buyCount, int sellCount, int priceEligibleCount,
            int unpricedCount, BigInteger baseRawUnits, BigInteger quoteRawUnits,
            List<String> operationRevisionKeys, List<UnpricedOperation> unpricedOperations) {
        public Activity { operationRevisionKeys = List.copyOf(operationRevisionKeys); unpricedOperations = List.copyOf(unpricedOperations); }
    }
    public record Candle(Pool pool, Instant fromInclusive, Instant toExclusive, Activity activity, Optional<Ohlc> ohlc) {}
    public record PoolTotal(Pool pool, Activity activity) {}
    public record TransactionDisposition(String revisionKey, String contentDigest, String chain, String signature,
            String provider, long slot, Optional<Instant> sourceTime, Instant receivedAt, Instant admittedAt,
            Instant ingestedAt, PumpSwapOperations.Status status, String reason, Optional<BigInteger> networkFee,
            boolean insideWindow, List<String> operationRevisionKeys) {
        public TransactionDisposition { operationRevisionKeys = List.copyOf(operationRevisionKeys); }
    }
    public record StatusCount(PumpSwapOperations.Status status, int count) {}
    public record Reconciliation(int transactionCount, int insideTransactions, int outsideTransactions,
            int operationCount, int selectedOperations, int outsideOperations, int buys, int sells,
            int priceEligibleOperations, int unpricedOperations, List<StatusCount> statuses) {
        public Reconciliation { statuses = List.copyOf(statuses); }
    }
    public record Source(String batchKey, String storageVersion, String decoderVersion, String schemaVersion,
            String canonicalizationVersion, String resultFingerprint, Map<String,String> pinnedSourceHashes,
            List<String> limitations) {
        public Source { pinnedSourceHashes = Map.copyOf(pinnedSourceHashes); limitations = List.copyOf(limitations); }
    }
    public record Interpretation(String purpose, String population, String coverage, String unreceivedLoss,
            String fees, String signalReadiness, String buckets, String ordering, String availability) {}
    public record Result(String algorithmVersion, String canonicalizationVersion, Request request, Source source,
            List<PoolTotal> pools, List<Candle> candles, List<TransactionDisposition> transactions,
            List<OperationReference> selectedOperations, List<OperationReference> outsideOperations,
            Reconciliation reconciliation, Interpretation interpretation, String fingerprint) {
        public Result {
            pools = List.copyOf(pools); candles = List.copyOf(candles); transactions = List.copyOf(transactions);
            selectedOperations = List.copyOf(selectedOperations); outsideOperations = List.copyOf(outsideOperations);
        }
    }
}
