package io.cryptoresearch.marketdata.application;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import tools.jackson.databind.json.JsonMapper;

/** Real loopback WebSocket fixture with bounded frame parsing and surfaced server failures. */
final class LocalCaptureWebSocket implements AutoCloseable {
	private final ServerSocket server;
	private volatile Socket connection;
	private final CompletableFuture<Void> sent=new CompletableFuture<>();
	private final Thread thread;
	LocalCaptureWebSocket(LongFunction<java.util.List<String>> notifications,boolean fragments) throws Exception {
		this(notifications,fragments,id->"{\"jsonrpc\":\"2.0\",\"id\":"+id+",\"result\":"+(10000+id)+"}");
	}
	LocalCaptureWebSocket(LongFunction<java.util.List<String>> notifications,boolean fragments,LongFunction<String> acknowledgement) throws Exception {
		this(notifications,fragments,acknowledgement,1);
	}
	LocalCaptureWebSocket(LongFunction<java.util.List<String>> notifications,boolean fragments,LongFunction<String> acknowledgement,int notificationOpcode) throws Exception {
		server=new ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")); server.setSoTimeout(10000);
		thread=Thread.ofPlatform().daemon(true).start(()->{
			try(var socket=server.accept()) {
				connection=socket; socket.setSoTimeout(10000); var input=socket.getInputStream(); var output=socket.getOutputStream();
				var headers=new StringBuilder(); while(!headers.toString().endsWith("\r\n\r\n")) { var b=input.read(); if(b<0 || headers.length()>8192) throw new java.io.IOException(); headers.append((char)b); }
				var key=headers.toString().lines().filter(line->line.toLowerCase().startsWith("sec-websocket-key:"))
						.findFirst().orElseThrow().split(":",2)[1].strip();
				var accept=java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-1")
						.digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
				output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); output.flush();
				long firstAccount=-1;
				for(var i=0;i<18;i++) {
					var request=JsonMapper.builder().build().readTree(readFrame(input));
					if(!"finalized".equals(request.path("params").path(1).path("commitment").asText())) throw new AssertionError("subscription must be finalized");
					var id=request.path("id").longValue(); var subscription=10000+id;
					if(firstAccount<0 && "accountSubscribe".equals(request.path("method").asText())) firstAccount=subscription;
					var response=acknowledgement.apply(id);
					if(response!=null) frame(output,response,1,true);
				}
				for(var text:notifications.apply(firstAccount)) {
					if(fragments) { var middle=text.length()/2; frame(output,text.substring(0,middle),1,false); frame(output,text.substring(middle),0,true); }
					else frame(output,text,notificationOpcode,true);
				}
				sent.complete(null);
				try { while(input.read()!=-1) { } } catch(java.io.IOException closed) { }
			} catch(Throwable failure) { sent.completeExceptionally(failure); }
		});
	}
	URI uri() { return URI.create("ws://127.0.0.1:"+server.getLocalPort()+"/"); }
	void awaitSent() throws Exception { sent.get(30,TimeUnit.SECONDS); }
	private static String readFrame(InputStream input) throws Exception {
		var first=input.read(); var second=input.read(); if(first<0 || second<0) throw new java.io.EOFException();
		long length=second&127;
		if(length==126) length=(input.read()<<8)|input.read();
		if(length==127) { length=0; for(var i=0;i<8;i++) length=(length<<8)|input.read(); }
		if(length>2000000 || (second&128)==0) throw new java.io.IOException("Invalid fixture frame");
		var mask=input.readNBytes(4); var bytes=input.readNBytes((int)length); if(bytes.length!=length) throw new java.io.EOFException();
		for(var i=0;i<bytes.length;i++) bytes[i]^=mask[i%4];
		return new String(bytes,StandardCharsets.UTF_8);
	}
	private static void frame(OutputStream output,String value,int opcode,boolean last) throws Exception {
		var bytes=value.getBytes(StandardCharsets.UTF_8); output.write((last?128:0)|opcode);
		if(bytes.length<126) output.write(bytes.length);
		else if(bytes.length<=65535) { output.write(126); output.write(bytes.length>>8); output.write(bytes.length); }
		else { output.write(127); for(var shift=56;shift>=0;shift-=8) output.write((int)((long)bytes.length>>>shift)); }
		output.write(bytes); output.flush();
	}
	@Override public void close() throws Exception { if(connection!=null) connection.close(); server.close(); thread.join(1000); }
}
