package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;
import tools.jackson.databind.ObjectMapper;

/** SQL only; transactions belong to the application use case. */
@Repository
public class JdbcCaptureStore {
	private final JdbcClient jdbc;
	private final ObjectMapper json;
	public JdbcCaptureStore(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
	public String encode(Object value) { return json.writeValueAsString(value); }
	private String periodAuthority(Configuration c) {
		var a=c.authority();
		return encode(List.of(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.exclusiveAllocation(),
				a.ceilingHundredths(),a.ceilingUsd().stripTrailingZeros().toPlainString(),a.usdPerMillionCu().stripTrailingZeros().toPlainString(),
				a.subscriptionCuPerByte().stripTrailingZeros().toPlainString()));
	}
	public Configuration configuration(UUID id) {
		return json.readValue(jdbc.sql("SELECT configuration_json FROM marketdata.capture_sessions WHERE session_id=:id")
				.param("id", id).query(String.class).single(), Configuration.class);
	}
	public boolean exists(UUID id) {
		return jdbc.sql("SELECT count(*) FROM marketdata.capture_sessions WHERE session_id=:id").param("id", id).query(Long.class).single() != 0;
	}
	public void period(Configuration c, long ceiling, long opening) {
		jdbc.sql("""
				INSERT INTO marketdata.capture_periods(account_period,authority_json,ceiling_hundredths,reserved_hundredths,period_end)
				VALUES (:period,:authority,:ceiling,:opening,:end)
				ON CONFLICT DO NOTHING
				""").param("period", c.authority().accountPeriod()).param("authority", periodAuthority(c))
				.param("ceiling", ceiling).param("opening", opening).param("end", time(c.authority().periodEnd())).update();
	}
	public boolean reservePeriod(Configuration c, long reservation) {
		return jdbc.sql("""
				UPDATE marketdata.capture_periods SET reserved_hundredths=reserved_hundredths+:amount
				WHERE account_period=:period AND authority_json=:authority
				AND reserved_hundredths <= ceiling_hundredths-:amount-known_overage_hundredths
				RETURNING 1
				""").param("period", c.authority().accountPeriod()).param("authority", periodAuthority(c))
				.param("amount", reservation).query(Integer.class).optional().isPresent();
	}
	public long reserved(String period) {
		return jdbc.sql("SELECT reserved_hundredths+known_overage_hundredths FROM marketdata.capture_periods WHERE account_period=:period")
				.param("period", period).query(Long.class).single();
	}
	public void insertSession(Configuration c, String digest, String holder, Instant until, Instant end, long reservation) {
		jdbc.sql("""
				INSERT INTO marketdata.capture_sessions
				(session_id,configuration_json,configuration_digest,account_period,owner_authorization,holder,locked_until,started_at,ends_at,state,reserved_hundredths)
				VALUES (:id,:config,:digest,:period,:authorization,:holder,:until,:start,:end,'RUNNING',:reservation)
				""").param("id", c.sessionId()).param("config", encode(c)).param("digest", digest)
				.param("period", c.authority().accountPeriod()).param("authorization", c.authority().ownerAuthorization())
				.param("holder", holder).param("until", time(until)).param("start", time(c.startsAt())).param("end", time(end))
				.param("reservation", reservation).update();
	}
	public boolean claim(UUID id, String holder, Instant now, Instant until) {
		return jdbc.sql("""
				UPDATE marketdata.capture_sessions SET holder=:holder,locked_until=:until
				WHERE session_id=:id AND state='RUNNING' AND ends_at>:now
				AND (holder=:holder OR locked_until<=:now) RETURNING 1
				""").param("id", id).param("holder", holder).param("now", time(now)).param("until", time(until))
				.query(Integer.class).optional().isPresent();
	}
	public void fence(UUID id, String holder, Instant now, Instant until) {
		var valid = jdbc.sql("""
				SELECT session_id FROM marketdata.capture_sessions WHERE session_id=:id
				AND holder=:holder AND locked_until>:now AND state='RUNNING' FOR UPDATE
				""").param("id", id).param("holder", holder).param("now", time(now)).query(UUID.class).optional();
		if (valid.isEmpty()) throw new IllegalStateException("CAPTURE_LEASE_LOST");
		jdbc.sql("UPDATE marketdata.capture_sessions SET locked_until=:until WHERE session_id=:id")
				.param("id", id).param("until", time(until)).update();
	}
	public void pending(UUID id, String pool, String signature, long slot, String notification, Instant now) {
		jdbc.sql("""
				INSERT INTO marketdata.capture_dispositions(session_id,pool,signature,slot,status,notification,received_at)
				VALUES (:id,:pool,:sig,:slot,'PENDING',:notification,:now) ON CONFLICT DO NOTHING
				""").param("id", id).param("pool", pool).param("sig", signature).param("slot", slot)
				.param("notification", notification).param("now", time(now)).update();
	}
	public void finish(UUID id, String pool, String signature, String status, String hash, String response, Instant admitted, boolean duplicate) {
		jdbc.sql("""
				UPDATE marketdata.capture_dispositions SET
				status=CASE WHEN raw_digest IS NOT NULL AND :status <> 'CONFLICT' THEN status ELSE :status END,
				raw_digest=COALESCE(raw_digest,:hash),response=CASE WHEN response='' THEN :response ELSE response END,
				admitted_at=COALESCE(admitted_at,:admitted),duplicates=duplicates+:duplicate,
				conflicts=conflicts+:conflict WHERE session_id=:id AND pool=:pool AND signature=:sig
				""").param("id", id).param("pool", pool).param("sig", signature).param("status", status)
				.param("hash", hash, java.sql.Types.VARCHAR).param("response", response).param("admitted", time(admitted))
				.param("duplicate", duplicate ? 1 : 0).param("conflict", "CONFLICT".equals(status) ? 1 : 0).update();
		if (!"CONFLICT".equals(status)) {
			jdbc.sql("""
					INSERT INTO marketdata.capture_checkpoints VALUES (:id,:pool,:sig)
					ON CONFLICT(session_id,pool) DO UPDATE SET signature=EXCLUDED.signature
					WHERE (SELECT slot FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND signature=:sig)
					> (SELECT slot FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND signature=marketdata.capture_checkpoints.signature)
					OR ((SELECT slot FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND signature=:sig)
					= (SELECT slot FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND signature=marketdata.capture_checkpoints.signature)
					AND EXCLUDED.signature COLLATE "C" > marketdata.capture_checkpoints.signature COLLATE "C")
					""").param("id", id).param("pool", pool).param("sig", signature).update();
		}
	}
	public Optional<String> boundary(UUID id, String pool) {
		return jdbc.sql("SELECT signature FROM marketdata.capture_checkpoints WHERE session_id=:id AND pool=:pool")
				.param("id", id).param("pool", pool).query(String.class).optional();
	}
	public List<Disposition> dispositions(UUID id) {
		return jdbc.sql("SELECT * FROM marketdata.capture_dispositions WHERE session_id=:id ORDER BY slot,pool COLLATE \"C\",signature COLLATE \"C\"")
				.param("id", id).query((r, n) -> new Disposition(r.getString("pool"), r.getString("signature"), r.getLong("slot"),
						r.getString("status"), r.getString("raw_digest"), r.getString("notification"), r.getString("response"),
						instant(r,"received_at"), instant(r,"admitted_at"), r.getLong("duplicates"), r.getLong("conflicts"))).list();
	}
	public void state(UUID id, Configuration c, String account, long slot, String payload, String envelope, String digest, String encoding, Instant received,Instant now) {
		jdbc.sql("""
				INSERT INTO marketdata.raw_account_snapshots VALUES
				('solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',:provider,:account,:slot,:digest,:encoding,:payload,:envelope,:received,:now,:now)
				ON CONFLICT DO NOTHING
				""").param("provider", c.provider()).param("account", account).param("slot", slot).param("digest", digest)
				.param("encoding", encoding).param("payload", payload).param("envelope", envelope).param("received",time(received)).param("now", time(now)).update();
		var existing = jdbc.sql("""
				SELECT payload FROM marketdata.raw_account_snapshots
				WHERE chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp' AND provider=:provider AND account=:account AND slot=:slot AND digest=:digest
				""").param("provider", c.provider()).param("account", account).param("slot", slot).param("digest", digest).query(String.class).single();
		if (!existing.equals(payload)) throw new IllegalStateException("ACCOUNT_CONTENT_CONFLICT");
		jdbc.sql("""
				INSERT INTO marketdata.capture_state_members VALUES (:id,'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',:provider,:account,:slot,:digest,0)
				ON CONFLICT(session_id,account,slot,digest) DO UPDATE SET duplicates=marketdata.capture_state_members.duplicates+1
				""").param("id", id).param("provider", c.provider()).param("account", account).param("slot", slot).param("digest", digest).update();
	}
	public List<AccountSnapshot> states(UUID id) {
		return jdbc.sql("""
				SELECT s.*, (SELECT count(*) FROM marketdata.raw_account_snapshots x
				WHERE x.chain_id=s.chain_id AND x.provider=s.provider AND x.account=s.account AND x.slot=s.slot)>1 AS ambiguous
				FROM marketdata.raw_account_snapshots s JOIN marketdata.capture_state_members m USING(chain_id,provider,account,slot,digest)
				WHERE m.session_id=:id ORDER BY s.slot,s.account COLLATE "C",s.digest COLLATE "C"
				""").param("id", id).query((r,n) -> new AccountSnapshot(r.getString("account"),r.getLong("slot"),r.getString("digest"),
						r.getString("encoding"),r.getString("payload"),r.getString("envelope"),instant(r,"received_at"),instant(r,"admitted_at"),
						instant(r,"ingested_at"),"UNKNOWN_TRANSACTION_POSITION","UNKNOWN",r.getBoolean("ambiguous"))).list();
	}
	public void gap(UUID id, String pool, String kind, String reason, Instant now) {
		jdbc.sql("""
				INSERT INTO marketdata.capture_gaps(session_id,pool,kind,reason,from_slot,opened_at,boundary_signature,status)
				VALUES (:id,:pool,:kind,:reason,
				(SELECT d.slot FROM marketdata.capture_checkpoints c JOIN marketdata.capture_dispositions d USING(session_id,pool,signature) WHERE c.session_id=:id AND c.pool=:pool),
				:now,(SELECT signature FROM marketdata.capture_checkpoints WHERE session_id=:id AND pool=:pool),:status)
				""").param("id", id).param("pool", pool).param("kind", kind).param("reason", reason).param("now", time(now))
				.param("status", "STATE".equals(kind) ? "UNRESOLVED_STATE_GAP" : "UNRESOLVED_TRANSACTION_GAP").update();
	}
	public void recovered(UUID id, String pool, String boundary, Instant now) {
		jdbc.sql("""
				UPDATE marketdata.capture_gaps SET status='RECOVERED_TRANSACTIONS',closed_at=:now
				WHERE session_id=:id AND pool=:pool AND kind='TRANSACTION' AND boundary_signature=:boundary
				AND NOT EXISTS(SELECT 1 FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND status IN ('PENDING','CONFLICT','UNVERIFIED_FINALITY','UNAVAILABLE','FETCH_FAILURE'))
				""").param("id", id).param("pool", pool).param("boundary", boundary).param("now", time(now)).update();
	}
	public List<Gap> gaps(UUID id) {
		return jdbc.sql("SELECT * FROM marketdata.capture_gaps WHERE session_id=:id ORDER BY opened_at,pool COLLATE \"C\",gap_id")
				.param("id", id).query((r,n) -> new Gap(r.getLong("gap_id"),r.getString("pool"),r.getString("kind"),r.getString("reason"),
						(Long)r.getObject("from_slot"),(Long)r.getObject("to_slot"),instant(r,"opened_at"),instant(r,"closed_at"),r.getString("boundary_signature"),r.getString("status"))).list();
	}
	public boolean consume(UUID id, long requests, long received, long subscription, long retained, long rpc, Limits l, Instant now) {
		if(accountExhausted(id)) return false;
		return jdbc.sql("""
				UPDATE marketdata.capture_sessions SET requests=requests+:requests,received_bytes=received_bytes+:received,
				subscription_bytes=subscription_bytes+:subscription,retained_bytes=retained_bytes+:retained,rpc_hundredths=rpc_hundredths+:rpc
				WHERE session_id=:id AND ends_at>:now AND requests<=:maxRequests-:requests
				AND received_bytes<=:maxReceived-:received AND subscription_bytes<=:maxSubscription-:subscription
				AND retained_bytes<=:maxRetained-:retained AND rpc_hundredths<=:maxRpc-:rpc RETURNING 1
				""").param("id",id).param("requests",requests).param("received",received).param("subscription",subscription)
				.param("retained",retained).param("rpc",rpc).param("now",time(now)).param("maxRequests",l.requests())
				.param("maxReceived",l.receivedBytes()).param("maxSubscription",l.subscriptionBytes()).param("maxRetained",l.retainedBytes())
				.param("maxRpc",l.rpcHundredths()).query(Integer.class).optional().isPresent();
	}
	public void stop(UUID id, String reason) {
		jdbc.sql("UPDATE marketdata.capture_sessions SET state='STOPPED',reason=:reason WHERE session_id=:id")
				.param("id",id).param("reason",reason).update();
	}
	public void observe(UUID id,String holder,long bytes,long subscription) {
		var changed=jdbc.sql("""
				UPDATE marketdata.capture_sessions SET received_bytes=received_bytes+:bytes,subscription_bytes=subscription_bytes+:subscription
				WHERE session_id=:id AND holder=:holder RETURNING subscription_bytes
				""").param("id",id).param("holder",holder).param("bytes",bytes).param("subscription",subscription).query(Long.class).optional();
		if(changed.isEmpty()) throw new IllegalStateException("CAPTURE_LEASE_LOST");
		var c=configuration(id); var current=changed.orElseThrow(); var maximum=c.limits().subscriptionBytes();
		var extra=Math.subtractExact(Math.max(0,current-maximum),Math.max(0,current-subscription-maximum));
		if(extra>0) jdbc.sql("UPDATE marketdata.capture_periods SET known_overage_hundredths=known_overage_hundredths+:extra WHERE account_period=:period")
				.param("extra",io.cryptoresearch.marketdata.application.CaptureBudget.subscriptionHundredths(c,extra)).param("period",c.authority().accountPeriod()).update();
	}
	public boolean accountExhausted(UUID id) {
		return jdbc.sql("""
				SELECT p.known_overage_hundredths>p.ceiling_hundredths-p.reserved_hundredths
				FROM marketdata.capture_periods p JOIN marketdata.capture_sessions s USING(account_period) WHERE s.session_id=:id
				""").param("id",id).query(Boolean.class).single();
	}
	public Status status(UUID id) {
		var c = configuration(id);
		var counts=jdbc.sql("""
				SELECT count(DISTINCT signature) FILTER(WHERE raw_digest IS NOT NULL) AS tx,
				COALESCE(sum(duplicates),0) AS duplicates,COALESCE(sum(conflicts),0) AS conflicts,
				count(*) FILTER(WHERE status NOT IN ('CAPTURED','FAILED_TRANSACTION','PENDING')) AS failures
				FROM marketdata.capture_dispositions WHERE session_id=:id
				""").param("id",id).query((r,n)->new long[]{r.getLong("tx"),r.getLong("duplicates"),r.getLong("conflicts"),r.getLong("failures")}).single();
		var stateCounts=jdbc.sql("SELECT count(*),COALESCE(sum(duplicates),0) FROM marketdata.capture_state_members WHERE session_id=:id")
				.param("id",id).query((r,n)->new long[]{r.getLong(1),r.getLong(2)}).single();
		return jdbc.sql("SELECT * FROM marketdata.capture_sessions WHERE session_id=:id").param("id",id).query((r,n) ->
				new Status(id,r.getString("state"),r.getString("reason"),r.getString("configuration_digest"),
						counts[0],stateCounts[0],Math.addExact(counts[1],stateCounts[1]),counts[2],counts[3],
						r.getLong("requests"),r.getLong("received_bytes"),r.getLong("subscription_bytes"),r.getLong("retained_bytes"),r.getLong("rpc_hundredths"),
						r.getLong("reserved_hundredths"),io.cryptoresearch.marketdata.application.CaptureBudget.cost(r.getLong("reserved_hundredths"),c.authority().usdPerMillionCu()),
						"UNKNOWN","UNKNOWN_UNSOLICITED_OR_BUFFERED_BILLING","CAPTURE_ONLY_ECONOMICS_UNSUPPORTED",gaps(id))).single();
	}
	public void envelope(UUID id,String kind,String digest,String payload,Instant now) {
		jdbc.sql("INSERT INTO marketdata.capture_envelopes VALUES (:id,:kind,:digest,:payload,:now) ON CONFLICT DO NOTHING")
				.param("id",id).param("kind",kind).param("digest",digest).param("payload",payload).param("now",time(now)).update();
	}
	public List<Envelope> envelopes(UUID id) {
		return jdbc.sql("SELECT * FROM marketdata.capture_envelopes WHERE session_id=:id ORDER BY received_at,kind COLLATE \"C\",digest COLLATE \"C\"")
				.param("id",id).query((r,n)->new Envelope(r.getString("kind"),r.getString("digest"),r.getString("payload"),instant(r,"received_at"))).list();
	}
	private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
	private static Instant instant(ResultSet r, String column) throws SQLException {
		var value = r.getObject(column, OffsetDateTime.class); return value == null ? null : value.toInstant();
	}
}
