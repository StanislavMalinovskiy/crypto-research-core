package io.cryptoresearch.marketdata.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class CanonicalFingerprint {

	private final MessageDigest digest;

	CanonicalFingerprint() {
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
		}
	}

	CanonicalFingerprint field(String name, String value) {
		append(name);
		append(value);
		return this;
	}

	String finish() {
		return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
	}

	private void append(String value) {
		var bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}
}
