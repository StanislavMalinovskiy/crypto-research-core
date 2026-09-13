package io.cryptoresearch.kernel.api;

/** A chain-aware wallet address with an opaque chain-local value. */
public record WalletAddress(ChainId chain, String value) implements Comparable<WalletAddress> {

	public WalletAddress {
		chain = IdentityValues.requireChain(chain);
		value = IdentityValues.requireOpaque(value, "wallet address");
	}

	@Override
	public int compareTo(WalletAddress other) {
		return IdentityValues.compare(chain, value, other.chain, other.value);
	}
}
