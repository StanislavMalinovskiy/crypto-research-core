package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static io.cryptoresearch.marketdata.application.PumpSwapRawOperationDecoderTest.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class PumpSwapRawOperationDecoderIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi api;
    @Autowired JdbcClient jdbc;
    @Test void publicWiringReturnsPureEvidenceWithoutMutatingAnyOwnedTable() {
        var before=tableCounts();var input=source(fixture("1","buy",true,1),"integration");
        var batch=new PumpSwapOperations.RecordedBatch(List.of(input),List.of("GAPS_UNKNOWN"));
        var first=api.decodePumpSwapOperations(batch);assertThat(first.operations()).hasSize(1);
        assertThat(api.decodePumpSwapOperations(batch)).isEqualTo(first);assertThat(tableCounts()).isEqualTo(before);
        assertThat(jdbc.sql("select version() ").query(String.class).single()).contains("PostgreSQL 18.6");
        assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single()).isEqualTo(15);
    }
    private java.util.Map<String,Long> tableCounts(){
        var result=new java.util.TreeMap<String,Long>();
        var tables=jdbc.sql("select schemaname || '.' || tablename from pg_tables where schemaname in ('marketdata','signal','evaluation') order by schemaname,tablename").query(String.class).list();
        for(String table:tables)result.put(table,jdbc.sql("select count(*) from "+table).query(Long.class).single());return result;
    }
}
