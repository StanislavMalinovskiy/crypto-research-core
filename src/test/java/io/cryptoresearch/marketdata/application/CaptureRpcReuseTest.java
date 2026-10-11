package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static io.cryptoresearch.marketdata.application.PumpSwapPoolCaptureTest.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapCapture.*;

class CaptureRpcReuseTest {
    static Configuration policy(Configuration c, String version, String period, String rate, long bytes) {
        var a=c.authority(); var l=c.limits();
        return new Configuration(c.sessionId(),version,c.provider(),c.startsAt(),c.pools(),
                new Limits(l.windowSeconds(),l.requests(),l.receivedBytes(),l.retainedBytes(),bytes,l.rpcHundredths(),l.messageBytes(),l.responseBytes(),l.queueItems(),l.queueBytes(),l.timeoutSeconds(),l.retries(),l.reconnects(),l.recoveryPages(),l.recoverySeconds(),l.requestsPerSecond(),l.leaseSeconds()),
                new Authority(period,a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),a.ceilingHundredths(),a.ceilingUsd(),a.usdPerMillionCu(),new BigDecimal(rate),a.ownerAuthorization(),a.retentionAllowed(),a.exclusiveAllocation()));
    }
    static Configuration reuse(String rate,long bytes) { return policy(configuration(SESSION,0L),"capture-rpc-reuse-v6","synthetic-fractional-period",rate,bytes); }

    @Test void fractionalCumulativeBytesRoundUpConservatively() {
        var c=reuse("0.0002",51);
        var bytes=List.of(0L,1L,50L,51L,7778076L);
        var expected=List.of(0L,1L,1L,2L,155562L);
        assertThatCode(()->CaptureBudget.subscriptionHundredths(c,1)).as("fractional positive byte cost must be representable conservatively").doesNotThrowAnyException();
        for(var i=0;i<bytes.size();i++) assertThat(CaptureBudget.subscriptionHundredths(c,bytes.get(i)))
                .as("cumulative fractional byte cost for %s bytes",bytes.get(i)).isEqualTo(expected.get(i));
        assertThat(CaptureBudget.reservation(c)).isEqualTo(c.limits().rpcHundredths()+2);
        assertThat(CaptureBudget.launchProblem(c)).isEmpty();
    }
    @Test void legacyArithmeticAndUnroundedUsdRemainExact() {
        assertThat(CaptureBudget.reservation(configuration(SESSION,0L))).isEqualTo(95000000);
        assertThat(CaptureBudget.subscriptionHundredths(reuse("0.04",1),1)).isEqualTo(4);
        assertThat(CaptureBudget.cost(155562,new BigDecimal("0.525"))).isEqualByComparingTo("0.0008167005");
        var legacy=policy(configuration(SESSION,0L),"capture-provenance-v5","legacy","0.0002",1);
        assertThatThrownBy(()->CaptureBudget.subscriptionHundredths(legacy,1)).isInstanceOf(ArithmeticException.class);
    }
    @Test void overflowCannotProduceAnAdmissibleReservation() {
        var c=reuse("99999999999999999999999999",1);
        assertThatThrownBy(()->CaptureBudget.reservation(c)).isInstanceOf(ArithmeticException.class);
        assertThat(CaptureBudget.launchProblem(c)).isEqualTo("INVALID_LINKAGE_OR_LIMITS");
    }
    @Test void supportedVersionAndSlotNeverAcceptWrappedIntegralValues() {
        var full=CaptureRpcReuseIT.full(CaptureRpcReuseIT.SIG,101,"1",false);
        for(var version:List.of("4294967296","4294967297","18446744073709551617","-4294967295")) {
            var altered=CaptureTransactionSource.parse(full.replace("\"version\":1","\"version\":"+version));
            assertThatCode(()->CaptureTransactionSource.rejection(altered,CaptureRpcReuseIT.SIG,101)).as("unsupported integral version has explicit bounded fallback disposition").doesNotThrowAnyException();
            assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(full.replace("\"version\":1","\"version\":"+version)),CaptureRpcReuseIT.SIG,101))
                    .as("only exact legacy/v0/v1 qualify, never wrapped %s",version).isEqualTo("UNSUPPORTED_SHAPE");
        }
        assertThatThrownBy(()->CaptureTransactionSource.rejection(CaptureTransactionSource.parse(full.replace("\"slot\" : 101","\"slot\" : 18446744073709551717")),CaptureRpcReuseIT.SIG,101)).hasMessageContaining("CONFLICT");
    }
    @Test void completeMessageRequiresConsistentExactHeaderCounts() {
        var full=CaptureRpcReuseIT.full(CaptureRpcReuseIT.SIG,101,"1",false);
        var noHeader=full.replace("\"numRequiredSignatures\":1,\"numReadonlySignedAccounts\":0,\"numReadonlyUnsignedAccounts\":0","");
        assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(noHeader),CaptureRpcReuseIT.SIG,101)).as("object alone is not complete header evidence").isEqualTo("INCOMPLETE");
        for(var count:List.of("0","2","4294967297","-1")) {
            assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(full.replace("\"numRequiredSignatures\":1","\"numRequiredSignatures\":"+count)),CaptureRpcReuseIT.SIG,101)).isEqualTo("UNSUPPORTED_SHAPE");
        }
    }
    @Test void v1ResourceConfigAndNoLookupTablesAreVersionApplicableEvidence() {
        var row=CaptureRpcReuseIT.full(CaptureRpcReuseIT.SIG,101,"1",false);
        assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(row.replace(CaptureRpcReuseIT.V1_CONFIG,"")),CaptureRpcReuseIT.SIG,101)).as("official v1 requires all four transactionConfig fields").isEqualTo("INCOMPLETE");
        for(var key:List.of("computeUnitLimit","heapSize","loadedAccountsDataSizeLimit","priorityFee")) {
            assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(row.replace("\""+key+"\":null,","").replace(",\""+key+"\":null","")),CaptureRpcReuseIT.SIG,101)).isEqualTo("INCOMPLETE");
        }
        assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(row.replace("\"header\":","\"addressTableLookups\":[],\"header\":")),CaptureRpcReuseIT.SIG,101)).isEqualTo("UNSUPPORTED_SHAPE");
        for(var version:List.of("0","\"legacy\"")) assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(row.replace("\"version\":1","\"version\":"+version)),CaptureRpcReuseIT.SIG,101)).isEqualTo("UNSUPPORTED_SHAPE");
    }
    @Test void failedErrorStringRemainsCompleteRaw() {
        var row=CaptureRpcReuseIT.full(CaptureRpcReuseIT.SIG,101,"1",false).replace("\"err\":null","\"err\":\"AccountInUse\"");
        assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(row),CaptureRpcReuseIT.SIG,101)).as("Solana transaction error enum may be a string; failed raw is retained").isEmpty();
        assertThat(CaptureTransactionSource.status(CaptureTransactionSource.parse(row))).isEqualTo("FAILED_TRANSACTION");
    }
    @Test void exactCostCapAndOneHundredthBelowAreDistinct() {
        var c=reuse("0.0002",51); var a=c.authority(); var amount=c.limits().rpcHundredths()+2;
        for(var ceiling:List.of(amount-1,amount,amount+1)) {
            var capped=new Configuration(c.sessionId(),c.version(),c.provider(),c.startsAt(),c.pools(),c.limits(),new Authority(a.accountPeriod(),a.periodEnd(),a.source(),a.openingHundredths(),a.otherActivityHundredths(),ceiling,a.ceilingUsd(),a.usdPerMillionCu(),a.subscriptionCuPerByte(),a.ownerAuthorization(),a.retentionAllowed(),a.exclusiveAllocation()));
            assertThat(CaptureBudget.launchProblem(capped)).isEqualTo(ceiling<amount?"ACCOUNT_COST_LIMIT":"");
        }
    }
    @Test void tokenAndLookupIndicesRequireCompleteResolvableSourceShape() {
        var row=CaptureRpcReuseIT.full(CaptureRpcReuseIT.SIG,101,"0",false);
        for(var balance:List.of("{\"accountIndex\":1,\"mint\":\""+address(0)+"\",\"uiTokenAmount\":{\"amount\":\"1\",\"decimals\":0,\"uiAmount\":1,\"uiAmountString\":\"1\"}}","{\"accountIndex\":0}","{\"accountIndex\":18446744073709551616}")) {
            assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(row.replace("\"preTokenBalances\":[]","\"preTokenBalances\":["+balance+"]")),CaptureRpcReuseIT.SIG,101)).as("complete token balance source requires resolvable index and mint/amount fields").isEqualTo("UNSUPPORTED_SHAPE");
        }
        var lookup="\"addressTableLookups\":[{\"accountKey\":\""+address(0)+"\",\"writableIndexes\":[],\"readonlyIndexes\":[0]}],";
        var unmatched=row.replace("\"header\":",lookup+"\"header\":").replace("\"err\":null","\"loadedAddresses\":{\"writable\":[],\"readonly\":[]},\"err\":null");
        assertThat(CaptureTransactionSource.rejection(CaptureTransactionSource.parse(unmatched),CaptureRpcReuseIT.SIG,101)).as("v0 lookup count must agree with already resolved loaded keys").isEqualTo("UNSUPPORTED_SHAPE");
    }
}
