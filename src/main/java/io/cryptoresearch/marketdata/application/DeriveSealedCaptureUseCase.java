package io.cryptoresearch.marketdata.application;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.cryptoresearch.kernel.api.ChainId;
import io.cryptoresearch.marketdata.api.PumpSwapOperations.*;
import io.cryptoresearch.marketdata.infrastructure.persistence.JdbcContinuousCaptureStore.*;

/** Fixed decoder invocation outside every capture/read transaction; cursor follows exact publication. */
@Service
public class DeriveSealedCaptureUseCase {
	private final ContinuousCaptureLedgerUseCase ledger;
	private final CaptureLedgerUseCase capture;
	private final PersistPumpSwapOperationsUseCase persistence;
	private final DecodePumpSwapOperationsUseCase decoder;
	public DeriveSealedCaptureUseCase(ContinuousCaptureLedgerUseCase ledger,CaptureLedgerUseCase capture,PersistPumpSwapOperationsUseCase persistence,DecodePumpSwapOperationsUseCase decoder) {
		this.ledger=ledger;this.capture=capture;this.persistence=persistence;this.decoder=decoder;
	}
	RecordedBatch recorded(Segment s,Page page) {
		var json=tools.jackson.databind.json.JsonMapper.builder().build();var transactions=new ArrayList<RecordedTransaction>();
		var c=s.configuration();
		for(var raw:page.raw()) {
			if(!PayloadFingerprint.sha256(raw.payload()).equals(raw.digest()))throw new IllegalStateException("SEALED_RAW_DIGEST_CONFLICT");
			var bindings=page.bindings().stream().filter(b->b.signature().equals(raw.signature())).sorted(Comparator.comparing(Binding::pool)).toList();
			if(bindings.isEmpty() || bindings.size()>3)throw new IllegalStateException("SEALED_SOURCE_BINDING_CONFLICT");
			var identity=new CanonicalFingerprint().field("version","continuous-sealed-source-v1").field("session",c.sessionId().toString())
				.field("chain",ChainId.SOLANA_MAINNET.value()).field("provider",c.provider()).field("signature",raw.signature());
			var status=bindings.getFirst().status();
			for(var b:bindings) {
				if(c.pools().stream().noneMatch(p->p.address().equals(b.pool())) || b.slot()!=raw.slot() || !b.digest().equals(raw.digest()) || !b.status().equals(status)
						|| b.received()==null || b.admitted()==null)throw new IllegalStateException("SEALED_SOURCE_BINDING_CONFLICT");
				var notification=json.readTree(b.notification());
				if(notification.has("pageDigest")) {
					var digest=notification.path("pageDigest").asText();var cursor=notification.path("cursor").asText();
					var envelope=page.proofs().stream().filter(p->p.kind().equals("HTTP_getTransactionsForAddress") && p.digest().equals(digest)).findFirst().orElseThrow(()->new IllegalStateException("RECOVERY_PROVENANCE_MISSING"));
					var context=page.proofs().stream().filter(p->{if(!p.kind().equals("CAPTURE_HISTORY_CONTEXT"))return false;var n=json.readTree(p.payload());return n.path("pageDigest").asText().equals(digest) && n.path("pool").asText().equals(b.pool()) && n.path("cursor").asText().equals(cursor);}).findFirst().orElseThrow(()->new IllegalStateException("RECOVERY_PROVENANCE_MISSING"));
					var n=json.readTree(context.payload());
					if(!PayloadFingerprint.sha256(envelope.payload()).equals(envelope.digest()) || !PayloadFingerprint.sha256(context.payload()).equals(context.digest())
							|| !n.path("version").asText().equals("capture-history-context-v1") || !n.path("session").asText().equals(c.sessionId().toString())
							|| !n.path("method").asText().equals("getTransactionsForAddress") || !n.path("receivedAt").asText().equals(envelope.receivedAt().toString()))throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
					var rows=io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.exactElements(envelope.payload(),"/result/data");
					if(rows.size()>100)throw new IllegalStateException("RECOVERY_PROVENANCE_INVALID");
					capture.verifyHistoryReference(c,new CaptureLedgerUseCase.HistoryPage(c.sessionId(),b.pool(),cursor,envelope,rows,context.payload()),b.pool(),b.signature(),b.slot(),b.notification(),b.received());
				}
				identity.field("pool",b.pool()).field("disposition",b.status()).field("slot",Long.toString(b.slot())).field("rawDigest",b.digest())
					.field("notificationProof",PayloadFingerprint.sha256(b.notification())).field("received",b.received().toString()).field("admitted",b.admitted().toString());
			}
			transactions.add(new RecordedTransaction(c.sessionId(),ChainId.SOLANA_MAINNET,raw.signature(),c.provider(),raw.slot(),"finalized",status,identity.finish(),
				Optional.ofNullable(raw.sourceTime()),raw.received(),raw.admitted(),raw.ingested(),raw.payload(),raw.digest()));
		}
		return new RecordedBatch(transactions,List.of("SEALED_CAPTURE_EXPLICIT_GAPS_UNKNOWN_COVERAGE"));
	}
	private void verify(io.cryptoresearch.marketdata.api.PumpSwapOperationPersistence.StoredBatch batch,RecordedBatch expected) {
		var result=batch.result();
		if(!result.decoderVersion().equals("pumpswap-operations-v4") || !result.schemaVersion().equals("may18-oct07-endpoint-proof-v4")
				|| !result.canonicalizationVersion().equals("length-prefixed-v1") || !batch.storageVersion().equals("pumpswap-storage-v1")
				|| !result.transactions().stream().map(TransactionResult::source).toList().equals(expected.transactions()))throw new IllegalStateException("DECODE_MEMBERSHIP_CONFLICT");
		var saved=persistence.find(batch.batchKey()).orElseThrow(()->new IllegalStateException("DECODE_PUBLICATION_MISSING"));
		if(!saved.equals(batch) || !result.equals(decoder.decode(expected)))throw new IllegalStateException("DECODE_MEMBERSHIP_CONFLICT");
	}
	public void derive(ContinuousCaptureLedgerUseCase.Admission admission) {
		if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("DECODE_REQUIRES_NO_TRANSACTION");
		try {deriveAdmitted(admission);}catch(RuntimeException failure) {
			try {ledger.terminal(admission,io.cryptoresearch.marketdata.infrastructure.JdkPumpSwapTransport.safeReason(failure));}
			catch(RuntimeException terminalFailure) {failure.addSuppressed(terminalFailure);}
			throw failure;
		}
	}
	private void deriveAdmitted(ContinuousCaptureLedgerUseCase.Admission admission) {
		var s=ledger.current(admission);
		if(s.sealedAt()==null || !s.state().equals("DERIVING"))throw new IllegalStateException("CAPTURE_NOT_SEALED");
		while(true) {
			ledger.heartbeat(admission);
			// An empty terminal probe admits no decoder work and does not consume a page allowance.
			var page=s.pages()>=admission.configuration().maxPages()?ledger.page(admission,s,s.cursor()):ledger.admitPage(admission,s);
			if(page.raw().isEmpty())break;
			if(s.pages()>=admission.configuration().maxPages())throw new IllegalStateException("DECODE_BACKLOG");
			var recorded=recorded(s,page);var batch=persistence.persist(recorded);verify(batch,recorded);
			s=ledger.progress(admission,new Receipt(s.cursor(),page.through(),batch.batchKey(),recorded.transactions().size()));
		}
		// Independent replay of every contiguous receipt range and its exact frozen V14 membership.
		var cursor="";long count=0;
		for(int i=0;i<s.pages();i++) {
			ledger.heartbeat(admission);var receipt=ledger.receipt(s,i);
			if(!receipt.from().equals(cursor))throw new IllegalStateException("DECODE_RECONCILIATION_FAILED");
			var page=ledger.page(admission,s,cursor);var expected=recorded(s,page);
			if(!page.through().equals(receipt.through()) || expected.transactions().size()!=receipt.count())throw new IllegalStateException("DECODE_RECONCILIATION_FAILED");
			verify(persistence.find(receipt.batch()).orElseThrow(()->new IllegalStateException("DECODE_PUBLICATION_MISSING")),expected);
			cursor=receipt.through();count=Math.addExact(count,receipt.count());
		}
		if(!cursor.equals(s.cursor()) || count!=s.sources() || !ledger.page(admission,s,cursor).raw().isEmpty())throw new IllegalStateException("DECODE_RECONCILIATION_FAILED");
		ledger.complete(admission,s);
	}
}
