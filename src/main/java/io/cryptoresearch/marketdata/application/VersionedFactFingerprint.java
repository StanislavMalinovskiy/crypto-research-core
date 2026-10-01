package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.application.RecordMarketFactUseCase.RawSourceKind;

final class VersionedFactFingerprint {

	private VersionedFactFingerprint() { }

	static String sourceIdentity(RawSourceKind kind, NormalizedSwapIdentity source, String provider) {
		var fingerprint = new CanonicalFingerprint().field("contract", "raw-source-ref-v1")
				.field("kind", kind.name()).field("chain", source.chain().value())
				.field("transaction", source.transactionId().value());
		if (kind == RawSourceKind.RAW_CHAIN_EVENT) {
			fingerprint.field("locator", source.eventId().locator());
		}
		return fingerprint.field("provider", provider).finish();
	}

	static String revision(String kind, NormalizedSwapIdentity source, String sourceIdentity,
			String rawHash, String derivationVersion, String dimension) {
		return new CanonicalFingerprint().field("contract", "market-fact-revision-v1")
				.field("factKind", kind).field("chain", source.chain().value())
				.field("transaction", source.transactionId().value())
				.field("locator", source.eventId().locator()).field("dimension", dimension)
				.field("sourceIdentity", sourceIdentity).field("rawPayloadHash", rawHash)
				.field("derivationVersion", derivationVersion).finish();
	}

	static String swapContent(MarketObservation value, String revisionKey) {
		return new CanonicalFingerprint().field("contract", "swap-revision-content-v1")
				.field("revision", revisionKey).field("asset", value.asset().value())
				.field("wallet", value.wallet().value()).field("side", value.side().name())
				.field("tokenQuantity", value.tokenQuantity().toString())
				.field("nativeQuantity", value.nativeQuantity().toString())
				.field("priceUsd", value.priceUsd().toPlainString())
				.field("liquidityUsd", value.liquidityUsd().toPlainString())
				.field("confidence", value.confidence().toPlainString())
				.field("venue", value.venue())
				.field("blockPosition", Long.toString(value.blockPosition().value()))
				.field("blockHash", value.blockHash().orElse(""))
				.field("sourceEventTime", value.sourceEventTime().map(Object::toString).orElse(""))
				.field("observedAt", value.observedAt().toString()).field("provider", value.provider())
				.field("rawPayloadHash", value.rawPayloadFingerprint())
				.field("transformationVersion", value.transformationVersion())
				.field("availabilityStatus", "HISTORICAL_MODEL").finish();
	}

	static String priceContent(PriceObservation value, String revisionKey) {
		return new CanonicalFingerprint().field("contract", "price-revision-content-v1")
				.field("revision", revisionKey).field("asset", value.asset().value())
				.field("quoteAsset", value.quoteAsset().value()).field("venue", value.venue())
				.field("price", value.price().toPlainString())
				.field("tradeNotionalQuote", value.tradeNotionalQuote().toPlainString())
				.field("blockPosition", Long.toString(value.blockPosition().value()))
				.field("observedAt", value.observedAt().toString())
				.field("confidence", value.confidence().toPlainString())
				.field("provider", value.provider())
				.field("sourceEventTime", value.sourceEventTime().map(Object::toString).orElse(""))
				.field("availabilityStatus", "HISTORICAL_MODEL").finish();
	}

	static String liquidityContent(LiquidityObservation value, String revisionKey) {
		return new CanonicalFingerprint().field("contract", "liquidity-revision-content-v1")
				.field("revision", revisionKey).field("asset", value.asset().value())
				.field("pool", value.poolAddress()).field("quoteAsset", value.quoteAsset().value())
				.field("liquidityUsd", value.liquidityUsd().toPlainString())
				.field("baseReserve", value.baseReserve().toString())
				.field("quoteReserve", value.quoteReserve().toString())
				.field("blockPosition", Long.toString(value.blockPosition().value()))
				.field("observedAt", value.observedAt().toString())
				.field("confidence", value.confidence().toPlainString())
				.field("provider", value.provider())
				.field("sourceEventTime", value.sourceEventTime().map(Object::toString).orElse(""))
				.field("availabilityStatus", "HISTORICAL_MODEL").finish();
	}

	static String usdRevision(UsdConversionFact fact, String convertedPriceRevision, String quotePriceRevision) {
		return new CanonicalFingerprint().field("contract", "usd-revision-v1")
				.field("chain", fact.source().chain().value())
				.field("transaction", fact.source().transactionId().value())
				.field("locator", fact.source().eventId().locator())
				.field("asset", fact.asset().value())
				.field("convertedPriceRevision", convertedPriceRevision)
				.field("quotePriceRevision", quotePriceRevision)
				.field("methodVersion", fact.methodVersion()).finish();
	}

	static String usdContent(UsdConversionFact fact, String revisionKey) {
		return new CanonicalFingerprint().field("contract", "usd-revision-content-v1")
				.field("revision", revisionKey).field("priceUsd", fact.priceUsd().toPlainString())
				.field("computedAt", fact.computedAt().toString())
				.field("availabilityStatus", "HISTORICAL_MODEL").finish();
	}
}
