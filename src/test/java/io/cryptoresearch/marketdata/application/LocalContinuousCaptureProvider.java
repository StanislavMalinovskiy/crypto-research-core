package io.cryptoresearch.marketdata.application;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import io.cryptoresearch.marketdata.api.PumpSwapCapture;
import tools.jackson.databind.json.JsonMapper;

/** Dependency-free local finalized RPC fixture; never external provider/fallback evidence. */
final class LocalContinuousCaptureProvider implements AutoCloseable {
	private final HttpServer http;
	final AtomicInteger requests=new AtomicInteger();
	final Map<String,String> transactions=new java.util.concurrent.ConcurrentHashMap<>();
	final Map<String,List<String>> history=new java.util.concurrent.ConcurrentHashMap<>();
	LocalContinuousCaptureProvider(PumpSwapCapture.Configuration c) throws Exception {
		http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
		http.createContext("/",exchange->{
			var mapper=JsonMapper.builder().build();var request=mapper.readTree(exchange.getRequestBody().readAllBytes());requests.incrementAndGet();
			var method=request.path("method").asText();String result;
			if(method.equals("getAccountInfo")) {
				var account=request.path("params").path(0).asText();var p=c.pools().stream().filter(pool->pool.accounts().contains(account)).findFirst().orElseThrow();
				var base=account.equals(p.baseVault()) || account.equals(p.baseMint());var owner=account.equals(p.address())?p.program():PumpSwapPoolCaptureTest.TOKEN;
				var data=account.equals(p.address())?"[\"AA==\",\"base64\"]":account.equals(p.baseVault()) || account.equals(p.quoteVault())?
					"{\"parsed\":{\"type\":\"account\",\"info\":{\"mint\":\""+(base?p.baseMint():p.quoteMint())+"\",\"owner\":\""+p.address()+"\"}}}":
					"{\"parsed\":{\"type\":\"mint\",\"info\":{\"decimals\":6}}}";
				result="{\"context\":{\"slot\":100},\"value\":{\"owner\":\""+owner+"\",\"data\":"+data+"}}";
			} else if(method.equals("getTransaction"))result=transactions.getOrDefault(request.path("params").path(0).asText(),"null");
			else if(method.equals("getTransactionsForAddress"))result="{\"data\":["+String.join(",",history.getOrDefault(request.path("params").path(0).asText(),List.of()))+"],\"paginationToken\":null}";
			else result="null";
			var bytes=("{\"jsonrpc\":\"2.0\",\"id\":"+request.path("id").longValue()+",\"result\":"+result+"}").getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200,bytes.length);try(var body=exchange.getResponseBody()){body.write(bytes);}
		});http.start();
	}
	URI uri() {return URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/");}
	static String signature(int ordinal) {
		var alphabet="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
		return "1".repeat(62)+alphabet.charAt(10+ordinal/58)+alphabet.charAt(ordinal%58);
	}
	static String transaction(String signature,long slot,boolean failed) {
		var fixture=PumpSwapRawOperationDecoderTest.fixture("1","buy",true,1);
		fixture.put("slot",slot);
		@SuppressWarnings("unchecked") var tx=(Map<String,Object>)fixture.get("transaction");tx.put("signatures",List.of(signature));
		if(failed)PumpSwapRawOperationDecoderTest.meta(fixture).put("err",Map.of("InstructionError",List.of(0,"Custom")));
		return JsonMapper.builder().build().writeValueAsString(fixture);
	}
	static String notification(long firstAccountSubscription,String signature,long slot) {
		return "{\"params\":{\"subscription\":"+(firstAccountSubscription-1)+",\"result\":{\"context\":{\"slot\":"+slot+"},\"value\":{\"signature\":\""+signature+"\"}}}}";
	}
	@Override public void close() {http.stop(0);}
}
