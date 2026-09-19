package io.cryptoresearch.marketdata.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.WalletAddress;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.marketdata.api.MarketDataApi.TradeSide;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class RecordedSwapParser {

	private static final String SUPPORTED_VERSION = "recorded-swap-v1";
	private static final Set<String> FIELDS = Set.of(
			"schemaVersion", "asset", "wallet", "side", "tokenQuantity", "nativeQuantity",
			"priceUsd", "liquidityUsd", "confidence", "venue");

	private final ObjectMapper objectMapper;

	RecordedSwapParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	MarketObservation parse(RecordedSwapInput input, String rawPayloadFingerprint) {
		if (!SUPPORTED_VERSION.equals(input.transformationVersion())) {
			throw new IllegalArgumentException("Unsupported recorded-swap transformation version: "
					+ input.transformationVersion());
		}
		try {
			var payload = objectMapper.readTree(input.payload());
			if (!payload.isObject() || !payload.propertyNames().equals(FIELDS)) {
				throw new IllegalArgumentException("Recorded swap parse failed: payload fields differ from " + FIELDS);
			}
			if (!SUPPORTED_VERSION.equals(text(payload, "schemaVersion"))) {
				throw new IllegalArgumentException("Recorded swap parse failed: payload schemaVersion mismatch");
			}
			var tokenQuantity = integer(payload, "tokenQuantity", true);
			var nativeQuantity = integer(payload, "nativeQuantity", false);
			var price = decimal(payload, "priceUsd", 18, true);
			var liquidity = decimal(payload, "liquidityUsd", 8, false);
			var confidence = decimal(payload, "confidence", 4, false);
			if (confidence.compareTo(BigDecimal.ONE) > 0) {
				throw new IllegalArgumentException("Recorded swap parse failed: confidence must not exceed 1.0000");
			}
			return new MarketObservation(
					new NormalizedSwapIdentity(input.chain(), input.transactionId(), input.eventId()),
					new AssetId(input.chain(), text(payload, "asset")),
					new WalletAddress(input.chain(), text(payload, "wallet")),
					TradeSide.valueOf(text(payload, "side")),
					tokenQuantity,
					nativeQuantity,
					price,
					liquidity,
					confidence,
					text(payload, "venue"),
					input.blockPosition(),
					input.blockHash(),
					input.sourceEventTime().map(value ->
							RawObservationValues.toMicroseconds(value, "sourceEventTime")),
					RawObservationValues.toMicroseconds(input.observedAt(), "observedAt"),
					input.provider(),
					rawPayloadFingerprint,
					input.transformationVersion());
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("Recorded swap parse failed", exception);
		}
	}

	private String text(JsonNode node, String field) {
		var value = node.required(field);
		if (!value.isTextual() || value.textValue().isBlank()) {
			throw new IllegalArgumentException("Recorded swap parse failed: " + field + " must be non-blank text");
		}
		return value.textValue();
	}

	private BigInteger integer(JsonNode node, String field, boolean positive) {
		var text = text(node, field);
		if (!text.matches("0|[1-9][0-9]*")) {
			throw new IllegalArgumentException("Recorded swap parse failed: " + field + " must be an exact integer");
		}
		var value = new BigInteger(text);
		if ((positive && value.signum() <= 0) || (!positive && value.signum() < 0)) {
			throw new IllegalArgumentException("Recorded swap parse failed: invalid " + field);
		}
		return value;
	}

	private BigDecimal decimal(JsonNode node, String field, int scale, boolean positive) {
		var text = text(node, field);
		var value = new BigDecimal(text);
		if (value.scale() != scale || !value.setScale(scale, RoundingMode.UNNECESSARY).toPlainString().equals(text)) {
			throw new IllegalArgumentException("Recorded swap parse failed: " + field + " requires scale " + scale);
		}
		if ((positive && value.signum() <= 0) || (!positive && value.signum() < 0)) {
			throw new IllegalArgumentException("Recorded swap parse failed: invalid " + field);
		}
		return value;
	}
}
