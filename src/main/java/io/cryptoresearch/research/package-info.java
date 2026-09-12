/** Point-in-time replay, backtest and evidence reporting. */
@ApplicationModule(allowedDependencies = {
		"marketdata::api", "risk::api", "wallet::api", "strategy::api", "measurement::api"
})
package io.cryptoresearch.research;

import org.springframework.modulith.ApplicationModule;
