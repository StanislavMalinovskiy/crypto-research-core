package io.cryptoresearch.kernel.api;

/** An exact case-sensitive CAIP-2 blockchain network identity. */
public record ChainId(String value) implements Comparable<ChainId> {

	public static final ChainId SOLANA_MAINNET =
			new ChainId("solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp");

	public ChainId {
		value = IdentityValues.requireCaip2(value);
	}

	@Override
	public int compareTo(ChainId other) {
		return value.compareTo(other.value);
	}
}
