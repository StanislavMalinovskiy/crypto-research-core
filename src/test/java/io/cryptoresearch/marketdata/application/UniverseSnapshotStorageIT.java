package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.application.FinalizeUniverseSnapshotUseCase.UniverseSnapshotRequest;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UniverseSnapshotStorageIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private static final ChainId CHAIN = ChainId.SOLANA_MAINNET;
	private static final AssetId ASSET_A = new AssetId(CHAIN, "AssetAAAAAAAA1111111111111111111111111111111");
	private static final AssetId ASSET_B = new AssetId(CHAIN, "AssetBBBBBBBB1111111111111111111111111111111");
	private static final Instant T1 = Instant.parse("2026-09-01T00:00:00Z");
	private static final Instant T2 = Instant.parse("2026-09-05T00:00:00Z");
	private static final Instant T3 = Instant.parse("2026-09-10T00:00:00Z");

	private final FinalizeUniverseSnapshotUseCase useCase;
	private final JdbcClient jdbcClient;

	@Autowired
	UniverseSnapshotStorageIT(FinalizeUniverseSnapshotUseCase useCase, JdbcClient jdbcClient) {
		this.useCase = useCase;
		this.jdbcClient = jdbcClient;
	}

	@BeforeEach
	void resetTables() {
		jdbcClient.sql("DELETE FROM marketdata.universe_members").update();
		jdbcClient.sql("DELETE FROM marketdata.universe_snapshots").update();
	}

	@Test
	void finalizeCreatesFingerprintedSnapshotWithCanonicalOrder() {
		var snapshot = useCase.finalizeSnapshot(new UniverseSnapshotRequest(
				"universe-v1", T3, List.of(member(ASSET_B, T1, null, null), member(ASSET_A, T1, null, null))));

		assertThat(snapshot.snapshotId()).matches("sha256:[0-9a-f]{64}");
		assertThat(snapshot.fingerprint()).isEqualTo(snapshot.snapshotId());
		assertThat(snapshot.members()).extracting(member -> member.asset())
				.containsExactly(ASSET_A, ASSET_B);
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.universe_members")
				.query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void excludedTokenIsNotMemberAfterExclusionAndReasonStaysQueryable() {
		var snapshot = useCase.finalizeSnapshot(new UniverseSnapshotRequest(
				"universe-v1", T3, List.of(member(ASSET_A, T1, T2, "dead-token"))));

		assertThat(useCase.findMembersAt(snapshot.snapshotId(), T1.plusSeconds(1)))
				.extracting(member -> member.asset())
				.containsExactly(ASSET_A);
		assertThat(useCase.findMembersAt(snapshot.snapshotId(), T2.plusSeconds(1))).isEmpty();

		var stored = jdbcClient.sql("""
				SELECT exclusion_reason FROM marketdata.universe_members
				WHERE snapshot_id = :snapshotId AND asset_address = :asset
				""")
				.param("snapshotId", snapshot.snapshotId())
				.param("asset", ASSET_A.value())
				.query(String.class)
				.single();
		assertThat(stored).isEqualTo("dead-token");
	}

	@Test
	void equalSnapshotRetryIsIdempotent() {
		var request = new UniverseSnapshotRequest(
				"universe-v1", T3, List.of(member(ASSET_A, T1, null, null), member(ASSET_B, T1, T2, "dead-token")));
		var first = useCase.finalizeSnapshot(request);
		var retry = useCase.finalizeSnapshot(request);

		assertThat(retry).isEqualTo(first);
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.universe_snapshots")
				.query(Integer.class).single()).isEqualTo(1);
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.universe_members")
				.query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void inclusionAfterCutoffIsRejected() {
		assertThatThrownBy(() -> useCase.finalizeSnapshot(new UniverseSnapshotRequest(
				"universe-v1", T1, List.of(member(ASSET_A, T3, null, null)))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void duplicateMembersAreRejected() {
		assertThatThrownBy(() -> useCase.finalizeSnapshot(new UniverseSnapshotRequest(
				"universe-v1", T3, List.of(member(ASSET_A, T1, null, null), member(ASSET_A, T1, null, null)))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void chunkedMemberPersistenceIsCompleteIdempotentAndAtomicBeyondBoundary() {
		var members = members(1_001);
		var request = new UniverseSnapshotRequest("universe-v1", T3, members);
		var first = useCase.finalizeSnapshot(request);

		assertThat(first.members()).hasSize(1_001);
		assertThat(first.members()).extracting(member -> member.asset().value())
				.containsExactlyElementsOf(members.stream().map(member -> member.asset().value()).sorted().toList());
		assertThat(useCase.finalizeSnapshot(request)).isEqualTo(first);
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.universe_snapshots")
				.query(Integer.class).single()).isEqualTo(1);
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.universe_members")
				.query(Integer.class).single()).isEqualTo(1_001);
		assertThat(jdbcClient.sql("""
				SELECT count(*) FROM (
				    SELECT snapshot_id, chain_id, asset_address, count(*)
				    FROM marketdata.universe_members
				    GROUP BY snapshot_id, chain_id, asset_address
				    HAVING count(*) > 1
				) duplicates
				""").query(Integer.class).single()).isZero();

		installSecondChunkFailure("marketdata.universe_members", "member_ordinal");
		try {
			assertThatThrownBy(() -> useCase.finalizeSnapshot(
					new UniverseSnapshotRequest("universe-v2", T3, members)))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("forced second chunk failure");
		}
		finally {
			removeSecondChunkFailure("marketdata.universe_members");
		}
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.universe_snapshots")
				.query(Integer.class).single()).isEqualTo(1);
		assertThat(jdbcClient.sql("SELECT count(*) FROM marketdata.universe_members")
				.query(Integer.class).single()).isEqualTo(1_001);
	}

	private List<UniverseMember> members(int count) {
		var members = new ArrayList<UniverseMember>(count);
		for (var index = 0; index < count; index++) {
			members.add(member(new AssetId(CHAIN, "Asset%04d".formatted(index)), T1, null, null));
		}
		return members;
	}

	private void installSecondChunkFailure(String table, String ordinalColumn) {
		jdbcClient.sql("""
				CREATE OR REPLACE FUNCTION marketdata.fail_second_batch_chunk() RETURNS trigger
				LANGUAGE plpgsql AS $$
				BEGIN
				    RAISE EXCEPTION 'forced second chunk failure';
				END;
				$$
				""").update();
		jdbcClient.sql("CREATE TRIGGER fail_second_batch_chunk BEFORE INSERT ON " + table
				+ " FOR EACH ROW WHEN (NEW." + ordinalColumn + " = 1000) "
				+ "EXECUTE FUNCTION marketdata.fail_second_batch_chunk()").update();
	}

	private void removeSecondChunkFailure(String table) {
		jdbcClient.sql("DROP TRIGGER IF EXISTS fail_second_batch_chunk ON " + table).update();
		jdbcClient.sql("DROP FUNCTION IF EXISTS marketdata.fail_second_batch_chunk()").update();
	}

	private UniverseMember member(AssetId asset, Instant inclusion, Instant exclusion, String reason) {
		return new UniverseMember(
				asset, "watched-program", "universe-v1", inclusion,
				Optional.ofNullable(exclusion), Optional.ofNullable(reason));
	}
}
