package io.cryptoresearch.kernel.api;

/** An ordered chain position, such as a Solana slot or EVM block number. */
public record BlockPosition(ChainId chain, long value) implements Comparable<BlockPosition> {

	public BlockPosition {
		chain = IdentityValues.requireChain(chain);
		if (value < 0) {
			throw new IllegalArgumentException("block position must not be negative");
		}
	}

	@Override
	public int compareTo(BlockPosition other) {
		var chainComparison = chain.compareTo(other.chain);
		return chainComparison != 0 ? chainComparison : Long.compare(value, other.value);
	}
}
