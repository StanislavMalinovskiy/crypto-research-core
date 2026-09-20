package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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

	private UniverseMember member(AssetId asset, Instant inclusion, Instant exclusion, String reason) {
		return new UniverseMember(
				asset, "watched-program", "universe-v1", inclusion,
				Optional.ofNullable(exclusion), Optional.ofNullable(reason));
	}
}
