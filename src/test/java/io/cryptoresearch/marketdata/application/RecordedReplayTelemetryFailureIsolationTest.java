package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.cryptoresearch.marketdata.api.MarketDataApi.RecordedSwapInput;
import io.cryptoresearch.marketdata.api.MarketDataApi.ReplayStatus;

class RecordedReplayTelemetryFailureIsolationTest {

	@Test
	void unexpectedNormalizationFailurePreservesExceptionAndStopsAfterStartedPrefix() {
		RecordedReplayTelemetryTestSupport.withHarness(harness -> {
			var order = new java.util.ArrayList<String>();
			var normalizationFailure = new IllegalStateException("unexpected normalization detail");
			org.mockito.Mockito.doAnswer(invocation -> {
				var raw = (RawChainObservation) invocation.getArgument(0);
				order.add("store:" + raw.transactionId().value());
				return null;
			}).when(harness.rawStore).store(org.mockito.ArgumentMatchers.any(RawChainObservation.class));
			org.mockito.Mockito.doAnswer(invocation -> {
				var input = (RecordedSwapInput) invocation.getArgument(0);
				order.add("normalize:" + input.transactionId().value());
				if (input.payload().equals("unexpected-payload")) {
					throw normalizationFailure;
				}
				return null;
			}).when(harness.normalizer).normalize(
					org.mockito.ArgumentMatchers.any(RecordedSwapInput.class), org.mockito.ArgumentMatchers.anyString());
			var dataset = RecordedReplayTelemetryTestSupport.dataset(
					"fixture-secret-unexpected",
					RecordedReplayTelemetryTestSupport.input("unexpected-1", "valid-payload", "provider-secret"),
					RecordedReplayTelemetryTestSupport.input("unexpected-2", "unexpected-payload", "provider-secret"),
					RecordedReplayTelemetryTestSupport.input("unexpected-3", "must-not-run", "provider-secret"));

			assertThatThrownBy(() -> harness.service.replay(dataset)).isSameAs(normalizationFailure);
			assertThat(order).containsExactly(
					"store:synthetic-tx-unexpected-1", "normalize:synthetic-tx-unexpected-1",
					"store:synthetic-tx-unexpected-2", "normalize:synthetic-tx-unexpected-2");
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.ITEMS, "status", "normalized"))
				.isEqualTo(1.0);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					harness.registry, RecordedReplayTelemetryTestSupport.INVOCATIONS, "outcome", "aborted"))
				.isEqualTo(1.0);
			assertThat(harness.appender.list).hasSize(1);
			assertThat(harness.appender.list.getFirst().getMessage()).isEqualTo("Recorded replay summary");
			assertThat(RecordedReplayTelemetryTestSupport.fields(harness.appender.list.getFirst()))
					.containsEntry("outcome", "aborted")
					.containsEntry("attempted", 2)
					.containsEntry("normalized", 1)
					.containsEntry("normalization_failed", 0)
					.containsEntry("unclassified", 1);
			assertThat(harness.appender.list.getFirst().getThrowableProxy()).isNull();
		});
	}

	@Test
	void registrationFailureDoesNotRetryOrPreventSummaryAndResult() {
		var failure = new IllegalStateException("metric registration failure");
		var registry = new RegistrationFailureRegistry(RecordedReplayTelemetryTestSupport.ITEMS, failure);
		RecordedReplayTelemetryTestSupport.withHarness(registry, harness -> {
			var dataset = RecordedReplayTelemetryTestSupport.dataset(
					"registration-secret",
					RecordedReplayTelemetryTestSupport.input("registration", "registration-payload", "registration-provider"));

			var result = harness.service.replay(dataset);

			assertThat(result.items()).hasSize(1).first().extracting(item -> item.status())
					.isEqualTo(ReplayStatus.NORMALIZED);
			assertThat(registry.failedRegistrationAttempts).isEqualTo(1);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					registry, RecordedReplayTelemetryTestSupport.INVOCATIONS, "outcome", "completed"))
				.isEqualTo(1.0);
			assertThat(harness.appender.list).hasSize(1);
			assertThat(harness.appender.list.getFirst().getMessage()).isEqualTo("Recorded replay summary");
		});
	}

	@Test
	void incrementFailureDoesNotRetryOrPreventSummaryAndResult() {
		var failure = new IllegalStateException("metric increment failure");
		var registry = new IncrementFailureRegistry(RecordedReplayTelemetryTestSupport.ITEMS, failure);
		RecordedReplayTelemetryTestSupport.withHarness(registry, harness -> {
			var dataset = RecordedReplayTelemetryTestSupport.dataset(
					"increment-secret",
					RecordedReplayTelemetryTestSupport.input("increment", "increment-payload", "increment-provider"));

			var result = harness.service.replay(dataset);

			assertThat(result.items()).hasSize(1).first().extracting(item -> item.status())
					.isEqualTo(ReplayStatus.NORMALIZED);
			assertThat(registry.failedIncrementAttempts).isEqualTo(1);
			assertThat(RecordedReplayTelemetryTestSupport.counter(
					registry, RecordedReplayTelemetryTestSupport.INVOCATIONS, "outcome", "completed"))
				.isEqualTo(1.0);
			assertThat(harness.appender.list).hasSize(1);
		});
	}

	@Test
	void logFailureDoesNotUndoCountersOrDomainResultAndIsNotRetried() {
		RecordedReplayTelemetryTestSupport.withHarness(harness -> {
			var logFailure = new IllegalStateException("logging sink failure");
			var filter = new ThrowingInfoFilter(logFailure);
			filter.start();
			harness.loggerContext.addTurboFilter(filter);
			try {
				assertThatThrownBy(() -> harness.logger.atInfo().log("test-only logging failure probe"))
						.isSameAs(logFailure);
				assertThat(filter.attempts).isEqualTo(1);
				filter.reset();

				var result = harness.service.replay(RecordedReplayTelemetryTestSupport.dataset(
						"logging-secret",
						RecordedReplayTelemetryTestSupport.input("logging", "logging-payload", "logging-provider")));

				assertThat(result.items()).hasSize(1).first().extracting(item -> item.status())
						.isEqualTo(ReplayStatus.NORMALIZED);
				assertThat(RecordedReplayTelemetryTestSupport.counter(
						harness.registry, RecordedReplayTelemetryTestSupport.ITEMS, "status", "normalized"))
					.isEqualTo(1.0);
				assertThat(RecordedReplayTelemetryTestSupport.counter(
						harness.registry, RecordedReplayTelemetryTestSupport.INVOCATIONS, "outcome", "completed"))
					.isEqualTo(1.0);
				assertThat(filter.attempts).isEqualTo(1);
				assertThat(harness.appender.list).isEmpty();
			}
			finally {
				harness.loggerContext.getTurboFilterList().remove(filter);
				filter.stop();
			}
		});
	}

	@Test
	void fatalErrorFromTelemetryIsNotCaughtAsARuntimeSinkFailure() {
		var fatal = new AssertionError("fatal telemetry error");
		var registry = new ErrorRegistry(RecordedReplayTelemetryTestSupport.ITEMS, fatal);
		RecordedReplayTelemetryTestSupport.withHarness(registry, harness -> assertThatThrownBy(() ->
				harness.service.replay(RecordedReplayTelemetryTestSupport.dataset(
						"fatal-secret",
						RecordedReplayTelemetryTestSupport.input("fatal", "fatal-payload", "fatal-provider"))))
				.isSameAs(fatal));
	}

	@Test
	void infoDisabledPreservesCompletedResultAndOriginalAbortException() {
		RecordedReplayTelemetryTestSupport.withHarness(harness -> {
			var storageFailure = new IllegalStateException("storage exception identity");
			org.mockito.Mockito.doAnswer(invocation -> {
				var raw = (RawChainObservation) invocation.getArgument(0);
				if (raw.provider().equals("disabled-info-failure")) {
					throw storageFailure;
				}
				return null;
			}).when(harness.rawStore).store(org.mockito.ArgumentMatchers.any(RawChainObservation.class));
			harness.logger.setLevel(Level.WARN);
			try {
				var completed = harness.service.replay(RecordedReplayTelemetryTestSupport.dataset(
						"disabled-info-success",
						RecordedReplayTelemetryTestSupport.input("disabled-success", "success-payload", "safe-provider")));
				assertThat(completed.items()).hasSize(1).allMatch(item -> item.status() == ReplayStatus.NORMALIZED);

				assertThatThrownBy(() -> harness.service.replay(RecordedReplayTelemetryTestSupport.dataset(
						"disabled-info-abort",
						RecordedReplayTelemetryTestSupport.input("disabled-failure", "failure-payload", "disabled-info-failure"),
						RecordedReplayTelemetryTestSupport.input("disabled-later", "must-not-run", "safe-provider"))))
					.isSameAs(storageFailure);
				org.mockito.Mockito.verify(harness.rawStore, org.mockito.Mockito.times(2))
					.store(org.mockito.ArgumentMatchers.any(RawChainObservation.class));
				org.mockito.Mockito.verify(harness.normalizer, org.mockito.Mockito.times(1))
					.normalize(org.mockito.ArgumentMatchers.any(RecordedSwapInput.class), org.mockito.ArgumentMatchers.anyString());
				assertThat(harness.appender.list).isEmpty();
			}
			finally {
				harness.logger.setLevel(null);
			}
		});
	}

	@Test
	void metricAndLogSinkFailuresCannotMaskOrRetryStorageAbort() {
		var metricFailure = new IllegalStateException("metric sink failure");
		var registry = new FailingAllCountersRegistry(metricFailure);
		RecordedReplayTelemetryTestSupport.withHarness(registry, harness -> {
			var order = new java.util.ArrayList<String>();
			var storageFailure = new IllegalStateException("original storage exception");
			org.mockito.Mockito.doAnswer(invocation -> {
				var raw = (RawChainObservation) invocation.getArgument(0);
				order.add("store:" + raw.transactionId().value());
				if (raw.provider().equals("r9-storage-failure")) {
					throw storageFailure;
				}
				return null;
			}).when(harness.rawStore).store(org.mockito.ArgumentMatchers.any(RawChainObservation.class));
			org.mockito.Mockito.doAnswer(invocation -> {
				var input = (RecordedSwapInput) invocation.getArgument(0);
				order.add("normalize:" + input.transactionId().value());
				return null;
			}).when(harness.normalizer).normalize(
					org.mockito.ArgumentMatchers.any(RecordedSwapInput.class), org.mockito.ArgumentMatchers.anyString());
			var logFailure = new IllegalStateException("summary sink failure");
			var filter = new ThrowingInfoFilter(logFailure);
			filter.start();
			harness.loggerContext.addTurboFilter(filter);
			try {
				var dataset = RecordedReplayTelemetryTestSupport.dataset(
						"r9-fixture-secret",
						RecordedReplayTelemetryTestSupport.input("r9-first", "valid-payload", "safe-provider"),
						RecordedReplayTelemetryTestSupport.input("r9-fail", "failure-payload", "r9-storage-failure"),
						RecordedReplayTelemetryTestSupport.input("r9-later", "must-not-run", "safe-provider"));

				assertThatThrownBy(() -> harness.service.replay(dataset)).isSameAs(storageFailure);
				assertThat(order).containsExactly(
						"store:synthetic-tx-r9-first", "normalize:synthetic-tx-r9-first",
						"store:synthetic-tx-r9-fail");
				assertThat(registry.attempts).containsExactly(
						RecordedReplayTelemetryTestSupport.ITEMS + ":status=normalized",
						RecordedReplayTelemetryTestSupport.INVOCATIONS + ":outcome=aborted");
				assertThat(filter.attempts).isEqualTo(1);
				assertThat(harness.appender.list).isEmpty();
				org.mockito.Mockito.verify(harness.rawStore, org.mockito.Mockito.times(2))
					.store(org.mockito.ArgumentMatchers.any(RawChainObservation.class));
				org.mockito.Mockito.verify(harness.normalizer, org.mockito.Mockito.times(1))
					.normalize(org.mockito.ArgumentMatchers.any(RecordedSwapInput.class), org.mockito.ArgumentMatchers.anyString());
			}
			finally {
				harness.loggerContext.getTurboFilterList().remove(filter);
				filter.stop();
			}
		});
	}

	private static final class RegistrationFailureRegistry extends SimpleMeterRegistry {

		private final String failingName;
		private final RuntimeException failure;
		private int failedRegistrationAttempts;

		private RegistrationFailureRegistry(String failingName, RuntimeException failure) {
			this.failingName = failingName;
			this.failure = failure;
		}

		@Override
		public Counter counter(String name, Tags tags) {
			if (failingName.equals(name)) {
				failedRegistrationAttempts++;
				throw failure;
			}
			return super.counter(name, tags);
		}
	}

	private static final class IncrementFailureRegistry extends SimpleMeterRegistry {

		private final String failingName;
		private final RuntimeException failure;
		private int failedIncrementAttempts;

		private IncrementFailureRegistry(String failingName, RuntimeException failure) {
			this.failingName = failingName;
			this.failure = failure;
		}

		@Override
		protected Counter newCounter(Meter.Id id) {
			if (!failingName.equals(id.getName())) {
				return super.newCounter(id);
			}
			return new Counter() {
				@Override
				public void increment(double amount) {
					failedIncrementAttempts++;
					throw failure;
				}

				@Override
				public double count() {
					return 0.0;
				}

				@Override
				public Meter.Id getId() {
					return id;
				}
			};
		}
	}

	private static final class ErrorRegistry extends SimpleMeterRegistry {

		private final String failingName;
		private final Error failure;

		private ErrorRegistry(String failingName, Error failure) {
			this.failingName = failingName;
			this.failure = failure;
		}

		@Override
		public Counter counter(String name, Tags tags) {
			if (failingName.equals(name)) {
				throw failure;
			}
			return super.counter(name, tags);
		}
	}

	private static final class FailingAllCountersRegistry extends SimpleMeterRegistry {

		private final RuntimeException failure;
		private final java.util.List<String> attempts = new java.util.ArrayList<>();

		private FailingAllCountersRegistry(RuntimeException failure) {
			this.failure = failure;
		}

		@Override
		public Counter counter(String name, Tags tags) {
			var tag = tags.iterator().next();
			attempts.add(name + ":" + tag.getKey() + "=" + tag.getValue());
			throw failure;
		}
	}

	private static final class ThrowingInfoFilter extends TurboFilter {

		private final RuntimeException failure;
		private int attempts;
		private boolean failNext = true;

		private ThrowingInfoFilter(RuntimeException failure) {
			this.failure = failure;
		}

		@Override
		public FilterReply decide(
				Marker marker,
				Logger logger,
				Level level,
				String format,
				Object[] params,
				Throwable throwable) {
			if (logger.getName().equals(RecordedMarketDataService.class.getName()) && level == Level.INFO) {
				attempts++;
				if (failNext) {
					failNext = false;
					throw failure;
				}
			}
			return FilterReply.NEUTRAL;
		}

		private void reset() {
			attempts = 0;
			failNext = true;
		}
	}
}
