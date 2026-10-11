package io.cryptoresearch.marketdata.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import io.cryptoresearch.marketdata.api.PumpSwapChainTimeCandles;

@Service
public class ReadPumpSwapChainTimeCandlesUseCase {
    private final PersistPumpSwapOperationsUseCase persistence;
    private final PumpSwapChainTimeCandleCalculator calculator;
    private final TransactionTemplate read;
    public ReadPumpSwapChainTimeCandlesUseCase(PersistPumpSwapOperationsUseCase persistence,
            PumpSwapChainTimeCandleCalculator calculator,PlatformTransactionManager transactionManager) {
        this.persistence=persistence;this.calculator=calculator;read=new TransactionTemplate(transactionManager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(10);
    }
    public Optional<PumpSwapChainTimeCandles.Result> read(PumpSwapChainTimeCandles.Request input) {
        var request=PumpSwapChainTimeCandleCalculator.normalize(input);
        Optional<io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.StoredBatch> batch;
        try {batch=read.execute(status->persistence.find(request.batchKey()));}
        catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException failure){throw new IllegalStateException("CHAIN_TIME_READ_FAILED");}
        return batch.map(value->calculator.calculate(request,value));
    }
}
