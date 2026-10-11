package io.cryptoresearch.marketdata.application;

import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.Configuration;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings;

/** One finite owning workload; terminal causes never acquire another provider segment. */
@Service
public class PumpSwapContinuousCaptureCoordinator {
	private final ContinuousCaptureLedgerUseCase ledger;
	private final PumpSwapPoolCollector collector;
	private final DeriveSealedCaptureUseCase derive;
	private final PumpSwapCaptureSettings settings;
	private volatile Thread worker;
	private volatile ContinuousCaptureLedgerUseCase.Admission current;
	public PumpSwapContinuousCaptureCoordinator(ContinuousCaptureLedgerUseCase ledger,PumpSwapPoolCollector collector,DeriveSealedCaptureUseCase derive,PumpSwapCaptureSettings settings) {
		this.ledger=ledger;this.collector=collector;this.derive=derive;this.settings=settings;
	}
	public synchronized void start(Configuration configuration) {
		if(worker!=null && worker.isAlive())throw new IllegalStateException("CONTINUOUS_CAPTURE_ALREADY_RUNNING");
		validateSettings();worker=Thread.ofVirtual().name("pumpswap-continuous-capture").start(()->run(configuration));
	}
	private void validateSettings() {
		if(!settings.isEnabled() || !settings.isContinuousEnabled())throw new IllegalStateException("CONTINUOUS_CAPTURE_DISABLED");
		if(settings.getConfigurationFile()!=null && !settings.getConfigurationFile().isBlank())throw new IllegalStateException("AMBIGUOUS_CAPTURE_LAUNCH_MODE");
		if(settings.getRpc()==null || settings.getWebsocket()==null || settings.getSecret()==null || settings.getSecret().isBlank())
			throw new IllegalStateException("MISSING_PROVIDER_CREDENTIAL_OR_ENDPOINT");
	}
	void run(Configuration configuration) {
		validateSettings();
		if(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(configuration).contains(settings.getSecret()))
			throw new IllegalStateException("SECRET_IN_CAPTURE_CONFIGURATION");
		var holder=UUID.randomUUID().toString();
		try {
			while(true) {
				if(Thread.currentThread().isInterrupted())throw new InterruptedException();
				var a=ledger.admit(configuration,holder);current=a;if(!a.terminal().isEmpty())break;
				if(a.segment().state().equals("CAPTURING")) {
					var started=collector.startEnrolled(a);
					if(!started.state().equals("RUNNING"))throw new IllegalStateException(started.reason());
					while(true) {
						ledger.heartbeat(a);var lifecycle=collector.lifecycle(a.segment().configuration().sessionId());
						if(lifecycle.workersExited()) {
							if(lifecycle.reason().equals("CAPTURE_TIME_LIMIT") && ledger.now().isBefore(CaptureLedgerUseCase.end(a.segment().configuration()))) {
								waitBounded(configuration,CaptureLedgerUseCase.end(a.segment().configuration()));continue;
							}
							var sealed=ledger.seal(a,lifecycle);if(!sealed.state().equals("DERIVING"))return;break;
						}
						// Cleanup may consume its original finite timeout after the provider end, never a new provider window.
						var drainEnd=CaptureLedgerUseCase.end(a.segment().configuration()).plusSeconds(a.segment().configuration().limits().timeoutSeconds()+1);
						if(!ledger.now().isBefore(drainEnd))throw new IllegalStateException("INCOMPLETE_TERMINAL");
						waitBounded(configuration,drainEnd);
					}
				}
				derive.derive(a);
			}
		} catch(InterruptedException cancelled) {
			Thread.currentThread().interrupt();fail("OWNER_STOP");
		} catch(RuntimeException failure) {
			fail(Thread.currentThread().isInterrupted()?"OWNER_STOP":io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.safeReason(failure));
		} finally {
			var a=current;
			if(a!=null)org.slf4j.LoggerFactory.getLogger(getClass()).info("PumpSwap deployment progress identity={} terminal={} segment={} billing=UNKNOWN coverage=UNVERIFIED",
				PayloadFingerprint.sha256(a.configuration().deploymentKey()),a.terminal(),a.segment()==null?0:a.segment().ordinal());
		}
	}
	private void waitBounded(Configuration c,Instant deadline) throws InterruptedException {
		var remaining=Duration.between(ledger.now(),deadline).toMillis();
		if(remaining<=0)return;
		Thread.sleep(Math.min(c.pollMillis(),remaining));
	}
	private void fail(String reason) {
		var a=current;if(a==null)return;
		// Interrupted virtual-thread JDBC closes its socket. Defer cancellation restoration until
		// the existing finite drain and fenced terminal transition, never release collector ownership.
		var interrupted=Thread.interrupted();
		try {
			try {if(a.segment()!=null && a.segment().state().equals("CAPTURING"))collector.stop(a.segment().configuration().sessionId());}
			catch(RuntimeException cleanupFailure) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("PumpSwap deployment cleanup unavailable category=FENCE_OR_STORAGE_FAILURE");}
			try {ledger.terminal(a,reason);}catch(RuntimeException lostFence) {
				org.slf4j.LoggerFactory.getLogger(getClass()).warn("PumpSwap deployment terminal persistence unavailable category=FENCE_OR_STORAGE_FAILURE");
			}
		} finally {
			if(interrupted)Thread.currentThread().interrupt();
		}
	}
	@jakarta.annotation.PreDestroy void shutdown() {var w=worker;if(w!=null)w.interrupt();}
}
