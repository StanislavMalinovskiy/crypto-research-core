package io.cryptoresearch.marketdata.api;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Pool;
import io.cryptoresearch.marketdata.api.PumpSwapPoolMetrics.Rational;

/** Pure selected-version recorded-window evidence; no fabricated stored-batch clocks. */
public final class PumpSwapVersionedPoolCandles {
    private PumpSwapVersionedPoolCandles() { }
    public record Request(PumpSwapOperations.RecordedBatch batch,List<PumpSwapPoolLegs.PoolBinding> pools,
            String decoderVersion,Instant fromInclusive,Instant toExclusive,Instant derivationObservedAt,Instant knowledgeCutoff) {
        public Request { pools=List.copyOf(pools); }
    }
    public record Descriptor(String decoderVersion,List<PumpSwapPoolLegs.PoolBinding> pools,
            Instant fromInclusive,Instant toExclusive,Instant derivationObservedAt,Instant knowledgeCutoff) {
        public Descriptor {pools=List.copyOf(pools);}
    }
    public record Clocks(Instant receivedAt,Instant admittedAt,Instant ingestedAt,Instant derivationObservedAt,Instant knownAt) { }
    public record Transaction(String chain,String signature,String provider,String session,String rawDigest,String sourceReference,
            long slot,Optional<Long> transactionIndex,Optional<Instant> eventTime,Optional<Instant> sourceTime,
            Clocks clocks,PumpSwapOperations.Status status,String reason,int confirmedOperations) { }
    public record Operation(String identity,String chain,String signature,String rawDigest,String sourceReference,
            PumpSwapOperations.Position position,Pool pool,String kind,String direction,BigInteger baseAmount,
            Optional<Integer> baseDecimals,Optional<BigInteger> poolNetQuoteLamports,Optional<Rational> price,
            String priceBasis,String priceReason,Optional<BigInteger> sourceUserQuote,long slot,
            Optional<Long> transactionIndex,Optional<Instant> eventTime,Clocks clocks,String disposition,
            String eventDigest,List<Integer> eventPath,List<List<Integer>> transferPaths,String proofDigest) {
        public Operation {eventPath=List.copyOf(eventPath);transferPaths=transferPaths.stream().map(List::copyOf).toList();}
    }
    public record Price(Rational value,String operationIdentity) { }
    public record Ohlc(Price open,Price high,Price low,Price close) { }
    public record Candle(Pool pool,Instant fromInclusive,Instant toExclusive,int operationCount,int ordinaryCount,
            int multiHopCount,int pricedCount,int unpricedCount,int missingIndexCount,BigInteger baseAmount,
            Optional<BigInteger> poolNetQuoteLamports,List<String> operationIdentities,Optional<Ohlc> ohlc) {
        public Candle {operationIdentities=List.copyOf(operationIdentities);}
    }
    public record Uncertainty(Pool pool,Optional<Instant> eventTime,String signature,String reason) { }
    public record Reconciliation(int transactions,int operations,int included,int excluded,int priced,int unpriced,
            Map<String,Integer> statuses) {public Reconciliation {statuses=Map.copyOf(statuses);}}
    public record Result(String algorithmVersion,String canonicalizationVersion,Descriptor request,
            String selectedInputFingerprint,Map<String,String> pinnedSourceHashes,List<Transaction> transactions,
            List<Operation> operations,List<Candle> candles,List<Uncertainty> uncertainties,Reconciliation reconciliation,
            List<String> limitations,String fingerprint) {
        public Result {pinnedSourceHashes=Map.copyOf(pinnedSourceHashes);transactions=List.copyOf(transactions);
            operations=List.copyOf(operations);candles=List.copyOf(candles);uncertainties=List.copyOf(uncertainties);limitations=List.copyOf(limitations);}
    }
}
