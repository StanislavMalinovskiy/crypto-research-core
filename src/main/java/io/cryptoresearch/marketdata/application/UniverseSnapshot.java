package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record UniverseSnapshot(
		String snapshotId,
		String fingerprint,
		String ruleVersion,
		Instant cutoff,
		List<UniverseMember> members) {

	public UniverseSnapshot {
		snapshotId = RawObservationValues.requirePayloadHash(snapshotId);
		fingerprint = RawObservationValues.requirePayloadHash(fingerprint);
		ruleVersion = RawObservationValues.requireOpaque(
				ruleVersion, "ruleVersion", RawObservationValues.MAX_PARSER_VERSION_UTF8_BYTES);
		cutoff = RawObservationValues.toMicroseconds(cutoff, "cutoff");
		members = List.copyOf(Objects.requireNonNull(members, "members must not be null"));
		for (var member : members) {
			Objects.requireNonNull(member, "member must not be null");
			if (member.inclusionTime().isAfter(cutoff)) {
				throw new IllegalArgumentException("member inclusion time must not be after the snapshot cutoff");
			}
		}
	}
}
