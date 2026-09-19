package io.cryptoresearch.marketdata.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;

@Service
public class NormalizeRecordedSwapUseCase {

	private final RecordedSwapParser parser;
	private final NormalizedMarketDataStore store;

	public NormalizeRecordedSwapUseCase(RecordedSwapParser parser, NormalizedMarketDataStore store) {
		this.parser = parser;
		this.store = store;
	}

	@Transactional
	public MarketObservation normalize(RecordedSwapInput input, String rawPayloadFingerprint) {
		return store.store(parser.parse(input, rawPayloadFingerprint));
	}
}
