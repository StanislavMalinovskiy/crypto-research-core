package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

class ManagedPostgresConfigurationTest {

	private static final String RUNTIME_URL = "jdbc:postgresql://db.invalid:5432/research?sslmode=verify-full";
	private static final String FLYWAY_URL = "jdbc:postgresql://migrations.invalid:5432/research?sslmode=verify-full";
	private static final List<String> MANAGED_SECRET_KEYS = List.of(
			"CRYPTO_RESEARCH_MANAGED_DB_URL",
			"CRYPTO_RESEARCH_MANAGED_DB_USERNAME",
			"CRYPTO_RESEARCH_MANAGED_DB_PASSWORD",
			"CRYPTO_RESEARCH_MANAGED_FLYWAY_URL",
			"CRYPTO_RESEARCH_MANAGED_FLYWAY_USERNAME",
			"CRYPTO_RESEARCH_MANAGED_FLYWAY_PASSWORD");
	private final Path repositoryRoot = Path.of("").toAbsolutePath().normalize();

	@TempDir
	Path temporaryDirectory;

	@Test
	void completeManagedSecretsBindRuntimeAndFlywayIdentities() throws IOException {
		var secrets = writeSecrets("""
				CRYPTO_RESEARCH_MANAGED_DB_URL=%s
				CRYPTO_RESEARCH_MANAGED_DB_USERNAME=runtime-test
				CRYPTO_RESEARCH_MANAGED_DB_PASSWORD=runtime-secret
				CRYPTO_RESEARCH_MANAGED_FLYWAY_URL=%s
				CRYPTO_RESEARCH_MANAGED_FLYWAY_USERNAME=migrator-test
				CRYPTO_RESEARCH_MANAGED_FLYWAY_PASSWORD=migrator-secret
				""".formatted(RUNTIME_URL, FLYWAY_URL));

		try (var context = run(ProbeConfiguration.class, "managed", secrets)) {
			var environment = context.getEnvironment();

			assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(RUNTIME_URL);
			assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("runtime-test");
			assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("runtime-secret");
			assertThat(environment.getProperty("spring.flyway.url")).isEqualTo(FLYWAY_URL);
			assertThat(environment.getProperty("spring.flyway.user")).isEqualTo("migrator-test");
			assertThat(environment.getProperty("spring.flyway.password")).isEqualTo("migrator-secret");
		}
	}

	@Test
	void managedProfileFailsWhenSecretsFileIsMissing() {
		var missingSecrets = temporaryDirectory.resolve("missing-managed-secrets.properties");

		assertThatThrownBy(() -> run(ProbeConfiguration.class, "managed", missingSecrets))
				.hasStackTraceContaining("missing-managed-secrets.properties");
	}

	@Test
	void managedProfileFailsWhenMandatorySettingIsMissing() throws IOException {
		var incompleteSecrets = writeSecrets("""
				CRYPTO_RESEARCH_MANAGED_DB_URL=%s
				CRYPTO_RESEARCH_MANAGED_DB_USERNAME=runtime-test
				CRYPTO_RESEARCH_MANAGED_DB_PASSWORD=runtime-secret
				CRYPTO_RESEARCH_MANAGED_FLYWAY_URL=%s
				CRYPTO_RESEARCH_MANAGED_FLYWAY_USERNAME=migrator-test
				""".formatted(RUNTIME_URL, FLYWAY_URL));

		assertThatThrownBy(() -> run(ValidatingProbeConfiguration.class, "managed", incompleteSecrets))
				.hasStackTraceContaining("CRYPTO_RESEARCH_MANAGED_FLYWAY_PASSWORD");
	}

	@Test
	void managedProfileRejectsSslModeThatDoesNotRequireTls() throws IOException {
		var insecureSecrets = writeSecrets("""
				CRYPTO_RESEARCH_MANAGED_DB_URL=jdbc:postgresql://db.invalid:5432/research?sslmode=disable
				CRYPTO_RESEARCH_MANAGED_DB_USERNAME=runtime-test
				CRYPTO_RESEARCH_MANAGED_DB_PASSWORD=runtime-secret
				CRYPTO_RESEARCH_MANAGED_FLYWAY_URL=%s
				CRYPTO_RESEARCH_MANAGED_FLYWAY_USERNAME=migrator-test
				CRYPTO_RESEARCH_MANAGED_FLYWAY_PASSWORD=migrator-secret
				""".formatted(FLYWAY_URL));

		assertThatThrownBy(() -> run(ProbeConfiguration.class, "managed", insecureSecrets))
				.hasStackTraceContaining("CRYPTO_RESEARCH_MANAGED_DB_URL");
	}

	@Test
	void defaultProfileKeepsLocalComposeDefaultsWithoutManagedSecrets() {
		try (var context = run(ProbeConfiguration.class, null, null)) {
			var environment = context.getEnvironment();

			assertThat(environment.getProperty("spring.datasource.url"))
					.isEqualTo("jdbc:postgresql://localhost:5432/crypto_research");
			assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("crypto_research");
			assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("crypto_research");
			assertThat(environment.getProperty("spring.flyway.user")).isNull();
		}
	}

	@Test
	void repositoryProvidesPlaceholderOnlyExampleAndIgnoreRule() throws IOException {
		var example = Files.readString(repositoryRoot.resolve("config/application-managed-secrets.example.properties"));
		var gitignore = Files.readString(repositoryRoot.resolve(".gitignore"));

		assertThat(MANAGED_SECRET_KEYS).allSatisfy(key -> assertThat(example).contains(key + "="));
		assertThat(example)
				.contains("<host>", "<runtime-username>", "<runtime-password>", "<migration-username>",
						"<migration-password>")
				.doesNotContain("localhost", "127.0.0.1");
		assertThat(gitignore).contains("/config/application-managed-secrets.properties");
	}

	private Path writeSecrets(String content) throws IOException {
		var secrets = temporaryDirectory.resolve("application-managed-secrets.properties");
		Files.writeString(secrets, content);
		return secrets;
	}

	private ConfigurableApplicationContext run(Class<?> configuration, String profile, Path secrets) {
		var application = new SpringApplication(configuration, ManagedPostgresConfiguration.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setRegisterShutdownHook(false);

		var arguments = new java.util.ArrayList<String>();
		arguments.add("--spring.main.banner-mode=off");
		arguments.add("--logging.level.root=OFF");
		if (profile != null) {
			arguments.add("--spring.profiles.active=" + profile);
		}
		if (secrets != null) {
			arguments.add("--CRYPTO_RESEARCH_MANAGED_SECRETS_LOCATION=" + secrets.toUri());
		}
		return application.run(arguments.toArray(String[]::new));
	}

	@Configuration(proxyBeanMethods = false)
	static class ProbeConfiguration {
	}

	@Configuration(proxyBeanMethods = false)
	static class ValidatingProbeConfiguration {

		@Bean
		ManagedSettingsProbe managedSettingsProbe(Environment environment,
				@Value("${spring.datasource.url}") String datasourceUrl,
				@Value("${spring.datasource.username}") String datasourceUsername,
				@Value("${spring.datasource.password}") String datasourcePassword,
				@Value("${spring.flyway.url}") String flywayUrl,
				@Value("${spring.flyway.user}") String flywayUsername,
				@Value("${spring.flyway.password}") String flywayPassword) {
			return new ManagedSettingsProbe(environment, datasourceUrl, datasourceUsername, datasourcePassword,
					flywayUrl, flywayUsername, flywayPassword);
		}
	}

	record ManagedSettingsProbe(Environment environment, String datasourceUrl, String datasourceUsername,
			String datasourcePassword, String flywayUrl, String flywayUsername, String flywayPassword) {
	}
}
