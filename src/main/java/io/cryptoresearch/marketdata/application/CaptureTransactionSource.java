package io.cryptoresearch.marketdata.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Eligibility of complete transport evidence; it makes no economic-decoding claim. */
final class CaptureTransactionSource {
    private CaptureTransactionSource() { }
    private static final JsonMapper JSON=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    static JsonNode parse(String text) {
        try { return JSON.readTree(text); }
        catch(RuntimeException invalid) { throw new IllegalStateException("CAPTURE_REUSE_CONTENT_CONFLICT"); }
    }
    static String rejection(JsonNode n,String signature,long slot) {
        if(!n.isObject() || !n.path("slot").isIntegralNumber() || !n.path("slot").bigIntegerValue().equals(java.math.BigInteger.valueOf(slot))
                || !signature.equals(n.path("transaction").path("signatures").path(0).asText())) throw new IllegalStateException("CAPTURE_REUSE_IDENTITY_CONFLICT");
        if(n.has("confirmationStatus") && !"finalized".equals(n.path("confirmationStatus").asText())) return "PROVISIONAL";
        var version=n.path("version");
        if(version.isMissingNode()) return "INCOMPLETE";
        if(!(version.isString() && "legacy".equals(version.asText()) || version.isIntegralNumber() && (version.bigIntegerValue().equals(java.math.BigInteger.ZERO) || version.bigIntegerValue().equals(java.math.BigInteger.ONE)))) return "UNSUPPORTED_SHAPE";
        var tx=n.path("transaction"); var message=tx.path("message"); var meta=n.path("meta");
        if(!tx.path("signatures").isArray() || tx.path("signatures").isEmpty() || !message.isObject()
                || !message.path("accountKeys").isArray() || message.path("accountKeys").isEmpty()
                || !message.path("instructions").isArray() || !message.path("header").isObject()
                || !message.path("recentBlockhash").isString() || !meta.isObject() || !meta.has("err")
                || !(meta.path("err").isNull() || meta.path("err").isObject() || meta.path("err").isString() && !meta.path("err").asText().isBlank()) || !nonnegative(meta.path("fee"))) return "INCOMPLETE";
        var config=message.path("transactionConfig"); var v1=version.isIntegralNumber() && version.bigIntegerValue().equals(java.math.BigInteger.ONE);
        if(v1) {
            if(!config.isObject()) return "INCOMPLETE";
            for(var name:java.util.List.of("computeUnitLimit","heapSize","loadedAccountsDataSizeLimit","priorityFee")) {
                if(!config.has(name)) return "INCOMPLETE";
                if(!config.path(name).isNull() && !nonnegative(config.path(name))) return "UNSUPPORTED_SHAPE";
            }
            if(message.has("addressTableLookups")) return "UNSUPPORTED_SHAPE";
        } else if(message.has("transactionConfig")) return "UNSUPPORTED_SHAPE";
        var header=message.path("header");
        for(var name:java.util.List.of("numRequiredSignatures","numReadonlySignedAccounts","numReadonlyUnsignedAccounts")) if(!header.has(name)) return "INCOMPLETE";
        for(var name:java.util.List.of("numRequiredSignatures","numReadonlySignedAccounts","numReadonlyUnsignedAccounts"))
            if(!index(header.path(name),message.path("accountKeys").size()+1L)) return "UNSUPPORTED_SHAPE";
        var required=header.path("numRequiredSignatures").intValue();
        if(required!=tx.path("signatures").size() || required==0 || header.path("numReadonlySignedAccounts").intValue()>required || header.path("numReadonlyUnsignedAccounts").intValue()>message.path("accountKeys").size()-required) return "UNSUPPORTED_SHAPE";
        try {
            for(var sig:tx.path("signatures")) { if(!sig.isString()) return "UNSUPPORTED_SHAPE"; CaptureBudget.signature(sig.asText()); }
            for(var key:message.path("accountKeys")) { if(!key.isString()) return "UNSUPPORTED_SHAPE"; CaptureBudget.address(key.asText()); }
            CaptureBudget.address(message.path("recentBlockhash").asText());
        } catch(IllegalArgumentException invalid) { return "UNSUPPORTED_SHAPE"; }
        var count=message.path("accountKeys").size(); var loaded=meta.path("loadedAddresses");
        if(!loaded.isMissingNode() && !loaded.isNull()) {
            if(!loaded.isObject() || !loaded.path("writable").isArray() || !loaded.path("readonly").isArray()) return "UNSUPPORTED_SHAPE";
            for(var field:java.util.List.of("writable","readonly")) for(var key:loaded.path(field)) {
                try { if(!key.isString()) return "UNSUPPORTED_SHAPE"; CaptureBudget.address(key.asText()); } catch(IllegalArgumentException invalid) { return "UNSUPPORTED_SHAPE"; }
                count=Math.addExact(count,1);
            }
        }
        if(v1 && count!=message.path("accountKeys").size()) return "UNSUPPORTED_SHAPE";
        var lookups=message.path("addressTableLookups");
        if(!lookups.isMissingNode() && !lookups.isNull() && !lookups.isArray()) return "UNSUPPORTED_SHAPE";
        if(lookups.isArray() && !lookups.isEmpty() && !loaded.isObject()) return "INCOMPLETE";
        int writable=0,readonly=0;
        for(var lookup:lookups) {
            try { if(!lookup.path("accountKey").isString()) return "UNSUPPORTED_SHAPE"; CaptureBudget.address(lookup.path("accountKey").asText()); } catch(IllegalArgumentException invalid) { return "UNSUPPORTED_SHAPE"; }
            for(var field:java.util.List.of("writableIndexes","readonlyIndexes")) {
                if(!lookup.path(field).isArray()) return "UNSUPPORTED_SHAPE";
                for(var value:lookup.path(field)) if(!index(value,256)) return "UNSUPPORTED_SHAPE";
            }
            writable+=lookup.path("writableIndexes").size(); readonly+=lookup.path("readonlyIndexes").size();
        }
        if(writable!=loaded.path("writable").size() || readonly!=loaded.path("readonly").size()) return "UNSUPPORTED_SHAPE";
        for(var name:java.util.List.of("preBalances","postBalances")) {
            if(!meta.path(name).isArray() || meta.path(name).size()!=count) return "INCOMPLETE";
            for(var value:meta.path(name)) if(!nonnegative(value)) return "UNSUPPORTED_SHAPE";
        }
        for(var name:java.util.List.of("preTokenBalances","postTokenBalances","innerInstructions","logMessages"))
            if(!meta.has(name) || !(meta.path(name).isArray() || meta.path(name).isNull())) return "INCOMPLETE";
        for(var instruction:message.path("instructions")) if(!instruction(instruction,count)) return "UNSUPPORTED_SHAPE";
        for(var field:java.util.List.of("preTokenBalances","postTokenBalances")) for(var balance:meta.path(field)) {
            var amount=balance.path("uiTokenAmount");
            if(!index(balance.path("accountIndex"),count) || !balance.path("mint").isString() || !amount.isObject()
                    || !amount.path("amount").isString() || !amount.path("amount").asText().matches("[0-9]+") || !index(amount.path("decimals"),256)
                    || !amount.has("uiAmount") || !(amount.path("uiAmount").isNull() || amount.path("uiAmount").isNumber()) || !amount.path("uiAmountString").isString()) return "UNSUPPORTED_SHAPE";
            try { CaptureBudget.address(balance.path("mint").asText()); } catch(IllegalArgumentException invalid) { return "UNSUPPORTED_SHAPE"; }
        }
        for(var group:meta.path("innerInstructions")) {
            if(!index(group.path("index"),message.path("instructions").size()) || !group.path("instructions").isArray()) return "UNSUPPORTED_SHAPE";
            for(var instruction:group.path("instructions")) if(!instruction(instruction,count)) return "UNSUPPORTED_SHAPE";
        }
        for(var line:meta.path("logMessages")) if(!line.isString()) return "UNSUPPORTED_SHAPE";
        return "";
    }
    private static boolean nonnegative(JsonNode n) { return n.isIntegralNumber() && n.bigIntegerValue().signum()>=0 && n.bigIntegerValue().bitLength()<=64; }
    private static boolean index(JsonNode n,long bound) { return n.isIntegralNumber() && n.bigIntegerValue().signum()>=0 && n.bigIntegerValue().compareTo(java.math.BigInteger.valueOf(bound))<0; }
    private static boolean instruction(JsonNode n,int count) {
        if(!n.isObject() || !index(n.path("programIdIndex"),count) || !n.path("accounts").isArray() || !n.path("data").isString()) return false;
        for(var value:n.path("accounts")) if(!index(value,count)) return false;
        return true;
    }
    static String status(JsonNode n) { return n.path("meta").path("err").isNull()?"CAPTURED":"FAILED_TRANSACTION"; }
}
