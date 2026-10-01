package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.AvailabilityStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.ExcludedFact;
import io.cryptoresearch.marketdata.api.MarketDataApi.FactKind;
import io.cryptoresearch.marketdata.api.MarketDataApi.PointInTimeQuery;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedDataset;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.marketdata.api.MarketDataApi.ReplayStatus;
import io.cryptoresearch.marketdata.api.MarketDataApi.RevisionReference;
import io.cryptoresearch.marketdata.api.MarketDataApi.SelectionScope;
import io.cryptoresearch.marketdata.api.MarketDataApi.VersionedFinalizeRequest;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VersionedSnapshotStorageIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private static final ChainId CHAIN = ChainId.SOLANA_MAINNET;
	private static final AssetId ASSET = new AssetId(CHAIN, "VersionedAsset11111111111111111111111111111");
	private static final Instant TIME = Instant.parse("2026-09-20T10:00:00Z");

	@Autowired MarketDataApi marketData;
	@Autowired JdbcClient jdbc;
	@Autowired PlatformTransactionManager transactions;
	@Autowired ObjectMapper json;
	@Autowired DataSource dataSource;

	@BeforeEach
	void clean() {
		for (var table : List.of("marketdata.v2_dataset_snapshot_exclusions",
				"marketdata.v2_dataset_snapshot_members", "marketdata.v2_dataset_snapshots",
				"marketdata.usd_revisions", "marketdata.liquidity_revisions", "marketdata.price_revisions",
				"marketdata.swap_revisions", "marketdata.raw_chain_events")) {
			jdbc.sql("DELETE FROM " + table).update();
		}
	}

	@Test
	void pinsOneExactRevisionAndScopeFingerprintAcrossRetryAndLaterRevision() {
		var first = record("same-event", "provider-a", "1.000000000000000000", TIME);
		var scope = scope(TIME, TIME);
		var request = request(scope, List.of(first));
		var frozen = marketData.finalizeVersioned(request);
		assertThat(marketData.finalizeVersioned(request)).isEqualTo(frozen);
		assertThat(marketData.versionedObservations(query(frozen.fingerprint(), TIME)))
				.singleElement().satisfies(observation -> {
					assertThat(observation.priceUsd()).isEqualByComparingTo("1.000000000000000000");
					assertThat(observation.provider()).isEqualTo("provider-a");
				});
		var second = record("same-event", "provider-b", "1.200000000000000000", TIME);
		assertThat(second.revisionKey()).isNotEqualTo(first.revisionKey());
		assertThat(marketData.versionedObservations(query(frozen.fingerprint(), TIME)))
				.singleElement().satisfies(observation ->
					assertThat(observation.provider()).isEqualTo("provider-a"));
		var alternative = marketData.finalizeVersioned(request(scope, List.of(second)));
		assertThat(alternative.fingerprint()).isNotEqualTo(frozen.fingerprint());
		assertThat(marketData.versionedObservations(query(alternative.fingerprint(), TIME)))
				.singleElement().satisfies(observation ->
					assertThat(observation.priceUsd()).isEqualByComparingTo("1.200000000000000000"));
		var scoped = marketData.finalizeVersioned(request(new SelectionScope(
				CHAIN, List.of(ASSET), TIME, TIME, List.of(FactKind.SWAP), List.of(),
				List.of("fixture-venue")), List.of(first)));
		assertThat(scoped.fingerprint()).isNotEqualTo(frozen.fingerprint());
		assertThat(count("marketdata.v2_dataset_snapshots")).isEqualTo(3);
	}

	@Test
	void rejectsOmissionDuplicateDispositionAndForgedExclusion() {
		var older = record("older", "provider-a", "1.000000000000000000", TIME);
		var newer = record("newer", "provider-a", "1.100000000000000000", TIME.plusSeconds(1));
		var scope = scope(TIME, TIME.plusSeconds(1));
		assertThatThrownBy(() -> marketData.finalizeVersioned(request(scope, List.of(older))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("incomplete selection");
		assertThatThrownBy(() -> marketData.finalizeVersioned(request(scope, List.of(older, older))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate selected revision");
		var digest = jdbc.sql("SELECT content_digest FROM marketdata.swap_revisions WHERE revision_key = :key")
				.param("key", newer.revisionKey()).query(String.class).single();
		var forged = new ExcludedFact(FactKind.SWAP, newer.canonicalIdentity(), ASSET.value(), "",
				"CALLER_PREFERENCE", digest);
		assertThatThrownBy(() -> marketData.finalizeVersioned(new VersionedFinalizeRequest(
				"length-prefixed-v2", TIME.plusSeconds(1), scope, List.of(older), List.of(forged),
				"explicit-revisions-v1", "modeled-history-v1", AvailabilityStatus.HISTORICAL_MODEL)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
		assertThat(count("marketdata.v2_dataset_snapshots")).isZero();
	}

	@Test
	void verifiedSelectionRecordsAProvenUnknownAvailabilityExclusion() {
		var source = record("excluded", "provider-a", "1.000000000000000000", TIME);
		var digest = jdbc.sql("SELECT content_digest FROM marketdata.swap_revisions WHERE revision_key = :key")
				.param("key", source.revisionKey()).query(String.class).single();
		var exclusion = new ExcludedFact(FactKind.SWAP, source.canonicalIdentity(), ASSET.value(), "",
				"UNVERIFIED_AVAILABILITY", digest);
		var request = new VersionedFinalizeRequest("length-prefixed-v2", null, scope(TIME, TIME),
				List.of(), List.of(exclusion), "explicit-revisions-v1", "verified-realtime-v1",
				AvailabilityStatus.VERIFIED_REALTIME);
		var frozen = marketData.finalizeVersioned(request);
		assertThat(frozen.included()).isEmpty();
		assertThat(frozen.excluded()).containsExactly(exclusion);
		assertThat(count("marketdata.v2_dataset_snapshot_exclusions")).isOne();
		assertThat(jdbc.sql("SELECT covered_key_count FROM marketdata.v2_dataset_snapshots")
				.query(Integer.class).single()).isOne();
		var evidence = json.valueToTree(marketData.versionedSnapshotEvidence(frozen.fingerprint()).orElseThrow());
		assertThat(evidence.get("scope")).isNotNull();
		assertThat(evidence.get("excluded").size()).isOne();
		assertThat(evidence.get("coveredKeyCount").asInt()).isOne();
		assertThat(evidence.get("selectionVersion").textValue()).isEqualTo("explicit-revisions-v1");
		assertThat(evidence.get("canonicalizationVersion").textValue()).isEqualTo("length-prefixed-v2");
		assertThatThrownBy(() -> marketData.finalizeVersioned(new VersionedFinalizeRequest(
				"length-prefixed-v2", TIME, scope(TIME, TIME), List.of(), List.of(exclusion),
				"explicit-revisions-v1", "verified-realtime-v1", AvailabilityStatus.VERIFIED_REALTIME)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("captured at freeze");
	}

	@Test
	void rejectsExclusionWhenAnotherRevisionOfTheSameKeyIsAdmissible() {
		var unavailable = record("mixed-availability", "provider-a", "1.000000000000000000", TIME);
		var available = record("mixed-availability", "provider-b", "1.100000000000000000", TIME);
		jdbc.sql("UPDATE marketdata.swap_revisions SET availability_status = 'VERIFIED_REALTIME', "
				+ "available_at = :time WHERE revision_key = :revision")
				.param("time", java.time.OffsetDateTime.ofInstant(TIME, java.time.ZoneOffset.UTC))
				.param("revision", available.revisionKey()).update();
		var digest = jdbc.sql("SELECT content_digest FROM marketdata.swap_revisions WHERE revision_key = :revision")
				.param("revision", unavailable.revisionKey()).query(String.class).single();
		var exclusion = new ExcludedFact(FactKind.SWAP, unavailable.canonicalIdentity(), ASSET.value(), "",
				"UNVERIFIED_AVAILABILITY", digest);
		assertThatThrownBy(() -> marketData.finalizeVersioned(new VersionedFinalizeRequest(
				"length-prefixed-v2", null, scope(TIME, TIME), List.of(), List.of(exclusion),
				"explicit-revisions-v1", "verified-realtime-v1", AvailabilityStatus.VERIFIED_REALTIME)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("admissible revision");
		assertThat(count("marketdata.v2_dataset_snapshots")).isZero();
	}

	@Test
	void rejectsRevisionWorkAboveBoundEvenForOneCanonicalKey() {
		seedBulk(1);
		jdbc.sql("""
				INSERT INTO marketdata.swap_revisions
				SELECT 'sha256:' || lpad(to_hex(i + 100000), 64, '0'), chain_id,
				 transaction_value, event_locator, source_kind, source_identity, provider, raw_payload_hash,
				 derivation_version, 'sha256:' || lpad(to_hex(i + 200000), 64, '0'),
				 availability_status, available_at, asset_address, wallet_address, side,
				 token_quantity, native_quantity, price_usd, liquidity_usd, confidence, venue,
				 observed_block_position, observed_block_hash, source_event_time, observed_at
				FROM marketdata.swap_revisions CROSS JOIN generate_series(1, 20001) AS i
				WHERE transaction_value = 'batch-1'
				""").update();
		assertThatThrownBy(() -> marketData.finalizeVersioned(request(scope(TIME, TIME.plusSeconds(1)),
				List.of(bulkReferences(1).getFirst()))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision work exceeds");
		assertThat(count("marketdata.v2_dataset_snapshots")).isZero();
	}

	@Test
	void accepts20000VisibleRevisionsAndRejects20001WithoutPartialPublication() {
		seedBulk(1);
		insertAdditionalRevisions(1, 19999);
		var selection = request(scope(TIME, TIME.plusSeconds(1)), List.of(bulkReferences(1).getFirst()));
		var accepted = marketData.finalizeVersioned(selection);
		assertThat(accepted.included()).hasSize(1);
		assertThat(count("marketdata.v2_dataset_snapshots")).isOne();
		insertAdditionalRevisions(20000, 20000);
		assertThatThrownBy(() -> marketData.finalizeVersioned(selection))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision work exceeds 20000");
		assertThat(count("marketdata.v2_dataset_snapshots")).isOne();
		assertThat(count("marketdata.v2_dataset_snapshot_members")).isOne();
		assertThat(count("marketdata.v2_dataset_snapshot_exclusions")).isZero();
	}

	private void insertAdditionalRevisions(int from, int to) {
		jdbc.sql("""
				INSERT INTO marketdata.swap_revisions
				SELECT 'sha256:' || lpad(to_hex(i + 100000), 64, '0'), chain_id,
				 transaction_value, event_locator, source_kind, source_identity, provider, raw_payload_hash,
				 derivation_version, 'sha256:' || lpad(to_hex(i + 200000), 64, '0'),
				 availability_status, available_at, asset_address, wallet_address, side,
				 token_quantity, native_quantity, price_usd, liquidity_usd, confidence, venue,
				 observed_block_position, observed_block_hash, source_event_time, observed_at
				FROM marketdata.swap_revisions CROSS JOIN generate_series(:from, :to) AS i
				WHERE transaction_value = 'batch-1' AND revision_key = 'sha256:' || lpad(to_hex(1), 64, '0')
				""").param("from", from).param("to", to).update();
	}

	@Test
	void scopeOrderIsCanonicalAndExclusionEvidenceChangesTheFingerprint() {
		var first = record("excluded-revision", "provider-a", "1.000000000000000000", TIME);
		var second = record("excluded-revision", "provider-b", "1.200000000000000000", TIME);
		var firstDigest = jdbc.sql("SELECT content_digest FROM marketdata.swap_revisions WHERE revision_key = :key")
				.param("key", first.revisionKey()).query(String.class).single();
		var secondDigest = jdbc.sql("SELECT content_digest FROM marketdata.swap_revisions WHERE revision_key = :key")
				.param("key", second.revisionKey()).query(String.class).single();
		var ordered = new SelectionScope(CHAIN, List.of(ASSET), TIME, TIME,
				List.of(FactKind.SWAP), List.of(), List.of("another-venue", "fixture-venue"));
		var permuted = new SelectionScope(CHAIN, List.of(ASSET, ASSET), TIME, TIME,
				List.of(FactKind.SWAP, FactKind.SWAP), List.of(), List.of("fixture-venue", "another-venue"));
		var excludedFirst = new ExcludedFact(FactKind.SWAP, first.canonicalIdentity(), ASSET.value(), "",
				"UNVERIFIED_AVAILABILITY", firstDigest);
		var request = new VersionedFinalizeRequest("length-prefixed-v2", null, ordered,
				List.of(), List.of(excludedFirst), "explicit-revisions-v1", "verified-realtime-v1",
				AvailabilityStatus.VERIFIED_REALTIME);
		var original = marketData.finalizeVersioned(request);
		var manifest = jdbc.sql("SELECT scope_manifest::text FROM marketdata.v2_dataset_snapshots WHERE snapshot_id = :id")
				.param("id", original.snapshotId()).query(String.class).single();
		var excludedSecond = new ExcludedFact(FactKind.SWAP, second.canonicalIdentity(), ASSET.value(), "",
				"UNVERIFIED_AVAILABILITY", secondDigest);
		var changed = marketData.finalizeVersioned(new VersionedFinalizeRequest(
				"length-prefixed-v2", null, permuted, List.of(), List.of(excludedSecond),
				"explicit-revisions-v1", "verified-realtime-v1", AvailabilityStatus.VERIFIED_REALTIME));
		assertThat(changed.fingerprint()).isNotEqualTo(original.fingerprint());
		assertThat(changed.scope()).isEqualTo(original.scope());
		assertThat(jdbc.sql("SELECT scope_manifest::text FROM marketdata.v2_dataset_snapshots WHERE snapshot_id = :id")
				.param("id", changed.snapshotId()).query(String.class).single()).isEqualTo(manifest);
		assertThat(jdbc.sql("SELECT evidence_fingerprint FROM marketdata.v2_dataset_snapshot_exclusions "
					+ "WHERE snapshot_id = :id").param("id", changed.snapshotId()).query(String.class).single())
				.isEqualTo(secondDigest);
	}

	@Test
	void rollsBackSnapshotHeaderWhenLateExclusionManifestInsertFails() {
		var source = record("late-exclusion", "provider-a", "1.000000000000000000", TIME);
		var digest = jdbc.sql("SELECT content_digest FROM marketdata.swap_revisions WHERE revision_key = :key")
				.param("key", source.revisionKey()).query(String.class).single();
		var exclusion = new ExcludedFact(FactKind.SWAP, source.canonicalIdentity(), ASSET.value(), "",
				"UNVERIFIED_AVAILABILITY", digest);
		var request = new VersionedFinalizeRequest("length-prefixed-v2", null, scope(TIME, TIME),
				List.of(), List.of(exclusion), "explicit-revisions-v1", "verified-realtime-v1",
				AvailabilityStatus.VERIFIED_REALTIME);
		jdbc.sql("""
				CREATE FUNCTION marketdata.fail_late_exclusion() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'late-exclusion-test'; END $$
				""").update();
		jdbc.sql("""
				CREATE TRIGGER fail_late_exclusion BEFORE INSERT ON marketdata.v2_dataset_snapshot_exclusions
				FOR EACH ROW EXECUTE FUNCTION marketdata.fail_late_exclusion()
				""").update();
		try {
			assertThatThrownBy(() -> marketData.finalizeVersioned(request))
					.isInstanceOf(RuntimeException.class).hasMessageContaining("late-exclusion-test");
			assertThat(count("marketdata.v2_dataset_snapshots")).isZero();
			assertThat(count("marketdata.v2_dataset_snapshot_exclusions")).isZero();
		}
		finally {
			jdbc.sql("DROP TRIGGER fail_late_exclusion ON marketdata.v2_dataset_snapshot_exclusions").update();
			jdbc.sql("DROP FUNCTION marketdata.fail_late_exclusion()").update();
		}
		assertThat(marketData.finalizeVersioned(request).excluded()).containsExactly(exclusion);
	}

	@Test
	void writesAll1001MembersAndRollsBackASecondChunkFailure() {
		seedBulk(1001);
		var cutoff = TIME.plusSeconds(1);
		var request = request(scope(TIME, cutoff), bulkReferences(1001));
		jdbc.sql("""
				CREATE FUNCTION marketdata.fail_late_member() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'late-member-test'; END $$
				""").update();
		jdbc.sql("""
				CREATE TRIGGER fail_late_member BEFORE INSERT ON marketdata.v2_dataset_snapshot_members
				FOR EACH ROW WHEN (NEW.member_ordinal = 1000)
				EXECUTE FUNCTION marketdata.fail_late_member()
				""").update();
		try {
			assertThatThrownBy(() -> marketData.finalizeVersioned(request))
					.isInstanceOf(RuntimeException.class).hasMessageContaining("late-member-test");
			assertThat(count("marketdata.v2_dataset_snapshots")).isZero();
			assertThat(count("marketdata.v2_dataset_snapshot_members")).isZero();
		}
		finally {
			jdbc.sql("DROP TRIGGER fail_late_member ON marketdata.v2_dataset_snapshot_members").update();
			jdbc.sql("DROP FUNCTION marketdata.fail_late_member()").update();
		}
		var snapshot = marketData.finalizeVersioned(request);
		assertThat(snapshot.included()).hasSize(1001);
		assertThat(count("marketdata.v2_dataset_snapshot_members")).isEqualTo(1001);
		assertThat(jdbc.sql("SELECT max(member_ordinal) FROM marketdata.v2_dataset_snapshot_members")
				.query(Integer.class).single()).isEqualTo(1000);
		assertThat(marketData.finalizeVersioned(request)).isEqualTo(snapshot);
	}

	@Test
	void rejectsThe10001stVisibleCanonicalKeyWithoutTruncation() {
		seedBulk(10001);
		var cutoff = TIME.plusSeconds(1);
		assertThatThrownBy(() -> marketData.finalizeVersioned(request(scope(TIME, cutoff), List.of())))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds 10000 canonical keys");
		assertThat(count("marketdata.v2_dataset_snapshots")).isZero();
	}

	@Test
	void laterCommitWithAnEarlierObservationCannotChangeFrozenMembership() throws Exception {
		var first = record("committed-first", "provider-a", "1.000000000000000000", TIME);
		var writerReady = new CountDownLatch(1);
		var commitAllowed = new CountDownLatch(1);
		try (var executor = Executors.newSingleThreadExecutor()) {
			var pending = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
				var late = record("late-backfill", "provider-a", "1.100000000000000000", TIME);
				writerReady.countDown();
				try {
					if (!commitAllowed.await(20, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting to commit late backfill");
				}
				}
				catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				return late;
			}));
			try {
				assertThat(writerReady.await(20, TimeUnit.SECONDS)).isTrue();
				var frozen = marketData.finalizeVersioned(request(scope(TIME, TIME), List.of(first)));
				assertThat(frozen.included()).containsExactly(first);
				commitAllowed.countDown();
				var late = pending.get(20, TimeUnit.SECONDS);
				assertThat(marketData.versionedObservations(query(frozen.fingerprint(), TIME)))
						.singleElement().satisfies(observation ->
								assertThat(observation.identity()).isEqualTo(first.canonicalIdentity()));
				var expanded = marketData.finalizeVersioned(request(scope(TIME, TIME), List.of(first, late)));
				assertThat(expanded.fingerprint()).isNotEqualTo(frozen.fingerprint());
				assertThat(marketData.versionedObservations(query(frozen.fingerprint(), TIME))).hasSize(1);
				assertThat(marketData.versionedObservations(query(expanded.fingerprint(), TIME))).hasSize(2);
			}
			finally {
				commitAllowed.countDown();
			}
		}
	}

	@Test
	void conflictingStoredManifestCannotBeMistakenForEqualRetry() {
		var revision = record("immutable-snapshot", "provider-a", "1.000000000000000000", TIME);
		var request = request(scope(TIME, TIME), List.of(revision));
		var snapshot = marketData.finalizeVersioned(request);
		jdbc.sql("UPDATE marketdata.v2_dataset_snapshots SET scope_manifest = '{\"tampered\":true}'::jsonb WHERE snapshot_id = :id")
				.param("id", snapshot.snapshotId()).update();
		assertThatThrownBy(() -> marketData.finalizeVersioned(request))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting immutable v2 snapshot header");
		assertThat(count("marketdata.v2_dataset_snapshot_members")).isOne();
	}

	@Test
	void concurrentEqualFinalizationResolvesOneCompleteSnapshot() throws Exception {
		var revision = record("concurrent-snapshot", "provider-a", "1.000000000000000000", TIME);
		var request = request(scope(TIME, TIME), List.of(revision));
		var release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
				return marketData.finalizeVersioned(request);
			});
			var second = executor.submit(() -> {
				if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
				return marketData.finalizeVersioned(request);
			});
			release.countDown();
			assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
			assertThat(count("marketdata.v2_dataset_snapshots")).isOne();
			assertThat(count("marketdata.v2_dataset_snapshot_members")).isOne();
		}
	}

	@Test
	void retriesEqualFinalizationAfterBothTransactionsEnumerateBeforeFirstCommit() throws Exception {
		var revision = record("serialized-snapshot", "provider-a", "1.000000000000000000", TIME);
		var selection = request(scope(TIME, TIME), List.of(revision));
		jdbc.sql("CREATE SEQUENCE marketdata.snapshot_insert_barrier START 1").update();
		jdbc.sql("""
				CREATE FUNCTION marketdata.await_snapshot_insert() RETURNS trigger LANGUAGE plpgsql AS $$
				DECLARE arrival bigint;
				BEGIN
				  arrival := nextval('marketdata.snapshot_insert_barrier');
				  PERFORM pg_advisory_xact_lock(17001, LEAST(arrival, 2)::integer);
				  IF arrival = 2 THEN
				    RAISE EXCEPTION 'concurrent-snapshot-serialization' USING ERRCODE = '40001';
				  END IF;
				  RETURN NEW;
				END $$
				""").update();
		jdbc.sql("""
				CREATE TRIGGER await_snapshot_insert BEFORE INSERT ON marketdata.v2_dataset_snapshots
				FOR EACH ROW EXECUTE FUNCTION marketdata.await_snapshot_insert()
				""").update();
		try (var barrier = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
			barrier.createStatement().execute("SELECT pg_advisory_lock(17001, 1)");
			barrier.createStatement().execute("SELECT pg_advisory_lock(17001, 2)");
			try {
				var first = executor.submit(() -> marketData.finalizeVersioned(selection));
				awaitSnapshotArrival(1);
				var second = executor.submit(() -> marketData.finalizeVersioned(selection));
				awaitSnapshotArrival(2);
				barrier.createStatement().execute("SELECT pg_advisory_unlock(17001, 1)");
				var published = first.get(20, TimeUnit.SECONDS);
				barrier.createStatement().execute("SELECT pg_advisory_unlock(17001, 2)");
				assertThatCode(() -> assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(published))
						.doesNotThrowAnyException();
				assertThat(count("marketdata.v2_dataset_snapshots")).isOne();
				assertThat(count("marketdata.v2_dataset_snapshot_members")).isOne();
				assertThat(count("marketdata.v2_dataset_snapshot_exclusions")).isZero();
				assertThat(jdbc.sql("SELECT last_value FROM marketdata.snapshot_insert_barrier")
						.query(Long.class).single()).isEqualTo(3L);
			}
			finally {
				barrier.createStatement().execute("SELECT pg_advisory_unlock(17001, 1)");
				barrier.createStatement().execute("SELECT pg_advisory_unlock(17001, 2)");
			}
		}
		finally {
			jdbc.sql("DROP TRIGGER await_snapshot_insert ON marketdata.v2_dataset_snapshots").update();
			jdbc.sql("DROP FUNCTION marketdata.await_snapshot_insert()").update();
			jdbc.sql("DROP SEQUENCE marketdata.snapshot_insert_barrier").update();
		}
	}

	private void awaitSnapshotArrival(int expected) throws InterruptedException {
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			var arrived = jdbc.sql("SELECT is_called AND last_value >= :expected FROM marketdata.snapshot_insert_barrier")
					.param("expected", expected).query(Boolean.class).single();
			if (arrived) return;
			Thread.sleep(20);
		}
		throw new AssertionError("Timed out waiting for snapshot insertion " + expected);
	}

	private void seedBulk(int size) {
		jdbc.sql("""
				INSERT INTO marketdata.raw_chain_events
				(chain_id, transaction_value, event_locator, provider, observed_block_position,
				 observed_at, payload, payload_hash, parser_version, ingested_at)
				SELECT :chain, 'batch-' || i, 'i:1', 'batch', i,
				 :observed::timestamptz + i * interval '1 microsecond', '{}',
				 'sha256:' || repeat('a', 64), 'batch-v1', :observed::timestamptz
				FROM generate_series(1, :size) AS i
				""").param("chain", CHAIN.value()).param("observed", TIME.toString())
				.param("size", size).update();
		jdbc.sql("""
				INSERT INTO marketdata.swap_revisions
				(revision_key, chain_id, transaction_value, event_locator, source_kind, source_identity,
				 provider, raw_payload_hash, derivation_version, content_digest, availability_status,
				 asset_address, wallet_address, side, token_quantity, native_quantity, price_usd,
				 liquidity_usd, confidence, venue, observed_block_position, source_event_time, observed_at)
				SELECT 'sha256:' || lpad(to_hex(i), 64, '0'), :chain, 'batch-' || i, 'i:1',
				 'RAW_CHAIN_EVENT', 'batch-source-' || i, 'batch', 'sha256:' || repeat('a', 64),
				 'batch-v1', 'sha256:' || lpad(to_hex(i + 1000000), 64, '0'), 'HISTORICAL_MODEL',
				 :asset, 'VersionedWallet111111111111111111111111111', 'BUY', 1000, 1000,
				 1.000000000000000000, 50000.00000000, 1.0000, 'fixture-venue', i,
				 :observed::timestamptz, :observed::timestamptz + i * interval '1 microsecond'
				FROM generate_series(1, :size) AS i
				""").param("chain", CHAIN.value()).param("asset", ASSET.value())
				.param("observed", TIME.toString()).param("size", size).update();
	}

	private List<RevisionReference> bulkReferences(int size) {
		var refs = new ArrayList<RevisionReference>(size);
		for (var i = 1; i <= size; i++) {
			var transaction = new TransactionId(CHAIN, "batch-" + i);
			refs.add(new RevisionReference(FactKind.SWAP,
					new MarketDataApi.NormalizedSwapIdentity(CHAIN, transaction, new EventId(transaction, "i:1")),
					"sha256:" + "%064x".formatted(i)));
		}
		return refs;
	}

	private RevisionReference record(String transactionValue, String provider, String price, Instant observedAt) {
		var transaction = new TransactionId(CHAIN, transactionValue);
		var input = new RecordedSwapInput(CHAIN, transaction, new EventId(transaction, "i:1"), provider,
				new BlockPosition(CHAIN, 100), Optional.empty(), Optional.of(observedAt), observedAt,
				"{\"schemaVersion\":\"recorded-swap-v1\",\"asset\":\"" + ASSET.value() + "\","
						+ "\"wallet\":\"VersionedWallet111111111111111111111111111\",\"side\":\"BUY\","
						+ "\"tokenQuantity\":\"1000\",\"nativeQuantity\":\"1000\",\"priceUsd\":\""
						+ price + "\",\"liquidityUsd\":\"50000.00000000\",\"confidence\":\"1.0000\","
						+ "\"venue\":\"fixture-venue\"}", "recorded-swap-v1");
		var replay = marketData.replayVersioned(new RecordedDataset("versioned-fixture-v1", List.of(input)));
		assertThat(replay.items()).singleElement().satisfies(item ->
				assertThat(item.legacyItem().status()).isEqualTo(ReplayStatus.NORMALIZED));
		return replay.items().getFirst().revision();
	}

	private SelectionScope scope(Instant from, Instant to) {
		return new SelectionScope(CHAIN, List.of(ASSET), from, to, List.of(FactKind.SWAP), List.of(), List.of());
	}

	private VersionedFinalizeRequest request(SelectionScope scope, List<RevisionReference> refs) {
		return new VersionedFinalizeRequest("length-prefixed-v2", scope.toInclusive(), scope, refs,
				List.of(), "explicit-revisions-v1", "modeled-history-v1", AvailabilityStatus.HISTORICAL_MODEL);
	}

	private PointInTimeQuery query(String fingerprint, Instant cutoff) {
		return new PointInTimeQuery(ASSET, TIME, cutoff, cutoff, Optional.of(fingerprint));
	}

	private int count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
	}
}
