package io.cryptoresearch.marketdata.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.application.RecordMarketFactUseCase.RawSourceKind;

@Service
public class VersionedSwapUseCase {

	private final RecordedSwapParser parser;
	private final VersionedFactStore store;

	public VersionedSwapUseCase(RecordedSwapParser parser, VersionedFactStore store) {
		this.parser = parser;
		this.store = store;
	}

	@Transactional
	public RevisionReference normalizeAndStore(RecordedSwapInput input, String rawHash) {
		return storeRevision(parser.parse(input, rawHash), RawSourceKind.RAW_CHAIN_EVENT);
	}

	@Transactional
	public RevisionReference storeFromRawTransaction(
			io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation observation) {
		return storeRevision(observation, RawSourceKind.RAW_TRANSACTION);
	}

	private RevisionReference storeRevision(io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation observation,
			RawSourceKind sourceKind) {
		var source = observation.identity();
		var storedHash = store.rawPayloadHash(sourceKind, source, observation.provider());
		if (!storedHash.equals(observation.rawPayloadFingerprint())) {
			throw new IllegalArgumentException("Raw source digest does not match replay payload");
		}
		var sourceIdentity = VersionedFactFingerprint.sourceIdentity(
				sourceKind, source, observation.provider());
		var key = VersionedFactFingerprint.revision("SWAP", source, sourceIdentity, storedHash,
				observation.transformationVersion(), "");
		store.storeSwap(observation, key, VersionedFactFingerprint.swapContent(observation, key),
				sourceIdentity, sourceKind);
		return new RevisionReference(FactKind.SWAP, source, key);
	}
}
