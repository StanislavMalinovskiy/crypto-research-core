package io.cryptoresearch.marketdata.application;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.StoredBatch;

@Service
public class PublishPumpSwapOperationsUseCase {
    private final PumpSwapOperationStore store;
    public PublishPumpSwapOperationsUseCase(PumpSwapOperationStore store) { this.store = store; }
    @Transactional(timeout = 30)
    public StoredBatch publish(PumpSwapOperationCanonicalizer.Prepared prepared) {
        store.verifyRaw(prepared);
        return store.publish(prepared);
    }
}
