package io.cryptoresearch.signal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LiquiditySpikeSignalServiceTest {

	@Test
	void appliesBothExactLiquidityThresholds() {
		var service = new LiquiditySpikeSignalService(null, null, null, null);

		assertThat(service.meetsThresholds(new BigDecimal("50000.00000000"), new BigDecimal("75000.00000000")))
				.isTrue();
		assertThat(service.meetsThresholds(new BigDecimal("50000.00000000"), new BigDecimal("74999.99999999")))
				.isFalse();
		assertThat(service.meetsThresholds(new BigDecimal("10000.00000000"), new BigDecimal("15000.00000000")))
				.isFalse();
	}
}
