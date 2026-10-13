package io.cryptoresearch.marketdata.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import io.cryptoresearch.marketdata.api.PumpSwapRetainedWindow.*;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.Envelope;

/** New owning projections only: no capture locks, writes, cursors or provider access. */
@Repository
public class JdbcPumpSwapRetainedWindowStore {
    public record Header(Instant observedAt,String digest,long bytes) { }
    public record Meta(String signature,String pool,String status,String digest,long slot,long notificationBytes,long responseBytes,long rawBytes,boolean fits) { }
    public record Binding(String signature,String pool,long slot,String status,String digest,String notification,String response,Instant received,Instant admitted,long duplicates,long conflicts) { }
    public record Raw(String signature,long slot,String digest,String payload,Instant source,Instant received,Instant admitted,Instant ingested,String parser,String blockHash) { }
    public record ProofMeta(String kind,String digest,long bytes) { }
    private final JdbcClient jdbc;
    public JdbcPumpSwapRetainedWindowStore(JdbcClient jdbc){this.jdbc=jdbc;}
    private static Instant instant(ResultSet r,String name)throws SQLException{var v=r.getObject(name,java.time.OffsetDateTime.class);return v==null?null:v.toInstant();}
    public Header header(UUID session){return jdbc.sql("select clock_timestamp() as observed,configuration_digest,octet_length(configuration_json) as bytes from marketdata.capture_sessions where session_id=:id")
        .param("id",session).query((r,n)->new Header(instant(r,"observed"),r.getString("configuration_digest"),r.getLong("bytes"))).optional().orElseThrow(()->new IllegalStateException("RETAINED_SESSION_MISSING"));}
    public String configuration(UUID session){return jdbc.sql("select configuration_json from marketdata.capture_sessions where session_id=:id").param("id",session).query(String.class).single();}
    public List<String> signatures(Request q){return jdbc.sql("""
        with selected as (select distinct signature collate "C" as signature from marketdata.capture_dispositions
        where session_id=:id and received_at>=:f and received_at<:t order by signature collate "C" limit 4097)
        select case when octet_length(signature)<=128 then signature end from selected order by signature collate "C"
        """).param("id",q.session()).param("f",q.receivedFromInclusive().atOffset(java.time.ZoneOffset.UTC)).param("t",q.receivedToExclusive().atOffset(java.time.ZoneOffset.UTC)).query(String.class).list();}
    public List<Meta> metadata(UUID id,String provider,List<String> keys){if(keys.isEmpty())return List.of();return jdbc.sql("""
        with selected as (select d.signature,d.pool,d.status,d.raw_digest,d.slot,octet_length(d.notification) as nb,octet_length(d.response) as rb,
        case when d.raw_digest is not null then coalesce(octet_length(r.payload),-1) else 0 end as rawbytes
        from marketdata.capture_dispositions d left join marketdata.raw_transactions r
        on r.chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp' and r.provider=:provider and r.transaction_value=d.signature
        where d.session_id=:id and d.signature in (:keys) order by d.signature collate "C",d.pool collate "C" limit 12289),
        bounded as (select *,octet_length(pool)<=128 and octet_length(status)<=131072
        and coalesce(octet_length(raw_digest),0)<=71 and sum(octet_length(signature)+octet_length(pool)+octet_length(status)+coalesce(octet_length(raw_digest),0)) over()<=64000000 as fits from selected)
        select signature,case when fits then pool end as pool,case when fits then status end as status,
        case when fits then raw_digest end as raw_digest,slot,nb,rb,rawbytes,fits from bounded order by signature collate "C",pool collate "C"
        """).param("id",id).param("provider",provider).param("keys",keys).query((r,n)->new Meta(r.getString("signature"),r.getString("pool"),r.getString("status"),r.getString("raw_digest"),r.getLong("slot"),r.getLong("nb"),r.getLong("rb"),r.getLong("rawbytes"),r.getBoolean("fits"))).list();}
    public List<Binding> bindings(UUID id,List<String> keys){if(keys.isEmpty())return List.of();return jdbc.sql("""
        select signature,pool,slot,status,raw_digest,notification,response,received_at,admitted_at,duplicates,conflicts
        from marketdata.capture_dispositions where session_id=:id and signature in (:keys)
        order by signature collate "C",pool collate "C" limit 385
        """).param("id",id).param("keys",keys).query((r,n)->new Binding(r.getString("signature"),r.getString("pool"),r.getLong("slot"),r.getString("status"),r.getString("raw_digest"),r.getString("notification"),r.getString("response"),instant(r,"received_at"),instant(r,"admitted_at"),r.getLong("duplicates"),r.getLong("conflicts"))).list();}
    public List<Raw> raw(String provider,List<String> keys){if(keys.isEmpty())return List.of();return jdbc.sql("""
        select transaction_value,observed_block_position,payload_hash,payload,source_event_time,received_at,admitted_at,ingested_at,parser_version,observed_block_hash
        from marketdata.raw_transactions where chain_id='solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp' and provider=:p and transaction_value in (:keys)
        order by transaction_value collate "C" limit 129
        """).param("p",provider).param("keys",keys).query((r,n)->new Raw(r.getString("transaction_value"),r.getLong("observed_block_position"),r.getString("payload_hash"),r.getString("payload"),instant(r,"source_event_time"),instant(r,"received_at"),instant(r,"admitted_at"),instant(r,"ingested_at"),r.getString("parser_version"),r.getString("observed_block_hash"))).list();}
    public List<ProofMeta> proofMetadata(UUID id,Set<String> digests){if(digests.isEmpty())return List.of();return jdbc.sql("""
        select kind,digest,octet_length(payload) as bytes from marketdata.capture_envelopes where session_id=:id
        and ((kind='HTTP_getTransactionsForAddress' and digest in (:keys))
        or (kind='CAPTURE_HISTORY_CONTEXT' and payload::jsonb->>'pageDigest' in (:keys)))
        order by kind collate "C",digest collate "C" limit 769
        """).param("id",id).param("keys",digests).query((r,n)->new ProofMeta(r.getString("kind"),r.getString("digest"),r.getLong("bytes"))).list();}
    public List<Envelope> proofs(UUID id,List<ProofMeta> keys){if(keys.isEmpty())return List.of();var clauses=new ArrayList<String>();var q=jdbc.sql("select kind,digest,payload,received_at from marketdata.capture_envelopes where session_id=:id and ("+java.util.stream.IntStream.range(0,keys.size()).mapToObj(i->"(kind=:k"+i+" and digest=:d"+i+")").collect(java.util.stream.Collectors.joining(" or "))+") order by kind collate \"C\",digest collate \"C\" limit 769").param("id",id);
        for(int i=0;i<keys.size();i++)q=q.param("k"+i,keys.get(i).kind()).param("d"+i,keys.get(i).digest());
        return q.query((r,n)->new Envelope(r.getString("kind"),r.getString("digest"),r.getString("payload"),instant(r,"received_at"))).list();}
    public List<Gap> gaps(UUID id){return jdbc.sql("""
        with selected as (select gap_id,pool,kind,reason,from_slot,to_slot,opened_at,closed_at,boundary_signature,status
        from marketdata.capture_gaps where session_id=:id order by gap_id limit 1001),
        bounded as (select *,greatest(octet_length(pool),octet_length(kind),octet_length(reason),coalesce(octet_length(boundary_signature),0),octet_length(status))<=8000000
        and sum(octet_length(pool)+octet_length(kind)+octet_length(reason)+coalesce(octet_length(boundary_signature),0)+octet_length(status)) over()<=64000000 as fits from selected)
        select gap_id,case when fits then pool end as pool,case when fits then kind end as kind,case when fits then reason end as reason,
        from_slot,to_slot,opened_at,closed_at,case when fits then boundary_signature end as boundary_signature,case when fits then status end as status from bounded order by gap_id
        """).param("id",id).query((r,n)->new Gap(r.getLong("gap_id"),r.getString("pool"),r.getString("kind"),r.getString("reason"),Optional.ofNullable(r.getObject("from_slot",Long.class)),Optional.ofNullable(r.getObject("to_slot",Long.class)),instant(r,"opened_at"),Optional.ofNullable(instant(r,"closed_at")),Optional.ofNullable(r.getString("boundary_signature")),r.getString("status"))).list();}
}
