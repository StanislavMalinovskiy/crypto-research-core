package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.cryptoresearch.marketdata.api.MarketDataApi;
import io.cryptoresearch.marketdata.api.PumpSwapPoolLegs;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class PumpSwapPoolLegDecoderIT {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MarketDataApi api;
    @Autowired JdbcClient jdbc;
    @Test void optInPublicApiPreservesOldDecodeAndEveryOwnedTable() {
        var before=counts();var batch=PumpSwapPoolLegDecoderTest.batch(PumpSwapPoolLegDecoderTest.source("term"));
        var old=api.decodePumpSwapOperations(batch);
        var request=new PumpSwapPoolLegs.Request(batch,List.of(PumpSwapPoolLegDecoderTest.GTA));
        var result=api.decodePumpSwapPoolLegs(request);
        assertThat(result.poolLegs()).hasSize(1);assertThat(result.v4()).isEqualTo(old);
        assertThat(api.decodePumpSwapPoolLegs(request)).isEqualTo(result);
        assertThat(api.decodePumpSwapOperations(batch)).isEqualTo(old);assertThat(counts()).isEqualTo(before);
        assertThat(jdbc.sql("select version()").query(String.class).single()).contains("PostgreSQL 18.6");
        assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single()).isEqualTo(15);
    }
    private Map<String,Long> counts() {
        var result=new TreeMap<String,Long>();
        for(var table:jdbc.sql("select schemaname || '.' || tablename from pg_tables where schemaname in ('marketdata','signal','evaluation') order by schemaname,tablename").query(String.class).list())
            result.put(table,jdbc.sql("select count(*) from "+table).query(Long.class).single());
        return result;
    }
}
