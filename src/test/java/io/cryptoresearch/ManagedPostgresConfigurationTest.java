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

	private static final String MANAGED_URL = "jdbc:postgresql://managed.invalid:5432/research?sslmode=verify-full";
	private static final List<String> MANAGED_SECRET_KEYS = List.of(
			"CRYPTO_RESEARCH_MANAGED_DB_URL",
			"CRYPTO_RESEARCH_MANAGED_DB_USERNAME",
			"CRYPTO_RESEARCH_MANAGED_DB_PASSWORD");
	private final Path repositoryRoot = Path.of("").toAbsolutePath().normalize();

	@TempDir
	Path temporaryDirectory;

	@Test
	void completeManagedSecretsConfigureOneDatasourceIdentityInheritedByFlyway() throws IOException {
		var secrets = writeSecrets("""
				CRYPTO_RESEARCH_MANAGED_DB_URL=%s
				CRYPTO_RESEARCH_MANAGED_DB_USERNAME=managed-test
				CRYPTO_RESEARCH_MANAGED_DB_PASSWORD=managed-secret
				""".formatted(MANAGED_URL));

		try (var context = runProfileConfigurationOnly("managed", secrets)) {
			var environment = context.getEnvironment();

			assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(MANAGED_URL);
			assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("managed-test");
			assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("managed-secret");
			assertThat(environment.containsProperty("spring.flyway.url")).isFalse();
			assertThat(environment.containsProperty("spring.flyway.user")).isFalse();
			assertThat(environment.containsProperty("spring.flyway.password")).isFalse();
		}
	}

	@Test
	void managedProfileFailsWhenSecretsFileIsMissing() {
		var missingSecrets = temporaryDirectory.resolve("missing-managed-secrets.properties");

		assertThatThrownBy(() -> run(ProbeConfiguration.class, "managed", missingSecrets))
				.hasStackTraceContaining("missing-managed-secrets.properties");
	}

	@Test
	void managedProfileAcceptsOnlyTheSharedDatasourceSettings() throws IOException {
		var secrets = writeSecrets(completeManagedSecrets());

		try (var context = run(ValidatingProbeConfiguration.class, "managed", secrets)) {
			var probe = context.getBean(ManagedSettingsProbe.class);

			assertThat(probe.datasourceUrl()).isEqualTo(MANAGED_URL);
			assertThat(probe.datasourceUsername()).isEqualTo("managed-test");
			assertThat(probe.datasourcePassword()).isEqualTo("managed-secret");
			assertThat(probe.environment().containsProperty("spring.flyway.url")).isFalse();
			assertThat(probe.environment().containsProperty("spring.flyway.user")).isFalse();
			assertThat(probe.environment().containsProperty("spring.flyway.password")).isFalse();
		}
	}

	@Test
	void managedProfileFailsWhenAnySharedDatasourceSettingIsMissing() throws IOException {
		for (var missingSetting : MANAGED_SECRET_KEYS) {
			var incompleteSecrets = writeSecrets(managedSecretsWithout(missingSetting));

			assertThatThrownBy(() -> run(ValidatingProbeConfiguration.class, "managed", incompleteSecrets))
					.hasStackTraceContaining(missingSetting)
					.hasStackTraceContaining("Missing mandatory managed PostgreSQL setting");
		}
	}

	@Test
	void managedProfileRejectsSslModeThatDoesNotRequireTls() throws IOException {
		var insecureSecrets = writeSecrets("""
				CRYPTO_RESEARCH_MANAGED_DB_URL=jdbc:postgresql://db.invalid:5432/research?sslmode=disable
				CRYPTO_RESEARCH_MANAGED_DB_USERNAME=managed-test
				CRYPTO_RESEARCH_MANAGED_DB_PASSWORD=managed-secret
				""");

		assertThatThrownBy(() -> run(ProbeConfiguration.class, "managed", insecureSecrets))
				.hasStackTraceContaining("CRYPTO_RESEARCH_MANAGED_DB_URL");
	}

	@Test
	void managedProfileRejectsDuplicateSslModeParametersInEitherOrder() throws IOException {
		var conflictingUrls = List.of(
				"jdbc:postgresql://db.invalid:5432/research?sslmode=require&sslmode=disable",
				"jdbc:postgresql://db.invalid:5432/research?sslmode=disable&sslmode=require");

		for (var conflictingUrl : conflictingUrls) {
			var conflictingSecrets = writeSecrets("""
					CRYPTO_RESEARCH_MANAGED_DB_URL=%s
					CRYPTO_RESEARCH_MANAGED_DB_USERNAME=managed-test
					CRYPTO_RESEARCH_MANAGED_DB_PASSWORD=managed-secret
					""".formatted(conflictingUrl));

			assertThatThrownBy(() -> {
				try (var ignored = run(ProbeConfiguration.class, "managed", conflictingSecrets)) {
					// A duplicate sslmode must fail before a context can be used.
				}
			})
					.hasStackTraceContaining("CRYPTO_RESEARCH_MANAGED_DB_URL");
		}
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
		var configuredLines = example.lines()
				.map(String::trim)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.toList();

		assertThat(MANAGED_SECRET_KEYS).allSatisfy(key -> assertThat(example).contains(key + "="));
		assertThat(configuredLines).hasSize(3);
		assertThat(example)
				.contains("<host>", "<database-username>", "<database-password>")
				.doesNotContain("CRYPTO_RESEARCH_MANAGED_FLYWAY_", "<migration-username>", "<migration-password>")
				.doesNotContain("localhost", "127.0.0.1");
		assertThat(gitignore).contains("/config/application-managed-secrets.properties");
	}

	private String completeManagedSecrets() {
		return """
				CRYPTO_RESEARCH_MANAGED_DB_URL=%s
				CRYPTO_RESEARCH_MANAGED_DB_USERNAME=managed-test
				CRYPTO_RESEARCH_MANAGED_DB_PASSWORD=managed-secret
				""".formatted(MANAGED_URL);
	}

	private String managedSecretsWithout(String missingSetting) {
		return MANAGED_SECRET_KEYS.stream()
				.filter(setting -> !setting.equals(missingSetting))
				.map(setting -> setting + "=" + switch (setting) {
					case "CRYPTO_RESEARCH_MANAGED_DB_URL" -> MANAGED_URL;
					case "CRYPTO_RESEARCH_MANAGED_DB_USERNAME" -> "managed-test";
					case "CRYPTO_RESEARCH_MANAGED_DB_PASSWORD" -> "managed-secret";
					default -> throw new IllegalArgumentException("Unexpected managed setting: " + setting);
				})
				.collect(java.util.stream.Collectors.joining(System.lineSeparator()));
	}

	private Path writeSecrets(String content) throws IOException {
		var secrets = temporaryDirectory.resolve("application-managed-secrets.properties");
		Files.writeString(secrets, content);
		return secrets;
	}

	private ConfigurableApplicationContext run(Class<?> configuration, String profile, Path secrets) {
		var application = new SpringApplication(configuration, ManagedPostgresConfiguration.class);
		return run(application, profile, secrets);
	}

	private ConfigurableApplicationContext runProfileConfigurationOnly(String profile, Path secrets) {
		return run(new SpringApplication(ProbeConfiguration.class), profile, secrets);
	}

	private ConfigurableApplicationContext run(SpringApplication application, String profile, Path secrets) {
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
				@Value("${spring.datasource.password}") String datasourcePassword) {
			return new ManagedSettingsProbe(environment, datasourceUrl, datasourceUsername, datasourcePassword);
		}
	}

	record ManagedSettingsProbe(Environment environment, String datasourceUrl, String datasourceUsername,
			String datasourcePassword) {
	}
}
