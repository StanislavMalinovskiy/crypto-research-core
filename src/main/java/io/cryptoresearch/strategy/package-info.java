/** Versioned strategy experiments, candidates and accepted signals. */
@ApplicationModule(allowedDependencies = {
		"kernel::api", "marketdata::api", "risk::api", "wallet::api"
})
package io.cryptoresearch.strategy;

import org.springframework.modulith.ApplicationModule;
