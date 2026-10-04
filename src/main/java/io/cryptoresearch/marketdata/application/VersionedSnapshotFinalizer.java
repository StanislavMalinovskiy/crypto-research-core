package io.cryptoresearch.marketdata.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.marketdata.api.MarketDataApi.AvailabilityStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.ExcludedFact;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.SelectionScope;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedFinalizeRequest;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedSnapshot;
import io.cryptoresearch.marketdata.application.VersionedSnapshotStore.Fact;
import io.cryptoresearch.marketdata.application.VersionedSnapshotStore.CanonicalFactKey;

@Service
public class VersionedSnapshotFinalizer {

	private record RevisionKey(io.cryptoresearch.marketdata.api.MarketDataApi.FactKind kind,
			String chain, String transaction, String locator, String revision) { }

	private static final int MAX_KEYS = 10_000;
	private static final int MAX_REVISIONS = 20_000;
	private static final int CHUNK = 1_000;
	private final VersionedSnapshotStore store;

	public VersionedSnapshotFinalizer(VersionedSnapshotStore store) {
		this.store = store;
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public VersionedSnapshot finalizeSnapshot(VersionedFinalizeRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		request = new VersionedFinalizeRequest(request.canonicalizationVersion(), request.knowledgeCutoff(),
				canonicalScope(request.scope()), request.included(), request.excluded(), request.selectionVersion(),
				request.policyVersion(), request.availabilityStatus());
		var freezeInstant = store.databaseFreezeInstant();
		var cutoff = cutoff(request, freezeInstant);
		validate(request, cutoff);

		var proposed = new HashMap<RevisionKey, RevisionReference>();
		for (var reference : request.included()) {
			if (proposed.putIfAbsent(revisionRefKey(reference), reference) != null) {
				throw new IllegalArgumentException("Duplicate selected revision");
			}
		}
		var exclusions = new HashMap<CanonicalFactKey, ExcludedFact>();
		for (var exclusion : request.excluded()) {
			if (exclusions.putIfAbsent(exclusionKey(exclusion), exclusion) != null) {
				throw new IllegalArgumentException("Duplicate exclusion disposition");
			}
		}

		var keys = new HashSet<CanonicalFactKey>();
		var selected = new HashMap<CanonicalFactKey, Fact>();
		var exclusionsProven = new HashSet<CanonicalFactKey>();
		var admissibleKeys = new HashSet<CanonicalFactKey>();
		var revisionCount = 0;
		Fact cursor = null;
		while (true) {
			var pageLimit = Math.min(CHUNK, MAX_REVISIONS - revisionCount + 1);
			var page = store.visible(request.scope(), cutoff, cursor, pageLimit);
			if (page.isEmpty()) {
				break;
			}
			for (var fact : page) {
				if (++revisionCount > MAX_REVISIONS) {
					throw new IllegalArgumentException("Visible revision work exceeds 20000 facts");
				}
				var key = fact.canonicalKey();
				if (keys.add(key) && keys.size() > MAX_KEYS) {
					throw new IllegalArgumentException("Visible scope exceeds 10000 canonical keys");
				}
				if (admissible(fact, request.availabilityStatus(), cutoff)) {
					admissibleKeys.add(key);
				}
				if (proposed.containsKey(revisionRefKey(fact))) {
					if (!admissible(fact, request.availabilityStatus(), cutoff)) {
						throw new IllegalArgumentException("Selected revision is unavailable at knowledge cutoff");
					}
					if (selected.putIfAbsent(key, fact) != null) {
						throw new IllegalArgumentException("Two selected revisions for one canonical key");
					}
				}
				var exclusion = exclusions.get(key);
				if (exclusion != null && exclusion.evidenceFingerprint().equals(fact.contentDigest())
						&& exclusionAllowed(exclusion, fact, request.availabilityStatus(), cutoff)) {
					exclusionsProven.add(key);
				}
			}
			cursor = page.getLast();
			if (page.size() < pageLimit) {
				break;
			}
		}
		if (selected.size() != proposed.size()) {
			throw new IllegalArgumentException("Selected revision is absent or outside declared scope");
		}
		if (exclusionsProven.size() != exclusions.size()) {
			throw new IllegalArgumentException("Unsupported or out-of-scope exclusion");
		}
		for (var key : exclusionsProven) {
			if (admissibleKeys.contains(key)) {
				throw new IllegalArgumentException("Cannot exclude canonical key with an admissible revision");
			}
		}
		for (var key : keys) {
			var included = selected.containsKey(key);
			var excluded = exclusionsProven.contains(key);
			if (included == excluded) {
				throw new IllegalArgumentException(included
						? "Contradictory included and excluded fact" : "incomplete selection: visible fact has no disposition");
			}
		}
		var orderedMembers = selected.values().stream().sorted(FACT_ORDER).toList();
		var orderedExclusions = request.excluded().stream().sorted(EXCLUSION_ORDER).toList();
		var fingerprint = fingerprint(request, cutoff, orderedMembers, orderedExclusions, keys.size());
		var refs = orderedMembers.stream().map(this::reference).toList();
		var snapshot = new VersionedSnapshot(fingerprint, fingerprint, cutoff, request.scope(), refs,
				orderedExclusions, request.availabilityStatus(), "EXPLICIT_REVISIONS_V1");
		return store.store(snapshot, request.canonicalizationVersion(), request.selectionVersion(),
				request.policyVersion(), orderedMembers, orderedExclusions);
	}

	private SelectionScope canonicalScope(SelectionScope scope) {
		Objects.requireNonNull(scope, "scope must not be null");
		return new SelectionScope(scope.chain(),
				scope.assets().stream().distinct()
						.sorted(Comparator.comparing(a -> a.value(), VersionedSnapshotFinalizer::compareUtf8)).toList(),
				scope.fromInclusive().truncatedTo(ChronoUnit.MICROS), scope.toInclusive().truncatedTo(ChronoUnit.MICROS),
				scope.factKinds().stream().distinct()
						.sorted(Comparator.comparing(Enum::name, VersionedSnapshotFinalizer::compareUtf8)).toList(),
				scope.pools().stream().distinct().sorted(VersionedSnapshotFinalizer::compareUtf8).toList(),
				scope.venues().stream().distinct().sorted(VersionedSnapshotFinalizer::compareUtf8).toList(),
				scope.eventFromInclusive().truncatedTo(ChronoUnit.MICROS),
				scope.eventToInclusive().truncatedTo(ChronoUnit.MICROS));
	}

	private Instant cutoff(VersionedFinalizeRequest request, Instant freezeInstant) {
		if (request.availabilityStatus() == AvailabilityStatus.VERIFIED_REALTIME) {
			if (request.knowledgeCutoff() != null) {
				throw new IllegalArgumentException("Verified realtime knowledge cutoff must be captured at freeze");
			}
			return freezeInstant.truncatedTo(ChronoUnit.MICROS);
		}
		return Objects.requireNonNull(request.knowledgeCutoff(), "historical modeled cutoff must not be null")
				.truncatedTo(ChronoUnit.MICROS);
	}

	private void validate(VersionedFinalizeRequest request, Instant cutoff) {
		var scope = Objects.requireNonNull(request.scope(), "scope must not be null");
		if (!"explicit-revisions-v1".equals(request.selectionVersion())) {
			throw new IllegalArgumentException("Unsupported selection rule");
		}
		if (request.availabilityStatus() == AvailabilityStatus.HISTORICAL_MODEL
				&& !"modeled-history-v1".equals(request.policyVersion())) {
			throw new IllegalArgumentException("Unsupported modeled availability policy");
		}
		if (request.availabilityStatus() == AvailabilityStatus.VERIFIED_REALTIME
				&& !"verified-realtime-v1".equals(request.policyVersion())) {
			throw new IllegalArgumentException("Unsupported realtime availability policy");
		}
		if (scope.assets().isEmpty() || scope.factKinds().isEmpty()
				|| scope.fromInclusive().isAfter(scope.toInclusive())
				|| scope.eventFromInclusive().isAfter(scope.eventToInclusive())
				|| scope.toInclusive().isAfter(cutoff)
				|| scope.assets().stream().anyMatch(asset -> !scope.chain().equals(asset.chain()))) {
			throw new IllegalArgumentException("Invalid or out-of-cutoff selection scope");
		}
		if (request.included().size() > MAX_KEYS || request.excluded().size() > MAX_KEYS) {
			throw new IllegalArgumentException("Snapshot dispositions exceed 10000");
		}
	}

	private boolean admissible(Fact fact, AvailabilityStatus status, Instant cutoff) {
		if ("VERIFIED_REALTIME".equals(fact.availabilityStatus())) {
			return fact.availableAt().filter(at -> !at.isAfter(cutoff)).isPresent();
		}
		return status == AvailabilityStatus.HISTORICAL_MODEL
				&& "HISTORICAL_MODEL".equals(fact.availabilityStatus());
	}

	private boolean exclusionAllowed(ExcludedFact exclusion, Fact fact, AvailabilityStatus status, Instant cutoff) {
		return status == AvailabilityStatus.VERIFIED_REALTIME && (
				("UNVERIFIED_AVAILABILITY".equals(exclusion.reason())
						&& !"VERIFIED_REALTIME".equals(fact.availabilityStatus()))
				|| ("AFTER_KNOWLEDGE_CUTOFF".equals(exclusion.reason())
						&& fact.availableAt().filter(at -> at.isAfter(cutoff)).isPresent()));
	}

	private RevisionKey revisionRefKey(RevisionReference reference) {
		return new RevisionKey(reference.kind(), reference.canonicalIdentity().chain().value(),
				reference.canonicalIdentity().transactionId().value(),
				reference.canonicalIdentity().eventId().locator(), reference.revisionKey());
	}

	private RevisionKey revisionRefKey(Fact fact) {
		return new RevisionKey(fact.kind(), fact.chain(), fact.transaction(), fact.locator(), fact.revisionKey());
	}

	private CanonicalFactKey exclusionKey(ExcludedFact exclusion) {
		return new CanonicalFactKey(exclusion.kind(), exclusion.canonicalIdentity().chain().value(),
				exclusion.canonicalIdentity().transactionId().value(),
				exclusion.canonicalIdentity().eventId().locator(), exclusion.assetAddress(), exclusion.scopeDimension());
	}

	/** Retains the exact saved v1 fingerprint encoding independently of tuple equality. */
	private String legacyExclusionFingerprintKey(ExcludedFact exclusion) {
		return exclusion.kind() + "|" + exclusion.canonicalIdentity().chain().value() + "|"
				+ exclusion.canonicalIdentity().transactionId().value() + "|"
				+ exclusion.canonicalIdentity().eventId().locator() + "|"
				+ exclusion.assetAddress() + "|" + exclusion.scopeDimension();
	}

	private RevisionReference reference(Fact fact) {
		var chain = new io.cryptoresearch.kernel.api.ChainId(fact.chain());
		var transaction = new io.cryptoresearch.kernel.api.TransactionId(chain, fact.transaction());
		return new RevisionReference(fact.kind(), new io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity(
				chain, transaction, new io.cryptoresearch.kernel.api.EventId(transaction, fact.locator())), fact.revisionKey());
	}

	private String fingerprint(VersionedFinalizeRequest request, Instant cutoff,
			List<Fact> members, List<ExcludedFact> exclusions, int coveredKeys) {
		var scope = request.scope();
		var hash = new CanonicalFingerprint().field("contract", "marketdata-versioned-snapshot-v1")
				.field("canonicalizationVersion", request.canonicalizationVersion())
				.field("selectionVersion", request.selectionVersion())
				.field("policyVersion", request.policyVersion())
				.field("availabilityStatus", request.availabilityStatus().name())
				.field("knowledgeCutoff", cutoff.toString()).field("chain", scope.chain().value())
				.field("observedFrom", scope.fromInclusive().toString())
				.field("observedTo", scope.toInclusive().toString())
				.field("eventFrom", scope.eventFromInclusive().toString())
				.field("eventTo", scope.eventToInclusive().toString());
		for (var asset : scope.assets().stream().map(a -> a.value()).sorted(VersionedSnapshotFinalizer::compareUtf8).toList()) {
			hash.field("asset", asset);
		}
		for (var kind : scope.factKinds().stream().map(Enum::name).sorted(VersionedSnapshotFinalizer::compareUtf8).toList()) {
			hash.field("factKind", kind);
		}
		for (var pool : scope.pools().stream().sorted(VersionedSnapshotFinalizer::compareUtf8).toList()) {
			hash.field("pool", pool);
		}
		for (var venue : scope.venues().stream().sorted(VersionedSnapshotFinalizer::compareUtf8).toList()) {
			hash.field("venue", venue);
		}
		hash.field("coveredKeyCount", Integer.toString(coveredKeys))
				.field("includedCount", Integer.toString(members.size()))
				.field("excludedCount", Integer.toString(exclusions.size()));
		for (var fact : members) {
			hash.field("memberObservedAt", fact.observedAt().toString())
					.field("memberKey", fact.legacyFingerprintKey()).field("memberRevision", fact.revisionKey())
					.field("memberContent", fact.contentDigest())
					.field("memberAvailability", fact.availabilityStatus())
					.field("memberAvailableAt", fact.availableAt().map(Object::toString).orElse(""));
		}
		for (var exclusion : exclusions) {
			hash.field("exclusionKey", legacyExclusionFingerprintKey(exclusion))
					.field("exclusionReason", exclusion.reason())
					.field("exclusionEvidence", exclusion.evidenceFingerprint());
		}
		return hash.finish();
	}

	private static final Comparator<Fact> FACT_ORDER = Comparator.comparing(Fact::observedAt)
			.thenComparing(Fact::chain, VersionedSnapshotFinalizer::compareUtf8)
			.thenComparing(Fact::transaction, VersionedSnapshotFinalizer::compareUtf8)
			.thenComparing(Fact::locator, VersionedSnapshotFinalizer::compareUtf8)
			.thenComparing(f -> f.kind().name(), VersionedSnapshotFinalizer::compareUtf8)
			.thenComparing(Fact::revisionKey, VersionedSnapshotFinalizer::compareUtf8);

	private static final Comparator<ExcludedFact> EXCLUSION_ORDER =
			Comparator.comparing((ExcludedFact e) -> e.kind().name(), VersionedSnapshotFinalizer::compareUtf8)
					.thenComparing(e -> e.canonicalIdentity().chain().value(), VersionedSnapshotFinalizer::compareUtf8)
					.thenComparing(e -> e.canonicalIdentity().transactionId().value(), VersionedSnapshotFinalizer::compareUtf8)
					.thenComparing(e -> e.canonicalIdentity().eventId().locator(), VersionedSnapshotFinalizer::compareUtf8)
					.thenComparing(ExcludedFact::assetAddress, VersionedSnapshotFinalizer::compareUtf8)
					.thenComparing(ExcludedFact::scopeDimension, VersionedSnapshotFinalizer::compareUtf8);

	private static int compareUtf8(String first, String second) {
		return java.util.Arrays.compareUnsigned(first.getBytes(StandardCharsets.UTF_8),
				second.getBytes(StandardCharsets.UTF_8));
	}
}
