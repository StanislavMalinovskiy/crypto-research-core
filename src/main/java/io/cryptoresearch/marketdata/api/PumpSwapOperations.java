package io.cryptoresearch.marketdata.api;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import io.cryptoresearch.kernel.api.ChainId;

/** Immutable offline operation evidence; no market-price or admission semantics. */
public final class PumpSwapOperations {
    private PumpSwapOperations() { }
    public enum Status { DECODED, PARTIAL, UNSUPPORTED, FAILED, NO_PUMPSWAP, NON_OPERATION, INVALID }
    public record RecordedBatch(List<RecordedTransaction> transactions, List<String> limitations) {
        public RecordedBatch { transactions = List.copyOf(transactions); limitations = List.copyOf(limitations); }
    }
    public record RecordedTransaction(UUID session, ChainId chain, String signature, String provider, long slot,
            String finality, String captureDisposition, String sourceReference, Optional<Instant> sourceTime,
            Instant receivedAt, Instant admittedAt, Instant ingestedAt, String rawResult, String rawDigest) {
        public RecordedTransaction {
            sourceTime = sourceTime.map(t -> t.truncatedTo(ChronoUnit.MICROS));
            receivedAt = receivedAt.truncatedTo(ChronoUnit.MICROS);
            admittedAt = admittedAt.truncatedTo(ChronoUnit.MICROS);
            ingestedAt = ingestedAt.truncatedTo(ChronoUnit.MICROS);
        }
    }
    public record Position(int outerIndex, List<Integer> innerPath, int legOrdinal) {
        public Position { innerPath = List.copyOf(innerPath); }
    }
    public record Invocation(Position position, Status status, String reason, String variant) { }
    public record TransactionResult(RecordedTransaction source, String transactionVersion, Status status,
            String reason, Optional<BigInteger> networkFee, List<Invocation> invocations) {
        public TransactionResult { networkFee = networkFee == null ? Optional.empty() : networkFee; invocations = List.copyOf(invocations); }
    }
    public record Operation(ChainId chain, String signature, Position position, String variant, String trader,
            String pool, String baseMint, String quoteMint, BigInteger baseAmount, BigInteger quoteAmount,
            Optional<Integer> baseDecimals, Optional<Integer> quoteDecimals, List<Integer> eventPath,
            List<List<Integer>> transferPaths, List<List<Integer>> lifecyclePaths, Map<String, BigInteger> eventDeclaredFees,
            Map<String, BigInteger> attributedFees, Map<String, BigInteger> eventFacts, String feeStatus, List<String> feeReasons,
            String rawDigest, String eventDigest, String sourceReference) {
        public Operation {
            eventPath = List.copyOf(eventPath); transferPaths = transferPaths.stream().map(List::copyOf).toList();
            lifecyclePaths = lifecyclePaths.stream().map(List::copyOf).toList();
            eventDeclaredFees = Map.copyOf(eventDeclaredFees); attributedFees = Map.copyOf(attributedFees); eventFacts = Map.copyOf(eventFacts); feeReasons = List.copyOf(feeReasons);
        }
    }
    public record Result(String decoderVersion, String schemaVersion, String canonicalizationVersion,
            Map<String, String> pinnedSourceHashes, List<TransactionResult> transactions, List<Operation> operations,
            List<String> limitations, String fingerprint) {
        public Result { pinnedSourceHashes = Map.copyOf(pinnedSourceHashes); transactions = List.copyOf(transactions);
            operations = List.copyOf(operations); limitations = List.copyOf(limitations); }
    }
}
