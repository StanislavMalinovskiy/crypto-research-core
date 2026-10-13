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
	private volatile boolean continuationSchemaPresent;
	private volatile boolean replacementSchemaPresent;
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
	public boolean continuationSchemaPresent() {
		if(!continuationSchemaPresent)continuationSchemaPresent=jdbc.sql("select to_regclass('marketdata.capture_period_continuations') is not null").query(Boolean.class).single();
		return continuationSchemaPresent;
	}
	public boolean replacementSchemaPresent() {
		if(!replacementSchemaPresent)replacementSchemaPresent=jdbc.sql("select to_regclass('marketdata.capture_same_period_replacements') is not null").query(Boolean.class).single();
		return replacementSchemaPresent;
	}
	private static Resources maximum(Resources a,Resources b) {
		return new Resources(Math.max(a.requests(),b.requests()),Math.max(a.receivedBytes(),b.receivedBytes()),Math.max(a.subscriptionBytes(),b.subscriptionBytes()),
			Math.max(a.retainedBytes(),b.retainedBytes()),Math.max(a.rpcHundredths(),b.rpcHundredths()),Math.max(a.reservedHundredths(),b.reservedHundredths()),Math.max(a.seconds(),b.seconds()));
	}
	/** Provider lock is already held. Lock all owning session rows without materializing payloads/IDs in Java. */
	private Instant replacementDatabaseTime(String provider) {
		jdbc.sql("with locked as materialized(select session_id from marketdata.capture_sessions where configuration_json::jsonb->>'provider'=:provider order by session_id for update) select count(*) from locked")
			.param("provider",provider).query(Long.class).single();
		var at=jdbc.sql("select clock_timestamp() as sampled_at").query((r,n)->instant(r,"sampled_at")).single();
		if(jdbc.sql("""
			select exists(select 1 from marketdata.capture_sessions s where s.configuration_json::jsonb->>'provider'=:provider and
			(s.locked_until>:at or (s.state='RUNNING' and (s.ends_at>:at or exists(select 1 from marketdata.capture_segments x where x.session_id=s.session_id)))))
			or exists(select 1 from marketdata.capture_deployments where provider=:provider and state='RUNNING')
			""").param("provider",provider).param("at",time(at)).query(Boolean.class).single())throw new IllegalStateException("REPLACEMENT_ACTIVE_WORKLOAD");
		return at;
	}
	private Resources replacementKnownCarry(String provider,String period) {
		return jdbc.sql("""
			with facts as materialized (
			 select account_period,configuration_json::jsonb->'limits' as limits,requests,received_bytes,subscription_bytes,retained_bytes,rpc_hundredths
			 from marketdata.capture_sessions where configuration_json::jsonb->>'provider'=:provider), checked as (
			 select *,not exists(select 1 from unnest(array['requests','receivedBytes','subscriptionBytes','retainedBytes','rpcHundredths','windowSeconds']) dimension
			 where limits->>dimension is null or (limits->>dimension)::numeric<0 or (limits->>dimension)::numeric<>trunc((limits->>dimension)::numeric)) as known from facts)
			select coalesce(bool_and(known),true) as known,
			coalesce(sum(greatest((limits->>'requests')::numeric,requests)) filter(where account_period=:period),0) as requests,
			coalesce(sum(greatest((limits->>'receivedBytes')::numeric,received_bytes)) filter(where account_period=:period),0) as received,
			coalesce(sum(greatest((limits->>'subscriptionBytes')::numeric,subscription_bytes)) filter(where account_period=:period),0) as subscription,
			coalesce(sum(greatest((limits->>'retainedBytes')::numeric,retained_bytes)),0) as retained,
			coalesce(sum(greatest((limits->>'rpcHundredths')::numeric,rpc_hundredths)) filter(where account_period=:period),0) as rpc,
			coalesce(sum((limits->>'windowSeconds')::numeric) filter(where account_period=:period),0) as seconds,
			(select reserved_hundredths::numeric+known_overage_hundredths from marketdata.capture_periods where account_period=:period) as cu
			from checked
			""").param("provider",provider).param("period",period).query((r,n)->{
			if(!r.getBoolean("known") || r.getBigDecimal("cu")==null)throw new IllegalStateException("UNKNOWN_REPLACEMENT_CARRY");
			return new Resources(r.getBigDecimal("requests").longValueExact(),r.getBigDecimal("received").longValueExact(),r.getBigDecimal("subscription").longValueExact(),
				r.getBigDecimal("retained").longValueExact(),r.getBigDecimal("rpc").longValueExact(),r.getBigDecimal("cu").longValueExact(),r.getBigDecimal("seconds").longValueExact());
		}).single();
	}
	private boolean lineageChild(String key) {
		var replacement=replacementSchemaPresent()?" or exists(select 1 from marketdata.capture_same_period_replacements where predecessor_deployment_key=:key)":"";
		return jdbc.sql("select exists(select 1 from marketdata.capture_period_continuations where predecessor_deployment_key=:key)"+replacement).param("key",key).query(Boolean.class).single();
	}
	private void replacementLineage(Deployment root,Deployment parent) {
		var key=parent.configuration().deploymentKey();var seen=new HashSet<String>();
		for(var depth=0;!key.equals(root.configuration().deploymentKey());depth++) {
			if(depth>=root.configuration().maxSegments() || !seen.add(key))throw new IllegalStateException("REPLACEMENT_LINEAGE_LIMIT");
			var edge=jdbc.sql("select predecessor_deployment_key,allocation_root_key,root_digest,original_global_end,original_allowances,original_max_segments from marketdata.capture_same_period_replacements where successor_deployment_key=:key")
				.param("key",key).query((r,n)->new Object[]{r.getString("predecessor_deployment_key"),r.getString("allocation_root_key"),r.getString("root_digest"),instant(r,"original_global_end"),resources(r,"original_allowances"),r.getInt("original_max_segments")})
				.optional().orElseThrow(()->new IllegalStateException("REPLACEMENT_ROOT_CONFLICT"));
			if(!edge[1].equals(root.configuration().deploymentKey()) || !edge[2].equals(root.digest()) || !edge[3].equals(root.configuration().globalEnd())
				|| !edge[4].equals(root.configuration().allowances()) || !edge[5].equals(root.configuration().maxSegments()))throw new IllegalStateException("REPLACEMENT_ROOT_CONFLICT");
			key=(String)edge[0];
			if(depth+1>=root.configuration().maxSegments())throw new IllegalStateException("REPLACEMENT_LINEAGE_LIMIT");
		}
		if(jdbc.sql("select exists(select 1 from marketdata.capture_same_period_replacements where successor_deployment_key=:key)")
			.param("key",root.configuration().deploymentKey()).query(Boolean.class).single())throw new IllegalStateException("REPLACEMENT_ROOT_CONFLICT");
	}
	private static boolean replacementTemplate(Configuration root,Configuration parent,Configuration next) {
		var a=root.template();var p=parent.template();var b=next.template();var x=p.limits();var y=b.limits();
		var permitted=new io.cryptoresearch.marketdata.api.PumpSwapCapture.Limits(y.windowSeconds(),y.requests(),y.receivedBytes(),y.retainedBytes(),y.subscriptionBytes(),y.rpcHundredths(),
			x.messageBytes(),x.responseBytes(),x.queueItems(),x.queueBytes(),x.timeoutSeconds(),x.retries(),x.reconnects(),x.recoveryPages(),x.recoverySeconds(),x.requestsPerSecond(),x.leaseSeconds());
		return (p.version().equals("capture-rpc-reuse-v6") || p.version().equals("capture-durable-inbox-v7")) && b.version().equals("capture-durable-inbox-v7")
			&& (a.version().equals("capture-rpc-reuse-v6") || a.version().equals("capture-durable-inbox-v7"))
			&& b.provider().equals(a.provider()) && p.provider().equals(a.provider()) && b.pools().equals(a.pools()) && p.pools().equals(a.pools())
			&& b.authority().equals(a.authority()) && p.authority().equals(a.authority()) && !b.startsAt().isBefore(p.startsAt())
			&& next.allowances().equals(root.allowances()) && parent.allowances().equals(root.allowances()) && next.globalEnd().equals(root.globalEnd()) && parent.globalEnd().equals(root.globalEnd())
			&& next.version().equals(root.version()) && next.maxPages()==parent.maxPages() && next.decodeSeconds()==parent.decodeSeconds() && next.receiptBytes()==parent.receiptBytes() && next.pollMillis()==parent.pollMillis()
			&& y.equals(permitted) && y.windowSeconds()<=x.windowSeconds() && y.requests()<=x.requests() && y.receivedBytes()<=x.receivedBytes() && y.subscriptionBytes()<=x.subscriptionBytes()
			&& y.retainedBytes()<=x.retainedBytes() && y.rpcHundredths()<=x.rpcHundredths() && next.segmentSeconds()<=parent.segmentSeconds();
	}
	public io.cryptoresearch.marketdata.api.PumpSwapCaptureSamePeriodReplacement.Receipt enrollReplacement(
			io.cryptoresearch.marketdata.api.PumpSwapCaptureSamePeriodReplacement r,String digest,String successorDigest,Resources first,long monetaryCeiling) {
		if(!replacementSchemaPresent())throw new IllegalStateException("REPLACEMENT_SCHEMA_UNAVAILABLE");
		var next=r.successor();var provider=next.template().provider();var text=encode(r);var rows=new TreeMap<String,Deployment>();
		for(var key:new TreeSet<>(List.of(r.allocationRootKey(),r.predecessorDeploymentKey())))rows.put(key,lockDeployment(provider,key));
		var root=rows.get(r.allocationRootKey());var parent=rows.get(r.predecessorDeploymentKey());
		if(!root.digest().equals(r.allocationRootConfigurationDigest()) || !parent.digest().equals(r.predecessorConfigurationDigest()))throw new IllegalStateException("REPLACEMENT_PREDECESSOR_CONFLICT");
		for(var deployment:rows.values()) {
			var c=deployment.configuration();
			if(!jdbc.sql("select provider=:provider and account_period=:period and owner_authorization=:authority and allowances=cast(:caps as bigint[]) and global_end=:end and max_segments=:maximum as valid from marketdata.capture_deployments where deployment_key=:key")
				.param("provider",c.template().provider()).param("period",c.template().authority().accountPeriod()).param("authority",c.template().authority().ownerAuthorization())
				.param("caps",array(c.allowances())).param("end",time(c.globalEnd())).param("maximum",c.maxSegments()).param("key",c.deploymentKey()).query(Boolean.class).single())
				throw new IllegalStateException("REPLACEMENT_PREDECESSOR_CONFLICT");
		}
		lockPeriod(root.configuration().template().authority().accountPeriod());
		var replay=jdbc.sql("select request_json,request_digest,applied_at,carried,consumed_segments from marketdata.capture_same_period_replacements where successor_deployment_key=:key")
			.param("key",next.deploymentKey()).query((q,n)->new Object[]{q.getString("request_json"),q.getString("request_digest"),instant(q,"applied_at"),resources(q,"carried"),q.getInt("consumed_segments")}).optional();
		if(replay.isPresent()) {
			var p=replay.get();if(!text.equals(p[0]) || !digest.equals(p[1]))throw new IllegalStateException("REPLACEMENT_RECEIPT_CONFLICT");
			return new io.cryptoresearch.marketdata.api.PumpSwapCaptureSamePeriodReplacement.Receipt((String)p[1],(Instant)p[2],next.deploymentKey(),(Resources)p[3],(Integer)p[4]);
		}
		if(!parent.state().equals("STOPPED") || !parent.reason().equals(r.predecessorReason()) || next.deploymentKey().equals(r.predecessorDeploymentKey())
			|| next.deploymentKey().equals(r.allocationRootKey()) || !replacementTemplate(root.configuration(),parent.configuration(),next))throw new IllegalStateException("REPLACEMENT_PREDECESSOR_CONFLICT");
		replacementLineage(root,parent);
		if(lineageChild(r.predecessorDeploymentKey()) || jdbc.sql("select exists(select 1 from marketdata.capture_deployments where deployment_key=:key)").param("key",next.deploymentKey()).query(Boolean.class).single())
			throw new IllegalStateException("REPLACEMENT_FORK_CONFLICT");
		var at=replacementDatabaseTime(provider);var period=next.template().authority().accountPeriod();
		var periodFacts=jdbc.sql("select period_end,ceiling_hundredths,reserved_hundredths::numeric+known_overage_hundredths as held,coalesce((select amended_authority_json from marketdata.capture_authority_amendments where account_period=:period),authority_json) as authority from marketdata.capture_periods where account_period=:period")
			.param("period",period).query((q,n)->new Object[]{instant(q,"period_end"),q.getLong("ceiling_hundredths"),q.getBigDecimal("held").longValueExact(),q.getString("authority")}).single();
		var a=next.template().authority();var authority=encode(List.of(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.exclusiveAllocation(),a.ceilingHundredths(),
			a.ceilingUsd().stripTrailingZeros().toPlainString(),a.usdPerMillionCu().stripTrailingZeros().toPlainString(),a.subscriptionCuPerByte().stripTrailingZeros().toPlainString()));
		if(parent.until().isAfter(at) || !at.isBefore(root.configuration().globalEnd()) || !at.isBefore((Instant)periodFacts[0]) || !periodFacts[0].equals(a.periodEnd())
			|| (Long)periodFacts[1]!=monetaryCeiling || !authority.equals(periodFacts[3]) || java.time.Duration.between(at,root.configuration().globalEnd()).getSeconds()<next.segmentSeconds())throw new IllegalStateException("REPLACEMENT_TIME_OR_AUTHORITY_CONFLICT");
		var count=jdbc.sql("""
			select count(distinct s.session_id) from marketdata.capture_segments s join marketdata.capture_deployments d using(deployment_key)
			where d.provider=:provider and d.account_period=:period and (d.deployment_key=:root or exists(
			select 1 from marketdata.capture_same_period_replacements x where x.successor_deployment_key=d.deployment_key and x.allocation_root_key=:root))
			""").param("provider",provider).param("period",period).param("root",r.allocationRootKey()).query(Long.class).single();
		if(count!=r.consumedSegments() || count>=root.configuration().maxSegments() || next.maxSegments()!=root.configuration().maxSegments()-count)throw new IllegalStateException("REPLACEMENT_COUNT_CONFLICT");
		var required=maximum(maximum(root.reserved(),parent.reserved()),replacementKnownCarry(provider,period));
		if(!r.carried().contains(required) || !next.allowances().contains(r.carried().plus(first)) || (Long)periodFacts[2]>(Long)periodFacts[1]-first.reservedHundredths())
			throw new IllegalStateException("REPLACEMENT_RESOURCE_LIMIT");
		insert(next,successorDigest,"replacement-enrollment",at.minusNanos(1000));
		jdbc.sql("""
			insert into marketdata.capture_same_period_replacements(successor_deployment_key,predecessor_deployment_key,allocation_root_key,provider,account_period,version,
			predecessor_digest,root_digest,successor_digest,predecessor_reason,decision_version,owner_decision,request_json,request_digest,applied_at,original_global_end,original_allowances,original_max_segments,carried,consumed_segments)
			values(:next,:parent,:root,:provider,:period,:version,:parentDigest,:rootDigest,:successorDigest,:reason,:decisionVersion,:decision,:request,:digest,:at,:end,cast(:caps as bigint[]),:maximum,cast(:carry as bigint[]),:consumed)
			""").param("next",next.deploymentKey()).param("parent",r.predecessorDeploymentKey()).param("root",r.allocationRootKey()).param("provider",provider).param("period",period).param("version",r.version())
			.param("parentDigest",r.predecessorConfigurationDigest()).param("rootDigest",r.allocationRootConfigurationDigest()).param("successorDigest",successorDigest).param("reason",r.predecessorReason())
			.param("decisionVersion",r.decisionVersion()).param("decision",r.ownerDecision()).param("request",text).param("digest",digest).param("at",time(at))
			.param("end",time(root.configuration().globalEnd())).param("caps",array(root.configuration().allowances())).param("maximum",root.configuration().maxSegments()).param("carry",array(r.carried())).param("consumed",r.consumedSegments()).update();
		return new io.cryptoresearch.marketdata.api.PumpSwapCaptureSamePeriodReplacement.Receipt(digest,at,next.deploymentKey(),r.carried(),r.consumedSegments());
	}
	public Deployment lockDeployment(String provider,String key) {
		return jdbc.sql("select * from marketdata.capture_deployments where provider=:provider and deployment_key=:key for update")
			.param("provider",provider).param("key",key).query((r,n)->new Deployment(json.readValue(r.getString("configuration_json"),Configuration.class),r.getString("configuration_digest"),resources(r,"reserved"),r.getString("state"),r.getString("reason"),new Fence(r.getString("deployment_key"),r.getString("holder"),r.getLong("generation")),instant(r,"locked_until")))
			.optional().orElseThrow(()->new IllegalStateException("CONTINUATION_PREDECESSOR_CONFLICT"));
	}
	/** Prevent V17's period-scoped constraint from turning ordinary enrollment into an alias bypass. */
	public void requireReceiptedEnrollment(Configuration c) {
		if(!continuationSchemaPresent())return;
		var existing=jdbc.sql("select configuration_json from marketdata.capture_deployments where deployment_key=:key and provider=:provider")
			.param("key",c.deploymentKey()).param("provider",c.template().provider()).query(String.class).optional();
		if(existing.isPresent()) {
			if(!json.readValue(existing.get(),Configuration.class).equals(c))throw new IllegalStateException("CONTINUOUS_CONFIGURATION_CONFLICT");return;
		}
		if(jdbc.sql("select exists(select 1 from marketdata.capture_deployments where provider=:provider)").param("provider",c.template().provider()).query(Boolean.class).single())
			throw new IllegalStateException("CONTINUOUS_CONFIGURATION_CONFLICT");
	}
	public io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation.Receipt enrollContinuation(
			io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation r,String digest,String configDigest,Resources firstSegment,long monetaryCeiling) {
		if(!continuationSchemaPresent())throw new IllegalStateException("CONTINUATION_SCHEMA_UNAVAILABLE");
		var next=r.successor();var provider=next.template().provider();var text=encode(r);
		var parent=lockDeployment(provider,r.predecessorDeploymentKey());lockPeriod(parent.configuration().template().authority().accountPeriod());
		var previous=jdbc.sql("select request_json,request_digest,applied_at,retained_opening_bytes from marketdata.capture_period_continuations where successor_deployment_key=:key")
			.param("key",next.deploymentKey()).query((q,n)->new Object[]{q.getString("request_json"),q.getString("request_digest"),instant(q,"applied_at"),q.getLong("retained_opening_bytes")}).optional();
		if(previous.isPresent()) {
			var p=previous.get();if(!text.equals(p[0]))throw new IllegalStateException("CONTINUATION_RECEIPT_CONFLICT");
			return new io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation.Receipt((String)p[1],(Instant)p[2],next.deploymentKey(),(Long)p[3]);
		}
		var old=parent.configuration();var a=old.template().authority();var b=next.template().authority();
		if(!parent.digest().equals(r.predecessorConfigurationDigest()) || !parent.state().equals("STOPPED") || parent.reason().isBlank()
			|| !r.periodStart().equals(a.periodEnd()) || a.accountPeriod().equals(b.accountPeriod()) || old.deploymentKey().equals(next.deploymentKey())
			|| !old.template().provider().equals(provider) || !old.template().version().equals(next.template().version()) || !old.template().pools().equals(next.template().pools())
			|| a.usdPerMillionCu().compareTo(b.usdPerMillionCu())!=0 || a.subscriptionCuPerByte().compareTo(b.subscriptionCuPerByte())!=0)
			throw new IllegalStateException("CONTINUATION_PREDECESSOR_CONFLICT");
		var periodEnd=jdbc.sql("select period_end from marketdata.capture_periods where account_period=:period").param("period",a.accountPeriod()).query((q,n)->instant(q,"period_end")).single();
		if(!periodEnd.equals(a.periodEnd()))throw new IllegalStateException("CONTINUATION_PREDECESSOR_CONFLICT");
		var childAlreadyExists=lineageChild(old.deploymentKey());
		var at=capture.inactiveProviderDatabaseTime(provider);
		if(at.isBefore(a.periodEnd()) || parent.until().isAfter(at) || r.baseline().observedAt().isAfter(at)
			|| !at.isBefore(b.periodEnd()) || jdbc.sql("select exists(select 1 from marketdata.capture_deployments where provider=:provider and state='RUNNING')")
			.param("provider",provider).query(Boolean.class).single())throw new IllegalStateException("CONTINUATION_ACTIVE_OR_PERIOD_NOT_READY");
		if(childAlreadyExists || jdbc.sql("select exists(select 1 from marketdata.capture_periods where account_period=:period) or exists(select 1 from marketdata.capture_deployments where deployment_key=:next)")
			.param("period",b.accountPeriod()).param("next",next.deploymentKey()).query(Boolean.class).single())throw new IllegalStateException("CONTINUATION_FORK_OR_PERIOD_CONFLICT");
		var held=jdbc.sql("""
			select case when count(*) filter(where configuration_json::jsonb->'limits'->>'retainedBytes' is null or (configuration_json::jsonb->'limits'->>'retainedBytes')::numeric<0)>0 then null
			else coalesce(sum(greatest((configuration_json::jsonb->'limits'->>'retainedBytes')::bigint,retained_bytes)),0) end as held
			from marketdata.capture_sessions where configuration_json::jsonb->>'provider'=:provider
			""").param("provider",provider).query((q,n)->q.getBigDecimal("held")).optional().orElse(null);
		if(held==null)throw new IllegalStateException("UNKNOWN_CONTINUATION_RETENTION");
		var retained=Math.max(parent.reserved().retainedBytes(),held.longValueExact());
		if(next.allowances().retainedBytes()!=old.allowances().retainedBytes() || next.opening().retainedBytes()<retained
			|| !next.allowances().contains(next.opening().plus(firstSegment)))
			throw new IllegalStateException("CONTINUATION_STORAGE_OR_RESOURCE_LIMIT");
		capture.bootstrapContinuationPeriod(next.template(),monetaryCeiling,Math.addExact(b.openingHundredths(),b.otherActivityHundredths()));
		insert(next,configDigest,"continuation-enrollment",r.periodStart().minusNanos(1000));
		jdbc.sql("""
			insert into marketdata.capture_period_continuations(successor_deployment_key,predecessor_deployment_key,provider,account_period,version,
			period_start,period_end,predecessor_digest,request_json,request_digest,applied_at,baseline_source,baseline_at,opening_hundredths,other_activity_hundredths,retained_opening_bytes,retained_ceiling_bytes)
			values(:next,:old,:provider,:period,:version,:start,:end,:parentDigest,:request,:digest,:at,:source,:observed,:opening,:other,:retained,:ceiling)
			""").param("next",next.deploymentKey()).param("old",old.deploymentKey()).param("provider",provider).param("period",b.accountPeriod()).param("version",r.version())
			.param("start",time(r.periodStart())).param("end",time(b.periodEnd())).param("parentDigest",r.predecessorConfigurationDigest()).param("request",text).param("digest",digest)
			.param("at",time(at)).param("source",r.baseline().source()).param("observed",time(r.baseline().observedAt())).param("opening",b.openingHundredths()).param("other",b.otherActivityHundredths())
			.param("retained",retained).param("ceiling",next.allowances().retainedBytes()).update();
		return new io.cryptoresearch.marketdata.api.PumpSwapCapturePeriodContinuation.Receipt(digest,at,next.deploymentKey(),retained);
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
	public void periodBoundary(Configuration deployment,io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration next,Instant at) {
		if(replacementSchemaPresent()) {
			var predecessor=jdbc.sql("select predecessor_deployment_key,applied_at from marketdata.capture_same_period_replacements where successor_deployment_key=:key")
				.param("key",deployment.deploymentKey()).query((r,n)->new Object[]{r.getString("predecessor_deployment_key"),instant(r,"applied_at")}).optional();
			if(predecessor.isPresent()) {
				var prior=latest((String)predecessor.get()[0]);
				for(var pool:next.pools()) {
					var signature=prior.isEmpty()?null:capture.boundary(prior.get().configuration().sessionId(),pool.address()).orElse(null);
					Long slot=signature==null?null:jdbc.sql("select slot from marketdata.capture_dispositions where session_id=:session and pool=:pool and signature=:signature")
						.param("session",prior.get().configuration().sessionId()).param("pool",pool.address()).param("signature",signature).query(Long.class).single();
					for(var kind:List.of("TRANSACTION","STATE"))jdbc.sql("""
						insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status,from_slot,boundary_signature)
						values(:session,:pool,:kind,:reason,:at,:status,:slot,:signature)
						""").param("session",next.sessionId()).param("pool",pool.address()).param("kind",kind).param("reason","SAME_PERIOD_BOUNDARY:"+predecessor.get()[0]+":"+deployment.deploymentKey())
						.param("at",time((Instant)predecessor.get()[1])).param("status",kind.equals("TRANSACTION")?"UNRESOLVED_TRANSACTION_GAP":"UNRESOLVED_STATE_GAP").param("slot",slot).param("signature",signature).update();
				}
				return;
			}
		}
		if(!continuationSchemaPresent())return;
		var parent=jdbc.sql("select predecessor_deployment_key,period_start from marketdata.capture_period_continuations where successor_deployment_key=:key")
			.param("key",deployment.deploymentKey()).query((r,n)->new Object[]{r.getString("predecessor_deployment_key"),instant(r,"period_start")}).optional();
		if(parent.isEmpty())return;
		var prior=latest((String)parent.get()[0]);
		for(var pool:next.pools()) {
			var signature=prior.isEmpty()?null:capture.boundary(prior.get().configuration().sessionId(),pool.address()).orElse(null);
			Long slot=signature==null?null:jdbc.sql("select slot from marketdata.capture_dispositions where session_id=:session and pool=:pool and signature=:signature")
				.param("session",prior.get().configuration().sessionId()).param("pool",pool.address()).param("signature",signature).query(Long.class).single();
			for(var kind:List.of("TRANSACTION","STATE"))jdbc.sql("""
				insert into marketdata.capture_gaps(session_id,pool,kind,reason,opened_at,status,from_slot,boundary_signature)
				values(:session,:pool,:kind,:reason,:at,:status,:slot,:signature)
				""").param("session",next.sessionId()).param("pool",pool.address()).param("kind",kind)
				.param("reason","PERIOD_BOUNDARY:"+parent.get()[0]+":"+deployment.deploymentKey()).param("at",time((Instant)parent.get()[1]))
				.param("status",kind.equals("TRANSACTION")?"UNRESOLVED_TRANSACTION_GAP":"UNRESOLVED_STATE_GAP").param("slot",slot).param("signature",signature).update();
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
