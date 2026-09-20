package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.TransactionId;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(RawTransactionStorageIT.ClockConfiguration.class)
class RawTransactionStorageIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private static final ChainId CHAIN = ChainId.SOLANA_MAINNET;
	private static final Instant RECEIVED = Instant.parse("2026-09-20T10:00:00Z");
	private static final Instant ADMITTED = Instant.parse("2026-09-20T10:00:05Z");
	private static final Instant INGESTED = Instant.parse("2026-09-20T10:00:06Z");
	private static final Instant LATER = Instant.parse("2026-09-20T11:00:00Z");

	private final StoreRawTransactionUseCase useCase;
	private final JdbcClient jdbcClient;
	private final MutableUtcClock clock;

	@Autowired
	RawTransactionStorageIT(StoreRawTransactionUseCase useCase, JdbcClient jdbcClient, MutableUtcClock clock) {
		this.useCase = useCase;
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	@BeforeEach
	void resetTable() {
		jdbcClient.sql("DELETE FROM marketdata.raw_transactions").update();
		clock.set(INGESTED);
	}

	@Test
	void equalRetryKeepsFirstRecordAndTimes() {
		var first = useCase.store(payload(1));
		clock.set(LATER);
		var retry = useCase.store(payload(1));

		assertThat(retry.ingestedAt()).isEqualTo(INGESTED);
		assertThat(retry).isEqualTo(first);
		assertThat(rowCount()).isEqualTo(1);
		assertThat(jdbcClient.sql(
				"SELECT ingested_at FROM marketdata.raw_transactions WHERE transaction_value = 'fixture-tx-1'")
				.query(java.time.OffsetDateTime.class).single().toInstant()).isEqualTo(INGESTED);
	}

	@Test
	void conflictingPayloadIsRejectedWithoutOverwrite() {
		useCase.store(payload(1));
		var conflicting = new RawTransactionPayload(
				CHAIN, transaction(1), "provider-a", new BlockPosition(CHAIN, 100), java.util.Optional.empty(),
				java.util.Optional.empty(), RECEIVED, ADMITTED, "{\"different\":true}", java.util.Optional.empty());

		assertThatThrownBy(() -> useCase.store(conflicting))
				.isInstanceOf(RawTransactionConflictException.class);
		assertThat(rowCount()).isEqualTo(1);
		assertThat(jdbcClient.sql(
				"SELECT payload FROM marketdata.raw_transactions WHERE transaction_value = 'fixture-tx-1'")
				.query(String.class).single()).isEqualTo("{\"i\":1}");
	}

	@Test
	void batchInsertIsIdempotentWithInternalDuplicates() {
		var batch = List.of(payload(1), payload(2), payload(1), payload(3));
		var stored = useCase.storeBatch(batch);

		assertThat(stored).hasSize(4);
		assertThat(rowCount()).isEqualTo(3);
		clock.set(LATER);
		useCase.storeBatch(batch);
		assertThat(rowCount()).isEqualTo(3);
		assertThat(jdbcClient.sql("SELECT count(DISTINCT ingested_at) FROM marketdata.raw_transactions")
				.query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void volumeBatchStoresEveryDistinctIdentity() {
		var batch = new ArrayList<RawTransactionPayload>();
		for (var index = 0; index < 10_000; index++) {
			batch.add(payload(index));
		}
		batch.add(payload(0));
		batch.add(payload(9_999));

		var stored = useCase.storeBatch(batch);

		assertThat(stored).hasSize(10_002);
		assertThat(rowCount()).isEqualTo(10_000);
		assertThat(stored.stream().map(value -> value.ingestedAt()).distinct()).containsOnly(INGESTED);
	}

	@Test
	void recordRejectsUnorderedTimes() {
		assertThatThrownBy(() -> new RawTransactionPayload(
				CHAIN, transaction(1), "provider-a", new BlockPosition(CHAIN, 100), java.util.Optional.empty(),
				java.util.Optional.empty(), ADMITTED, RECEIVED, "{}", java.util.Optional.empty()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private RawTransactionPayload payload(int index) {
		return new RawTransactionPayload(
				CHAIN, transaction(index), "provider-a", new BlockPosition(CHAIN, 100 + index),
				java.util.Optional.of("hash-" + index), java.util.Optional.of(RECEIVED),
				RECEIVED, ADMITTED, "{\"i\":" + index + "}", java.util.Optional.of("parser-v1"));
	}

	private TransactionId transaction(int index) {
		return new TransactionId(CHAIN, "fixture-tx-" + index);
	}

	private int rowCount() {
		return jdbcClient.sql("SELECT count(*) FROM marketdata.raw_transactions").query(Integer.class).single();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {
		@Bean
		@Primary
		MutableUtcClock testClock() {
			return new MutableUtcClock(INGESTED);
		}
	}
}
