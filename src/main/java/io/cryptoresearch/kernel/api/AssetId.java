package io.cryptoresearch.kernel.api;

/** A chain-aware asset identity with an opaque chain-local value. */
public record AssetId(ChainId chain, String value) implements Comparable<AssetId> {

	public AssetId {
		chain = IdentityValues.requireChain(chain);
		value = IdentityValues.requireOpaque(value, "asset identity");
	}

	@Override
	public int compareTo(AssetId other) {
		return IdentityValues.compare(chain, value, other.chain, other.value);
	}
}
