package io.cryptoresearch.marketdata.application;

import java.util.List;
import java.util.Optional;

import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.TransactionId;

public interface RawTransactionStore {

	StoredRawTransaction store(StoredRawTransaction transaction);

	List<StoredRawTransaction> storeBatch(List<StoredRawTransaction> batch);

	Optional<StoredRawTransaction> find(ChainId chain, TransactionId transactionId, String provider);
}
