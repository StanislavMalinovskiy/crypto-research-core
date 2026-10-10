package io.cryptoresearch.marketdata.application;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;

/** Versioned identities are separate from complete canonical immutable content. */
@Component
public class PumpSwapOperationCanonicalizer {
    public static final String STORAGE_VERSION = "pumpswap-storage-v1";
    public static final long MAX_BYTES = 64_000_000;
    private final JsonMapper json = JsonMapper.builder().build();
    public record TransactionRow(String key, String digest, String content, PumpSwapOperations.TransactionResult transaction, List<String> operationKeys) {
        public TransactionRow { operationKeys = List.copyOf(operationKeys); }
    }
    public record OperationRow(String canonicalKey, String key, String transactionKey, String digest, String content, PumpSwapOperations.Operation operation) { }
    public record Prepared(String key, String digest, String content, PumpSwapOperations.Result result, List<TransactionRow> transactions, List<OperationRow> operations) {
        public Prepared {
            transactions = List.copyOf(transactions); operations = List.copyOf(operations);
            if(transactions.size()>128||operations.size()>10000)throw new IllegalArgumentException("PERSISTENCE_LIMIT");
            long bytes=content.getBytes(StandardCharsets.UTF_8).length;
            for(var tx:transactions)bytes+=tx.content().getBytes(StandardCharsets.UTF_8).length;
            for(var op:operations)bytes+=op.content().getBytes(StandardCharsets.UTF_8).length;
            if(bytes>MAX_BYTES)throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");
        }
    }
    public String transactionKey(PumpSwapOperations.Result result, PumpSwapOperations.TransactionResult tx) {
        var fields = json.createObjectNode(); fields.set("source", source(tx.source())); fields.put("storageVersion", STORAGE_VERSION);
        fields.put("decoderVersion", result.decoderVersion()); fields.put("schemaVersion", result.schemaVersion());
        fields.put("canonicalizationVersion", result.canonicalizationVersion()); fields.set("pinnedSourceHashes", tree(result.pinnedSourceHashes()));
        return hash("pumpswap-tx-revision-v1", fields);
    }
    public String operationKey(PumpSwapOperations.Operation op) {
        var fields = json.createObjectNode(); fields.put("chain", op.chain().value()); fields.put("signature", op.signature()); fields.set("position", tree(op.position()));
        return hash("pumpswap-operation-key-v1", fields);
    }
    public String operationRevision(String canonicalKey, String transactionKey) {
        return hash("pumpswap-operation-revision-v1", tree(Map.of("canonicalKey", canonicalKey, "transactionKey", transactionKey)));
    }
    public String digest(String content) { return hash("pumpswap-content-v1", json.readTree(content)); }
    public String encode(Object value) { return canonicalJson(tree(value)); }
    public <T> T decode(String value, Class<T> type) { return json.readValue(value, type); }
    public JsonNode tree(Object value) { return json.valueToTree(value); }
    public String canonicalJson(JsonNode value) {
        var output=new BoundedOutput();json.writeValue(output,sorted(value));return output.toString(StandardCharsets.UTF_8);
    }
    private static final class BoundedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(byte[] bytes,int offset,int length) {
            if((long)count+length>MAX_BYTES)throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");super.write(bytes,offset,length);
        }
        @Override public synchronized void write(int value) {if(count>=MAX_BYTES)throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");super.write(value);}
    }
    private void verifyResultBytes(PumpSwapOperations.Result result) {
        json.writeValue(new java.io.OutputStream() {
            long count;
            @Override public void write(int value) {if(++count>MAX_BYTES)throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");}
            @Override public void write(byte[] bytes,int offset,int length) {count+=length;if(count>MAX_BYTES)throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");}
        },result);
    }
    private JsonNode sorted(JsonNode value) {
        if (value.isObject()) { var result = json.createObjectNode(); var names = new ArrayList<>(value.propertyNames()); Collections.sort(names); for (var name : names) result.set(name, sorted(value.get(name))); return result; }
        if (value.isArray()) { var result = json.createArrayNode(); for (var item : value) result.add(sorted(item)); return result; }
        return value;
    }
    private ObjectNode source(PumpSwapOperations.RecordedTransaction source) { ObjectNode node = (ObjectNode) tree(source); node.remove("rawResult"); return node; }
    public PumpSwapOperations.TransactionResult transaction(String content, String raw) {
        ObjectNode node = (ObjectNode) json.readTree(content).get("transaction"); ((ObjectNode) node.get("source")).put("rawResult", raw);
        return json.treeToValue(node, PumpSwapOperations.TransactionResult.class);
    }
    public PumpSwapOperations.Result result(String manifest, List<PumpSwapOperations.TransactionResult> transactions, List<PumpSwapOperations.Operation> operations) {
        ObjectNode node = (ObjectNode) json.readTree(manifest); node.remove("storageVersion"); node.remove("transactionKeys"); node.remove("operationKeys");
        node.set("transactions", tree(transactions)); node.set("operations", tree(operations)); return json.treeToValue(node, PumpSwapOperations.Result.class);
    }
    public List<String> operationKeys(String manifest) {
        var keys=new ArrayList<String>();var node=json.readTree(manifest).get("operationKeys");
        if(node==null||!node.isArray()||node.size()>10000)throw new IllegalArgumentException("DERIVATION_IMMUTABLE_CONFLICT");
        for(var value:node) {if(!value.isTextual()||!value.asText().matches("sha256:[0-9a-f]{64}"))throw new IllegalArgumentException("DERIVATION_IMMUTABLE_CONFLICT");keys.add(value.asText());}
        if(new HashSet<>(keys).size()!=keys.size())throw new IllegalArgumentException("DERIVATION_IMMUTABLE_CONFLICT");
        return List.copyOf(keys);
    }
    public Prepared prepare(PumpSwapOperations.Result result) {
        Objects.requireNonNull(result, "result");
        if (result.transactions().size() > 128 || result.operations().size() > 10_000) throw new IllegalArgumentException("PERSISTENCE_LIMIT");
        verifyResultBytes(result);
        var transactions = new ArrayList<TransactionRow>(); var operations = new ArrayList<OperationRow>(); long inputBytes = 0, contentBytes = 0;
        var sourceOrder = Comparator.comparing((PumpSwapOperations.TransactionResult t) -> t.source().chain().value()).thenComparing(t -> t.source().signature())
            .thenComparing(t -> t.source().provider()).thenComparing(t -> t.source().session().toString()).thenComparing(t -> t.source().sourceReference());
        for (var tx : result.transactions().stream().sorted(sourceOrder).toList()) {
            long rawBytes = tx.source().rawResult().getBytes(StandardCharsets.UTF_8).length; inputBytes += rawBytes;
            if (rawBytes > 8_000_000 || inputBytes > MAX_BYTES) throw new IllegalArgumentException("PERSISTENCE_LIMIT");
            var key = transactionKey(result, tx); var keys = new ArrayList<String>();
            var matching = result.operations().stream().filter(o -> o.chain().equals(tx.source().chain()) && o.signature().equals(tx.source().signature())
                && o.rawDigest().equals(tx.source().rawDigest()) && o.sourceReference().equals(tx.source().sourceReference())).distinct().sorted(operationOrder()).toList();
            if (tx.status() == PumpSwapOperations.Status.FAILED && !matching.isEmpty()) throw new IllegalArgumentException("FAILED_OPERATIONS");
            for (var op : matching) {
                if (op.position().outerIndex() < 0 || op.position().legOrdinal() < 0 || op.position().innerPath().stream().anyMatch(n -> n < 0)) throw new IllegalArgumentException("INVALID_OPERATION_POSITION");
                var canonicalKey = operationKey(op); var revision = operationRevision(canonicalKey, key); var content = encode(op);
                if (keys.contains(revision)) throw new IllegalArgumentException("DUPLICATE_OPERATION_POSITION");
                keys.add(revision); operations.add(new OperationRow(canonicalKey, revision, key, digest(content), content, op)); contentBytes += content.getBytes(StandardCharsets.UTF_8).length;
            }
            ObjectNode txNode = (ObjectNode) tree(tx); txNode.set("source", source(tx.source()));
            var content = canonicalJson(tree(Map.of("transaction", txNode, "operationKeys", keys, "operationCount", keys.size())));
            transactions.add(new TransactionRow(key, digest(content), content, tx, keys)); contentBytes += content.getBytes(StandardCharsets.UTF_8).length;
        }
        if (operations.size() != result.operations().size() || transactions.stream().map(TransactionRow::key).distinct().count() != transactions.size()) throw new IllegalArgumentException("AMBIGUOUS_DERIVATION_MEMBERSHIP");
        // The decoder already supplies its total order; equal path/reference ties retain source order.
        var rowsByOperation=new HashMap<PumpSwapOperations.Operation,ArrayDeque<OperationRow>>();
        for(var row:operations)rowsByOperation.computeIfAbsent(row.operation(),ignored->new ArrayDeque<>()).add(row);
        var orderedOperations=new ArrayList<OperationRow>();
        for(var operation:result.operations()) {
            var rows=rowsByOperation.get(operation);if(rows==null||rows.isEmpty())throw new IllegalArgumentException("AMBIGUOUS_DERIVATION_MEMBERSHIP");
            orderedOperations.add(rows.removeFirst());
        }
        operations=orderedOperations;
        ObjectNode manifest = (ObjectNode) tree(result); manifest.remove("transactions"); manifest.remove("operations"); manifest.put("storageVersion", STORAGE_VERSION);
        manifest.set("transactionKeys", tree(transactions.stream().map(TransactionRow::key).toList())); manifest.set("operationKeys", tree(operations.stream().map(OperationRow::key).toList()));
        var content = canonicalJson(manifest); contentBytes += content.getBytes(StandardCharsets.UTF_8).length;
        if (contentBytes > MAX_BYTES) throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");
        return new Prepared(hash("pumpswap-batch-v1", manifest), digest(content), content, result, transactions, operations);
    }
    public static Comparator<PumpSwapOperations.Operation> operationOrder() {
        return Comparator.comparing((PumpSwapOperations.Operation o) -> o.chain().value()).thenComparing(PumpSwapOperations.Operation::signature).thenComparingInt(o -> o.position().outerIndex())
            .thenComparing(o -> o.position().innerPath(), PumpSwapOperationCanonicalizer::comparePaths).thenComparingInt(o -> o.position().legOrdinal())
            .thenComparing(PumpSwapOperations.Operation::sourceReference).thenComparing(PumpSwapOperations.Operation::rawDigest);
    }
    private static int comparePaths(List<Integer> a, List<Integer> b) { for (int i = 0; i < Math.min(a.size(), b.size()); i++) { int c = Integer.compare(a.get(i), b.get(i)); if (c != 0) return c; } return Integer.compare(a.size(), b.size()); }
    private String hash(String domain, JsonNode node) {
        var output = new ByteArrayOutputStream(); field(output, domain); canonical(output, node);
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(output.toByteArray())); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void canonical(ByteArrayOutputStream output, JsonNode node) {
        if (node.isObject()) { var names = new ArrayList<>(node.propertyNames()); Collections.sort(names); field(output, "object"); field(output, Integer.toString(names.size())); for (var name : names) { field(output, name); canonical(output, node.get(name)); } }
        else if (node.isArray()) { field(output, "array"); field(output, Integer.toString(node.size())); for (var value : node) canonical(output, value); }
        else { field(output, node.isNull() ? "null" : node.isTextual() ? "text" : node.isIntegralNumber() ? "integer" : node.isBoolean() ? "boolean" : "number"); field(output, node.isTextual() ? node.asText() : node.toString()); }
    }
    private void field(ByteArrayOutputStream output, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8); if ((long) output.size() + bytes.length + 4 > MAX_BYTES) throw new IllegalArgumentException("PERSISTENCE_OUTPUT_LIMIT");
        output.writeBytes(ByteBuffer.allocate(4).putInt(bytes.length).array()); output.writeBytes(bytes);
    }
}
