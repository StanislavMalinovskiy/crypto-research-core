package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UniverseSnapshotStore {

	UniverseSnapshot store(UniverseSnapshot snapshot);

	Optional<UniverseSnapshot> find(String snapshotId);

	List<UniverseMember> membersAt(String snapshotId, Instant instant);
}
