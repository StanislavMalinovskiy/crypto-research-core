package io.cryptoresearch.marketdata.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashSet;
import tools.jackson.databind.json.JsonMapper;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration;

public final class CaptureBudget {
	private CaptureBudget() { }
	public static long reservation(Configuration configuration) {
		return Math.addExact(subscriptionHundredths(configuration, configuration.limits().subscriptionBytes()),
				configuration.limits().rpcHundredths());
	}
	public static long subscriptionHundredths(Configuration configuration, long bytes) {
		var exact=configuration.authority().subscriptionCuPerByte().multiply(BigDecimal.valueOf(bytes)).movePointRight(2);
		return (reuse(configuration)?exact.setScale(0,RoundingMode.CEILING):exact).longValueExact();
	}
	public static boolean reuse(Configuration configuration) { return "capture-rpc-reuse-v6".equals(configuration.version()); }
	public static long subscriptionOverageIncrement(Configuration c,long current,long increment) {
		var previous=Math.subtractExact(current,increment); var maximum=c.limits().subscriptionBytes();
		if(!reuse(c)) return subscriptionHundredths(c,Math.subtractExact(Math.max(0,current-maximum),Math.max(0,previous-maximum)));
		var reserved=subscriptionHundredths(c,maximum);
		return Math.subtractExact(Math.max(0,Math.subtractExact(subscriptionHundredths(c,current),reserved)),
				Math.max(0,Math.subtractExact(subscriptionHundredths(c,previous),reserved)));
	}
	public static BigDecimal cost(long hundredths, BigDecimal rate) {
		return BigDecimal.valueOf(hundredths).multiply(rate).movePointLeft(8);
	}
	public static long ceiling(Configuration configuration) {
		var authority = configuration.authority();
		return Math.min(authority.ceilingHundredths(), Math.multiplyExact(authority.ceilingUsd().movePointRight(6)
				.divide(authority.usdPerMillionCu(), 0, RoundingMode.FLOOR).longValueExact(),100));
	}
	public static String launchProblem(Configuration c) {
		if (c == null || c.authority() == null || c.limits() == null) return "MISSING_CONFIGURATION";
		var a = c.authority(); var l = c.limits();
		if(c.startsAt()==null) return "MISSING_START_TIME";
		if (a.openingHundredths() == null) return "UNKNOWN_OPENING_USAGE";
		if (a.openingHundredths() < 0 || a.otherActivityHundredths() < 0) return "INVALID_OPENING_USAGE";
		if(!a.exclusiveAllocation() && a.otherActivityHundredths()==0) return "UNKNOWN_OTHER_ACCOUNT_USAGE";
		if (a.ownerAuthorization() == null || a.ownerAuthorization().isBlank()) return "MISSING_OWNER_AUTHORIZATION";
		if (!a.retentionAllowed()) return "MISSING_RETENTION_RIGHTS";
		if (a.accountPeriod() == null || a.accountPeriod().isBlank() || a.periodEnd() == null
				|| !a.periodEnd().isAfter(c.startsAt())) return "INVALID_BILLING_PERIOD";
		if (a.source()==null || !java.util.Set.of("OWNER_REPORTED", "INDEPENDENTLY_OBSERVED").contains(a.source())) return "UNKNOWN_ACCOUNT_AUTHORITY";
		if (c.sessionId() == null || c.version() == null || c.version().isBlank() || !"alchemy".equals(c.provider())) return "INVALID_CAPTURE_IDENTITY";
		if (a.ceilingUsd() == null || a.usdPerMillionCu() == null || a.subscriptionCuPerByte() == null
				|| a.ceilingUsd().signum() <= 0 || a.usdPerMillionCu().signum() <= 0
				|| a.subscriptionCuPerByte().signum() <= 0 || a.ceilingHundredths() <= 0) return "UNKNOWN_COST_BOUND";
		if (l.windowSeconds() <= 0 || l.windowSeconds() > 86400 || l.requests() <= 0 || l.receivedBytes() <= 0
				|| l.retainedBytes() <= 0 || l.subscriptionBytes() <= 0 || l.rpcHundredths() <= 0
				|| l.messageBytes() <= 0 || l.responseBytes() <= 0 || l.queueItems() <= 0 || l.queueBytes() <= 0
				|| l.timeoutSeconds() <= 0 || l.timeoutSeconds() > 10 || l.retries() < 0 || l.retries() > 2
				|| l.reconnects() < 0 || l.reconnects() > 20 || l.recoveryPages() <= 0 || l.recoveryPages() > 10
				|| l.recoverySeconds() <= 0 || l.recoverySeconds() > 300 || l.requestsPerSecond() <= 0
				|| l.requestsPerSecond() > 2 || l.leaseSeconds() <= l.timeoutSeconds()) return "INVALID_FINITE_LIMITS";
		if (c.pools().size() != 3) return "MISSING_THREE_POOL_ALLOWLIST";
		try {
			var pools = new HashSet<String>(); var accounts = new HashSet<String>();
			var json = JsonMapper.builder().build();
			for (var p : c.pools()) {
				for (var value : p.accounts()) { address(value); accounts.add(value); }
				address(p.program()); address(p.baseTokenProgram()); address(p.quoteTokenProgram());
				if (!pools.add(p.address()) || new HashSet<>(p.accounts()).size() != 5) return "INVALID_POOL_ALLOWLIST";
				if (!"pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA".equals(p.program())) return "UNSUPPORTED_POOL_PROGRAM";
				var proof = p.proof();
				if (proof == null || proof.contextSlot() < 0 || !"finalized".equals(proof.commitment())
						|| proof.sourceVersion() == null || proof.sourceVersion().isBlank() || proof.provenance() == null
						|| proof.provenance().isBlank() || proof.payload() == null) return "UNRESOLVED_LINKAGE_PROOF";
				if (!PayloadFingerprint.sha256(proof.payload()).equals(proof.digest())) return "LINKAGE_PROOF_DIGEST";
				var fields = json.readTree(proof.payload());
				if(!"solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp".equals(fields.path("chain").asText())
						|| !fields.path("contextSlot").isIntegralNumber() || fields.path("contextSlot").longValue()!=proof.contextSlot()
						|| !proof.commitment().equals(fields.path("commitment").asText())) return "LINKAGE_PROOF_CHAIN_CONTEXT_OR_FINALITY";
				var names = java.util.List.of("pool", "program", "baseVault", "quoteVault", "baseMint", "quoteMint", "baseTokenProgram", "quoteTokenProgram");
				var values = java.util.List.of(p.address(), p.program(), p.baseVault(), p.quoteVault(), p.baseMint(), p.quoteMint(), p.baseTokenProgram(), p.quoteTokenProgram());
				for (var i = 0; i < names.size(); i++) if (!values.get(i).equals(fields.path(names.get(i)).asText())) return "LINKAGE_PROOF_MAPPING";
			}
			if (accounts.size() > 15) return "ACCOUNT_ALLOWLIST_LIMIT";
			if (Math.addExact(Math.addExact(a.openingHundredths(), a.otherActivityHundredths()), reservation(c)) > ceiling(c)) return "ACCOUNT_COST_LIMIT";
		}
		catch (RuntimeException invalid) { return "INVALID_LINKAGE_OR_LIMITS"; }
		return "";
	}
	public static byte[] address(String value) { return base58(value, 32); }
	public static byte[] signature(String value) { return base58(value, 64); }
	private static byte[] base58(String value, int size) {
		if (value == null || value.isEmpty() || value.length() > (size == 32 ? 44 : 88)) throw new IllegalArgumentException("Invalid base58 identity");
		var alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
		var number = BigInteger.ZERO;
		for (var ch : value.toCharArray()) {
			var digit = alphabet.indexOf(ch); if (digit < 0) throw new IllegalArgumentException("Invalid base58 identity");
			number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
		}
		var leading = 0; while (leading < value.length() && value.charAt(leading) == '1') leading++;
		var body = number.signum() == 0 ? new byte[0] : number.toByteArray();
		var offset = body.length > 0 && body[0] == 0 ? 1 : 0;
		if (leading + body.length - offset != size) throw new IllegalArgumentException("Invalid base58 identity size");
		var decoded = new byte[size]; System.arraycopy(body, offset, decoded, leading, body.length - offset); return decoded;
	}
}
