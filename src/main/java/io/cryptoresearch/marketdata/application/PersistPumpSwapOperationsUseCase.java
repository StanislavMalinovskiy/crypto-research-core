package io.cryptoresearch.marketdata.application;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.StoredBatch;

@Service
public class PersistPumpSwapOperationsUseCase {
    private final DecodePumpSwapOperationsUseCase decoder;
    private final PumpSwapOperationCanonicalizer canonicalizer;
    private final PumpSwapOperationStore store;
    private final PublishPumpSwapOperationsUseCase publisher;
    public PersistPumpSwapOperationsUseCase(DecodePumpSwapOperationsUseCase decoder, PumpSwapOperationCanonicalizer canonicalizer,
            PumpSwapOperationStore store, PublishPumpSwapOperationsUseCase publisher) {
        this.decoder = decoder; this.canonicalizer = canonicalizer; this.store = store; this.publisher = publisher;
    }
    public StoredBatch persist(PumpSwapOperations.RecordedBatch batch) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("DECODE_REQUIRES_NO_TRANSACTION");
        var prepared = canonicalizer.prepare(decoder.decode(batch));
        store.verifyRaw(prepared);
        return publisher.publish(prepared);
    }
    public Optional<StoredBatch> find(String batchKey) {
        if (batchKey == null || !batchKey.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("INVALID_BATCH_KEY");
        return store.find(batchKey);
    }
}
