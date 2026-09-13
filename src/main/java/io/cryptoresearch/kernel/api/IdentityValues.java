package io.cryptoresearch.kernel.api;

import java.util.Objects;
import java.util.regex.Pattern;

final class IdentityValues {

	private static final Pattern CAIP_2 = Pattern.compile("[-a-z0-9]{3,8}:[-_a-zA-Z0-9]{1,32}");

	private IdentityValues() {
	}

	static ChainId requireChain(ChainId chain) {
		return Objects.requireNonNull(chain, "chain must not be null");
	}

	static String requireCaip2(String value) {
		if (value == null || !CAIP_2.matcher(value).matches()) {
			throw new IllegalArgumentException("chain identifier must use exact customary CAIP-2 representation");
		}
		return value;
	}

	static String requireOpaque(String value, String label) {
		if (value == null || value.isBlank() || !value.equals(value.strip()) || containsControlCharacter(value)) {
			throw new IllegalArgumentException(label + " must be a non-blank opaque value without surrounding whitespace or control characters");
		}
		return value;
	}

	static int compare(ChainId leftChain, String leftValue, ChainId rightChain, String rightValue) {
		var chainComparison = leftChain.compareTo(rightChain);
		return chainComparison != 0 ? chainComparison : leftValue.compareTo(rightValue);
	}

	private static boolean containsControlCharacter(String value) {
		return value.codePoints().anyMatch(Character::isISOControl);
	}
}
