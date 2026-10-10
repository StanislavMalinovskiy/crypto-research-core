package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;

class PumpSwapRawOperationDecoderTest {
    static final String PUMP="pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA", TOKEN="TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
        TOKEN2022="TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb", SYSTEM="11111111111111111111111111111111",
        ATA="ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", EVENT_AUTH="GS4CU59F31iL7aR2Q8zVS8DRrcRnXX1yjQ66TqNVQnaR";
    static final JsonMapper JSON=JsonMapper.builder().build();
    final DecodePumpSwapOperationsUseCase decoder=new DecodePumpSwapOperationsUseCase();
    @Test void selectiveServiceContextDefersDecoderButDecodeCallRequiresRealBean() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(RecordedMarketDataService.class,()->new RecordedMarketDataService(null,null,null,null,null,null,null,null));
            context.refresh();
            var api=context.getBean(RecordedMarketDataService.class);var batch=new PumpSwapOperations.RecordedBatch(List.of(),List.of());
            assertThatThrownBy(()->api.decodePumpSwapOperations(batch)).isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
            context.getBeanFactory().registerSingleton("requiredOperationDecoder",decoder);
            assertThat(api.decodePumpSwapOperations(batch)).isEqualTo(decoder.decode(batch));
        }
    }
    @ParameterizedTest @CsvSource({"classicValid", "token2022Valid", "zeroEndpoints", "vaultDeduction", "traderDeduction", "externalValid", "externalMisallocation", "unknownTouch", "hook", "epoch"})
    void actualEndpointAmountsCorroborateButNeverAllocateInvocationPrincipal(String fault) {
        var raw=fixture("1","buy",!fault.equals("classicValid"),1);var r=roles("buy",!fault.equals("classicValid"),0);
        // Source-derived ordinary transfer ledger, independent pre/post maps: pool -> trader MAX base; trader -> pool 100 quote.
        setEndpointAmount(raw,"preTokenBalances",r.get(7),new BigInteger("18446744073709551615"));
        setEndpointAmount(raw,"preTokenBalances",r.get(6),BigInteger.valueOf(100));
        setEndpointAmount(raw,"postTokenBalances",r.get(5),new BigInteger("18446744073709551615"));
        setEndpointAmount(raw,"postTokenBalances",r.get(8),BigInteger.valueOf(100));
        var keys=(List<String>)message(raw).get("accountKeys");var outer=(List<Map<String,Object>>)message(raw).get("instructions");
        if(fault.equals("zeroEndpoints"))for(var side:List.of("preTokenBalances","postTokenBalances"))for(int role:List.of(5,6,7,8))setEndpointAmount(raw,side,r.get(role),BigInteger.ZERO);
        if(fault.equals("vaultDeduction"))setEndpointAmount(raw,"postTokenBalances",r.get(8),BigInteger.valueOf(99));
        if(fault.equals("traderDeduction"))setEndpointAmount(raw,"postTokenBalances",r.get(5),new BigInteger("18446744073709551614"));
        if(fault.startsWith("external")) {
            outer.add(ix(keys,TOKEN,List.of(r.get(8),r.get(4),r.get(6),r.get(0)),checked(BigInteger.TEN,9),null));
            setEndpointAmount(raw,"postTokenBalances",r.get(8),BigInteger.valueOf(90));setEndpointAmount(raw,"postTokenBalances",r.get(6),BigInteger.TEN);
            if(fault.equals("externalMisallocation"))inner(raw,0).get(1).put("data",base58(checked(BigInteger.valueOf(90),9)));
        }
        if(fault.equals("unknownTouch"))outer.add(ix(keys,TOKEN2022,List.of(r.get(5)),new byte[]{26,1},null));
        if(fault.equals("hook"))inner(raw,0).add(1,ix(keys,SYSTEM,List.of(r.get(5)),new byte[]{1},3));
        if(fault.equals("epoch")){var init=new byte[33];init[0]=18;putAddress(init,1,r.get(1));outer.add(ix(keys,TOKEN2022,List.of(r.get(5),r.get(3)),init,null));}
        var result=decode(raw);
        if(List.of("classicValid","token2022Valid","externalValid").contains(fault)) {
            assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().quoteAmount()).isEqualTo(BigInteger.valueOf(100));
            assertThat(result.operations().getFirst().baseAmount()).isEqualTo(new BigInteger("18446744073709551615"));
            assertThat(result.operations().getFirst().transferPaths()).containsExactly(List.of(0,0),List.of(0,1));
        } else assertThat(result.operations()).as("unproved actual endpoint flow: %s",fault).isEmpty();
    }
    @Test void nonNative165CloseCannotEraseUnspentReceivedPrincipal() {
        var raw=transientFixture("complete");var r=roles("buy",true,0);
        ((List<Map<String,Object>>)message(raw).get("instructions")).remove(2);
        setEndpointAmount(raw,"preTokenBalances",r.get(7),new BigInteger("18446744073709551615"));
        setEndpointAmount(raw,"preTokenBalances",r.get(6),BigInteger.valueOf(100));setEndpointAmount(raw,"postTokenBalances",r.get(8),BigInteger.valueOf(100));
        assertThat(decode(raw).operations()).as("non-native close requires zero remaining token amount, not merely Close9 presence").isEmpty();
    }
    static void setEndpointAmount(Map<String,Object> raw,String side,String account,BigInteger amount) {
        var keys=(List<String>)message(raw).get("accountKeys");var copy=new ArrayList<Map<String,Object>>();
        for(var row:(List<Map<String,Object>>)meta(raw).get(side)){var next=new HashMap<>(row);var ui=new HashMap<>((Map<String,Object>)row.get("uiTokenAmount"));if(row.get("accountIndex").equals(keys.indexOf(account)))ui.put("amount",amount.toString());next.put("uiTokenAmount",ui);copy.add(next);}
        meta(raw).put(side,copy);
    }
    @ParameterizedTest @CsvSource({"valid", "late", "bytes", "accounts"})
    void classicImmutableInitializationMarkerRequiresExactUninitializedAtaOrdering(String fault) {
        var raw=transientFixture("complete");var keys=(List<String>)message(raw).get("accountKeys");keys.set(keys.indexOf(TOKEN2022),TOKEN);
        for(String side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)meta(raw).get(side))if(row.get("programId").equals(TOKEN2022))row.put("programId",TOKEN);
        var create=unbase58((String)inner(raw,0).getFirst().get("data"));putAddress(create,20,TOKEN);inner(raw,0).getFirst().put("data",base58(create));
        inner(raw,0).add(fault.equals("late")?2:1,ix(keys,TOKEN,fault.equals("accounts")?List.of(address(5),address(1)):List.of(address(5)),fault.equals("bytes")?new byte[]{22,0}:new byte[]{22},2));
        var result=decode(raw);if(fault.equals("valid"))assertThat(result.operations()).hasSize(1);else assertThat(result.operations()).isEmpty();
    }
    @Test void uncheckedExternalDrainUsesUniqueLifecycleMintWithoutAllocatingItToPrincipal() {
        var raw=transientFixture("complete");var r=roles("buy",true,0);var keys=(List<String>)message(raw).get("accountKeys");var drain=((List<Map<String,Object>>)message(raw).get("instructions")).get(2);
        var bytes=new byte[9];bytes[0]=3;putU64(bytes,1,new BigInteger("18446744073709551615"));drain.put("data",base58(bytes));drain.put("accounts",List.of(keys.indexOf(r.get(5)),keys.indexOf(r.get(7)),keys.indexOf(r.get(1))));
        setEndpointAmount(raw,"postTokenBalances",r.get(7),new BigInteger("18446744073709551615"));
        var result=decode(raw);assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().baseAmount()).isEqualTo(new BigInteger("18446744073709551615"));
        assertThat(result.operations().getFirst().transferPaths()).containsExactly(List.of(1,0),List.of(1,1));
    }
    @Test void nativePresentTerminalAmountCannotInventUnobservedInitialRentReserve() {
        var raw=transientFixture("complete");var keys=(List<String>)message(raw).get("accountKeys");keys.set(keys.indexOf(address(3)),"So11111111111111111111111111111111111111112");keys.set(keys.indexOf(TOKEN2022),TOKEN);
        for(String side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)meta(raw).get(side)){if(row.get("programId").equals(TOKEN2022))row.put("programId",TOKEN);if(row.get("mint").equals(address(3))){row.put("mint","So11111111111111111111111111111111111111112");var ui=new HashMap<>((Map<String,Object>)row.get("uiTokenAmount"));ui.put("decimals",9);row.put("uiTokenAmount",ui);}}
        var outer=(List<Map<String,Object>>)message(raw).get("instructions");outer.removeLast();outer.removeLast();var create=unbase58((String)inner(raw,0).getFirst().get("data"));putAddress(create,20,TOKEN);inner(raw,0).getFirst().put("data",base58(create));inner(raw,1).getFirst().put("data",base58(checked(new BigInteger("18446744073709551615"),9)));
        addBalance(raw,address(5),"So11111111111111111111111111111111111111112",address(1),TOKEN,9);((List<Map<String,Object>>)meta(raw).get("preTokenBalances")).removeIf(row->row.get("accountIndex").equals(keys.indexOf(address(5))));setEndpointAmount(raw,"postTokenBalances",address(5),new BigInteger("18446744073709551614"));
        assertThat(decode(raw).operations()).isEmpty();
    }
    @Test void zeroNetPoolFlowsCannotHideInsufficientObservedInitialBalance() {
        var raw=transientFixture("complete");var r=roles("buy",true,0);var keys=(List<String>)message(raw).get("accountKeys");var drain=((List<Map<String,Object>>)message(raw).get("instructions")).get(2);((List<Integer>)drain.get("accounts")).set(2,keys.indexOf(r.get(7)));
        setEndpointAmount(raw,"preTokenBalances",r.get(7),BigInteger.ZERO);setEndpointAmount(raw,"postTokenBalances",r.get(7),BigInteger.ZERO);
        assertThat(decode(raw).operations()).isEmpty();
    }
    @ParameterizedTest @CsvSource({"0,false", "100,true"})
    void observedNativeCloseChecksEveryTransferPrefixWithoutInventingTerminalZero(long initial,boolean proven) {
        var raw=fixture("1","buy",false,1);var r=roles("buy",false,0);var keys=(List<String>)message(raw).get("accountKeys");
        String nativeMint="So11111111111111111111111111111111111111112";keys.set(keys.indexOf(r.get(4)),nativeMint);
        for(String side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)meta(raw).get(side))if(row.get("mint").equals(r.get(4)))row.put("mint",nativeMint);
        var outer=(List<Map<String,Object>>)message(raw).get("instructions");
        outer.add(ix(keys,TOKEN,List.of(r.get(8),nativeMint,r.get(6),r.get(0)),checked(BigInteger.valueOf(100),9),null));
        outer.add(ix(keys,TOKEN,List.of(r.get(6),r.get(1),r.get(1)),new byte[]{9},null));
        ((List<Map<String,Object>>)meta(raw).get("postTokenBalances")).removeIf(row->row.get("accountIndex").equals(keys.indexOf(r.get(6))));
        setEndpointAmount(raw,"preTokenBalances",r.get(6),BigInteger.valueOf(initial));
        setEndpointAmount(raw,"preTokenBalances",r.get(8),BigInteger.ZERO);setEndpointAmount(raw,"postTokenBalances",r.get(8),BigInteger.ZERO);
        var result=decode(raw);
        if(proven){assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().quoteAmount()).isEqualTo(BigInteger.valueOf(100));assertThat(result.operations().getFirst().transferPaths()).containsExactly(List.of(0,0),List.of(0,1));}
        else assertThat(result.operations()).as("later native credit cannot fund an earlier debit from observed zero").isEmpty();
    }
    @ParameterizedTest @CsvSource({"sell,449", "buy,497", "buy_exact_quote_in,512"})
    void octoberCoreLayoutsRetainExactPrincipalAndSignedSourceFacts(String variant,int length) {
        // Synthetic derivation of the October7 pinned IDL: old complete frame plus its 49-byte suffix.
        var raw=fixture("1",variant,true,1);var bytes=octoberEvent(variant,roles(variant,true,0));
        assertThat(bytes).hasSize(length);inner(raw,0).get(2).put("data",base58(bytes));
        ordinarySyntheticBalances(raw);var result=decode(raw);assertThat(result.operations()).hasSize(1);
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.DECODED);
        assertThat(result.operations().getFirst().baseAmount()).isEqualTo(new BigInteger("18446744073709551615"));
        assertThat(result.operations().getFirst().quoteAmount()).isEqualTo(BigInteger.valueOf(100));
        assertThat(result.operations().getFirst().eventDigest()).isEqualTo(io.cryptoresearch.marketdata.infrastructure.PumpSwapRawOperationParser.sha256(bytes));
        assertThat(result.decoderVersion()).isEqualTo("pumpswap-operations-v4");
        assertThat(result.schemaVersion()).isEqualTo("may18-oct07-endpoint-proof-v4");
    }
    @ParameterizedTest @CsvSource({"bool", "truncated", "extra"})
    void octoberMalformedSuffixNeverConfirmsPrincipal(String fault) {
        var raw=fixture("1","sell",true,1);var bytes=octoberEvent("sell",roles("sell",true,0));
        if(fault.equals("bool"))bytes[416]=2;
        else bytes=Arrays.copyOf(bytes,bytes.length+(fault.equals("extra")?1:-1));
        inner(raw,0).get(2).put("data",base58(bytes));var result=decode(raw);
        assertThat(result.operations()).isEmpty();assertThat(result.transactions().getFirst().reason()).contains("EVENT_LAYOUT");
    }
    static byte[] octoberEvent(String variant,List<String> roles) {
        var prefix=event(variant,roles);var bytes=Arrays.copyOf(prefix,prefix.length+49);
        // -2^127, intentionally signed; no unsigned amount interpretation is permitted.
        bytes[prefix.length+15]=(byte)128;bytes[prefix.length+16]=1;
        putU64(bytes,prefix.length+17,new BigInteger("18446744073709551615"));
        return bytes;
    }
    @ParameterizedTest @CsvSource({"buy_v2,500", "buy_exact_quote_in_v2,515", "sell_v2,449"})
    void octoberV2RetainedFeesUseObservedVaultFlowsNotV1Payouts(String variant,int length) {
        var raw=v2Fixture(variant);var result=decode(raw);
        assertThat(result.operations()).hasSize(1);var op=result.operations().getFirst();
        assertThat(op.variant()).isEqualTo(variant);assertThat(op.quoteAmount()).isEqualTo(BigInteger.valueOf(100));
        assertThat(op.attributedFees()).doesNotContainKeys("protocol","creator");
        assertThat(op.eventDeclaredFees()).containsEntry("protocol",BigInteger.valueOf(9)).containsEntry("creator",BigInteger.valueOf(3));
        assertThat(op.eventFacts()).containsEntry("virtual_quote_reserves",BigInteger.ONE.shiftLeft(127).negate());
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.DECODED);
    }
    static Map<String,Object> v2Fixture(String variant) {
        boolean sell=variant.equals("sell_v2");String old=sell?"sell":variant.equals("buy_v2")?"buy":"buy_exact_quote_in";
        var raw=fixture("1",old,true,1);var core=roles(old,true,0);var keys=(List<String>)message(raw).get("accountKeys");
        var roles=new ArrayList<>(core.subList(0,9));roles.addAll(List.of(TOKEN2022,TOKEN,SYSTEM,address(60),address(61),address(62),EVENT_AUTH,PUMP));
        var instruction=new byte[24];System.arraycopy(HexFormat.of().parseHex(sell?"5df6823ce7e940b2":variant.equals("buy_v2")?"b817ee6167c5d33d":"c2ab1c46684d5b2f"),0,instruction,0,8);
        ((List<Map<String,Object>>)message(raw).get("instructions")).set(0,ix(keys,PUMP,roles,instruction,null));
        var prefix=event(old,core);if(!sell){prefix=Arrays.copyOf(prefix,445+variant.length());ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).putInt(409,variant.length());System.arraycopy(variant.getBytes(StandardCharsets.UTF_8),0,prefix,413,variant.length());}
        // The pinned v2 documentation makes buyback (7) a part of protocol (9), not an extra fee.
        putAddress(prefix,256,SYSTEM);putAddress(prefix,288,SYSTEM);putU64(prefix,104,BigInteger.valueOf(9));putU64(prefix,360,BigInteger.valueOf(3));
        putU64(prefix,112,BigInteger.valueOf(sell?112:88));putU64(prefix,sell?392:437+variant.length(),BigInteger.valueOf(7));
        if(variant.equals("buy_exact_quote_in_v2"))putU64(prefix,120,BigInteger.valueOf(88));
        var bytes=Arrays.copyOf(prefix,prefix.length+49);bytes[prefix.length+15]=(byte)128;bytes[prefix.length+16]=1;
        var rows=inner(raw,0);rows.get(1).put("data",base58(checked(BigInteger.valueOf(sell?100:93),9)));
        rows.set(2,ix(keys,TOKEN,List.of(core.get(sell?8:6),core.get(4),address(62),core.get(sell?0:1)),checked(BigInteger.valueOf(7),9),2));
        rows.add(ix(keys,PUMP,List.of(EVENT_AUTH),bytes,2));
        ordinarySyntheticBalances(raw);return raw;
    }
    @ParameterizedTest @CsvSource({"sell,false,false", "buy,true,false", "buy_exact_quote_in,true,true", "sell,true,true"})
    void sdkNamedRemainingAccountsAreProvenRatherThanAcceptedByCount(String variant,boolean creator,boolean cashback) {
        var result=decode(tailFixture(variant,creator,cashback));
        assertThat(result.operations()).hasSize(1);
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.DECODED);
        assertThat(result.operations().getFirst().baseAmount()).isEqualTo(new BigInteger("18446744073709551615"));
    }
    @ParameterizedTest @CsvSource({"valid", "debit", "vault", "recipient", "cashback", "budget"})
    void currentExactQuoteInputSeparatesCurveInputFromExecutedDebit(String fault) {
        var raw=fixture("1","buy_exact_quote_in",true,1);var roles=roles("buy_exact_quote_in",true,0);var keys=(List<String>)message(raw).get("accountKeys");
        var event=octoberEvent("buy_exact_quote_in",roles);putU64(event,120,BigInteger.valueOf(88));putU64(event,88,BigInteger.valueOf(5));putU64(event,104,BigInteger.valueOf(7));putU64(event,112,BigInteger.valueOf(93));
        var rows=inner(raw,0);rows.get(1).put("data",base58(checked(BigInteger.valueOf(fault.equals("vault")?92:93),9)));
        rows.add(1,ix(keys,TOKEN,List.of(roles.get(6),roles.get(4),fault.equals("recipient")?roles.get(17):roles.get(10),roles.get(1)),checked(BigInteger.valueOf(fault.equals("debit")?6:7),9),2));
        if(fault.equals("cashback"))putU64(event,439,BigInteger.ONE);if(fault.equals("budget"))putU64(event,72,BigInteger.valueOf(99));rows.getLast().put("data",base58(event));
        ordinarySyntheticBalances(raw);var result=decode(raw);if(fault.equals("valid")){assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().quoteAmount()).isEqualTo(BigInteger.valueOf(100));assertThat(result.operations().getFirst().eventFacts()).containsEntry("curve_quote_input",BigInteger.valueOf(88));}
        else assertThat(result.operations()).isEmpty();
    }
    @ParameterizedTest @CsvSource({"poolPda", "buybackOwner", "unknownTail", "missingBool", "multihop"})
    void unprovedRemainingAccountsOrUnsupportedRoutingNeverConfirm(String fault) {
        var raw=tailFixture("buy",true,false);var keys=(List<String>)message(raw).get("accountKeys");
        var row=((List<Map<String,Object>>)message(raw).get("instructions")).getFirst();var accounts=(List<Integer>)row.get("accounts");
        if(fault.equals("poolPda"))accounts.set(23,keys.indexOf(address(0)));
        if(fault.equals("buybackOwner"))for(String side:List.of("preTokenBalances","postTokenBalances"))for(var b:(List<Map<String,Object>>)meta(raw).get(side))if(b.get("owner").equals(address(70)))b.put("owner",address(71));
        if(fault.equals("unknownTail"))accounts.add(keys.indexOf(address(0)));
        if(fault.equals("missingBool"))row.put("data",base58(Arrays.copyOf(instruction("buy"),24)));
        if(fault.equals("multihop")){var bytes=instruction("buy");System.arraycopy(HexFormat.of().parseHex("2b644913e9f66f94"),0,bytes,0,8);row.put("data",base58(bytes));}
        assertThat(decode(raw).operations()).isEmpty();
    }
    @ParameterizedTest @CsvSource({"v2Program", "zeroRecipient", "oldFrame", "missingBuyback", "wrongBuyback", "conflictingEvent"})
    void v2RequiresExactRolesCurrentEventAndExclusiveBuybackFlow(String fault) {
        var raw=v2Fixture("buy_v2");var rows=inner(raw,0);var keys=(List<String>)message(raw).get("accountKeys");
        if(fault.equals("v2Program"))((List<Integer>)((List<Map<String,Object>>)message(raw).get("instructions")).getFirst().get("accounts")).set(9,keys.indexOf(SYSTEM));
        if(fault.equals("zeroRecipient")){var b=unbase58((String)rows.getLast().get("data"));putAddress(b,256,address(9));rows.getLast().put("data",base58(b));}
        if(fault.equals("oldFrame")){var b=unbase58((String)rows.getLast().get("data"));rows.getLast().put("data",base58(Arrays.copyOf(b,b.length-49)));}
        if(fault.equals("missingBuyback"))rows.remove(2);
        if(fault.equals("wrongBuyback"))((List<Integer>)rows.get(2).get("accounts")).set(2,keys.indexOf(address(0)));
        if(fault.equals("conflictingEvent")){var b=unbase58((String)rows.getLast().get("data"));b[24]=1;var duplicate=new HashMap<>(rows.getLast());duplicate.put("data",base58(b));rows.add(duplicate);}
        assertThat(decode(raw).operations()).isEmpty();
    }
    @ParameterizedTest @CsvSource({"init_user_volume_accumulator", "close_user_volume_accumulator", "sweep_protocol_fee", "sweep_creator_fee"})
    void exactAncillaryInvocationsRemainNonOperationsWithoutTradeInflation(String variant) {
        var only=decode(ancillaryFixture(variant,false));assertThat(only.operations()).isEmpty();
        assertThat(only.transactions().getFirst().status().name()).isEqualTo("NON_OPERATION");
        assertThat(only.transactions().getFirst().invocations()).singleElement().satisfies(i->assertThat(i.status().name()).isEqualTo("NON_OPERATION"));
        var mixed=decode(ancillaryFixture(variant,true));assertThat(mixed.operations()).hasSize(1);
        assertThat(mixed.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.DECODED);
        assertThat(mixed.transactions().getFirst().invocations()).hasSize(2);
        assertThat(mixed.operations().getFirst().attributedFees()).doesNotContainKeys("protocol","creator");
    }
    @ParameterizedTest @CsvSource({"bucket", "eventRole", "conflict", "transfer", "volumePda", "initCreate"})
    void malformedAncillaryLeavesMixedTransactionPartial(String fault) {
        String variant=fault.equals("volumePda")?"close_user_volume_accumulator":fault.equals("initCreate")?"init_user_volume_accumulator":"sweep_protocol_fee";
        var raw=ancillaryFixture(variant,true);var rows=inner(raw,1);var keys=(List<String>)message(raw).get("accountKeys");
        if(fault.equals("bucket")){var b=unbase58((String)rows.getLast().get("data"));b[192]=1;rows.getLast().put("data",base58(b));}
        if(fault.equals("eventRole"))rows.getLast().put("accounts",List.of(keys.indexOf(TOKEN)));
        if(fault.equals("conflict")){var b=unbase58((String)rows.getLast().get("data"));b[184]=8;var dup=new HashMap<>(rows.getLast());dup.put("data",base58(b));rows.add(dup);}
        if(fault.equals("transfer")||fault.equals("initCreate"))rows.removeFirst();
        if(fault.equals("volumePda"))((List<Integer>)((List<Map<String,Object>>)message(raw).get("instructions")).get(1).get("accounts")).set(1,keys.indexOf(address(0)));
        ordinarySyntheticBalances(raw);
        var result=decode(raw);assertThat(result.operations()).hasSize(1);
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.PARTIAL);
    }
    static Map<String,Object> tailFixture(String variant,boolean creator,boolean cashback) {
        var raw=fixture("1",variant,true,1);var core=roles(variant,true,0);var keys=(List<String>)message(raw).get("accountKeys");var extra=new ArrayList<>(core);
        if(cashback){extra.add("5wQVye8BaV3hr4otGYLeZ8eYSRGzDv57V4iz24zPRxZS");if(variant.equals("sell"))extra.add("5FgJwQyFzkpjbJ6EUofbxZXpfu87oEq9z8kbxnQhbaWK");}
        if(creator)extra.add("9hUHMjDaya1WKmqBVjvKpjYk198BbpiL6FpHsQbGBELG");
        extra.add(address(70));extra.add("FCbt3zT5fjXU971sw42ZC9pcr5xSzbbsSjJ5cgGhUY44");
        ((List<Map<String,Object>>)message(raw).get("instructions")).set(0,ix(keys,PUMP,extra,instruction(variant),null));
        var bytes=octoberEvent(variant,core);if(!creator)putAddress(bytes,320,SYSTEM);inner(raw,0).get(2).put("data",base58(bytes));
        addBalance(raw,extra.getLast(),core.get(4),address(70),TOKEN,9);
        if(cashback)addBalance(raw,"5wQVye8BaV3hr4otGYLeZ8eYSRGzDv57V4iz24zPRxZS",core.get(4),"5FgJwQyFzkpjbJ6EUofbxZXpfu87oEq9z8kbxnQhbaWK",TOKEN,9);
        return raw;
    }
    static void addBalance(Map<String,Object> raw,String account,String mint,String owner,String program,int decimals) {
        var keys=(List<String>)message(raw).get("accountKeys");if(!keys.contains(account))keys.add(account);
        // Fixture pre/post vectors originally share a list; add once without changing original establishing helpers.
        var pre=(List<Map<String,Object>>)meta(raw).get("preTokenBalances");var post=(List<Map<String,Object>>)meta(raw).get("postTokenBalances");
        var b=new HashMap<String,Object>(Map.of("accountIndex",keys.indexOf(account),"mint",mint,"owner",owner,"programId",program,"uiTokenAmount",Map.of("amount","0","decimals",decimals)));
        pre.add(b);if(post!=pre)post.add(new HashMap<>(b));
    }
    static Map<String,Object> ancillaryFixture(String variant,boolean mixed) {
        var raw=fixture("1","buy",true,mixed?1:0);var keys=(List<String>)message(raw).get("accountKeys");var outer=(List<Map<String,Object>>)message(raw).get("instructions");var groups=(List<Map<String,Object>>)meta(raw).get("innerInstructions");
        var rows=new ArrayList<Map<String,Object>>();byte[] bytes;List<String> accounts;String tag;
        String volume="5FgJwQyFzkpjbJ6EUofbxZXpfu87oEq9z8kbxnQhbaWK";
        if(variant.equals("init_user_volume_accumulator")) {
            tag="5e06ca73ff60e8b7";accounts=List.of(address(1),address(1),volume,SYSTEM,EVENT_AUTH,PUMP);bytes=new byte[88];System.arraycopy(HexFormat.of().parseHex("e445a52e51cb9a1d86240d48e86582d8"),0,bytes,0,16);putAddress(bytes,16,address(1));putAddress(bytes,48,address(1));
            var create=new byte[52];putU64(create,12,BigInteger.valueOf(137));putAddress(create,20,PUMP);rows.add(ix(keys,SYSTEM,List.of(address(1),volume),create,2));
        }else if(variant.equals("close_user_volume_accumulator")) {
            tag="f945a4da9667548a";accounts=List.of(address(1),volume,EVENT_AUTH,PUMP);bytes=new byte[88];System.arraycopy(HexFormat.of().parseHex("e445a52e51cb9a1d929fbdac925838f4"),0,bytes,0,16);putAddress(bytes,16,address(1));
        }else {
            tag=variant.equals("sweep_protocol_fee")?"0830be07b644b7e5":"20f6bf3408c949ba";
            accounts=List.of(address(1),address(2),address(0),address(4),TOKEN,address(8),address(9),"AhC6kfccmcNQTKYT5kdCgmhn3PASpr2vHizBge2wAzCv",SYSTEM,ATA,EVENT_AUTH,PUMP);
            bytes=new byte[193];System.arraycopy(HexFormat.of().parseHex("e445a52e51cb9a1d82a42461e48287a5"),0,bytes,0,16);int[] offsets={24,56,88,120,152};String[] values={address(0),address(3),address(4),address(9),address(1)};for(int n=0;n<offsets.length;n++)putAddress(bytes,offsets[n],values[n]);putU64(bytes,184,BigInteger.valueOf(7));bytes[192]=(byte)(variant.equals("sweep_protocol_fee")?0:1);
            rows.add(ix(keys,TOKEN,List.of(address(8),address(4),accounts.get(7),address(0)),checked(BigInteger.valueOf(7),9),2));
            if(!mixed)addBalance(raw,address(8),address(4),address(0),TOKEN,9);addBalance(raw,accounts.get(7),address(4),address(9),TOKEN,9);
        }
        outer.add(ix(keys,PUMP,accounts,HexFormat.of().parseHex(tag),null));rows.add(ix(keys,PUMP,List.of(EVENT_AUTH),bytes,2));groups.add(new HashMap<>(Map.of("index",outer.size()-1,"instructions",rows)));ordinarySyntheticBalances(raw);return raw;
    }
    @Test void savedNativeReceiveUsesProvenCreateInitializeTransferCloseWithoutUnnecessarySync() throws Exception {
        var source=savedNativeReceive();assertThat(source.rawDigest()).isEqualTo("sha256:c2cb540ff6de2408d4040ebf90a7546fbc76f8b09fe3e056c20639154e1654ab");
        assertThat(hash(source.rawResult())).isEqualTo(source.rawDigest());
        var result=decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(source),List.of("SAVED124_EXACT_SOURCE_NOT_QUALIFICATION")));
        assertThat(result.operations()).hasSize(1);var op=result.operations().getFirst();
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.DECODED);
        assertThat(op.variant()).isEqualTo("sell_v2");assertThat(op.baseAmount()).isEqualTo(new BigInteger("501165997416"));
        assertThat(op.quoteAmount()).isEqualTo(new BigInteger("1126226989"));assertThat(op.position()).isEqualTo(new PumpSwapOperations.Position(2,List.of(2),0));
        assertThat(op.lifecyclePaths()).contains(List.of(2,0,1),List.of(2,0,3),List.of(2,8));
        assertThat(op.rawDigest()).isEqualTo(source.rawDigest());assertThat(op.attributedFees()).doesNotContainKeys("protocol","creator");
    }
    @ParameterizedTest @CsvSource({"false,false,true", "true,false,false", "true,true,true"})
    void nativeInitializationOnlyNeedsSyncForLaterLamportFunding(boolean funding,boolean sync,boolean confirmed) {
        var raw=transientFixture("complete");var keys=(List<String>)message(raw).get("accountKeys");keys.set(keys.indexOf(address(3)),"So11111111111111111111111111111111111111112");keys.set(keys.indexOf(TOKEN2022),TOKEN);
        for(String side:List.of("preTokenBalances","postTokenBalances"))for(var b:(List<Map<String,Object>>)meta(raw).get(side)){if(b.get("mint").equals(address(3))){b.put("mint","So11111111111111111111111111111111111111112");b.put("uiTokenAmount",Map.of("amount","0","decimals",9));}if(b.get("programId").equals(TOKEN2022))b.put("programId",TOKEN);}
        var nativeTransfer=unbase58((String)inner(raw,1).getFirst().get("data"));nativeTransfer[9]=9;inner(raw,1).getFirst().put("data",base58(nativeTransfer));
        var create=unbase58((String)inner(raw,0).get(0).get("data"));putAddress(create,20,TOKEN);inner(raw,0).get(0).put("data",base58(create));
        if(funding){var outer=(List<Map<String,Object>>)message(raw).get("instructions");var transfer=new byte[12];ByteBuffer.wrap(transfer).order(ByteOrder.LITTLE_ENDIAN).putInt(0,2);putU64(transfer,4,BigInteger.ONE);outer.add(1,ix(keys,SYSTEM,List.of(address(1),address(5)),transfer,null));((List<Map<String,Object>>)meta(raw).get("innerInstructions")).get(1).put("index",2);if(sync){outer.add(2,ix(keys,TOKEN,List.of(address(5)),new byte[]{17},null));((List<Map<String,Object>>)meta(raw).get("innerInstructions")).get(1).put("index",3);}}
        var drain=((List<Map<String,Object>>)message(raw).get("instructions")).get(funding?(sync?4:3):2);drain.put("data",base58(checked(new BigInteger("18446744073709551615"),9)));ordinarySyntheticBalances(raw);
        var result=decode(raw);if(confirmed)assertThat(result.operations()).hasSize(1);else {assertThat(result.operations()).isEmpty();assertThat(result.transactions().getFirst().reason()).contains("SYNC_NATIVE_UNPROVED");}
    }
    static PumpSwapOperations.RecordedTransaction savedNativeReceive() throws Exception {
        try(var stream=PumpSwapRawOperationDecoderTest.class.getResourceAsStream("/fixtures/pumpswap-operations/saved-v2-sell-native-receive.json")) {
            var t=JSON.readTree(stream);return new PumpSwapOperations.RecordedTransaction(UUID.fromString(t.path("session").asString()),new ChainId(t.path("chain").asString()),t.path("signature").asString(),t.path("provider").asString(),t.path("slot").longValue(),t.path("finality").asString(),t.path("captureDisposition").asString(),t.path("sourceReference").asString(),Optional.of(Instant.parse(t.path("sourceTime").asString())),Instant.parse(t.path("receivedAt").asString()),Instant.parse(t.path("admittedAt").asString()),Instant.parse(t.path("ingestedAt").asString()),t.path("rawResult").asString(),t.path("rawDigest").asString());
        }
    }
    @ParameterizedTest @CsvSource({"legacy,buy,false", "0,sell,false", "1,buy_exact_quote_in,true"})
    void exactVariantsRetainExecutedAmountsVersionAndProof(String version,String variant,boolean token2022) {
        var result=decode(fixture(version,variant,token2022,1));
        assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.DECODED);
        assertThat(result.transactions().getFirst().transactionVersion()).isEqualTo(version);
        assertThat(result.operations()).hasSize(1);
        var op=result.operations().getFirst();
        assertThat(op.variant()).isEqualTo(variant); assertThat(op.baseAmount()).isEqualTo(new BigInteger("18446744073709551615"));
        assertThat(op.quoteAmount()).isEqualTo(BigInteger.valueOf(100));
        assertThat(op.position()).isEqualTo(new PumpSwapOperations.Position(0,List.of(),0));
        assertThat(op.transferPaths()).containsExactly(List.of(0,0),List.of(0,1));
        assertThat(op.eventPath()).containsExactly(0,2); assertThat(op.feeStatus()).isEqualTo("INCOMPLETE");
        assertThat(op.feeReasons()).contains("NETWORK_FEE_TRANSACTION_SCOPED","TIPS_UNKNOWN");
        assertThat(op.rawDigest()).isEqualTo(result.transactions().getFirst().source().rawDigest());
    }
    @Test void separatesSameAndDifferentTradersWithoutAggregation() {
        for(boolean same:List.of(false,true)) {
            var raw=fixture("1","buy",true,2);
            if(same) { // Change trader and matching event role, preserving two invocation identities.
                var keys=(List<String>)message(raw).get("accountKeys"); keys.set(keys.indexOf(address(31)),address(1));
                for(String side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)meta(raw).get(side))if(row.get("owner").equals(address(31)))row.put("owner",address(1));
                var event=event("buy",roles("buy",true,30)); putAddress(event,160,address(1));
                inner(raw,1).get(2).put("data",base58(event));
            }
            var result=decode(raw); assertThat(result.operations()).hasSize(2);
            assertThat(result.operations()).extracting(o->o.position().outerIndex()).containsExactly(0,1);
            assertThat(result.operations()).extracting(PumpSwapOperations.Operation::baseAmount).containsOnly(new BigInteger("18446744073709551615"));
        }
    }
    @Test void failedTransactionHasCountedDispositionAndNoOperations() {
        var raw=fixture("1","buy",false,1); meta(raw).put("err",Map.of("InstructionError",List.of(0,Map.of("Custom",1))));
        var result=decode(raw); assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.FAILED); assertThat(result.operations()).isEmpty();
    }
    @ParameterizedTest @CsvSource({"missingErr,MISSING_ERROR", "missingVersion,UNSUPPORTED_VERSION", "path,PATH_UNKNOWN", "index,ACCOUNT_INDEX_INVALID", "role,INVOCATION_LAYOUT", "eventRole,EVENT_ROLE_MISMATCH", "extraEvent,EVENT_LAYOUT", "bool,EVENT_LAYOUT", "transferMint,TRANSFER_MISMATCH", "extension,UNSUPPORTED_TOKEN_INSTRUCTION", "missingTransfer,PRINCIPAL_UNPROVED", "conflictEvent,EVENT_CONFLICT"})
    void unsupportedEvidenceNeverConfirmsPrincipal(String fault,String reason) {
        var raw=fixture("1","buy",true,1); var ins=inner(raw,0); var keys=(List<String>)message(raw).get("accountKeys");
        switch(fault) {
            case "missingErr" -> meta(raw).remove("err"); case "missingVersion" -> raw.remove("version");
            case "path" -> ins.getFirst().put("stackHeight",3); case "index" -> ins.getFirst().put("programIdIndex",9999);
            case "role" -> ((List<Integer>)((List<Map<String,Object>>)message(raw).get("instructions")).getFirst().get("accounts")).set(13,keys.indexOf(TOKEN));
            case "eventRole" -> ins.get(2).put("accounts",List.of(keys.indexOf(TOKEN)));
            case "extraEvent" -> ins.get(2).put("data",base58(Arrays.copyOf(event("buy",roles("buy",true,0)),449)));
            case "bool" -> {var b=event("buy",roles("buy",true,0)); b[368]=2; ins.get(2).put("data",base58(b));}
            case "transferMint" -> ((List<Integer>)ins.getFirst().get("accounts")).set(1,keys.indexOf(roles("buy",true,0).get(4)));
            case "extension" -> ins.getFirst().put("data",base58(new byte[]{26,1}));
            case "missingTransfer" -> ins.removeFirst();
            case "conflictEvent" -> {var b=event("buy",roles("buy",true,0)); b[24]=1; var duplicate=new HashMap<>(ins.get(2)); duplicate.put("data",base58(b)); ins.add(duplicate);}
        }
        var result=decode(raw); assertThat(result.operations()).isEmpty();
        assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().getFirst().reason()).contains(reason);
    }
    @Test void repeatPermutationAndEqualDuplicateAreIdenticalButConflictFails() {
        var a=source(fixture("0","buy",false,1),"a"); var b=source(fixture("1","sell",true,1),"b");
        var first=decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("GAPS_UNKNOWN")));
        assertThat(decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(b,a,a),List.of("GAPS_UNKNOWN")))).isEqualTo(first);
        var changed=new PumpSwapOperations.RecordedTransaction(a.session(),a.chain(),a.signature(),a.provider(),a.slot(),a.finality(),a.captureDisposition(),a.sourceReference(),a.sourceTime(),a.receivedAt(),a.admittedAt(),a.ingestedAt(),a.rawResult()+" ",hash(a.rawResult()+" "));
        assertThatThrownBy(()->decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(a,changed),List.of()))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CONFLICT");
    }
    @Test void rawHashIdentityAndBatchBoundRejectRatherThanTruncate() {
        var a=source(fixture("1","buy",false,1),"a");
        var tampered=new PumpSwapOperations.RecordedTransaction(a.session(),a.chain(),a.signature(),a.provider(),a.slot(),a.finality(),a.captureDisposition(),a.sourceReference(),a.sourceTime(),a.receivedAt(),a.admittedAt(),a.ingestedAt(),a.rawResult(),"sha256:"+"0".repeat(64));
        assertThatThrownBy(()->decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(tampered),List.of()))).hasMessageContaining("HASH_MISMATCH");
        var many=new ArrayList<PumpSwapOperations.RecordedTransaction>(); for(int i=0;i<129;i++)many.add(source(fixture("1","sell",false,1),"tx"+i));
        assertThatThrownBy(()->decoder.decode(new PumpSwapOperations.RecordedBatch(many,List.of()))).hasMessageContaining("LIMIT");
    }
    @Test void repeatedIdenticalFramesDoNotDuplicateOperationAndFeesStaySourceDeclared() {
        var raw=fixture("1","buy",true,1);var b=event("buy",roles("buy",true,0));putU64(b,440,BigInteger.valueOf(7));
        inner(raw,0).get(2).put("data",base58(b));inner(raw,0).add(new HashMap<>(inner(raw,0).get(2)));
        var result=decode(raw);assertThat(result.operations()).hasSize(1);
        assertThat(result.operations().getFirst().eventDeclaredFees().get("buyback")).isEqualTo(BigInteger.valueOf(7));
        assertThat(result.operations().getFirst().attributedFees()).doesNotContainKeys("buyback","cashback","protocol","creator");
    }
    @Test void invalidLifecycleInstructionCannotHideBehindStableBalances() {
        var raw=fixture("1","buy",true,1);var r=roles("buy",true,0);
        inner(raw,0).add(ix((List<String>)message(raw).get("accountKeys"),TOKEN2022,List.of(r.get(5),r.get(1),r.get(1)),new byte[]{9,1},2));
        var result=decode(raw);assertThat(result.operations()).isEmpty();assertThat(result.transactions().getFirst().reason()).contains("TOKEN_LIFECYCLE_LAYOUT");
    }
    @ParameterizedTest @CsvSource({"complete,true", "missingAta,false", "missingCreate,false", "missingInit,false", "missingClose,false", "wrongOwner,false"})
    void transientAtaRequiresCompleteOrderedProof(String fault,boolean confirmed) {
        var raw=transientFixture(fault);var result=decode(raw);
        if(confirmed){assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().lifecyclePaths()).containsExactly(List.of(0,0),List.of(0,1),List.of(3));}
        else {assertThat(result.operations()).isEmpty();assertThat(result.transactions()).hasSize(1);assertThat(result.transactions().getFirst().reason()).contains("UNPROVED");}
    }
    @ParameterizedTest @CsvSource({"valid", "missing", "late", "bytes", "accounts", "wrongAccount", "wrongProgram", "size", "owner", "mint", "close", "epoch", "prefunded", "extension", "balance"})
    void immutableOwnerRequiresExactNonNativeLifecycleAndGlobalCorroboration(String fault) {
        var raw=transientFixture("complete");var roles=roles("buy",true,0);var keys=(List<String>)message(raw).get("accountKeys");var setup=inner(raw,0);
        var create=new byte[52];putU64(create,12,BigInteger.valueOf(fault.equals("size")?171:170));putAddress(create,20,TOKEN2022);setup.getFirst().put("data",base58(create));
        setup.add(1,ix(keys,fault.equals("wrongProgram")?TOKEN:TOKEN2022,List.of(fault.equals("wrongAccount")?roles.get(7):roles.get(5)),fault.equals("bytes")?new byte[]{22,0}:new byte[]{22},2));
        if(fault.equals("missing"))setup.remove(1);if(fault.equals("late"))Collections.swap(setup,1,2);if(fault.equals("accounts"))((List<Integer>)setup.get(1).get("accounts")).add(keys.indexOf(roles.get(1)));
        if(fault.equals("owner")){var init=new byte[33];init[0]=18;putAddress(init,1,roles.get(0));setup.getLast().put("data",base58(init));}if(fault.equals("mint"))((List<Integer>)setup.getLast().get("accounts")).set(1,keys.indexOf(roles.get(4)));
        if(fault.equals("epoch"))setup.addFirst(new HashMap<>(setup.getFirst()));if(fault.equals("prefunded"))setup.getFirst().put("data",base58(new byte[]{8,0,0,0}));
        var outer=(List<Map<String,Object>>)message(raw).get("instructions");var close=outer.removeLast();outer.removeLast();outer.add(ix(keys,TOKEN2022,List.of(roles.get(5),roles.get(3),roles.get(7),roles.get(1)),checked(new BigInteger("18446744073709551615"),6),null));if(!fault.equals("close"))outer.add(close);
        if(fault.equals("extension"))outer.add(ix(keys,TOKEN2022,List.of(roles.get(5)),new byte[]{26,1},null));
        ordinarySyntheticBalances(raw);
        if(fault.equals("balance")){var balances=new ArrayList<>((List<Map<String,Object>>)meta(raw).get("postTokenBalances"));balances.add(new HashMap<>(Map.of("accountIndex",keys.indexOf(roles.get(5)),"mint",roles.get(3),"owner",roles.get(1),"programId",TOKEN2022,"uiTokenAmount",Map.of("amount","1","decimals",6))));meta(raw).put("postTokenBalances",balances);}
        var result=decode(raw);if(fault.equals("valid")){assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().baseAmount()).isEqualTo(new BigInteger("18446744073709551615"));assertThat(result.operations().getFirst().lifecyclePaths()).contains(List.of(0),List.of(0,0),List.of(0,1),List.of(0,2),List.of(3));}
        else assertThat(result.operations()).isEmpty();
    }
    static Map<String,Object> transientFixture(String fault) {
        var raw=fixture("1","buy",true,1);var r=roles("buy",true,0);var keys=(List<String>)message(raw).get("accountKeys");
        for(String side:List.of("preTokenBalances","postTokenBalances"))((List<Map<String,Object>>)meta(raw).get(side)).removeIf(row->row.get("accountIndex").equals(keys.indexOf(r.get(5))));
        var outer=(List<Map<String,Object>>)message(raw).get("instructions");var swap=outer.removeFirst();
        outer.add(ix(keys,fault.equals("missingAta")?SYSTEM:ATA,List.of(r.get(1),r.get(5),r.get(1),r.get(3),SYSTEM,TOKEN2022),new byte[]{0},null));outer.add(swap);
        outer.add(ix(keys,TOKEN2022,List.of(r.get(5),r.get(3),address(90),r.get(1)),checked(new BigInteger("18446744073709551615"),6),null));
        outer.add(ix(keys,TOKEN2022,List.of(r.get(5),r.get(1),r.get(1)),new byte[]{9},null));
        if(fault.equals("missingClose"))outer.removeLast();
        var create=new byte[52];putU64(create,12,BigInteger.valueOf(165));putAddress(create,20,TOKEN2022);
        var init=new byte[33];init[0]=18;putAddress(init,1,fault.equals("wrongOwner")?r.get(0):r.get(1));
        var setup=new ArrayList<Map<String,Object>>();if(!fault.equals("missingCreate"))setup.add(ix(keys,SYSTEM,List.of(r.get(1),r.get(5)),create,2));if(!fault.equals("missingInit"))setup.add(ix(keys,TOKEN2022,List.of(r.get(5),r.get(3)),init,2));
        var groups=(List<Map<String,Object>>)meta(raw).get("innerInstructions");groups.getFirst().put("index",1);groups.addFirst(new HashMap<>(Map.of("index",0,"instructions",setup)));ordinarySyntheticBalances(raw);return raw;
    }
    @ParameterizedTest @CsvSource({"keys", "instructions", "balances", "depth", "bytes", "rawBytes", "totalBytes"})
    void everyInputWorkLimitRejectsWithoutTruncation(String bound) {
        var raw=fixture("1","buy",false,1);var keys=(List<String>)message(raw).get("accountKeys");
        switch(bound) {
            case "keys" -> {while(keys.size()<2049)keys.add(address(90));}
            case "instructions" -> {var rows=(List<Map<String,Object>>)message(raw).get("instructions");while(rows.size()<10001)rows.add(rows.getFirst());}
            case "balances" -> {var rows=(List<Map<String,Object>>)meta(raw).get("preTokenBalances");while(rows.size()<10001)rows.add(rows.getFirst());}
            case "depth" -> inner(raw,0).getFirst().put("stackHeight",65);
            case "bytes" -> inner(raw,0).getFirst().put("data",base58(new byte[4097]));
            case "rawBytes" -> raw.put("padding","x".repeat(8_000_000));
            case "totalBytes" -> raw.put("padding","x".repeat(7_200_000));
        }
        if(bound.equals("totalBytes")){var many=new ArrayList<PumpSwapOperations.RecordedTransaction>();for(int n=0;n<9;n++)many.add(source(raw,"limit"+n));assertThatThrownBy(()->decoder.decode(new PumpSwapOperations.RecordedBatch(many,List.of()))).hasMessageContaining("LIMIT");}
        else assertThatThrownBy(()->decode(raw)).hasMessageContaining("LIMIT");
    }
    @Test void malformedErrorDoesNotBecomeFailedOrSuccessfulEvidence() {
        var raw=fixture("1","buy",false,1);meta(raw).put("err",false);var result=decode(raw);
        assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.INVALID);
        assertThat(result.transactions().getFirst().reason()).isEqualTo("MALFORMED_ERROR");assertThat(result.operations()).isEmpty();
    }
    @ParameterizedTest @CsvSource({"37,false","38,true"})
    void fixedSavedWindowRecognitionCountsOnlyFullyDecodedTransactions(int recognized,boolean majority) {
        var sources=new ArrayList<PumpSwapOperations.RecordedTransaction>();
        for(int n=0;n<124;n++){var raw=fixture("1","buy",true,1);if(n>=75)meta(raw).put("err",Map.of("InstructionError",List.of(0,Map.of("Custom",1))));else if(n>=recognized)inner(raw,0).removeFirst();sources.add(source(raw,"boundary"+n));}
        var result=decoder.decode(new PumpSwapOperations.RecordedBatch(sources,List.of("SYNTHETIC_BOUNDARY_NOT_REAL_WINDOW")));
        long count=result.transactions().stream().filter(t->t.status()==PumpSwapOperations.Status.DECODED).count();
        assertThat(count).isEqualTo(recognized);assertThat(count>=38).isEqualTo(majority);
        assertThat(result.transactions()).hasSize(124);assertThat(result.transactions().stream().filter(t->t.status()==PumpSwapOperations.Status.FAILED).count()).isEqualTo(49);
    }
    @Test void oneConfirmedAndOneUnresolvedInvocationRemainsPartial() {
        var raw=fixture("1","buy",true,2);inner(raw,1).removeFirst();var result=decode(raw);
        assertThat(result.operations()).hasSize(1);assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.PARTIAL);
        assertThat(result.transactions().getFirst().invocations()).extracting(PumpSwapOperations.Invocation::status).containsExactly(PumpSwapOperations.Status.DECODED,PumpSwapOperations.Status.UNSUPPORTED);
    }
    @Test void loadedKeysAndCompleteNestedCpiPathRemainObservedIdentity() {
        var raw=fixture("0","sell",false,1);var keys=(List<String>)message(raw).get("accountKeys");var loaded=new ArrayList<>(keys.subList(18,keys.size()));keys.subList(18,keys.size()).clear();meta(raw).put("loadedAddresses",Map.of("writable",loaded,"readonly",List.of()));
        var rows=(List<Map<String,Object>>)message(raw).get("instructions");var swap=rows.removeFirst();rows.add(new HashMap<>(Map.of("programIdIndex",keys.indexOf(SYSTEM),"accounts",List.of(),"data","")));swap.put("stackHeight",2);
        var inside=inner(raw,0);inside.forEach(i->i.put("stackHeight",3));inside.addFirst(swap);var result=decode(raw);
        assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().position()).isEqualTo(new PumpSwapOperations.Position(0,List.of(0),0));
        assertThat(result.operations().getFirst().eventPath()).containsExactly(0,0,2);assertThat(result.operations().getFirst().transferPaths()).containsExactly(List.of(0,0,0),List.of(0,0,1));
    }
    @Test void operationCountBoundRejectsBatchWithoutPublishingPrefix() {
        var sources=new ArrayList<PumpSwapOperations.RecordedTransaction>();
        for(int n=0;n<112;n++) {
            var raw=fixture("1","buy",true,1);var outer=(List<Map<String,Object>>)message(raw).get("instructions");var groups=(List<Map<String,Object>>)meta(raw).get("innerInstructions");
            inner(raw,0).getFirst().put("data",base58(checked(BigInteger.ONE,6)));var event=event("buy",roles("buy",true,0));putU64(event,24,BigInteger.ONE);inner(raw,0).getLast().put("data",base58(event));
            for(int p=1;p<90;p++){outer.add(outer.getFirst());groups.add(new HashMap<>(Map.of("index",p,"instructions",groups.getFirst().get("instructions"))));}
            ordinarySyntheticBalances(raw);
            sources.add(source(raw,"operations-limit"+n));
        }
        assertThatThrownBy(()->decoder.decode(new PumpSwapOperations.RecordedBatch(sources,List.of()))).hasMessageContaining("LIMIT");
    }
    @Test void outputExpansionRejectsInsteadOfReturningOversizedRawLineage() {
        var sources=new ArrayList<PumpSwapOperations.RecordedTransaction>();
        for(int n=0;n<5;n++){var raw=fixture("1","buy",true,1);raw.put("padding","\"".repeat(3_500_000));sources.add(source(raw,"output-limit"+n));}
        assertThatThrownBy(()->decoder.decode(new PumpSwapOperations.RecordedBatch(sources,List.of()))).hasStackTraceContaining("DECODER_OUTPUT_LIMIT");
    }
    @Test void orphanEventFrameCannotBeSilentlyDroppedFromFullyRecognizedTransaction() {
        var raw=fixture("1","buy",false,1);var orphan=new HashMap<>(inner(raw,0).get(2));orphan.remove("stackHeight");
        ((List<Map<String,Object>>)message(raw).get("instructions")).add(orphan);var result=decode(raw);
        assertThat(result.operations()).hasSize(1);assertThat(result.transactions().getFirst().status()).isEqualTo(PumpSwapOperations.Status.PARTIAL);
        assertThat(result.transactions().getFirst().reason()).contains("ORPHAN_EVENT");
    }
    PumpSwapOperations.Result decode(Map<String,Object> raw) {return decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(source(raw,"fixture")),List.of("UNRECEIVED_LOSS_UNKNOWN")));}
    static PumpSwapOperations.RecordedTransaction source(Map<String,Object> raw,String signature) {
        ((Map<String,Object>)raw.get("transaction")).put("signatures",List.of(signature)); var text=JSON.writeValueAsString(raw);
        var time=Instant.parse("2026-10-09T01:00:00.123456789Z");
        return new PumpSwapOperations.RecordedTransaction(UUID.fromString("484bec54-c92b-438f-9fe0-89f1c8150844"),ChainId.SOLANA_MAINNET,signature,"fixture-provider",100,"finalized","CAPTURED","capture-disposition/"+signature,Optional.of(time),time,time,time,text,hash(text));
    }
    static Map<String,Object> fixture(String version,String variant,boolean t22,int count) {
        var keys=new ArrayList<String>(); var outer=new ArrayList<Map<String,Object>>(); var groups=new ArrayList<Map<String,Object>>(); var balances=new ArrayList<Map<String,Object>>();
        for(int n=0;n<count;n++) {
            var r=roles(variant,t22,n*30); for(String k:r)if(!keys.contains(k))keys.add(k);
            outer.add(ix(keys,PUMP,r,instruction(variant),null)); var sell=variant.equals("sell");
            var transfers=new ArrayList<Map<String,Object>>();
            transfers.add(ix(keys,r.get(11),List.of(r.get(sell?5:7),r.get(3),r.get(sell?7:5),r.get(sell?1:0)),checked(new BigInteger("18446744073709551615"),6),2));
            transfers.add(ix(keys,r.get(12),List.of(r.get(sell?8:6),r.get(4),r.get(sell?6:8),r.get(sell?0:1)),checked(BigInteger.valueOf(100),9),2));
            transfers.add(ix(keys,PUMP,List.of(EVENT_AUTH),event(variant,r),2)); groups.add(new HashMap<>(Map.of("index",n,"instructions",transfers)));
            for(int role:List.of(5,6,7,8))balances.add(new HashMap<>(Map.of("accountIndex",keys.indexOf(r.get(role)),"mint",r.get(role==5||role==7?3:4),"owner",r.get(role<7?1:0),"programId",r.get(role==5||role==7?11:12),"uiTokenAmount",Map.of("amount","0","decimals",role==5||role==7?6:9))));
        }
        var result=new HashMap<String,Object>(Map.of("slot",100,"version",version.equals("legacy")?"legacy":Integer.valueOf(version),"transaction",new HashMap<>(Map.of("signatures",List.of("fixture"),"message",new HashMap<>(Map.of("accountKeys",keys,"instructions",outer)))),"meta",new HashMap<>(Map.of("innerInstructions",groups,"preTokenBalances",balances,"postTokenBalances",balances,"fee",5000))));
        meta(result);ordinarySyntheticBalances(result);return result;
    }
    static void ordinarySyntheticBalances(Map<String,Object> raw) {
        // Synthetic ledger only: exact ordinary TransferChecked arguments determine valid source-derived endpoint fixtures.
        var net=new HashMap<Integer,BigInteger>();var minimum=new HashMap<Integer,BigInteger>();var rows=new ArrayList<Map<String,Object>>();var outer=(List<Map<String,Object>>)message(raw).get("instructions");
        for(int index=0;index<outer.size();index++){rows.add(outer.get(index));for(var group:(List<Map<String,Object>>)meta(raw).get("innerInstructions"))if(group.get("index").equals(index))rows.addAll((List<Map<String,Object>>)group.get("instructions"));}
        var keys=(List<String>)message(raw).get("accountKeys");
        for(var row:rows){String program=keys.get((Integer)row.get("programIdIndex"));var b=unbase58((String)row.get("data"));if((program.equals(TOKEN)||program.equals(TOKEN2022))&&b.length==10&&b[0]==12){var a=(List<Integer>)row.get("accounts");var amount=BigInteger.ZERO;for(int n=0;n<8;n++)amount=amount.add(BigInteger.valueOf(Byte.toUnsignedInt(b[n+1])).shiftLeft(n*8));net.merge(a.get(0),amount.negate(),BigInteger::add);minimum.merge(a.get(0),net.get(a.get(0)).min(BigInteger.ZERO),BigInteger::min);net.merge(a.get(2),amount,BigInteger::add);minimum.merge(a.get(2),net.get(a.get(2)).min(BigInteger.ZERO),BigInteger::min);}}
        for(String side:List.of("preTokenBalances","postTokenBalances")){var copy=new ArrayList<Map<String,Object>>();for(var row:(List<Map<String,Object>>)meta(raw).get(side)){var next=new HashMap<>(row);var ui=new HashMap<>((Map<String,Object>)row.get("uiTokenAmount"));int account=(Integer)row.get("accountIndex");var initial=minimum.getOrDefault(account,BigInteger.ZERO).negate();ui.put("amount",(side.equals("preTokenBalances")?initial:initial.add(net.getOrDefault(account,BigInteger.ZERO))).toString());next.put("uiTokenAmount",ui);copy.add(next);}meta(raw).put(side,copy);}
    }
    static List<String> roles(String variant,boolean t22,int offset) {var r=new ArrayList<String>(); for(int i=0;i<(variant.equals("sell")?21:23);i++)r.add(address(offset+i));r.set(11,t22?TOKEN2022:TOKEN);r.set(12,TOKEN);r.set(13,SYSTEM);r.set(14,ATA);r.set(15,EVENT_AUTH);r.set(16,PUMP);return r;}
    static Map<String,Object> ix(List<String> keys,String program,List<String> accounts,byte[] data,Integer depth) {if(!keys.contains(program))keys.add(program);var indexes=new ArrayList<Integer>();for(String a:accounts){if(!keys.contains(a))keys.add(a);indexes.add(keys.indexOf(a));}var row=new HashMap<String,Object>();row.put("programIdIndex",keys.indexOf(program));row.put("accounts",indexes);row.put("data",base58(data));if(depth!=null)row.put("stackHeight",depth);return row;}
    static byte[] instruction(String variant) {var b=new byte[variant.equals("sell")?24:25];System.arraycopy(HexFormat.of().parseHex(variant.equals("sell")?"33e685a4017f83ad":variant.equals("buy")?"66063d1201daebea":"c62e1552b4d9e870"),0,b,0,8);return b;}
    static byte[] checked(BigInteger amount,int decimals){var b=new byte[10];b[0]=12;putU64(b,1,amount);b[9]=(byte)decimals;return b;}
    static byte[] event(String variant,List<String> r) {
        boolean sell=variant.equals("sell");var b=new byte[sell?400:445+variant.length()];System.arraycopy(HexFormat.of().parseHex("e445a52e51cb9a1d"+(sell?"3e2f370aa503dc2a":"67f4521f2cf57777")),0,b,0,16);
        putU64(b,16,BigInteger.valueOf(-1));putU64(b,24,new BigInteger("18446744073709551615")); for(int i:List.of(72,112,120))putU64(b,i,BigInteger.valueOf(100));
        int[] roles={0,1,5,6,9,10,18}; for(int i=0;i<7;i++)putAddress(b,128+i*32,r.get(roles[i]));
        if(!sell){ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(409,variant.length());System.arraycopy(variant.getBytes(StandardCharsets.UTF_8),0,b,413,variant.length());}return b;
    }
    static void putU64(byte[] b,int offset,BigInteger value){for(int i=0;i<8;i++)b[offset+i]=value.shiftRight(i*8).byteValue();}
    static void putAddress(byte[] b,int offset,String address){System.arraycopy(unbase58(address),0,b,offset,32);}
    static String address(int value){var b=new byte[32];b[0]=(byte)(value+1);return base58(b);}
    static String base58(byte[] bytes){String alphabet="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";var n=new BigInteger(1,bytes);var s=new StringBuilder();while(n.signum()>0){var q=n.divideAndRemainder(BigInteger.valueOf(58));s.append(alphabet.charAt(q[1].intValue()));n=q[0];}for(byte b:bytes){if(b!=0)break;s.append('1');}return s.reverse().toString();}
    static byte[] unbase58(String s){var n=BigInteger.ZERO;String a="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";for(char c:s.toCharArray())n=n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(a.indexOf(c)));var b=n.toByteArray();if(b.length>0&&b[0]==0)b=Arrays.copyOfRange(b,1,b.length);int z=0;while(z<s.length()&&s.charAt(z)=='1')z++;var out=new byte[z+b.length];System.arraycopy(b,0,out,z,b.length);return out;}
    static String hash(String text){try{return "sha256:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static Map<String,Object> message(Map<String,Object> r){return (Map<String,Object>)((Map<String,Object>)r.get("transaction")).get("message");}
    static Map<String,Object> meta(Map<String,Object> r){var m=(Map<String,Object>)r.get("meta");if(!m.containsKey("err"))m.put("err",null);return m;}
    static List<Map<String,Object>> inner(Map<String,Object> r,int n){return (List<Map<String,Object>>)((List<Map<String,Object>>)meta(r).get("innerInstructions")).get(n).get("instructions");}
}
