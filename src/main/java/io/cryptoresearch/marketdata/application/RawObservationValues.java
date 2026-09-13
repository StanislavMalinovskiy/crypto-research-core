package io.cryptoresearch.marketdata.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Pattern;

final class RawObservationValues {

	static final int MAX_CHAIN_ID_UTF8_BYTES = 41;
	static final int MAX_TRANSACTION_VALUE_UTF8_BYTES = 512;
	static final int MAX_EVENT_LOCATOR_UTF8_BYTES = 256;
	static final int MAX_PROVIDER_UTF8_BYTES = 64;
	static final int MAX_OBSERVED_BLOCK_HASH_UTF8_BYTES = 256;
	static final int MAX_PARSER_VERSION_UTF8_BYTES = 128;

	private static final Pattern PAYLOAD_HASH = Pattern.compile("sha256:[0-9a-f]{64}");

	private RawObservationValues() {
	}

	static String requireOpaque(String value, String label, int maximumUtf8Bytes) {
		Objects.requireNonNull(value, label + " must not be null");
		if (value.isBlank() || !value.equals(value.strip()) || containsControlCharacter(value)) {
			throw new IllegalArgumentException(
					label + " must be a non-blank value without surrounding whitespace or control characters");
		}
		if (value.getBytes(StandardCharsets.UTF_8).length > maximumUtf8Bytes) {
			throw new IllegalArgumentException(label + " must not exceed " + maximumUtf8Bytes + " UTF-8 bytes");
		}
		return value;
	}

	static String requirePayload(String payload) {
		Objects.requireNonNull(payload, "payload must not be null");
		if (payload.isBlank()) {
			throw new IllegalArgumentException("payload must not be blank");
		}
		return payload;
	}

	static String requirePayloadHash(String payloadHash) {
		Objects.requireNonNull(payloadHash, "payloadHash must not be null");
		if (!PAYLOAD_HASH.matcher(payloadHash).matches()) {
			throw new IllegalArgumentException("payloadHash must be an algorithm-qualified lowercase SHA-256 digest");
		}
		return payloadHash;
	}

	static Instant toMicroseconds(Instant instant, String label) {
		return Objects.requireNonNull(instant, label + " must not be null").truncatedTo(ChronoUnit.MICROS);
	}

	private static boolean containsControlCharacter(String value) {
		return value.codePoints().anyMatch(Character::isISOControl);
	}
}
