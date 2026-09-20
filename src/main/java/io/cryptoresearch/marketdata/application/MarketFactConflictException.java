package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;

public class MarketFactConflictException extends RuntimeException {

	public MarketFactConflictException(NormalizedSwapIdentity source) {
		super("Conflicting market fact evidence for " + source);
	}
}
