package io.cryptoresearch.kernel.api;

/** A chain-aware transaction identity with an opaque chain-local value. */
public record TransactionId(ChainId chain, String value) implements Comparable<TransactionId> {

	public TransactionId {
		chain = IdentityValues.requireChain(chain);
		value = IdentityValues.requireOpaque(value, "transaction identity");
	}

	@Override
	public int compareTo(TransactionId other) {
		return IdentityValues.compare(chain, value, other.chain, other.value);
	}
}
