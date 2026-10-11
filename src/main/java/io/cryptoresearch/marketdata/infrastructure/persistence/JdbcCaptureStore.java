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
    public record MonitorHeader(UUID sessionId,String configurationDigest,String state,String reason,Instant startedAt,Instant endsAt,Instant lockedUntil) { }
    public record MonitorData(MonitorHeader header,io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.Counters counters,
            List<io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.DispositionCount> dispositions,
            List<io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.PoolPosition> positions,
            List<io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.GapCount> gapCounts,List<Gap> details) { }
	private final JdbcClient jdbc;
	private final ObjectMapper json;
	private volatile boolean continuousSchemaPresent;
	public JdbcCaptureStore(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public void monitorTimeout() {jdbc.sql("set local statement_timeout='10s'").update();}
    public Optional<MonitorHeader> monitorHeader(UUID id) {
        return jdbc.sql("""
            select configuration_digest,started_at,ends_at,locked_until,
            octet_length(state)+octet_length(reason)<=131072
            and jsonb_array_length(configuration_json::jsonb->'pools')<=3
            and coalesce((select sum(octet_length(p.value->>'address')) from jsonb_array_elements(configuration_json::jsonb->'pools') p(value)),0)<=131072 as fits,
            case when octet_length(state)+octet_length(reason)<=131072 then state end as state,
            case when octet_length(state)+octet_length(reason)<=131072 then reason end as reason
            from marketdata.capture_sessions where session_id=:id
            """).param("id",id).query((r,n)->{
                if(!r.getBoolean("fits"))throw monitorLimit();
                return new MonitorHeader(id,r.getString("configuration_digest"),r.getString("state"),r.getString("reason"),instant(r,"started_at"),instant(r,"ends_at"),instant(r,"locked_until"));
            }).optional();
    }
    public List<io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.DispositionCount> monitorDispositions(UUID id) {
        var result=jdbc.sql("""
            select case when octet_length(status)<=131072 then status end as status,count(*) as n,
            octet_length(status)<=131072 as fits from marketdata.capture_dispositions where session_id=:id
            group by status order by status collate "C" limit 33
            """).param("id",id).query((r,n)->{if(!r.getBoolean("fits"))throw monitorLimit();return new io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.DispositionCount(r.getString("status"),r.getLong("n"));}).list();
        if(result.size()>32)throw monitorLimit();return result;
    }
    public long[] monitorEvidenceCounts(UUID id) {
        return jdbc.sql("""
            select count(distinct signature) filter(where raw_digest is not null) as tx,
            coalesce(sum(duplicates),0) as duplicates,coalesce(sum(conflicts),0) as conflicts,
            count(*) filter(where status not in ('CAPTURED','FAILED_TRANSACTION','PENDING')) as failures,
            (select count(*) from marketdata.capture_state_members where session_id=:id) as states,
            (select coalesce(sum(duplicates),0) from marketdata.capture_state_members where session_id=:id) as state_duplicates
            from marketdata.capture_dispositions where session_id=:id
            """).param("id",id).query((r,n)->new long[]{r.getLong("tx"),r.getLong("states"),Math.addExact(r.getLong("duplicates"),r.getLong("state_duplicates")),r.getLong("conflicts"),r.getLong("failures")}).single();
    }
    public List<io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.GapCount> monitorGapCounts(UUID id) {
        var result=jdbc.sql("""
            select case when octet_length(pool)+octet_length(kind)+octet_length(status)<=131072 then pool end as pool,
            case when octet_length(pool)+octet_length(kind)+octet_length(status)<=131072 then kind end as kind,
            case when octet_length(pool)+octet_length(kind)+octet_length(status)<=131072 then status end as status,
            octet_length(pool)+octet_length(kind)+octet_length(status)<=131072 as fits,count(*) as n
            from marketdata.capture_gaps where session_id=:id and not(kind='TRANSACTION' and status='RECOVERED_TRANSACTIONS')
            group by pool,kind,status order by pool collate "C",kind collate "C",status collate "C" limit 65
            """).param("id",id).query((r,n)->{if(!r.getBoolean("fits"))throw monitorLimit();return new io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.GapCount(r.getString("pool"),r.getString("kind"),r.getString("status"),r.getLong("n"));}).list();
        if(result.size()>64)throw monitorLimit();return result;
    }
    public List<Gap> monitorDetails(UUID id) {
        return jdbc.sql("""
            with bounded as (select gap_id,pool,kind,reason,from_slot,to_slot,opened_at,closed_at,boundary_signature,status
            from marketdata.capture_gaps where session_id=:id and not(kind='TRANSACTION' and status='RECOVERED_TRANSACTIONS') order by gap_id limit 101),
            sized as (select gap_id,pool,kind,reason,from_slot,to_slot,opened_at,closed_at,boundary_signature,status,
            sum(octet_length(pool)+octet_length(kind)+octet_length(reason)+coalesce(octet_length(boundary_signature),0)+octet_length(status)) over()<=131072 as fits from bounded)
            select gap_id,from_slot,to_slot,opened_at,closed_at,fits,
            case when fits then pool end as pool,case when fits then kind end as kind,case when fits then reason end as reason,
            case when fits then boundary_signature end as boundary_signature,case when fits then status end as status from sized order by gap_id
            """).param("id",id).query((r,n)->{if(!r.getBoolean("fits"))throw monitorLimit();return new Gap(r.getLong("gap_id"),r.getString("pool"),r.getString("kind"),r.getString("reason"),(Long)r.getObject("from_slot"),(Long)r.getObject("to_slot"),instant(r,"opened_at"),instant(r,"closed_at"),r.getString("boundary_signature"),r.getString("status"));}).list();
    }
    private static IllegalArgumentException monitorLimit() {return new IllegalArgumentException("CAPTURE_MONITOR_RESULT_LIMIT");}
	public String encode(Object value) { return json.writeValueAsString(value); }
	/** First admission lock, shared by manual start and enrollment; held through owning commit. */
	public void providerAdmissionLock(String provider) {
		jdbc.sql("set local statement_timeout='10s'").update();
		jdbc.sql("set local lock_timeout='10s'").update();
		jdbc.sql("select pg_advisory_xact_lock(1129464656,hashtext(:provider))").param("provider",provider).query((r,n)->0).single();
	}
	public void manualStartGuard(String provider) {
		if(!jdbc.sql("show transaction_isolation").query(String.class).single().equals("read committed"))
			throw new IllegalStateException("CAPTURE_ADMISSION_REQUIRES_READ_COMMITTED");
		if(!jdbc.sql("select to_regclass('marketdata.capture_deployments') is not null").query(Boolean.class).single())return;
		providerAdmissionLock(provider);
		// Separate post-lock statement obtains a fresh READ_COMMITTED snapshot after a competing commit.
		if(jdbc.sql("select exists(select 1 from marketdata.capture_deployments where provider=:provider)").param("provider",provider).query(Boolean.class).single())
			throw new IllegalStateException("ENROLLED_PROVIDER_REQUIRES_ADMISSION");
	}
	public void continuousFence(UUID session,String holder,Instant now) {
		if(!continuousSchemaPresent())return;
		var valid=jdbc.sql("""
			select (d.holder||':'||d.generation::text)=:holder and d.locked_until>:now and d.state='RUNNING'
			and s.sealed_at is null and s.state='CAPTURING' as valid
			from marketdata.capture_segments s join marketdata.capture_deployments d using(deployment_key)
			where s.session_id=:session for update of d,s
			""").param("session",session).param("holder",holder).param("now",time(now)).query(Boolean.class).optional();
		if(valid.isPresent() && !valid.get())throw new IllegalStateException("CONTINUOUS_CAPTURE_FENCE_LOST");
	}
	private boolean continuousSchemaPresent() {
		if(continuousSchemaPresent)return true;
		synchronized(this) {
			if(!continuousSchemaPresent)continuousSchemaPresent=jdbc.sql("select to_regclass('marketdata.capture_segments') is not null").query(Boolean.class).single();
			return continuousSchemaPresent;
		}
	}
	/** Materialization and the mutation's guard dependency lock d,s before the session row. */
	private String continuousCte() {
		if(!continuousSchemaPresent())return "WITH continuous_guard AS MATERIALIZED (SELECT true AS valid), ";
		return """
			WITH continuous_rows AS MATERIALIZED (
			select (d.holder||':'||d.generation::text)=:holder and d.locked_until>:now and d.state='RUNNING'
			and s.sealed_at is null and s.state='CAPTURING' as valid
			from marketdata.capture_segments s join marketdata.capture_deployments d using(deployment_key)
			where s.session_id=:id for update of d,s
			), continuous_guard AS MATERIALIZED (select coalesce(bool_and(valid),true) as valid from continuous_rows),
			""";
	}
	private static final String FENCED_RESULT=" SELECT g.valid AS continuous_valid,m.* FROM continuous_guard g LEFT JOIN mutation m ON true";
	private static void checkContinuous(ResultSet r) throws SQLException {
		if(!r.getBoolean("continuous_valid"))throw new IllegalStateException("CONTINUOUS_CAPTURE_FENCE_LOST");
	}
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
		return jdbc.sql(continuousCte()+"""
				mutation AS (
				UPDATE marketdata.capture_sessions SET holder=:holder,locked_until=:until
				WHERE session_id=:id AND state='RUNNING' AND ends_at>:now
				AND (holder=:holder OR locked_until<=:now) AND (select valid from continuous_guard) RETURNING 1 AS changed)
				"""+FENCED_RESULT).param("id", id).param("holder", holder).param("now", time(now)).param("until", time(until))
				.query((r,n)->{checkContinuous(r);return r.getObject("changed")!=null;}).single();
	}
	public void fence(UUID id, String holder, Instant now, Instant until) {
		var valid = jdbc.sql(continuousCte()+"""
				mutation AS (
				UPDATE marketdata.capture_sessions SET locked_until=:until WHERE session_id=:id
				AND holder=:holder AND locked_until>:now AND state='RUNNING' AND (select valid from continuous_guard)
				RETURNING 1 AS changed)
				"""+FENCED_RESULT).param("id", id).param("holder", holder).param("now", time(now)).param("until",time(until))
				.query((r,n)->{checkContinuous(r);return r.getObject("changed")!=null;}).single();
		if (!valid) throw new IllegalStateException("CAPTURE_LEASE_LOST");
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
		if (java.util.Set.of("CAPTURED","FAILED_TRANSACTION").contains(status) && hash!=null) {
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
		// Legacy checkpoints may reference unavailable fetches. Resolve evidence without mutating old rows.
		// Signatures are identities, not intra-slot order: ambiguous latest slots use a known older slot.
		return jdbc.sql("""
				WITH supported AS (
				SELECT d.signature,d.slot FROM marketdata.capture_dispositions d
				JOIN marketdata.capture_sessions s USING(session_id)
				JOIN marketdata.raw_transactions r ON r.chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp'
				AND r.provider=(s.configuration_json::jsonb->>'provider') AND r.transaction_value=d.signature AND r.payload_hash=d.raw_digest AND r.observed_block_position=d.slot
				WHERE d.session_id=:id AND d.pool=:pool AND d.status IN ('CAPTURED','FAILED_TRANSACTION'))
				SELECT d.signature FROM supported d WHERE NOT EXISTS
				(SELECT 1 FROM supported other WHERE other.slot=d.slot AND other.signature<>d.signature)
				ORDER BY d.slot DESC LIMIT 1
				""")
				.param("id", id).param("pool", pool).query(String.class).optional();
	}
	public List<Disposition> dispositions(UUID id) {
		return jdbc.sql("SELECT * FROM marketdata.capture_dispositions WHERE session_id=:id ORDER BY slot,pool COLLATE \"C\",signature COLLATE \"C\"")
				.param("id", id).query((r, n) -> new Disposition(r.getString("pool"), r.getString("signature"), r.getLong("slot"),
						r.getString("status"), r.getString("raw_digest"), r.getString("notification"), r.getString("response"),
						instant(r,"received_at"), instant(r,"admitted_at"), r.getLong("duplicates"), r.getLong("conflicts"))).list();
	}
	public List<Disposition> pending(UUID id,int limit,long bytes) {
		return jdbc.sql("WITH selected AS (SELECT pool,signature,slot,notification,received_at FROM marketdata.capture_dispositions WHERE session_id=:id AND status='PENDING' ORDER BY slot,pool COLLATE \"C\",signature COLLATE \"C\" LIMIT :limit) SELECT * FROM (SELECT *,sum(octet_length(notification)) OVER(ORDER BY slot,pool COLLATE \"C\",signature COLLATE \"C\") AS selected_bytes FROM selected) bounded WHERE selected_bytes<=:bytes ORDER BY slot,pool COLLATE \"C\",signature COLLATE \"C\"")
			.param("id",id).param("limit",limit).param("bytes",bytes).query((r,n)->new Disposition(r.getString("pool"),r.getString("signature"),r.getLong("slot"),"PENDING",null,r.getString("notification"),"",instant(r,"received_at"),null,0,0)).list();
	}
	public java.util.Set<String> successfulPage(UUID id,String pool,List<String> signatures) {
		if(signatures.isEmpty()) return java.util.Set.of(); if(signatures.size()>100) throw new IllegalArgumentException("RECOVERY_PAGE_KEY_LIMIT");
		return java.util.Set.copyOf(jdbc.sql("""
				SELECT d.signature FROM marketdata.capture_dispositions d JOIN marketdata.capture_sessions s USING(session_id)
				JOIN marketdata.raw_transactions r ON r.chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp' AND r.provider=(s.configuration_json::jsonb->>'provider')
				AND r.transaction_value=d.signature AND r.payload_hash=d.raw_digest AND r.observed_block_position=d.slot
				WHERE d.session_id=:id AND d.pool=:pool AND d.signature IN (:signatures) AND d.status IN ('CAPTURED','FAILED_TRANSACTION')
				""").param("id",id).param("pool",pool).param("signatures",signatures).query(String.class).list());
	}
	public record CaptureProof(String pool,long slot,String status,String digest,String provider,String version,String notification,String response,Instant received,Instant admitted) { }
	public List<CaptureProof> captureProofs(UUID id,String signature) {
		return jdbc.sql("""
				SELECT d.*,s.configuration_json::jsonb->>'provider' AS provider,s.configuration_json::jsonb->>'version' AS version
				FROM marketdata.capture_dispositions d JOIN marketdata.capture_sessions s USING(session_id)
				WHERE d.session_id=:id AND d.signature=:signature AND d.status IN ('CAPTURED','FAILED_TRANSACTION','CONFLICT')
				ORDER BY d.pool COLLATE "C" LIMIT 4
				""").param("id",id).param("signature",signature)
				.query((r,n)->new CaptureProof(r.getString("pool"),r.getLong("slot"),r.getString("status"),r.getString("raw_digest"),r.getString("provider"),r.getString("version"),r.getString("notification"),r.getString("response"),instant(r,"received_at"),instant(r,"admitted_at"))).list();
	}
	public void reuseTarget(UUID id,String pool,String signature,long slot) {
		var valid=jdbc.sql("SELECT count(*) FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND signature=:sig AND slot=:slot AND status<>'CONFLICT'").param("id",id).param("pool",pool).param("sig",signature).param("slot",slot).query(Long.class).single();
		if(valid!=1) throw new IllegalStateException("CAPTURE_REUSE_IDENTITY_CONFLICT");
	}
	public Optional<Envelope> historyPage(UUID id,String digest) {
		return jdbc.sql("SELECT * FROM marketdata.capture_envelopes WHERE session_id=:id AND kind='HTTP_getTransactionsForAddress' AND digest=:digest")
			.param("id",id).param("digest",digest).query((r,n)->new Envelope(r.getString("kind"),r.getString("digest"),r.getString("payload"),instant(r,"received_at"))).optional();
	}
	public Instant historyPageReceivedAt(UUID id,String digest) {
		return jdbc.sql("SELECT received_at FROM marketdata.capture_envelopes WHERE session_id=:id AND kind='HTTP_getTransactionsForAddress' AND digest=:digest")
			.param("id",id).param("digest",digest).query((r,n)->instant(r,"received_at")).optional().orElseThrow(()->new IllegalStateException("RECOVERY_PROVENANCE_MISSING"));
	}
	public String historyContext(UUID id,String digest,String pool,String cursor) {
		return jdbc.sql("SELECT payload FROM marketdata.capture_envelopes WHERE session_id=:id AND kind='CAPTURE_HISTORY_CONTEXT' AND payload::jsonb->>'pageDigest'=:digest AND payload::jsonb->>'pool'=:pool AND payload::jsonb->>'cursor'=:cursor")
			.param("id",id).param("digest",digest).param("pool",pool).param("cursor",cursor).query(String.class).single();
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
	/** Bounded already-admitted tail; each SQL statement handles the same validated batch. */
	public void tail(UUID id,Configuration c,List<java.util.Map<String,Object>> rows,Instant now,Runnable guard) {
		if(rows.isEmpty()) return; var text=json.writeValueAsString(rows);
		var input="jsonb_to_recordset(CAST(:rows AS jsonb)) AS x(ord integer,pool text,account text,sig text,slot bigint,data text,envelope text,digest text,encoding text,received timestamptz)";
		guard.run();
		jdbc.sql("INSERT INTO marketdata.raw_account_snapshots SELECT 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',:provider,x.account,x.slot,x.digest,x.encoding,x.data,x.envelope,x.received,:now,:now FROM (SELECT DISTINCT ON(account COLLATE \"C\",slot,digest COLLATE \"C\") * FROM "+input+" WHERE account IS NOT NULL ORDER BY account COLLATE \"C\",slot,digest COLLATE \"C\",ord) x ON CONFLICT DO NOTHING")
			.param("rows",text).param("provider",c.provider()).param("now",time(now)).update(); guard.run();
		var conflict=jdbc.sql("SELECT EXISTS (SELECT 1 FROM "+input+" JOIN marketdata.raw_account_snapshots r ON r.chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp' AND r.provider=:provider AND r.account=x.account AND r.slot=x.slot AND r.digest=x.digest WHERE r.payload<>x.data)")
			.param("rows",text).param("provider",c.provider()).query(Boolean.class).single(); guard.run(); if(conflict) throw new IllegalStateException("ACCOUNT_CONTENT_CONFLICT");
		jdbc.sql("INSERT INTO marketdata.capture_state_members SELECT :id,'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',:provider,account,slot,digest,count(*)-1 FROM "+input+" WHERE account IS NOT NULL GROUP BY account,slot,digest ON CONFLICT(session_id,account,slot,digest) DO UPDATE SET duplicates=marketdata.capture_state_members.duplicates+EXCLUDED.duplicates+1")
			.param("id",id).param("provider",c.provider()).param("rows",text).update(); guard.run();
		jdbc.sql("INSERT INTO marketdata.capture_dispositions(session_id,pool,signature,slot,status,notification,received_at) SELECT :id,pool,sig,slot,'PENDING',envelope,received FROM (SELECT DISTINCT ON(pool COLLATE \"C\",sig COLLATE \"C\") * FROM "+input+" WHERE account IS NULL ORDER BY pool COLLATE \"C\",sig COLLATE \"C\",ord) x ON CONFLICT DO NOTHING")
			.param("id",id).param("rows",text).update(); guard.run();
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
		var signature=boundary(id,pool).orElse(null);
		var slot=signature==null?null:jdbc.sql("SELECT slot FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND signature=:sig")
			.param("id",id).param("pool",pool).param("sig",signature).query(Long.class).single();
		jdbc.sql("""
				INSERT INTO marketdata.capture_gaps(session_id,pool,kind,reason,from_slot,opened_at,boundary_signature,status)
				VALUES (:id,:pool,:kind,:reason,
				:slot,:now,:signature,:status)
				""").param("id", id).param("pool", pool).param("kind", kind).param("reason", reason).param("now", time(now))
				.param("slot",slot,java.sql.Types.BIGINT).param("signature",signature,java.sql.Types.VARCHAR)
				.param("status", "STATE".equals(kind) ? "UNRESOLVED_STATE_GAP" : "UNRESOLVED_TRANSACTION_GAP").update();
	}
	public void recovered(UUID id, String pool, String boundary, Instant now) {
		jdbc.sql("""
				UPDATE marketdata.capture_gaps SET status='RECOVERED_TRANSACTIONS',closed_at=:now
				WHERE session_id=:id AND pool=:pool AND kind='TRANSACTION' AND boundary_signature=:boundary
				AND NOT EXISTS(SELECT 1 FROM marketdata.capture_dispositions WHERE session_id=:id AND pool=:pool AND status IN ('PENDING','CONFLICT','UNVERIFIED_FINALITY','UNAVAILABLE','FETCH_FAILURE'))
				""").param("id", id).param("pool", pool).param("boundary", boundary).param("now", time(now)).update();
	}
	public void recoveredGap(UUID id,long gap,String boundary,Instant now) {
		jdbc.sql("UPDATE marketdata.capture_gaps SET status='RECOVERED_TRANSACTIONS',closed_at=:now WHERE session_id=:id AND gap_id=:gap AND kind='TRANSACTION' AND boundary_signature=:boundary AND status='UNRESOLVED_TRANSACTION_GAP'")
			.param("id",id).param("gap",gap).param("boundary",boundary).param("now",time(now)).update();
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
	public boolean captureAdvance(Configuration c,String holder,Instant now,long requests,long received,long subscription,long retained,long rpc) {
		return captureAdvance(c,holder,now,requests,received,subscription,retained,rpc,false);
	}
	public boolean captureAdvance(Configuration c,String holder,Instant now,long requests,long received,long subscription,long retained,long rpc,boolean closing) {
		var l=c.limits();
		var result=jdbc.sql(continuousCte()+"""
				admission AS (
				SELECT session_id,requests<=:maxRequests-:requests AND received_bytes<=:maxReceived-:received
				AND subscription_bytes<=:maxSubscription-:subscription AND retained_bytes<=:maxRetained-:retained
				AND rpc_hundredths<=:maxRpc-:rpc AND (:closing OR ends_at>:now)
				AND NOT EXISTS (SELECT 1 FROM marketdata.capture_periods p WHERE p.account_period=s.account_period
				AND p.known_overage_hundredths>p.ceiling_hundredths-p.reserved_hundredths) AS fits
				FROM marketdata.capture_sessions s WHERE session_id=:id AND holder=:holder AND locked_until>:now AND state='RUNNING'
				AND (select valid from continuous_guard) FOR UPDATE
				), mutation AS (
				UPDATE marketdata.capture_sessions s SET requests=requests+CASE WHEN a.fits THEN :requests ELSE 0 END,
				received_bytes=received_bytes+:received,subscription_bytes=subscription_bytes+:subscription,
				retained_bytes=retained_bytes+:retained,rpc_hundredths=rpc_hundredths+CASE WHEN a.fits THEN :rpc ELSE 0 END,locked_until=:until
				FROM admission a WHERE s.session_id=a.session_id RETURNING s.subscription_bytes,a.fits)
				"""+FENCED_RESULT).param("id",c.sessionId()).param("holder",holder).param("now",time(now)).param("until",time(now.plusSeconds(l.leaseSeconds()))).param("closing",closing)
				.param("requests",requests).param("received",received).param("subscription",subscription).param("retained",retained).param("rpc",rpc)
				.param("maxRequests",l.requests()).param("maxReceived",l.receivedBytes()).param("maxSubscription",l.subscriptionBytes())
				.param("maxRetained",l.retainedBytes()).param("maxRpc",l.rpcHundredths())
				.query((r,n)->{checkContinuous(r);if(r.getObject("subscription_bytes")==null)throw new IllegalStateException("CAPTURE_LEASE_LOST");return new long[]{r.getLong("subscription_bytes"),r.getBoolean("fits")?1:0};}).single();
		var extra=io.cryptoresearch.marketdata.application.CaptureBudget.subscriptionOverageIncrement(c,result[0],subscription);
		if(extra>0) jdbc.sql("UPDATE marketdata.capture_periods SET known_overage_hundredths=known_overage_hundredths+:extra WHERE account_period=:period")
				.param("extra",extra).param("period",c.authority().accountPeriod()).update();
		return result[1]==1;
	}
	public void stop(UUID id, String reason) {
		jdbc.sql("UPDATE marketdata.capture_sessions SET state='STOPPED',reason=:reason WHERE session_id=:id")
				.param("id",id).param("reason",reason).update();
	}
	/** One statement/snapshot; no notification, response or account payload leaves this projection. */
	public java.util.Map<String,Object> summarySnapshot(UUID id) {
		return jdbc.sql("""
				WITH supported AS (
				SELECT d.pool,d.signature,d.slot FROM marketdata.capture_dispositions d
				JOIN marketdata.capture_sessions s USING(session_id)
				JOIN marketdata.raw_transactions r ON r.chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp'
				AND r.provider=(s.configuration_json::jsonb->>'provider') AND r.transaction_value=d.signature
				AND r.payload_hash=d.raw_digest AND r.observed_block_position=d.slot
				WHERE d.session_id=:id AND d.status IN ('CAPTURED','FAILED_TRANSACTION')),
				positions AS (SELECT DISTINCT ON (d.pool COLLATE "C") d.pool,d.signature,d.slot FROM supported d
				WHERE NOT EXISTS (SELECT 1 FROM supported other WHERE other.pool=d.pool AND other.slot=d.slot AND other.signature<>d.signature)
				ORDER BY d.pool COLLATE "C",d.slot DESC)
				SELECT jsonb_build_object('requests',s.requests,'received',s.received_bytes,'wss',s.subscription_bytes,
				'retained',s.retained_bytes,'rpcHundredths',s.rpc_hundredths,'reservedHundredths',s.reserved_hundredths)::text AS durable,
				COALESCE((SELECT jsonb_object_agg(status,n) FROM (SELECT status,count(*) AS n FROM marketdata.capture_dispositions WHERE session_id=:id GROUP BY status) counts),'{}'::jsonb)::text AS counts,
				(SELECT jsonb_object_agg(pool.value->>'address',COALESCE((SELECT jsonb_build_object('signature',p.signature,'slot',p.slot) FROM positions p WHERE p.pool=pool.value->>'address'),'"UNKNOWN"'::jsonb)) FROM jsonb_array_elements(s.configuration_json::jsonb->'pools') pool(value))::text AS positions
				FROM marketdata.capture_sessions s WHERE s.session_id=:id
				""").param("id",id).query((r,n)->{
			var result=new java.util.LinkedHashMap<String,Object>();
			result.put("durable",json.readTree(r.getString("durable"))); result.put("durableDispositions",json.readTree(r.getString("counts"))); result.put("committedPositions",json.readTree(r.getString("positions"))); return result;
		}).single();
	}
	/** Closing gaps use the same immutable raw-position proof as boundary(), in one fixed-pool statement. */
	public void terminalGaps(UUID id,String reason,Instant now) {
		jdbc.sql("""
				WITH supported AS (
				SELECT d.pool,d.signature,d.slot FROM marketdata.capture_dispositions d JOIN marketdata.capture_sessions s USING(session_id)
				JOIN marketdata.raw_transactions r ON r.chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp'
				AND r.provider=(s.configuration_json::jsonb->>'provider') AND r.transaction_value=d.signature AND r.payload_hash=d.raw_digest AND r.observed_block_position=d.slot
				WHERE d.session_id=:id AND d.status IN ('CAPTURED','FAILED_TRANSACTION')),
				positions AS (SELECT DISTINCT ON (d.pool COLLATE "C") d.pool,d.signature,d.slot FROM supported d
				WHERE NOT EXISTS (SELECT 1 FROM supported other WHERE other.pool=d.pool AND other.slot=d.slot AND other.signature<>d.signature)
				ORDER BY d.pool COLLATE "C",d.slot DESC)
				INSERT INTO marketdata.capture_gaps(session_id,pool,kind,reason,from_slot,opened_at,boundary_signature,status)
				SELECT s.session_id,pool.value->>'address',k.kind,:reason,p.slot,:now,p.signature,
				CASE WHEN k.kind='TRANSACTION' THEN 'UNRESOLVED_TRANSACTION_GAP' ELSE 'UNRESOLVED_STATE_GAP' END
				FROM marketdata.capture_sessions s CROSS JOIN LATERAL jsonb_array_elements(s.configuration_json::jsonb->'pools') pool(value)
				CROSS JOIN (VALUES ('TRANSACTION'),('STATE')) k(kind) LEFT JOIN positions p ON p.pool=pool.value->>'address' WHERE s.session_id=:id
				""").param("id",id).param("reason",reason).param("now",time(now)).update();
	}
	public void observe(UUID id,String holder,long bytes,long subscription,Instant now) {
		var changed=jdbc.sql(continuousCte()+"""
				mutation AS (
				UPDATE marketdata.capture_sessions SET received_bytes=received_bytes+:bytes,subscription_bytes=subscription_bytes+:subscription
				WHERE session_id=:id AND holder=:holder AND (select valid from continuous_guard) RETURNING subscription_bytes)
				"""+FENCED_RESULT).param("id",id).param("holder",holder).param("now",time(now)).param("bytes",bytes).param("subscription",subscription)
				.query((r,n)->{checkContinuous(r);if(r.getObject("subscription_bytes")==null)throw new IllegalStateException("CAPTURE_LEASE_LOST");return r.getLong("subscription_bytes");}).single();
		var c=configuration(id); var extra=io.cryptoresearch.marketdata.application.CaptureBudget.subscriptionOverageIncrement(c,changed,subscription);
		if(extra>0) jdbc.sql("UPDATE marketdata.capture_periods SET known_overage_hundredths=known_overage_hundredths+:extra WHERE account_period=:period")
				.param("extra",extra).param("period",c.authority().accountPeriod()).update();
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
