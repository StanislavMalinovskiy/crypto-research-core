package io.cryptoresearch.marketdata.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Immutable finite deployment authority. Reservations are not measured use or invoices. */
public final class PumpSwapContinuousCapture {
	private PumpSwapContinuousCapture() { }
	public record Resources(long requests,long receivedBytes,long subscriptionBytes,long retainedBytes,
			long rpcHundredths,long reservedHundredths,long seconds) {
		public Resources {
			if(requests<0 || receivedBytes<0 || subscriptionBytes<0 || retainedBytes<0 || rpcHundredths<0 || reservedHundredths<0 || seconds<0)
				throw new IllegalArgumentException("NEGATIVE_DEPLOYMENT_RESOURCES");
		}
		public Resources plus(Resources r) {
			return new Resources(Math.addExact(requests,r.requests),Math.addExact(receivedBytes,r.receivedBytes),
					Math.addExact(subscriptionBytes,r.subscriptionBytes),Math.addExact(retainedBytes,r.retainedBytes),
					Math.addExact(rpcHundredths,r.rpcHundredths),Math.addExact(reservedHundredths,r.reservedHundredths),Math.addExact(seconds,r.seconds));
		}
		public boolean contains(Resources r) {
			return requests>=r.requests && receivedBytes>=r.receivedBytes && subscriptionBytes>=r.subscriptionBytes
					&& retainedBytes>=r.retainedBytes && rpcHundredths>=r.rpcHundredths && reservedHundredths>=r.reservedHundredths && seconds>=r.seconds;
		}
	}
	public record Configuration(String deploymentKey,String version,PumpSwapCapture.Configuration template,
			Resources allowances,Resources opening,Instant globalEnd,int maxSegments,long segmentSeconds,
			int maxPages,long decodeSeconds,long receiptBytes,long pollMillis) {
		public Configuration {
			Objects.requireNonNull(template,"MISSING_CAPTURE_TEMPLATE");
			template=new PumpSwapCapture.Configuration(template.sessionId(),template.version(),template.provider(),template.startsAt().truncatedTo(ChronoUnit.MICROS),
				template.pools().stream().sorted(java.util.Comparator.comparing(PumpSwapCapture.Pool::address)).toList(),template.limits(),template.authority());
			Objects.requireNonNull(allowances,"MISSING_DEPLOYMENT_ALLOWANCES");
			if(opening==null)throw new IllegalArgumentException("UNKNOWN_DEPLOYMENT_OPENING_USAGE");
			if(deploymentKey==null || deploymentKey.isBlank() || version==null || !version.equals("continuous-capture-v1"))
				throw new IllegalArgumentException("INVALID_DEPLOYMENT_IDENTITY");
			if(globalEnd==null || !globalEnd.isAfter(template.startsAt()) || globalEnd.isAfter(template.authority().periodEnd()))
				throw new IllegalArgumentException("INVALID_DEPLOYMENT_END");
			globalEnd=globalEnd.truncatedTo(ChronoUnit.MICROS);
			if(maxSegments<=0 || segmentSeconds<=0 || segmentSeconds>template.limits().windowSeconds() || maxPages<=0 || decodeSeconds<=0
					|| receiptBytes<Math.multiplyExact((long)maxPages,1024L) || pollMillis<=0
					|| pollMillis>Math.multiplyExact(Math.max(1,template.limits().leaseSeconds()/3),1000))
				throw new IllegalArgumentException("INVALID_DEPLOYMENT_FINITE_LIMITS");
			if(!allowances.contains(opening))throw new IllegalArgumentException("DEPLOYMENT_OPENING_EXCEEDS_ALLOWANCE");
		}
	}
}
