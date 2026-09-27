package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedDataset;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.marketdata.api.MarketDataApi.ReplayItem;
import io.cryptoresearch.marketdata.api.MarketDataApi.ReplayStatus;

class RecordedMarketDataReplayTelemetryTest {

	@Test
	void recordsMixedResultsAndAbortPrefixWithFixedDimensionsAndRawFirstOrder() {
		RecordedReplayTelemetryTestSupport.withHarness(harness -> {
			var order = new java.util.ArrayList<String>();
			var normalizationFailure = new IllegalArgumentException("sensitive normalization detail");
			var storageFailure = new IllegalStateException("sensitive storage detail");
			doAnswer(invocation -> {
				var raw = (RawChainObservation) invocation.getArgument(0);
				order.add("store:" + raw.transactionId().value());
				if (raw.provider().equals("storage-failure")) {
					throw storageFailure;
				}
				return null;
			}).when(harness.rawStore).store(any(RawChainObservation.class));
			doAnswer(invocation -> {
				var input = (RecordedSwapInput) invocation.getArgument(0);
				order.add("normalize:" + input.transactionId().value());
				if (input.payload().equals("payload-normalization-failure")) {
					throw normalizationFailure;
				}
				return null;
			}).when(harness.normalizer).normalize(any(RecordedSwapInput.class), anyString());

			var mixed = RecordedReplayTelemetryTestSupport.dataset(
					"fixture-secret-mixed",
					RecordedReplayTelemetryTestSupport.input("mixed-1", "payload-one", "provider-secret"),
					RecordedReplayTelemetryTestSupport.input(
							"mixed-2", "payload-normalization-failure", "provider-secret"),
					RecordedReplayTelemetryTestSupport.input("mixed-3", "payload-three", "provider-secret"));
			var mixedResult = harness.service.replay(mixed);

			assertThat(mixedResult.items()).extracting(ReplayItem::status)
					.containsExactly(ReplayStatus.NORMALIZED, ReplayStatus.NORMALIZATION_FAILED, ReplayStatus.NORMALIZED);
			assertThat(mixedResult.items().get(1).failure()).contains("sensitive normalization detail");
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.ITEMS, "status", "normalized"))
				.isEqualTo(2.0);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.ITEMS, "status", "normalization_failed"))
				.isEqualTo(1.0);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.INVOCATIONS, "outcome", "completed"))
				.isEqualTo(1.0);
			assertSummary(harness.appender.list.get(0), "completed", 3, 2, 1, 0);

			var before = RecordedReplayTelemetryTestSupport.counterValues(harness.registry);
			var abort = RecordedReplayTelemetryTestSupport.dataset(
					"fixture-secret-abort",
					RecordedReplayTelemetryTestSupport.input("abort-1", "payload-four", "provider-secret"),
					RecordedReplayTelemetryTestSupport.input(
							"abort-2", "payload-normalization-failure", "provider-secret"),
					RecordedReplayTelemetryTestSupport.input("abort-3", "payload-five", "storage-failure"),
					RecordedReplayTelemetryTestSupport.input("abort-4", "must-not-run", "provider-secret"));

			assertThatThrownBy(() -> harness.service.replay(abort)).isSameAs(storageFailure);
			var after = RecordedReplayTelemetryTestSupport.counterValues(harness.registry);
			assertThat(after.normalized - before.normalized).isEqualTo(1.0);
			assertThat(after.normalizationFailed - before.normalizationFailed).isEqualTo(1.0);
			assertThat(after.completed - before.completed).isZero();
			assertThat(after.aborted - before.aborted).isEqualTo(1.0);
			assertSummary(harness.appender.list.get(1), "aborted", 3, 1, 1, 1);
			assertThat(order).containsExactly(
					"store:synthetic-tx-mixed-1", "normalize:synthetic-tx-mixed-1",
					"store:synthetic-tx-mixed-2", "normalize:synthetic-tx-mixed-2",
					"store:synthetic-tx-mixed-3", "normalize:synthetic-tx-mixed-3",
					"store:synthetic-tx-abort-1", "normalize:synthetic-tx-abort-1",
					"store:synthetic-tx-abort-2", "normalize:synthetic-tx-abort-2",
					"store:synthetic-tx-abort-3");
			assertFixedDimensions(harness.registry);
		});
	}

	@Test
	void repeatedAndEmptyCallsCountAttemptsWhileNullIsRejectedBeforeTelemetry() {
		RecordedReplayTelemetryTestSupport.withHarness(harness -> {
			var dataset = RecordedReplayTelemetryTestSupport.dataset(
					"fixture-secret-repeat",
					RecordedReplayTelemetryTestSupport.input("repeat-1", "repeat-payload-one", "repeat-provider"),
					RecordedReplayTelemetryTestSupport.input("repeat-2", "repeat-payload-two", "repeat-provider"));

			var first = harness.service.replay(dataset);
			var second = harness.service.replay(dataset);
			var empty = harness.service.replay(RecordedReplayTelemetryTestSupport.dataset("empty-secret"));
			var beforeNull = RecordedReplayTelemetryTestSupport.counterValues(harness.registry);

			assertThatThrownBy(() -> harness.service.replay(null))
					.isInstanceOf(NullPointerException.class)
					.hasMessage("dataset must not be null");
			var afterNull = RecordedReplayTelemetryTestSupport.counterValues(harness.registry);

			assertThat(first.items()).hasSize(2).allMatch(item -> item.status() == ReplayStatus.NORMALIZED);
			assertThat(second).isEqualTo(first);
			assertThat(empty.items()).isEmpty();
			assertThat(afterNull.normalized).isEqualTo(4.0);
			assertThat(afterNull.normalizationFailed).isZero();
			assertThat(afterNull.completed).isEqualTo(3.0);
			assertThat(afterNull.aborted).isZero();
			assertThat(afterNull.normalized).isEqualTo(beforeNull.normalized);
			assertThat(afterNull.normalizationFailed).isEqualTo(beforeNull.normalizationFailed);
			assertThat(afterNull.completed).isEqualTo(beforeNull.completed);
			assertThat(afterNull.aborted).isEqualTo(beforeNull.aborted);
			assertThat(harness.appender.list).hasSize(3);
			assertSummary(harness.appender.list.get(0), "completed", 2, 2, 0, 0);
			assertSummary(harness.appender.list.get(1), "completed", 2, 2, 0, 0);
			assertSummary(harness.appender.list.get(2), "completed", 0, 0, 0, 0);
		});
	}

	@Test
	void rawConstructionFailureCountsTheStartedItemAndDoesNotTouchLaterItems() {
		RecordedReplayTelemetryTestSupport.withHarness(harness -> {
			var valid = RecordedReplayTelemetryTestSupport.input("construction-1", "valid-payload", "provider-safe");
			var invalid = RecordedReplayTelemetryTestSupport.input("construction-2", "second-payload", null);
			var later = RecordedReplayTelemetryTestSupport.input("construction-3", "must-not-run", "provider-safe");
			var dataset = RecordedReplayTelemetryTestSupport.dataset("fixture-secret-construction", valid, invalid, later);

			assertThatThrownBy(() -> harness.service.replay(dataset))
					.isInstanceOf(NullPointerException.class)
					.hasMessage("provider must not be null");

			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.ITEMS, "status", "normalized"))
				.isEqualTo(1.0);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.INVOCATIONS, "outcome", "aborted"))
				.isEqualTo(1.0);
			org.mockito.Mockito.verify(harness.rawStore, org.mockito.Mockito.times(1))
				.store(any(RawChainObservation.class));
			org.mockito.Mockito.verify(harness.normalizer, org.mockito.Mockito.times(1))
				.normalize(any(RecordedSwapInput.class), anyString());
			assertThat(harness.appender.list).hasSize(1);
			assertSummary(harness.appender.list.getFirst(), "aborted", 2, 1, 0, 1);
		});
	}

	@Test
	void handledNormalizationConflictRemainsACompletedResult() {
		RecordedReplayTelemetryTestSupport.withHarness(harness -> {
			var conflict = RecordedReplayTelemetryTestSupport.input("conflict", "conflict-payload", "provider-secret");
			var success = RecordedReplayTelemetryTestSupport.input("conflict-next", "next-payload", "provider-secret");
			var identity = new NormalizedSwapIdentity(conflict.chain(), conflict.transactionId(), conflict.eventId());
			doAnswer(invocation -> {
				var input = (RecordedSwapInput) invocation.getArgument(0);
				if (input.payload().equals("conflict-payload")) {
					throw new NormalizationConflictException(identity);
				}
				return null;
			}).when(harness.normalizer).normalize(any(RecordedSwapInput.class), anyString());

			var result = harness.service.replay(RecordedReplayTelemetryTestSupport.dataset(
					"conflict-fixture-secret", conflict, success));

			assertThat(result.items()).extracting(ReplayItem::status)
					.containsExactly(ReplayStatus.NORMALIZATION_FAILED, ReplayStatus.NORMALIZED);
			assertThat(result.items().getFirst().failure()).isPresent().get().asString()
					.contains(identity.transactionId().value());
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.ITEMS, "status", "normalization_failed"))
				.isEqualTo(1.0);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.ITEMS, "status", "normalized"))
				.isEqualTo(1.0);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.INVOCATIONS, "outcome", "completed"))
				.isEqualTo(1.0);
			assertThat(harness.appender.list).hasSize(1);
			assertSummary(harness.appender.list.getFirst(), "completed", 2, 1, 1, 0);
		});
	}

	private static void assertSummary(
			ILoggingEvent event, String outcome, int attempted, int normalized, int normalizationFailed, int unclassified) {
		assertThat(event.getMessage()).isEqualTo("Recorded replay summary");
		assertThat(event.getThrowableProxy()).isNull();
		assertThat(RecordedReplayTelemetryTestSupport.fields(event)).isEqualTo(Map.of(
				"operation", "recorded_replay",
				"outcome", outcome,
				"attempted", attempted,
				"normalized", normalized,
				"normalization_failed", normalizationFailed,
				"unclassified", unclassified));
		assertThat(RecordedReplayTelemetryTestSupport.fields(event).values())
				.doesNotContain("fixture-secret-mixed", "payload-normalization-failure", "provider-secret",
						"sensitive normalization detail", "sensitive storage detail");
	}

	private static void assertFixedDimensions(MeterRegistry registry) {
		var itemIds = registry.getMeters().stream().map(Meter::getId)
				.filter(id -> id.getName().equals(RecordedReplayTelemetryTestSupport.ITEMS)).toList();
		var invocationIds = registry.getMeters().stream().map(Meter::getId)
				.filter(id -> id.getName().equals(RecordedReplayTelemetryTestSupport.INVOCATIONS)).toList();
		assertThat(itemIds).hasSize(2).allSatisfy(id -> assertThat(id.getTags()).hasSize(1));
		assertThat(itemIds).extracting(id -> id.getTag("status"))
				.containsExactlyInAnyOrder("normalized", "normalization_failed");
		assertThat(invocationIds).hasSize(2).allSatisfy(id -> assertThat(id.getTags()).hasSize(1));
		assertThat(invocationIds).extracting(id -> id.getTag("outcome"))
				.containsExactlyInAnyOrder("completed", "aborted");
		assertThat(registry.getMeters()).hasSize(4);
	}
}

final class RecordedReplayTelemetryTestSupport {

	static final String ITEMS = "crypto.research.marketdata.replay.items";
	static final String INVOCATIONS = "crypto.research.marketdata.replay.invocations";

	private RecordedReplayTelemetryTestSupport() {
	}

	static void withHarness(java.util.function.Consumer<ReplayHarness> scenario) {
		withHarness(new SimpleMeterRegistry(), scenario);
	}

	static void withHarness(MeterRegistry registry, java.util.function.Consumer<ReplayHarness> scenario) {
		var rawStore = mock(StoreRawObservationUseCase.class);
		var normalizer = mock(NormalizeRecordedSwapUseCase.class);
		var normalizedStore = mock(NormalizedMarketDataStore.class);
		new ApplicationContextRunner()
				.withBean(StoreRawObservationUseCase.class, () -> rawStore)
				.withBean(NormalizeRecordedSwapUseCase.class, () -> normalizer)
				.withBean(NormalizedMarketDataStore.class, () -> normalizedStore)
				.withBean(MeterRegistry.class, () -> registry)
				.withUserConfiguration(ServiceConfiguration.class)
				.run(context -> {
					var loggerContext = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
					var logger = loggerContext.getLogger(RecordedMarketDataService.class);
					var priorLevel = logger.getLevel();
					var appender = new ListAppender<ILoggingEvent>();
					appender.setContext(loggerContext);
					appender.start();
					logger.setLevel(Level.INFO);
					logger.addAppender(appender);
					try {
						scenario.accept(new ReplayHarness(
								context.getBean(RecordedMarketDataService.class), registry, rawStore, normalizer,
								appender, logger, loggerContext));
					}
					finally {
						logger.detachAppender(appender);
						logger.setLevel(priorLevel);
						appender.stop();
					}
				});
	}

	static RecordedDataset dataset(String fixtureVersion, RecordedSwapInput... inputs) {
		return new RecordedDataset(fixtureVersion, List.of(inputs));
	}

	static RecordedSwapInput input(String id, String payload, String provider) {
		var chain = ChainId.SOLANA_MAINNET;
		var transaction = new TransactionId(chain, "synthetic-tx-" + id);
		return new RecordedSwapInput(
				chain,
				transaction,
				new EventId(transaction, "synthetic-event-" + id),
				provider,
				new BlockPosition(chain, 42),
				Optional.empty(),
				Optional.of(Instant.parse("2026-09-01T00:00:00Z")),
				Instant.parse("2026-09-01T00:00:01Z"),
				payload,
				"synthetic-parser-v1");
	}

	static double counter(MeterRegistry registry, String name, String tagKey, String tagValue) {
		var counter = registry.find(name).tag(tagKey, tagValue).counter();
		return counter == null ? 0.0 : counter.count();
	}

	static CounterValues counterValues(MeterRegistry registry) {
		return new CounterValues(
				counter(registry, ITEMS, "status", "normalized"),
				counter(registry, ITEMS, "status", "normalization_failed"),
				counter(registry, INVOCATIONS, "outcome", "completed"),
				counter(registry, INVOCATIONS, "outcome", "aborted"));
	}

	static Map<String, Object> fields(ILoggingEvent event) {
		var pairs = event.getKeyValuePairs();
		if (pairs == null) {
			return Map.of();
		}
		return pairs.stream().collect(java.util.stream.Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}

	static final class CounterValues {
		final double normalized;
		final double normalizationFailed;
		final double completed;
		final double aborted;

		CounterValues(double normalized, double normalizationFailed, double completed, double aborted) {
			this.normalized = normalized;
			this.normalizationFailed = normalizationFailed;
			this.completed = completed;
			this.aborted = aborted;
		}
	}

	static final class ReplayHarness {
		final RecordedMarketDataService service;
		final MeterRegistry registry;
		final StoreRawObservationUseCase rawStore;
		final NormalizeRecordedSwapUseCase normalizer;
		final ListAppender<ILoggingEvent> appender;
		final Logger logger;
		final LoggerContext loggerContext;

		ReplayHarness(
				RecordedMarketDataService service,
				MeterRegistry registry,
				StoreRawObservationUseCase rawStore,
				NormalizeRecordedSwapUseCase normalizer,
				ListAppender<ILoggingEvent> appender,
				Logger logger,
				LoggerContext loggerContext) {
			this.service = service;
			this.registry = registry;
			this.rawStore = rawStore;
			this.normalizer = normalizer;
			this.appender = appender;
			this.logger = logger;
			this.loggerContext = loggerContext;
		}
	}

	@Configuration(proxyBeanMethods = false)
	@Import(RecordedMarketDataService.class)
	static class ServiceConfiguration {
	}
}
