package io.cryptoresearch.marketdata.application;

import io.cryptoresearch.marketdata.api.PumpSwapPoolLegs;
import io.cryptoresearch.marketdata.api.PumpSwapOperations;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapMultiHopPoolLegParser;
import io.cryptoresearch.marketdata.infrastructure.PumpSwapRawOperationParser;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Additive pure derivation; the v4 report remains an exact independent oracle. */
@Service
public class DecodePumpSwapPoolLegsUseCase {
    private final DecodePumpSwapOperationsUseCase v4;
    private final PumpSwapMultiHopPoolLegParser parser;
    public DecodePumpSwapPoolLegsUseCase() { this(new DecodePumpSwapOperationsUseCase(),new PumpSwapMultiHopPoolLegParser()); }
    @org.springframework.beans.factory.annotation.Autowired
    public DecodePumpSwapPoolLegsUseCase(DecodePumpSwapOperationsUseCase v4,PumpSwapMultiHopPoolLegParser parser) {
        this.v4=Objects.requireNonNull(v4);this.parser=Objects.requireNonNull(parser);
    }
    public PumpSwapPoolLegs.Result decode(PumpSwapPoolLegs.Request request) {
        Objects.requireNonNull(request,"request");
        if(request.pools().isEmpty()||request.pools().size()>3)throw new IllegalArgumentException("POOL_BINDING_LIMIT");
        var seen=new HashSet<String>();
        for(var p:request.pools()) {
            for(var address:List.of(p.pool(),p.baseVault(),p.quoteVault(),p.baseMint(),p.quoteMint(),p.baseProgram(),p.quoteProgram()))
                PumpSwapMultiHopPoolLegParser.validAddress(address);
            if(!seen.add(p.pool())||p.baseVault().equals(p.quoteVault())||p.baseMint().equals(p.quoteMint()))throw new IllegalArgumentException("POOL_BINDING_CONFLICT");
        }
        var bindings=request.pools().stream().sorted(Comparator.comparing(PumpSwapPoolLegs.PoolBinding::pool)).toList();
        var old = v4.decode(request.batch());
        var transactions=new ArrayList<PumpSwapOperations.TransactionResult>();var legs=new ArrayList<PumpSwapPoolLegs.PoolLeg>();
        for(var tx:old.transactions()){var parsed=parser.parse(tx,bindings);transactions.add(parsed.transaction());legs.addAll(parsed.legs());if(legs.size()+old.operations().size()>10000)throw new IllegalArgumentException("DECODER_LIMIT");}
        legs.sort(Comparator.comparing((PumpSwapPoolLegs.PoolLeg l)->l.source().chain().value()).thenComparing(l->l.source().signature())
            .thenComparingInt(l->l.position().outerIndex()).thenComparing(l->l.position().innerPath(),PumpSwapRawOperationParser::comparePaths)
            .thenComparingInt(l->l.position().legOrdinal()).thenComparing(l->l.source().provider()).thenComparing(l->l.source().session().toString()));
        var result=new PumpSwapPoolLegs.Result("pumpswap-pool-legs-v5", "single-own-event-pool-net-sol-v1",
                "length-prefixed-v1", old.pinnedSourceHashes(), bindings, old,transactions,legs,old.limitations(),"");
        var json=JsonMapper.builder().build();var bounded=new BoundedOutput();json.writeValue(bounded,result);
        var canonical=new BoundedOutput();field(canonical,"domain","pumpswap-pool-legs-v5/length-prefixed-v1");
        canonical(canonical,json.readTree(bounded.toByteArray()),"result");
        return new PumpSwapPoolLegs.Result(result.decoderVersion(),result.schemaVersion(),result.canonicalizationVersion(),
            result.pinnedSourceHashes(),bindings,old,transactions,legs,result.limitations(),PumpSwapRawOperationParser.sha256(canonical.toByteArray()));
    }
    private static void canonical(BoundedOutput out,JsonNode n,String path){
        if(n.isObject()){var names=new ArrayList<>(n.propertyNames());Collections.sort(names);field(out,path,"object:"+names.size());for(var name:names)canonical(out,n.get(name),path+"/"+name);}
        else if(n.isArray()){field(out,path,"array:"+n.size());for(int j=0;j<n.size();j++)canonical(out,n.get(j),path+"/"+j);}
        else field(out,path,n.isTextual()?"text:"+n.asText():n.toString());
    }
    private static void field(BoundedOutput out,String key,String value){for(var s:List.of(key,value)){var b=s.getBytes(StandardCharsets.UTF_8);out.writeBytes(ByteBuffer.allocate(4).putInt(b.length).array());out.writeBytes(b);}}
    private static final class BoundedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(byte[] b,int offset,int length){if((long)count+length>64_000_000)throw new IllegalArgumentException("DECODER_OUTPUT_LIMIT");super.write(b,offset,length);}
        @Override public synchronized void write(int b){if(count>=64_000_000)throw new IllegalArgumentException("DECODER_OUTPUT_LIMIT");super.write(b);}
        @Override public void writeBytes(byte[] b){write(b,0,b.length);}
    }
}
