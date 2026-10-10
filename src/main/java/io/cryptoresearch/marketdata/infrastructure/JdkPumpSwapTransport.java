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
		default void websocketFragment(long bytes) { received(bytes,true); }
		default void discardWebsocketMessage() { }
		default void websocketHandled(long elapsedNanos) { }
		void nextMessage();
		default void evidence(String kind,String envelope) { }
		default void waitSeconds(long seconds) { }
		default void diagnostic(String text) { evidence("CAPTURE_DIAGNOSTIC",text); }
		default void controlStarted() { start(0); }
		java.time.Instant now();
		default void evidence(String kind,String envelope,java.time.Instant receivedAt) { evidence(kind,envelope); }
		default CompletableFuture<Void> websocketEvidence(String envelope,java.time.Instant receivedAt) {
			evidence("WEBSOCKET",envelope,receivedAt); return CompletableFuture.completedFuture(null);
		}
	}
	public record Response(String result, String envelope,java.time.Instant receivedAt) { }
	public static final class UnsupportedTransactionVersion extends IllegalStateException {
		private final Response response;
		private UnsupportedTransactionVersion(String envelope,java.time.Instant receivedAt) { super("UNSUPPORTED_TRANSACTION_VERSION"); response=new Response(null,envelope,receivedAt); }
		public Response response() { return response; }
	}
	private static final java.util.regex.Pattern UNSUPPORTED_VERSION=java.util.regex.Pattern.compile("Transaction version \\((0|[1-9][0-9]{0,9})\\) is not supported by the requesting client\\. Please try the request again with the following configuration parameter: \\\"maxSupportedTransactionVersion\\\": (0|[1-9][0-9]{0,9})");
	private boolean knownUnsupportedVersion(String method,String params,tools.jackson.databind.JsonNode error) {
		if(!method.equals("getTransaction") || !error.path("code").isIntegralNumber() || !error.path("code").asText().equals("-32015") || !error.path("message").isString()) return false;
		var ceiling=json.readTree(params).path(1).path("maxSupportedTransactionVersion");
		if(!ceiling.isIntegralNumber() || !ceiling.asText().equals("1")) return false;
		var match=UNSUPPORTED_VERSION.matcher(error.path("message").asText());
		return match.matches() && match.group(1).equals(match.group(2)) && Long.parseLong(match.group(1))>1;
	}
	public record Notification(String pool, String account, String signature, long slot, String payload, String envelope,java.time.Instant receivedAt) { }
	public static String exactValue(String text,String path) { return ExactCaptureJson.value(text,path); }
	public static List<String> exactElements(String text,String path) { return ExactCaptureJson.elements(text,path); }
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
	private final java.util.Set<CompletableFuture<?>> admitted=ConcurrentHashMap.newKeySet();
	private volatile Integer closeCode;
	private volatile boolean transientFailure;
	private java.time.Instant nextPing,pongDeadline;
	private volatile String expectedPong;
	private volatile Consumer<String> interruption;
	private final Object callbackAdmission=new Object();
	private volatile java.util.concurrent.atomic.AtomicBoolean activeAckArrived;
	private static boolean transientNetwork(Throwable failure) {
		var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>()); boolean io=false; Boolean handshakeTransient=null;
		for(var current=failure;current!=null && seen.size()<8 && seen.add(current);current=current.getCause()) {
			if(current instanceof javax.net.ssl.SSLException || current instanceof java.net.ProtocolException || current instanceof SecurityException) return false;
			if(current instanceof WebSocketHandshakeException handshake) {
				if(!java.util.Set.of(429,500,502,503,504).contains(handshake.getResponse().statusCode())) return false;
				handshakeTransient=true;
			}
			io|=current instanceof java.io.IOException || current instanceof TimeoutException;
		}
		return handshakeTransient==null?io:handshakeTransient;
	}
	public boolean reconnectable() { return transientFailure; }
	public Integer closeCode() { return closeCode; }
	public void awaitAdmitted() {
		try { CompletableFuture.allOf(admitted.toArray(CompletableFuture[]::new)).get(limits.timeoutSeconds(),TimeUnit.SECONDS); }
		catch(Exception failure) { throw new IllegalStateException("CAPTURE_DRAIN_FAILURE"); }
	}
	public static String diagnostic(String phase,String category,Throwable failure,Integer http,Integer rpc,Integer close,String text,java.time.Instant at) {
		var fields=new java.util.LinkedHashMap<String,Object>(); fields.put("version","capture-diagnostic-v2"); fields.put("phase",phase); fields.put("category",category); fields.put("observedAt",at.toString());
		fields.put("httpStatus",http==null?"UNAVAILABLE":http); fields.put("rpcCode",rpc==null?"UNAVAILABLE":rpc); fields.put("wsCloseCode",close==null?"UNAVAILABLE":close); fields.put("wsCloseCodeSource",close==null?"UNAVAILABLE":close==1006?"OBSERVED_LIBRARY_ABNORMAL":"OBSERVED_CLOSE_CALLBACK");
		fields.put("text",text==null?"UNAVAILABLE":java.util.Set.of("normal closure","going away","service restart","try again later","internal server error").contains(text)?text:"REDACTED_UNTRUSTED_TEXT");
		var causes=new java.util.ArrayList<Object>(); var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>()); int frames=0;
		for(var current=failure;current!=null && causes.size()<8 && seen.add(current);current=current.getCause()) {
			var stack=new java.util.ArrayList<String>(); for(var entry:current.getStackTrace()) { if(frames++>=32) break; var location=entry.getClassName()+"."+entry.getMethodName()+":"+entry.getLineNumber(); stack.add(location.matches("[A-Za-z0-9_.$<>:-]{1,180}")?location:"REDACTED_STACK_LOCATION"); }
			var name=current.getClass().getName(); var item=new java.util.LinkedHashMap<String,Object>(); item.put("class",name.matches("[A-Za-z0-9_.$]{1,180}")?name:"REDACTED_CLASS"); item.put("stack",stack);
			if(current instanceof java.sql.SQLException sql) { var state=sql.getSQLState(); item.put("sqlState",state!=null && state.matches("[A-Z0-9]{5}")?state:"UNAVAILABLE"); item.put("vendorCode",sql.getErrorCode()); }
			causes.add(item);
		}
		fields.put("causes",causes); fields.put("causeAvailability",failure==null?"UNAVAILABLE":"AVAILABLE"); fields.put("bounded",true); fields.put("truncated",causes.size()==8 || frames>=32);
		var mapper=JsonMapper.builder().build(); var result=mapper.writeValueAsString(fields);
		if(result.getBytes(StandardCharsets.UTF_8).length>8192) { fields.put("causes",List.of()); fields.put("truncated",true); result=mapper.writeValueAsString(fields); }
		return result;
	}
	private void report(String phase,String category,Throwable failure,Integer http,Integer rpc,Integer code,String text) {
		try { var record=diagnostic(phase,category,failure,http,rpc,code,text,accounting.now()); if(record.contains(secret) || record.contains(java.net.URLEncoder.encode(secret,StandardCharsets.UTF_8))) record=diagnostic(phase,category,null,http,rpc,code,null,accounting.now()); accounting.diagnostic(record); } catch(RuntimeException unavailable) { /* Never mask the operational reason. */ }
	}
	public void heartbeat() {
		if(closed || nextPing==null) return;
		var now=accounting.now();
		if(expectedPong!=null && !now.isBefore(pongDeadline)) { transientFailure=true; report("WS_LISTENER","WS_PONG_TIMEOUT",null,null,null,null,null); interruption.accept("WS_PONG_TIMEOUT"); return; }
		if(expectedPong==null && !now.isBefore(nextPing)) {
			accounting.waitSeconds(10); accounting.start(0); expectedPong=Long.toString(ids.incrementAndGet()); pongDeadline=now.plusSeconds(10); nextPing=now.plusSeconds(30);
			try { awaitWebSocket(socket.sendPing(ByteBuffer.wrap(expectedPong.getBytes(StandardCharsets.US_ASCII))),"WS_SEND",false,TimeUnit.SECONDS.toNanos(limits.timeoutSeconds())); }
			catch(RuntimeException failure) { report("WS_SEND",safeReason(failure),failure,null,null,null,null); interruption.accept(safeReason(failure)); }
		}
	}
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
				if(java.util.Set.of(429,500,502,503,504).contains(response.statusCode()) && attempt<limits.retries()) {
					report("getAccountInfo".equals(method)?"LINKAGE_READ":"getTransactionsForAddress".equals(method)?"HISTORY_RECOVERY":"TX_FETCH","HTTP_RETRY",null,response.statusCode(),null,null,null);
					var retry=response.headers().firstValue("Retry-After").orElse("");
					long wait=attempt==0?1:3;
					if(!retry.isEmpty()) { try { wait=Math.max(wait,Math.min(limits.timeoutSeconds(),Long.parseLong(retry))); }
						catch(NumberFormatException ignored) { wait=limits.timeoutSeconds(); } }
					pause(wait); continue;
				}
				if(response.statusCode()!=200) { report("TX_FETCH","PROVIDER_HTTP_FAILURE",null,response.statusCode(),null,null,null); throw new IllegalStateException("PROVIDER_HTTP_FAILURE"); }
				var node=json.readTree(text);
				if(!"2.0".equals(node.path("jsonrpc").asText()) || !node.path("id").isIntegralNumber() || node.path("id").longValue()!=id || node.has("result")==node.has("error")) throw new IllegalStateException("MALFORMED_RPC_RESPONSE");
				if(node.has("error")) { var code=node.path("error").path("code"); if(!code.isIntegralNumber()) throw new IllegalStateException("MALFORMED_RPC_RESPONSE"); if(knownUnsupportedVersion(method,params,node.path("error"))) throw new UnsupportedTransactionVersion(text,subscriber.receivedAt); report("TX_FETCH","PROVIDER_RPC_FAILURE",null,response.statusCode(),code.intValue(),null,null); throw new IllegalStateException("PROVIDER_RPC_FAILURE"); }
				var result=ExactCaptureJson.value(text,"/result");
				if("getTransaction".equals(method) && "null".equals(result) && attempt<limits.retries()) { report("TX_FETCH","NULL_AVAILABILITY_RETRY",null,200,null,null,null); pause(attempt==0?2:5); continue; }
				return new Response(result==null?"null":result,text,subscriber.receivedAt);
			}
			catch(UnsupportedTransactionVersion unsupported) { throw unsupported; }
			catch(IllegalStateException safe) { report("getAccountInfo".equals(method)?"LINKAGE_READ":"getTransactionsForAddress".equals(method)?"HISTORY_RECOVERY":"TX_FETCH",safeReason(safe),safe,null,null,null,null); throw safe; }
			catch(InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
			catch(Exception failure) {
				if(failure instanceof ExecutionException execution && execution.getCause() instanceof IllegalStateException safe) throw safe;
				Throwable cause=failure; while(cause.getCause()!=null && (cause instanceof ExecutionException || cause instanceof CompletionException)) cause=cause.getCause();
				report("TX_FETCH","PROVIDER_TRANSPORT_FAILURE",failure,null,null,null,null);
				if(!transientNetwork(cause) || attempt==limits.retries()) throw new IllegalStateException("PROVIDER_TRANSPORT_FAILURE");
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
		public void onError(Throwable failure) { body.completeExceptionally(failure); }
		public void onComplete() { receivedAt=accounting.now(); body.complete(bytes.toByteArray()); }
	}
	private record Channel(String pool,String account) { }
	private record PendingSubscription(Channel channel,CompletableFuture<Void> durableAck,java.util.concurrent.atomic.AtomicBoolean arrived) { }
	private record Subscription(Channel channel,CompletableFuture<Void> durableAck) { }
	public void connect(List<Pool> pools,Consumer<Notification> notifications,Consumer<String> interrupted) {
		interruption=interrupted;
		var pending=new ConcurrentHashMap<Long,PendingSubscription>(); var subscriptions=new ConcurrentHashMap<Long,Subscription>();
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
							synchronized(callbackAdmission) {
							if(closed) { accounting.received(fragment.toString().getBytes(StandardCharsets.UTF_8).length,true); return CompletableFuture.completedFuture(null); }
							var began=System.nanoTime();
							try {
								if(messageBytes==0) messageReceivedAt=accounting.now();
								var count=fragment.toString().getBytes(StandardCharsets.UTF_8).length;
								accounting.websocketFragment(count); messageBytes=Math.addExact(messageBytes,count);
								if(messageBytes>limits.messageBytes()) throw new IllegalStateException("WS_MESSAGE_BYTE_LIMIT");
								message.append(fragment);
								if(last) {
									var envelope=message.toString(); message.setLength(0); messageBytes=0; checkSecret(envelope);
									var n=json.readTree(envelope);
									var durable=accounting.websocketEvidence(envelope,messageReceivedAt);
									durable.whenComplete((value,error)->{ if(error!=null) { var reason=safeReason(error instanceof CompletionException?error.getCause():error); failureSignal.completeExceptionally(new IllegalStateException(reason)); ws.abort(); interrupted.accept(reason); } });
									if(n.has("id")) {
										var request=pending.remove(n.path("id").longValue());
										if(request==null || !n.path("result").isIntegralNumber() || n.path("result").longValue()<0 || subscriptions.putIfAbsent(n.path("result").longValue(),new Subscription(request.channel(),request.durableAck()))!=null)
											throw new IllegalStateException("SUBSCRIPTION_REJECTED");
										request.arrived().set(true);
										durable.thenRun(()->{ if(!closed && !failureSignal.isDone()) request.durableAck().complete(null); });
									} else {
										var params=n.path("params"); var subscription=subscriptions.get(params.path("subscription").longValue());
										if(subscription==null) throw new IllegalStateException("UNKNOWN_SUBSCRIPTION");
										var channel=subscription.channel();
										var slot=params.path("result").path("context").path("slot");
										if(!slot.isIntegralNumber() || slot.longValue()<0) throw new IllegalStateException("INVALID_NOTIFICATION_SLOT");
										if(!params.path("result").has("value")) throw new IllegalStateException("INVALID_NOTIFICATION_VALUE");
										var signature=channel.account()==null?params.path("result").path("value").path("signature").asText():null;
										var receivedAt=messageReceivedAt; var position=slot.longValue();
										var completion=CompletableFuture.allOf(durable,subscription.durableAck()).thenRun(()->notifications.accept(new Notification(channel.pool(),channel.account(),signature,position,ExactCaptureJson.value(envelope,"/params/result/value"),envelope,receivedAt)))
											.exceptionally(error->{ var reason=safeReason(error instanceof CompletionException?error.getCause():error); failureSignal.completeExceptionally(new IllegalStateException(reason)); ws.abort(); interrupted.accept(reason); return null; });
										admitted.add(completion); completion.whenComplete((v,e)->admitted.remove(completion));
									}
								}
								accounting.nextMessage(); ws.request(1);
							} catch(RuntimeException failure) { accounting.discardWebsocketMessage(); failureSignal.completeExceptionally(new IllegalStateException(safeReason(failure))); ws.abort(); interrupted.accept(safeReason(failure)); }
							finally { accounting.websocketHandled(System.nanoTime()-began); }
							return CompletableFuture.completedFuture(null);
							}
						}
						public CompletionStage<?> onPing(WebSocket ws,ByteBuffer bytes) {
							if(closed) { accounting.received(bytes.remaining(),true); return CompletableFuture.completedFuture(null); }
							try { accounting.received(bytes.remaining(),true); accounting.controlStarted(); accounting.nextMessage(); ws.request(1); return ws.sendPong(bytes); }
							catch(RuntimeException failure) { failureSignal.completeExceptionally(new IllegalStateException(safeReason(failure))); ws.abort(); interrupted.accept(safeReason(failure)); return CompletableFuture.completedFuture(null); }
						}
						public CompletionStage<?> onPong(WebSocket ws,ByteBuffer bytes) {
							if(closed) { accounting.received(bytes.remaining(),true); return CompletableFuture.completedFuture(null); }
							try { accounting.received(bytes.remaining(),true); var value=StandardCharsets.US_ASCII.decode(bytes.duplicate()).toString(); if(value.equals(expectedPong)) expectedPong=null; accounting.nextMessage(); ws.request(1); }
							catch(RuntimeException failure) { failureSignal.completeExceptionally(new IllegalStateException(safeReason(failure))); ws.abort(); interrupted.accept(safeReason(failure)); }
							return CompletableFuture.completedFuture(null);
						}
						public CompletionStage<?> onClose(WebSocket ws,int code,String reason) {
							accounting.discardWebsocketMessage();
							failureSignal.completeExceptionally(new IllegalStateException("DISCONNECT"));
							closeCode=code; transientFailure=java.util.Set.of(1000,1001,1011,1012,1013).contains(code);
							accounting.received(reason.getBytes(StandardCharsets.UTF_8).length+2L,true); String category="DISCONNECT";
							try { checkSecret(json.writeValueAsString(reason)); } catch(RuntimeException unsafe) { category=safeReason(unsafe); transientFailure=false; }
							report("WS_LISTENER",category,null,null,null,code,reason); if(!closed) interrupted.accept(category); return CompletableFuture.completedFuture(null);
						}
						public void onError(WebSocket ws,Throwable failure) { accounting.discardWebsocketMessage(); transientFailure=transientNetwork(failure); report("WS_LISTENER","WS_LISTENER_FAILURE",failure,null,null,null,null); failureSignal.completeExceptionally(new IllegalStateException("WS_LISTENER_FAILURE")); if(!closed) interrupted.accept("WS_LISTENER_FAILURE"); }
					});
			socket=awaitWebSocket(connecting,"WS_CONNECT",false,connectDeadline-System.nanoTime());
			awaitWebSocket(CompletableFuture.anyOf(opened,failureSignal),"WS_CONNECT",true,connectDeadline-System.nanoTime());
			var accounts=new java.util.HashSet<String>();
			for(var pool:pools) {
				subscribe(pending,failureSignal,new Channel(pool.address(),null),"logsSubscribe","[{\"mentions\":[\""+pool.address()+"\"]},{\"commitment\":\"finalized\"}]");
				for(var account:pool.accounts()) if(accounts.add(account)) subscribe(pending,failureSignal,new Channel(pool.address(),account),"accountSubscribe","[\""+account+"\",{\"commitment\":\"finalized\",\"encoding\":\"base64\"}]");
			}
			nextPing=accounting.now().plusSeconds(30);
		} catch(RuntimeException failure) { var reason=safeReason(failure); var phase=reason.equals("WS_DURABLE_ACK_TIMEOUT")?"WS_ACK_COMMIT":reason.startsWith("WS_ACK")?"WS_ACK_ARRIVAL":reason.startsWith("WS_SEND")?"WS_SEND":reason.equals("DISCONNECT")?"WS_LISTENER":"WS_CONNECT"; report(phase,reason,failure,null,null,null,null); close(); throw new IllegalStateException(reason); }
	}
	private void subscribe(Map<Long,PendingSubscription> pending,CompletableFuture<Void> failureSignal,Channel channel,String method,String params) {
		if(closed) throw new IllegalStateException("CAPTURE_TRANSPORT_CLOSED");
		accounting.waitSeconds(limits.timeoutSeconds()); accounting.start(0);
		accounting.waitSeconds(limits.timeoutSeconds());
		var id=ids.incrementAndGet(); var ack=new CompletableFuture<Void>(); var arrived=new java.util.concurrent.atomic.AtomicBoolean(); pending.put(id,new PendingSubscription(channel,ack,arrived));
		activeAckArrived=arrived;
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
		try { awaitWebSocket(CompletableFuture.anyOf(ack,failureSignal),"WS_ACK",true,remaining); }
		catch(IllegalStateException failure) { if("WS_ACK_TIMEOUT".equals(failure.getMessage()) && arrived.get()) throw new IllegalStateException("WS_DURABLE_ACK_TIMEOUT"); throw failure; }
	}
	private <T> T awaitWebSocket(CompletableFuture<T> future,String phase,boolean internalFailure,long remainingNanos) {
		try { return future.get(Math.max(0,remainingNanos),TimeUnit.NANOSECONDS); }
		catch(TimeoutException failure) { future.cancel(true); transientFailure=true; report(phase.equals("WS_ACK")?(activeAckArrived!=null && activeAckArrived.get()?"WS_ACK_COMMIT":"WS_ACK_ARRIVAL"):phase,phase+"_TIMEOUT",failure,null,null,null,null); throw new IllegalStateException(phase+"_TIMEOUT"); }
		catch(InterruptedException failure) { future.cancel(true); Thread.currentThread().interrupt(); throw new IllegalStateException("CAPTURE_INTERRUPTED"); }
		catch(ExecutionException failure) {
			var cause=failure.getCause(); if(!internalFailure) transientFailure=transientNetwork(cause); report(phase,phase+"_FAILURE",failure,cause instanceof WebSocketHandshakeException handshake?handshake.getResponse().statusCode():null,null,null,null);
			throw new IllegalStateException(internalFailure?safeReason(failure.getCause()):phase+"_FAILURE");
		}
	}
	public static String safeReason(Throwable failure) {
		var reason=failure.getMessage();
		return reason!=null && reason.matches("[A-Z][A-Z0-9_]{1,80}")?reason:"PROVIDER_TRANSPORT_FAILURE";
	}
	@Override public void close() { synchronized(callbackAdmission) { closed=true; var ws=socket; if(ws!=null) ws.abort(); client.shutdownNow(); } }
}
