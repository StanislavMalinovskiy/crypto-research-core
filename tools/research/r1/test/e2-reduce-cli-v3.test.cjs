'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {fragments,h}=require('./pumpswap-analysis-adapter-v2.test.cjs'),{policy}=require('./e2-trade-reducer-v2.test.cjs');
const filename=path.join(__dirname,'../e2-reduce-cli-v3.cjs'),cli=fs.existsSync(filename)?require(filename):require('../e2-reduce-cli.cjs');
// Exercise the former source.loadContext route as well as the repaired helper.
// Only Git/filesystem/module metadata is synthetic; no raw or transport exists.
const capturePin={root:'C:/git/crypto-research/worktrees/e2-d1-completion-exec-20261005-r4',commit:'4d0861f1c1e2ef2719714269943c9a05120186a4',sourceHash:'sha256:3a5f71d02c673f366daa9b07173c3fa2d796e06f250d63a0808112c436a07a00',indexHash:'sha256:f2b531c20820239c5f0b0188f90e549800ff86c03848fa1c243867e3e24df85c'};
function captureHarness(change={}){
 const analysis={root:path.resolve(__dirname,'../../../..'),commit:'9ceb0a2a02c489fde1f11c8a01644e34df2166e2',dirty:false},sourceBytes=Buffer.from('source literal'),indexBytes=Buffer.from('index literal'),calls=[];
 const operational={commit:capturePin.commit,dirty:false,scripts:{'e2-cohort-d1-continuation-v2.cjs':capturePin.sourceHash,index:capturePin.indexHash}},context={operational,capture:{source:{commit:'1f8b07d1d2d2fa5e914f25c85bf1aaa25ec3d63a',dirty:false}}};
 const deps={safeDirectory:r=>{calls.push('safe');if(change.unsafe)throw Error('UNSAFE_PATH');return path.resolve(r);},
  git:(root,args)=>{calls.push(args.join(' '));if(args[0]==='rev-parse')return Buffer.from((change.head??capturePin.commit)+'\n');if(args[0]==='status')return Buffer.from(change.dirty?' M file':'');if(args[0]==='cat-file'&&args[1]==='-e'){if(change.absent)throw Error('missing');return Buffer.alloc(0);}if(args[0]==='merge-base'){if(change.unrelated)throw Error('unrelated');return Buffer.alloc(0);}if(args[0]==='cat-file')return change.blob?Buffer.from('wrong blob'):args[2].endsWith('e2-reduce-cli.cjs')?indexBytes:sourceBytes;throw Error('unexpected Git');},
  read:file=>{calls.push('read '+path.basename(file));return change.bytes?Buffer.from('wrong bytes'):file.endsWith('e2-reduce-cli.cjs')?indexBytes:sourceBytes;},
  sha:b=>b.equals(sourceBytes)?capturePin.sourceHash:b.equals(indexBytes)?capturePin.indexHash:'sha256:'+'0'.repeat(64),
  load:file=>{calls.push('require historical');return {loadContext(){calls.push('dependencies');if(change.dependencies)throw Error('LINEAGE_CHANGED');return change.context?{...context,operational:{...operational,commit:analysis.commit}}:context;},verifySnapshot(){return {semanticIntegrity:'PASS',manifestHash:'synthetic sealed head'};}};}};
 const vm=require('node:vm'),mod={exports:{}},script=fs.readFileSync(filename,'utf8')+'\nmodule.exports.testCaptureLoader=typeof loadCaptureContext===\'function\'?loadCaptureContext:()=>({context:source.loadContext(),verifier:source});';
 vm.runInNewContext(script,{module:mod,exports:mod.exports,__filename:filename,__dirname:path.dirname(filename),Buffer,process,require:n=>n==='./e2-cohort-d1-continuation-v2.cjs'?{loadContext:()=>({operational:{...operational,commit:analysis.commit}})}:require(n.startsWith('./')?path.resolve(path.dirname(filename),n):n)});
 return {run:pin=>mod.exports.testCaptureLoader(pin??capturePin,analysis,deps),calls,analysis};
}
test('historical capture context remains valid when current analysis HEAD differs',()=>{
 const t=captureHarness(),loaded=t.run();assert.equal(loaded.context.operational.commit,capturePin.commit,'captured HEAD must not be replaced with current analysis HEAD');assert.notEqual(loaded.context.operational.commit,t.analysis.commit);assert.equal(loaded.verifier.verifySnapshot().semanticIntegrity,'PASS');assert.equal(loaded.captureIdentity.commit,capturePin.commit);assert.equal(t.calls.filter(x=>x==='dependencies').length,1);
});
for(const [name,change]of Object.entries({wrongHead:{head:'a'.repeat(40)},dirty:{dirty:true},unsafe:{unsafe:true},wrongBytes:{bytes:true},wrongBlob:{blob:true},dependencyMismatch:{dependencies:true},wrongContext:{context:true},absentOrigin:{absent:true},unrelatedOrigin:{unrelated:true}}))test('historical capture guard rejects '+name+' before raw',()=>{
 const t=captureHarness(change);assert.throws(()=>t.run(),/LINEAGE_CHANGED|UNSAFE_PATH/,'unproved historical lineage must reject');assert.ok(!t.calls.some(c=>c.endsWith('.raw')));
});
for(const field of ['root','commit','sourceHash','indexHash'])test('historical capture guard rejects wrong configuration '+field,()=>{
 const t=captureHarness(),bad={...capturePin,[field]:field==='root'?capturePin.root+'/other':field==='commit'?'a'.repeat(40):'sha256:'+'0'.repeat(64)};assert.throws(()=>t.run(bad),/LINEAGE_CHANGED/);assert.equal(t.calls.length,0,'config mismatch must reject before filesystem/module/raw access');
});
test('real shared-pool two-owner transaction produces two candidates and trader-matched variant proof with once-TX fee context',()=>{
 const {realTwoTrader}=require('./e2-trade-reducer-v2.test.cjs'),raw=Buffer.from(realTwoTrader),c=cli.reductionConsumer(policy);c.consume(h.parse(raw),raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},12);
 const r=c.finish({state:{selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]}}),candidates=r.crossCheckCandidates.flatMap(g=>g.candidates);
 assert.equal(r.uniqueTransactions,1);assert.equal(r.economicActions,2);assert.equal(r.reconstructed,1);assert.equal(candidates.length,2);assert.equal(new Set(candidates.map(c=>c.actionId)).size,2);
 assert.deepEqual(r.qualifiedVariants.map(p=>p.variant),['buy','sell']);assert.ok(r.qualifiedVariants.every(p=>p.action.legs.some(l=>l.variant===p.variant&&l.declaredTrader===p.action.trader)));
 assert.equal(r.transactionFeeContext.observed,1);assert.equal(r.transactionFeeContext.formulaProven,1);assert.equal(r.transactionFeeContext.attributedPayerActions,1);assert.equal(r.transactionFeeContext.nonpayerUnknownActions,1);assert.equal(r.transactionFeeContext.unknownActionReasons.NONPAYER_COST_ATTRIBUTION_UNPROVED,1);assert.equal(r.transactionFeeContext.formulaUnknown,0);
});
test('versioned CLI consumes the actual opposite-orientation first-five proof',()=>{
 const c=cli.reductionConsumer(policy);const raw=Buffer.from(fragments[1].raw),row=h.parse(raw);c.consume(row,raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},3);
 const r=c.finish({snapshot:{selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]},scope:{records:0,tokens:5}});
 assert.equal(r.reconstructed,1,'real source-backed action and exact explicit fee proof must reach CLI counters');assert.equal(r.qualifiedVariants[0].variant,'sell');assert.equal(r.qualifiedVariants[0].action.side,'BUY');
});
test('current owner-authorized reader does not stop on expired internal v2 time/byte caps',()=>{
 assert.doesNotThrow(()=>cli.budget({scopeBytes:690793891,elapsedMs:100000000,rawReadBytes:200000000000,metadataBytes:8000000},Date.parse('2026-10-06T00:00:00Z')),'expired internal guard cannot replace actual owner/storage boundary');
});
test('source qualification accepts exact trader/legs even when network fee split is unknown',()=>{
 const changed=JSON.parse(fragments[1].raw);changed.meta.fee='6001';const raw=Buffer.from(JSON.stringify(changed)),row=h.parse(raw),c=cli.reductionConsumer(policy);c.consume(row,raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},3);
 const state={selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]},v={state,scope:{tokens:5},manifestHash:require('../e2-cohort-d1-continuation-v2.cjs').OLD_HEAD,semanticIntegrity:'PASS',budgetValidity:'PASS',recordedTiming:{status:'PASS'}},r=c.finish(v);
 assert.equal(r.reconstructed,0);assert.equal(r.qualifiedVariants.length,1);assert.equal(r.qualifiedVariants[0].action.fees.splitStatus,'UNKNOWN');
 const original={manifestHash:v.manifestHash,status:'SEMANTIC_REPLAY_VALID',semanticIntegrity:'PASS',budgetValidity:'PASS',recordedTiming:{status:'PASS'},phase:'QUALIFICATION_PENDING',qualification:{technicalUsable:true}};
 assert.equal(cli.qualification(v,r,original,{w:{qualify:()=>({technicalUsable:true})}}).eligibleForReview,true);
 r.unprovedSupportedVariants=['sell'];assert.equal(cli.qualification(v,r,original,{w:{qualify:()=>({technicalUsable:true})}}).technicalUsable,false);
});
test('CLI is disabled before inspecting configuration or captured evidence',async()=>{
 await assert.rejects(cli.run([]),/DISABLED/);await assert.rejects(cli.run(['--enable','false','--config','missing','--config-hash','missing']),/DISABLED/);
});
test('physical owner boundaries remain enforced independently of old reader caps',()=>{
 const now=Date.parse('2026-10-06T00:00:00Z'),r={retainedBytes:1000000000,freeBytes:40000000000,observedAt:'2026-10-05T00:00:00Z',validUntil:'2026-10-07T00:00:00Z'};
 assert.equal(cli.physical(r,now,0,40000000000),'WITHIN_LIMIT');assert.throws(()=>cli.physical({...r,retainedBytes:49999999999},now,0,40000000000),/DISK_LIMIT/);assert.throws(()=>cli.physical(r,now,0,29999999999),/FREE_SPACE_LIMIT/);
});
test('April-created token trading in August is stratified by admitted UTC trade month',()=>{
 const changed=JSON.parse(fragments[1].raw);changed.blockTime=Date.parse('2026-08-01T00:00:00Z')/1000;const raw=Buffer.from(JSON.stringify(changed)),c=cli.reductionConsumer(policy);c.consume(h.parse(raw),raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},3);
 const r=c.finish({state:{selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]}});assert.equal(r.crossCheckCandidates[0].candidates[0].month,'2026-08','trade month must include July/August, not creation-cohort month');
});
test('actual economic candidates use transaction UTC month, retain cohort month, and exclude failed/non-economic transactions',()=>{
 const c=cli.reductionConsumer(policy),changed=JSON.parse(fragments[1].raw);changed.blockTime=Date.parse('2026-07-31T23:59:59Z')/1000;changed.meta.fee='6001';const raw=Buffer.from(JSON.stringify(changed));c.consume(h.parse(raw),raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},3);
 const failed=JSON.parse(fragments[1].raw);failed.meta.err={InstructionError:[3,{Custom:6004}]};const failedBytes=Buffer.from(JSON.stringify(failed));c.consume(h.parse(failedBytes),failedBytes,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},4);
 const noneconomic=JSON.parse(fragments[1].raw);noneconomic.transaction.message.instructions=noneconomic.transaction.message.instructions.slice(0,2);noneconomic.meta.innerInstructions=[];const noBytes=Buffer.from(JSON.stringify(noneconomic));c.consume(h.parse(noBytes),noBytes,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},5);
 const r=c.finish({state:{selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]}}),candidates=r.crossCheckCandidates.flatMap(g=>g.candidates);
 assert.equal(candidates.length,1,'only proven economic action identities, including unknown-fee actions, are trade candidates');assert.equal(candidates[0].month,'2026-07','UTC blockTime, never tokenIndex, selects trade-month');assert.equal(candidates[0].creationCohortMonth,'april');assert.equal(candidates[0].blockTime,String(changed.blockTime));assert.ok(candidates[0].actionIdentity.trader);assert.equal(candidates[0].venue,'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA');
 assert.equal(r.reconstructed,0);assert.equal(r.economicActions,1);assert.equal(r.uniqueTransactions,3);assert.equal(r.units.counts,'UNIQUE_TRANSACTIONS');assert.equal(r.units.crossCheckCandidates,'PROVEN_TX_TRADER_ECONOMIC_ACTIONS');assert.equal(r.d1CandidateDenominator.status,'NOT_MEASURED');
 assert.equal(r.samplingGaps.failedSwapTransactions,1);assert.equal(r.samplingGaps.nonEconomicTransactions,1);assert.equal(r.crossCheck.fullCandidateUniverse,false);
});
test('RSS engineering guide is advisory rather than a source/data STOP boundary',()=>{
 const memory=process.memoryUsage;try{process.memoryUsage=()=>({rss:1800000000});assert.doesNotThrow(()=>cli.budget({scopeBytes:0,elapsedMs:0,rawReadBytes:0,metadataBytes:0}),'historical RSS guide cannot override owner physical boundary');}finally{process.memoryUsage=memory;}
});
test('bounded first rejection locator identifies raw conflict without changing transaction counts',()=>{
 const c=cli.reductionConsumer(policy),changed=JSON.parse(fragments[1].raw);changed.meta.err={InstructionError:[3,{Custom:6004}]};const raw=Buffer.from(JSON.stringify(changed));
 for(const row of [42,43])c.consume(h.parse(raw),raw,{ordinal:538,tokenIndex:100,rawHash:'sha256:'+'1'.repeat(64)},row);
 const r=c.finish({state:{selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]}});
 assert.equal(r.rejectionLocators?.length,1,'first source-bound locator per reason must be retained, not all rows');
 const l=r.rejectionLocators[0];assert.equal(l.reason,'FAILED_TRANSACTION');assert.equal(l.ordinal,538);assert.equal(l.row,42);assert.equal(l.signature,changed.transaction.signatures[0]);assert.equal(l.transactionRawHash,h.digest(raw));assert.equal(l.pageRawHash,'sha256:'+'1'.repeat(64));assert.ok(l.instructionPaths.length>0);
 assert.equal(r.uniqueTransactions,2);assert.equal(r.reasons.FAILED_TRANSACTION,2);assert.equal(r.crossCheckCandidates.length,0);
});
test('candidate identity ordering is deterministic and unresolved real swap attempt is a sampling gap',()=>{
 const consume=(c,index)=>{const raw=Buffer.from(fragments[index].raw);c.consume(h.parse(raw),raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},index);},finish=c=>c.finish({state:{selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]}});
 const a=cli.reductionConsumer(policy),b=cli.reductionConsumer(policy);consume(a,0);consume(a,1);consume(b,1);consume(b,0);assert.deepEqual(finish(a).crossCheckCandidates,finish(b).crossCheckCandidates);
 const changed=JSON.parse(fragments[1].raw);changed.meta.innerInstructions=[];const raw=Buffer.from(JSON.stringify(changed)),gap=cli.reductionConsumer(policy);gap.consume(h.parse(raw),raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},1);const r=finish(gap);
 assert.equal(r.crossCheckCandidates.length,0);assert.equal(r.samplingGaps.successfulUnresolvedSwapTransactions,1);assert.equal(r.d1CandidateDenominator.status,'NOT_MEASURED');assert.equal(cli.tradeMonth(null),'UNKNOWN');
});
