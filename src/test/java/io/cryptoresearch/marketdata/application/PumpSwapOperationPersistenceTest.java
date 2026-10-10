package io.cryptoresearch.marketdata.application;

import static org.assertj.core.api.Assertions.*;
import static io.cryptoresearch.marketdata.application.PumpSwapRawOperationDecoderTest.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;

class PumpSwapOperationPersistenceTest {
    final DecodePumpSwapOperationsUseCase decoder = new DecodePumpSwapOperationsUseCase();
    final PumpSwapOperationCanonicalizer canonicalizer = new PumpSwapOperationCanonicalizer();

    @Test void reportLabelsCannotChangeTransactionRevisionIdentity() {
        var source = source(fixture("1", "buy", true, 1), "identity-label");
        var first = decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(source), List.of("GAPS_UNKNOWN")));
        var relabeled = decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(source), List.of("FEES_UNKNOWN")));
        assertThat(first.fingerprint()).isNotEqualTo(relabeled.fingerprint());
        assertThat(canonicalizer.transactionKey(relabeled, relabeled.transactions().getFirst()))
            .as("batch limitation labels must not enter source-specific transaction revision identity")
            .isEqualTo(canonicalizer.transactionKey(first, first.transactions().getFirst()));
    }
    @Test void exactSignedUnsignedOptionalAndProofEvidenceRoundTrips() {
        var source = source(v2Fixture("buy_v2"), "exact-roundtrip");
        var result = decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(source), List.of()));
        var op = result.operations().getFirst();
        assertThat(op.eventFacts()).containsEntry("virtual_quote_reserves", java.math.BigInteger.ONE.shiftLeft(127).negate());
        assertThat(canonicalizer.decode(canonicalizer.encode(op), PumpSwapOperations.Operation.class)).isEqualTo(op);
        var prepared = canonicalizer.prepare(result);
        assertThat(canonicalizer.transaction(prepared.transactions().getFirst().content(), source.rawResult())).isEqualTo(result.transactions().getFirst());
        assertThat(prepared.transactions().getFirst().content()).doesNotContain("rawResult");
        assertThat(canonicalizer.result(prepared.content(), result.transactions(), result.operations())).isEqualTo(result);
    }
    @Test void regroupingPermutationAndDuplicatesPreserveRevisionKeys() {
        var a = source(fixture("1","buy",true,1),"group-a"); var b = source(fixture("0","sell",false,1),"group-b");
        var together=canonicalizer.prepare(decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of("L"))));
        var reversed=canonicalizer.prepare(decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(b,a,a),List.of("L"))));
        var separate=canonicalizer.prepare(decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(a),List.of("other"))));
        assertThat(reversed).isEqualTo(together);
        assertThat(together.transactions()).extracting(PumpSwapOperationCanonicalizer.TransactionRow::key).contains(separate.transactions().getFirst().key());
        assertThat(together.operations()).extracting(PumpSwapOperationCanonicalizer.OperationRow::key).contains(separate.operations().getFirst().key());
        assertThat(separate.key()).isNotEqualTo(together.key());
    }
    @Test void versionAndSourceBindingChangeRevisionsButNotCanonicalOperation() {
        var s=source(fixture("1","buy",true,1),"binding");var r=decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(s),List.of()));
        var later=new PumpSwapOperations.Result("synthetic-future",r.schemaVersion(),r.canonicalizationVersion(),r.pinnedSourceHashes(),r.transactions(),r.operations(),r.limitations(),r.fingerprint());
        var a=canonicalizer.prepare(r);var b=canonicalizer.prepare(later);
        assertThat(b.transactions().getFirst().key()).isNotEqualTo(a.transactions().getFirst().key());
        assertThat(b.operations().getFirst().canonicalKey()).isEqualTo(a.operations().getFirst().canonicalKey());
        assertThat(b.operations().getFirst().key()).isNotEqualTo(a.operations().getFirst().key());
        var replacement=new PumpSwapOperations.RecordedTransaction(s.session(),s.chain(),s.signature(),"other",s.slot(),s.finality(),s.captureDisposition(),s.sourceReference(),s.sourceTime(),s.receivedAt(),s.admittedAt(),s.ingestedAt(),s.rawResult(),s.rawDigest());
        var other=canonicalizer.prepare(decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(replacement),List.of())));
        assertThat(other.operations().getFirst().canonicalKey()).isEqualTo(a.operations().getFirst().canonicalKey());
        assertThat(other.transactions().getFirst().key()).isNotEqualTo(a.transactions().getFirst().key());
    }
    @Test void fullNumericPathsAndOpaqueComponentsCannotCollide() {
        var r=decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(source(fixture("1","buy",true,1),"paths")),List.of()));
        var op=r.operations().getFirst();
        assertThat(canonicalizer.operationKey(position(op,new PumpSwapOperations.Position(1,List.of(2,34),0))))
            .isNotEqualTo(canonicalizer.operationKey(position(op,new PumpSwapOperations.Position(1,List.of(23,4),0))));
        assertThat(canonicalizer.encode(java.util.Map.of("a/b",java.util.Map.of("c",1))))
            .isNotEqualTo(canonicalizer.encode(java.util.Map.of("a",java.util.Map.of("b/c",1))));
        assertThat(canonicalizer.digest(canonicalizer.encode(java.util.Map.of("x",java.util.Map.of("b",2,"a",1)))))
            .isEqualTo(canonicalizer.digest(canonicalizer.encode(java.util.Map.of("x",java.util.Map.of("a",1,"b",2)))));
        var changed=new PumpSwapOperations.TransactionResult(r.transactions().getFirst().source(),"1",PumpSwapOperations.Status.INVALID,"different",java.util.Optional.empty(),List.of());
        assertThat(canonicalizer.transactionKey(r,changed)).isEqualTo(canonicalizer.transactionKey(r,r.transactions().getFirst()));
    }
    static PumpSwapOperations.Operation position(PumpSwapOperations.Operation o,PumpSwapOperations.Position p) {
        return new PumpSwapOperations.Operation(o.chain(),o.signature(),p,o.variant(),o.trader(),o.pool(),o.baseMint(),o.quoteMint(),o.baseAmount(),o.quoteAmount(),o.baseDecimals(),o.quoteDecimals(),o.eventPath(),o.transferPaths(),o.lifecyclePaths(),o.eventDeclaredFees(),o.attributedFees(),o.eventFacts(),o.feeStatus(),o.feeReasons(),o.rawDigest(),o.eventDigest(),o.sourceReference());
    }
    @Test void sourceProviderTieOrderingPreservesTheFixedDecodersExactOperationList() {
        var a=source(fixture("1","sell",true,1),"source-provider-tie");
        var original=source(fixture("1","buy",true,1),"source-provider-tie");
        var b=new PumpSwapOperations.RecordedTransaction(original.session(),original.chain(),original.signature(),"another-provider",original.slot(),original.finality(),original.captureDisposition(),original.sourceReference(),original.sourceTime(),original.receivedAt(),original.admittedAt(),original.ingestedAt(),original.rawResult(),original.rawDigest());
        var r=decoder.decode(new PumpSwapOperations.RecordedBatch(List.of(a,b),List.of()));
        assertThat(r.operations()).extracting(PumpSwapOperations.Operation::variant).containsExactly("buy","sell");
        assertThat(canonicalizer.prepare(r).operations()).extracting(PumpSwapOperationCanonicalizer.OperationRow::operation)
            .as("equal canonical path and source reference cannot replace the fixed decoder's provider-tie order with digest order")
            .containsExactlyElementsOf(r.operations());
    }
}
