package io.cryptoresearch.marketdata.application;

public class UniverseSnapshotConflictException extends RuntimeException {

	public UniverseSnapshotConflictException(String snapshotId) {
		super("Conflicting universe snapshot for " + snapshotId);
	}
}
