package io.cryptoresearch.marketdata.application;

import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.cryptoresearch.marketdata.api.PumpSwapRetainedWindow.*;

/** Reject caller transactions before opening the independent owning reader. */
@Service
public class ReadPumpSwapRetainedWindowUseCase {
    private final PumpSwapRetainedWindowReader reader;
    public ReadPumpSwapRetainedWindowUseCase(PumpSwapRetainedWindowReader reader){this.reader=reader;}
    public static Request normalize(Request q){
        try {
            Objects.requireNonNull(q.session());var f=time(q.receivedFromInclusive());var t=time(q.receivedToExclusive());
            var e=time(q.eventFromInclusive());var z=time(q.eventToExclusive());
            if(!f.isBefore(t)||java.time.Duration.between(f,t).compareTo(java.time.Duration.ofHours(24))>0
                ||!e.isBefore(z)||e.getNano()!=0||z.getNano()!=0||Math.floorMod(e.getEpochSecond(),300)!=0||Math.floorMod(z.getEpochSecond(),300)!=0
                ||java.time.Duration.between(e,z).getSeconds()>300000)throw new IllegalArgumentException();
            return new Request(q.session(),f,t,e,z);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("RETAINED_INVALID_REQUEST");}
    }
    private static Instant time(Instant t){return Objects.requireNonNull(t).truncatedTo(ChronoUnit.MICROS);}
    public Snapshot read(Request q){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("RETAINED_REQUIRES_NO_TRANSACTION");
        return reader.read(normalize(q));
    }
}
