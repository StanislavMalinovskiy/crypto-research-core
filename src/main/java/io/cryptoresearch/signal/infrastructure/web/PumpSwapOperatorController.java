package io.cryptoresearch.signal.infrastructure.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Semaphore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.cryptoresearch.kernel.api.*;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.signal.api.*;

/** Default-off local manual adapter; no transaction, provider or durable actions. */
@RestController
@ConditionalOnProperty(prefix="crypto.research.signal.operator-screen",name="enabled",havingValue="true")
public final class PumpSwapOperatorController {
    private static final Set<String> DIAGNOSTIC_FIELDS=Set.of("session","receivedFrom","receivedTo","eventFrom","eventTo","chain","pool","baseMint","quoteMint","eventEnd");
    private static final String CSP="default-src 'none'; script-src 'self'; connect-src 'self'; style-src 'none'; img-src 'none'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'";
    private final MarketDataApi marketdata;
    private final SignalApi signal;
    private final PumpSwapOperatorAccessPolicy policy;
    private final PumpSwapOperatorProjection projection=new PumpSwapOperatorProjection();
    private final Semaphore diagnosticPermit=new Semaphore(1);

    public PumpSwapOperatorController(MarketDataApi marketdata,SignalApi signal,Environment environment) {
        this.marketdata=marketdata;this.signal=signal;this.policy=new PumpSwapOperatorAccessPolicy(environment);
    }
    @GetMapping("/operator")
    ResponseEntity<byte[]> index(HttpServletRequest request) {return resource(request,"index.html",MediaType.TEXT_HTML);}
    @GetMapping("/operator/operator.js")
    ResponseEntity<byte[]> script(HttpServletRequest request) {return resource(request,"operator.js",MediaType.parseMediaType("text/javascript;charset=UTF-8"));}
    private ResponseEntity<byte[]> resource(HttpServletRequest request,String name,MediaType type) {
        try{policy.check(request,false);if(request.getQueryString()!=null)throw new IllegalArgumentException("INVALID_INPUT");
            try(var input=new ClassPathResource("signal/operator/"+name).getInputStream()){var bytes=input.readNBytes(65537);if(bytes.length>65536)return error(422,"OUTPUT_LIMIT",null);return response(200,bytes,type);}
        }catch(IllegalArgumentException failure){return error("LOCAL_POLICY".equals(failure.getMessage())?403:400,"LOCAL_POLICY".equals(failure.getMessage())?"LOCAL_POLICY":"INVALID_INPUT",null);}
        catch(Exception failure){return error(500,"UNEXPECTED_FAILURE",null);}
    }
    @GetMapping("/operator/api/capture")
    ResponseEntity<byte[]> capture(HttpServletRequest request) {
        try{policy.check(request,true);var fields=fields(request,Set.of("session"));var session=uuid(fields.get("session"));var monitor=marketdata.captureMonitor(session);return response(monitor.isPresent()?200:404,projection.encode(projection.monitor(session,monitor)),MediaType.APPLICATION_JSON);}
        catch(RuntimeException failure){return failure(failure);}
    }
    @PostMapping("/operator/api/diagnostic")
    ResponseEntity<byte[]> diagnostic(HttpServletRequest request) {
        try {
            policy.check(request,true);
            if(request.getHeader("Transfer-Encoding")!=null||request.getContentLengthLong()>0||request.getInputStream().read()!=-1)throw new IllegalArgumentException("INVALID_INPUT");
            var query=diagnosticRequest(fields(request,DIAGNOSTIC_FIELDS));
            if(!diagnosticPermit.tryAcquire())return error(429,"BUSY",null);
            try{return response(200,projection.encode(projection.diagnostic(signal.diagnoseRetainedPumpSwapNativePriceRise(query))),MediaType.APPLICATION_JSON);}
            finally{diagnosticPermit.release();}
        }catch(RuntimeException failure){return failure(failure);}
        catch(java.io.IOException failure){return error(400,"INVALID_INPUT",null);}
    }
    private ResponseEntity<byte[]> failure(RuntimeException failure) {
        if(failure instanceof PumpSwapRetainedWindow.ReadFailure retained)return error(422,"RETAINED_READ_FAILURE",retained.diagnostic());
        if(failure instanceof DataAccessException)return error(503,"READ_UNAVAILABLE",null);
        if(failure instanceof IllegalArgumentException) {
            if("LOCAL_POLICY".equals(failure.getMessage()))return error(403,"LOCAL_POLICY",null);
            if("OUTPUT_LIMIT".equals(failure.getMessage()))return error(422,"OUTPUT_LIMIT",null);
            if("INVALID_INPUT".equals(failure.getMessage()))return error(400,"INVALID_INPUT",null);
            return error(422,"DIAGNOSTIC_REJECTED",null);
        }
        return error(500,"UNEXPECTED_FAILURE",null);
    }
    private ResponseEntity<byte[]> error(int status,String code,PumpSwapRetainedWindow.Diagnostic observed) {return response(status,projection.encode(projection.error(code,observed)),MediaType.APPLICATION_JSON);}
    private static ResponseEntity<byte[]> response(int status,byte[] body,MediaType type) {
        var headers=new HttpHeaders();headers.setContentType(type);headers.set("Cache-Control","no-store");headers.set("X-Content-Type-Options","nosniff");headers.set("X-Frame-Options","DENY");headers.set("Referrer-Policy","no-referrer");headers.set("Content-Security-Policy",CSP);
        return new ResponseEntity<>(body,headers,HttpStatusCode.valueOf(status));
    }
    private static Map<String,String> fields(HttpServletRequest request,Set<String> allowed) {
        try{var raw=Objects.requireNonNull(request.getQueryString());if(raw.getBytes(StandardCharsets.UTF_8).length>4096)throw new IllegalArgumentException();var result=new LinkedHashMap<String,String>();
            for(var field:raw.split("&",-1)){var pair=field.split("=",-1);if(pair.length!=2)throw new IllegalArgumentException();var key=decode(pair[0]);var value=decode(pair[1]);if(!allowed.contains(key)||value.isEmpty()||value.getBytes(StandardCharsets.UTF_8).length>256||result.putIfAbsent(key,value)!=null)throw new IllegalArgumentException();}
            if(!result.keySet().equals(allowed))throw new IllegalArgumentException();return result;
        }catch(RuntimeException malformed){throw new IllegalArgumentException("INVALID_INPUT");}
    }
    private static String decode(String text) {var value=URLDecoder.decode(text,StandardCharsets.UTF_8);if(value.indexOf('\uFFFD')>=0)throw new IllegalArgumentException();return value;}
    private static UUID uuid(String value) {try{var id=UUID.fromString(value);if(!id.toString().equals(value))throw new IllegalArgumentException();return id;}catch(RuntimeException invalid){throw new IllegalArgumentException("INVALID_INPUT");}}
    private static Instant utc(String value) {if(!value.endsWith("Z"))throw new IllegalArgumentException();return Instant.parse(value).truncatedTo(ChronoUnit.MICROS);}
    private static PumpSwapRetainedNativePriceSignal.Request diagnosticRequest(Map<String,String> fields) {
        try{var session=uuid(fields.get("session"));var f=utc(fields.get("receivedFrom"));var t=utc(fields.get("receivedTo"));var a=utc(fields.get("eventFrom"));var b=utc(fields.get("eventTo"));var e=utc(fields.get("eventEnd"));
            var chain=new ChainId(fields.get("chain"));if(!chain.equals(ChainId.SOLANA_MAINNET))throw new IllegalArgumentException();new WalletAddress(chain,fields.get("pool"));new AssetId(chain,fields.get("baseMint"));new AssetId(chain,fields.get("quoteMint"));
            if(!f.isBefore(t)||Duration.between(f,t).compareTo(Duration.ofHours(24))>0||!a.isBefore(b)||Duration.between(a,b).getSeconds()<600||Duration.between(a,b).getSeconds()>300000||a.getNano()!=0||b.getNano()!=0||e.getNano()!=0||Math.floorMod(a.getEpochSecond(),300)!=0||Math.floorMod(b.getEpochSecond(),300)!=0||Math.floorMod(e.getEpochSecond(),300)!=0||e.isAfter(b)||e.minusSeconds(600).isBefore(a))throw new IllegalArgumentException();
            return new PumpSwapRetainedNativePriceSignal.Request(new PumpSwapRetainedWindow.Request(session,f,t,a,b),new PumpSwapPoolMetrics.Pool(chain.value(),fields.get("pool"),fields.get("baseMint"),fields.get("quoteMint")),e);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("INVALID_INPUT");}
    }
}
