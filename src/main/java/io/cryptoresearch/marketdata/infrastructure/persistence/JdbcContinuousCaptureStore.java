package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.*;
import tools.jackson.databind.ObjectMapper;

/** SQL only; deployment transitions are owned by the application ledger. */
@Repository
public class JdbcContinuousCaptureStore {
	public record Fence(String deployment,String holder,long generation) {
		public String sessionHolder() {return holder+":"+generation;}
	}
	public record Deployment(Configuration configuration,String digest,Resources reserved,String state,String reason,Fence fence,Instant until) { }
	public record Segment(String deployment,int ordinal,io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration configuration,
			String digest,Resources allowances,String state,String reason,Instant sealedAt,Instant decodeEnd,String cursor,String batch,
			long sources,long processed,int pages,long receiptBytes) { }
	public record Receipt(String from,String through,String batch,long count) { }
	public record SourceKey(String signature,long bytes,boolean valid) { }
	public record Raw(String signature,long slot,String digest,String payload,Instant sourceTime,Instant received,Instant admitted,Instant ingested) { }
	public record Binding(String pool,String signature,long slot,String status,String digest,String notification,Instant received,Instant admitted) { }
	public record Page(List<Raw> raw,List<Binding> bindings,List<io.cryptoresearch.marketdata.api.PumpSwapCapture.Envelope> proofs,String through) {
		public Page {raw=List.copyOf(raw);bindings=List.copyOf(bindings);proofs=List.copyOf(proofs);}
	}
	private final JdbcClient jdbc;
	private static final String SEGMENT_COLUMNS="deployment_key,ordinal,configuration_json,configuration_digest,allowances,state,reason,sealed_at,decode_end,cursor_signature,last_batch,source_count,processed_count,page_count,receipt_bytes";
	private final ObjectMapper json;
	private final JdbcCaptureStore capture;
	public JdbcContinuousCaptureStore(JdbcClient jdbc,ObjectMapper json,JdbcCaptureStore capture) {this.jdbc=jdbc;this.json=json;this.capture=capture;}
	public String encode(Object o) {return json.writeValueAsString(o);}
	public void timeout() {jdbc.sql("set local statement_timeout='10s'").update();jdbc.sql("set local lock_timeout='10s'").update();}
	private static OffsetDateTime time(Instant i) {return i.atOffset(ZoneOffset.UTC);}
	private static Instant instant(ResultSet r,String name) throws SQLException {var v=r.getObject(name,OffsetDateTime.class);return v==null?null:v.toInstant();}
	private static String array(Resources r) {return "{"+r.requests()+","+r.receivedBytes()+","+r.subscriptionBytes()+","+r.retainedBytes()+","+r.rpcHundredths()+","+r.reservedHundredths()+","+r.seconds()+"}";}
	private static Resources resources(ResultSet r,String field) throws SQLException {var a=(Long[])r.getArray(field).getArray();return new Resources(a[0],a[1],a[2],a[3],a[4],a[5],a[6]);}
	public void insert(Configuration c,String digest,String holder,Instant until) {
		jdbc.sql("""
			insert into marketdata.capture_deployments(deployment_key,provider,account_period,owner_authorization,configuration_json,
			configuration_digest,allowances,reserved,global_end,max_segments,holder,generation,locked_until)
			values(:key,:provider,:period,:authority,:config,:digest,cast(:caps as bigint[]),cast(:opening as bigint[]),:end,:count,:holder,1,:until)
			on conflict do nothing
			""").param("key",c.deploymentKey()).param("provider",c.template().provider()).param("period",c.template().authority().accountPeriod())
			.param("authority",c.template().authority().ownerAuthorization()).param("config",encode(c)).param("digest",digest)
			.param("caps",array(c.allowances())).param("opening",array(c.opening())).param("end",time(c.globalEnd()))
			.param("count",c.maxSegments()).param("holder",holder).param("until",time(until)).update();
	}
	public Deployment lockProvider(String provider) {
		return jdbc.sql("select * from marketdata.capture_deployments where provider=:provider for update").param("provider",provider).query((r,n)->
			new Deployment(json.readValue(r.getString("configuration_json"),Configuration.class),r.getString("configuration_digest"),resources(r,"reserved"),
				r.getString("state"),r.getString("reason"),new Fence(r.getString("deployment_key"),r.getString("holder"),r.getLong("generation")),instant(r,"locked_until"))).single();
	}
	public void lockPeriod(String period) {jdbc.sql("select account_period from marketdata.capture_periods where account_period=:period for update").param("period",period).query(String.class).single();}
	public Resources priorAuthority(String period) {
		var unknown=jdbc.sql("""
			select count(*) from marketdata.capture_sessions where account_period=:period and exists (
			select 1 from unnest(array['requests','receivedBytes','subscriptionBytes','retainedBytes','rpcHundredths','windowSeconds']) dimension
			where configuration_json::jsonb->'limits'->>dimension is null)
			""").param("period",period).query(Long.class).single();
		if(unknown!=0)throw new IllegalStateException("UNKNOWN_DEPLOYMENT_OPENING_USAGE");
		return jdbc.sql("""
			select coalesce(sum((configuration_json::jsonb->'limits'->>'requests')::bigint),0) as requests,
			coalesce(sum((configuration_json::jsonb->'limits'->>'receivedBytes')::bigint),0) as received,
			coalesce(sum((configuration_json::jsonb->'limits'->>'subscriptionBytes')::bigint),0) as subscription,
			coalesce(sum((configuration_json::jsonb->'limits'->>'retainedBytes')::bigint),0) as retained,
			coalesce(sum((configuration_json::jsonb->'limits'->>'rpcHundredths')::bigint),0) as rpc,
			coalesce(sum((configuration_json::jsonb->'limits'->>'windowSeconds')::bigint),0) as seconds,
			(select reserved_hundredths+known_overage_hundredths from marketdata.capture_periods where account_period=:period) as cu
			from marketdata.capture_sessions where account_period=:period
			""").param("period",period).query((r,n)->new Resources(r.getLong("requests"),r.getLong("received"),r.getLong("subscription"),r.getLong("retained"),r.getLong("rpc"),r.getLong("cu"),r.getLong("seconds"))).single();
	}
	/** Read-only admission check in the same finite snapshot as precise source selection. */
	public void readFence(Fence f,Segment expected,Instant now) {
		var valid=jdbc.sql("""
			select count(*) from marketdata.capture_deployments d join marketdata.capture_segments s using(deployment_key)
			where d.deployment_key=:key and d.holder=:holder and d.generation=:generation and d.locked_until>:now
			and d.state='RUNNING' and s.ordinal=:ordinal and s.state='DERIVING' and s.sealed_at is not null
			""").param("key",f.deployment()).param("holder",f.holder()).param("generation",f.generation()).param("now",time(now))
			.param("ordinal",expected.ordinal()).query(Integer.class).single();
		if(valid!=1)throw new IllegalStateException("CONTINUOUS_CAPTURE_FENCE_LOST");
		if(!segment(f.deployment(),expected.ordinal()).equals(expected))throw new IllegalStateException("DECODE_PAGE_ADMISSION_CONFLICT");
	}
	public long sourceCount(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c) {
		return jdbc.sql("select count(distinct signature) from marketdata.capture_dispositions where session_id=:session and raw_digest is not null and status in ('CAPTURED','FAILED_TRANSACTION')")
			.param("session",c.sessionId()).query(Long.class).single();
	}
	public Page page(Segment s,String cursor) {
		if(s.sealedAt()==null || !s.state().equals("DERIVING"))throw new IllegalStateException("CAPTURE_NOT_SEALED");
		var c=s.configuration();
		var candidates=jdbc.sql("""
			with keys as materialized (
			select distinct k.signature collate "C" as signature from unnest(cast(:pools as text[])) p(pool) cross join lateral (
				select signature from marketdata.capture_dispositions where session_id=:session and pool=p.pool
				and status in ('CAPTURED','FAILED_TRANSACTION') and raw_digest is not null
				and signature collate "C">:cursor collate "C" order by signature collate "C" limit 129
			) k order by k.signature collate "C" limit 129)
			select d.signature,coalesce(max(octet_length(r.payload)),0)+sum(octet_length(d.notification)) as bytes,
			bool_and(r.transaction_value is not null and r.payload_hash=d.raw_digest and r.observed_block_position=d.slot)
			and count(*)<=3 and count(distinct d.pool)=count(*) and max(octet_length(r.payload))<=8000000
			and sum(octet_length(d.notification))<=8000000 as valid
			from keys k join marketdata.capture_dispositions d on d.signature=k.signature and d.session_id=:session
			left join marketdata.raw_transactions r
			on r.chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp' and r.provider=:provider and r.transaction_value=d.signature
			where d.session_id=:session and d.status in ('CAPTURED','FAILED_TRANSACTION') and d.raw_digest is not null
			and d.signature collate "C">:cursor collate "C" group by d.signature order by d.signature collate "C" limit 129
			""").param("provider",c.provider()).param("session",c.sessionId()).param("cursor",cursor)
			.param("pools","{"+c.pools().stream().map(io.cryptoresearch.marketdata.api.PumpSwapCapture.Pool::address).collect(java.util.stream.Collectors.joining(","))+"}")
			.query((r,n)->new SourceKey(r.getString("signature"),r.getLong("bytes"),r.getBoolean("valid"))).list();
		var selected=new ArrayList<String>();long bytes=0;
		for(var k:candidates) {
			if(selected.size()==128)break;
			if(!k.valid() || k.bytes()>64000000)throw new IllegalStateException("SEALED_SOURCE_PROOF_OR_BYTE_LIMIT");
			if(k.bytes()>64000000-bytes)break;
			bytes=Math.addExact(bytes,k.bytes());selected.add(k.signature());
		}
		if(selected.isEmpty())return new Page(List.of(),List.of(),List.of(),cursor);
		var raw=jdbc.sql("""
			select transaction_value,observed_block_position,payload_hash,payload,source_event_time,received_at,admitted_at,ingested_at
			from marketdata.raw_transactions where chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp' and provider=:provider
			and transaction_value in (:keys) order by transaction_value collate "C" limit 129
			""").param("provider",c.provider()).param("keys",selected).query((r,n)->new Raw(r.getString("transaction_value"),r.getLong("observed_block_position"),
				r.getString("payload_hash"),r.getString("payload"),instant(r,"source_event_time"),instant(r,"received_at"),instant(r,"admitted_at"),instant(r,"ingested_at"))).list();
		var bindings=jdbc.sql("""
			select pool,signature,slot,status,raw_digest,notification,received_at,admitted_at from marketdata.capture_dispositions
			where session_id=:session and signature in (:keys) and raw_digest is not null and status in ('CAPTURED','FAILED_TRANSACTION')
			order by signature collate "C",pool collate "C" limit 385
			""").param("session",c.sessionId()).param("keys",selected).query((r,n)->new Binding(r.getString("pool"),r.getString("signature"),r.getLong("slot"),r.getString("status"),
				r.getString("raw_digest"),r.getString("notification"),instant(r,"received_at"),instant(r,"admitted_at"))).list();
		if(raw.size()!=selected.size() || bindings.size()>384)throw new IllegalStateException("SEALED_SOURCE_SET_CONFLICT");
		var digests=new TreeSet<String>();
		for(var b:bindings) {
			var n=json.readTree(b.notification());if(n.has("pageDigest"))digests.add(n.path("pageDigest").asText());
		}
		var proofs=new ArrayList<io.cryptoresearch.marketdata.api.PumpSwapCapture.Envelope>();
		if(!digests.isEmpty()) {
			var metadata=jdbc.sql("""
				select kind,digest,octet_length(payload) as bytes from marketdata.capture_envelopes where session_id=:session
				and ((kind='HTTP_getTransactionsForAddress' and digest in (:keys)) or
				(kind='CAPTURE_HISTORY_CONTEXT' and payload::jsonb->>'pageDigest' in (:keys))) order by kind collate "C",digest collate "C" limit 769
				""").param("session",c.sessionId()).param("keys",digests).query((r,n)->new SourceKey(r.getString("digest"),r.getLong("bytes"),true)).list();
			if(metadata.size()>768)throw new IllegalStateException("SEALED_HISTORY_PROOF_LIMIT");
			for(var m:metadata){bytes=Math.addExact(bytes,m.bytes());if(m.bytes()>8000000 || bytes>64000000)throw new IllegalStateException("SEALED_HISTORY_PROOF_LIMIT");}
			proofs.addAll(jdbc.sql("""
				select kind,digest,payload,received_at from marketdata.capture_envelopes where session_id=:session
				and ((kind='HTTP_getTransactionsForAddress' and digest in (:keys)) or
				(kind='CAPTURE_HISTORY_CONTEXT' and payload::jsonb->>'pageDigest' in (:keys))) order by kind collate "C",digest collate "C" limit 769
				""").param("session",c.sessionId()).param("keys",digests).query((r,n)->new io.cryptoresearch.marketdata.api.PumpSwapCapture.Envelope(r.getString("kind"),r.getString("digest"),r.getString("payload"),instant(r,"received_at"))).list());
		}
		return new Page(raw,bindings,proofs,selected.getLast());
	}
	public void boundary(Segment previous,io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration next,Instant at) {
		for(var p:next.pools()) {
			var signature=capture.boundary(previous.configuration().sessionId(),p.address()).orElse(null);
			Long slot=signature==null?null:jdbc.sql("select slot from marketdata.capture_dispositions where session_id=:session and pool=:pool and signature=:signature")
				.param("session",previous.configuration().sessionId()).param("pool",p.address()).param("signature",signature).query(Long.class).single();
			for(var kind:List.of("TRANSACTION","STATE")) {
			jdbc.sql("""
				insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status,from_slot,boundary_signature)
				values(:session,:pool,:kind,:reason,:at,:status,:slot,:signature)
				""").param("session",next.sessionId()).param("pool",p.address()).param("kind",kind)
				.param("slot",slot).param("signature",signature)
				.param("reason","SEGMENT_BOUNDARY:"+previous.configuration().sessionId()+":"+next.sessionId())
				.param("at",time(previous.configuration().startsAt().plusSeconds(previous.configuration().limits().windowSeconds())))
				.param("status",kind.equals("TRANSACTION")?"UNRESOLVED_TRANSACTION_GAP":"UNRESOLVED_STATE_GAP").update();
			}
		}
	}
	public Fence takeover(Deployment d,String holder,Instant now,Instant until) {
		if(d.until().isAfter(now)) {
			if(!d.fence().holder().equals(holder))throw new IllegalStateException("CONTINUOUS_CAPTURE_LEASE_HELD");
			return d.fence();
		}
		var generation=Math.addExact(d.fence().generation(),1);
		jdbc.sql("update marketdata.capture_deployments set holder=:holder,generation=:generation,locked_until=:until where deployment_key=:key")
			.param("holder",holder).param("generation",generation).param("until",time(until)).param("key",d.fence().deployment()).update();
		return new Fence(d.fence().deployment(),holder,generation);
	}
	public void fence(Fence f,Instant now,Instant until) {
		if(jdbc.sql("""
			update marketdata.capture_deployments set locked_until=:until where deployment_key=:key and holder=:holder
			and generation=:generation and locked_until>:now and state='RUNNING' returning 1
			""").param("until",time(until)).param("key",f.deployment()).param("holder",f.holder()).param("generation",f.generation())
			.param("now",time(now)).query(Integer.class).optional().isEmpty())throw new IllegalStateException("CONTINUOUS_CAPTURE_FENCE_LOST");
	}
	public void reserve(String key,Resources reserved) {
		jdbc.sql("update marketdata.capture_deployments set reserved=cast(:reserved as bigint[]) where deployment_key=:key")
			.param("reserved",array(reserved)).param("key",key).update();
	}
	public void terminal(Fence f,String reason) {
		jdbc.sql("update marketdata.capture_deployments set state='STOPPED',reason=:reason where deployment_key=:key")
			.param("key",f.deployment()).param("reason",reason).update();
	}
	/** Small payload-free projection; STOPPED/reason is the existing durable local stop marker. */
	public io.cryptoresearch.marketdata.application.ContinuousCaptureLedgerUseCase.TerminalObservation terminalObservation(String key) {
		return jdbc.sql("""
			select d.state,d.reason,d.reserved,p.reserved_hundredths,p.known_overage_hundredths,
			coalesce(s.ordinal,0) as ordinal,coalesce(c.state,'NONE') as session_state,coalesce(c.reason,'') as session_reason,
			octet_length(d.state)+octet_length(d.reason)+coalesce(octet_length(c.state),0)+coalesce(octet_length(c.reason),0)<=131072 as fits
			from marketdata.capture_deployments d join marketdata.capture_periods p using(account_period)
			left join lateral(select ordinal,session_id from marketdata.capture_segments where deployment_key=d.deployment_key order by ordinal desc limit 1) s on true
			left join marketdata.capture_sessions c on c.session_id=s.session_id where d.deployment_key=:key
			""").param("key",key).query((r,n)->{
				if(!r.getBoolean("fits"))throw new IllegalStateException("CAPTURE_TERMINAL_OBSERVATION_LIMIT");
				return new io.cryptoresearch.marketdata.application.ContinuousCaptureLedgerUseCase.TerminalObservation(r.getString("state"),r.getString("reason"),r.getInt("ordinal"),
					r.getString("session_state"),r.getString("session_reason"),resources(r,"reserved"),r.getLong("reserved_hundredths"),r.getLong("known_overage_hundredths"));
			}).single();
	}
	public Optional<Segment> latest(String key) {
		return jdbc.sql("select "+SEGMENT_COLUMNS+" from marketdata.capture_segments where deployment_key=:key order by ordinal desc limit 1 for update")
			.param("key",key).query((r,n)->mapSegment(r)).optional();
	}
	public Segment segment(String key,int ordinal) {
		return jdbc.sql("select "+SEGMENT_COLUMNS+" from marketdata.capture_segments where deployment_key=:key and ordinal=:ordinal")
			.param("key",key).param("ordinal",ordinal).query((r,n)->mapSegment(r)).single();
	}
	private Segment mapSegment(ResultSet r) throws SQLException {
		return new Segment(r.getString("deployment_key"),r.getInt("ordinal"),json.readValue(r.getString("configuration_json"),io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration.class),
			r.getString("configuration_digest"),resources(r,"allowances"),r.getString("state"),r.getString("reason"),instant(r,"sealed_at"),instant(r,"decode_end"),
			r.getString("cursor_signature"),r.getString("last_batch"),r.getLong("source_count"),r.getLong("processed_count"),r.getInt("page_count"),r.getLong("receipt_bytes"));
	}
	public void insertSegment(Configuration deployment,int ordinal,io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String digest,Resources allowance) {
		jdbc.sql("""
			insert into marketdata.capture_segments(deployment_key,ordinal,session_id,configuration_json,configuration_digest,allowances,receipt_bytes)
			values(:key,:ordinal,:session,:config,:digest,cast(:allowances as bigint[]),:bytes)
			""").param("key",deployment.deploymentKey()).param("ordinal",ordinal).param("session",c.sessionId()).param("config",encode(c))
			.param("digest",digest).param("allowances",array(allowance)).param("bytes",deployment.receiptBytes()).update();
	}
	public void seal(Segment s,Instant now,Instant deadline,long count,String reason) {
		jdbc.sql("""
			update marketdata.capture_segments set state='DERIVING',sealed_at=:at,decode_end=:deadline,source_count=:count,reason=:reason
			where deployment_key=:key and ordinal=:ordinal and sealed_at is null
			""").param("at",time(now)).param("deadline",time(deadline)).param("count",count).param("reason",reason).param("key",s.deployment()).param("ordinal",s.ordinal()).update();
	}
	public void state(Segment s,String state,String reason) {
		jdbc.sql("update marketdata.capture_segments set state=:state,reason=:reason where deployment_key=:key and ordinal=:ordinal")
			.param("state",state).param("reason",reason).param("key",s.deployment()).param("ordinal",s.ordinal()).update();
	}
	public Receipt receipt(Segment s,int index) {
		return jdbc.sql("select receipts_json::jsonb->:index from marketdata.capture_segments where deployment_key=:key and ordinal=:ordinal")
			.param("index",index).param("key",s.deployment()).param("ordinal",s.ordinal()).query((r,n)->json.readValue(r.getString(1),Receipt.class)).single();
	}
	public void progress(Segment s,Receipt receipt,long processed) {
		var text=encode(receipt);
		if(jdbc.sql("""
			update marketdata.capture_segments set cursor_signature=:cursor,last_batch=:batch,processed_count=:processed,
			page_count=page_count+1,receipts_json=(receipts_json::jsonb || cast(:receipt as jsonb))::text
			where deployment_key=:key and ordinal=:ordinal and cursor_signature=:from and state='DERIVING'
			and octet_length((receipts_json::jsonb || cast(:receipt as jsonb))::text)<=receipt_bytes returning 1
			""").param("cursor",receipt.through()).param("batch",receipt.batch()).param("processed",processed).param("receipt",text)
			.param("key",s.deployment()).param("ordinal",s.ordinal()).param("from",receipt.from()).query(Integer.class).optional().isEmpty())
			throw new IllegalStateException("DECODE_RECEIPT_OR_CURSOR_LIMIT");
	}
}
