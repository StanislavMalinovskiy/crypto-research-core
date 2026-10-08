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
