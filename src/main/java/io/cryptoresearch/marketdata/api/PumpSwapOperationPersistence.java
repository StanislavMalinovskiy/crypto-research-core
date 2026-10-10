package io.cryptoresearch.marketdata.api;
import java.time.Instant;
import java.util.List;

/** Exact saved derivation evidence without market-data admission semantics. */
public final class PumpSwapOperationPersistence {
    private PumpSwapOperationPersistence() { }
    public record TransactionRevision(String revisionKey, String contentDigest, Instant persistedAt, List<String> operationRevisionKeys) {
        public TransactionRevision { operationRevisionKeys = List.copyOf(operationRevisionKeys); }
    }
    public record OperationRevision(String canonicalKey, String revisionKey, String transactionRevisionKey, String contentDigest, Instant persistedAt) { }
    public record StoredBatch(String storageVersion, String batchKey, Instant persistedAt, PumpSwapOperations.Result result,
            List<TransactionRevision> transactions, List<OperationRevision> operations) {
        public StoredBatch { transactions = List.copyOf(transactions); operations = List.copyOf(operations); }
    }
}
