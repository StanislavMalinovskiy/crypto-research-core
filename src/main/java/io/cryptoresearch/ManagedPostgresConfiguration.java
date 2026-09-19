package io.cryptoresearch;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("managed")
class ManagedPostgresConfiguration {

	private static final String POSTGRESQL_JDBC_PREFIX = "jdbc:postgresql://";
	private static final Set<String> REQUIRED_TLS_MODES = Set.of("require", "verify-ca", "verify-full");
	private static final List<String> MANDATORY_SETTINGS = List.of(
			"CRYPTO_RESEARCH_MANAGED_DB_URL",
			"CRYPTO_RESEARCH_MANAGED_DB_USERNAME",
			"CRYPTO_RESEARCH_MANAGED_DB_PASSWORD");

	ManagedPostgresConfiguration(Environment environment) {
		MANDATORY_SETTINGS.forEach(setting -> requireValue(environment, setting));
		requireTlsMode(environment, "CRYPTO_RESEARCH_MANAGED_DB_URL");
	}

	private void requireValue(Environment environment, String setting) {
		var value = environment.getProperty(setting);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing mandatory managed PostgreSQL setting: " + setting);
		}
	}

	private void requireTlsMode(Environment environment, String setting) {
		var jdbcUrl = environment.getRequiredProperty(setting);
		var sslModes = sslModes(jdbcUrl);
		if (!jdbcUrl.regionMatches(true, 0, POSTGRESQL_JDBC_PREFIX, 0, POSTGRESQL_JDBC_PREFIX.length())
				|| sslModes.size() != 1
				|| !REQUIRED_TLS_MODES.contains(sslModes.getFirst().toLowerCase(Locale.ROOT))) {
			throw new IllegalStateException(
					"Managed PostgreSQL JDBC setting must use PostgreSQL and exactly one TLS-required sslmode: "
							+ setting);
		}
	}

	private List<String> sslModes(String jdbcUrl) {
		var queryStart = jdbcUrl.indexOf('?');
		if (queryStart < 0 || queryStart == jdbcUrl.length() - 1) {
			return List.of();
		}
		return Arrays.stream(jdbcUrl.substring(queryStart + 1).split("&", -1))
				.map(parameter -> parameter.split("=", 2))
				.filter(parts -> decode(parts[0]).equalsIgnoreCase("sslmode"))
				.map(parts -> parts.length == 2 ? decode(parts[1]) : "")
				.toList();
	}

	private String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
