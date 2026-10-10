package io.cryptoresearch.marketdata.application;
import java.util.Optional;
import io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.StoredBatch;

public interface PumpSwapOperationStore {
    void verifyRaw(PumpSwapOperationCanonicalizer.Prepared prepared);
    StoredBatch publish(PumpSwapOperationCanonicalizer.Prepared prepared);
    Optional<StoredBatch> find(String batchKey);
}
