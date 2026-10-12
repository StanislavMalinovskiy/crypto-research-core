package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.cryptoresearch.marketdata.api.*;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapOrdinaryPoolNetProof;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapRawOperationParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PumpSwapVersionedPoolCandlesTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final Instant FROM=Instant.parse("2026-10-11T11:10:00Z"),TO=Instant.parse("2026-10-11T11:35:00Z"),D=Instant.parse("2026-10-12T14:00:00Z"),K=Instant.parse("2030-01-01T00:00:00Z");
    static final PumpSwapPoolLegs.PoolBinding GTA=PumpSwapPoolLegDecoderTest.GTA;
    final CalculatePumpSwapVersionedPoolCandlesUseCase calculator=new CalculatePumpSwapVersionedPoolCandlesUseCase();
    @Test void completeDenseWindowMustProcessAllSourcesWithoutRaisingOldDecoderBound() {
        var many=new ArrayList<PumpSwapOperations.RecordedTransaction>();
        for(int n=0;n<129;n++)many.add(cloneIdentity(PumpSwapPoolLegDecoderTest.source("term"),n));
        var q=request(many);
        assertThatCode(()->calculator.calculate(q)).as("complete129 sources through unchanged bounded chunks").doesNotThrowAnyException();
        var result=calculator.calculate(q);assertThat(result.transactions()).hasSize(129);assertThat(result.operations()).hasSize(129);
        assertThat(result.reconciliation().statuses()).containsEntry("DECODED",129);
        assertThat(new DecodePumpSwapPoolLegsUseCase().decode(new PumpSwapPoolLegs.Request(new PumpSwapOperations.RecordedBatch(List.of(many.getFirst()),List.of()),List.of(GTA))).poolLegs()).hasSize(1);
    }
    @Test void ordinaryPriceUsesActualPoolNetNotUserExecutionAmount() {
        var source=ordinary();var old=new DecodePumpSwapOperationsUseCase().decode(PumpSwapPoolLegDecoderTest.batch(source));
        assertThat(old.operations()).hasSize(2);
        var target=old.operations().stream().filter(o->o.pool().equals(GTA.pool())).toList();assertThat(target).hasSize(1);
        assertThat(target.getFirst().quoteAmount()).isEqualTo(BigInteger.valueOf(347841));
        var proof=ordinaryPriceProof(source,target.getFirst());
        assertThat(proof.quote()).as("actual source quote vault delta, not347841 user amount").contains(BigInteger.valueOf(350476));
        var report=calculator.calculate(request(List.of(source)));assertThat(report.operations()).hasSize(2);
        assertThat(report.operations().stream().filter(o->o.pool().pool().equals(GTA.pool())).findFirst().orElseThrow().poolNetQuoteLamports()).contains(BigInteger.valueOf(350476));
        assertThat(report.operations()).filteredOn(o->o.disposition().equals("OUTSIDE_TARGET")).hasSize(1);
    }
    @Test void mixedOrdinaryAndMultiNeverReuseUserPriceOrDuplicateVersions() {
        var report=calculator.calculate(request(List.of(ordinary(),PumpSwapPoolLegDecoderTest.source("term"))));
        assertThat(report.operations()).hasSize(3);assertThat(report.operations()).extracting(PumpSwapVersionedPoolCandles.Operation::priceBasis).containsOnly("POOL_NET_SOL_PER_BASE");
        assertThat(report.operations().stream().filter(o->o.kind().equals("ORDINARY")&&o.pool().pool().equals(GTA.pool())).findFirst().orElseThrow().poolNetQuoteLamports()).contains(BigInteger.valueOf(350476));
    }
    @Test void chunkPackagingAndPermutationCannotChangeGlobalResult() {
        var inputs=List.of(ordinary(),PumpSwapPoolLegDecoderTest.source("term"),PumpSwapPoolLegDecoderTest.source("flash"));
        var a=calculator.calculate(request(inputs),128);var b=calculator.calculate(request(inputs),64);var c=calculator.calculate(request(inputs),1);
        assertThat(a).isEqualTo(b).isEqualTo(c);
        var reverse=new ArrayList<>(inputs);Collections.reverse(reverse);assertThat(calculator.calculate(request(reverse))).isEqualTo(a);
        var duplicates=new ArrayList<>(inputs);duplicates.add(inputs.getFirst());assertThat(calculator.calculate(request(duplicates))).isEqualTo(a);
    }
    @Test void competingProviderAndConflictingRawRejectBeforeSelection() {
        var s=ordinary();var other=new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),"alternate",s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),s.sourceTime(),s.receivedAt(),s.admittedAt(),s.ingestedAt(),s.rawResult(),s.rawDigest());
        assertThatThrownBy(()->calculator.calculate(request(List.of(s,other)))).hasMessage("VERSIONED_CANDLES_AMBIGUOUS_SOURCE");
        var raw=(tools.jackson.databind.node.ObjectNode)JSON.readTree(s.rawResult());raw.put("blockTime",raw.path("blockTime").longValue()+1);
        assertThatThrownBy(()->calculator.calculate(request(List.of(s,changedRaw(s,raw))))).hasMessage("VERSIONED_CANDLES_AMBIGUOUS_SOURCE");
    }
    @ParameterizedTest @ValueSource(strings={"missingTime","missingIndex","futureK","unknownTarget","failedControl"})
    void uncertaintyIsExplicitAndFailedExecutionDoesNotTaint(String fault) {
        var s=PumpSwapPoolLegDecoderTest.source("term");var raw=(tools.jackson.databind.node.ObjectNode)JSON.readTree(s.rawResult());
        if(fault.equals("missingTime"))raw.remove("blockTime");if(fault.equals("missingIndex"))raw.remove("transactionIndex");
        if(fault.equals("unknownTarget")||fault.equals("failedControl")) {
            var all=(tools.jackson.databind.node.ArrayNode)raw.path("meta").path("innerInstructions").get(0).path("instructions");
            for(var ix:all) {var bytes=PumpSwapPoolLegDecoderTest.decode58(ix.path("data").asText());if(bytes.length==24&&HexFormat.of().formatHex(bytes,0,8).equals("2b644913e9f66f94")){bytes[0]^=1;((tools.jackson.databind.node.ObjectNode)ix).put("data",PumpSwapPoolLegDecoderTest.encode58(bytes));}}
            if(fault.equals("failedControl"))((tools.jackson.databind.node.ObjectNode)raw.path("meta")).put("err","AccountInUse");
        }
        var changed=changedRaw(s,raw);
        if(fault.equals("missingTime"))changed=copy(changed,Optional.empty(),changed.provider(),changed.rawResult(),changed.rawDigest());
        var q=request(List.of(changed));if(fault.equals("futureK"))q=new PumpSwapVersionedPoolCandles.Request(q.batch(),q.pools(),q.decoderVersion(),q.fromInclusive(),q.toExclusive(),D,D.minusSeconds(1));
        var result=calculator.calculate(q);
        if(fault.equals("failedControl")){assertThat(result.uncertainties()).isEmpty();assertThat(result.operations()).isEmpty();}
        else if(fault.equals("missingIndex")){assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().transactionIndex()).isEmpty();}
        else if(fault.equals("futureK")){assertThat(result.operations()).hasSize(1);assertThat(result.operations().getFirst().disposition()).isEqualTo("FUTURE_KNOWLEDGE");}
        else assertThat(result.uncertainties()).isNotEmpty();
    }
    @Test void malformedTimePositionAndEarlyDerivationReject() {
        var s=ordinary();var raw=(tools.jackson.databind.node.ObjectNode)JSON.readTree(s.rawResult());raw.put("transactionIndex",-1);
        assertThatThrownBy(()->calculator.calculate(request(List.of(changedRaw(s,raw))))).hasMessage("VERSIONED_CANDLES_INVALID_INDEX");
        var q=request(List.of(s));
        assertThatThrownBy(()->calculator.calculate(new PumpSwapVersionedPoolCandles.Request(q.batch(),q.pools(),q.decoderVersion(),FROM,TO,s.receivedAt().minusSeconds(1),K))).hasMessage("VERSIONED_CANDLES_DERIVATION_PRECEDES_SOURCE");
        assertThatThrownBy(()->calculator.calculate(new PumpSwapVersionedPoolCandles.Request(q.batch(),q.pools(),"v4",FROM,TO,D,K))).hasMessage("VERSIONED_CANDLES_UNSUPPORTED_VERSION");
    }
    @Test void twoOrdinaryInvocationsSharingVaultUseSeparateContributions() {
        var synthetic=twoOrdinary();var binding=syntheticBinding();
        var q=new PumpSwapVersionedPoolCandles.Request(new PumpSwapOperations.RecordedBatch(List.of(synthetic),List.of("SYNTHETIC")),List.of(binding),"pumpswap-pool-legs-v5",FROM,TO,D,K);
        var report=calculator.calculate(q);assertThat(report.operations()).hasSize(2);
        assertThat(report.operations()).extracting(o->o.poolNetQuoteLamports().orElseThrow()).containsExactly(BigInteger.valueOf(100),BigInteger.valueOf(100));
        assertThat(report.operations()).extracting(PumpSwapVersionedPoolCandles.Operation::identity).doesNotHaveDuplicates();
    }
    @Test void confirmedBaseWithMissingQuoteRemainsCountedUnpriced() {
        var source=PumpSwapPoolLegDecoderTest.mutate(PumpSwapPoolLegDecoderTest.source("term"),"quoteMissing");
        var report=calculator.calculate(request(List.of(source)));assertThat(report.operations()).hasSize(1);
        assertThat(report.operations().getFirst().price()).isEmpty();assertThat(report.reconciliation().unpriced()).isEqualTo(1);
    }
    @Test void wholeWindowCountAndPositionBoundsFailClosedBeforeParsing() {
        var original=ordinary();var many=new ArrayList<PumpSwapOperations.RecordedTransaction>();
        for(int n=0;n<4097;n++)many.add(new PumpSwapOperations.RecordedTransaction(original.session(),original.chain(),"declared-synthetic-"+n,original.provider(),original.slot(),original.finality(),original.captureDisposition(),original.sourceReference(),original.sourceTime(),original.receivedAt(),original.admittedAt(),original.ingestedAt(),original.rawResult(),original.rawDigest()));
        assertThatThrownBy(()->calculator.calculate(request(many))).hasMessage("VERSIONED_CANDLES_LIMIT");
        var q=request(List.of(original));
        assertThatThrownBy(()->calculator.calculate(new PumpSwapVersionedPoolCandles.Request(q.batch(),q.pools(),q.decoderVersion(),FROM,FROM.plusSeconds(300300),D,K))).isInstanceOf(IllegalArgumentException.class);
    }
    static PumpSwapVersionedPoolCandles.Request request(List<PumpSwapOperations.RecordedTransaction> sources) {return new PumpSwapVersionedPoolCandles.Request(new PumpSwapOperations.RecordedBatch(sources,List.of("INCOMPLETE","NOT_ADMITTED")),List.of(GTA),"pumpswap-pool-legs-v5",FROM,TO,D,K);}
    static PumpSwapOrdinaryPoolNetProof.Proof ordinaryPriceProof(PumpSwapOperations.RecordedTransaction source,PumpSwapOperations.Operation operation) {
        return new PumpSwapOrdinaryPoolNetProof().prove(source,operation,GTA);
    }
    @Test void identicalPoolFlowsYieldIdenticalOrdinaryAndMultiPricesDespiteDifferentUserDebit() {
        var multi=PumpSwapPoolLegDecoderTest.source("term");var observed=new DecodePumpSwapPoolLegsUseCase().decode(new PumpSwapPoolLegs.Request(PumpSwapPoolLegDecoderTest.batch(multi),List.of(GTA))).poolLegs().getFirst();
        var synthetic=equalOrdinary(observed.baseAmount(),observed.poolNetQuoteLamports().orElseThrow(),BigInteger.valueOf(499950000));
        var report=calculator.calculate(request(List.of(synthetic,multi)));var selected=report.operations().stream().filter(o->o.pool().pool().equals(GTA.pool())).toList();
        assertThat(selected).hasSize(2);assertThat(selected).extracting(o->o.price().orElseThrow()).containsOnly(selected.getFirst().price().orElseThrow());
        assertThat(selected.stream().filter(o->o.kind().equals("ORDINARY")).findFirst().orElseThrow().sourceUserQuote()).contains(BigInteger.valueOf(499950000));
        assertThat(selected).extracting(o->o.poolNetQuoteLamports().orElseThrow()).containsOnly(BigInteger.valueOf(499825075));
    }
    static PumpSwapOperations.RecordedTransaction equalOrdinary(BigInteger baseAmount,BigInteger netQuote,BigInteger userQuote) {
        var raw=PumpSwapRawOperationDecoderTest.fixture("1","buy",true,1);var r=PumpSwapRawOperationDecoderTest.roles("buy",true,0);
        var keys=(List<String>)PumpSwapRawOperationDecoderTest.message(raw).get("accountKeys");
        var replacements=Map.of(r.get(0),GTA.pool(),r.get(3),GTA.baseMint(),r.get(4),GTA.quoteMint(),r.get(7),GTA.baseVault(),r.get(8),GTA.quoteVault());
        for(int n=0;n<keys.size();n++)keys.set(n,replacements.getOrDefault(keys.get(n),keys.get(n)));
        for(String side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)PumpSwapRawOperationDecoderTest.meta(raw).get(side)) {row.put("mint",replacements.getOrDefault(row.get("mint"),row.get("mint").toString()));row.put("owner",replacements.getOrDefault(row.get("owner"),row.get("owner").toString()));}
        var inner=PumpSwapRawOperationDecoderTest.inner(raw,0);var base=PumpSwapRawOperationDecoderTest.unbase58((String)inner.get(0).get("data"));PumpSwapRawOperationDecoderTest.putU64(base,1,baseAmount);inner.get(0).put("data",PumpSwapRawOperationDecoderTest.base58(base));
        var quote=PumpSwapRawOperationDecoderTest.unbase58((String)inner.get(1).get("data"));PumpSwapRawOperationDecoderTest.putU64(quote,1,netQuote);inner.get(1).put("data",PumpSwapRawOperationDecoderTest.base58(quote));
        var event=PumpSwapRawOperationDecoderTest.unbase58((String)inner.get(2).get("data"));PumpSwapRawOperationDecoderTest.putAddress(event,128,GTA.pool());PumpSwapRawOperationDecoderTest.putU64(event,24,baseAmount);PumpSwapRawOperationDecoderTest.putU64(event,72,userQuote);PumpSwapRawOperationDecoderTest.putU64(event,112,netQuote);PumpSwapRawOperationDecoderTest.putU64(event,120,userQuote);PumpSwapRawOperationDecoderTest.putU64(event,104,userQuote.subtract(netQuote));inner.get(2).put("data",PumpSwapRawOperationDecoderTest.base58(event));
        inner.add(2,PumpSwapRawOperationDecoderTest.ix(keys,PumpSwapPoolLegDecoderTest.TOKEN,List.of(r.get(6),GTA.quoteMint(),r.get(10),r.get(1)),PumpSwapRawOperationDecoderTest.checked(userQuote.subtract(netQuote),9),2));
        PumpSwapRawOperationDecoderTest.ordinarySyntheticBalances(raw);raw.put("blockTime",FROM.plusSeconds(10).getEpochSecond());raw.put("transactionIndex",1);
        var source=PumpSwapRawOperationDecoderTest.source(raw,"equal-pool-net-synthetic");return copy(source,Optional.of(FROM.plusSeconds(10)),source.provider(),source.rawResult(),source.rawDigest());
    }
    static PumpSwapOperations.RecordedTransaction ordinary() {
        try(var stream=PumpSwapVersionedPoolCandlesTest.class.getResourceAsStream("/fixtures/pumpswap-versioned-candles/ordinary-sell.json")){return PumpSwapPoolLegDecoderTest.sourceNode(JSON.readTree(stream));}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    static PumpSwapOperations.RecordedTransaction cloneIdentity(PumpSwapOperations.RecordedTransaction source,int n) {
        var raw=(tools.jackson.databind.node.ObjectNode)JSON.readTree(source.rawResult());var sig=source.signature()+"_synthetic_"+n;
        ((tools.jackson.databind.node.ArrayNode)raw.path("transaction").path("signatures")).set(0,JSON.getNodeFactory().textNode(sig));raw.put("transactionIndex",1000+n);
        var text=JSON.writeValueAsString(raw);return new PumpSwapOperations.RecordedTransaction(source.session(),source.chain(),sig,source.provider(),source.slot(),source.finality(),source.captureDisposition(),"synthetic/"+sig,source.sourceTime(),source.receivedAt(),source.admittedAt(),source.ingestedAt(),text,PumpSwapRawOperationParser.sha256(text.getBytes(StandardCharsets.UTF_8)));
    }
    static PumpSwapOperations.RecordedTransaction changedRaw(PumpSwapOperations.RecordedTransaction s,JsonNode raw){var text=JSON.writeValueAsString(raw);return copy(s,s.sourceTime(),s.provider(),text,PumpSwapRawOperationParser.sha256(text.getBytes(StandardCharsets.UTF_8)));}
    static PumpSwapOperations.RecordedTransaction copy(PumpSwapOperations.RecordedTransaction s,Optional<Instant> time,String provider,String raw,String hash){return new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),provider,s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),time,s.receivedAt(),s.admittedAt(),s.ingestedAt(),raw,hash);}
    static PumpSwapPoolLegs.PoolBinding syntheticBinding(){var r=PumpSwapRawOperationDecoderTest.roles("buy",false,0);return new PumpSwapPoolLegs.PoolBinding(r.get(0),r.get(7),r.get(8),r.get(3),PumpSwapPoolLegDecoderTest.WSOL,r.get(11),r.get(12));}
    static PumpSwapOperations.RecordedTransaction twoOrdinary() {
        var raw=PumpSwapRawOperationDecoderTest.fixture("1","buy",false,1);var r=PumpSwapRawOperationDecoderTest.roles("buy",false,0);
        var keys=(List<String>)PumpSwapRawOperationDecoderTest.message(raw).get("accountKeys");keys.set(keys.indexOf(r.get(4)),PumpSwapPoolLegDecoderTest.WSOL);
        for(String side:List.of("preTokenBalances","postTokenBalances"))for(var row:(List<Map<String,Object>>)PumpSwapRawOperationDecoderTest.meta(raw).get(side))if(row.get("mint").equals(r.get(4)))row.put("mint",PumpSwapPoolLegDecoderTest.WSOL);
        var events=PumpSwapRawOperationDecoderTest.inner(raw,0);var base=PumpSwapRawOperationDecoderTest.unbase58((String)events.get(0).get("data"));PumpSwapRawOperationDecoderTest.putU64(base,1,BigInteger.valueOf(100));events.get(0).put("data",PumpSwapRawOperationDecoderTest.base58(base));
        var event=PumpSwapRawOperationDecoderTest.unbase58((String)events.get(2).get("data"));PumpSwapRawOperationDecoderTest.putU64(event,24,BigInteger.valueOf(100));events.get(2).put("data",PumpSwapRawOperationDecoderTest.base58(event));
        var outer=(List<Map<String,Object>>)PumpSwapRawOperationDecoderTest.message(raw).get("instructions");outer.add(new HashMap<>(outer.getFirst()));
        var groupCopy=new ArrayList<Map<String,Object>>();events.forEach(e->groupCopy.add(new HashMap<>(e)));((List<Map<String,Object>>)PumpSwapRawOperationDecoderTest.meta(raw).get("innerInstructions")).add(new HashMap<>(Map.of("index",1,"instructions",groupCopy)));
        PumpSwapRawOperationDecoderTest.ordinarySyntheticBalances(raw);raw.put("blockTime",FROM.plusSeconds(10).getEpochSecond());raw.put("transactionIndex",1);
        var initial=PumpSwapRawOperationDecoderTest.source(raw,"same-vault-synthetic");return copy(initial,Optional.of(FROM.plusSeconds(10)),initial.provider(),initial.rawResult(),initial.rawDigest());
    }
}
