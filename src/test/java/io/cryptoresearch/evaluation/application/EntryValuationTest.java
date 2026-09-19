package io.cryptoresearch.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.cryptoresearch.evaluation.api.EvaluationApi.PricingStatus;
import io.cryptoresearch.kernel.api.AssetId;
import io.cryptoresearch.kernel.api.BlockPosition;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.kernel.api.EventId;
import io.cryptoresearch.kernel.api.TransactionId;
import io.cryptoresearch.kernel.api.WalletAddress;
import io.cryptoresearch.marketdata.api.MarketDataApi.MarketObservation;
import io.cryptoresearch.marketdata.api.MarketDataApi.NormalizedSwapIdentity;
import io.cryptoresearch.marketdata.api.MarketDataApi.TradeSide;
import io.cryptoresearch.risk.api.RiskApi.AssetLifecycle;
import io.cryptoresearch.risk.api.RiskApi.RiskAssessment;
import io.cryptoresearch.risk.api.RiskApi.RiskDecision;
import io.cryptoresearch.risk.api.RiskApi.RiskFacts;
import io.cryptoresearch.signal.api.SignalApi.AcceptedSignalSnapshot;

class EntryValuationTest {

	private final EntryValuation valuation = new EntryValuation();
	private final Instant availableAt = Instant.parse("2026-09-01T01:00:00Z");

	@Test
	void selectsStrictlyPostDecisionEntryAndCalculatesExactReturn() {
		var outcome = valuation.evaluate(
				"sha256:" + "1".repeat(64), signal(), "1h",
				List.of(observation("at-decision", availableAt, "0.900000000000000000", "80000.00000000"),
						observation("entry", availableAt.plusSeconds(1), "1.000000000000000000", "80000.00000000")),
				List.of(observation("horizon", availableAt.plusSeconds(3600), "1.200000000000000000", "85000.00000000")));

		assertThat(outcome.entryPrice()).hasValueSatisfying(value -> assertThat(value.observedAt()).isAfter(availableAt));
		assertThat(outcome.grossReturn()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.20000000"));
		assertThat(outcome.friction()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.04000000"));
		assertThat(outcome.netReturn()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0.16000000"));
	}

	@Test
	void appliesAllThreeSolanaLiquidityTiersAtExactBoundaries() {
		assertThat(valuation.friction(new BigDecimal("200000.00000000"))).isEqualByComparingTo("0.03000000");
		assertThat(valuation.friction(new BigDecimal("50000.00000000"))).isEqualByComparingTo("0.04000000");
		assertThat(valuation.friction(new BigDecimal("49999.99999999"))).isEqualByComparingTo("0.05000000");
	}

	@Test
	void leavesOutcomeUnpricedWhenNoEntryExistsBeforeTheOneHourHorizon() {
		var atHorizon = observation(
				"at-horizon", availableAt.plusSeconds(3600), "1.200000000000000000", "85000.00000000");

		var outcome = valuation.evaluate(
				"sha256:" + "5".repeat(64), signal(), "1h", List.of(atHorizon), List.of(atHorizon));

		assertThat(outcome.status()).isEqualTo(PricingStatus.UNPRICED);
		assertThat(outcome.missingPriceReason()).contains("NO_ADMISSIBLE_ENTRY_PRICE");
		assertThat(outcome.entryPrice()).isEmpty();
		assertThat(outcome.horizonPrice()).isEmpty();
	}

	private AcceptedSignalSnapshot signal() {
		var asset = new AssetId(ChainId.SOLANA_MAINNET, "asset");
		var facts = new RiskFacts(asset, availableAt, 0, AssetLifecycle.DISCOVERY,
				new BigDecimal("80000.00000000"), "risk-v1");
		return new AcceptedSignalSnapshot(
				"signal", "candidate", "LIQUIDITY_SPIKE", "ENTRY", asset, availableAt,
				"sha256:" + "2".repeat(64), List.of(), new RiskAssessment(RiskDecision.ALLOW, facts, List.of("allow")),
				"detector-v1", "scorer-v1", "sha256:" + "3".repeat(64), 70, "B",
				new BigDecimal("1.0000"), List.of());
	}

	private MarketObservation observation(String transactionValue, Instant observedAt, String price, String liquidity) {
		var transaction = new TransactionId(ChainId.SOLANA_MAINNET, transactionValue);
		return new MarketObservation(
				new NormalizedSwapIdentity(ChainId.SOLANA_MAINNET, transaction, new EventId(transaction, "event")),
				new AssetId(ChainId.SOLANA_MAINNET, "asset"),
				new WalletAddress(ChainId.SOLANA_MAINNET, "wallet"), TradeSide.BUY,
				BigInteger.ONE, BigInteger.ONE, new BigDecimal(price), new BigDecimal(liquidity),
				new BigDecimal("1.0000"), "venue", new BlockPosition(ChainId.SOLANA_MAINNET, 1),
				Optional.empty(), Optional.empty(), observedAt, "provider", "sha256:" + "4".repeat(64), "parser-v1");
	}
}
