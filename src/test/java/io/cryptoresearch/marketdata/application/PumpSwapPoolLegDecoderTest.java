package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.api.PumpSwapPoolLegs;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapRawOperationParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PumpSwapPoolLegDecoderTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PUMP="pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA";
    static final String TOKEN="TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    static final String TOKEN2022="TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";
    static final String WSOL="So11111111111111111111111111111111111111112";
    static final String POOL="5iSVs31h7HMdGGmyUvzmHDsYwV7cydD6CZJTbqzCtLii";
    static final String BASE_VAULT="wSqnGS6eb9AdNiGJVstrgkh1Am1oA35AovnPrCg61FL";
    static final String QUOTE_VAULT="9Dx7EA7h6Bp2YmiEDVXtJvYrArtEWoBe5qPr3sgW6R2W";
    static final PumpSwapPoolLegs.PoolBinding GTA=new PumpSwapPoolLegs.PoolBinding(POOL,BASE_VAULT,QUOTE_VAULT,
            "BsbsB3WLq7vbY5En3MBCAsTrcwaCxNKY2Mp5pGuLpump",WSOL,TOKEN2022,TOKEN);
    final DecodePumpSwapPoolLegsUseCase decoder = new DecodePumpSwapPoolLegsUseCase();
    @ParameterizedTest @CsvSource({"flash,SELL,1219488534", "sixvo,BUY,2499375312", "term,BUY,499825075", "direct,BUY,1749562719"})
    void realRoutingExamplesConfirmExactPoolNetAndPreserveV4(String name,String direction,String quote) {
        var source=source(name); var result=decode(source);
        assertThat(result.v4()).isEqualTo(new DecodePumpSwapOperationsUseCase().decode(batch(source)));
        assertThat(result.v4().transactions().getFirst().reason()).contains("UNSUPPORTED_VARIANT");
        assertThat(result.transactions().getFirst().status()).as("%s: %s",name,result.transactions().getFirst().reason()).isEqualTo(PumpSwapOperations.Status.DECODED);
        assertThat(result.poolLegs()).hasSize(1);
        var leg=result.poolLegs().getFirst();
        assertThat(leg.source()).isEqualTo(source);assertThat(leg.binding()).isEqualTo(GTA);
        assertThat(leg.direction()).isEqualTo(direction);assertThat(leg.baseAmount()).isPositive();
        assertThat(leg.baseDecimals()).isEqualTo(6);
        assertThat(leg.poolNetQuoteLamports()).contains(new BigInteger(quote));
        assertThat(leg.priceStatus()).isEqualTo("PRICE_CONFIRMED");
        var ratio=leg.price().orElseThrow();
        assertThat(ratio.basis()).isEqualTo("POOL_NET_SOL_PER_BASE");
        assertThat(ratio.numerator().multiply(leg.baseAmount()).multiply(BigInteger.TEN.pow(9)))
            .isEqualTo(new BigInteger(quote).multiply(BigInteger.TEN.pow(6)).multiply(ratio.denominator()));
        assertThat(ratio.numerator().gcd(ratio.denominator())).isEqualTo(BigInteger.ONE);
        assertThat(leg.ultimateTrader()).isEmpty();assertThat(leg.ultimateTraderStatus()).isEqualTo("UNDETERMINED");
        assertThat(leg.observedInitiator()).isNotBlank();assertThat(leg.eventUser()).isNotBlank();
        assertThat(result.decoderVersion()).isEqualTo("pumpswap-pool-legs-v5");
    }
    @ParameterizedTest @CsvSource({"multiple,MULTIPLE_OWN_EVENTS", "wrongParent,MISSING_OWN_EVENT", "wrongUser,EVENT_ROLE_MISMATCH", "badEvent,EVENT_LAYOUT", "baseMismatch,BASE_BALANCE_UNPROVED", "baseUnknown,VAULT_TOUCH_UNPROVED", "baseHook,VAULT_HOOK_UNPROVED"})
    void executionProofFaultsRemainUnsupported(String fault,String reason) {
        var changed=mutate(source("term"),fault);var result=decode(changed);
        assertThat(result.poolLegs()).isEmpty();
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.UNSUPPORTED);
        assertThat(result.transactions().getFirst().reason()).contains(reason);
    }
    @ParameterizedTest @CsvSource({"quoteMissing", "quoteMismatch", "quoteDecimals", "quoteUnknown"})
    void quoteProofFaultsKeepProvenBaseWithoutPrice(String fault) {
        var result=decode(mutate(source("term"),fault));assertThat(result.poolLegs()).hasSize(1);
        var leg=result.poolLegs().getFirst();assertThat(leg.price()).isEmpty();
        assertThat(leg.poolNetQuoteLamports()).isEmpty();assertThat(leg.priceStatus()).isEqualTo("PRICE_NOT_CONFIRMED");
        assertThat(leg.priceReason()).isNotBlank();assertThat(leg.baseAmount()).isPositive();
    }
    @Test void digestAndIdentityFailClosed() {
        var s=source("term");
        assertThatThrownBy(()->decode(copy(s,s.rawResult(),"sha256:"+"0".repeat(64)))).hasMessage("HASH_MISMATCH");
        var raw=JSON.readTree(s.rawResult());((tools.jackson.databind.node.ObjectNode)raw).put("slot",s.slot()+1);
        assertThatThrownBy(()->decode(withRaw(s,JSON.writeValueAsString(raw)))).hasMessage("RAW_IDENTITY_MISMATCH");
    }
    @Test void failedTransactionCannotProduceLeg() {
        var s=source("term");var raw=JSON.readTree(s.rawResult());((tools.jackson.databind.node.ObjectNode)raw.path("meta")).put("err","AccountInUse");
        assertThat(decode(withRaw(s,JSON.writeValueAsString(raw))).poolLegs()).isEmpty();
    }
    @Test void correctedDuplicateOwnEventUsesSameParentAndRejectsEvenIdenticalBytes() {
        var s=mutate(source("term"),"multiple");
        assertThat(duplicateDisposition(s).reason()).contains("MULTIPLE_OWN_EVENTS");
    }
    static PumpSwapOperations.TransactionResult duplicateDisposition(PumpSwapOperations.RecordedTransaction s) {
        return qualified(s).transactions().getFirst();
    }
    @Test void documentaryFixtureReconstructsExactPinnedIdlBytes() {
        try(var stream=getClass().getResourceAsStream("/fixtures/pumpswap-pool-legs/october-idl.json")) {
            var bytes=stream.readAllBytes();
            // apply_patch adds a terminal newline; original pinned JSON has none.
            assertThat(PumpSwapRawOperationParser.sha256(Arrays.copyOf(bytes,bytes.length-1)))
                .isEqualTo(PumpSwapRawOperationParser.OCTOBER_IDL_HASH);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    @ParameterizedTest @CsvSource({"unknownDiscriminator", "foreignDiscriminator", "eventFeeMismatch", "eventBool", "multipleConflict"})
    void exactOwnSchemaAndEventArithmeticCannotBeInvented(String fault) {
        var result=decode(mutate(source("term"),fault));
        assertThat(result.poolLegs()).as("%s must not confirm source-schema or amount contradiction",fault).isEmpty();
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.UNSUPPORTED);
    }
    @Test void unresolvedSecondInvocationKeepsLegVisibleButTransactionPartial() {
        var source=source("term");var raw=(tools.jackson.databind.node.ObjectNode)JSON.readTree(source.rawResult());
        var keys=resolvedKeys(raw);var ix=JSON.createObjectNode();ix.put("programIdIndex",keys.indexOf(PUMP));ix.putArray("accounts");ix.put("data",encode58(new byte[8]));
        ((tools.jackson.databind.node.ArrayNode)raw.path("transaction").path("message").path("instructions")).add(ix);
        var result=decode(withRaw(source,JSON.writeValueAsString(raw)));assertThat(result.poolLegs()).hasSize(1);
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.PARTIAL);
        assertThat(result.transactions().getFirst().reason()).contains("UNSUPPORTED_VARIANT");
    }
    @Test void configuredBindingCannotInventMintAndUnknownPoolCannotBeCounted() {
        var other=new PumpSwapPoolLegs.PoolBinding(WSOL,BASE_VAULT,QUOTE_VAULT,GTA.baseMint(),WSOL,TOKEN2022,TOKEN);
        var result=decoder.decode(new PumpSwapPoolLegs.Request(batch(source("term")),List.of(other)));
        assertThat(result.poolLegs()).isEmpty();assertThat(result.transactions().getFirst().reason()).contains("NON_TARGET_POOL");
        var wrong=new PumpSwapPoolLegs.PoolBinding(POOL,BASE_VAULT,QUOTE_VAULT,POOL,WSOL,TOKEN2022,TOKEN);
        assertThat(decoder.decode(new PumpSwapPoolLegs.Request(batch(source("term")),List.of(wrong))).poolLegs()).isEmpty();
    }
    @Test void selectiveContextStillDefersOptionalDecoderUntilNewCall() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(RecordedMarketDataService.class,()->new RecordedMarketDataService(null,null,null,null,null,null,null,null));context.refresh();
            var api=context.getBean(RecordedMarketDataService.class);var request=new PumpSwapPoolLegs.Request(batch(source("term")),List.of(GTA));
            assertThatThrownBy(()->api.decodePumpSwapPoolLegs(request)).isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
            context.getBeanFactory().registerSingleton("requiredPoolLegDecoder",decoder);
            assertThat(api.decodePumpSwapPoolLegs(request)).isEqualTo(decoder.decode(request));
        }
    }
    @Test void permutationRepeatAndBindingIdentityAreDeterministic() {
        var a=source("term");var b=source("flash");
        var x=decoder.decode(new PumpSwapPoolLegs.Request(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("INCOMPLETE")),List.of(GTA)));
        var y=decoder.decode(new PumpSwapPoolLegs.Request(new PumpSwapOperations.RecordedBatch(List.of(b,a),List.of("INCOMPLETE")),List.of(GTA)));
        assertThat(x).isEqualTo(y);assertThat(x.fingerprint()).startsWith("sha256:");
        assertThat(x.v4()).isEqualTo(new DecodePumpSwapOperationsUseCase().decode(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("INCOMPLETE"))));
    }
    @Test void invalidBindingsAndSourceLimitReject() {
        assertThatThrownBy(()->decoder.decode(new PumpSwapPoolLegs.Request(batch(source("term")),List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->decoder.decode(new PumpSwapPoolLegs.Request(batch(source("term")),List.of(GTA,GTA)))).isInstanceOf(IllegalArgumentException.class);
    }
    static PumpSwapPoolLegs.Result qualified(PumpSwapOperations.RecordedTransaction s) { return new DecodePumpSwapPoolLegsUseCase().decode(new PumpSwapPoolLegs.Request(batch(s),List.of(GTA))); }
    PumpSwapPoolLegs.Result decode(PumpSwapOperations.RecordedTransaction s) { return qualified(s); }
    static PumpSwapOperations.RecordedBatch batch(PumpSwapOperations.RecordedTransaction s) {return new PumpSwapOperations.RecordedBatch(List.of(s),List.of("INCOMPLETE","NOT_ADMITTED"));}
    static PumpSwapOperations.RecordedTransaction source(String name) {
        try(var stream=PumpSwapPoolLegDecoderTest.class.getResourceAsStream("/fixtures/pumpswap-pool-legs/"+name+".json")) {
            var n=JSON.readTree(stream);return sourceNode(n);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    static PumpSwapOperations.RecordedTransaction sourceNode(JsonNode n) {
        return new PumpSwapOperations.RecordedTransaction(UUID.fromString(n.path("session").asText()),new ChainId(n.path("chain").asText()),n.path("signature").asText(),n.path("provider").asText(),n.path("slot").longValue(),n.path("finality").asText(),n.path("captureDisposition").asText(),n.path("sourceReference").asText(),Optional.of(Instant.parse(n.path("sourceTime").asText())),Instant.parse(n.path("receivedAt").asText()),Instant.parse(n.path("admittedAt").asText()),Instant.parse(n.path("ingestedAt").asText()),n.path("rawResult").asText(),n.path("rawDigest").asText());
    }
    static PumpSwapOperations.RecordedTransaction copy(PumpSwapOperations.RecordedTransaction s,String raw,String digest) {return new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),s.provider(),s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),s.sourceTime(),s.receivedAt(),s.admittedAt(),s.ingestedAt(),raw,digest);}
    static PumpSwapOperations.RecordedTransaction withRaw(PumpSwapOperations.RecordedTransaction s,String raw) {return copy(s,raw,PumpSwapRawOperationParser.sha256(raw.getBytes(StandardCharsets.UTF_8)));}
    static PumpSwapOperations.RecordedTransaction mutate(PumpSwapOperations.RecordedTransaction s,String fault) {
        var raw=(tools.jackson.databind.node.ObjectNode)JSON.readTree(s.rawResult());
        var keys=new ArrayList<String>();for(var k:raw.path("transaction").path("message").path("accountKeys"))keys.add(k.asText());
        for(String side:List.of("writable","readonly"))for(var k:raw.path("meta").path("loadedAddresses").path(side))keys.add(k.asText());
        var groups=raw.path("meta").path("innerInstructions");tools.jackson.databind.node.ArrayNode eventGroup=null;
        tools.jackson.databind.node.ObjectNode event=null;
        for(var g:groups)for(var i:g.path("instructions")) {
            var bytes=decode58(i.path("data").asText());
            if(bytes.length>16&&HexFormat.of().formatHex(bytes,8,16).equals("67f4521f2cf57777")) {event=(tools.jackson.databind.node.ObjectNode)i;eventGroup=(tools.jackson.databind.node.ArrayNode)g.path("instructions");}
        }
        if(fault.equals("multiple")||fault.equals("multipleConflict")) {
            var duplicate=event.deepCopy();if(fault.equals("multipleConflict")){var b=decode58(duplicate.path("data").asText());b[24]^=1;duplicate.put("data",encode58(b));}
            for(int j=0;j<eventGroup.size();j++)if(eventGroup.get(j)==event){eventGroup.insert(j+1,duplicate);break;}
        }
        if(fault.equals("wrongParent"))event.put("stackHeight",event.path("stackHeight").intValue()+1);
        if(fault.equals("wrongUser")||fault.equals("badEvent")) {var b=decode58(event.path("data").asText());if(fault.equals("wrongUser"))b[160]^=1;else b=Arrays.copyOf(b,b.length+1);event.put("data",encode58(b));}
        if(List.of("unknownDiscriminator","foreignDiscriminator","eventFeeMismatch","eventBool").contains(fault)) {
            var b=decode58(event.path("data").asText());
            if(fault.equals("unknownDiscriminator"))b[8]^=1;
            if(fault.equals("foreignDiscriminator"))System.arraycopy(HexFormat.of().parseHex("bddb7fd34ee661ee"),0,b,8,8);
            if(fault.equals("eventFeeMismatch"))b[88]^=1;
            if(fault.equals("eventBool"))b[475]=2;
            event.put("data",encode58(b));
        }
        String vault=fault.startsWith("quote")?QUOTE_VAULT:BASE_VAULT;
        var post=(tools.jackson.databind.node.ArrayNode)raw.path("meta").path("postTokenBalances");
        for(int j=post.size()-1;j>=0;j--)if(keys.get(post.get(j).path("accountIndex").intValue()).equals(vault)) {
            var row=(tools.jackson.databind.node.ObjectNode)post.get(j);var ui=(tools.jackson.databind.node.ObjectNode)row.path("uiTokenAmount");
            if(fault.equals("quoteMissing"))post.remove(j);
            if(fault.endsWith("Mismatch"))ui.put("amount",new BigInteger(ui.path("amount").asText()).add(BigInteger.ONE).toString());
            if(fault.equals("quoteDecimals"))ui.put("decimals",8);
        }
        if(fault.endsWith("Unknown")) {
            var ix=JSON.createObjectNode();ix.put("programIdIndex",keys.indexOf(fault.startsWith("quote")?TOKEN:TOKEN2022));
            ix.putArray("accounts").add(keys.indexOf(vault));ix.put("data",encode58(new byte[]{26,1}));
            ((tools.jackson.databind.node.ArrayNode)raw.path("transaction").path("message").path("instructions")).add(ix);
        }
        if(fault.equals("baseHook")) {
            for(int j=0;j<eventGroup.size();j++){var i=eventGroup.get(j);if(i.path("accounts").toString().contains(Integer.toString(keys.indexOf(BASE_VAULT)))&&decode58(i.path("data").asText())[0]==12){var hook=i.deepCopy();((tools.jackson.databind.node.ObjectNode)hook).put("stackHeight",i.path("stackHeight").intValue()+1);eventGroup.insert(j+1,hook);break;}}
        }
        return withRaw(s,JSON.writeValueAsString(raw));
    }
    static List<String> resolvedKeys(JsonNode raw) {
        var result=new ArrayList<String>();for(var k:raw.path("transaction").path("message").path("accountKeys"))result.add(k.asText());
        for(String side:List.of("writable","readonly"))for(var k:raw.path("meta").path("loadedAddresses").path(side))result.add(k.asText());return result;
    }
    static final String ALPHABET="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    static byte[] decode58(String s){var n=BigInteger.ZERO;for(char c:s.toCharArray())n=n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(ALPHABET.indexOf(c)));byte[] b=n.signum()==0?new byte[0]:n.toByteArray();if(b.length>0&&b[0]==0)b=Arrays.copyOfRange(b,1,b.length);int z=0;while(z<s.length()&&s.charAt(z)=='1')z++;var r=new byte[z+b.length];System.arraycopy(b,0,r,z,b.length);return r;}
    static String encode58(byte[] b){var n=new BigInteger(1,b);var s=new StringBuilder();while(n.signum()>0){var q=n.divideAndRemainder(BigInteger.valueOf(58));s.append(ALPHABET.charAt(q[1].intValue()));n=q[0];}for(byte x:b){if(x!=0)break;s.append('1');}return s.reverse().toString();}
}
