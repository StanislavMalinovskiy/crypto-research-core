package io.cryptoresearch.marketdata.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import io.cryptoresearch.kernel.api.*;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CaptureEvidenceUseCase {
	private final CaptureLedgerUseCase ledger;
	private final RawTransactionStore raw;
	private final StoreRawTransactionUseCase storage;
	private final ObjectMapper json;
	private final org.springframework.transaction.PlatformTransactionManager transactions;
	public CaptureEvidenceUseCase(CaptureLedgerUseCase ledger,RawTransactionStore raw,StoreRawTransactionUseCase storage,ObjectMapper json,org.springframework.transaction.PlatformTransactionManager transactions) {
		this.ledger=ledger; this.raw=raw; this.storage=storage; this.json=json; this.transactions=transactions;
	}
	private <T> T rawBoundary(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,java.util.function.Supplier<T> work) {
		var remaining=java.time.Duration.between(ledger.now(),CaptureLedgerUseCase.end(c)).getSeconds();
		if(remaining<=0) throw new IllegalStateException("CAPTURE_TIME_LIMIT");
		var boundary=new org.springframework.transaction.support.TransactionTemplate(transactions);
		boundary.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		boundary.setTimeout((int)Math.min(remaining,Math.min(c.limits().timeoutSeconds(),c.limits().leaseSeconds())));
		return boundary.execute(status->work.get());
	}
	/** Runs on the existing single writer; retained receipt headroom precedes any admission. */
	public Optional<String> reuse(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String holder,String pool,String signature,long slot,
			String reference,Instant received,CaptureLedgerUseCase.HistoryPage supplied,java.util.function.Consumer<String> retainReceipt) {
		var id=new TransactionId(ChainId.SOLANA_MAINNET,signature);
		var proofs=ledger.reuseProofs(c,signature);
		var old=rawBoundary(c,()->raw.find(ChainId.SOLANA_MAINNET,id,c.provider()));
		tools.jackson.databind.JsonNode original=null;
		if(!proofs.isEmpty()) {
			if(proofs.size()>3 || old.isEmpty()) throw new IllegalStateException("CAPTURE_REUSE_PROOF_CONFLICT");
			original=verifyRaw(c,old.get(),signature,slot);
			boolean anchor=false;
			for(var proof:proofs) {
				if(proof.slot()!=slot || !c.provider().equals(proof.provider()) || !c.version().equals(proof.version()) || !old.get().payloadHash().equals(proof.digest()) || !CaptureTransactionSource.status(original).equals(proof.status())
						|| proof.received()==null || proof.admitted()==null || proof.received().isAfter(proof.admitted()) || proof.admitted().isAfter(ledger.now())) throw new IllegalStateException("CAPTURE_REUSE_PROOF_CONFLICT");
				anchor |= verifyProof(c,proof,old.get(),original,signature,slot);
			}
			if(!anchor) throw new IllegalStateException("CAPTURE_REUSE_PROVENANCE_CONFLICT");
		}
		CaptureLedgerUseCase.HistoryPage page=null; String row=null; tools.jackson.databind.JsonNode history=null;
		if(supplied!=null) {
			page=ledger.historyPage(c,reference,supplied); ledger.verifyHistoryReference(c,page,pool,signature,slot,reference,received);
			var index=CaptureTransactionSource.parse(reference).path("rowIndex").intValue(); row=page.rows().get(index); history=CaptureTransactionSource.parse(row);
			var rejection=CaptureTransactionSource.rejection(history,signature,slot);
			if(!rejection.isEmpty()) {
				var receipt=sourceReceipt(c,pool,signature,slot,page,reference,null,rejection); retainReceipt.accept(receipt);
				ledger.reuseSource(c,holder,pool,signature,null,null,receipt,false,received,proofs); return Optional.empty();
			}
			if(old.isPresent()) {
				if(original==null) original=verifyRaw(c,old.get(),signature,slot);
				if(!original.equals(history)) throw new IllegalStateException("CAPTURE_REUSE_CONTENT_CONFLICT");
			}
		}
		if(page==null && proofs.isEmpty()) return Optional.empty();
		var payload=row; var digest=old.map(StoredRawTransaction::payloadHash).orElseGet(()->PayloadFingerprint.sha256(payload));
		var receipt=sourceReceipt(c,pool,signature,slot,page,reference,digest,"REUSED"); retainReceipt.accept(receipt); retainReceipt.accept(receipt);
		StoredRawTransaction stored;
		if(old.isPresent()) stored=old.get();
		else {
			retainReceipt.accept(row);
			var n=history; var source=n.path("blockTime").isIntegralNumber()?Optional.of(Instant.ofEpochSecond(n.path("blockTime").longValue())):Optional.<Instant>empty();
			var input=new RawTransactionPayload(ChainId.SOLANA_MAINNET,id,c.provider(),new BlockPosition(ChainId.SOLANA_MAINNET,slot),Optional.empty(),source,page.envelope().receivedAt(),ledger.now(),row,Optional.of("pumpswap-capture-v1"));
			stored=rawBoundary(c,()->storage.store(input));
		}
		var status=CaptureTransactionSource.status(history==null?original:history);
		ledger.reuseSource(c,holder,pool,signature,status,stored.payloadHash(),receipt,old.isPresent(),received,proofs);
		return Optional.of(status);
	}
	private boolean verifyProof(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,io.cryptoresearch.marketdata.infrastructure.persistence.JdbcCaptureStore.CaptureProof proof,StoredRawTransaction raw,tools.jackson.databind.JsonNode original,String signature,long slot) {
		if(!CaptureTransactionSource.parse(proof.notification()).isObject()) throw new IllegalStateException("CAPTURE_REUSE_PROVENANCE_CONFLICT");
		var response=CaptureTransactionSource.parse(proof.response());
		if("2.0".equals(response.path("jsonrpc").asText()) && response.path("id").isIntegralNumber() && response.has("result") && !response.has("error") && response.path("result").equals(original)) return true;
		if(!"capture-transaction-source-v2".equals(response.path("version").asText()) || !c.version().equals(response.path("sourcePolicy").asText()) || !c.sessionId().toString().equals(response.path("session").asText()) || !c.provider().equals(response.path("provider").asText()) || !proof.pool().equals(response.path("pool").asText()) || !signature.equals(response.path("signature").asText()) || !response.path("slot").isIntegralNumber() || !response.path("slot").bigIntegerValue().equals(java.math.BigInteger.valueOf(slot)) || !raw.payloadHash().equals(response.path("rawDigest").asText()) || !"REUSED".equals(response.path("result").asText())) throw new IllegalStateException("CAPTURE_REUSE_PROVENANCE_CONFLICT");
		if("COMMITTED_CAPTURE_RAW".equals(response.path("method").asText())) return false;
		if(!"getTransactionsForAddress".equals(response.path("method").asText())) throw new IllegalStateException("CAPTURE_REUSE_PROVENANCE_CONFLICT");
		var reference=response.path("reference").toString(); var page=ledger.historyPage(c,reference);
		ledger.verifyHistoryReference(c,page,proof.pool(),signature,slot,reference,proof.received());
		var row=CaptureTransactionSource.parse(page.rows().get(response.path("reference").path("rowIndex").intValue()));
		if(!CaptureTransactionSource.rejection(row,signature,slot).isEmpty() || !row.equals(original) || !CaptureTransactionSource.parse(page.context()).equals(response.path("sourceContext")) || !page.envelope().receivedAt().toString().equals(response.path("receivedAt").asText())) throw new IllegalStateException("CAPTURE_REUSE_PROVENANCE_CONFLICT");
		return true;
	}
	private tools.jackson.databind.JsonNode verifyRaw(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,StoredRawTransaction old,String signature,long slot) {
		if(!old.chain().equals(ChainId.SOLANA_MAINNET) || !old.provider().equals(c.provider()) || !old.transactionId().value().equals(signature) || old.blockPosition().value()!=slot
				|| !PayloadFingerprint.sha256(old.payload()).equals(old.payloadHash()) || old.receivedAt().isAfter(old.admittedAt()) || old.admittedAt().isAfter(old.ingestedAt())
				|| old.ingestedAt().isAfter(ledger.now()) || !old.parserVersion().equals(Optional.of("pumpswap-capture-v1")) || old.blockHash().isPresent()) throw new IllegalStateException("CAPTURE_REUSE_RAW_CONFLICT");
		var n=CaptureTransactionSource.parse(old.payload()); if(!CaptureTransactionSource.rejection(n,signature,slot).isEmpty()) throw new IllegalStateException("CAPTURE_REUSE_RAW_CONFLICT");
		var source=n.path("blockTime").isIntegralNumber()?Optional.of(Instant.ofEpochSecond(n.path("blockTime").longValue())):Optional.<Instant>empty();
		if(!source.equals(old.sourceEventTime())) throw new IllegalStateException("CAPTURE_REUSE_RAW_CONFLICT"); return n;
	}
	private String sourceReceipt(io.cryptoresearch.marketdata.api.PumpSwapCapture.Configuration c,String pool,String signature,long slot,CaptureLedgerUseCase.HistoryPage page,String reference,String digest,String result) {
		var values=new java.util.LinkedHashMap<String,Object>(); values.put("version","capture-transaction-source-v2"); values.put("sourcePolicy",c.version()); values.put("session",c.sessionId().toString()); values.put("provider",c.provider()); values.put("pool",pool); values.put("signature",signature); values.put("slot",slot); values.put("result",result); values.put("rawDigest",digest);
		values.put("method",page==null?"COMMITTED_CAPTURE_RAW":"getTransactionsForAddress");
		if(page!=null) { values.put("reference",CaptureTransactionSource.parse(reference)); values.put("sourceContext",CaptureTransactionSource.parse(page.context())); values.put("receivedAt",page.envelope().receivedAt().toString()); }
		var text=json.writeValueAsString(values); if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8192) throw new IllegalStateException("CAPTURE_SOURCE_RECEIPT_BYTE_LIMIT"); return text;
	}
	public String transaction(UUID session, String holder, String pool, String signature,
			String result, String envelope, Instant received, boolean finalized) {
		var c=ledger.configuration(session);
		String status;
		if(!finalized) status="UNVERIFIED_FINALITY";
		else if(result==null || "null".equals(result)) status="UNAVAILABLE";
		else {
			try {
				var n=json.readTree(result);
				if(!n.path("slot").isIntegralNumber() || n.path("slot").longValue()<0 || !n.path("transaction").isObject()
						|| !n.path("transaction").path("message").isObject() || !n.path("meta").isObject()
						|| !n.path("meta").has("err") || !signature.equals(n.path("transaction").path("signatures").path(0).asText())) {
					status="UNAVAILABLE";
				}
				else if(n.has("confirmationStatus") && !"finalized".equals(n.path("confirmationStatus").asText())) status="UNVERIFIED_FINALITY";
				else {
					var id=new TransactionId(ChainId.SOLANA_MAINNET,signature);
					var old=rawBoundary(c,()->raw.find(ChainId.SOLANA_MAINNET,id,c.provider()));
					var source=n.path("blockTime").isIntegralNumber()?Optional.of(Instant.ofEpochSecond(n.path("blockTime").longValue())):Optional.<Instant>empty();
					var payload=new RawTransactionPayload(ChainId.SOLANA_MAINNET,id,c.provider(),new BlockPosition(ChainId.SOLANA_MAINNET,n.path("slot").longValue()),
							Optional.empty(),source,old.map(StoredRawTransaction::receivedAt).orElse(received),
							old.map(StoredRawTransaction::admittedAt).orElse(ledger.now()),result,Optional.of("pumpswap-capture-v1"));
					// This transaction commits independently before the fenced checkpoint transition.
					var stored=rawBoundary(c,()->storage.store(payload));
					status=n.path("meta").path("err").isNull()?"CAPTURED":"FAILED_TRANSACTION";
					ledger.finish(session,holder,pool,signature,status,stored.payloadHash(),envelope,old.isPresent());
					return status;
				}
			}
			catch(RawTransactionConflictException conflict) { status="CONFLICT"; }
			catch(IllegalArgumentException malformed) { status="UNAVAILABLE"; }
		}
		ledger.finish(session,holder,pool,signature,status,null,envelope,false);
		return status;
	}
}
