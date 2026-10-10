package io.cryptoresearch.marketdata.application;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import io.cryptoresearch.marketdata.api.PumpSwapCaptureMonitor.*;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore;

@Service
public class ReadCaptureMonitorUseCase {
    private final JdbcCaptureStore store;
    private final Clock clock;
    public ReadCaptureMonitorUseCase(JdbcCaptureStore store,Clock clock) {this.store=store;this.clock=clock;}
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,timeout=10)
    public Optional<Snapshot> read(UUID id) {
        Objects.requireNonNull(id,"sessionId");var observed=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        try {
            store.monitorTimeout();var header=store.monitorHeader(id);if(header.isEmpty())return Optional.empty();
            var dispositions=store.monitorDispositions(id);var evidence=store.monitorEvidenceCounts(id);
            var summary=store.summarySnapshot(id);var durable=(tools.jackson.databind.JsonNode)summary.get("durable");
            var positionsNode=(tools.jackson.databind.JsonNode)summary.get("committedPositions");
            var positions=new ArrayList<PoolPosition>();
            for(var pool:positionsNode.propertyNames()) {
                var p=positionsNode.get(pool);positions.add(new PoolPosition(pool,p.isObject()?Optional.of(new Position(p.path("signature").asText(),p.path("slot").longValue())):Optional.empty()));
            }
            var counters=new Counters(durable.path("requests").longValue(),durable.path("received").longValue(),durable.path("wss").longValue(),durable.path("retained").longValue(),durable.path("rpcHundredths").longValue(),durable.path("reservedHundredths").longValue(),evidence[0],evidence[1],evidence[2],evidence[3],evidence[4]);
            return Optional.of(project(new JdbcCaptureStore.MonitorData(header.orElseThrow(),counters,dispositions,positions,store.monitorGapCounts(id),store.monitorDetails(id)),observed));
        } catch(org.springframework.dao.DataAccessException failure) {throw new IllegalStateException("CAPTURE_MONITOR_READ_FAILED");}
    }
    static Snapshot project(JdbcCaptureStore.MonitorData d,Instant observed) {
        var h=d.header();
        if(d.dispositions().size()>32||d.positions().size()>3||d.gapCounts().size()>64||d.details().size()>101)throw limit();
        var details=d.details().stream().sorted(Comparator.comparingLong(io.cryptoresearch.marketdata.api.PumpSwapCapture.Gap::id)).toList();
        var groups=d.gapCounts().stream().sorted(Comparator.comparing(GapCount::pool).thenComparing(GapCount::kind).thenComparing(GapCount::status)).toList();
        long total=0;for(var group:groups)total=Math.addExact(total,group.count());
        observed=observed.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var snapshot=new Snapshot(h.sessionId(),h.configurationDigest(),h.state(),h.reason(),observed,h.startedAt(),h.endsAt(),h.lockedUntil(),observed.isBefore(h.lockedUntil()),!observed.isBefore(h.startedAt())&&observed.isBefore(h.endsAt()),
            d.counters(),d.dispositions().stream().sorted(Comparator.comparing(DispositionCount::status)).toList(),d.positions().stream().sorted(Comparator.comparing(PoolPosition::pool)).toList(),groups,total,
            groups.stream().anyMatch(g->!g.kind().equals("TRANSACTION")&&g.status().equals("RECOVERED_TRANSACTIONS")),details.subList(0,Math.min(100,details.size())),details.size()>100,"UNVERIFIED","UNVERIFIED","UNVERIFIED","UNKNOWN","UNKNOWN");
        try {
            tools.jackson.databind.json.JsonMapper.builder().build().writeValue(new java.io.OutputStream() {
                int bytes;
                @Override public void write(int value) {if(++bytes>131072)throw limit();}
                @Override public void write(byte[] b,int off,int len) {if(len>131072-bytes)throw limit();bytes+=len;}
            },snapshot);
        } catch(tools.jackson.core.JacksonException failure) {throw limit();}
        return snapshot;
    }
    private static IllegalArgumentException limit() {return new IllegalArgumentException("CAPTURE_MONITOR_RESULT_LIMIT");}
}
