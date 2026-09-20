package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.application.UniverseMember;
import io.cryptoresearch.marketdata.application.UniverseSnapshot;
import io.cryptoresearch.marketdata.application.UniverseSnapshotConflictException;
import io.cryptoresearch.marketdata.application.UniverseSnapshotStore;

@Repository
public class JdbcUniverseSnapshotStore implements UniverseSnapshotStore {

	private static final int BATCH_CHUNK_SIZE = 1000;

	private static final String MEMBER_COLUMNS = """
			snapshot_id, member_ordinal, chain_id, asset_address, discovery_source,
			eligibility_rule_version, inclusion_time, exclusion_time, exclusion_reason
			""";

	private final JdbcClient jdbcClient;
	private final JdbcTemplate jdbcTemplate;

	public JdbcUniverseSnapshotStore(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
		this.jdbcClient = jdbcClient;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public UniverseSnapshot store(UniverseSnapshot snapshot) {
		var inserted = jdbcClient.sql("""
				INSERT INTO marketdata.universe_snapshots (
				    snapshot_id, fingerprint, rule_version, cutoff
				) VALUES (:snapshotId, :fingerprint, :ruleVersion, :cutoff)
				ON CONFLICT (snapshot_id) DO NOTHING
				RETURNING 1
				""")
				.param("snapshotId", snapshot.snapshotId())
				.param("fingerprint", snapshot.fingerprint())
				.param("ruleVersion", snapshot.ruleVersion())
				.param("cutoff", timestamp(snapshot.cutoff()))
				.query(Integer.class)
				.optional()
				.isPresent();
		if (inserted) {
			for (var start = 0; start < snapshot.members().size(); start += BATCH_CHUNK_SIZE) {
				var chunk = snapshot.members().subList(
						start, Math.min(start + BATCH_CHUNK_SIZE, snapshot.members().size()));
				var parameters = new java.util.ArrayList<Object[]>(chunk.size());
				for (var index = 0; index < chunk.size(); index++) {
					var member = chunk.get(index);
					parameters.add(new Object[] {
							snapshot.snapshotId(),
							start + index,
							member.asset().chain().value(),
							member.asset().value(),
							member.discoverySource(),
							member.eligibilityRuleVersion(),
							timestamp(member.inclusionTime()),
							member.exclusionTime().map(this::timestamp).orElse(null),
							member.exclusionReason().orElse(null) });
				}
				jdbcTemplate.batchUpdate("""
						INSERT INTO marketdata.universe_members (
						    snapshot_id, member_ordinal, chain_id, asset_address, discovery_source,
						    eligibility_rule_version, inclusion_time, exclusion_time, exclusion_reason
						) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", parameters);
			}
			return snapshot;
		}
		var existing = find(snapshot.snapshotId())
				.orElseThrow(() -> new IllegalStateException(
						"Universe snapshot disappeared after uniqueness resolution"));
		if (!existing.equals(snapshot)) {
			throw new UniverseSnapshotConflictException(snapshot.snapshotId());
		}
		return existing;
	}

	@Override
	public Optional<UniverseSnapshot> find(String snapshotId) {
		var header = jdbcClient.sql("""
				SELECT snapshot_id, fingerprint, rule_version, cutoff
				FROM marketdata.universe_snapshots WHERE snapshot_id = :snapshotId
				""")
				.param("snapshotId", snapshotId)
				.query((resultSet, rowNumber) -> new SnapshotHeader(
						resultSet.getString("snapshot_id"),
						resultSet.getString("fingerprint"),
						resultSet.getString("rule_version"),
						resultSet.getObject("cutoff", OffsetDateTime.class).toInstant()))
				.optional();
		return header.map(value -> new UniverseSnapshot(
				value.snapshotId(), value.fingerprint(), value.ruleVersion(), value.cutoff(),
				members(snapshotId)));
	}

	@Override
	public List<UniverseMember> membersAt(String snapshotId, Instant instant) {
		return jdbcClient.sql("SELECT " + MEMBER_COLUMNS + """
				 FROM marketdata.universe_members
				 WHERE snapshot_id = :snapshotId
				   AND inclusion_time <= :instant
				   AND (exclusion_time IS NULL OR exclusion_time > :instant)
				 ORDER BY member_ordinal
				""")
				.param("snapshotId", snapshotId)
				.param("instant", timestamp(instant))
				.query(this::mapMember)
				.list();
	}

	private List<UniverseMember> members(String snapshotId) {
		return jdbcClient.sql("SELECT " + MEMBER_COLUMNS + """
				 FROM marketdata.universe_members
				 WHERE snapshot_id = :snapshotId
				 ORDER BY member_ordinal
				""")
				.param("snapshotId", snapshotId)
				.query(this::mapMember)
				.list();
	}

	private UniverseMember mapMember(ResultSet resultSet, int rowNumber) throws SQLException {
		return new UniverseMember(
				new AssetId(new ChainId(resultSet.getString("chain_id")), resultSet.getString("asset_address")),
				resultSet.getString("discovery_source"),
				resultSet.getString("eligibility_rule_version"),
				resultSet.getObject("inclusion_time", OffsetDateTime.class).toInstant(),
				Optional.ofNullable(resultSet.getObject("exclusion_time", OffsetDateTime.class))
						.map(OffsetDateTime::toInstant),
				Optional.ofNullable(resultSet.getString("exclusion_reason")));
	}

	private OffsetDateTime timestamp(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private record SnapshotHeader(
			String snapshotId, String fingerprint, String ruleVersion, Instant cutoff) {
	}
}
