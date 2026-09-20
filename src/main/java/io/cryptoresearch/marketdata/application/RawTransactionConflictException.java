package io.cryptoresearch.marketdata.application;

public final class RawTransactionConflictException extends RuntimeException {

	public RawTransactionConflictException(StoredRawTransaction transaction) {
		super("Conflicting immutable evidence for raw transaction "
				+ transaction.chain().value() + "/"
				+ transaction.transactionId().value() + "/"
				+ transaction.provider());
	}
}
