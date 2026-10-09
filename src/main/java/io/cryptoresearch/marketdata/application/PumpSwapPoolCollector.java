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
	public PumpSwapPoolCollector(PumpSwapCaptureSettings settings,CaptureLedgerUseCase ledger,CaptureEvidenceUseCase evidence,ObjectMapper json) {
		this.settings=settings; this.ledger=ledger; this.evidence=evidence; this.json=json;
	}
	public synchronized Status start(Configuration c) {
		var problem=!settings.isEnabled()?"COLLECTOR_DISABLED":CaptureBudget.launchProblem(c);
		if(problem.isEmpty() && (settings.getRpc()==null || settings.getWebsocket()==null || settings.getSecret()==null || settings.getSecret().isBlank())) problem="MISSING_PROVIDER_CREDENTIAL_OR_ENDPOINT";
		if(problem.isEmpty() && json.writeValueAsString(c).contains(settings.getSecret())) problem="SECRET_IN_CAPTURE_CONFIGURATION";
		if(!problem.isEmpty()) return blocked(c,problem);
		if(active.get()!=null) return blocked(c,"COLLECTOR_ALREADY_RUNNING");
		var holder=UUID.randomUUID().toString();
		try {
			if(!ledger.open(c,holder)) return blocked(c,"CAPTURE_LEASE_HELD");
			var run=new Run(c,holder); active.set(run);
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
				try { run.worker.join(java.time.Duration.ofSeconds(run.c.limits().timeoutSeconds()+1L)); }
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
		private volatile boolean coordinatorDone,writerDone;
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
			long r,b,s,t,cu;
			synchronized(local) { r=requests; b=received; s=subscription; t=retained; cu=rpc; }
			var fits=ledger.captureWrite(c,holder,r-writtenRequests,b-writtenReceived,s-writtenSubscription,t-writtenRetained,cu-writtenRpc,kind,text,at);
			accounted(r,b,s,t,cu,fits);
		}
		private void flushAccount(Notification n,java.time.Instant at) {
			long r,b,s,t,cu; synchronized(local) { r=requests; b=received; s=subscription; t=retained; cu=rpc; }
			var fits=ledger.captureAccount(c,holder,r-writtenRequests,b-writtenReceived,s-writtenSubscription,t-writtenRetained,cu-writtenRpc,n.account(),n.slot(),n.payload(),n.envelope(),at);
			accounted(r,b,s,t,cu,fits);
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
			try {
				while(!writerClosing || !writes.isEmpty()) {
					if(System.nanoTime()>drainDeadline) throw new IllegalStateException("CAPTURE_WRITER_DRAIN_TIMEOUT");
					var work=writes.poll(100,TimeUnit.MILLISECONDS);
					if(work!=null) {
						long began=System.nanoTime();
						try {
							work.action().run();
							if(work.notificationFrame()) { completionTransfer=work; completionTransferred=false; work.done().complete(null); if(!completionTransferred) release(work.bytes()); completionTransfer=null; }
							else { release(work.bytes()); work.done().complete(null); }
							int items; long bytes; synchronized(local) { items=mailboxItems; bytes=mailboxBytes; }
							try { log.debug("PumpSwap writer commit elapsedNanos={} payloadBytes={} mailboxItems={} mailboxBytes={} callbackMaxNanos={}",System.nanoTime()-began,work.bytes(),items,bytes,callbackMaxNanos.get()); }
							catch(RuntimeException diagnosticFailure) { /* Diagnostics never change durable completion or capacity. */ }
						}
					catch(RuntimeException failure) { release(work.bytes()); work.done().completeExceptionally(new IllegalStateException(writerReason(failure))); throw failure; }
					}
					if(!writerClosing && !ledger.now().isBefore(renewal)) { if(!ledger.claim(c.sessionId(),holder)) throw new IllegalStateException("CAPTURE_LEASE_LOST"); renewal=ledger.now().plusSeconds(Math.max(1,c.limits().leaseSeconds()/3)); }
				}
				Queued tail; while((tail=queue.poll())!=null) { var n=tail.notification(); try {
					if(n.account()==null) { retained(n.envelope().getBytes(StandardCharsets.UTF_8).length); ledger.pendingAt(c.sessionId(),holder,n.pool(),n.signature(),n.slot(),n.envelope(),n.receivedAt()); }
					else { retained(n.envelope().getBytes(StandardCharsets.UTF_8).length+(long)n.payload().getBytes(StandardCharsets.UTF_8).length); flushAccount(n,n.receivedAt()); }
				} finally { var bytes=size(n); queuedBytes.addAndGet(-bytes); release(bytes); } }
				flush(null,null,null);
				try { summary("TERMINAL_"+(interruption.get()==null?"OWNER_STOP":interruption.get())); } catch(RuntimeException unavailable) { /* No invented committed diagnostic when the bound or store prevents it. */ }
				ledger.stop(c.sessionId(),holder,interruption.get()==null?"OWNER_STOP":interruption.get());
			} catch(InterruptedException failure) { Thread.currentThread().interrupt(); interrupted("CAPTURE_WRITER_INTERRUPTED"); }
			catch(RuntimeException failure) {
				writerFailed=true;
				log.warn("PumpSwap writer diagnostic {}",JdkPumpSwapTransport.diagnostic("WRITER",writerReason(failure),failure,null,null,null,null,ledger.now()));
				interrupted(writerReason(failure));
				try { flush(null,null,null); } catch(RuntimeException unavailable) { /* Preserve the terminal signal even when flushing cannot finish. */ }
				try { ledger.stop(c.sessionId(),holder,interruption.get()); }
				catch(RuntimeException unavailable) { /* Unfinished session and conservative reservation expose failed/expired storage. */ }
			}
			finally { Write work; while((work=writes.poll())!=null) { release(work.bytes()); work.done().completeExceptionally(new IllegalStateException(interruption.get()==null?"CAPTURE_WRITER_FAILURE":interruption.get())); } writerDone=true; releaseActive(); }
		}
		private void time() {
			if(writerFailed) throw new IllegalStateException(interruption.get()==null?"CAPTURE_WRITER_FAILURE":interruption.get());
			if(stopped || !ledger.now().isBefore(CaptureLedgerUseCase.end(c))) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
			var deadline=recoveryDeadline;
			if(deadline!=null && !ledger.now().isBefore(deadline)) throw new IllegalStateException("RECOVERY_TIME_LIMIT");
		}
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
			CaptureBudget.signature(signature);
			retained(envelope.getBytes(StandardCharsets.UTF_8).length);
			onWriter(envelope.getBytes(StandardCharsets.UTF_8).length,()->ledger.pendingAt(c.sessionId(),holder,pool,signature,slot,envelope,received));
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
			var result=new AtomicReference<String>(); onWriter(response.envelope().getBytes(StandardCharsets.UTF_8).length+(long)response.result().getBytes(StandardCharsets.UTF_8).length,()->result.set(evidence.transaction(c.sessionId(),holder,pool,signature,response.result(),response.envelope(),response.receivedAt(),true))); return result.get();
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
				if(!gap.kind().equals("TRANSACTION")) continue;
				var pool=c.pools().stream().filter(p->p.address().equals(gap.pool())).findFirst().orElseThrow();
				var boundary=java.util.Optional.ofNullable(gap.boundarySignature());
				if(boundary.isEmpty()) { summary("UNPROVED_INITIAL_BOUNDARY"); continue; }
				String cursor=null; boolean continuity=true; long previous=Long.MAX_VALUE; var seen=new java.util.HashSet<String>();
				for(var page=episodePages.getOrDefault(pool.address(),0);page<c.limits().recoveryPages();page++) {
					time(); episodePages.put(pool.address(),page+1); recoveryPages++;
					var params="[\""+pool.address()+"\",{\"commitment\":\"finalized\",\"transactionDetails\":\"full\",\"encoding\":\"json\",\"maxSupportedTransactionVersion\":"+RAW_TRANSACTION_VERSION+",\"sortOrder\":\"desc\",\"filters\":{\"status\":\"any\"},\"limit\":100"
							+(cursor==null?"":",\"paginationToken\":\""+cursor+"\"")+"}]";
					var response=transport.rpc("getTransactionsForAddress",params); retained(response.envelope().getBytes(StandardCharsets.UTF_8).length);
					var result=json.readTree(response.result()); var transactions=historyRows(response.result());
					if(!transactions.isArray() || transactions.size()>100) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
					boolean reached=false;
					for(var row:transactions) {
						time();
						var signature=row.path("transaction").path("signatures").path(0).asText(); var slot=row.path("slot"); CaptureBudget.signature(signature); recoveryIdentities++;
						if(!slot.isIntegralNumber() || slot.longValue()<0 || slot.longValue()>previous || !seen.add(signature)) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
						previous=slot.longValue();
						recoveryUpper.putIfAbsent(pool.address(),java.util.Map.of("slot",slot.longValue(),"signature",signature));
						if(signature.equals(boundary.get())) { if(gap.fromSlot()==null || slot.longValue()!=gap.fromSlot()) throw new IllegalStateException("RECOVERY_BOUNDARY_CONFLICT"); reached=true; break; }
						var prior=ledger.dispositions(c.sessionId()).stream().anyMatch(d->d.pool().equals(pool.address()) && d.signature().equals(signature) && d.rawDigest()!=null);
						var status=transaction(pool.address(),signature,slot.longValue(),response.envelope(),response.receivedAt());
						if(java.util.Set.of("CAPTURED","FAILED_TRANSACTION").contains(status)) { if(prior) recoveryDuplicates++; else recoveryNewlyDurable++; }
						if(!java.util.Set.of("CAPTURED","FAILED_TRANSACTION").contains(status)) { if(java.util.Set.of("UNAVAILABLE","UNSUPPORTED_TRANSACTION_VERSION").contains(status)) continuity=false; else throw new IllegalStateException("RECOVERY_EVIDENCE_FAILURE"); }
					}
					if(reached) { if(continuity) onWriter(()->ledger.recoveredGap(c.sessionId(),holder,gap)); summary(continuity?"RECOVERED_TRANSACTIONS":"PARTIAL_UNAVAILABLE_OR_UNSUPPORTED"); break; }
					var next=result.path("paginationToken").asText("");
					if(next.isBlank() || next.equals(cursor) || !next.matches("[A-Za-z0-9_:-]{1,512}")) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
					if(page+1==c.limits().recoveryPages()) throw new IllegalStateException("RECOVERY_PAGE_LIMIT");
					cursor=next;
				}
				if(episodePages.getOrDefault(pool.address(),0)>=c.limits().recoveryPages() && ledger.gaps(c.sessionId()).stream().anyMatch(g->g.id()==gap.id() && g.status().equals("UNRESOLVED_TRANSACTION_GAP")) && continuity) throw new IllegalStateException("RECOVERY_PAGE_LIMIT");
			}
		}
		private void summary(String result) {
			var status=ledger.status(c.sessionId()); var counts=new java.util.TreeMap<String,Long>(); var positions=new java.util.TreeMap<String,Object>();
			for(var d:ledger.dispositions(c.sessionId())) counts.merge(d.status(),1L,Long::sum);
			for(var p:c.pools()) { var boundary=ledger.boundary(c.sessionId(),p.address()); var d=ledger.dispositions(c.sessionId()).stream().filter(v->boundary.isPresent() && v.pool().equals(p.address()) && v.signature().equals(boundary.get())).findFirst(); positions.put(p.address(),d.<Object>map(v->java.util.Map.of("signature",v.signature(),"slot",v.slot())).orElse("UNKNOWN")); }
			var fields=new java.util.LinkedHashMap<String,Object>(); fields.put("version","capture-summary-v2"); fields.put("session",c.sessionId().toString()); fields.put("result",result); fields.put("at",ledger.now().toString()); fields.put("durableDispositions",counts); fields.put("committedPositions",positions);
			fields.put("durable",java.util.Map.of("requests",status.requests(),"received",status.receivedBytes(),"wss",status.subscriptionBytes(),"retained",status.retainedBytes(),"rpcHundredths",status.rpcHundredths(),"reservedHundredths",status.reservedHundredths()));
			synchronized(local) { fields.put("localUnflushed",java.util.Map.of("requests",requests-writtenRequests,"received",received-writtenReceived,"wss",subscription-writtenSubscription,"retained",retained-writtenRetained,"rpcHundredths",rpc-writtenRpc)); }
			fields.put("attempts",reconnectAttempts); fields.put("successes",reconnectSuccesses); fields.put("recoveryPages",recoveryPages); fields.put("returnedIdentities",recoveryIdentities); fields.put("newlyDurable",recoveryNewlyDurable); fields.put("recoveryDuplicates",recoveryDuplicates); fields.put("recoveryUpper",recoveryUpper); fields.put("episodeGaps",episode.stream().map(g->java.util.Map.of("id",g.id(),"pool",g.pool(),"kind",g.kind(),"boundary",g.boundarySignature()==null?"UNKNOWN":g.boundarySignature())).toList()); fields.put("outsideEvidenceDelivery","UNKNOWN");
			var text=json.writeValueAsString(fields); var bytes=text.getBytes(StandardCharsets.UTF_8).length; if(bytes>8192) throw new IllegalStateException("CAPTURE_SUMMARY_BYTE_LIMIT");
			if(Thread.currentThread()==databaseWriter) { retained(bytes); flush("CAPTURE_SUMMARY",text,ledger.now()); } else evidence("CAPTURE_SUMMARY",text);
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
			for(var d:ledger.dispositions(c.sessionId())) if("PENDING".equals(d.status())) {
				time(); transaction(d.pool(),d.signature(),d.slot(),d.notification(),d.receivedAt());
			}
		}
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
						if(!episode.isEmpty()) { recovering=true; retryPending(); recover(); recovering=false; summary("RECOVERY_COMPLETE_WITH_EXPLICIT_GAPS"); episode=java.util.List.of(); episodePages.clear(); recoveryDeadline=null; }
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
				try { if(!writerClosing && !writerFailed) try { summary("TERMINAL_"+terminal); } catch(RuntimeException unavailable) { /* Unknown tail remains under reservation. */ } terminal(terminal); coordinatorDone=true; releaseActive(); }
				finally { if(interrupted) Thread.currentThread().interrupt(); }
			}
		}
		private void terminal(String reason) {
			interruption.compareAndSet(null,reason); stopped=true; admitting=false; discardWebsocketMessage(); var current=transport; if(current!=null) current.close();
			if(!writerClosing) { drainDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(c.limits().timeoutSeconds()+1L); writerClosing=true; }
			if(databaseWriter!=null && Thread.currentThread()!=databaseWriter) try { databaseWriter.join(java.time.Duration.ofSeconds(c.limits().timeoutSeconds()+1L)); }
			catch(InterruptedException failure) { Thread.currentThread().interrupt(); }
		}
	}
}
