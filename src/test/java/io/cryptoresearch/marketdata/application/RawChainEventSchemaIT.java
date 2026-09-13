package io.cryptoresearch.marketdata.application;

import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.OBSERVED_AT;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.PAYLOAD;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.observation;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.rowCount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.kernel.api.ChainId;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RawChainEventSchemaIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final StoreRawObservationUseCase useCase;
	private final JdbcClient jdbcClient;
	private final Flyway flyway;

	@Autowired
	RawChainEventSchemaIT(StoreRawObservationUseCase useCase, JdbcClient jdbcClient, Flyway flyway) {
		this.useCase = useCase;
		this.jdbcClient = jdbcClient;
		this.flyway = flyway;
	}

	@BeforeEach
	void resetTable() {
		jdbcClient.sql("DELETE FROM marketdata.raw_chain_events").update();
	}

	@Test
	void migrationOwnsTheExactTableShapeAndOnlyIdentityIndex() {
		assertThat(jdbcClient.sql("SHOW server_version").query(String.class).single()).isEqualTo("18.6");
		assertThat(flyway.info().applied()).singleElement().satisfies(migration ->
				assertThat(migration.getVersion().getVersion()).isEqualTo("1"));
		assertThat(flyway.info().pending()).isEmpty();

		var columns = jdbcClient.sql("""
				SELECT column_name, data_type, character_maximum_length, datetime_precision, is_nullable
				FROM information_schema.columns
				WHERE table_schema = 'marketdata' AND table_name = 'raw_chain_events'
				ORDER BY ordinal_position
				""").query((resultSet, rowNumber) -> new ColumnShape(
				resultSet.getString("column_name"),
				resultSet.getString("data_type"),
				(Integer) resultSet.getObject("character_maximum_length"),
				(Integer) resultSet.getObject("datetime_precision"),
				resultSet.getString("is_nullable"))).list();
		assertThat(columns).containsExactly(
				varchar("chain_id", 41, false), varchar("transaction_value", 512, false),
				varchar("event_locator", 256, false), varchar("provider", 64, false),
				new ColumnShape("observed_block_position", "bigint", null, null, "NO"),
				varchar("observed_block_hash", 256, true), timestamp("source_event_time", true),
				timestamp("observed_at", false), new ColumnShape("payload", "text", null, null, "NO"),
				varchar("payload_hash", 71, false), varchar("parser_version", 128, false),
				timestamp("ingested_at", false));
		assertThat(columns).extracting(ColumnShape::name)
				.doesNotContain("chain", "transaction_id", "event_id", "block_position", "block_hash");

		assertThat(primaryKeyColumns()).isEqualTo("chain_id,transaction_value,event_locator,provider");
		assertThat(checkConstraints()).containsExactly(
				"raw_chain_events_chain_id_caip2_check",
				"raw_chain_events_event_locator_format_check",
				"raw_chain_events_event_locator_utf8_length_check",
				"raw_chain_events_observed_block_hash_format_check",
				"raw_chain_events_observed_block_hash_utf8_length_check",
				"raw_chain_events_observed_block_position_non_negative_check",
				"raw_chain_events_parser_version_format_check",
				"raw_chain_events_parser_version_utf8_length_check",
				"raw_chain_events_payload_hash_format_check",
				"raw_chain_events_payload_not_blank_check",
				"raw_chain_events_provider_format_check",
				"raw_chain_events_provider_utf8_length_check",
				"raw_chain_events_transaction_value_format_check",
				"raw_chain_events_transaction_value_utf8_length_check");
		assertThat(jdbcClient.sql("""
				SELECT count(*) FROM pg_indexes
				WHERE schemaname = 'marketdata' AND tablename = 'raw_chain_events'
				""").query(Integer.class).single()).isOne();
	}

	@Test
	void acceptsMaximumUtf8KeyBudgetsAndRejectsOneByteOver() {
		var asciiMaximum = observation("t".repeat(512), "e".repeat(256), "p".repeat(64),
				1, Optional.empty(), Optional.empty(), OBSERVED_AT, PAYLOAD, "parser-v1");
		var multibyteMaximum = observation("😀".repeat(128), "😀".repeat(64), "😀".repeat(16),
				2, Optional.empty(), Optional.empty(), OBSERVED_AT, PAYLOAD, "parser-v1");
		var oneByteOverTransactionBudget = "a".repeat(509) + "😀";

		assertThat(useCase.store(asciiMaximum)).isEqualTo(StoreRawObservationOutcome.INSERTED);
		assertThat(useCase.store(multibyteMaximum)).isEqualTo(StoreRawObservationOutcome.INSERTED);
		assertThat(rowCount(jdbcClient)).isEqualTo(2);
		assertThatThrownBy(() -> directInsert(ChainId.SOLANA_MAINNET.value(),
				oneByteOverTransactionBudget, 3, "sha256:" + "0".repeat(64), "{}"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsMalformedNetworkDigestPositionAndBlankPayload() {
		assertThatThrownBy(() -> directInsert("solana", "bad-chain", 1,
				"sha256:" + "0".repeat(64), "{}"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> directInsert(ChainId.SOLANA_MAINNET.value(), "bad-digest", 1,
				"not-a-digest", "{}"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> directInsert(ChainId.SOLANA_MAINNET.value(), "negative", -1,
				"sha256:" + "0".repeat(64), "{}"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> directInsert(ChainId.SOLANA_MAINNET.value(), "blank-payload", 1,
				"sha256:" + "0".repeat(64), "\n\t"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(rowCount(jdbcClient)).isZero();
	}

	private String primaryKeyColumns() {
		return jdbcClient.sql("""
				SELECT string_agg(attribute.attname, ',' ORDER BY key_column.ordinality)
				FROM pg_constraint definition
				JOIN pg_class table_definition ON table_definition.oid = definition.conrelid
				JOIN pg_namespace schema_definition ON schema_definition.oid = table_definition.relnamespace
				CROSS JOIN LATERAL unnest(definition.conkey) WITH ORDINALITY AS key_column(attnum, ordinality)
				JOIN pg_attribute attribute ON attribute.attrelid = table_definition.oid
				    AND attribute.attnum = key_column.attnum
				WHERE schema_definition.nspname = 'marketdata'
				  AND table_definition.relname = 'raw_chain_events' AND definition.contype = 'p'
				""").query(String.class).single();
	}

	private List<String> checkConstraints() {
		return jdbcClient.sql("""
				SELECT conname FROM pg_constraint definition
				JOIN pg_class table_definition ON table_definition.oid = definition.conrelid
				JOIN pg_namespace schema_definition ON schema_definition.oid = table_definition.relnamespace
				WHERE schema_definition.nspname = 'marketdata'
				  AND table_definition.relname = 'raw_chain_events' AND definition.contype = 'c'
				ORDER BY conname
				""").query(String.class).list();
	}

	private void directInsert(
			String chainId, String transactionValue, long blockPosition, String payloadHash, String payload) {
		jdbcClient.sql("""
				INSERT INTO marketdata.raw_chain_events (
				    chain_id, transaction_value, event_locator, provider, observed_block_position,
				    observed_at, payload, payload_hash, parser_version, ingested_at
				) VALUES (:chainId, :transactionValue, 'direct-event', 'direct', :blockPosition,
				    :observedAt, :payload, :payloadHash, 'parser-v1', :ingestedAt)
				""")
				.param("chainId", chainId)
				.param("transactionValue", transactionValue)
				.param("blockPosition", blockPosition)
				.param("observedAt", OffsetDateTime.ofInstant(OBSERVED_AT, ZoneOffset.UTC))
				.param("payload", payload)
				.param("payloadHash", payloadHash)
				.param("ingestedAt", OffsetDateTime.ofInstant(OBSERVED_AT, ZoneOffset.UTC))
				.update();
	}

	private static ColumnShape varchar(String name, int length, boolean nullable) {
		return new ColumnShape(name, "character varying", length, null, nullable ? "YES" : "NO");
	}

	private static ColumnShape timestamp(String name, boolean nullable) {
		return new ColumnShape(name, "timestamp with time zone", null, 6, nullable ? "YES" : "NO");
	}

	private record ColumnShape(
			String name, String type, Integer maximumLength, Integer datetimePrecision, String nullable) {
	}
}
