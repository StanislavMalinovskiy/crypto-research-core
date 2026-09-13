package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;

class RawChainObservationTest {

	private static final ChainId SOLANA = ChainId.SOLANA_MAINNET;
	private static final ChainId BASE = new ChainId("eip155:8453");
	private static final Instant PRECISE_TIME = Instant.parse("2026-09-13T01:02:03.123456789Z");

	@Test
	void validatesAndNormalizesCompleteEvidence() {
		var observation = observation(
				SOLANA,
				transaction(SOLANA, "tx-1"),
				event(transaction(SOLANA, "tx-1"), "event-1"),
				new BlockPosition(SOLANA, 42),
				"helius",
				Optional.of("block-hash"),
				Optional.of(PRECISE_TIME),
				"parser-v1");

		assertThat(observation.sourceEventTime()).contains(Instant.parse("2026-09-13T01:02:03.123456Z"));
		assertThat(observation.observedAt()).isEqualTo(Instant.parse("2026-09-13T01:02:03.123456Z"));
	}

	@Test
	void rejectsMissingBlankOversizedAndControlCharacterEvidence() {
		assertThatThrownBy(() -> observation(
				null, transaction(SOLANA, "tx-1"), event(transaction(SOLANA, "tx-1"), "event-1"),
				new BlockPosition(SOLANA, 1), "helius", Optional.empty(), Optional.empty(), "parser-v1"))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> observation(
				SOLANA, transaction(SOLANA, "tx-1"), event(transaction(SOLANA, "tx-1"), "event-1"),
				new BlockPosition(SOLANA, 1), " ", Optional.empty(), Optional.empty(), "parser-v1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> observation(
				SOLANA, transaction(SOLANA, "x".repeat(RawObservationValues.MAX_TRANSACTION_VALUE_UTF8_BYTES + 1)),
				event(transaction(SOLANA, "x".repeat(RawObservationValues.MAX_TRANSACTION_VALUE_UTF8_BYTES + 1)), "event-1"),
				new BlockPosition(SOLANA, 1), "helius", Optional.empty(), Optional.empty(), "parser-v1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("transactionValue");
		assertThatThrownBy(() -> observation(
				SOLANA, transaction(SOLANA, "😀".repeat(129)),
				event(transaction(SOLANA, "😀".repeat(129)), "event-1"),
				new BlockPosition(SOLANA, 1), "helius", Optional.empty(), Optional.empty(), "parser-v1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("512 UTF-8 bytes");
		assertThatThrownBy(() -> observation(
				SOLANA, transaction(SOLANA, "tx-1"), event(transaction(SOLANA, "tx-1"), "event-1"),
				new BlockPosition(SOLANA, 1), "helius", Optional.empty(), Optional.empty(), "parser\nversion"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("control characters");
		assertThatThrownBy(() -> observation(
				SOLANA, transaction(SOLANA, "tx-1"), event(transaction(SOLANA, "tx-1"), "event-1"),
				new BlockPosition(SOLANA, 1), "helius", Optional.empty(), Optional.empty(), "parser-v1", " "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload");
	}

	@Test
	void acceptsOpaqueValuesAtTheirExactUtf8ByteBudget() {
		var transaction = transaction(SOLANA, "😀".repeat(128));
		var observation = observation(
				SOLANA,
				transaction,
				event(transaction, "😀".repeat(64)),
				new BlockPosition(SOLANA, 1),
				"😀".repeat(16),
				Optional.of("😀".repeat(64)),
				Optional.empty(),
				"😀".repeat(32));

		assertThat(observation.transactionId().value()).hasSize(256);
		assertThat(observation.eventId().locator()).hasSize(128);
	}

	@Test
	void rejectsEveryCrossChainOrTransactionInconsistency() {
		var solanaTransaction = transaction(SOLANA, "tx-1");
		var baseTransaction = transaction(BASE, "tx-1");

		assertThatThrownBy(() -> observation(
				SOLANA, baseTransaction, event(baseTransaction, "event-1"), new BlockPosition(SOLANA, 1),
				"helius", Optional.empty(), Optional.empty(), "parser-v1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("transactionId chain");
		assertThatThrownBy(() -> observation(
				SOLANA, solanaTransaction, event(transaction(SOLANA, "tx-2"), "event-1"),
				new BlockPosition(SOLANA, 1), "helius", Optional.empty(), Optional.empty(), "parser-v1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("eventId transaction");
		assertThatThrownBy(() -> observation(
				SOLANA, solanaTransaction, event(solanaTransaction, "event-1"), new BlockPosition(BASE, 1),
				"helius", Optional.empty(), Optional.empty(), "parser-v1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("blockPosition chain");
	}

	@Test
	void fingerprintsExactUtf8BytesWithQualifiedLowercaseSha256() {
		assertThat(PayloadFingerprint.sha256("hello"))
				.isEqualTo("sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
		assertThat(PayloadFingerprint.sha256("{\"symbol\":\"◎\"}"))
				.isNotEqualTo(PayloadFingerprint.sha256("{\"symbol\": \"◎\"}"));
	}

	@Test
	void storedEvidenceRejectsAnInvalidOrMismatchedDigest() {
		var input = observation(
				SOLANA, transaction(SOLANA, "tx-1"), event(transaction(SOLANA, "tx-1"), "event-1"),
				new BlockPosition(SOLANA, 1), "helius", Optional.empty(), Optional.empty(), "parser-v1");

		assertThatThrownBy(() -> stored(input, "sha256:" + "A".repeat(64)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("lowercase SHA-256");
		assertThatThrownBy(() -> stored(input, "sha256:" + "0".repeat(64)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("match the exact payload");
	}

	private RawChainObservation observation(
			ChainId chain,
			TransactionId transactionId,
			EventId eventId,
			BlockPosition blockPosition,
			String provider,
			Optional<String> blockHash,
			Optional<Instant> sourceEventTime,
			String parserVersion) {
		return observation(
				chain, transactionId, eventId, blockPosition, provider, blockHash, sourceEventTime,
				parserVersion, "{\"event\":1}");
	}

	private RawChainObservation observation(
			ChainId chain,
			TransactionId transactionId,
			EventId eventId,
			BlockPosition blockPosition,
			String provider,
			Optional<String> blockHash,
			Optional<Instant> sourceEventTime,
			String parserVersion,
			String payload) {
		return new RawChainObservation(
				chain, transactionId, eventId, provider, blockPosition, blockHash, sourceEventTime,
				PRECISE_TIME, payload, parserVersion);
	}

	private StoredRawChainEvent stored(RawChainObservation input, String digest) {
		return new StoredRawChainEvent(
				input.chain(), input.transactionId(), input.eventId(), input.provider(), input.blockPosition(),
				input.blockHash(), input.sourceEventTime(), input.observedAt(), input.payload(), digest,
				input.parserVersion(), PRECISE_TIME);
	}

	private TransactionId transaction(ChainId chain, String value) {
		return new TransactionId(chain, value);
	}

	private EventId event(TransactionId transactionId, String locator) {
		return new EventId(transactionId, locator);
	}
}
