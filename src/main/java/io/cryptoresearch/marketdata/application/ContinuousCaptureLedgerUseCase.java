package io.cryptoresearch.marketdata.application;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import io.cryptoresearch.marketdata.api.PumpSwapContinuousCapture.*;
import io.cryptoresearch.marketdata.api.PumpSwapCapture;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore.*;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore;

/** Atomic conservative admission, committed before provider I/O. No reservation refund/reopen API. */
@Service
public class ContinuousCaptureLedgerUseCase {
	public record Admission(Configuration configuration,Fence fence,Segment segment,boolean resumed,String terminal) { }
	public record TerminalObservation(String state,String reason,int ordinal,String sessionState,String sessionReason,
			Resources reserved,long periodReservedHundredths,long knownOverageHundredths) { }
	@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
	public TerminalObservation terminalObservation(Admission admission) {
		store.timeout();return store.terminalObservation(admission.configuration().deploymentKey());
	}
	private final JdbcContinuousCaptureStore store;
	private final JdbcCaptureStore capture;
	private final CaptureLedgerUseCase legacy;
	private final Clock clock;
	public ContinuousCaptureLedgerUseCase(JdbcContinuousCaptureStore store,JdbcCaptureStore capture,CaptureLedgerUseCase legacy,Clock clock) {
		this.store=store;this.capture=capture;this.legacy=legacy;this.clock=clock;
	}
	public Instant now() {return clock.instant().truncatedTo(ChronoUnit.MICROS);}
	static Resources resources(PumpSwapCapture.Configuration c) {
		var l=c.limits();return new Resources(l.requests(),l.receivedBytes(),l.subscriptionBytes(),l.retainedBytes(),l.rpcHundredths(),CaptureBudget.reservation(c),l.windowSeconds());
	}
	static UUID sessionId(String key,int ordinal) {
		var hash=new CanonicalFingerprint().field("version","continuous-segment-v1").field("deployment",key).field("ordinal",Integer.toString(ordinal)).finish();
		var bytes=java.util.HexFormat.of().parseHex(hash.substring(7,39));var b=java.nio.ByteBuffer.wrap(bytes);return new UUID(b.getLong(),b.getLong());
	}
	static PumpSwapCapture.Configuration segmentConfiguration(Configuration d,int ordinal,Instant at,long seconds) {
		var c=d.template();var l=c.limits();var a=c.authority();
		var authorization=new CanonicalFingerprint().field("version","continuous-segment-authority-v1").field("deployment",d.deploymentKey())
			.field("originalAuthority",a.ownerAuthorization()).field("ordinal",Integer.toString(ordinal)).finish();
		return new PumpSwapCapture.Configuration(sessionId(d.deploymentKey(),ordinal),c.version(),c.provider(),at,
			c.pools().stream().sorted(Comparator.comparing(PumpSwapCapture.Pool::address)).toList(),
			new PumpSwapCapture.Limits(seconds,l.requests(),l.receivedBytes(),l.retainedBytes(),l.subscriptionBytes(),l.rpcHundredths(),l.messageBytes(),l.responseBytes(),
				l.queueItems(),l.queueBytes(),l.timeoutSeconds(),l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds()),
			new PumpSwapCapture.Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),authorization,a.retentionAllowed(),a.exclusiveAllocation()));
	}
	@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
	public Admission admit(Configuration c,String holder) {
		var problem=CaptureBudget.launchProblem(c.template());if(!problem.isEmpty())throw new IllegalArgumentException(problem);
		var text=store.encode(c);if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1000000)throw new IllegalArgumentException("CAPTURE_CONFIGURATION_BYTE_LIMIT");
		capture.providerAdmissionLock(c.template().provider());var at=now();var until=at.plusSeconds(c.template().limits().leaseSeconds());
		capture.period(c.template(),CaptureBudget.ceiling(c.template()),Math.addExact(c.template().authority().openingHundredths(),c.template().authority().otherActivityHundredths()));
		store.insert(c,new CanonicalFingerprint().field("version","continuous-configuration-v1").field("configuration",text).finish(),holder,until);
		var d=store.lockProvider(c.template().provider());
		if(!d.configuration().equals(c))throw new IllegalStateException("CONTINUOUS_CONFIGURATION_CONFLICT");
		store.lockPeriod(c.template().authority().accountPeriod());
		if(!d.state().equals("RUNNING"))return new Admission(c,d.fence(),null,false,d.reason());
		var f=store.takeover(d,holder,at,until);store.fence(f,at,until);
		var previous=store.latest(c.deploymentKey());
		if(previous.isPresent() && !previous.get().state().equals("COMPLETE")) {
			var s=previous.get();
			if(s.state().equals("DERIVING"))return new Admission(c,f,s,true,"");
			if(!s.state().equals("CAPTURING") || !at.isBefore(CaptureLedgerUseCase.end(s.configuration())) || !at.isBefore(c.globalEnd())
					|| !capture.status(s.configuration().sessionId()).state().equals("RUNNING"))return stop(c,f,s,"INCOMPLETE_TERMINAL");
			if(!capture.claim(s.configuration().sessionId(),f.sessionHolder(),at,until))throw new IllegalStateException("CAPTURE_LEASE_HELD");
			if(f.generation()!=d.fence().generation())legacy.interrupt(s.configuration().sessionId(),f.sessionHolder(),"RESTART_UNFINISHED_SESSION");
			return new Admission(c,f,s,true,"");
		}
		if(previous.isEmpty() && !c.opening().contains(store.priorAuthority(c.template().authority().accountPeriod())))
			throw new IllegalStateException("UNKNOWN_DEPLOYMENT_OPENING_USAGE");
		var ordinal=previous.map(s->Math.addExact(s.ordinal(),1)).orElse(1);
		if(!at.isBefore(c.globalEnd()))return stop(c,f,previous.orElse(null),"DEPLOYMENT_GLOBAL_END");
		if(ordinal>c.maxSegments())return stop(c,f,previous.orElse(null),"DEPLOYMENT_SEGMENT_LIMIT");
		var seconds=Math.min(c.segmentSeconds(),Duration.between(at,c.globalEnd()).getSeconds());
		if(seconds<=0)return stop(c,f,previous.orElse(null),"DEPLOYMENT_GLOBAL_END");
		var configuration=segmentConfiguration(c,ordinal,at,seconds);var allowance=resources(configuration);Resources reserved;
		try {reserved=d.reserved().plus(allowance);}catch(ArithmeticException overflow){return stop(c,f,previous.orElse(null),"DEPLOYMENT_RESOURCE_LIMIT");}
		if(!c.allowances().contains(reserved))return stop(c,f,previous.orElse(null),"DEPLOYMENT_RESOURCE_LIMIT");
		if(!capture.reservePeriod(configuration,allowance.reservedHundredths()))return stop(c,f,previous.orElse(null),"ACCOUNT_PERIOD_RESERVATION_REJECTED");
		store.reserve(c.deploymentKey(),reserved);
		capture.insertSession(configuration,legacy.fingerprint(configuration),f.sessionHolder(),until,CaptureLedgerUseCase.end(configuration),allowance.reservedHundredths());
		store.insertSegment(c,ordinal,configuration,legacy.fingerprint(configuration),allowance);
		if(previous.isPresent())store.boundary(previous.get(),configuration,at);
		return new Admission(c,f,store.segment(c.deploymentKey(),ordinal),false,"");
	}
	private Admission stop(Configuration c,Fence f,Segment s,String reason) {store.terminal(f,reason);return new Admission(c,f,s,false,reason);}
	@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
	public void heartbeat(Admission a) {store.timeout();store.fence(a.fence(),now(),now().plusSeconds(a.configuration().template().limits().leaseSeconds()));}
	@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
	public Segment seal(Admission a,PumpSwapPoolCollector.Lifecycle observation) {
		store.timeout();var at=now();store.fence(a.fence(),at,at.plusSeconds(a.configuration().template().limits().leaseSeconds()));
		var s=store.segment(a.fence().deployment(),a.segment().ordinal());
		if(s.sealedAt()!=null)return s;
		var status=capture.status(s.configuration().sessionId());
		if(!observation.session().equals(s.configuration().sessionId()) || !observation.workersExited() || !observation.terminalCommitted()
				|| observation.writerFailed() || !status.state().equals("STOPPED") || !status.reason().equals(observation.reason()))
			throw new IllegalStateException("INCOMPLETE_TERMINAL");
		if(!status.reason().equals("CAPTURE_TIME_LIMIT") || at.isBefore(CaptureLedgerUseCase.end(s.configuration()))) {
			store.state(s,"TERMINAL",status.reason());store.terminal(a.fence(),status.reason());return store.segment(s.deployment(),s.ordinal());
		}
		store.seal(s,at,at.plusSeconds(a.configuration().decodeSeconds()),store.sourceCount(s.configuration()),status.reason());
		return store.segment(s.deployment(),s.ordinal());
	}
	@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
	public void terminal(Admission a,String reason) {
		store.timeout();store.fence(a.fence(),now(),now().plusSeconds(a.configuration().template().limits().leaseSeconds()));
		store.terminal(a.fence(),reason);if(a.segment()!=null)store.state(a.segment(),"TERMINAL",reason);
	}
	@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
	public Segment progress(Admission a,Receipt receipt) {
		store.timeout();store.fence(a.fence(),now(),now().plusSeconds(a.configuration().template().limits().leaseSeconds()));
		var s=store.segment(a.fence().deployment(),a.segment().ordinal());
		if(s.sealedAt()==null || !s.state().equals("DERIVING") || !now().isBefore(s.decodeEnd()) || s.pages()>=a.configuration().maxPages())
			throw new IllegalStateException("DECODE_BACKLOG");
		store.progress(s,receipt,Math.addExact(s.processed(),receipt.count()));return store.segment(s.deployment(),s.ordinal());
	}
	@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
	public void complete(Admission a,Segment reconciled) {
		store.timeout();store.fence(a.fence(),now(),now().plusSeconds(a.configuration().template().limits().leaseSeconds()));
		var s=store.segment(reconciled.deployment(),reconciled.ordinal());
		if(!s.equals(reconciled) || s.processed()!=s.sources() || !now().isBefore(s.decodeEnd()))throw new IllegalStateException("DECODE_RECONCILIATION_FAILED");
		store.state(s,"COMPLETE",s.reason());
	}
	@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
	public JdbcContinuousCaptureStore.Page page(Admission a,Segment s,String cursor) {
		store.timeout();
		if(s.sealedAt()==null || !now().isBefore(s.decodeEnd()))throw new IllegalStateException("DECODE_BACKLOG");
		store.readFence(a.fence(),s,now());
		return store.page(s,cursor);
	}
	@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
	public JdbcContinuousCaptureStore.Page admitPage(Admission a,Segment s) {
		store.timeout();
		if(s.sealedAt()==null || !now().isBefore(s.decodeEnd()) || s.pages()>=a.configuration().maxPages())
			throw new IllegalStateException("DECODE_BACKLOG");
		store.readFence(a.fence(),s,now());return store.page(s,s.cursor());
	}
	@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
	public Segment current(Admission a) {store.timeout();return store.segment(a.fence().deployment(),a.segment().ordinal());}
	@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,timeout=10)
	public Receipt receipt(Segment s,int index) {store.timeout();return store.receipt(s,index);}
}
