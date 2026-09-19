package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class LocalDevelopmentDatabaseContractTest {

	private final Path repositoryRoot = Path.of("").toAbsolutePath().normalize();

	@Test
	void composeDefinesOneLoopbackOnlyPersistentPostgreSqlService() throws IOException {
		var compose = readRequired("compose.yaml", "portable local PostgreSQL Compose contract");

		assertThat(compose)
				.contains(
						"services:\n  postgres:",
						"image: postgres:18.6-alpine",
						"POSTGRES_DB: ${POSTGRES_DB:-crypto_research}",
						"POSTGRES_USER: ${POSTGRES_USER:-crypto_research}",
						"POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-crypto_research}",
						"127.0.0.1:${POSTGRES_HOST_PORT:-5432}:5432",
						"pg_isready",
						"postgres_data:/var/lib/postgresql",
						"volumes:\n  postgres_data:")
				.doesNotContain(
						"/var/lib/postgresql/data",
						"0.0.0.0:",
						"initdb.d",
						"./:/var/lib/postgresql");
		assertThat(serviceNames(compose)).containsExactly("postgres");
	}

	@Test
	void localEnvironmentDefaultsAreSafeTrackedAndApplicationCompatible() throws IOException {
		var example = readRequired(".env.example", "tracked safe local environment example");
		var ignoreRules = Files.readAllLines(repositoryRoot.resolve(".gitignore")).stream()
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.collect(Collectors.toSet());
		var application = readRequired("src/main/resources/application.yaml", "application database defaults");

		assertThat(example.lines().toList()).contains(
				"POSTGRES_HOST_PORT=5432",
				"POSTGRES_DB=crypto_research",
				"POSTGRES_USER=crypto_research",
				"POSTGRES_PASSWORD=crypto_research");
		assertThat(ignoreRules)
				.contains(".env", ".env.local", ".env.*.local")
				.doesNotContain(".env.example", ".env*");
		assertThat(application).contains(
				"jdbc:postgresql://localhost:5432/crypto_research",
				"CRYPTO_RESEARCH_DB_USERNAME:crypto_research",
				"CRYPTO_RESEARCH_DB_PASSWORD:crypto_research");
	}

	@Test
	void documentationDefinesRoutineLifecycleDestructiveResetAndOwnershipBoundaries() throws IOException {
		var readme = readRequired("README.md", "local database commands");
		var operations = readRequired("docs/OPERATIONS.md", "local database operating boundary");
		var techStack = readRequired("docs/TECH_STACK.md", "approved local development tooling");
		var deliveryPlan = readRequired("docs/DELIVERY_PLAN.md", "current local database work");

		assertThat(readme).contains(
				"docker compose up -d --wait postgres",
				"docker compose ps postgres",
				".\\mvnw.cmd spring-boot:run",
				"docker compose down",
				"docker compose down --volumes",
				"POSTGRES_HOST_PORT",
				"CRYPTO_RESEARCH_DB_URL",
				"jdbc:postgresql://localhost:");
		assertThat(operations).contains(
				"development-only Docker Compose",
				"Each workstation",
				"does not synchronize",
				"shared managed PostgreSQL");
		assertThat(techStack).contains(
				"Docker Compose",
				"local development only",
				"Testcontainers remains the isolated PostgreSQL mechanism");
		assertThat(deliveryPlan).contains(
				"локальную PostgreSQL",
				"shared managed PostgreSQL",
				"build-first-signal-evaluation-skeleton");
	}

	private String readRequired(String relativePath, String contract) throws IOException {
		var path = repositoryRoot.resolve(relativePath);
		assertThat(path)
				.as("repository must provide %s at %s", contract, relativePath)
				.isRegularFile();
		return Files.readString(path).replace("\r\n", "\n");
	}

	private Set<String> serviceNames(String compose) {
		var lines = compose.lines().toList();
		var servicesLine = lines.indexOf("services:");
		assertThat(servicesLine).as("compose services section").isNotNegative();

		return lines.subList(servicesLine + 1, lines.size()).stream()
				.takeWhile(line -> line.isBlank() || line.startsWith(" "))
				.filter(line -> line.matches("  [a-zA-Z0-9_-]+:"))
				.map(line -> line.strip().replace(":", ""))
				.collect(Collectors.toSet());
	}
}
