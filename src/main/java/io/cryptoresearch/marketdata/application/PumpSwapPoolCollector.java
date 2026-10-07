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
	static JsonNode historyRows(String result) { return tools.jackson.databind.json.JsonMapper.builder().build().readTree(result).path("data"); }
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
		private final Object writer=new Object();
		private record Queued(Notification notification,java.time.Instant receivedAt) { }
		private final ArrayBlockingQueue<Queued> queue;
		private final AtomicLong queuedBytes=new AtomicLong();
		private final AtomicReference<String> interruption=new AtomicReference<>();
		private volatile JdkPumpSwapTransport transport;
		private volatile boolean stopped;
		private Thread worker;
		private java.time.Instant lastRequest;
		private volatile java.time.Instant recoveryDeadline;
		Run(Configuration c,String holder) { this.c=c; this.holder=holder; queue=new ArrayBlockingQueue<>(c.limits().queueItems()); }
		private void time() {
			if(stopped || !ledger.now().isBefore(CaptureLedgerUseCase.end(c))) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
			var deadline=recoveryDeadline;
			if(deadline!=null && !ledger.now().isBefore(deadline)) throw new IllegalStateException("RECOVERY_TIME_LIMIT");
		}
		public void start(long cu) {
			time();
			if(cu>0) {
				waitSeconds(c.limits().timeoutSeconds());
				var status=ledger.status(c.sessionId());
				if(status.receivedBytes()>c.limits().receivedBytes()-c.limits().responseBytes()) throw new IllegalStateException("HTTP_RECEIVED_BYTE_HEADROOM_LIMIT");
				if(status.retainedBytes()>c.limits().retainedBytes()-2L*c.limits().responseBytes()) throw new IllegalStateException("HTTP_RETAINED_BYTE_HEADROOM_LIMIT");
			}
			if(lastRequest!=null) {
				var next=lastRequest.plusNanos(1000000000L/c.limits().requestsPerSecond());
				if(!next.isBefore(CaptureLedgerUseCase.end(c))) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
				var wait=java.time.Duration.between(ledger.now(),next).toNanos();
				if(wait>0) try { TimeUnit.NANOSECONDS.sleep(wait); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
			}
			synchronized(writer) {
				time(); if(cu>0) waitSeconds(c.limits().timeoutSeconds());
				if(!ledger.consume(c.sessionId(),holder,1,0,0,0,cu)) throw new IllegalStateException("CAPTURE_RESOURCE_LIMIT");
				lastRequest=ledger.now();
			}
		}
		public void waitSeconds(long seconds) {
			if(seconds<0 || !ledger.now().plusSeconds(seconds).isBefore(CaptureLedgerUseCase.end(c))) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
			var deadline=recoveryDeadline;
			if(deadline!=null && !ledger.now().plusSeconds(seconds).isBefore(deadline)) throw new IllegalStateException("RECOVERY_TIME_LIMIT");
		}
		public void received(long bytes,boolean subscription) {
			synchronized(writer) {
				if(!ledger.received(c.sessionId(),holder,bytes,subscription)) throw new IllegalStateException("CAPTURE_RESOURCE_LIMIT");
			}
		}
		public void nextMessage() {
			synchronized(writer) {
				time(); var status=ledger.status(c.sessionId());
				// Hold one full-message and close/control headroom before asking the JDK for another fragment.
				var headroom=Math.addExact(c.limits().messageBytes(),256L);
				if(status.subscriptionBytes()>c.limits().subscriptionBytes()-headroom || status.receivedBytes()>c.limits().receivedBytes()-headroom)
					throw new IllegalStateException("SUBSCRIPTION_BYTE_HEADROOM_LIMIT");
			}
		}
		public void evidence(String kind,String text) {
			evidence(kind,text,ledger.now());
		}
		public java.time.Instant now() { return ledger.now(); }
		public void evidence(String kind,String text,java.time.Instant receivedAt) {
			retained(text.getBytes(StandardCharsets.UTF_8).length);
			synchronized(writer) { ledger.envelopeAt(c.sessionId(),holder,kind,text,receivedAt); }
		}
		private long size(Notification n) { return n.envelope().getBytes(StandardCharsets.UTF_8).length; }
		private void enqueue(Notification n) {
			var bytes=size(n); var total=queuedBytes.addAndGet(bytes);
			if(stopped || total>c.limits().queueBytes() || !queue.offer(new Queued(n,n.receivedAt()))) {
				queuedBytes.addAndGet(-bytes); interrupted("QUEUE_OVERFLOW");
				var current=transport; if(current!=null) current.close();
			}
		}
		private void interrupted(String reason) {
			if(interruption.compareAndSet(null,reason)) synchronized(writer) {
				if(!stopped && "RUNNING".equals(ledger.status(c.sessionId()).state())) ledger.interrupt(c.sessionId(),holder,reason);
			}
		}
		private void retained(long bytes) {
			synchronized(writer) { if(!ledger.consume(c.sessionId(),holder,0,0,0,bytes,0)) throw new IllegalStateException("RETAINED_BYTE_LIMIT"); }
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
					synchronized(writer) { ledger.stateAt(c.sessionId(),holder,account,slot.longValue(),payload,response.envelope(),response.receivedAt()); }
				}
			}
		}
		private String transaction(String pool,String signature,long slot,String envelope,java.time.Instant received) {
			CaptureBudget.signature(signature);
			retained(envelope.getBytes(StandardCharsets.UTF_8).length);
			synchronized(writer) { ledger.pendingAt(c.sessionId(),holder,pool,signature,slot,envelope,received); }
			JdkPumpSwapTransport.Response response;
			try { response=transport.rpc("getTransaction","[\""+signature+"\",{\"commitment\":\"finalized\",\"encoding\":\"json\",\"maxSupportedTransactionVersion\":0}]"); }
			catch(RuntimeException failure) {
				var reason=JdkPumpSwapTransport.safeReason(failure);
				if(reason.startsWith("PROVIDER_") || reason.startsWith("MALFORMED_") || reason.equals("HTTP_RESPONSE_BYTE_LIMIT")) synchronized(writer) {
					if("RUNNING".equals(ledger.status(c.sessionId()).state())) ledger.finish(c.sessionId(),holder,pool,signature,"FETCH_FAILURE",null,"{\"clientFailure\":\""+reason+"\"}",false);
				}
				throw failure;
			}
			time(); retained(response.envelope().getBytes(StandardCharsets.UTF_8).length+(long)response.result().getBytes(StandardCharsets.UTF_8).length);
			synchronized(writer) { return evidence.transaction(c.sessionId(),holder,pool,signature,response.result(),response.envelope(),response.receivedAt(),true); }
		}
		private void process(Queued queued) {
			var n=queued.notification();
			if(n.account()==null) transaction(n.pool(),n.signature(),n.slot(),n.envelope(),queued.receivedAt());
			else {
				retained(size(n)+(long)n.payload().getBytes(StandardCharsets.UTF_8).length);
				synchronized(writer) { ledger.stateAt(c.sessionId(),holder,n.account(),n.slot(),n.payload(),n.envelope(),queued.receivedAt()); }
			}
		}
		private void recover() {
			var began=ledger.now();
			recoveryDeadline=began.plusSeconds(c.limits().recoverySeconds());
			try {
			for(var pool:c.pools()) {
				var boundary=ledger.gaps(c.sessionId()).stream().filter(g->g.pool().equals(pool.address()) && g.kind().equals("TRANSACTION")
						&& g.status().equals("UNRESOLVED_TRANSACTION_GAP")).map(Gap::boundarySignature).filter(java.util.Objects::nonNull).findFirst();
				if(boundary.isEmpty()) continue;
				String cursor=null; boolean continuity=true; long previous=Long.MAX_VALUE; var seen=new java.util.HashSet<String>();
				for(var page=0;page<c.limits().recoveryPages();page++) {
					if(!ledger.now().isBefore(began.plusSeconds(c.limits().recoverySeconds()))) throw new IllegalStateException("RECOVERY_TIME_LIMIT");
					var params="[\""+pool.address()+"\",{\"commitment\":\"finalized\",\"transactionDetails\":\"full\",\"encoding\":\"json\",\"maxSupportedTransactionVersion\":0,\"sortOrder\":\"desc\",\"filters\":{\"status\":\"any\"},\"limit\":100"
							+(cursor==null?"":",\"paginationToken\":\""+cursor+"\"")+"}]";
					var response=transport.rpc("getTransactionsForAddress",params); retained(response.envelope().getBytes(StandardCharsets.UTF_8).length);
					var result=json.readTree(response.result()); var transactions=historyRows(response.result());
					if(!transactions.isArray() || transactions.size()>100) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
					boolean reached=false;
					for(var row:transactions) {
						time();
						var signature=row.path("transaction").path("signatures").path(0).asText(); var slot=row.path("slot");
						if(!slot.isIntegralNumber() || slot.longValue()>previous || !seen.add(signature)) { continuity=false; break; }
						previous=slot.longValue();
						if(signature.equals(boundary.get())) { reached=true; break; }
						var status=transaction(pool.address(),signature,slot.longValue(),response.envelope(),ledger.now());
						if(!java.util.Set.of("CAPTURED","FAILED_TRANSACTION").contains(status)) continuity=false;
					}
					if(reached && continuity) { synchronized(writer) { ledger.recovered(c.sessionId(),holder,pool.address(),boundary.get()); } break; }
					var next=result.path("paginationToken").asText("");
					if(!continuity || next.isBlank() || next.equals(cursor) || !next.matches("[A-Za-z0-9_:-]{1,512}")) throw new IllegalStateException("RECOVERY_CONTINUITY_UNPROVED");
					if(page+1==c.limits().recoveryPages()) throw new IllegalStateException("RECOVERY_PAGE_LIMIT");
					cursor=next;
				}
			}
			} finally { recoveryDeadline=null; }
		}
		private void retryPending() {
			for(var d:ledger.dispositions(c.sessionId())) if("PENDING".equals(d.status())) {
				time(); transaction(d.pool(),d.signature(),d.slot(),d.notification(),d.receivedAt());
			}
		}
		private void loop() {
			String terminal="CAPTURE_TIME_LIMIT"; var reconnect=0;
			try {
				while(!stopped) {
					time(); transport=new JdkPumpSwapTransport(settings.getRpc(),settings.getWebsocket(),settings.getSecret(),c.limits(),this);
					verifyAndReadAccounts(); retryPending();
					interruption.set(null); transport.connect(c.pools(),this::enqueue,this::interrupted);
					if(reconnect>0 || !ledger.gaps(c.sessionId()).isEmpty()) recover();
					while(!stopped && interruption.get()==null) {
						time(); synchronized(writer) { if(!ledger.claim(c.sessionId(),holder)) throw new IllegalStateException("CAPTURE_LEASE_LOST"); }
						var notification=queue.poll(1,TimeUnit.SECONDS);
						if(notification!=null) { queuedBytes.addAndGet(-size(notification.notification())); process(notification); }
					}
					transport.close(); if(stopped) break;
					var reason=interruption.get();
					if("QUEUE_OVERFLOW".equals(reason) || reason.endsWith("LIMIT") || reason.contains("SECRET") || reconnect++>=c.limits().reconnects()) { terminal=reason; break; }
					var wait=reconnect==1?1:3; waitSeconds(wait); TimeUnit.SECONDS.sleep(wait);
				}
			} catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); terminal="OWNER_STOP"; }
			catch(RuntimeException failure) { terminal=JdkPumpSwapTransport.safeReason(failure); }
			finally {
				var interrupted=Thread.interrupted();
				try { terminal(terminal); active.compareAndSet(this,null); }
				finally { if(interrupted) Thread.currentThread().interrupt(); }
			}
		}
		private void terminal(String reason) {
			stopped=true; var current=transport; if(current!=null) current.close();
			synchronized(writer) {
				try { if("RUNNING".equals(ledger.status(c.sessionId()).state())) ledger.stop(c.sessionId(),holder,reason); }
				catch(RuntimeException lostLease) { /* Expired lease and pending rows expose an unfinished session. */ }
			}
		}
	}
}
