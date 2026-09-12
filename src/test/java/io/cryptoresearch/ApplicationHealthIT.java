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
import org.flywaydb.core.Flyway;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationHealthIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	private final Flyway flyway;
	private final int port;

	@Autowired
	ApplicationHealthIT(Flyway flyway, @Value("${local.server.port}") int port) {
		this.flyway = flyway;
		this.port = port;
	}

	@Test
	void startsWithMigratedPostgreSqlAndReportsHealth() throws Exception {
		var response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(healthUri()).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
		assertThat(flyway.info().applied()).isEmpty();
		assertThat(flyway.info().pending()).isEmpty();
	}

	private URI healthUri() {
		return URI.create("http://localhost:" + port + "/actuator/health");
	}

}
