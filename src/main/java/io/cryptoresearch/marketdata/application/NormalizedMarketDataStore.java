package io.cryptoresearch.marketdata.application;

import java.util.List;
import java.util.Optional;

import io.cryptoresearch.marketdata.api.MarketDataApi.DatasetSnapshot;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;

public interface NormalizedMarketDataStore {

	MarketObservation store(MarketObservation observation);

	Optional<MarketObservation> find(NormalizedSwapIdentity identity);

	List<MarketObservation> findAll(List<NormalizedSwapIdentity> identities);

	List<MarketObservation> observations(PointInTimeQuery query);

	DatasetSnapshot storeSnapshot(DatasetSnapshot snapshot);
}
