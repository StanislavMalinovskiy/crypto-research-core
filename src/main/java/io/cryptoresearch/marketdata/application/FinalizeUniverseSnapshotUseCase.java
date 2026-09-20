package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalizeUniverseSnapshotUseCase {

	private static final Comparator<UniverseMember> MEMBER_ORDER = Comparator
			.comparing((UniverseMember member) -> member.asset().chain().value())
			.thenComparing(member -> member.asset().value());

	private final UniverseSnapshotStore store;

	public FinalizeUniverseSnapshotUseCase(UniverseSnapshotStore store) {
		this.store = Objects.requireNonNull(store, "store must not be null");
	}

	@Transactional
	public UniverseSnapshot finalizeSnapshot(UniverseSnapshotRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		var cutoff = RawObservationValues.toMicroseconds(request.cutoff(), "cutoff");
		var members = request.members().stream()
				.distinct()
				.sorted(MEMBER_ORDER)
				.toList();
		if (members.size() != request.members().size()) {
			throw new IllegalArgumentException("Universe members must be unique per asset");
		}
		var digest = new CanonicalFingerprint()
				.field("contract", "marketdata-universe")
				.field("ruleVersion", request.ruleVersion())
				.field("cutoff", cutoff.toString())
				.field("memberCount", Integer.toString(members.size()));
		for (var member : members) {
			digest
					.field("chainId", member.asset().chain().value())
					.field("asset", member.asset().value())
					.field("discoverySource", member.discoverySource())
					.field("eligibilityRuleVersion", member.eligibilityRuleVersion())
					.field("inclusionTime", member.inclusionTime().toString())
					.field("exclusionTime", member.exclusionTime().map(Instant::toString).orElse(""))
					.field("exclusionReason", member.exclusionReason().orElse(""));
		}
		var fingerprint = digest.finish();
		return store.store(new UniverseSnapshot(
				fingerprint, fingerprint, request.ruleVersion(), cutoff, members));
	}

	public List<UniverseMember> findMembersAt(String snapshotId, Instant instant) {
		Objects.requireNonNull(snapshotId, "snapshotId must not be null");
		return store.membersAt(snapshotId,
				RawObservationValues.toMicroseconds(instant, "instant"));
	}

	public record UniverseSnapshotRequest(
			String ruleVersion,
			Instant cutoff,
			List<UniverseMember> members) {

		public UniverseSnapshotRequest {
			members = List.copyOf(Objects.requireNonNull(members, "members must not be null"));
		}
	}
}
