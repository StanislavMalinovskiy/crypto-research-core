package io.cryptoresearch.marketdata.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;
import io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport;
import io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapCaptureSettings;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@Service
public class PumpSwapPoolCollector {
	private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PumpSwapPoolCollector.class);
	private static final int RAW_TRANSACTION_VERSION=1;
	static JsonNode historyRows(String result) { return tools.jackson.databind.json.JsonMapper.builder().build().readTree(result).path("data"); }
	static boolean reconnectAllowed(int total,int consecutive,int configured) { return total<configured && total<20 && consecutive<3; }
	static int stableFailures(int count,java.time.Instant acknowledged,java.time.Instant now,boolean healthyWriter) { return healthyWriter && acknowledged!=null && !now.isBefore(acknowledged.plusSeconds(300))?0:count; }
	private final PumpSwapCaptureSettings settings;
	private final CaptureLedgerUseCase ledger;
	private final CaptureEvidenceUseCase evidence;
	private final ObjectMapper json;
	private final AtomicReference<Run> active=new AtomicReference<>();
	private volatile Run lastRun;
	record Lifecycle(UUID session,boolean workersExited,boolean terminalCommitted,boolean writerFailed,String reason) { }
	Lifecycle lifecycle(UUID session) {
		var run=active.get();if(run==null)run=lastRun;
		if(run==null || !run.c.sessionId().equals(session))throw new IllegalStateException("CAPTURE_LIFECYCLE_UNAVAILABLE");
		return new Lifecycle(session,run.coordinatorDone && run.writerDone && !run.worker.isAlive() && !run.databaseWriter.isAlive(),
			run.terminalCommitted,run.writerFailed,run.interruption.get()==null?"":run.interruption.get());
	}
	public PumpSwapPoolCollector(PumpSwapCaptureSettings settings,CaptureLedgerUseCase ledger,CaptureEvidenceUseCase evidence,ObjectMapper json) {
		this.settings=settings; this.ledger=ledger; this.evidence=evidence; this.json=json;
	}
	public synchronized Status start(Configuration c) {
		if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())return blocked(c,"CAPTURE_REQUIRES_NO_TRANSACTION");
		return start(c,null,null);
	}
	synchronized Status startEnrolled(ContinuousCaptureLedgerUseCase.Admission admission) {
		if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())return blocked(admission.segment().configuration(),"CAPTURE_REQUIRES_NO_TRANSACTION");
		return start(admission.segment().configuration(),admission.fence().sessionHolder(),admission);
	}
	private Status start(Configuration c,String admittedHolder,ContinuousCaptureLedgerUseCase.Admission admission) {
		var problem=!settings.isEnabled()?"COLLECTOR_DISABLED":CaptureBudget.launchProblem(c);
		if(problem.isEmpty() && (settings.getRpc()==null || settings.getWebsocket()==null || settings.getSecret()==null || settings.getSecret().isBlank())) problem="MISSING_PROVIDER_CREDENTIAL_OR_ENDPOINT";
		if(problem.isEmpty() && json.writeValueAsString(c).contains(settings.getSecret())) problem="SECRET_IN_CAPTURE_CONFIGURATION";
		if(!problem.isEmpty()) return blocked(c,problem);
		if(active.get()!=null) return blocked(c,"COLLECTOR_ALREADY_RUNNING");
		var holder=admittedHolder==null?UUID.randomUUID().toString():admittedHolder;
		try {
			if(admission==null) {if(!ledger.open(c,holder)) return blocked(c,"CAPTURE_LEASE_HELD");}
			else ledger.validateEnrolled(c,holder);
			var run=new Run(c,holder); active.set(run);lastRun=run;
			run.worker=Thread.ofVirtual().name("pumpswap-capture").start(run::loop);
			return ledger.status(c.sessionId());
		} catch(RuntimeException failure) { return blocked(c,JdkPumpSwapTransport.safeReason(failure)); }
	}
	private Status blocked(Configuration c,String reason) {
		return new Status(c==null?null:c.sessionId(),"BLOCKED_FOR_LAUNCH",reason,"",0,0,0,0,0,0,0,0,0,0,0,
				java.math.BigDecimal.ZERO,"UNKNOWN","UNKNOWN_UNSOLICITED_OR_BUFFERED_BILLING","CAPTURE_ONLY_ECONOMICS_UNSUPPORTED",java.util.List.of());
	}
	public Status stop(UUID session) {
		var run=active.get();
		if(run!=null && run.c.sessionId().equals(session)) {
			run.terminal("OWNER_STOP");
			if(run.worker!=null) {
				run.worker.interrupt();
				try { var remaining=run.drainDeadline-System.nanoTime(); if(remaining>0) run.worker.join(java.time.Duration.ofNanos(remaining)); }
				catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
			}
		}
		return ledger.status(session);
	}
	public Status status(UUID session) { return ledger.status(session); }
	public Replay replay(UUID session) { return ledger.replay(session); }
	@jakarta.annotation.PreDestroy void shutdown() {
		var run=active.get(); if(run==null) return;
		run.terminal("APPLICATION_SHUTDOWN"); if(run.worker!=null) run.worker.interrupt();
	}

	private final class Run implements JdkPumpSwapTransport.Accounting {
		private final Configuration c;
		private final String holder;
		private final Object local=new Object();
		private record Write(long bytes,Runnable action,CompletableFuture<Void> done,boolean notificationFrame) {
			Write(long bytes,Runnable action,CompletableFuture<Void> done) { this(bytes,action,done,false); }
		}
		private Write completionTransfer; private boolean completionTransferred;
		private final AtomicLong callbackMaxNanos=new AtomicLong();
		public void websocketHandled(long nanos) { callbackMaxNanos.accumulateAndGet(nanos,Math::max); }
		private String writerReason(Throwable failure) {
			var reason=JdkPumpSwapTransport.safeReason(failure);
			return "PROVIDER_TRANSPORT_FAILURE".equals(reason)?"CAPTURE_WRITER_FAILURE":reason;
		}
		private final LinkedBlockingDeque<Write> writes;
		private long requests,received,subscription,retained,rpc;
		private long writtenRequests,writtenReceived,writtenSubscription,writtenRetained,writtenRpc;
		private int mailboxItems; private long mailboxBytes;
		private boolean fragmentHeld; private long fragmentBytes;
		private volatile boolean admitting=true,writerClosing,writerFailed;
		private volatile long drainDeadline=Long.MAX_VALUE;
		private boolean terminalStarted;
		private volatile boolean coordinatorDone,writerDone,terminalCommitted;
		private void releaseActive() { if(coordinatorDone && writerDone) active.compareAndSet(this,null); }
		private Thread databaseWriter;
		private record Queued(Notification notification,java.time.Instant receivedAt) { }
		private final ArrayBlockingQueue<Queued> queue;
		private final AtomicLong queuedBytes=new AtomicLong();
		private final AtomicReference<String> interruption=new AtomicReference<>();
		private volatile JdkPumpSwapTransport transport;
		private volatile boolean stopped;
		private Thread worker;
		private java.time.Instant lastRequest;
		private volatile java.time.Instant recoveryDeadline;
		private volatile java.time.Instant interruptedAt;
		private java.util.List<Gap> episode=java.util.List.of();
		private int reconnectAttempts,reconnectSuccesses,recoveryPages,recoveryIdentities;
		private int recoveryNewlyDurable,recoveryDuplicates;
		private final java.util.Map<String,Object> recoveryUpper=new java.util.TreeMap<>();
		private final java.util.Map<String,Integer> episodePages=new java.util.HashMap<>();
		public void diagnostic(String text) {
			// Callback diagnostics share bounded payload capacity but never wait for the writer.
			try { var n=json.readTree(text); var fields=new java.util.LinkedHashMap<String,Object>(); fields.put("session",c.sessionId().toString()); fields.put("attempt",reconnectAttempts); fields.put("episode",episode.stream().map(Gap::id).toList()); fields.put("diagnostic",n); var record=json.writeValueAsString(fields); var at=java.time.Instant.parse(n.path("observedAt").asText()); var bytes=record.getBytes(StandardCharsets.UTF_8).length; if(bytes>8192) throw new IllegalStateException("CAPTURE_DIAGNOSTIC_BYTE_LIMIT"); log.info("PumpSwap capture diagnostic {}",record); retained(bytes); write(bytes,false,()->flush("CAPTURE_DIAGNOSTIC",record,at)); }
			catch(RuntimeException unavailable) { interrupted("CAPTURE_DIAGNOSTIC_UNDURABLE"); }
		}
		public void controlStarted() {
			synchronized(local) { if(!admitting || requests>=c.limits().requests()) throw new IllegalStateException("CAPTURE_RESOURCE_LIMIT"); requests++; }
			write(0,false,()->flush(null,null,null));
		}
		Run(Configuration c,String holder) {
			this.c=c; this.holder=holder; queue=new ArrayBlockingQueue<>(c.limits().queueItems()); writes=new LinkedBlockingDeque<>(c.limits().queueItems());
			var status=ledger.status(c.sessionId()); requests=writtenRequests=status.requests(); received=writtenReceived=status.receivedBytes();
			subscription=writtenSubscription=status.subscriptionBytes(); retained=writtenRetained=status.retainedBytes(); rpc=writtenRpc=status.rpcHundredths();
		}
		private boolean acquire(long bytes) {
			synchronized(local) { if(!admitting || mailboxItems>=c.limits().queueItems() || bytes>c.limits().queueBytes()-mailboxBytes) return false; mailboxItems++; mailboxBytes+=bytes; return true; }
		}
		private void release(long bytes) { synchronized(local) { mailboxItems--; mailboxBytes-=bytes; } }
		private CompletableFuture<Void> write(long bytes,boolean priority,Runnable action) {
			var done=new CompletableFuture<Void>();
			if(!acquire(bytes)) { interrupted("QUEUE_OVERFLOW"); done.completeExceptionally(new IllegalStateException(interruption.get()==null?"QUEUE_OVERFLOW":interruption.get())); return done; }
			var work=new Write(bytes,action,done);
			if(!(priority?writes.offerFirst(work):writes.offerLast(work))) { release(bytes); interrupted("QUEUE_OVERFLOW"); done.completeExceptionally(new IllegalStateException("QUEUE_OVERFLOW")); }
			return done;
		}
		private void onWriter(Runnable action) {
			onWriter(0,action);
		}
		private void onWriter(long bytes,Runnable action) {
			try { write(bytes,false,action).get(c.limits().timeoutSeconds(),TimeUnit.SECONDS); }
			catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
			catch(TimeoutException failure) { interrupted("CAPTURE_WRITER_TIMEOUT"); throw new IllegalStateException("CAPTURE_WRITER_TIMEOUT"); }
			catch(ExecutionException failure) { throw new IllegalStateException(JdkPumpSwapTransport.safeReason(failure.getCause())); }
		}
		private void flush(String kind,String text,java.time.Instant at) {
			flush(kind,text,at,()->{});
		}
		private void flush(String kind,String text,java.time.Instant at,Runnable guard) {
			long r,b,s,t,cu;
			synchronized(local) { r=requests; b=received; s=subscription; t=retained; cu=rpc; }
			var closing=drainDeadline!=Long.MAX_VALUE;
			var fits=ledger.captureWrite(c,holder,r-writtenRequests,b-writtenReceived,s-writtenSubscription,t-writtenRetained,cu-writtenRpc,kind,text,at,closing,closing?this::closingTime:guard);
			accounted(r,b,s,t,cu,fits);
		}
		private void flushAccount(Notification n,java.time.Instant at) {
			long r,b,s,t,cu; synchronized(local) { r=requests; b=received; s=subscription; t=retained; cu=rpc; }
			var closing=drainDeadline!=Long.MAX_VALUE;
			var fits=ledger.captureAccount(c,holder,r-writtenRequests,b-writtenReceived,s-writtenSubscription,t-writtenRetained,cu-writtenRpc,n.account(),n.slot(),n.payload(),n.envelope(),at,closing,closing?this::closingTime:()->{});
			accounted(r,b,s,t,cu,fits);
		}
		private void flushClosingAccounting() {
			closingTime();
			boolean pending; synchronized(local) { pending=requests!=writtenRequests || received!=writtenReceived || subscription!=writtenSubscription || retained!=writtenRetained || rpc!=writtenRpc; }
			if(pending) ledger.closingWork(c,holder,drainDeadline,()->flush(null,null,null),this::closingTime);
		}
		private void accounted(long r,long b,long s,long t,long cu,boolean fits) {
			writtenReceived=b; writtenSubscription=s; writtenRetained=t;
			if(!fits) {
				// Known delivered bytes remain durable; the provider operation was never admitted.
				synchronized(local) { requests-=r-writtenRequests; rpc-=cu-writtenRpc; }
				throw new IllegalStateException("CAPTURE_RESOURCE_LIMIT");
			}
			writtenRequests=r; writtenRpc=cu;
		}
		private void writerLoop() {
			var renewal=ledger.now().plusSeconds(Math.max(1,c.limits().leaseSeconds()/3));
			boolean closingOperation=false;
			try {
				while(!writerClosing || !writes.isEmpty()) {
					closingTime();
					var work=writes.poll(100,TimeUnit.MILLISECONDS);
					if(work!=null) {
						long began=System.nanoTime();
						try {
							if(!ledger.now().isBefore(CaptureLedgerUseCase.end(c))) beginClosing("CAPTURE_TIME_LIMIT");
							closingOperation=drainDeadline!=Long.MAX_VALUE;
							if(closingOperation) ledger.closingWork(c,holder,drainDeadline,work.action(),this::closingTime); else work.action().run();
							closingTime();
							if(work.notificationFrame()) { completionTransfer=work; completionTransferred=false; work.done().complete(null); if(!completionTransferred) release(work.bytes()); completionTransfer=null; }
							else { release(work.bytes()); work.done().complete(null); }
							int items; long bytes; synchronized(local) { items=mailboxItems; bytes=mailboxBytes; }
							try { log.debug("PumpSwap writer commit elapsedNanos={} payloadBytes={} mailboxItems={} mailboxBytes={} callbackMaxNanos={}",System.nanoTime()-began,work.bytes(),items,bytes,callbackMaxNanos.get()); }
							catch(RuntimeException diagnosticFailure) { /* Diagnostics never change durable completion or capacity. */ }
						}
					catch(RuntimeException failure) { release(work.bytes()); work.done().completeExceptionally(new IllegalStateException(writerReason(failure))); throw failure; }
					}
					if(!writerClosing && !ledger.now().isBefore(renewal)) {
						closingOperation=drainDeadline!=Long.MAX_VALUE;
						if(closingOperation) ledger.closingWork(c,holder,drainDeadline,()->{},this::closingTime);
						else if(!ledger.now().isBefore(CaptureLedgerUseCase.end(c))) interrupted("CAPTURE_TIME_LIMIT");
						else if(!ledger.claim(c.sessionId(),holder)) throw new IllegalStateException("CAPTURE_LEASE_LOST");
						renewal=ledger.now().plusSeconds(Math.max(1,c.limits().leaseSeconds()/3));
					}
				}
				closingOperation=true;
				var tail=new java.util.ArrayList<Notification>(); Queued item; while((item=queue.poll())!=null) tail.add(item.notification());
				if(!tail.isEmpty()) try {
					for(var n:tail) retained(n.envelope().getBytes(StandardCharsets.UTF_8).length+(n.account()==null?0L:n.payload().getBytes(StandardCharsets.UTF_8).length));
					long r,b,s,t,cu; synchronized(local) { r=requests; b=received; s=subscription; t=retained; cu=rpc; }
					ledger.closingTail(c,holder,drainDeadline,tail,r-writtenRequests,b-writtenReceived,s-writtenSubscription,t-writtenRetained,cu-writtenRpc,this::closingTime); accounted(r,b,s,t,cu,true);
				} finally { for(var n:tail) { var bytes=size(n); queuedBytes.addAndGet(-bytes); release(bytes); } }
				flushClosingAccounting();
				var reason=interruption.get()==null?"OWNER_STOP":interruption.get();
				var receipt=json.writeValueAsString(java.util.Map.of("version","capture-terminal-v1","session",c.sessionId().toString(),"result","TERMINAL_"+reason,"at",ledger.now().toString(),"knownAdmitted",java.util.Map.of("requests",writtenRequests,"received",writtenReceived,"wss",writtenSubscription,"retained",writtenRetained,"rpcHundredths",writtenRpc),"coverage","EXPLICIT_GAPS_NOT_COMPLETE_CHAIN","outsideEvidenceDelivery","UNKNOWN"));
				var bytes=receipt.getBytes(StandardCharsets.UTF_8).length;
				synchronized(local) { if(bytes>8192 || bytes>c.limits().retainedBytes()-retained) receipt=null; else retained+=bytes; }
				flushClosingAccounting();
				ledger.close(c,holder,reason,drainDeadline,receipt,this::closingTime);
				terminalCommitted=true;
			} catch(InterruptedException failure) { Thread.currentThread().interrupt(); interrupted("CAPTURE_WRITER_INTERRUPTED"); }
			catch(RuntimeException failure) {
				writerFailed=true;
				log.warn("PumpSwap writer diagnostic {}",JdkPumpSwapTransport.diagnostic("WRITER",writerReason(failure),failure,null,null,null,null,ledger.now()));
				interrupted(writerReason(failure));
				if(!closingOperation) {
					// A failed normal write is not a failed closing attempt. Persist its primary cause once,
					// only under the still-valid fence and the same original drain deadline.
					synchronized(local) { if(drainDeadline==Long.MAX_VALUE) drainDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(c.limits().timeoutSeconds()+1L); }
					stopped=true; admitting=false; writerClosing=true;
					try { ledger.closingWork(c,holder,drainDeadline,()->flush(null,null,null),this::closingTime); }
					catch(RuntimeException unavailable) { /* Known committed facts/reservation remain; no retried provider work. */ }
					try { ledger.close(c,holder,interruption.get(),drainDeadline,null,this::closingTime); }
					catch(RuntimeException secondary) { log.warn("PumpSwap closing diagnostic {}",JdkPumpSwapTransport.diagnostic("SHUTDOWN",writerReason(secondary),secondary,null,null,null,null,ledger.now())); }
				}
			}
			finally { Write work; while((work=writes.poll())!=null) { release(work.bytes()); work.done().completeExceptionally(new IllegalStateException(interruption.get()==null?"CAPTURE_WRITER_FAILURE":interruption.get())); } writerDone=true; releaseActive(); }
		}
		private void time() {
			if(writerFailed) throw new IllegalStateException(interruption.get()==null?"CAPTURE_WRITER_FAILURE":interruption.get());
			if(stopped || !ledger.now().isBefore(CaptureLedgerUseCase.end(c))) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
			var deadline=recoveryDeadline;
			if(deadline!=null && !ledger.now().isBefore(deadline)) throw new IllegalStateException("RECOVERY_TIME_LIMIT");
		}
		private void recoveryTime() { time(); var reason=interruption.get(); if(reason!=null) throw new IllegalStateException(reason); }
		private void closingTime() { if(System.nanoTime()>=drainDeadline) throw new IllegalStateException("CAPTURE_WRITER_DRAIN_TIMEOUT"); }
		public void start(long cu) {
			time();
			if(cu>0) {
				waitSeconds(c.limits().timeoutSeconds());
				synchronized(local) {
					if(received>c.limits().receivedBytes()-c.limits().responseBytes()) throw new IllegalStateException("HTTP_RECEIVED_BYTE_HEADROOM_LIMIT");
					if(retained>c.limits().retainedBytes()-2L*c.limits().responseBytes()) throw new IllegalStateException("HTTP_RETAINED_BYTE_HEADROOM_LIMIT");
				}
			}
			if(lastRequest!=null) {
				var next=lastRequest.plusNanos(1000000000L/c.limits().requestsPerSecond());
				if(!next.isBefore(CaptureLedgerUseCase.end(c))) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
				var wait=java.time.Duration.between(ledger.now(),next).toNanos();
				if(wait>0) try { TimeUnit.NANOSECONDS.sleep(wait); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
			}
			synchronized(local) { time(); if(!admitting) throw new IllegalStateException(interruption.get()==null?"CAPTURE_STOPPED":interruption.get());
				if(requests>=c.limits().requests() || cu>c.limits().rpcHundredths()-rpc) throw new IllegalStateException("CAPTURE_RESOURCE_LIMIT"); requests++; rpc+=cu; }
			onWriter(()->flush(null,null,null)); lastRequest=ledger.now();
		}
		public void waitSeconds(long seconds) {
			if(seconds<0 || !ledger.now().plusSeconds(seconds).isBefore(CaptureLedgerUseCase.end(c))) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
			var deadline=recoveryDeadline;
			if(deadline!=null && !ledger.now().plusSeconds(seconds).isBefore(deadline)) throw new IllegalStateException("RECOVERY_TIME_LIMIT");
		}
		public void received(long bytes,boolean subscription) {
			boolean exceeded;
			synchronized(local) { received=Math.addExact(received,bytes); if(subscription) this.subscription=Math.addExact(this.subscription,bytes);
				exceeded=received>c.limits().receivedBytes() || this.subscription>c.limits().subscriptionBytes(); }
			// Transport close waits for callback admission; never retain the accounting monitor across it.
			if(exceeded) { interrupted("CAPTURE_RESOURCE_LIMIT"); throw new IllegalStateException("CAPTURE_RESOURCE_LIMIT"); }
		}
		public void websocketFragment(long bytes) {
			received(bytes,true);
			synchronized(local) {
				if(!admitting || (!fragmentHeld && mailboxItems>=c.limits().queueItems()) || bytes>c.limits().queueBytes()-mailboxBytes) throw new IllegalStateException("QUEUE_OVERFLOW");
				if(!fragmentHeld) { fragmentHeld=true; mailboxItems++; }
				fragmentBytes+=bytes; mailboxBytes+=bytes;
			}
		}
		public void discardWebsocketMessage() {
			synchronized(local) { if(fragmentHeld) { mailboxItems--; mailboxBytes-=fragmentBytes; fragmentHeld=false; fragmentBytes=0; } }
		}
		public void nextMessage() {
			synchronized(local) {
				time(); if(!admitting) throw new IllegalStateException(interruption.get()==null?"CAPTURE_STOPPED":interruption.get());
				// Hold one full-message and close/control headroom before asking the JDK for another fragment.
				var headroom=Math.addExact(c.limits().messageBytes(),256L);
				if(subscription>c.limits().subscriptionBytes()-headroom || received>c.limits().receivedBytes()-headroom)
					throw new IllegalStateException("SUBSCRIPTION_BYTE_HEADROOM_LIMIT");
			}
		}
		public void evidence(String kind,String text) {
			evidence(kind,text,ledger.now());
		}
		public java.time.Instant now() { return ledger.now(); }
		public void evidence(String kind,String text,java.time.Instant receivedAt) {
			retained(text.getBytes(StandardCharsets.UTF_8).length);
			onWriter(text.getBytes(StandardCharsets.UTF_8).length,()->flush(kind,text,receivedAt));
		}
		public CompletableFuture<Void> websocketEvidence(String text,java.time.Instant at) {
			var bytes=text.getBytes(StandardCharsets.UTF_8).length; retained(bytes);
			var done=new CompletableFuture<Void>();
			synchronized(local) { if(!fragmentHeld || fragmentBytes!=bytes) throw new IllegalStateException("CAPTURE_FRAME_RESERVATION_MISMATCH"); fragmentHeld=false; fragmentBytes=0; }
			var ack=json.readTree(text).has("id"); var work=new Write(bytes,()->flush("WEBSOCKET",text,at),done,!ack);
			if(!(ack?writes.offerFirst(work):writes.offerLast(work))) { release(bytes); interrupted("QUEUE_OVERFLOW"); done.completeExceptionally(new IllegalStateException("QUEUE_OVERFLOW")); }
			return done;
		}
		private long size(Notification n) { return n.envelope().getBytes(StandardCharsets.UTF_8).length+(long)n.payload().getBytes(StandardCharsets.UTF_8).length; }
		private void enqueue(Notification n) {
			var bytes=size(n); var total=queuedBytes.addAndGet(bytes);
			boolean acquired=false;
			if(Thread.currentThread()==databaseWriter && completionTransfer!=null && !completionTransferred) {
				synchronized(local) { var extra=bytes-completionTransfer.bytes(); if(extra>=0 && extra<=c.limits().queueBytes()-mailboxBytes) { mailboxBytes+=extra; acquired=true; completionTransferred=true; } }
			} else if(drainDeadline!=Long.MAX_VALUE) {
				synchronized(local) { if(!writerDone && System.nanoTime()<drainDeadline && mailboxItems<c.limits().queueItems() && bytes<=c.limits().queueBytes()-mailboxBytes) { mailboxItems++; mailboxBytes+=bytes; acquired=true; } }
			} else acquired=!stopped && acquire(bytes);
			if(!acquired || !queue.offer(new Queued(n,n.receivedAt()))) {
				if(acquired) release(bytes);
				queuedBytes.addAndGet(-bytes); interrupted("QUEUE_OVERFLOW");
				var current=transport; if(current!=null) current.close();
			}
		}
		private void interrupted(String reason) {
			if(interruption.compareAndSet(null,reason)) { interruptedAt=ledger.now(); var current=transport; if(current!=null) current.close(); }
		}
		private void retained(long bytes) {
			synchronized(local) { if(bytes>c.limits().retainedBytes()-retained) throw new IllegalStateException("RETAINED_BYTE_LIMIT"); retained+=bytes; }
		}
		private void verifyAndReadAccounts() {
			var reads=new java.util.HashMap<String,JdkPumpSwapTransport.Response>();
			for(var pool:c.pools()) for(var account:pool.accounts()) {
				var first=!reads.containsKey(account);
				var response=reads.computeIfAbsent(account,ignored->transport.rpc("getAccountInfo","[\""+account+"\",{\"commitment\":\"finalized\",\"encoding\":\"jsonParsed\",\"minContextSlot\":"+pool.proof().contextSlot()+"}]"));
				var result=json.readTree(response.result()); var value=result.path("value"); var slot=result.path("context").path("slot");
				if(!slot.isIntegralNumber() || slot.longValue()<pool.proof().contextSlot() || !value.isObject()) throw new IllegalStateException("LINKAGE_ACCOUNT_UNAVAILABLE");
				var base=account.equals(pool.baseVault()) || account.equals(pool.baseMint());
				var expected=account.equals(pool.address())?pool.program():base?pool.baseTokenProgram():pool.quoteTokenProgram();
				if(!expected.equals(value.path("owner").asText())) throw new IllegalStateException("LINKAGE_OWNER_MISMATCH");
				if(!account.equals(pool.address())) {
					var parsed=value.path("data").path("parsed"); var info=parsed.path("info");
					if(account.equals(pool.baseVault()) || account.equals(pool.quoteVault())) {
						if(!"account".equals(parsed.path("type").asText())) throw new IllegalStateException("UNSUPPORTED_VAULT_METADATA");
						if(!(base?pool.baseMint():pool.quoteMint()).equals(info.path("mint").asText())) throw new IllegalStateException("LINKAGE_MINT_MISMATCH");
						if(!pool.address().equals(info.path("owner").asText())) throw new IllegalStateException("LINKAGE_AUTHORITY_MISMATCH");
					} else if(!"mint".equals(parsed.path("type").asText()) || !info.path("decimals").isIntegralNumber()) throw new IllegalStateException("UNSUPPORTED_MINT_METADATA");
				}
				var payload=JdkPumpSwapTransport.exactValue(response.result(),"/value");
				if(first) {
					retained(response.envelope().getBytes(StandardCharsets.UTF_8).length+(long)payload.getBytes(StandardCharsets.UTF_8).length);
					onWriter(response.envelope().getBytes(StandardCharsets.UTF_8).length+(long)payload.getBytes(StandardCharsets.UTF_8).length,()->ledger.stateAt(c.sessionId(),holder,account,slot.longValue(),payload,response.envelope(),response.receivedAt()));
				}
			}
		}
		private String transaction(String pool,String signature,long slot,String envelope,java.time.Instant received) {
			return transaction(pool,signature,slot,envelope,received,null);
		}
		private String transaction(String pool,String signature,long slot,String envelope,java.time.Instant received,CaptureLedgerUseCase.HistoryPage page) {
			CaptureBudget.signature(signature);
			if(page!=null) ledger.verifyHistoryReference(c,page,pool,signature,slot,envelope,received);
			retained(envelope.getBytes(StandardCharsets.UTF_8).length);
			onWriter(envelope.getBytes(StandardCharsets.UTF_8).length,()->{ if(page==null) ledger.pendingAt(c.sessionId(),holder,pool,signature,slot,envelope,received); else ledger.pendingHistory(c,holder,pool,signature,slot,envelope,received,page); });
			if(CaptureBudget.reuse(c)) {
				retained(envelope.getBytes(StandardCharsets.UTF_8).length);
				onWriter(envelope.getBytes(StandardCharsets.UTF_8).length,()->ledger.envelopeAt(c.sessionId(),holder,"CAPTURE_TRANSACTION_NOTIFICATION",envelope,received));
				var reused=new AtomicReference<java.util.Optional<String>>();
				onWriter(()->reused.set(evidence.reuse(c,holder,pool,signature,slot,envelope,received,page,receipt->retained(receipt.getBytes(StandardCharsets.UTF_8).length))));
				time(); if(reused.get().isPresent()) return reused.get().get();
			}
			JdkPumpSwapTransport.Response response;
			try { response=transport.rpc("getTransaction","[\""+signature+"\",{\"commitment\":\"finalized\",\"encoding\":\"json\",\"maxSupportedTransactionVersion\":"+RAW_TRANSACTION_VERSION+"}]"); }
			catch(JdkPumpSwapTransport.UnsupportedTransactionVersion unsupported) {
				time(); var failed=unsupported.response();
				var fields=new java.util.LinkedHashMap<String,Object>(); fields.put("session",c.sessionId().toString()); fields.put("sourcePolicy",c.version()); fields.put("attempt",reconnectAttempts); fields.put("episode",episode.stream().map(Gap::id).toList()); fields.put("pool",pool); fields.put("signature",signature); fields.put("slot",slot); fields.put("requestedCeiling",RAW_TRANSACTION_VERSION); fields.put("errorDigest",PayloadFingerprint.sha256(failed.envelope()));
				fields.put("diagnostic",json.readTree(JdkPumpSwapTransport.diagnostic("TX_FETCH","UNSUPPORTED_TRANSACTION_VERSION",null,200,-32015,null,null,failed.receivedAt())));
				var diagnostic=json.writeValueAsString(fields); var diagnosticBytes=diagnostic.getBytes(StandardCharsets.UTF_8).length;
				if(diagnosticBytes>8192) throw new IllegalStateException("CAPTURE_DIAGNOSTIC_BYTE_LIMIT");
				var bytes=failed.envelope().getBytes(StandardCharsets.UTF_8).length+(long)diagnosticBytes; retained(bytes);
				onWriter(bytes,()->ledger.unsupportedTransactionVersion(c.sessionId(),holder,pool,signature,failed.envelope(),diagnostic,failed.receivedAt()));
				log.info("PumpSwap capture diagnostic {}",diagnostic);
				return "UNSUPPORTED_TRANSACTION_VERSION";
			}
			catch(RuntimeException failure) {
				var reason=JdkPumpSwapTransport.safeReason(failure);
				if(reason.startsWith("PROVIDER_") || reason.startsWith("MALFORMED_") || reason.equals("HTTP_RESPONSE_BYTE_LIMIT")) onWriter(()->{
					if("RUNNING".equals(ledger.status(c.sessionId()).state())) ledger.finish(c.sessionId(),holder,pool,signature,"FETCH_FAILURE",null,"{\"clientFailure\":\""+reason+"\"}",false);
				});
				throw failure;
			}
			time(); retained(response.envelope().getBytes(StandardCharsets.UTF_8).length+(long)response.result().getBytes(StandardCharsets.UTF_8).length);
			var result=new AtomicReference<String>(); onWriter(response.envelope().getBytes(StandardCharsets.UTF_8).length+(long)response.result().getBytes(StandardCharsets.UTF_8).length,()->{
				if(CaptureBudget.reuse(c) && response.result()!=null && !"null".equals(response.result())) {
					var fetched=CaptureTransactionSource.parse(response.result()).path("slot");
					if(!fetched.isIntegralNumber() || !fetched.bigIntegerValue().equals(java.math.BigInteger.valueOf(slot))) throw new IllegalStateException("CAPTURE_REUSE_IDENTITY_CONFLICT");
				}
				result.set(evidence.transaction(c.sessionId(),holder,pool,signature,response.result(),response.envelope(),response.receivedAt(),true));
			}); return result.get();
		}
		private void process(Queued queued) {
			var n=queued.notification();
			if(n.account()==null) transaction(n.pool(),n.signature(),n.slot(),n.envelope(),queued.receivedAt());
			else {
				retained(n.envelope().getBytes(StandardCharsets.UTF_8).length+(long)n.payload().getBytes(StandardCharsets.UTF_8).length);
				onWriter(()->flushAccount(n,queued.receivedAt()));
			}
		}
		private void recover() {
			for(var gap:episode) {
				recoveryTime();
				if(!gap.kind().equals("TRANSACTION")) continue;
				var pool=c.pools().stream().filter(p->p.address().equals(gap.pool())).findFirst().orElseThrow();
				var boundary=java.util.Optional.ofNullable(gap.boundarySignature());
				if(boundary.isEmpty()) { summary("UNPROVED_INITIAL_BOUNDARY",this::recoveryTime); recoveryTime(); continue; }
				String cursor=null; boolean continuity=true; long previous=Long.MAX_VALUE; var seen=new java.util.HashSet<String>();
				for(var page=episodePages.getOrDefault(pool.address(),0);page<c.limits().recoveryPages();page++) {
					recoveryTime(); episodePages.put(pool.address(),page+1); recoveryPages++;
					var params="[\""+pool.address()+"\",{\"commitment\":\"finalized\",\"transactionDetails\":\"full\",\"encoding\":\"json\",\"maxSupportedTransactionVersion\":"+RAW_TRANSACTION_VERSION+",\"sortOrder\":\"desc\",\"filters\":{\"status\":\"any\"},\"limit\":100"
							+(cursor==null?"":",\"paginationToken\":\""+cursor+"\"")+"}]";
					var response=transport.rpc("getTransactionsForAddress",params); recoveryTime(); if(!historyProvenance()) retained(response.envelope().getBytes(StandardCharsets.UTF_8).length);
					var result=json.readTree(response.result()); var transactions=historyRows(response.result());
					if(!transactions.isArray() || transactions.size()>100) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
					CaptureLedgerUseCase.HistoryPage proof=null;
					if(historyProvenance()) {
						var digest=PayloadFingerprint.sha256(response.envelope()); var firstReceived=ledger.historyPageReceivedAt(c,digest); recoveryTime(); var context=new java.util.LinkedHashMap<String,Object>(); context.put("version","capture-history-context-v1"); context.put("session",c.sessionId().toString()); context.put("method","getTransactionsForAddress"); context.put("pool",pool.address()); context.put("cursor",cursor==null?"":cursor); context.put("pageDigest",digest); context.put("receivedAt",firstReceived.toString());
						if(CaptureBudget.reuse(c)) { context.put("sourcePolicy",c.version()); context.put("commitment","finalized"); context.put("encoding","json"); context.put("transactionDetails","full"); context.put("maxSupportedTransactionVersion",RAW_TRANSACTION_VERSION); }
						var text=json.writeValueAsString(context); retained(text.getBytes(StandardCharsets.UTF_8).length);
						var verified=new AtomicReference<CaptureLedgerUseCase.HistoryPage>(); onWriter(text.getBytes(StandardCharsets.UTF_8).length,()->verified.set(ledger.historyPage(c,holder,text))); recoveryTime(); proof=verified.get();
					}
					var keys=new java.util.ArrayList<String>(); for(var row:transactions) keys.add(row.path("transaction").path("signatures").path(0).asText()); var priorKeys=new java.util.HashSet<>(ledger.successfulPage(c,pool.address(),keys)); recoveryTime(); var rowIndex=0;
					boolean reached=false;
					for(var row:transactions) {
						recoveryTime();
						var signature=row.path("transaction").path("signatures").path(0).asText(); var slot=row.path("slot"); CaptureBudget.signature(signature); recoveryIdentities++;
						if(!slot.isIntegralNumber() || slot.longValue()<0 || slot.longValue()>previous || !seen.add(signature)) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
						previous=slot.longValue();
						recoveryUpper.putIfAbsent(pool.address(),java.util.Map.of("slot",slot.longValue(),"signature",signature));
						if(signature.equals(boundary.get())) { if(gap.fromSlot()==null || slot.longValue()!=gap.fromSlot()) throw new IllegalStateException("RECOVERY_BOUNDARY_CONFLICT"); reached=true; break; }
						var prior=priorKeys.contains(signature); String notification=response.envelope();
						if(proof!=null) { var ref=new java.util.LinkedHashMap<String,Object>(); ref.put("version","capture-history-row-v1"); ref.put("session",c.sessionId().toString()); ref.put("method","getTransactionsForAddress"); ref.put("pool",pool.address()); ref.put("cursor",proof.cursor()); ref.put("pageDigest",proof.envelope().digest()); ref.put("rowIndex",rowIndex); ref.put("rowDigest",PayloadFingerprint.sha256(proof.rows().get(rowIndex))); ref.put("signature",signature); ref.put("slot",slot.longValue()); ref.put("receivedAt",proof.envelope().receivedAt().toString()); notification=json.writeValueAsString(ref); }
						var status=transaction(pool.address(),signature,slot.longValue(),notification,proof==null?response.receivedAt():proof.envelope().receivedAt(),proof); rowIndex++;
						recoveryTime();
						if(java.util.Set.of("CAPTURED","FAILED_TRANSACTION").contains(status)) { if(prior) recoveryDuplicates++; else recoveryNewlyDurable++; priorKeys.add(signature); }
						if(!java.util.Set.of("CAPTURED","FAILED_TRANSACTION").contains(status)) { if(java.util.Set.of("UNAVAILABLE","UNSUPPORTED_TRANSACTION_VERSION").contains(status)) continuity=false; else throw new IllegalStateException("RECOVERY_EVIDENCE_FAILURE"); }
					}
					if(reached) { recoveryTime(); if(continuity) onWriter(()->ledger.recoveredGap(c,holder,gap,this::recoveryTime)); recoveryTime(); summary(continuity?"RECOVERED_TRANSACTIONS":"PARTIAL_UNAVAILABLE_OR_UNSUPPORTED",this::recoveryTime); recoveryTime(); break; }
					var next=result.path("paginationToken").asText("");
					if(next.isBlank() || next.equals(cursor) || !next.matches("[A-Za-z0-9_:-]{1,512}")) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
					if(page+1==c.limits().recoveryPages()) throw new IllegalStateException("RECOVERY_PAGE_LIMIT");
					cursor=next;
				}
				if(episodePages.getOrDefault(pool.address(),0)>=c.limits().recoveryPages() && ledger.gaps(c.sessionId()).stream().anyMatch(g->g.id()==gap.id() && g.status().equals("UNRESOLVED_TRANSACTION_GAP")) && continuity) throw new IllegalStateException("RECOVERY_PAGE_LIMIT");
			}
		}
		private void summary(String result) {
			summary(result,()->{});
		}
		private void summary(String result,Runnable guard) {
			guard.run(); var snapshot=ledger.summarySnapshot(c); guard.run();
			var fields=new java.util.LinkedHashMap<String,Object>(snapshot); fields.put("version","capture-summary-v2"); fields.put("session",c.sessionId().toString()); fields.put("result",result); fields.put("at",ledger.now().toString());
			synchronized(local) { fields.put("localUnflushed",java.util.Map.of("requests",requests-writtenRequests,"received",received-writtenReceived,"wss",subscription-writtenSubscription,"retained",retained-writtenRetained,"rpcHundredths",rpc-writtenRpc)); }
			fields.put("attempts",reconnectAttempts); fields.put("successes",reconnectSuccesses); fields.put("recoveryPages",recoveryPages); fields.put("returnedIdentities",recoveryIdentities); fields.put("newlyDurable",recoveryNewlyDurable); fields.put("recoveryDuplicates",recoveryDuplicates); fields.put("recoveryUpper",recoveryUpper); fields.put("episodeGaps",episode.stream().map(g->java.util.Map.of("id",g.id(),"pool",g.pool(),"kind",g.kind(),"boundary",g.boundarySignature()==null?"UNKNOWN":g.boundarySignature())).toList()); fields.put("outsideEvidenceDelivery","UNKNOWN");
			var text=json.writeValueAsString(fields); var bytes=text.getBytes(StandardCharsets.UTF_8).length; if(bytes>8192) throw new IllegalStateException("CAPTURE_SUMMARY_BYTE_LIMIT");
			guard.run(); retained(bytes); var observed=ledger.now();
			if(Thread.currentThread()==databaseWriter) flush("CAPTURE_SUMMARY",text,observed,guard); else onWriter(bytes,()->{ guard.run(); flush("CAPTURE_SUMMARY",text,observed,guard); });
			guard.run();
		}
		private void drain() {
			transport.close(); transport.awaitAdmitted(); onWriter(()->{ });
			Queued item; while((item=queue.poll())!=null) {
				var n=item.notification(); try {
					if(n.account()==null) { retained(n.envelope().getBytes(StandardCharsets.UTF_8).length); onWriter(()->ledger.pendingAt(c.sessionId(),holder,n.pool(),n.signature(),n.slot(),n.envelope(),n.receivedAt())); }
					else process(item);
				} finally { var bytes=size(n); queuedBytes.addAndGet(-bytes); release(bytes); }
			}
		}
		private void retryPending() {
			var pages=new java.util.HashMap<java.util.List<String>,CaptureLedgerUseCase.HistoryPage>(); var payloads=new java.util.HashMap<String,CaptureLedgerUseCase.HistoryPage>(); long bytes=0;
			for(var d:ledger.pending(c)) {
				time(); CaptureLedgerUseCase.HistoryPage page=null;
				if(historyReference(d.notification())) { var n=json.readTree(d.notification()); var digest=n.path("pageDigest").asText(); var key=java.util.List.of(c.sessionId().toString(),digest,n.path("pool").asText(),n.path("cursor").asText()); page=pages.get(key); if(page==null) { var cached=payloads.get(digest); page=ledger.historyPage(c,d.notification(),cached); if(cached==null) { bytes=Math.addExact(bytes,page.envelope().payload().getBytes(StandardCharsets.UTF_8).length); if(bytes>c.limits().queueBytes()) throw new IllegalStateException("RECOVERY_PROVENANCE_BYTE_LIMIT"); payloads.put(digest,page); } pages.put(key,page); } ledger.verifyHistoryReference(c,page,d.pool(),d.signature(),d.slot(),d.notification(),d.receivedAt()); }
				time(); transaction(d.pool(),d.signature(),d.slot(),d.notification(),d.receivedAt(),page);
			}
		}
		private boolean historyReference(String notification) {
			if(!notification.stripLeading().startsWith("{")) return false;
			try { var n=json.readTree(notification); return n.isObject() && (n.path("version").asText().startsWith("capture-history-") || historyProvenance() && n.has("pageDigest") && (n.has("rowIndex") || n.has("rowDigest"))); }
			catch(RuntimeException malformed) { if(notification.stripLeading().matches("(?s)\\{\\s*\"version\"\\s*:\\s*\"capture-history-.*")) throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID"); return false; }
		}
		private boolean historyProvenance() { return c.version().equals("capture-provenance-v5") || CaptureBudget.reuse(c); }
		private void loop() {
			String terminal="CAPTURE_TIME_LIMIT"; var consecutive=0; java.time.Instant stableSince=null;
			databaseWriter=Thread.ofVirtual().name("pumpswap-durable-writer").start(this::writerLoop);
			try {
				episode=ledger.gaps(c.sessionId()).stream().filter(g->g.reason().equals("RESTART_UNFINISHED_SESSION") && g.status().startsWith("UNRESOLVED")).toList();
				if(!episode.isEmpty()) recoveryDeadline=ledger.now().plusSeconds(c.limits().recoverySeconds());
				while(!stopped) {
					interruption.set(null); interruptedAt=null; admitting=true; time(); transport=new JdkPumpSwapTransport(settings.getRpc(),settings.getWebsocket(),settings.getSecret(),c.limits(),this);
					boolean recovering=false;
					try {
						verifyAndReadAccounts(); transport.connect(c.pools(),this::enqueue,this::interrupted);
						stableSince=ledger.now(); if(reconnectAttempts>0) reconnectSuccesses++;
						if(!episode.isEmpty()) { recovering=true; retryPending(); recoveryTime(); recover(); recoveryTime(); summary("RECOVERY_COMPLETE_WITH_EXPLICIT_GAPS",this::recoveryTime); recoveryTime(); episode=java.util.List.of(); episodePages.clear(); recoveryDeadline=null; recovering=false; }
						else retryPending();
						while(!stopped && interruption.get()==null) {
							time(); transport.heartbeat();
							consecutive=stableFailures(consecutive,stableSince,ledger.now(),!writerFailed);
							var notification=queue.poll(1,TimeUnit.SECONDS);
							if(notification!=null) { try { process(notification); } finally { var bytes=size(notification.notification()); queuedBytes.addAndGet(-bytes); release(bytes); } }
						}
					} catch(RuntimeException failure) { if(recovering || interruption.get()==null && !transport.reconnectable()) throw failure; interrupted(JdkPumpSwapTransport.safeReason(failure)); }
					if(stopped) break;
					var reason=interruption.get();
					if(writerFailed || !transport.reconnectable() || "WS_DURABLE_ACK_TIMEOUT".equals(reason) || reason.startsWith("CAPTURE_") || reason.endsWith("LIMIT") || reason.contains("SECRET") || !reconnectAllowed(reconnectAttempts,consecutive,c.limits().reconnects())) { terminal=reason; break; }
					if(recoveryDeadline==null) recoveryDeadline=interruptedAt.plusSeconds(c.limits().recoverySeconds());
					drain();
					if(episode.isEmpty()) { recoveryUpper.clear(); var opened=new AtomicReference<java.util.List<Gap>>(); onWriter(()->opened.set(ledger.episode(c.sessionId(),holder,reason,interruptedAt))); episode=opened.get(); }
					consecutive++; reconnectAttempts++; summary("RECONNECT_PENDING");
					var wait=consecutive==1?1:3; waitSeconds(wait); TimeUnit.SECONDS.sleep(wait);
				}
			} catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); terminal="OWNER_STOP"; }
			catch(RuntimeException failure) { terminal=JdkPumpSwapTransport.safeReason(failure); diagnostic(JdkPumpSwapTransport.diagnostic("SHUTDOWN",terminal,failure,null,null,transport==null?null:transport.closeCode(),null,ledger.now())); }
			finally {
				var interrupted=Thread.interrupted();
					try { terminal(terminal); coordinatorDone=true; releaseActive(); }
				finally { if(interrupted) Thread.currentThread().interrupt(); }
			}
		}
		private void beginClosing(String reason) {
			interruption.compareAndSet(null,reason); stopped=true; admitting=false;
			synchronized(local) { if(drainDeadline==Long.MAX_VALUE) drainDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(c.limits().timeoutSeconds()+1L); }
		}
		private void terminal(String reason) {
			beginClosing(reason);
			boolean first; synchronized(local) { first=!terminalStarted; terminalStarted=true; }
			if(first) {
				discardWebsocketMessage(); var current=transport; if(current!=null) { current.close(); try { current.awaitAdmitted(); } catch(RuntimeException secondary) { log.warn("PumpSwap closing diagnostic {}",JdkPumpSwapTransport.diagnostic("SHUTDOWN",JdkPumpSwapTransport.safeReason(secondary),secondary,null,null,null,null,ledger.now())); } }
				writerClosing=true;
			}
			if(databaseWriter!=null && Thread.currentThread()!=databaseWriter) try { var remaining=drainDeadline-System.nanoTime(); if(remaining>0) databaseWriter.join(java.time.Duration.ofNanos(remaining)); }
			catch(InterruptedException failure) { Thread.currentThread().interrupt(); }
		}
	}
}
