package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.flywaydb.core.Flyway;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.endpoint.health.show-components=always")
class ApplicationHealthIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final Flyway flyway;
	private final JdbcClient jdbcClient;
	private final int port;

	@Autowired
	ApplicationHealthIT(Flyway flyway, JdbcClient jdbcClient, @Value("${local.server.port}") int port) {
		this.flyway = flyway;
		this.jdbcClient = jdbcClient;
		this.port = port;
	}

	@Test
	void startsWithMigratedPostgreSqlAndReportsHealth() throws Exception {
		var aggregate = get("/actuator/health");
		var liveness = get("/actuator/health/liveness");
		var readiness = get("/actuator/health/readiness");

		assertUp(aggregate);
		assertUp(liveness);
		assertUp(readiness);
		assertThat(liveness.body()).contains("\"livenessState\"").doesNotContain("\"db\"");
		assertThat(readiness.body()).contains("\"readinessState\"", "\"db\"");
		assertThat(jdbcClient.sql("SHOW server_version").query(String.class).single()).isEqualTo("18.6");
		assertThat(flyway.info().applied()).isEmpty();
		assertThat(flyway.info().pending()).isEmpty();
	}

	private HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(uri(path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private void assertUp(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
