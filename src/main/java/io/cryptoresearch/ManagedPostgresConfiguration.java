package io.cryptoresearch;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("managed")
class ManagedPostgresConfiguration {

	private static final String POSTGRESQL_JDBC_PREFIX = "jdbc:postgresql://";
	private static final Pattern REQUIRED_TLS_MODE = Pattern.compile(
			"(?:[?&])sslmode=(?:require|verify-ca|verify-full)(?:&|$)", Pattern.CASE_INSENSITIVE);
	private static final List<String> MANDATORY_SETTINGS = List.of(
			"CRYPTO_RESEARCH_MANAGED_DB_URL",
			"CRYPTO_RESEARCH_MANAGED_DB_USERNAME",
			"CRYPTO_RESEARCH_MANAGED_DB_PASSWORD",
			"CRYPTO_RESEARCH_MANAGED_FLYWAY_URL",
			"CRYPTO_RESEARCH_MANAGED_FLYWAY_USERNAME",
			"CRYPTO_RESEARCH_MANAGED_FLYWAY_PASSWORD");

	ManagedPostgresConfiguration(Environment environment) {
		MANDATORY_SETTINGS.forEach(setting -> requireValue(environment, setting));
		requireTlsMode(environment, "CRYPTO_RESEARCH_MANAGED_DB_URL");
		requireTlsMode(environment, "CRYPTO_RESEARCH_MANAGED_FLYWAY_URL");
	}

	private void requireValue(Environment environment, String setting) {
		var value = environment.getProperty(setting);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing mandatory managed PostgreSQL setting: " + setting);
		}
	}

	private void requireTlsMode(Environment environment, String setting) {
		var jdbcUrl = environment.getRequiredProperty(setting);
		if (!jdbcUrl.regionMatches(true, 0, POSTGRESQL_JDBC_PREFIX, 0, POSTGRESQL_JDBC_PREFIX.length())
				|| !REQUIRED_TLS_MODE.matcher(jdbcUrl).find()) {
			throw new IllegalStateException(
					"Managed PostgreSQL JDBC setting must use PostgreSQL and a TLS-required sslmode: " + setting);
		}
	}
}
