package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.PumpSwapVersionedPoolCandles;
import io.cryptoresearch.marketdata.api.PumpSwapPoolLegs;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapOrdinaryPoolNetProof;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/** Complete bounded population; physical decoder chunks never become content identity. */
@Service
public class CalculatePumpSwapVersionedPoolCandlesUseCase {
    private final DecodePumpSwapPoolLegsUseCase decoder;
    private final PumpSwapVersionedPoolCandleCalculator calculator;
    private final PumpSwapOrdinaryPoolNetProof ordinaryProof;
    public CalculatePumpSwapVersionedPoolCandlesUseCase(){this(new DecodePumpSwapPoolLegsUseCase(),new PumpSwapVersionedPoolCandleCalculator(),new PumpSwapOrdinaryPoolNetProof());}
    @org.springframework.beans.factory.annotation.Autowired
    public CalculatePumpSwapVersionedPoolCandlesUseCase(DecodePumpSwapPoolLegsUseCase decoder,PumpSwapVersionedPoolCandleCalculator calculator,PumpSwapOrdinaryPoolNetProof proof){this.decoder=Objects.requireNonNull(decoder);this.calculator=Objects.requireNonNull(calculator);this.ordinaryProof=Objects.requireNonNull(proof);}
    public PumpSwapVersionedPoolCandles.Result calculate(PumpSwapVersionedPoolCandles.Request request) {return calculate(request,128);}
    public PumpSwapVersionedPoolCandles.Result calculate(PumpSwapVersionedPoolCandles.Request request,int chunkSize) {
        var descriptor=PumpSwapVersionedPoolCandleCalculator.normalize(request);if(chunkSize<1||chunkSize>128)throw new IllegalArgumentException("VERSIONED_CANDLES_INVALID_CHUNK");
        Objects.requireNonNull(request.batch());var selected=new HashMap<List<String>,PumpSwapOperations.RecordedTransaction>();long bytes=0;
        for(var source:request.batch().transactions()) {
            Objects.requireNonNull(source);var previous=selected.putIfAbsent(PumpSwapVersionedPoolCandleCalculator.sourceKey(source),source);
            if(previous!=null&&!previous.equals(source))throw new IllegalArgumentException("VERSIONED_CANDLES_AMBIGUOUS_SOURCE");
            long size=source.rawResult().getBytes(StandardCharsets.UTF_8).length;bytes+=size;
            if(size>8_000_000||bytes>64_000_000||selected.size()>4096)throw new IllegalArgumentException("VERSIONED_CANDLES_LIMIT");
        }
        var sources=selected.values().stream().sorted(Comparator.comparing((PumpSwapOperations.RecordedTransaction s)->s.chain().value(),PumpSwapVersionedPoolCandleCalculator.TEXT).thenComparing(PumpSwapOperations.RecordedTransaction::signature,PumpSwapVersionedPoolCandleCalculator.TEXT)).toList();
        for(var s:sources)if(descriptor.derivationObservedAt().isBefore(s.receivedAt())||descriptor.derivationObservedAt().isBefore(s.admittedAt())||descriptor.derivationObservedAt().isBefore(s.ingestedAt()))throw new IllegalArgumentException("VERSIONED_CANDLES_DERIVATION_PRECEDES_SOURCE");
        var transactions=new ArrayList<PumpSwapOperations.TransactionResult>();var operations=new ArrayList<PumpSwapVersionedPoolCandleCalculator.OperationInput>();Map<String,String> pins=null;
        // Empty input still validates exact bindings and selected decoder versions through the same boundary.
        for(int start=0;start<sources.size()||start==0;start+=chunkSize) {
            var batch=new PumpSwapOperations.RecordedBatch(sources.subList(start,Math.min(start+chunkSize,sources.size())),request.batch().limitations());
            var result=decoder.decode(new PumpSwapPoolLegs.Request(batch,descriptor.pools()));
            if(!result.decoderVersion().equals(descriptor.decoderVersion())||!result.schemaVersion().equals("single-own-event-pool-net-sol-v1")||!result.canonicalizationVersion().equals("length-prefixed-v1"))throw new IllegalArgumentException("VERSIONED_CANDLES_UNSUPPORTED_VERSION");
            if(pins==null)pins=result.pinnedSourceHashes();else if(!pins.equals(result.pinnedSourceHashes()))throw new IllegalArgumentException("VERSIONED_CANDLES_PIN_CONFLICT");
            transactions.addAll(result.transactions());
            var parents=new HashMap<List<String>,PumpSwapOperations.RecordedTransaction>();for(var tx:result.transactions())parents.put(PumpSwapVersionedPoolCandleCalculator.sourceKey(tx.source()),tx.source());
            for(var op:result.v4().operations()){var source=parents.get(List.of(op.chain().value(),op.signature()));if(source==null)throw new IllegalArgumentException("VERSIONED_CANDLES_SOURCE_MISMATCH");operations.add(new PumpSwapVersionedPoolCandleCalculator.OperationInput(source,op,null));}
            for(var leg:result.poolLegs())operations.add(new PumpSwapVersionedPoolCandleCalculator.OperationInput(leg.source(),null,leg));
            if(operations.size()>10000)throw new IllegalArgumentException("VERSIONED_CANDLES_LIMIT");if(sources.isEmpty())break;
        }
        if(transactions.size()!=sources.size())throw new IllegalArgumentException("VERSIONED_CANDLES_SOURCE_MISMATCH");
        return calculator.calculate(descriptor,transactions,operations,pins,request.batch().limitations(),ordinaryProof);
    }
}
