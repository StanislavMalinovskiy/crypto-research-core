package io.cryptoresearch.marketdata.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PayloadFingerprint {

	private PayloadFingerprint() {
	}

	static String sha256(String payload) {
		var exactPayload = RawObservationValues.requirePayload(payload);
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(exactPayload.getBytes(StandardCharsets.UTF_8));
			return "sha256:" + HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
		}
	}
}
