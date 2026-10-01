package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.WalletAddress;
import io.cryptoresearch.marketdata.api.MarketDataApi.ExcludedFact;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.marketdata.api.MarketDataApi.SelectionScope;
import io.cryptoresearch.marketdata.api.MarketDataApi.TradeSide;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedSnapshot;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedSnapshotEvidence;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.AvailabilityStatus;
import io.cryptoresearch.marketdata.application.VersionedSnapshotStore;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcVersionedSnapshotStore implements VersionedSnapshotStore {
	private record SnapshotHeader(Instant cutoff, AvailabilityStatus availability, SelectionScope scope,
			int coveredKeyCount, String selectionVersion, String canonicalizationVersion) { }

	private static final String VISIBLE_FACTS = """
			SELECT 'SWAP'::text COLLATE "C" AS fact_kind, chain_id, transaction_value, event_locator,
			 asset_address, ''::text COLLATE "C" AS scope_dimension, venue AS filter_value,
			 revision_key, content_digest,
			 observed_at, source_event_time AS event_time, availability_status, available_at
			 FROM marketdata.swap_revisions
			 UNION ALL
			 SELECT 'PRICE'::text COLLATE "C", chain_id, transaction_value, event_locator,
			 asset_address, venue, venue, revision_key, content_digest,
			 observed_at, source_event_time, availability_status, available_at
			 FROM marketdata.price_revisions
			 UNION ALL
			 SELECT 'LIQUIDITY'::text COLLATE "C", chain_id, transaction_value, event_locator,
			 asset_address, pool_address, pool_address, revision_key, content_digest,
			 observed_at, source_event_time, availability_status, available_at
			 FROM marketdata.liquidity_revisions
			 UNION ALL
			 SELECT 'USD'::text COLLATE "C", chain_id, transaction_value, event_locator,
			 asset_address, ''::text COLLATE "C", ''::text COLLATE "C", revision_key, content_digest,
			 computed_at, computed_at, availability_status, available_at
			 FROM marketdata.usd_revisions
			""";

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	public JdbcVersionedSnapshotStore(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public Instant databaseFreezeInstant() {
		return jdbc.sql("SELECT clock_timestamp()::timestamptz(6)")
				.query(OffsetDateTime.class).single().toInstant();
	}

	@Override
	public boolean containsSnapshot(String fingerprint) {
		if (!jdbc.sql("SELECT to_regclass('marketdata.v2_dataset_snapshots') IS NOT NULL")
				.query(Boolean.class).single()) {
			return false;
		}
		return jdbc.sql("SELECT 1 FROM marketdata.v2_dataset_snapshots WHERE fingerprint = :fingerprint")
				.param("fingerprint", fingerprint).query(Integer.class).optional().isPresent();
	}

	@Override
	public Optional<VersionedSnapshotEvidence> findEvidence(String fingerprint) {
		var header = jdbc.sql("""
				SELECT knowledge_cutoff, availability_status, scope_manifest, covered_key_count,
				 selection_version, canonicalization_version
				FROM marketdata.v2_dataset_snapshots
				WHERE fingerprint = :fingerprint
				""").param("fingerprint", fingerprint)
				.query((row, index) -> new SnapshotHeader(
						row.getObject("knowledge_cutoff", OffsetDateTime.class).toInstant(),
						AvailabilityStatus.valueOf(row.getString("availability_status")),
						json.readValue(row.getString("scope_manifest"), SelectionScope.class),
						row.getInt("covered_key_count"), row.getString("selection_version"),
						row.getString("canonicalization_version"))).optional();
		if (header.isEmpty()) {
			return Optional.empty();
		}
		var members = jdbc.sql("""
				SELECT fact_kind, chain_id, transaction_value, event_locator, revision_key
				FROM marketdata.v2_dataset_snapshot_members WHERE snapshot_id = :fingerprint
				ORDER BY member_ordinal
				""").param("fingerprint", fingerprint).query((row, index) -> {
			var chain = new ChainId(row.getString("chain_id"));
			var transaction = new TransactionId(chain, row.getString("transaction_value"));
			return new RevisionReference(FactKind.valueOf(row.getString("fact_kind")),
					new NormalizedSwapIdentity(chain, transaction,
							new EventId(transaction, row.getString("event_locator"))), row.getString("revision_key"));
		}).list();
		var exclusions = jdbc.sql("""
				SELECT fact_kind, chain_id, transaction_value, event_locator, asset_address,
				 scope_dimension, reason, evidence_fingerprint
				FROM marketdata.v2_dataset_snapshot_exclusions WHERE snapshot_id = :fingerprint
				ORDER BY exclusion_ordinal
				""").param("fingerprint", fingerprint).query((row, index) -> {
			var chain = new ChainId(row.getString("chain_id"));
			var transaction = new TransactionId(chain, row.getString("transaction_value"));
			return new ExcludedFact(FactKind.valueOf(row.getString("fact_kind")),
					new NormalizedSwapIdentity(chain, transaction,
							new EventId(transaction, row.getString("event_locator"))),
					row.getString("asset_address"), row.getString("scope_dimension"),
					row.getString("reason"), row.getString("evidence_fingerprint"));
		}).list();
		var evidence = header.orElseThrow();
		return Optional.of(new VersionedSnapshotEvidence(fingerprint, evidence.cutoff(),
				evidence.availability(), evidence.scope(), members, exclusions, evidence.coveredKeyCount(),
				evidence.selectionVersion(), evidence.canonicalizationVersion()));
	}

	@Override
	public List<Fact> visible(SelectionScope scope, Instant cutoff, Fact after, int limit) {
		if (limit < 1 || limit > 1000) {
			throw new IllegalArgumentException("fact read chunk must be between 1 and 1000");
		}
		var assets = scope.assets().stream().map(AssetId::value).toList();
		var kinds = scope.factKinds().stream().map(Enum::name).toList();
		return jdbc.sql("""
				SELECT f.* FROM (
				""" + VISIBLE_FACTS + """
				) f WHERE chain_id = :chain AND asset_address IN (:assets)
				AND fact_kind IN (:kinds)
				AND observed_at BETWEEN :fromObserved AND :toObserved AND observed_at <= :cutoff
				AND (event_time IS NULL OR event_time BETWEEN :fromEvent AND :toEvent)
				AND (fact_kind <> 'LIQUIDITY' OR :allPools OR filter_value IN (:pools))
				AND (fact_kind NOT IN ('SWAP', 'PRICE') OR :allVenues OR filter_value IN (:venues))
				AND (:firstPage OR (observed_at, chain_id, transaction_value, event_locator, fact_kind, revision_key)
				 > (:afterObserved, :afterChain, :afterTransaction, :afterLocator, :afterKind, :afterRevision))
				ORDER BY observed_at, chain_id, transaction_value, event_locator, fact_kind, revision_key
				LIMIT :limit
				""").param("chain", scope.chain().value()).param("assets", assets).param("kinds", kinds)
				.param("fromObserved", timestamp(scope.fromInclusive()))
				.param("toObserved", timestamp(scope.toInclusive())).param("cutoff", timestamp(cutoff))
				.param("fromEvent", timestamp(scope.eventFromInclusive()))
				.param("toEvent", timestamp(scope.eventToInclusive()))
				.param("allPools", scope.pools().isEmpty())
				.param("pools", scope.pools().isEmpty() ? List.of("") : scope.pools())
				.param("allVenues", scope.venues().isEmpty())
				.param("venues", scope.venues().isEmpty() ? List.of("") : scope.venues())
				.param("firstPage", after == null)
				.param("afterObserved", timestamp(after == null ? Instant.EPOCH : after.observedAt()))
				.param("afterChain", after == null ? "" : after.chain())
				.param("afterTransaction", after == null ? "" : after.transaction())
				.param("afterLocator", after == null ? "" : after.locator())
				.param("afterKind", after == null ? "" : after.kind().name())
				.param("afterRevision", after == null ? "" : after.revisionKey())
				.param("limit", limit).query(this::fact).list();
	}

	@Override
	public VersionedSnapshot store(VersionedSnapshot snapshot, String canonicalizationVersion,
			String selectionVersion, String policyVersion, List<Fact> members, List<ExcludedFact> exclusions) {
		var scopeJson = json.writeValueAsString(snapshot.scope());
		var inserted = jdbc.sql("""
				INSERT INTO marketdata.v2_dataset_snapshots
				(snapshot_id, fingerprint, canonicalization_version, selection_version, policy_version,
				 availability_status, knowledge_cutoff, scope_manifest, included_count, excluded_count, covered_key_count)
				VALUES (:id, :fingerprint, :canonicalization, :selection, :policy,
				 :availability, :cutoff, CAST(:scope AS JSONB), :included, :excluded, :covered)
				ON CONFLICT (snapshot_id) DO NOTHING RETURNING 1
				""").param("id", snapshot.snapshotId()).param("fingerprint", snapshot.fingerprint())
				.param("canonicalization", canonicalizationVersion).param("selection", selectionVersion)
				.param("policy", policyVersion).param("availability", snapshot.availabilityStatus().name())
				.param("cutoff", timestamp(snapshot.knowledgeCutoff())).param("scope", scopeJson)
				.param("included", members.size()).param("excluded", exclusions.size())
				.param("covered", members.size() + exclusions.size())
				.query(Integer.class).optional().isPresent();
		if (!inserted) {
			verifyExisting(snapshot, scopeJson, canonicalizationVersion, selectionVersion,
					policyVersion, members, exclusions);
			return snapshot;
		}
		for (var start = 0; start < members.size(); start += 1000) {
			for (var index = start; index < Math.min(start + 1000, members.size()); index++) {
				var fact = members.get(index);
				jdbc.sql("""
						INSERT INTO marketdata.v2_dataset_snapshot_members
						(snapshot_id, member_ordinal, fact_kind, chain_id, transaction_value, event_locator,
						 asset_address, scope_dimension, revision_key, content_digest,
						 swap_revision_key, price_revision_key, liquidity_revision_key, usd_revision_key)
						VALUES (:id, :ordinal, :kind, :chain, :transaction, :locator, :asset, :dimension,
						 :revision, :digest, :swap, :price, :liquidity, :usd)
						""").param("id", snapshot.snapshotId()).param("ordinal", index)
						.param("kind", fact.kind().name()).param("chain", fact.chain())
						.param("transaction", fact.transaction()).param("locator", fact.locator())
						.param("asset", fact.asset()).param("dimension", fact.dimension())
						.param("revision", fact.revisionKey()).param("digest", fact.contentDigest())
						.param("swap", fact.kind() == FactKind.SWAP ? fact.revisionKey() : null)
						.param("price", fact.kind() == FactKind.PRICE ? fact.revisionKey() : null)
						.param("liquidity", fact.kind() == FactKind.LIQUIDITY ? fact.revisionKey() : null)
						.param("usd", fact.kind() == FactKind.USD ? fact.revisionKey() : null).update();
			}
		}
		for (var index = 0; index < exclusions.size(); index++) {
			var exclusion = exclusions.get(index);
			jdbc.sql("""
						INSERT INTO marketdata.v2_dataset_snapshot_exclusions
						(snapshot_id, exclusion_ordinal, fact_kind, chain_id, transaction_value, event_locator,
						 asset_address, scope_dimension, reason, evidence_fingerprint)
						VALUES (:id, :ordinal, :kind, :chain, :transaction, :locator, :asset, :dimension,
						 :reason, :evidence)
						""").param("id", snapshot.snapshotId()).param("ordinal", index)
						.param("kind", exclusion.kind().name())
						.param("chain", exclusion.canonicalIdentity().chain().value())
						.param("transaction", exclusion.canonicalIdentity().transactionId().value())
						.param("locator", exclusion.canonicalIdentity().eventId().locator())
						.param("asset", exclusion.assetAddress()).param("dimension", exclusion.scopeDimension())
						.param("reason", exclusion.reason()).param("evidence", exclusion.evidenceFingerprint())
						.update();
		}
		return snapshot;
	}

	private void verifyExisting(VersionedSnapshot snapshot, String scopeJson,
			String canonicalizationVersion, String selectionVersion, String policyVersion,
			List<Fact> members, List<ExcludedFact> exclusions) {
		var equalHeader = jdbc.sql("""
				SELECT 1 FROM marketdata.v2_dataset_snapshots
				WHERE snapshot_id = :id AND fingerprint = :fingerprint AND scope_manifest = CAST(:scope AS JSONB)
				AND canonicalization_version = :canonicalization AND selection_version = :selection
				AND policy_version = :policy AND availability_status = :availability
				AND knowledge_cutoff = :cutoff AND included_count = :included
				AND excluded_count = :excluded AND covered_key_count = :covered
				""").param("id", snapshot.snapshotId()).param("fingerprint", snapshot.fingerprint())
				.param("scope", scopeJson).param("canonicalization", canonicalizationVersion)
				.param("selection", selectionVersion).param("policy", policyVersion)
				.param("availability", snapshot.availabilityStatus().name())
				.param("cutoff", timestamp(snapshot.knowledgeCutoff()))
				.param("included", members.size()).param("covered", members.size() + exclusions.size())
				.param("excluded", exclusions.size()).query(Integer.class).optional().isPresent();
		if (!equalHeader) {
			throw new IllegalStateException("Conflicting immutable v2 snapshot header");
		}
		var savedMembers = jdbc.sql("""
				SELECT revision_key || '|' || content_digest FROM marketdata.v2_dataset_snapshot_members
				WHERE snapshot_id = :id ORDER BY member_ordinal
				""").param("id", snapshot.snapshotId()).query(String.class).list();
		var expectedMembers = members.stream().map(f -> f.revisionKey() + "|" + f.contentDigest()).toList();
		if (!savedMembers.equals(expectedMembers)) {
			throw new IllegalStateException("Conflicting immutable v2 snapshot members");
		}
		var savedExclusions = jdbc.sql("""
				SELECT fact_kind || '|' || chain_id || '|' || transaction_value || '|' || event_locator || '|'
				 || asset_address || '|' || scope_dimension || '|' || reason || '|' || evidence_fingerprint
				FROM marketdata.v2_dataset_snapshot_exclusions WHERE snapshot_id = :id ORDER BY exclusion_ordinal
				""").param("id", snapshot.snapshotId()).query(String.class).list();
		var expectedExclusions = exclusions.stream().map(e -> e.kind() + "|" + e.canonicalIdentity().chain().value()
				+ "|" + e.canonicalIdentity().transactionId().value() + "|" + e.canonicalIdentity().eventId().locator()
				+ "|" + e.assetAddress() + "|" + e.scopeDimension() + "|" + e.reason() + "|" + e.evidenceFingerprint()).toList();
		if (!savedExclusions.equals(expectedExclusions)) {
			throw new IllegalStateException("Conflicting immutable v2 snapshot exclusions");
		}
	}

	@Override
	public List<MarketObservation> swapObservations(String snapshotFingerprint, PointInTimeQuery query) {
		return jdbc.sql("""
				SELECT swap.* FROM marketdata.v2_dataset_snapshots snapshot
				JOIN marketdata.v2_dataset_snapshot_members member ON member.snapshot_id = snapshot.snapshot_id
				JOIN marketdata.swap_revisions swap ON swap.revision_key = member.swap_revision_key
				WHERE snapshot.fingerprint = :fingerprint AND member.fact_kind = 'SWAP'
				AND swap.chain_id = :chain AND swap.asset_address = :asset
				AND swap.observed_at BETWEEN :from AND :to AND swap.observed_at <= :cutoff
				ORDER BY swap.observed_at, swap.chain_id, swap.transaction_value, swap.event_locator, swap.revision_key
				""").param("fingerprint", snapshotFingerprint).param("chain", query.asset().chain().value())
				.param("asset", query.asset().value()).param("from", timestamp(query.fromInclusive()))
				.param("to", timestamp(query.toInclusive())).param("cutoff", timestamp(query.cutoff()))
				.query(this::swap).list();
	}

	private Fact fact(ResultSet result, int row) throws SQLException {
		return new Fact(FactKind.valueOf(result.getString("fact_kind")), result.getString("chain_id"),
				result.getString("transaction_value"), result.getString("event_locator"),
				result.getString("asset_address"), result.getString("scope_dimension"),
				result.getString("revision_key"), result.getString("content_digest"),
				result.getObject("observed_at", OffsetDateTime.class).toInstant(),
				result.getString("availability_status"),
				Optional.ofNullable(result.getObject("available_at", OffsetDateTime.class)).map(OffsetDateTime::toInstant));
	}

	private MarketObservation swap(ResultSet result, int row) throws SQLException {
		var chain = new ChainId(result.getString("chain_id"));
		var transaction = new TransactionId(chain, result.getString("transaction_value"));
		return new MarketObservation(
				new NormalizedSwapIdentity(chain, transaction, new EventId(transaction, result.getString("event_locator"))),
				new AssetId(chain, result.getString("asset_address")),
				new WalletAddress(chain, result.getString("wallet_address")),
				TradeSide.valueOf(result.getString("side")),
				result.getBigDecimal("token_quantity").toBigIntegerExact(),
				result.getBigDecimal("native_quantity").toBigIntegerExact(),
				result.getBigDecimal("price_usd"), result.getBigDecimal("liquidity_usd"),
				result.getBigDecimal("confidence"), result.getString("venue"),
				new BlockPosition(chain, result.getLong("observed_block_position")),
				Optional.ofNullable(result.getString("observed_block_hash")),
				Optional.ofNullable(result.getObject("source_event_time", OffsetDateTime.class)).map(OffsetDateTime::toInstant),
				result.getObject("observed_at", OffsetDateTime.class).toInstant(), result.getString("provider"),
				result.getString("raw_payload_hash"), result.getString("derivation_version"));
	}

	private OffsetDateTime timestamp(Instant value) {
		return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
	}
}
