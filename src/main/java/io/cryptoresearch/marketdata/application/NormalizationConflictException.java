package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

public class NormalizationConflictException extends RuntimeException {

	public NormalizationConflictException(NormalizedSwapIdentity identity) {
		super("Normalized swap conflict for " + identity.chain().value() + "/"
				+ identity.transactionId().value() + "/" + identity.eventId().locator());
	}
}
