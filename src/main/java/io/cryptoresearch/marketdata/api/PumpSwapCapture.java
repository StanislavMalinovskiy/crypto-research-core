package io.cryptoresearch.marketdata.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable, secret-free capture contracts. No economic or research admission is implied. */
public final class PumpSwapCapture {

	private PumpSwapCapture() { }

	public record Pool(String address, String program, String baseVault, String quoteVault,
			String baseMint, String quoteMint, String baseTokenProgram, String quoteTokenProgram,
			LinkageProof proof) {
		public List<String> accounts() { return List.of(address, baseVault, quoteVault, baseMint, quoteMint); }
	}

	public record LinkageProof(long contextSlot, String commitment, String sourceVersion,
			String provenance, String payload, String digest) { }

	public record Limits(long windowSeconds, long requests, long receivedBytes, long retainedBytes,
			long subscriptionBytes, long rpcHundredths, int messageBytes, int responseBytes,
			int queueItems, long queueBytes, int timeoutSeconds, int retries, int reconnects,
			int recoveryPages, long recoverySeconds, int requestsPerSecond, long leaseSeconds) { }

	public record Authority(String accountPeriod, Instant periodEnd, String source,
			Long openingHundredths, long otherActivityHundredths, long ceilingHundredths,
			BigDecimal ceilingUsd, BigDecimal usdPerMillionCu, BigDecimal subscriptionCuPerByte,
			String ownerAuthorization, boolean retentionAllowed, boolean exclusiveAllocation) {
		public Authority {
			if(periodEnd!=null) periodEnd=periodEnd.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
			if(ceilingUsd!=null) ceilingUsd=ceilingUsd.stripTrailingZeros();
			if(usdPerMillionCu!=null) usdPerMillionCu=usdPerMillionCu.stripTrailingZeros();
			if(subscriptionCuPerByte!=null) subscriptionCuPerByte=subscriptionCuPerByte.stripTrailingZeros();
		}
	}

	public record Configuration(UUID sessionId, String version, String provider, Instant startsAt,
			List<Pool> pools, Limits limits, Authority authority) {
		public Configuration { pools = List.copyOf(pools); }
	}

	public record Disposition(String pool, String signature, long slot, String status,
			String rawDigest, String notification, String response, Instant receivedAt, Instant admittedAt,
			long duplicates, long conflicts) { }

	public record AccountSnapshot(String account, long slot, String digest, String encoding,
			String payload, String envelope, Instant receivedAt, Instant admittedAt, Instant ingestedAt,
			String positionStatus, String providerVisibleTimeStatus, boolean ambiguous) { }

	public record Envelope(String kind,String digest,String payload,Instant receivedAt) { }

	public record Gap(long id, String pool, String kind, String reason, Long fromSlot,
			Long toSlot, Instant openedAt, Instant closedAt, String boundarySignature, String status) { }

	public record Status(UUID sessionId, String state, String reason, String configurationDigest,
			long transactions, long states, long duplicates, long conflicts, long failures,
			long requests, long receivedBytes, long subscriptionBytes, long retainedBytes,
			long rpcHundredths, long reservedHundredths, BigDecimal reservedUsd,
			String actualBilling, String providerSideExcess, String quality,
			List<Gap> gaps) {
		public Status { gaps = List.copyOf(gaps); }
	}

	public record Replay(Configuration configuration, List<Disposition> dispositions,
			List<AccountSnapshot> states, List<Envelope> envelopes, Status status) {
		public Replay { dispositions = List.copyOf(dispositions); states = List.copyOf(states); envelopes=List.copyOf(envelopes); }
	}
}
