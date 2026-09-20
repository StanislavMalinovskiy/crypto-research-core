package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.WalletAddress;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.MarketDataApi.FinalizeDatasetRequest;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.TradeSide;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatasetSnapshotBatchStorageIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private static final ChainId CHAIN = ChainId.SOLANA_MAINNET;
	private static final Instant CUTOFF = Instant.parse("2026-09-20T12:00:00Z");
	private static final String HASH = "sha256:" + "1".repeat(64);

	private final MarketDataApi marketData;
	private final JdbcClient jdbcClient;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	DatasetSnapshotBatchStorageIT(MarketDataApi marketData, JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
		this.marketData = marketData;
		this.jdbcClient = jdbcClient;
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	void resetTables() {
		jdbcClient.sql("DELETE FROM marketdata.dataset_snapshot_members").update();
		jdbcClient.sql("DELETE FROM marketdata.dataset_snapshots").update();
		jdbcClient.sql("DELETE FROM marketdata.normalized_swaps").update();
		jdbcClient.sql("DELETE FROM marketdata.raw_chain_events").update();
	}

	@Test
	void chunkedMemberInsertAndLookupAreCompleteIdempotentAndAtomicBeyondBoundary() {
		var observations = observations(1_001);
		insertObservations(observations);
		var identities = observations.stream().map(MarketObservation::identity).toList();
		var request = new FinalizeDatasetRequest("length-prefixed-v1", CUTOFF, identities);

		var first = marketData.finalizeDataset(request);

		assertThat(first.observations()).hasSize(1_001);
		assertThat(first.observations()).extracting(value -> value.identity().transactionId().value())
				.containsExactlyElementsOf(identities.stream()
						.map(value -> value.transactionId().value()).sorted().toList());
		assertThat(marketData.finalizeDataset(request)).isEqualTo(first);
		assertThat(snapshotCount()).isEqualTo(1);
		assertThat(memberCount()).isEqualTo(1_001);
		assertThat(duplicateMemberCount()).isZero();

		installSecondChunkFailure();
		try {
			assertThatThrownBy(() -> marketData.finalizeDataset(
					new FinalizeDatasetRequest("length-prefixed-v2", CUTOFF, identities)))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("forced second chunk failure");
		}
		finally {
			removeSecondChunkFailure();
		}
		assertThat(snapshotCount()).isEqualTo(1);
		assertThat(memberCount()).isEqualTo(1_001);
		assertThat(duplicateMemberCount()).isZero();
	}

	private List<MarketObservation> observations(int count) {
		var values = new ArrayList<MarketObservation>(count);
		for (var index = 0; index < count; index++) {
			var transaction = new TransactionId(CHAIN, "snapshot-tx-%04d".formatted(index));
			var identity = new NormalizedSwapIdentity(
					CHAIN, transaction, new EventId(transaction, "i:0"));
			values.add(new MarketObservation(
					identity,
					new AssetId(CHAIN, "SnapshotAsset1111111111111111111111111111111"),
					new WalletAddress(CHAIN, "SnapshotWallet111111111111111111111111111111"),
					TradeSide.BUY,
					BigInteger.ONE,
					BigInteger.ONE,
					new BigDecimal("1.000000000000000000"),
					new BigDecimal("1000.00000000"),
					new BigDecimal("1.0000"),
					"fixture-venue",
					new BlockPosition(CHAIN, index),
					java.util.Optional.empty(),
					java.util.Optional.empty(),
					CUTOFF.minusSeconds(1),
					"fixture-provider",
					HASH,
					"recorded-swap-v1"));
		}
		return values;
	}

	private void insertObservations(List<MarketObservation> observations) {
		var timestamp = OffsetDateTime.ofInstant(CUTOFF.minusSeconds(1), ZoneOffset.UTC);
		jdbcTemplate.batchUpdate("""
				INSERT INTO marketdata.raw_chain_events (
				    chain_id, transaction_value, event_locator, provider, observed_block_position,
				    observed_at, payload, payload_hash, parser_version, ingested_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", observations.stream().map(value -> new Object[] {
					value.identity().chain().value(), value.identity().transactionId().value(),
					value.identity().eventId().locator(), value.provider(), value.blockPosition().value(),
					timestamp, "{}", HASH, value.transformationVersion(), timestamp }).toList());
		jdbcTemplate.batchUpdate("""
				INSERT INTO marketdata.normalized_swaps (
				    chain_id, transaction_value, event_locator, asset_address, wallet_address, side,
				    token_quantity, native_quantity, price_usd, liquidity_usd, confidence, venue,
				    observed_block_position, observed_at, provider, raw_payload_hash, transformation_version
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", observations.stream().map(value -> new Object[] {
					value.identity().chain().value(), value.identity().transactionId().value(),
					value.identity().eventId().locator(), value.asset().value(), value.wallet().value(),
					value.side().name(), value.tokenQuantity(), value.nativeQuantity(), value.priceUsd(),
					value.liquidityUsd(), value.confidence(), value.venue(), value.blockPosition().value(),
					timestamp, value.provider(), value.rawPayloadFingerprint(), value.transformationVersion() }).toList());
	}

	private void installSecondChunkFailure() {
		jdbcClient.sql("""
				CREATE OR REPLACE FUNCTION marketdata.fail_dataset_second_batch_chunk() RETURNS trigger
				LANGUAGE plpgsql AS $$
				BEGIN
				    RAISE EXCEPTION 'forced second chunk failure';
				END;
				$$
				""").update();
		jdbcClient.sql("""
				CREATE TRIGGER fail_dataset_second_batch_chunk
				BEFORE INSERT ON marketdata.dataset_snapshot_members
				FOR EACH ROW WHEN (NEW.member_ordinal = 1000)
				EXECUTE FUNCTION marketdata.fail_dataset_second_batch_chunk()
				""").update();
	}

	private void removeSecondChunkFailure() {
		jdbcClient.sql("DROP TRIGGER IF EXISTS fail_dataset_second_batch_chunk "
				+ "ON marketdata.dataset_snapshot_members").update();
		jdbcClient.sql("DROP FUNCTION IF EXISTS marketdata.fail_dataset_second_batch_chunk()").update();
	}

	private int snapshotCount() {
		return jdbcClient.sql("SELECT count(*) FROM marketdata.dataset_snapshots").query(Integer.class).single();
	}

	private int memberCount() {
		return jdbcClient.sql("SELECT count(*) FROM marketdata.dataset_snapshot_members")
				.query(Integer.class).single();
	}

	private int duplicateMemberCount() {
		return jdbcClient.sql("""
				SELECT count(*) FROM (
				    SELECT snapshot_id, chain_id, transaction_value, event_locator, count(*)
				    FROM marketdata.dataset_snapshot_members
				    GROUP BY snapshot_id, chain_id, transaction_value, event_locator
				    HAVING count(*) > 1
				) duplicates
				""").query(Integer.class).single();
	}
}
