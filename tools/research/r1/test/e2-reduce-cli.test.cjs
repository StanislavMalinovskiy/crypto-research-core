'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const capture=path.join(__dirname,'../e2-cohort-d1-window.cjs');
const w=require(capture),base=path.dirname(capture),census=require(path.join(base,'e2-offline-mint-count.cjs'));
const {digest,canonical}=require(path.join(base,'exploratory-probe.cjs'));
const cli=path.join(__dirname,'../e2-reduce-cli.cjs');
// Before implementation the actual exported original replay is the behavioral
// baseline. Its whole-manifest PASS cannot certify a requested in-flight scope.
const actual=fs.existsSync(cli)?require(cli):{verifyScope:(io,o)=>w.replay(io,o.head,{now:()=>w.START+1000})};
// Only synthetic in-memory fixtures use this explicit context. The pinned commit
// is a fixture field, NOT a claim about this checkout or actual capture evidence.
// Original replay still reads its real local lineage; no deps.lineage override.
const identity={runtime:process.version,synthetic:true,source:{commit:'1f8b07d1d2d2fa5e914f25c85bf1aaa25ec3d63a',dirty:false,synthetic:true},scripts:Object.fromEntries(['e2-cohort-d1-window.cjs','pumpswap-mapper-v2.cjs','e2-offline-mint-count.cjs','exploratory-probe.cjs'].map(n=>[n,digest(fs.readFileSync(path.join(base,n)))]))};
const synthetic={w,h:require(path.join(base,'exploratory-probe.cjs')),identity,provenance:{path:capture,synthetic:true}};
const target={...actual,verifyScope:(io,o)=>actual.verifyScope(io,o,synthetic),recheck:(io,r)=>actual.recheck(io,r,synthetic),reductionConsumer:(policy,ctx,facts)=>actual.reductionConsumer(policy,ctx??synthetic,facts)};
const alphabet='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function addr(n,length=32){const bytes=Buffer.alloc(length);bytes.writeUInt32BE(n,length-4);let x=BigInt('0x'+bytes.toString('hex')),s='';while(x){s=alphabet[Number(x%58n)]+s;x/=58n;}for(const b of bytes){if(b)break;s='1'+s;}return s;}
function selected(){const c=w.collector();for(let m=0;m<3;m++)for(let i=0;i<100;i++)c.add({status:'OBSERVED_DECLARED_CREATE_POOL',layoutApplicability:'UNVERIFIED',slot:census.ROOTS[m].start,timestamp:census.DATES[m],transactionIndex:i,instructionPath:[0],cpiDepth:0,accountCount:18,dataLength:59,lineOrdinal:0,rawHash:digest(Buffer.from('synthetic')),signature:addr(1000+m*100+i,64),pool:addr(10000+m*100+i),globalConfig:addr(2),creator:addr(3),baseMint:addr(1000+m*100+i),quoteMint:census.QUOTES[0]},m);return c.finish([]);}
function fixture(count=5,rowFactory=null){const selection=selected(),s=w.initial(selection,{creditsReservedBefore:0,ancillaryStartsBefore:0,publicStartsBefore:0,receivedBefore:0},structuredClone(identity)),files=new Map();
 s.phase=count===300?'SOURCE_COMPLETE':'QUALIFICATION_PENDING';
 const put=(n,v)=>files.set(n,Buffer.isBuffer(v)?v:Buffer.from(JSON.stringify(v)+'\n'));
 put('selection.json',selection);
 const seen=new Set();for(let i=0;i<count;i++){const rows=rowFactory?rowFactory(selection,i):[],raw=Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:rows,paginationToken:null}})),query=w.query(selection.tokens[i].addresses[0]),admitted=w.admit(raw,query),start=w.START+1000+i*250;
 let equalScopedRows=0;for(const row of admitted.rows){const key=row.transaction.signatures[0];if(seen.has(key))equalScopedRows++;seen.add(key);}
 const r={ordinal:i+1,tokenIndex:i,addressIndex:0,query,creditsReserved:100,actualCredits:null,reservedAt:start,start,transportStarted:true,end:start+1,received:raw.length,code:null,status:200,responseHash:digest(raw),rawHash:digest(raw),rawBytes:raw.length,mapping:w.mappingCounts(admitted.rows,admitted.rowBytes),equalScopedRows,returnedRows:rows.length,cursor:null,outcome:'RETAINED'};
 w.reserve(s,i);w.applyPage(s,i,admitted);s.actualHistoryStarts++;s.received+=raw.length;s.records.push(r);const n=String(i+1).padStart(5,'0');
 put(n+'.raw',raw);put(n+'.start.json',{ordinal:r.ordinal,tokenIndex:i,addressIndex:0,query,creditsReserved:100,actualCredits:null,reservedAt:start,start:null,outcome:'START_RESERVED'});put(n+'.receipt.json',r);}
 const io={files,read:n=>Buffer.from(files.get(n)),list:()=>[...files.keys()],size:()=>[...files].filter(([n])=>n!=='lock').reduce((a,[,b])=>a+b.length,0)};
 const publish=()=>{s.retained=0;for(let i=0;i<8;i++){put('manifest.json',w.seal('manifest',s));s.retained=io.size();}put('manifest.json',w.seal('manifest',s));return w.seal('manifest',s).hash;};
 return {io,s,publish,head:publish()};}
test('verifier refuses an in-flight token even when original whole-manifest replay passes',()=>{
 const f=fixture();assert.equal(w.replay(f.io,f.head,{now:()=>w.START+1000}).semanticIntegrity,'PASS');
 assert.throws(()=>target.verifyScope(f.io,{head:f.head,tokens:6}),/INFLIGHT_SCOPE/,'requested sixth token is NOT_STARTED and must never be verified');
});
module.exports={fixture,capture,w,canonical,digest};

test('literal first-five original replay parity, distinct version and unchanged denominator',()=>{
 const f=fixture(),original=w.replay(f.io,f.head,{now:()=>w.START+1000}),v=target.verifyScope(f.io,{head:f.head,tokens:5});
 for(const k of ['semanticIntegrity','budgetValidity','recordedTiming','phase','manifestHash','creditsReserved','actualCredits','received','rawFiles','uniqueTransactions','qualification','verdict'])assert.equal(canonical(v[k]),canonical(original[k]),k);
 assert.equal(v.version,'e2-read-only-verifier-v2');assert.equal(v.scope.whole,false);assert.equal(v.scope.cohortDenominator,300);assert.equal(v.scope.unverifiedTokens,295);assert.equal(v.d1Passed,false);assert.equal(v.originalFullReplay.status,'NOT_RUN');
});
test('append leaves immutable terminal prefix valid; checked mutation and orphan fail',()=>{
 const f=fixture(),v=target.verifyScope(f.io,{head:f.head,tokens:5}),later=fixture(6);
 assert.doesNotThrow(()=>target.recheck(later.io,v));
 later.io.files.set('00001.raw',Buffer.from('mutated'));assert.throws(()=>target.recheck(later.io,v),/IMMUTABLE_CONFLICT/);
 const orphan=fixture();orphan.io.files.set('orphan.raw',Buffer.from('x'));assert.throws(()=>target.verifyScope(orphan.io,{head:orphan.head,tokens:5}),/ORPHAN_FILE/);
});
for(const [name,mutate] of [
 ['query',f=>{f.s.records[0].query.body.params[1].limit=999;}],
 ['counter',f=>{f.s.creditsReserved++;}],
 ['receipt',f=>{f.io.files.set('00001.receipt.json',Buffer.from('{}'));}],
 ['raw',f=>{f.io.files.set('00001.raw',Buffer.from('{}'));}],
 ['lineage',f=>{f.s.lineage.scripts['e2-cohort-d1-window.cjs']=digest(Buffer.from('other'));}],
])test('v2 and original both reject '+name+' tamper',()=>{const f=fixture();mutate(f);const head=f.publish();assert.throws(()=>w.replay(f.io,head,{now:()=>w.START+1000}));assert.throws(()=>target.verifyScope(f.io,{head,tokens:5}));});
test('recorded timing parity does not conflate semantic and budget validity',()=>{
 const f=fixture();f.s.records[1].start=f.s.records[0].start+1;f.s.records[1].end=f.s.records[1].start+1;f.s.records[1].reservedAt=f.s.records[1].start;
 const r=f.s.records[1],n='00002';f.io.files.set(n+'.receipt.json',Buffer.from(JSON.stringify(r)+'\n'));
 f.io.files.set(n+'.start.json',Buffer.from(JSON.stringify({ordinal:2,tokenIndex:1,addressIndex:0,query:r.query,creditsReserved:100,actualCredits:null,reservedAt:r.reservedAt,start:null,outcome:'START_RESERVED'})+'\n'));
 const head=f.publish(),a=w.replay(f.io,head,{now:()=>w.START+1000}),b=target.verifyScope(f.io,{head,tokens:5});assert.deepEqual(b.recordedTiming,a.recordedTiming);assert.equal(b.recordedTiming.status,'FAIL');assert.equal(b.semanticIntegrity,'PASS');assert.equal(b.budgetValidity,'PASS');
});
test('cumulative projection preserves previous charges, deadline and raw read ceilings',()=>{
 assert.equal(target.allowance(690794562),448680);assert.equal(target.allowance(50000000000),10800000);
 const b={scopeBytes:690794562,elapsedMs:448679,rawReadBytes:2072383686,metadataBytes:64000000};assert.doesNotThrow(()=>target.budget(b,target.END-1));
 assert.throws(()=>target.budget({...b,elapsedMs:448680},target.END-1),/TIME_LIMIT/);assert.throws(()=>target.budget(b,target.END),/TIME_LIMIT/);assert.throws(()=>target.budget({...b,rawReadBytes:2072383687},target.END-1),/TIME_LIMIT/);
});
test('exact cross-scope signature content dedup and conflict',()=>{const i=new target.Index(),sig=addr(123,64),a=digest(Buffer.from('a'));assert.equal(i.add(sig,a),true);assert.equal(i.add(sig,a),false);assert.equal(i.size,1);assert.throws(()=>i.add(sig,digest(Buffer.from('b'))),/IMMUTABLE_CONFLICT/);});
test('actual capture pin rejects another path/commit; reducer bytes immutable',()=>{
 assert.throws(()=>target.loadCapture(path.join(__dirname,'../e2-cohort-d1-window.cjs')),/LINEAGE_CHANGED/);assert.throws(()=>target.loadCapture(capture,'a'.repeat(40)),/LINEAGE_CHANGED/);
 assert.equal(digest(fs.readFileSync(path.join(__dirname,'../e2-trade-reducer-v1.cjs'))),'sha256:06662533e841226aebee48e993899fe3e3ebdf8421787ebf489c853ecf657137');
});
function buy(base,explicitFees=false){
 const TOKEN='TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',PUMP='pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA',WSOL='So11111111111111111111111111111111111111112';
 const accounts=Array.from({length:23},(_,i)=>addr(500+i));accounts[3]=base;accounts[4]=WSOL;accounts[11]=accounts[12]=TOKEN;accounts[13]='11111111111111111111111111111111';accounts[14]='ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL';accounts[16]=PUMP;
 const keys=[accounts[1]],key=a=>{if(!keys.includes(a))keys.push(a);return keys.indexOf(a);};
 const encode=bytes=>{let x=BigInt('0x'+bytes.toString('hex')),s='';while(x){s=alphabet[Number(x%58n)]+s;x/=58n;}for(const b of bytes){if(b)break;s='1'+s;}return s;};
 const ix=(program,a,data,stackHeight)=>({programIdIndex:key(program),accounts:a.map(key),data:encode(data),...(stackHeight?{stackHeight}:{})});
 const data=Buffer.alloc(25);Buffer.from('66063d1201daebea','hex').copy(data);const outer=ix(PUMP,accounts,data),transfer=(a,b,owner,amount)=>{const data=Buffer.alloc(9);data[0]=3;data.writeBigUInt64LE(BigInt(amount),1);return ix(TOKEN,[a,b,owner],data,2);};
 const instructions=[outer];if(explicitFees){const price=Buffer.alloc(9),limit=Buffer.alloc(5);price[0]=3;price.writeBigUInt64LE(1000001n,1);limit[0]=2;limit.writeUInt32LE(3,1);instructions.unshift(ix('ComputeBudget111111111111111111111111111111',[],price),ix('ComputeBudget111111111111111111111111111111',[],limit));}
 const inner=[transfer(accounts[6],accounts[8],accounts[1],9),transfer(accounts[6],accounts[10],accounts[1],1),transfer(accounts[6],accounts[17],accounts[1],0),transfer(accounts[7],accounts[5],accounts[0],3)];
 const states=[[5,base,accounts[1],'9007199254740993123','9007199254740993126'],[6,WSOL,accounts[1],'100','90'],[7,base,accounts[0],'100','97'],[8,WSOL,accounts[0],'100','109'],[10,WSOL,accounts[9],'0','1'],[17,WSOL,accounts[18],'0','0']].map(([i,mint,owner,pre,post])=>({accountIndex:key(accounts[i]),mint,owner,pre,post}));
 const raw={slot:410195947,transactionIndex:0,blockTime:1775001600,version:0,transaction:{signatures:[addr(700,64)],message:{header:{numRequiredSignatures:1,numReadonlySignedAccounts:0,numReadonlyUnsignedAccounts:0},accountKeys:keys,instructions}},meta:{err:null,fee:explicitFees?'5004':'5000',loadedAddresses:{writable:[],readonly:[]},innerInstructions:[{index:explicitFees?2:0,instructions:inner}],preTokenBalances:states.map(x=>({accountIndex:x.accountIndex,mint:x.mint,owner:x.owner,uiTokenAmount:{amount:x.pre,decimals:9}})),postTokenBalances:states.map(x=>({accountIndex:x.accountIndex,mint:x.mint,owner:x.owner,uiTokenAmount:{amount:x.post,decimals:9}})),preBalances:keys.map(()=>'10000000'),postBalances:keys.map(()=>'7000000')}};
 const policy={version:'SYNTHETIC_ONLY',chain:'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',cohortMints:[base],quoteMints:[WSOL],venues:[PUMP],protocolFeeAccounts:[{pool:accounts[0],account:accounts[10],kind:'PROTOCOL',mint:WSOL,roleIndex:10},{pool:accounts[0],account:accounts[17],kind:'CREATOR',mint:WSOL,roleIndex:17}]};return {raw,policy};
}
test('real mapped economic buy is counted once across pools, with fees UNKNOWN outside numerator',()=>{
 let actual;const f=fixture(5,s=>{actual=buy(s.tokens[0].mint);return [actual.raw];});
 actual.policy.cohortMints=f.s.selection.tokens.map(t=>t.mint);const consumer=target.reductionConsumer(actual.policy),v=target.verifyScope(f.io,{head:f.head,tokens:5,consume:consumer.consume}),reduced=consumer.finish(v);
 const original=w.replay(f.io,f.head,{now:()=>w.START+1000});assert.equal(v.uniqueTransactions,1);assert.equal(original.uniqueTransactions,1);assert.equal(reduced.uniqueTransactions,1);assert.equal(reduced.reconstructed,0);assert.equal(reduced.reasons.FEES_UNDETERMINED,1);assert.equal(reduced.counts.MISSING_LEG,1);assert.equal(reduced.references[0].reason,'FEES_UNDETERMINED');assert.equal(v.qualification.technicalUsable,true);assert.equal(reduced.provenance.length,5);assert.equal(reduced.d1Passed,false);
 // Preserve actual net amounts by comparison to the approved reducer directly.
 const h=require(path.join(base,'exploratory-probe.cjs')),mapper=require(path.join(base,'pumpswap-mapper-v2.cjs')),bytes=Buffer.from(JSON.stringify(actual.raw));
 const r=require('../e2-trade-reducer-v1.cjs').reduceTransaction({mapping:mapper.mapHeliusTransaction(bytes,digest(bytes)),raw:h.parse(bytes),policy:actual.policy,lineage:{rawHash:digest(bytes),knownAt:'UNKNOWN'}});assert.equal(r.actions[0].base.rawDelta,'3');assert.equal(r.actions[0].quote.rawDelta,'-10');
});
test('cross-token content conflicts fail both original and v2, without mocked mapper',()=>{
 const f=fixture(5,(s,i)=>{const raw=buy(s.tokens[0].mint).raw;if(i===1)raw.meta.fee='5001';return [raw];});assert.throws(()=>w.replay(f.io,f.head,{now:()=>w.START+1000}),/IMMUTABLE_CONFLICT/);assert.throws(()=>target.verifyScope(f.io,{head:f.head,tokens:5}),/IMMUTABLE_CONFLICT/);
});
test('wrapper forwards only transaction-bound fee facts to the actual approved fee prover',()=>{
 let actual;const f=fixture(5,s=>{actual=buy(s.tokens[0].mint,true);return [actual.raw];}),hash=digest(Buffer.from(JSON.stringify(actual.raw)));
 // The pure reducer's documented context hash is sorted JSON, whereas capture
 // fingerprinting uses length-prefixed typed fields. These remain distinct.
 const sorted=x=>x&&typeof x==='object'?(Array.isArray(x)?'['+x.map(sorted).join(',')+']':'{'+Object.keys(x).sort().map(k=>JSON.stringify(k)+':'+sorted(x[k])).join(',')+'}'):JSON.stringify(x);
 const plain=x=>digest(Buffer.from(sorted(x))),documents=['https://solana.com/docs/core/fees/fee-structure','https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/program-runtime/src/prioritization_fee.rs','https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/sdk/src/fee.rs'].map(url=>({url,contentHash:plain('SYNTHETIC_DOCUMENT_ONLY')}));
 actual.policy.cohortMints=f.s.selection.tokens.map(x=>x.mint);actual.policy.feeRegime={version:'solana-legacy-v0-explicit-cu-v1',documents};
 const context={chain:actual.policy.chain,slot:'410195947',transactionRawHash:hash,runtimeVersion:'synthetic-runtime-fixture',transactionVersion:'0',lamportsPerSignature:'5000',maxComputeUnitLimit:'1400000',priorityRule:'CEIL_PRICE_TIMES_REQUESTED_LIMIT',limitRule:'EXPLICIT_UNCLAMPED',source:'fixture://recorded-bank-context',sourceHash:plain('SYNTHETIC_BANK_ONLY')},evidence={context,contextHash:plain(context)};
 const h=require(path.join(base,'exploratory-probe.cjs')),bytes=Buffer.from(JSON.stringify(actual.raw)),mapped=require(path.join(base,'pumpswap-mapper-v2.cjs')).mapHeliusTransaction(bytes,hash),direct=require('../e2-trade-reducer-v1.cjs').reduceTransaction({mapping:mapped,raw:h.parse(bytes),policy:actual.policy,lineage:{rawHash:hash,feeRegimeEvidence:evidence}});assert.equal(direct.reconstructedCount,1,JSON.stringify(direct.actions[0]?.fees));
 for(const [facts,expected] of [[{[hash]:evidence},1],[{[digest(Buffer.from('other'))]:evidence},0],[{[hash]:{regimeApproved:true}},0]]){const c=target.reductionConsumer(actual.policy,undefined,facts),v=target.verifyScope(f.io,{head:f.head,tokens:5,consume:c.consume}),r=c.finish(v);assert.equal(r.reconstructed,expected,JSON.stringify(r));assert.equal(r.uniqueTransactions,1);assert.equal(r.d1Passed,false);}
});
test('full terminal zero-history scope requires exact final tail and still cannot admit D1',()=>{
 const f=fixture(300),v=target.verifyScope(f.io,{head:f.head,tokens:300});assert.equal(v.scope.whole,true);assert.equal(v.scope.unverifiedTokens,0);assert.equal(v.d1Passed,false);f.io.files.set('lock',Buffer.from('active'));assert.throws(()=>target.verifyScope(f.io,{head:f.head,tokens:300}),/INFLIGHT_SCOPE/);
});
test('actual supervised CLI is disabled without opt-in and cannot run continuation',async()=>{
 // The historical parent deadline takes precedence over argument guards after
 // END. Evaluate unchanged source with fixed clocks, never a production override.
 const source=fs.readFileSync(cli,'utf8');assert.equal(digest(Buffer.from(source)),'sha256:9896ac930d9d1543ea1ca8dd41d0796af1f28529440399d526631223422cb659');
 const evaluate=(now,parent)=>{let childStarts=0,stdout='';const module={exports:{}},localRequire=require('node:module').createRequire(cli);
  const controlledRequire=name=>name==='node:child_process'?{...localRequire(name),spawnSync:()=>{childStarts++;throw Error('UNEXPECTED_CHILD_START');}}:localRequire(name);
  controlledRequire.main=parent?module:undefined;
  const processFixture={argv:[process.execPath,cli],execPath:process.execPath,execArgv:[],stdout:{write:s=>{stdout+=s;}}};
  const sandbox={require:controlledRequire,module,__filename:cli,__dirname:path.dirname(cli),Buffer,process:processFixture,Date:class extends Date{static now(){return now;}}};
  require('node:vm').runInNewContext(source,sandbox,{filename:cli,timeout:1000});return {run:module.exports.run,process:processFixture,result:()=>JSON.parse(stdout),starts:()=>childStarts};
 };
 const valid=evaluate(w.START+1000,false);
 for(const args of [[],['--enable','true','--mode','continue']])await assert.rejects(valid.run(args),e=>{assert.equal(e.message,args.length?'ARGUMENTS_INVALID':'DISABLED');return true;});
 assert.equal(valid.starts(),0);assert.equal(w.FLAGS.d1Passed,false);
 const expired=evaluate(Date.parse('2026-10-05T04:12:01Z'),true);assert.equal(expired.process.exitCode,1);assert.equal(expired.result().code,'TIME_LIMIT');assert.equal(expired.starts(),0);assert.equal(expired.result().d1Passed,false);
});
test('ancillary sanitized receipt bytes, cumulative starts and timing match original',()=>{
 const f=fixture(),start=w.START+3000,queryHash=digest(Buffer.from('ancillary')),r={ordinal:1,afterHistoryRecords:5,reservedAt:start,credits:1,queryHash,publicStartsReserved:1};
 const e={ordinal:1,queryHash,start,end:start+1,actualStarts:0,publicStarts:1,received:100,status:200,rawHash:digest(Buffer.from('metadata')),actualCredits:null};
 const bytes=Buffer.from(JSON.stringify(e)+'\n');r.externalReceipt={hash:digest(bytes),body:e};f.s.ancillary.push(r);f.s.ancillaryStarts++;f.s.publicStarts++;f.s.received+=100;f.s.creditsReserved++;
 const {externalReceipt,...reserved}=r;f.io.files.set('ancillary-00001.json',Buffer.from(JSON.stringify(reserved)+'\n'));f.io.files.set('ancillary-00001.receipt.json',bytes);const head=f.publish(),a=w.replay(f.io,head,{now:()=>w.START+1000}),b=target.verifyScope(f.io,{head,tokens:5});for(const k of ['received','creditsReserved','budgetValidity','recordedTiming'])assert.equal(canonical(a[k]),canonical(b[k]));
 e.url='SECRET_MUST_NEVER_ENTER_RECEIPT';f.io.files.set('ancillary-00001.receipt.json',Buffer.from(JSON.stringify(e)+'\n'));r.externalReceipt.hash=digest(f.io.files.get('ancillary-00001.receipt.json'));const changed=f.publish();assert.throws(()=>w.replay(f.io,changed,{now:()=>w.START+1000}));assert.throws(()=>target.verifyScope(f.io,{head:changed,tokens:5}));
});
test('qualification needs exact current original replay PASS and independent review, never low activity thresholds',()=>{
 const f=fixture(),v=target.verifyScope(f.io,{head:f.head,tokens:5}),original=w.replay(f.io,f.head,{now:()=>w.START+1000});
 assert.equal(target.qualification(v,v.snapshot,original,true).status,'INDEPENDENT_REVIEW_REQUIRED');
 assert.equal(target.qualification(v,v.snapshot,null,true).eligibleForReview,false);assert.equal(target.qualification(v,v.snapshot,original,false).eligibleForReview,false);
 assert.equal(target.qualification(v,{hash:digest(Buffer.from('later'))},original,true).eligibleForReview,false);
 original.qualification.technicalUsable=false;assert.equal(target.qualification(v,v.snapshot,original,true).eligibleForReview,false);
});
test('completed capture lifecycle QUALIFICATION_PENDING qualifies only for independent review',()=>{
 const f=fixture();f.s.phase='QUALIFICATION_PENDING';const head=f.publish(),v=target.verifyScope(f.io,{head,tokens:5}),original=w.replay(f.io,head,{now:()=>w.START+1000});
 assert.equal(original.phase,'QUALIFICATION_PENDING');assert.equal(original.semanticIntegrity,'PASS');assert.equal(original.budgetValidity,'PASS');assert.equal(original.recordedTiming.status,'PASS');assert.equal(original.qualification.technicalUsable,true);
 assert.equal(target.qualification(v,v.snapshot,original,true).status,'INDEPENDENT_REVIEW_REQUIRED','completed first-five lifecycle must be eligible, never automatically approved');
 for(const [label,mutate] of [
  ['verifier still running',(a,b)=>{a.phase='FIRST_FIVE';}],['original still running',(a,b)=>{b.phase='FIRST_FIVE';}],
  ['stale original head',(a,b)=>{b.manifestHash=digest(Buffer.from('stale'));}],['verifier timing failure',(a,b)=>{a.recordedTiming.status='FAIL';}],
  ['original timing failure',(a,b)=>{b.recordedTiming.status='FAIL';}],['original budget failure',(a,b)=>{b.budgetValidity='FAIL';}],
  ['original semantic failure',(a,b)=>{b.semanticIntegrity='FAIL';}],['source STOP',(a,b)=>{a.snapshot.code='TIME_LIMIT';}],
  ['pending ancillary',(a,b)=>{a.snapshot.ancillary.push({});}],
 ]){const a=structuredClone(v),b=structuredClone(original);mutate(a,b);assert.equal(target.qualification(a,a.snapshot,b,true).eligibleForReview,false,label);}
});
test('synthetic reader test imports are repository-relative, without an owner drive dependency',()=>{
 const source=fs.readFileSync(__filename,'utf8');
 assert.equal(/const capture=['"]C:\//.test(source),false,'synthetic test discovery must not require an external capture worktree');
});
test('all reader synthetic callbacks execute with filesystem reads confined to this checkout',()=>{
 if(process.argv.includes('reader-local-proof'))return;
 const root=path.resolve(__dirname,'../../../..'),program=`
 const fs=require('node:fs'),path=require('node:path'),root=${JSON.stringify(root)};
 for(const name of ['readFileSync','openSync']){const original=fs[name];fs[name]=function(file,...args){if(typeof file==='string'||Buffer.isBuffer(file)||file instanceof URL){const resolved=path.resolve(String(file));if(resolved!==root&&!resolved.startsWith(root+path.sep))throw Error('EXTERNAL_READ_FORBIDDEN:'+resolved);}return original.call(this,file,...args);};}
 require(${JSON.stringify(__filename)});`;
 const p=require('node:child_process').spawnSync(process.execPath,['--max-old-space-size=1024','-e',program,'--','reader-local-proof'],{cwd:root,timeout:30000,maxBuffer:1000000,encoding:'utf8'});
 assert.equal(p.error,undefined);assert.equal(p.status,0,p.stdout+p.stderr);assert.match(p.stdout,/(?:pass|# pass) 26\b/);
});
test('read-only verification/reduction preserves every synthetic source byte and rejects oversized child heap',()=>{
 const f=fixture(),before=[...f.io.files].map(([n,b])=>[n,Buffer.from(b)]),policy={cohortMints:f.s.selection.tokens.map(t=>t.mint)},c=target.reductionConsumer(policy),v=target.verifyScope(f.io,{head:f.head,tokens:5,consume:c.consume});c.finish(v);target.recheck(f.io,v);
 for(const [n,b] of before)assert.ok(f.io.files.get(n).equals(b),n);assert.equal(f.io.files.size,before.length);
 const p=require('node:child_process').spawnSync(process.execPath,['--max-old-space-size=2048',cli,'--bounded-reader'],{timeout:10000,encoding:'utf8'});assert.equal(p.status,1);assert.equal(JSON.parse(p.stdout).code,'RESOURCE_LIMIT');
});
test('bounded physical reader reserves before read and charges exact received bytes without writing',()=>{
 const original=fs.readFileSync(cli),events=[];const bytes=target.boundedRead(cli,32000000,n=>events.push(['received',n]),n=>events.push(['reserved',n]));assert.ok(bytes.equals(original));assert.deepEqual(events,[['reserved',original.length+1],['received',original.length]]);assert.throws(()=>target.boundedRead(cli,1),/INPUT_LIMIT/);assert.ok(fs.readFileSync(cli).equals(original));
});
test('inflated projection is rejected from sealed metadata before any history read',()=>{
 const f=fixture(),read=f.io.read;let rawReads=0;f.io.read=n=>{if(n.endsWith('.raw'))rawReads++;return read(n);};assert.throws(()=>target.verifyScope(f.io,{head:f.head,tokens:5,scopeBytes:50000000000}),/RESOURCE_LIMIT/);assert.equal(rawReads,0);
});
test('bounded synthetic resource proof retains 4.2M exact signatures with near-limit real framing and mapping',t=>{
 if(process.env.E2_READER_RESOURCE!=='1')return;
 const start=performance.now(),index=new target.Index(),content=digest(Buffer.from('synthetic-resource')),limit=1750000000;let peak=0;
 const check=()=>{peak=Math.max(peak,process.memoryUsage().rss);assert.ok(peak<=limit);assert.ok(performance.now()-start<300000);};
 assert.equal(census.decode('2'.repeat(80)+'1'.repeat(8),64).length,64);
 for(let i=0;i<4200000;i++){let n=i,s='';do{s=alphabet[n%58]+s;n=Math.floor(n/58);}while(n);assert.equal(index.add('2'.repeat(80)+s.padStart(8,'1'),content),true);if(i%10000===0)check();}
 assert.equal(index.size,4200000);assert.throws(()=>index.add('3'.repeat(78),content),/INPUT_LIMIT/);check();
 const rows=Array.from({length:1000},(_,i)=>({slot:410195947,transactionIndex:i,blockTime:1775001600,transaction:{signatures:[addr(i+1,64)]},padding:'x'.repeat(63500)}));
 const bytes=Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:rows,paginationToken:null}}));assert.ok(bytes.length>63000000&&bytes.length<=64000000);
 const a=w.admit(bytes,w.query(addr(500)));assert.equal(a.code,null);const mapped=w.mappingCounts(a.rows,a.rowBytes,check);assert.equal(mapped.transactions,1000);assert.equal(mapped.invalid,1000);check();
 t.diagnostic(JSON.stringify({synthetic:true,uniqueSignatures:index.size,pageBytes:bytes.length,rows:mapped.transactions,elapsedMs:performance.now()-start,peakRss:peak,maxRss:process.resourceUsage().maxRSS*1024,heapLimit:require('node:v8').getHeapStatistics().heap_size_limit}));
});
