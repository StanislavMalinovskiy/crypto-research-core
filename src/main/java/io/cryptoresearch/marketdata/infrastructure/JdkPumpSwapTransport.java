package io.cryptoresearch.marketdata.infrastructure;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.ByteArrayOutputStream;
import tools.jackson.databind.json.JsonMapper;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;

/** The JDK transport is private to marketdata; no asynchronous type enters the module API. */
public final class JdkPumpSwapTransport implements AutoCloseable {
	public interface Accounting {
		void start(long rpcHundredths);
		void received(long bytes, boolean subscription);
		void nextMessage();
		default void evidence(String kind,String envelope) { }
		default void waitSeconds(long seconds) { }
		java.time.Instant now();
		default void evidence(String kind,String envelope,java.time.Instant receivedAt) { evidence(kind,envelope); }
	}
	public record Response(String result, String envelope,java.time.Instant receivedAt) { }
	public record Notification(String pool, String account, String signature, long slot, String payload, String envelope,java.time.Instant receivedAt) { }
	public static String exactValue(String text,String path) { return ExactCaptureJson.value(text,path); }
	private final URI rpc;
	private final URI websocket;
	private final String secret;
	private final Limits limits;
	private final Accounting accounting;
	private final HttpClient client;
	private final AtomicLong ids=new AtomicLong();
	private final JsonMapper json=JsonMapper.builder().build();
	private volatile WebSocket socket;
	private volatile boolean closed;
	public JdkPumpSwapTransport(URI rpc,URI websocket,String secret,Limits limits,Accounting accounting) {
		this.rpc=rpc; this.websocket=websocket; this.secret=secret; this.limits=limits; this.accounting=accounting;
		if(secret==null || secret.isBlank()) throw new IllegalArgumentException("MISSING_PROVIDER_CREDENTIAL");
		validateEndpoint(rpc,"http","https"); validateEndpoint(websocket,"ws","wss");
		client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(limits.timeoutSeconds())).followRedirects(HttpClient.Redirect.NEVER).build();
	}
	private static void validateEndpoint(URI uri,String local,String secure) {
		if(uri==null || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null
				|| (!secure.equals(uri.getScheme()) && !(local.equals(uri.getScheme()) && java.util.Set.of("127.0.0.1","localhost","::1").contains(uri.getHost()))))
			throw new IllegalArgumentException("INVALID_PROVIDER_ENDPOINT");
	}
	public synchronized Response rpc(String method,String params) {
		var cu=switch(method) { case "getTransaction" -> 4000L; case "getAccountInfo" -> 1000L; case "getTransactionsForAddress" -> 10000L;
			default -> throw new IllegalArgumentException("UNSUPPORTED_CAPTURE_RPC"); };
		for(var attempt=0;attempt<=limits.retries();attempt++) {
			if(closed) throw new IllegalStateException("CAPTURE_TRANSPORT_CLOSED");
			accounting.start(cu);
			var id=ids.incrementAndGet();
			var request=HttpRequest.newBuilder(rpc).timeout(Duration.ofSeconds(limits.timeoutSeconds()))
					.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(
							"{\"jsonrpc\":\"2.0\",\"id\":"+id+",\"method\":\""+method+"\",\"params\":"+params+"}")).build();
			try {
				var subscriber=new BoundedBody();
				var future=client.sendAsync(request,info->subscriber);
				HttpResponse<byte[]> response;
				try { response=future.get(limits.timeoutSeconds(),TimeUnit.SECONDS); }
				catch(Exception failure) { future.cancel(true); throw failure; }
				var text=decode(response.body()); checkSecret(text);
				accounting.evidence("HTTP_"+method,text,subscriber.receivedAt);
				if((response.statusCode()==429 || response.statusCode()>=500) && attempt<limits.retries()) {
					var retry=response.headers().firstValue("Retry-After").orElse("");
					long wait=attempt==0?1:3;
					if(!retry.isEmpty()) { try { wait=Math.max(wait,Math.min(limits.timeoutSeconds(),Long.parseLong(retry))); }
						catch(NumberFormatException ignored) { wait=limits.timeoutSeconds(); } }
					pause(wait); continue;
				}
				if(response.statusCode()!=200) throw new IllegalStateException("PROVIDER_HTTP_FAILURE");
				var result=ExactCaptureJson.value(text,"/result");
				return new Response(result==null?"null":result,text,subscriber.receivedAt);
			}
			catch(IllegalStateException safe) { throw safe; }
			catch(InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
			catch(Exception failure) {
				if(failure instanceof ExecutionException execution && execution.getCause() instanceof IllegalStateException safe) throw safe;
				if(attempt==limits.retries()) throw new IllegalStateException("PROVIDER_TRANSPORT_FAILURE");
				pause(attempt==0?1:3);
			}
		}
		throw new IllegalStateException("PROVIDER_TRANSPORT_FAILURE");
	}
	private void pause(long seconds) {
		accounting.waitSeconds(seconds);
		try { TimeUnit.SECONDS.sleep(seconds); }
		catch(InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
	}
	private void checkSecret(String value) {
		if(value.contains(secret) || value.contains(java.net.URLEncoder.encode(secret,StandardCharsets.UTF_8))) throw new IllegalStateException("PROVIDER_SECRET_ECHO");
		try { if(json.readTree(value).toString().contains(secret)) throw new IllegalStateException("PROVIDER_SECRET_ECHO"); }
		catch(IllegalStateException secretEcho) { throw secretEcho; }
		catch(RuntimeException invalid) { throw new IllegalStateException("MALFORMED_PROVIDER_JSON"); }
	}
	private String decode(byte[] bytes) {
		try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
		catch(java.nio.charset.CharacterCodingException bad) { throw new IllegalStateException("MALFORMED_PROVIDER_UTF8"); }
	}
	private final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
		private final CompletableFuture<byte[]> body=new CompletableFuture<>();
		private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
		private Flow.Subscription subscription;
		private java.time.Instant receivedAt;
		public CompletionStage<byte[]> getBody() { return body; }
		public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; subscription.request(1); }
		public void onNext(List<ByteBuffer> chunks) {
			try {
				for(var chunk:chunks) {
					var count=chunk.remaining(); accounting.received(count,false);
					if((long)bytes.size()+count>limits.responseBytes()) throw new IllegalStateException("HTTP_RESPONSE_BYTE_LIMIT");
					var buffer=new byte[count]; chunk.get(buffer); bytes.writeBytes(buffer);
				}
				subscription.request(1);
			} catch(RuntimeException stopped) { subscription.cancel(); body.completeExceptionally(stopped); }
		}
		public void onError(Throwable failure) { body.completeExceptionally(new IllegalStateException("PROVIDER_TRANSPORT_FAILURE")); }
		public void onComplete() { receivedAt=accounting.now(); body.complete(bytes.toByteArray()); }
	}
	private record Channel(String pool,String account) { }
	private record PendingSubscription(Channel channel,CompletableFuture<Void> durableAck) { }
	public void connect(List<Pool> pools,Consumer<Notification> notifications,Consumer<String> interrupted) {
		var pending=new ConcurrentHashMap<Long,PendingSubscription>(); var subscriptions=new ConcurrentHashMap<Long,Channel>();
		var failureSignal=new CompletableFuture<Void>();
		var opened=new CompletableFuture<Void>();
		try {
			accounting.waitSeconds(limits.timeoutSeconds());
			var connectDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(limits.timeoutSeconds());
			var connecting=client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(limits.timeoutSeconds()))
					.buildAsync(websocket,new WebSocket.Listener() {
						private final StringBuilder message=new StringBuilder();
						private long messageBytes;
						private java.time.Instant messageReceivedAt;
						public void onOpen(WebSocket ws) {
							try { accounting.nextMessage(); ws.request(1); opened.complete(null); }
							catch(RuntimeException failure) { failureSignal.completeExceptionally(new IllegalStateException(safeReason(failure))); ws.abort(); interrupted.accept(safeReason(failure)); }
						}
						public CompletionStage<?> onText(WebSocket ws,CharSequence fragment,boolean last) {
							try {
								if(messageBytes==0) messageReceivedAt=accounting.now();
								var count=fragment.toString().getBytes(StandardCharsets.UTF_8).length;
								accounting.received(count,true); messageBytes=Math.addExact(messageBytes,count);
								if(messageBytes>limits.messageBytes()) throw new IllegalStateException("WS_MESSAGE_BYTE_LIMIT");
								message.append(fragment);
								if(last) {
									var envelope=message.toString(); message.setLength(0); messageBytes=0; checkSecret(envelope);
									var n=json.readTree(envelope);
									accounting.evidence("WEBSOCKET",envelope,messageReceivedAt);
									if(n.has("id")) {
										var request=pending.remove(n.path("id").longValue());
										if(request==null || !n.path("result").isIntegralNumber() || subscriptions.putIfAbsent(n.path("result").longValue(),request.channel())!=null)
											throw new IllegalStateException("SUBSCRIPTION_REJECTED");
										request.durableAck().complete(null);
									} else {
										var params=n.path("params"); var channel=subscriptions.get(params.path("subscription").longValue());
										if(channel==null) throw new IllegalStateException("UNKNOWN_SUBSCRIPTION");
										var slot=params.path("result").path("context").path("slot");
										if(!slot.isIntegralNumber() || slot.longValue()<0) throw new IllegalStateException("INVALID_NOTIFICATION_SLOT");
										var payload=ExactCaptureJson.value(envelope,"/params/result/value");
										var signature=channel.account()==null?params.path("result").path("value").path("signature").asText():null;
										notifications.accept(new Notification(channel.pool(),channel.account(),signature,slot.longValue(),payload,envelope,messageReceivedAt));
									}
								}
								accounting.nextMessage(); ws.request(1);
							} catch(RuntimeException failure) { failureSignal.completeExceptionally(new IllegalStateException(safeReason(failure))); ws.abort(); interrupted.accept(safeReason(failure)); }
							return CompletableFuture.completedFuture(null);
						}
						public CompletionStage<?> onPing(WebSocket ws,ByteBuffer bytes) {
							try { accounting.received(bytes.remaining(),true); accounting.nextMessage(); ws.request(1); return ws.sendPong(bytes); }
							catch(RuntimeException failure) { failureSignal.completeExceptionally(new IllegalStateException(safeReason(failure))); ws.abort(); interrupted.accept(safeReason(failure)); return CompletableFuture.completedFuture(null); }
						}
						public CompletionStage<?> onPong(WebSocket ws,ByteBuffer bytes) {
							try { accounting.received(bytes.remaining(),true); accounting.nextMessage(); ws.request(1); }
							catch(RuntimeException failure) { failureSignal.completeExceptionally(new IllegalStateException(safeReason(failure))); ws.abort(); interrupted.accept(safeReason(failure)); }
							return CompletableFuture.completedFuture(null);
						}
						public CompletionStage<?> onClose(WebSocket ws,int code,String reason) {
							failureSignal.completeExceptionally(new IllegalStateException("DISCONNECT"));
							accounting.received(reason.getBytes(StandardCharsets.UTF_8).length+2L,true); if(!closed) interrupted.accept("DISCONNECT"); return CompletableFuture.completedFuture(null);
						}
						public void onError(WebSocket ws,Throwable failure) { failureSignal.completeExceptionally(new IllegalStateException("WS_LISTENER_FAILURE")); if(!closed) interrupted.accept("WS_LISTENER_FAILURE"); }
					});
			socket=awaitWebSocket(connecting,"WS_CONNECT",false,connectDeadline-System.nanoTime());
			awaitWebSocket(CompletableFuture.anyOf(opened,failureSignal),"WS_CONNECT",true,connectDeadline-System.nanoTime());
			var accounts=new java.util.HashSet<String>();
			for(var pool:pools) {
				subscribe(pending,failureSignal,new Channel(pool.address(),null),"logsSubscribe","[{\"mentions\":[\""+pool.address()+"\"]},{\"commitment\":\"finalized\"}]");
				for(var account:pool.accounts()) if(accounts.add(account)) subscribe(pending,failureSignal,new Channel(pool.address(),account),"accountSubscribe","[\""+account+"\",{\"commitment\":\"finalized\",\"encoding\":\"base64\"}]");
			}
		} catch(RuntimeException failure) { close(); throw new IllegalStateException(safeReason(failure)); }
	}
	private void subscribe(Map<Long,PendingSubscription> pending,CompletableFuture<Void> failureSignal,Channel channel,String method,String params) {
		if(closed) throw new IllegalStateException("CAPTURE_TRANSPORT_CLOSED");
		accounting.waitSeconds(limits.timeoutSeconds()); accounting.start(0);
		accounting.waitSeconds(limits.timeoutSeconds());
		var id=ids.incrementAndGet(); var ack=new CompletableFuture<Void>(); pending.put(id,new PendingSubscription(channel,ack));
		// Monotonic elapsed time bounds only the transport wait; evidence timestamps use Accounting.now().
		var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(limits.timeoutSeconds());
		CompletableFuture<WebSocket> sending;
		try { sending=socket.sendText("{\"jsonrpc\":\"2.0\",\"id\":"+id+",\"method\":\""+method+"\",\"params\":"+params+"}",true); }
		catch(RuntimeException failure) { throw new IllegalStateException("WS_SEND_FAILURE"); }
		awaitWebSocket(sending,"WS_SEND",false,deadline-System.nanoTime());
		// One subscription is in flight: later requests cannot inherit a backlog of durable ACK writes.
		// Completion still requires the exact envelope to have committed before ACK validation/publication.
		var remaining=deadline-System.nanoTime();
		if(remaining>0) accounting.waitSeconds(TimeUnit.NANOSECONDS.toSeconds(remaining)+1);
		awaitWebSocket(CompletableFuture.anyOf(ack,failureSignal),"WS_ACK",true,remaining);
	}
	private <T> T awaitWebSocket(CompletableFuture<T> future,String phase,boolean internalFailure,long remainingNanos) {
		try { return future.get(Math.max(0,remainingNanos),TimeUnit.NANOSECONDS); }
		catch(TimeoutException failure) { future.cancel(true); throw new IllegalStateException(phase+"_TIMEOUT"); }
		catch(InterruptedException failure) { future.cancel(true); Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
		catch(ExecutionException failure) {
			throw new IllegalStateException(internalFailure?safeReason(failure.getCause()):phase+"_FAILURE");
		}
	}
	public static String safeReason(Throwable failure) {
		var reason=failure.getMessage();
		return reason!=null && reason.matches("[A-Z][A-Z0-9_]{1,80}")?reason:"PROVIDER_TRANSPORT_FAILURE";
	}
	@Override public void close() { closed=true; var ws=socket; if(ws!=null) ws.abort(); client.shutdownNow(); }
}
