package io.cryptoresearch.marketdata.application;

import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.FIRST_INGESTION;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.OBSERVED_AT;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.PAYLOAD;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.SECOND_INGESTION;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.SOURCE_TIME;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.observation;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.readSingleRow;
import static io.cryptoresearch.marketdata.application.RawChainEventFixtures.rowCount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

import io.cryptoresearch.kernel.api.ChainId;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(RawChainEventStorageIT.ClockConfiguration.class)
class RawChainEventStorageIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final StoreRawObservationUseCase useCase;
	private final JdbcClient jdbcClient;
	private final MutableUtcClock clock;

	@Autowired
	RawChainEventStorageIT(StoreRawObservationUseCase useCase, JdbcClient jdbcClient, MutableUtcClock clock) {
		this.useCase = useCase;
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	@BeforeEach
	void resetTableAndClock() {
		jdbcClient.sql("DELETE FROM marketdata.raw_chain_events").update();
		clock.set(FIRST_INGESTION);
	}

	@Test
	void insertsAndReadsBackCompleteEvidenceAtMicrosecondPrecision() {
		var input = observation("helius", 42, Optional.of("block-hash"), Optional.empty(),
				OBSERVED_AT, PAYLOAD, "parser-v1");

		assertThat(useCase.store(input)).isEqualTo(StoreRawObservationOutcome.INSERTED);
		var row = readSingleRow(jdbcClient);
		assertThat(row.chainId()).isEqualTo(ChainId.SOLANA_MAINNET.value());
		assertThat(row.transactionValue()).isEqualTo("tx-1");
		assertThat(row.eventLocator()).isEqualTo("event-1");
		assertThat(row.provider()).isEqualTo("helius");
		assertThat(row.observedBlockPosition()).isEqualTo(42);
		assertThat(row.observedBlockHash()).isEqualTo("block-hash");
		assertThat(row.sourceEventTime()).isNull();
		assertThat(row.observedAt()).isEqualTo(Instant.parse("2026-09-13T01:00:00.123456Z"));
		assertThat(row.payload()).isEqualTo(PAYLOAD);
		assertThat(row.payloadHash()).isEqualTo(PayloadFingerprint.sha256(PAYLOAD));
		assertThat(row.parserVersion()).isEqualTo("parser-v1");
		assertThat(row.ingestedAt()).isEqualTo(Instant.parse("2026-09-13T03:00:00.111222Z"));
	}

	@Test
	void acceptsEqualRetryWithoutChangingFirstTimesAndKeepsProvidersDistinct() {
		var first = observation("helius", 42, Optional.of("block-hash"), Optional.of(SOURCE_TIME),
				OBSERVED_AT, PAYLOAD, "parser-v1");
		assertThat(useCase.store(first)).isEqualTo(StoreRawObservationOutcome.INSERTED);

		clock.set(SECOND_INGESTION);
		var later = Instant.parse("2026-09-13T02:00:00.999999999Z");
		var retry = observation("helius", 42, Optional.of("block-hash"), Optional.of(SOURCE_TIME),
				later, PAYLOAD, "parser-v1");
		assertThat(useCase.store(retry)).isEqualTo(StoreRawObservationOutcome.ALREADY_PRESENT);
		assertThat(rowCount(jdbcClient)).isOne();
		assertThat(readSingleRow(jdbcClient).observedAt()).isEqualTo(Instant.parse("2026-09-13T01:00:00.123456Z"));
		assertThat(readSingleRow(jdbcClient).ingestedAt()).isEqualTo(Instant.parse("2026-09-13T03:00:00.111222Z"));

		var otherProvider = observation("bitquery", 42, Optional.of("block-hash"), Optional.of(SOURCE_TIME),
				later, PAYLOAD, "parser-v1");
		assertThat(useCase.store(otherProvider)).isEqualTo(StoreRawObservationOutcome.INSERTED);
		assertThat(rowCount(jdbcClient)).isEqualTo(2);
	}

	@Test
	void keepsEqualLocalIdentityValuesDistinctAcrossNetworksAndReferenceCase() {
		var chains = List.of(ChainId.SOLANA_MAINNET, new ChainId("eip155:8453"),
				new ChainId("example:Network"), new ChainId("example:network"));
		for (var chain : chains) {
			var input = observation(chain, "same-transaction", "same-event", "provider",
					42, Optional.empty(), Optional.empty(), OBSERVED_AT, PAYLOAD, "parser-v1");
			assertThat(useCase.store(input)).isEqualTo(StoreRawObservationOutcome.INSERTED);
		}
		assertThat(jdbcClient.sql("SELECT chain_id FROM marketdata.raw_chain_events")
				.query(String.class).list())
				.containsExactlyInAnyOrderElementsOf(chains.stream().map(ChainId::value).toList());
	}

	@Test
	void storesSyntheticEvmObservationThroughTheCommonContract() {
		var baseMainnet = new ChainId("eip155:8453");
		var input = observation(baseMainnet, "0xtransaction", "receipt.logs:3", "synthetic",
				19_000_000, Optional.of("0xblock"), Optional.of(SOURCE_TIME), OBSERVED_AT, PAYLOAD, "fixture-v1");

		assertThat(useCase.store(input)).isEqualTo(StoreRawObservationOutcome.INSERTED);
		var row = readSingleRow(jdbcClient);
		assertThat(row.chainId()).isEqualTo(baseMainnet.value());
		assertThat(row.transactionValue()).isEqualTo("0xtransaction");
		assertThat(row.eventLocator()).isEqualTo("receipt.logs:3");
		assertThat(row.observedBlockPosition()).isEqualTo(19_000_000);
	}

	@Test
	void rejectsEveryImmutableEvidenceConflictWithoutChangingTheFirstRow() {
		var first = observation("helius", 42, Optional.of("block-hash"), Optional.of(SOURCE_TIME),
				OBSERVED_AT, PAYLOAD, "parser-v1");
		assertThat(useCase.store(first)).isEqualTo(StoreRawObservationOutcome.INSERTED);
		var expected = readSingleRow(jdbcClient);

		var conflicts = List.of(
				observation("helius", 43, Optional.of("block-hash"), Optional.of(SOURCE_TIME), OBSERVED_AT, PAYLOAD, "parser-v1"),
				observation("helius", 42, Optional.of("other-hash"), Optional.of(SOURCE_TIME), OBSERVED_AT, PAYLOAD, "parser-v1"),
				observation("helius", 42, Optional.of("block-hash"), Optional.empty(), OBSERVED_AT, PAYLOAD, "parser-v1"),
				observation("helius", 42, Optional.of("block-hash"), Optional.of(SOURCE_TIME), OBSERVED_AT, "{\"event\":2}", "parser-v1"),
				observation("helius", 42, Optional.of("block-hash"), Optional.of(SOURCE_TIME), OBSERVED_AT, PAYLOAD, "parser-v2"));
		for (var conflict : conflicts) {
			assertThatThrownBy(() -> useCase.store(conflict))
					.isInstanceOf(RawObservationConflictException.class)
					.hasMessageContaining(ChainId.SOLANA_MAINNET.value() + "/tx-1/event-1/helius");
			assertThat(rowCount(jdbcClient)).isOne();
			assertThat(readSingleRow(jdbcClient)).isEqualTo(expected);
		}
	}

	@Test
	void concurrentEqualTransactionsConvergeWithoutApplicationLock() throws Exception {
		var input = observation("helius", 42, Optional.of("block-hash"), Optional.of(SOURCE_TIME),
				OBSERVED_AT, PAYLOAD, "parser-v1");
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> storeAfter(start, ready, input));
			var second = executor.submit(() -> storeAfter(start, ready, input));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(StoreRawObservationOutcome.INSERTED, StoreRawObservationOutcome.ALREADY_PRESENT);
		}
		assertThat(rowCount(jdbcClient)).isOne();
	}

	private StoreRawObservationOutcome storeAfter(
			CountDownLatch start, CountDownLatch ready, RawChainObservation input) throws InterruptedException {
		ready.countDown();
		start.await();
		return useCase.store(input);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {
		@Bean
		@Primary
		MutableUtcClock testClock() {
			return new MutableUtcClock(FIRST_INGESTION);
		}
	}
}
