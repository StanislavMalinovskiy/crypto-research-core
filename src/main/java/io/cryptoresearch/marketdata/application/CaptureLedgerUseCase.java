package io.cryptoresearch.marketdata.application;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;

@Service
public class CaptureLedgerUseCase {
	private final JdbcCaptureStore store;
	private final Clock clock;
	private final org.springframework.transaction.PlatformTransactionManager transactionManager;
	public CaptureLedgerUseCase(JdbcCaptureStore store, Clock clock,org.springframework.transaction.PlatformTransactionManager transactionManager) { this.store=store; this.clock=clock; this.transactionManager=transactionManager; }
	public Configuration configuration(UUID session) { return store.configuration(session); }
	public Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
	@Transactional public boolean open(Configuration configuration, String holder) {
		var problem = CaptureBudget.launchProblem(configuration);
		if (!problem.isEmpty()) throw new IllegalArgumentException(problem);
		var c = canonical(configuration);
		if (store.exists(c.sessionId())) {
			if (!store.configuration(c.sessionId()).equals(c)) throw new IllegalArgumentException("CAPTURE_CONFIGURATION_CONFLICT");
			var claimed = claim(c.sessionId(),holder);
			if (claimed) interrupt(c.sessionId(),holder,"RESTART_UNFINISHED_SESSION");
			return claimed;
		}
		if (now().isBefore(c.startsAt()) || !now().isBefore(end(c))) throw new IllegalArgumentException("CAPTURE_TIME_LIMIT");
		var reservation = CaptureBudget.reservation(c);
		store.period(c,CaptureBudget.ceiling(c),Math.addExact(c.authority().openingHundredths(),c.authority().otherActivityHundredths()));
		if (!store.reservePeriod(c,reservation)) throw new IllegalStateException("ACCOUNT_PERIOD_RESERVATION_REJECTED");
		store.insertSession(c,fingerprint(c),holder,now().plusSeconds(c.limits().leaseSeconds()),end(c),reservation);
		return true;
	}
	private Configuration canonical(Configuration c) {
		return new Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt().truncatedTo(ChronoUnit.MICROS),
				c.pools().stream().sorted(Comparator.comparing(Pool::address)).toList(),c.limits(),c.authority());
	}
	public String fingerprint(Configuration c) {
		return new CanonicalFingerprint().field("contract","pumpswap-capture-configuration-v1")
				.field("configuration",store.encode(canonical(c))).finish();
	}
	public static Instant end(Configuration c) {
		var window=c.startsAt().plusSeconds(c.limits().windowSeconds());
		return window.isBefore(c.authority().periodEnd()) ? window : c.authority().periodEnd();
	}
	@Transactional(timeout=10) public boolean claim(UUID session, String holder) {
		var c=configuration(session); return store.claim(session,holder,now(),now().plusSeconds(c.limits().leaseSeconds()));
	}
	private void fence(UUID session,String holder) {
		store.fence(session,holder,now(),now().plusSeconds(configuration(session).limits().leaseSeconds()));
	}
	public long reserved(String period) { return store.reserved(period); }
	/** One writer already holds the immutable configuration acquired before provider work. */
	@Transactional(timeout=10) public boolean captureWrite(Configuration c,String holder,long requests,long received,long subscription,long retained,long rpc,String kind,String text,Instant receivedAt) {
		return captureWrite(c,holder,requests,received,subscription,retained,rpc,kind,text,receivedAt,false,()->{});
	}
	@Transactional(timeout=10) public boolean captureWrite(Configuration c,String holder,long requests,long received,long subscription,long retained,long rpc,String kind,String text,Instant receivedAt,boolean closing,Runnable guard) {
		guard.run();
		if(requests<0 || received<0 || subscription<0 || retained<0 || rpc<0) throw new IllegalArgumentException("NEGATIVE_CAPTURE_ACCOUNTING");
		var fits=closing?store.captureAdvance(c,holder,now(),requests,received,subscription,retained,rpc,true):store.captureAdvance(c,holder,now(),requests,received,subscription,retained,rpc);
		if(text!=null) store.envelope(c.sessionId(),kind,PayloadFingerprint.sha256(text),text,receivedAt.truncatedTo(ChronoUnit.MICROS));
		guard.run();
		return fits;
	}
	@Transactional(timeout=10) public boolean captureAccount(Configuration c,String holder,long requests,long received,long subscription,long retained,long rpc,String account,long slot,String payload,String envelope,Instant at) {
		return captureAccount(c,holder,requests,received,subscription,retained,rpc,account,slot,payload,envelope,at,false,()->{});
	}
	@Transactional(timeout=10) public boolean captureAccount(Configuration c,String holder,long requests,long received,long subscription,long retained,long rpc,String account,long slot,String payload,String envelope,Instant at,boolean closing,Runnable guard) {
		if(!captureWrite(c,holder,requests,received,subscription,retained,rpc,null,null,null,closing,guard)) return false;
		if(slot<0 || c.pools().stream().flatMap(p->p.accounts().stream()).noneMatch(account::equals)) throw new IllegalArgumentException("UNKNOWN_ACCOUNT_OR_SLOT");
		var data=tools.jackson.databind.json.JsonMapper.builder().build().readTree(payload).path("data");
		var encoding=data.isArray() && data.size()==2?data.get(1).asText():data.path("program").asText("UNKNOWN");
		store.state(c.sessionId(),c,account,slot,payload,envelope,PayloadFingerprint.sha256(payload),encoding,at.truncatedTo(ChronoUnit.MICROS),now()); guard.run(); return true;
	}
	@Transactional
	public void pending(UUID session, String holder, String pool, String signature, long slot,
			String notification) {
		pendingAt(session,holder,pool,signature,slot,notification,now());
	}
	@Transactional(timeout=10) public void pendingAt(UUID session,String holder,String pool,String signature,long slot,String notification,Instant received) {
		fence(session,holder); CaptureBudget.signature(signature);
		if (slot<0 || configuration(session).pools().stream().noneMatch(p -> p.address().equals(pool))) throw new IllegalArgumentException("UNKNOWN_POOL_OR_SLOT");
		store.pending(session,pool,signature,slot,notification,received.truncatedTo(ChronoUnit.MICROS));
	}
	@Transactional(timeout=10) public void finish(UUID session,String holder,String pool,String signature,String status,String digest,String envelope,boolean duplicate) {
		fence(session,holder); store.finish(session,pool,signature,status,digest,envelope,now(),duplicate);
		if (!java.util.Set.of("CAPTURED","FAILED_TRANSACTION").contains(status)) {
			store.gap(session,pool,"TRANSACTION",status,now());
		}
	}
	@Transactional public void interrupt(UUID session, String holder, String reason) {
		fence(session,holder);
		for (var p:configuration(session).pools()) { store.gap(session,p.address(),"TRANSACTION",reason,now()); store.gap(session,p.address(),"STATE",reason,now()); }
	}
	@Transactional(timeout=10) public void unsupportedTransactionVersion(UUID session,String holder,String pool,String signature,String envelope,String diagnostic,Instant observed) {
		fence(session,holder);
		store.finish(session,pool,signature,"FETCH_FAILURE",null,envelope,now(),false);
		store.gap(session,pool,"TRANSACTION","UNSUPPORTED_TRANSACTION_VERSION",observed);
		store.envelope(session,"CAPTURE_DIAGNOSTIC",PayloadFingerprint.sha256(diagnostic),diagnostic,observed);
	}
	@Transactional public void recovered(UUID session, String holder, String pool, String boundary) {
		fence(session,holder); store.recovered(session,pool,boundary,now());
	}
	@Transactional(timeout=10) public List<Gap> episode(UUID session,String holder,String reason,Instant observed) {
		fence(session,holder); var before=store.gaps(session).stream().mapToLong(Gap::id).max().orElse(0);
		for(var p:configuration(session).pools()) { store.gap(session,p.address(),"TRANSACTION",reason,observed); store.gap(session,p.address(),"STATE",reason,observed); }
		return store.gaps(session).stream().filter(g->g.id()>before).toList();
	}
	@Transactional(timeout=10) public void recoveredGap(UUID session,String holder,Gap gap) {
		fence(session,holder); store.recoveredGap(session,gap.id(),gap.boundarySignature(),now());
	}
	@Transactional(timeout=10) public void recoveredGap(Configuration c,String holder,Gap gap,Runnable guard) {
		guard.run(); store.fence(c.sessionId(),holder,now(),now().plusSeconds(c.limits().leaseSeconds())); guard.run();
		store.recoveredGap(c.sessionId(),gap.id(),gap.boundarySignature(),now()); guard.run();
	}
	@Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,timeout=10)
	public java.util.Map<String,Object> summarySnapshot(Configuration c) { return store.summarySnapshot(c.sessionId()); }
	/** A closing transaction cannot start unless its complete existing operation bound fits the remaining drain. */
	public void closingWork(Configuration c,String holder,long deadline,Runnable work,Runnable guard) {
		guard.run(); var remaining=java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(deadline-System.nanoTime());
		if(remaining<1) throw new IllegalStateException("CAPTURE_WRITER_DRAIN_TIMEOUT");
		var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
		transaction.setTimeout((int)Math.min(c.limits().timeoutSeconds(),remaining));
		transaction.executeWithoutResult(status->{ guard.run(); store.fence(c.sessionId(),holder,now(),now().plusSeconds(c.limits().leaseSeconds())); guard.run(); work.run(); guard.run(); });
	}
	public void close(Configuration c,String holder,String reason,long deadline,String receipt,Runnable guard) {
		closingWork(c,holder,deadline,()->{
			store.terminalGaps(c.sessionId(),reason,now()); guard.run();
			if(receipt!=null) store.envelope(c.sessionId(),"CAPTURE_TERMINAL",PayloadFingerprint.sha256(receipt),receipt,now());
			guard.run(); store.stop(c.sessionId(),reason); guard.run();
		},guard);
	}
	public void closingTail(Configuration c,String holder,long deadline,List<io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.Notification> tail,long requests,long received,long subscription,long retained,long rpc,Runnable guard) {
		if(tail.size()>c.limits().queueItems()) throw new IllegalStateException("QUEUE_OVERFLOW");
		var rows=new java.util.ArrayList<java.util.Map<String,Object>>(); var equal=new java.util.HashMap<String,String>(); long bytes=0; var ordinal=0;
		for(var n:tail) {
			guard.run(); bytes=Math.addExact(bytes,n.envelope().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+(long)n.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length); if(bytes>c.limits().queueBytes()) throw new IllegalStateException("QUEUE_OVERFLOW");
			if(n.slot()<0 || c.pools().stream().noneMatch(p->p.address().equals(n.pool()))) throw new IllegalArgumentException("UNKNOWN_POOL_OR_SLOT");
			var row=new java.util.LinkedHashMap<String,Object>(); row.put("ord",ordinal++); row.put("pool",n.pool()); row.put("account",n.account()); row.put("sig",n.signature()); row.put("slot",n.slot()); row.put("data",n.payload()); row.put("envelope",n.envelope()); row.put("received",n.receivedAt().truncatedTo(ChronoUnit.MICROS).toString());
			if(n.account()==null) CaptureBudget.signature(n.signature());
			else {
				if(c.pools().stream().flatMap(p->p.accounts().stream()).noneMatch(n.account()::equals)) throw new IllegalArgumentException("UNKNOWN_ACCOUNT_OR_SLOT"); var digest=PayloadFingerprint.sha256(n.payload()); var key=n.account()+":"+n.slot()+":"+digest; var prior=equal.putIfAbsent(key,n.payload()); if(prior!=null && !prior.equals(n.payload())) throw new IllegalStateException("ACCOUNT_CONTENT_CONFLICT"); var data=tools.jackson.databind.json.JsonMapper.builder().build().readTree(n.payload()).path("data"); row.put("digest",digest); row.put("encoding",data.isArray() && data.size()==2?data.get(1).asText():data.path("program").asText("UNKNOWN"));
			}
			rows.add(row);
		}
		closingWork(c,holder,deadline,()->{ if(requests<0 || received<0 || subscription<0 || retained<0 || rpc<0) throw new IllegalArgumentException("NEGATIVE_CAPTURE_ACCOUNTING"); if(!store.captureAdvance(c,holder,now(),requests,received,subscription,retained,rpc,true)) throw new IllegalStateException("CAPTURE_RESOURCE_LIMIT"); guard.run(); store.tail(c.sessionId(),c,rows,now(),guard); },guard);
	}
	public java.util.Optional<String> boundary(UUID session,String pool) { return store.boundary(session,pool); }
	public List<Gap> gaps(UUID session) { return store.gaps(session); }
	public List<Disposition> dispositions(UUID session) { return store.dispositions(session); }
	public List<Disposition> pending(Configuration c) { return store.pending(c.sessionId(),Math.min(100,c.limits().queueItems()),c.limits().queueBytes()); }
	public java.util.Set<String> successfulPage(Configuration c,String pool,List<String> signatures) { return store.successfulPage(c.sessionId(),pool,signatures); }
	public record HistoryPage(UUID session,String pool,String cursor,Envelope envelope,List<String> rows,String context) { }
	private HistoryPage verifiedPage(Configuration c,String context) {
		return verifiedPage(c,context,null);
	}
	private HistoryPage verifiedPage(Configuration c,String context,HistoryPage cached) {
		var n=tools.jackson.databind.json.JsonMapper.builder().build().readTree(context);
		if(!"capture-history-context-v1".equals(n.path("version").asText()) || !c.sessionId().toString().equals(n.path("session").asText()) || !"getTransactionsForAddress".equals(n.path("method").asText()) || c.pools().stream().noneMatch(p->p.address().equals(n.path("pool").asText()))) throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
		var cursor=n.path("cursor").asText(); if(!n.path("cursor").isString() || !cursor.isEmpty() && !cursor.matches("[A-Za-z0-9_:-]{1,512}")) throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
		var page=cached==null?store.historyPage(c.sessionId(),n.path("pageDigest").asText()).orElseThrow(()->new IllegalStateException("RECOVERY_PROVENANCE_MISSING")):cached.envelope();
		if(cached!=null && (!cached.session().equals(c.sessionId()) || !page.digest().equals(n.path("pageDigest").asText()))) throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
		if(!PayloadFingerprint.sha256(page.payload()).equals(page.digest()) || !page.receivedAt().toString().equals(n.path("receivedAt").asText())) throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
		var rows=cached==null?io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.exactElements(page.payload(),"/result/data"):cached.rows(); if(rows.size()>100) throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
		return new HistoryPage(c.sessionId(),n.path("pool").asText(),cursor,page,rows,context);
	}
	@Transactional(timeout=10) public HistoryPage historyPage(Configuration c,String holder,String context) {
		store.fence(c.sessionId(),holder,now(),now().plusSeconds(c.limits().leaseSeconds())); var page=verifiedPage(c,context); store.envelope(c.sessionId(),"CAPTURE_HISTORY_CONTEXT",PayloadFingerprint.sha256(context),context,page.envelope().receivedAt()); return page;
	}
	@Transactional(readOnly=true,timeout=10) public HistoryPage historyPage(Configuration c,String reference) {
		return historyPage(c,reference,(HistoryPage)null);
	}
	@Transactional(readOnly=true,timeout=10) public HistoryPage historyPage(Configuration c,String reference,HistoryPage cached) {
		var ref=tools.jackson.databind.json.JsonMapper.builder().build().readTree(reference); if(!ref.path("cursor").isString()) throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID"); return verifiedPage(c,store.historyContext(c.sessionId(),ref.path("pageDigest").asText(),ref.path("pool").asText(),ref.path("cursor").asText()),cached);
	}
	@Transactional(readOnly=true,timeout=10) public Instant historyPageReceivedAt(Configuration c,String digest) { return store.historyPageReceivedAt(c.sessionId(),digest); }
	public void verifyHistoryReference(Configuration c,HistoryPage page,String pool,String signature,long slot,String reference,Instant received) {
		try {
			var ref=tools.jackson.databind.json.JsonMapper.builder().build().readTree(reference); var index=ref.path("rowIndex");
			if(!ref.path("cursor").isString()) throw new IllegalArgumentException();
			if(!"capture-history-row-v1".equals(ref.path("version").asText()) || !index.isIntegralNumber() || index.longValue()<0 || index.longValue()>=page.rows().size() || !page.session().equals(c.sessionId()) || !c.sessionId().toString().equals(ref.path("session").asText()) || !page.pool().equals(pool) || !pool.equals(ref.path("pool").asText()) || !"getTransactionsForAddress".equals(ref.path("method").asText()) || !page.cursor().equals(ref.path("cursor").asText()) || !page.envelope().digest().equals(ref.path("pageDigest").asText()) || !page.envelope().receivedAt().equals(received.truncatedTo(ChronoUnit.MICROS)) || !page.envelope().receivedAt().toString().equals(ref.path("receivedAt").asText()) || !signature.equals(ref.path("signature").asText()) || !ref.path("slot").isIntegralNumber() || ref.path("slot").longValue()!=slot) throw new IllegalArgumentException();
			var row=page.rows().get(index.intValue()); var n=tools.jackson.databind.json.JsonMapper.builder().build().readTree(row);
			if(!PayloadFingerprint.sha256(row).equals(ref.path("rowDigest").asText()) || !signature.equals(n.path("transaction").path("signatures").path(0).asText()) || !n.path("slot").isIntegralNumber() || n.path("slot").longValue()!=slot) throw new IllegalArgumentException();
		} catch(RuntimeException failure) { throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID"); }
	}
	@Transactional(timeout=10) public void pendingHistory(Configuration c,String holder,String pool,String signature,long slot,String reference,Instant received,HistoryPage page) {
		verifyHistoryReference(c,page,pool,signature,slot,reference,received); CaptureBudget.signature(signature); if(slot<0) throw new IllegalArgumentException("UNKNOWN_POOL_OR_SLOT"); store.fence(c.sessionId(),holder,now(),now().plusSeconds(c.limits().leaseSeconds())); store.pending(c.sessionId(),pool,signature,slot,reference,received.truncatedTo(ChronoUnit.MICROS));
	}
	public List<AccountSnapshot> states(UUID session) { return store.states(session); }
	@Transactional public void state(UUID session, String holder, String account, long slot, String payload, String envelope) {
		stateAt(session,holder,account,slot,payload,envelope,now());
	}
	@Transactional(timeout=10) public void stateAt(UUID session,String holder,String account,long slot,String payload,String envelope,Instant received) {
		fence(session,holder);
		var c=configuration(session);
		if(slot<0 || c.pools().stream().flatMap(p->p.accounts().stream()).noneMatch(account::equals)) throw new IllegalArgumentException("UNKNOWN_ACCOUNT_OR_SLOT");
		var node=tools.jackson.databind.json.JsonMapper.builder().build().readTree(payload);
		var data=node.path("data");
		var encoding=data.isArray() && data.size()==2 ? data.get(1).asText() : data.path("program").asText("UNKNOWN");
		store.state(session,c,account,slot,payload,envelope,PayloadFingerprint.sha256(payload),encoding,received.truncatedTo(ChronoUnit.MICROS),now());
	}
	@Transactional public boolean consume(UUID session,String holder,long requests,long received,long subscription,long retained,long rpc) {
		if(requests<0 || received<0 || subscription<0 || retained<0 || rpc<0) throw new IllegalArgumentException("NEGATIVE_CAPTURE_ACCOUNTING");
		fence(session,holder);
		var c=configuration(session);
		if(store.consume(session,requests,received,subscription,retained,rpc,c.limits(),now())) return true;
		// Delivered bytes are already a billed fact even when admission to more work fails.
		store.observe(session,holder,received,subscription);
		var status=store.status(session);
		var reason=status.subscriptionBytes()>c.limits().subscriptionBytes()?"SUBSCRIPTION_BYTE_LIMIT":
				status.receivedBytes()>c.limits().receivedBytes()?"RECEIVED_BYTE_LIMIT":
				!now().isBefore(end(c))?"CAPTURE_TIME_LIMIT":requests>c.limits().requests()-status.requests()?"REQUEST_LIMIT":
				rpc>c.limits().rpcHundredths()-status.rpcHundredths()?"RPC_CU_LIMIT":store.accountExhausted(session)?"ACCOUNT_COST_LIMIT":"RETAINED_BYTE_LIMIT";
		for(var pool:c.pools()) { store.gap(session,pool.address(),"TRANSACTION",reason,now()); store.gap(session,pool.address(),"STATE",reason,now()); }
		store.stop(session,reason); return false;
	}
	@Transactional public boolean received(UUID session,String holder,long bytes,boolean subscription) {
		if(bytes<0) throw new IllegalArgumentException("NEGATIVE_CAPTURE_ACCOUNTING");
		store.observe(session,holder,bytes,subscription?bytes:0);
		var c=configuration(session); var status=store.status(session);
		var reason=status.subscriptionBytes()>c.limits().subscriptionBytes()?"SUBSCRIPTION_BYTE_LIMIT":
				status.receivedBytes()>c.limits().receivedBytes()?"RECEIVED_BYTE_LIMIT":null;
		if(reason!=null && "RUNNING".equals(status.state())) {
			for(var p:c.pools()) { store.gap(session,p.address(),"TRANSACTION",reason,now()); store.gap(session,p.address(),"STATE",reason,now()); }
			store.stop(session,reason);
		}
		return "RUNNING".equals(store.status(session).state());
	}
	@Transactional(timeout=10) public void stop(UUID session,String holder,String reason) {
		fence(session,holder); interrupt(session,holder,reason); store.stop(session,reason);
	}
	@org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,timeout=10)
	public Status status(UUID session) { return store.status(session); }
	@Transactional public void envelope(UUID session,String holder,String kind,String text) {
		envelopeAt(session,holder,kind,text,now());
	}
	@Transactional public void envelopeAt(UUID session,String holder,String kind,String text,Instant received) {
		fence(session,holder); store.envelope(session,kind,PayloadFingerprint.sha256(text),text,received.truncatedTo(ChronoUnit.MICROS));
	}
	public Replay replay(UUID session) { return new Replay(configuration(session),dispositions(session),states(session),store.envelopes(session),status(session)); }
}
