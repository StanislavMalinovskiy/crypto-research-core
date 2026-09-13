package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;

class StoreRawObservationUseCaseTest {

	private static final Instant CLOCK_TIME = Instant.parse("2026-09-13T02:03:04.987654321Z");

	@Test
	void delegatesDerivedEvidenceAndReportsInsert() {
		var store = new CapturingStore(StoreRawObservationOutcome.INSERTED);
		var useCase = new StoreRawObservationUseCase(store, Clock.fixed(CLOCK_TIME, ZoneOffset.UTC));

		assertThat(useCase.store(observation())).isEqualTo(StoreRawObservationOutcome.INSERTED);
		assertThat(store.calls).isOne();
		assertThat(store.observation.payloadHash())
				.isEqualTo("sha256:f58648470054614dd08e09b6410b7535f060f99485e9c8c120c4c5eba2756b16");
		assertThat(store.observation.ingestedAt()).isEqualTo(Instant.parse("2026-09-13T02:03:04.987654Z"));
	}

	@Test
	void delegatesAlreadyPresentOutcomeAndPropagatesFailure() {
		var existingStore = new CapturingStore(StoreRawObservationOutcome.ALREADY_PRESENT);
		var useCase = new StoreRawObservationUseCase(existingStore, Clock.fixed(CLOCK_TIME, ZoneOffset.UTC));

		assertThat(useCase.store(observation())).isEqualTo(StoreRawObservationOutcome.ALREADY_PRESENT);

		var failure = new IllegalStateException("persistence failed");
		RawChainEventStore failingStore = ignored -> {
			throw failure;
		};
		var failingUseCase = new StoreRawObservationUseCase(failingStore, Clock.fixed(CLOCK_TIME, ZoneOffset.UTC));
		assertThatThrownBy(() -> failingUseCase.store(observation())).isSameAs(failure);
	}

	@Test
	void ownsTheTransactionalApplicationBoundary() throws NoSuchMethodException {
		assertThat(StoreRawObservationUseCase.class.getMethod("store", RawChainObservation.class)
				.getAnnotation(Transactional.class)).isNotNull();
	}

	private RawChainObservation observation() {
		var transactionId = new TransactionId(ChainId.SOLANA_MAINNET, "tx-1");
		return new RawChainObservation(
				ChainId.SOLANA_MAINNET,
				transactionId,
				new EventId(transactionId, "event-1"),
				"helius",
				new BlockPosition(ChainId.SOLANA_MAINNET, 42),
				Optional.empty(),
				Optional.empty(),
				Instant.parse("2026-09-13T01:00:00.123456789Z"),
				"{\"event\":1}",
				"parser-v1");
	}

	private static final class CapturingStore implements RawChainEventStore {

		private final StoreRawObservationOutcome outcome;
		private StoredRawChainEvent observation;
		private int calls;

		private CapturingStore(StoreRawObservationOutcome outcome) {
			this.outcome = outcome;
		}

		@Override
		public StoreRawObservationOutcome store(StoredRawChainEvent observation) {
			this.observation = observation;
			calls++;
			return outcome;
		}
	}
}
