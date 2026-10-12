package io.cryptoresearch.marketdata.api;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A confirmed pool exchange is not a route-wide trader operation or admitted market price. */
public final class PumpSwapPoolLegs {
    private PumpSwapPoolLegs() { }
    public record PoolBinding(String pool, String baseVault, String quoteVault, String baseMint,
            String quoteMint, String baseProgram, String quoteProgram) { }
    public record Request(PumpSwapOperations.RecordedBatch batch, List<PoolBinding> pools) {
        public Request { pools = List.copyOf(pools); }
    }
    public record ExactRatio(BigInteger numerator, BigInteger denominator, String basis) { }
    public record PoolLeg(PumpSwapOperations.RecordedTransaction source, PumpSwapOperations.Position position,
            String direction, PoolBinding binding, String observedInitiator, String eventUser,
            Optional<String> ultimateTrader, String ultimateTraderStatus, String outerProgram,
            BigInteger baseAmount, int baseDecimals, BigInteger eventGrossQuote, BigInteger eventUserQuote,
            Optional<BigInteger> poolNetQuoteLamports, Optional<ExactRatio> price, String priceStatus,
            String priceReason, List<Integer> eventPath, List<List<Integer>> transferPaths,
            String eventDigest, String proofDigest) {
        public PoolLeg {
            eventPath = List.copyOf(eventPath);
            transferPaths = transferPaths.stream().map(List::copyOf).toList();
        }
    }
    public record Result(String decoderVersion, String schemaVersion, String canonicalizationVersion,
            Map<String,String> pinnedSourceHashes, List<PoolBinding> bindings, PumpSwapOperations.Result v4,
            List<PumpSwapOperations.TransactionResult> transactions, List<PoolLeg> poolLegs,
            List<String> limitations, String fingerprint) {
        public Result {
            pinnedSourceHashes = Map.copyOf(pinnedSourceHashes); bindings = List.copyOf(bindings);
            transactions = List.copyOf(transactions); poolLegs = List.copyOf(poolLegs);
            limitations = List.copyOf(limitations);
        }
    }
}
