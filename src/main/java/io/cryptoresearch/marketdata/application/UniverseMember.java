package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.cryptoresearch.kernel.api.AssetId;

public record UniverseMember(
		AssetId asset,
		String discoverySource,
		String eligibilityRuleVersion,
		Instant inclusionTime,
		Optional<Instant> exclusionTime,
		Optional<String> exclusionReason) {

	public UniverseMember {
		asset = Objects.requireNonNull(asset, "asset must not be null");
		discoverySource = RawObservationValues.requireOpaque(
				discoverySource, "discoverySource", RawObservationValues.MAX_VENUE_UTF8_BYTES);
		eligibilityRuleVersion = RawObservationValues.requireOpaque(
				eligibilityRuleVersion, "eligibilityRuleVersion", RawObservationValues.MAX_PARSER_VERSION_UTF8_BYTES);
		inclusionTime = RawObservationValues.toMicroseconds(inclusionTime, "inclusionTime");
		exclusionTime = Objects.requireNonNull(exclusionTime, "exclusionTime must not be null")
				.map(value -> RawObservationValues.toMicroseconds(value, "exclusionTime"));
		exclusionReason = Objects.requireNonNull(exclusionReason, "exclusionReason must not be null");
		if (exclusionTime.isPresent() != exclusionReason.isPresent()) {
			throw new IllegalArgumentException("exclusionTime and exclusionReason must be present together");
		}
		exclusionReason = exclusionReason.map(reason -> {
			if (reason.isBlank() || !reason.equals(reason.strip())) {
				throw new IllegalArgumentException("exclusionReason must be a non-blank trimmed value");
			}
			if (reason.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
					> RawObservationValues.MAX_EXCLUSION_REASON_UTF8_BYTES) {
				throw new IllegalArgumentException("exclusionReason must not exceed "
						+ RawObservationValues.MAX_EXCLUSION_REASON_UTF8_BYTES + " UTF-8 bytes");
			}
			return reason;
		});
		if (exclusionTime.isPresent() && !exclusionTime.orElseThrow().isAfter(inclusionTime)) {
			throw new IllegalArgumentException("exclusionTime must be after inclusionTime");
		}
	}
}
