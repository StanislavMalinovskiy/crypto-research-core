package io.cryptoresearch.marketdata.application;

import org.springframework.stereotype.Service;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapRawOperationParser;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DecodePumpSwapOperationsUseCase {
    private final PumpSwapRawOperationParser parser;
    public DecodePumpSwapOperationsUseCase(){this(new PumpSwapRawOperationParser());}
    @org.springframework.beans.factory.annotation.Autowired
    public DecodePumpSwapOperationsUseCase(PumpSwapRawOperationParser parser){this.parser=parser;}
    public PumpSwapOperations.Result decode(PumpSwapOperations.RecordedBatch batch) {
        Objects.requireNonNull(batch,"batch");
        var sources=new HashMap<List<Object>,PumpSwapOperations.RecordedTransaction>();long total=0;
        for(var source:batch.transactions()) {
            Objects.requireNonNull(source,"source");if(utf8Length(source.rawResult())>8_000_000)throw new IllegalArgumentException("DECODER_LIMIT");var bytes=source.rawResult().getBytes(StandardCharsets.UTF_8);
            total+=bytes.length;if(bytes.length>8_000_000||total>64_000_000)throw new IllegalArgumentException("DECODER_LIMIT");
            if(!PumpSwapRawOperationParser.sha256(bytes).equals(source.rawDigest()))throw new IllegalArgumentException("HASH_MISMATCH");
            if(source.session()==null||source.chain()==null||!source.chain().value().startsWith("solana:")||source.signature().isBlank()||source.provider().isBlank()||source.sourceReference().isBlank()||source.slot()<0
                ||source.receivedAt().isAfter(source.admittedAt())||source.admittedAt().isAfter(source.ingestedAt()))throw new IllegalArgumentException("SOURCE_LINEAGE_INVALID");
            var key=List.<Object>of(source.session(),source.chain(),source.signature(),source.provider());var previous=sources.putIfAbsent(key,source);
            if(previous!=null&&!previous.equals(source))throw new IllegalArgumentException("RAW_IMMUTABLE_CONFLICT");
            if(sources.size()>128)throw new IllegalArgumentException("DECODER_LIMIT");
        }
        var ordered=sources.values().stream().sorted(Comparator.comparing((PumpSwapOperations.RecordedTransaction s)->s.chain().value()).thenComparing(PumpSwapOperations.RecordedTransaction::signature).thenComparing(PumpSwapOperations.RecordedTransaction::provider).thenComparing(s->s.session().toString())).toList();
        var transactions=new ArrayList<PumpSwapOperations.TransactionResult>();var operations=new ArrayList<PumpSwapOperations.Operation>();
        for(var source:ordered){var parsed=parser.parse(source);transactions.add(parsed.transaction());operations.addAll(parsed.operations());if(operations.size()>10000)throw new IllegalArgumentException("DECODER_LIMIT");}
        operations.sort(Comparator.comparing((PumpSwapOperations.Operation o)->o.chain().value()).thenComparing(PumpSwapOperations.Operation::signature).thenComparingInt(o->o.position().outerIndex()).thenComparing(o->o.position().innerPath(),PumpSwapRawOperationParser::comparePaths).thenComparingInt(o->o.position().legOrdinal()).thenComparing(PumpSwapOperations.Operation::sourceReference));
        var pins=Map.of("pumpIdl",PumpSwapRawOperationParser.IDL_HASH,"pumpIdlOctober7",PumpSwapRawOperationParser.OCTOBER_IDL_HASH,"pumpSwapSdk2.1.0",PumpSwapRawOperationParser.SDK_HASH,"token2022",PumpSwapRawOperationParser.TOKEN2022_HASH,
            "token2022Processor","sha256:9cdc9a2066d14e5fa420d864e6b02daf0b1c5881711d6eb7d0706850c80652d4","token2022ExtensionLayout","sha256:7df80a802e151ff13a4f8ac517ca103f741242a313de366de326df478d72cb9e","token2022ImmutableOwner","sha256:8346ba8c6b309d75280550585a1bd7b6a43f0059db9fda0fc4d43d5ac408647d",
            "pumpSwapSdkBuy","sha256:f0da0f9a62fe2eab0c5780205b61abcce8d20686f81b7896a9b00db377bc0f12","pumpSwapSdkExactInputEventProof","sha256:e3d98cc714113a6d621fd89aba5da48658ad5e9abf69db35058ff4276b706721",
            "classicTokenProcessor","sha256:400cdf9d6d5d18cb3cade83abda7ad4ffb3894ede5e29c189cf8fa2dfa5160a1");
        var limitations=batch.limitations().stream().distinct().sorted().toList();
        var result=new PumpSwapOperations.Result("pumpswap-operations-v4","may18-oct07-endpoint-proof-v4","length-prefixed-v1",pins,transactions,operations,limitations,"");
        var json=JsonMapper.builder().build();var bounded=new BoundedOutput();json.writeValue(bounded,result);var output=bounded.toByteArray();
        var canonical=new ByteArrayOutputStream();field(canonical,"domain","pumpswap-operations-v4/length-prefixed-v1");canonical(canonical,json.readTree(output),"result");
        return new PumpSwapOperations.Result(result.decoderVersion(),result.schemaVersion(),result.canonicalizationVersion(),pins,transactions,operations,limitations,PumpSwapRawOperationParser.sha256(canonical.toByteArray()));
    }
    private void canonical(ByteArrayOutputStream out,JsonNode node,String path){
        if(node.isObject()){var names=new ArrayList<>(node.propertyNames());Collections.sort(names);field(out,path,"object:"+names.size());for(String name:names)canonical(out,node.get(name),path+"/"+name);}
        else if(node.isArray()){field(out,path,"array:"+node.size());for(int n=0;n<node.size();n++)canonical(out,node.get(n),path+"/"+n);}
        else field(out,path,node.isTextual()?"text:"+node.asText():node.toString());
    }
    private void field(ByteArrayOutputStream out,String name,String value){for(String s:List.of(name,value)){var bytes=s.getBytes(StandardCharsets.UTF_8);out.writeBytes(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());out.writeBytes(bytes);}}
    private long utf8Length(String s){long length=0;for(int n=0;n<s.length();n++){char c=s.charAt(n);if(c<128)length++;else if(c<2048)length+=2;else if(Character.isHighSurrogate(c)&&n+1<s.length()&&Character.isLowSurrogate(s.charAt(n+1))){length+=4;n++;}else if(Character.isSurrogate(c))throw new IllegalArgumentException("INVALID_UTF8_TEXT");else length+=3;}return length;}
    private static final class BoundedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(byte[] bytes,int offset,int length){if((long)count+length>64_000_000)throw new IllegalArgumentException("DECODER_OUTPUT_LIMIT");super.write(bytes,offset,length);}
        @Override public synchronized void write(int b){if(count>=64_000_000)throw new IllegalArgumentException("DECODER_OUTPUT_LIMIT");super.write(b);}
    }
}
