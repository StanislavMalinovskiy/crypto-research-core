package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"management.endpoint.health.show-components=always",
				"logging.level.org.springframework.boot.jdbc.health.DataSourceHealthIndicator=OFF",
				"spring.datasource.hikari.connection-timeout=500",
				"spring.datasource.hikari.validation-timeout=500"
		})
class ApplicationReadinessIT {

	private static final Duration PROBE_DEADLINE = Duration.ofSeconds(15);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final int port;

	ApplicationReadinessIT(@Value("${local.server.port}") int port) {
		this.port = port;
	}

	@Test
	void databaseLossMakesReadinessDownWithoutChangingLiveness() throws Exception {
		assertUp(get("/actuator/health/readiness"));
		assertUp(get("/actuator/health/liveness"));

		postgres.getDockerClient().stopContainerCmd(postgres.getContainerId()).exec();
		try {
			var readiness = awaitStatus("/actuator/health/readiness", 503);
			assertThat(readiness.body()).contains("\"status\":\"DOWN\"", "\"db\"");
			assertUp(get("/actuator/health/liveness"));
		} finally {
			postgres.getDockerClient().startContainerCmd(postgres.getContainerId()).exec();
		}
	}

	private HttpResponse<String> awaitStatus(String path, int expectedStatus) throws Exception {
		var deadline = Instant.now().plus(PROBE_DEADLINE);
		HttpResponse<String> response = null;
		IOException lastFailure = null;

		do {
			try {
				response = get(path);
				if (response.statusCode() == expectedStatus) {
					return response;
				}
			} catch (IOException failure) {
				lastFailure = failure;
			}
			Thread.sleep(POLL_INTERVAL);
		} while (Instant.now().isBefore(deadline));

		var body = response == null ? "no response" : response.body();
		throw new AssertionError("Probe did not reach HTTP " + expectedStatus + ": " + body, lastFailure);
	}

	private HttpResponse<String> get(String path) throws Exception {
		return httpClient.send(
				HttpRequest.newBuilder(uri(path)).timeout(REQUEST_TIMEOUT).GET().build(),
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
