/** Point-in-time valuation, outcomes, evaluation replay and reports. */
@ApplicationModule(allowedDependencies = {
		"kernel::api", "marketdata::api", "signal::api"
})
package io.cryptoresearch.evaluation;

import org.springframework.modulith.ApplicationModule;
