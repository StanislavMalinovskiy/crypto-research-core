package io.cryptoresearch.kernel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

class ChainIdentityTest {

	private static final ChainId BASE_MAINNET = new ChainId("eip155:8453");
	private static final List<BiFunction<ChainId, String, ?>> OPAQUE_IDENTITIES = List.of(
			AssetId::new, WalletAddress::new, TransactionId::new);

	@Test
	void acceptsCanonicalChainIdentifiers() {
		var solana = new ChainId("solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp");
		var representativeNetworks = List.of(
				solana.value(), "eip155:1", BASE_MAINNET.value(), "eip155:42161", "cosmos:cosmoshub-4");

		assertThat(representativeNetworks).allSatisfy(value ->
				assertThat(new ChainId(value).value()).isEqualTo(value));
		assertThat(solana).isEqualTo(ChainId.SOLANA_MAINNET);
		assertThat(solana.hashCode()).isEqualTo(ChainId.SOLANA_MAINNET.hashCode());
		assertThat(List.of(solana, BASE_MAINNET).stream().sorted()).containsExactly(BASE_MAINNET, solana);
	}

	@Test
	void preservesCaseSensitiveCaip2References() {
		var uppercaseReference = new ChainId("solana:AbC123");
		var lowercaseReference = new ChainId("solana:abc123");

		assertThat(uppercaseReference.value()).isEqualTo("solana:AbC123");
		assertThat(uppercaseReference).isNotEqualTo(lowercaseReference);
	}

	@Test
	void rejectsNonCanonicalChainIdentifiers() {
		var invalidValues = new String[] {
			null, "", " ", "solana", "Solana:mainnet", "so:mainnet", "toolongid:mainnet",
			"solana:", "solana:mainnet!", "solana:mainnet:extra", " solana:mainnet", "solana:mainnet "
		};

		for (var value : invalidValues) {
			assertThatThrownBy(() -> new ChainId(value)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void opaqueIdentitiesPreserveExactValuesAndRejectInvalidOnes() {
		for (var factory : OPAQUE_IDENTITIES) {
			assertThat(factory.apply(ChainId.SOLANA_MAINNET, "AbC-123_+/="))
					.extracting("value").isEqualTo("AbC-123_+/=");
			assertThatThrownBy(() -> factory.apply(null, "value")).isInstanceOf(NullPointerException.class);
			for (var invalid : new String[] { null, "", " ", " value", "value ", "line\nbreak", "zero\u0000byte" }) {
				assertThatThrownBy(() -> factory.apply(ChainId.SOLANA_MAINNET, invalid))
						.isInstanceOf(IllegalArgumentException.class);
			}
		}
	}

	@Test
	void categoriesAndChainsRemainDistinct() {
		for (var factory : OPAQUE_IDENTITIES) {
			var solanaIdentity = factory.apply(ChainId.SOLANA_MAINNET, "same");
			var baseIdentity = factory.apply(BASE_MAINNET, "same");
			assertThat(Set.of(solanaIdentity, baseIdentity)).hasSize(2);
			assertThat(factory.apply(ChainId.SOLANA_MAINNET, "same"))
					.isEqualTo(solanaIdentity)
					.hasSameHashCodeAs(solanaIdentity);
		}
		assertThat(AssetId.class.isAssignableFrom(WalletAddress.class)).isFalse();
		assertThat(AssetId.class.isAssignableFrom(TransactionId.class)).isFalse();
	}

	@Test
	void opaqueIdentitiesHaveDeterministicTechnicalOrder() {
		assertThat(List.of(
				new AssetId(ChainId.SOLANA_MAINNET, "z"),
				new AssetId(BASE_MAINNET, "z"),
				new AssetId(ChainId.SOLANA_MAINNET, "a")).stream().sorted()).containsExactly(
						new AssetId(BASE_MAINNET, "z"),
						new AssetId(ChainId.SOLANA_MAINNET, "a"),
						new AssetId(ChainId.SOLANA_MAINNET, "z"));
		assertThat(List.of(
				new WalletAddress(ChainId.SOLANA_MAINNET, "z"),
				new WalletAddress(BASE_MAINNET, "z"),
				new WalletAddress(ChainId.SOLANA_MAINNET, "a")).stream().sorted()).containsExactly(
						new WalletAddress(BASE_MAINNET, "z"),
						new WalletAddress(ChainId.SOLANA_MAINNET, "a"),
						new WalletAddress(ChainId.SOLANA_MAINNET, "z"));
		assertThat(List.of(
				new TransactionId(ChainId.SOLANA_MAINNET, "z"),
				new TransactionId(BASE_MAINNET, "z"),
				new TransactionId(ChainId.SOLANA_MAINNET, "a")).stream().sorted()).containsExactly(
						new TransactionId(BASE_MAINNET, "z"),
						new TransactionId(ChainId.SOLANA_MAINNET, "a"),
						new TransactionId(ChainId.SOLANA_MAINNET, "z"));
	}

	@Test
	void eventsAreScopedToTransactions() {
		var firstTransaction = new TransactionId(ChainId.SOLANA_MAINNET, "tx-a");
		var secondTransaction = new TransactionId(ChainId.SOLANA_MAINNET, "tx-b");
		var otherChainTransaction = new TransactionId(BASE_MAINNET, "tx-a");
		var firstEvent = new EventId(firstTransaction, "1");
		var receiptLog = new EventId(firstTransaction, "receipt.logs:3");

		assertThat(Set.of(
				firstEvent,
				new EventId(firstTransaction, "2"),
				new EventId(secondTransaction, "1"),
				new EventId(otherChainTransaction, "1"))).hasSize(4);
		assertThat(new EventId(firstTransaction, "1")).isEqualTo(firstEvent);
		assertThat(firstEvent.locator()).isEqualTo("1");
		assertThat(receiptLog.locator()).isEqualTo("receipt.logs:3");
		assertThat(new EventId(firstTransaction, receiptLog.locator())).isEqualTo(receiptLog);
		assertThat(List.of(new EventId(secondTransaction, "1"), firstEvent).stream().sorted())
				.containsExactly(firstEvent, new EventId(secondTransaction, "1"));
		assertThatThrownBy(() -> new EventId(null, "1")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new EventId(firstTransaction, " ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void blockPositionsAreNonNegativeAndOrderedNumericallyWithinChain() {
		var slotNine = new BlockPosition(ChainId.SOLANA_MAINNET, 9);
		var baseNine = new BlockPosition(BASE_MAINNET, 9);

		assertThat(Set.of(slotNine, baseNine)).hasSize(2);
		assertThat(List.of(new BlockPosition(ChainId.SOLANA_MAINNET, 10), slotNine, baseNine).stream().sorted())
				.containsExactly(baseNine, slotNine, new BlockPosition(ChainId.SOLANA_MAINNET, 10));
		assertThat(new BlockPosition(ChainId.SOLANA_MAINNET, 0).value()).isZero();
		assertThatThrownBy(() -> new BlockPosition(null, 1)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new BlockPosition(ChainId.SOLANA_MAINNET, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
