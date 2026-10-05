'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {fragments,h}=require('./pumpswap-analysis-adapter-v2.test.cjs'),{policy}=require('./e2-trade-reducer-v2.test.cjs');
const filename=path.join(__dirname,'../e2-reduce-cli-v3.cjs'),cli=fs.existsSync(filename)?require(filename):require('../e2-reduce-cli.cjs');
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
test('candidate identity ordering is deterministic and unresolved real swap attempt is a sampling gap',()=>{
 const consume=(c,index)=>{const raw=Buffer.from(fragments[index].raw);c.consume(h.parse(raw),raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},index);},finish=c=>c.finish({state:{selection:{tokens:[{mint:policy.cohortMints[0]}]},records:[]}});
 const a=cli.reductionConsumer(policy),b=cli.reductionConsumer(policy);consume(a,0);consume(a,1);consume(b,1);consume(b,0);assert.deepEqual(finish(a).crossCheckCandidates,finish(b).crossCheckCandidates);
 const changed=JSON.parse(fragments[1].raw);changed.meta.innerInstructions=[];const raw=Buffer.from(JSON.stringify(changed)),gap=cli.reductionConsumer(policy);gap.consume(h.parse(raw),raw,{ordinal:1,tokenIndex:0,rawHash:'sha256:'+'1'.repeat(64)},1);const r=finish(gap);
 assert.equal(r.crossCheckCandidates.length,0);assert.equal(r.samplingGaps.successfulUnresolvedSwapTransactions,1);assert.equal(r.d1CandidateDenominator.status,'NOT_MEASURED');assert.equal(cli.tradeMonth(null),'UNKNOWN');
});
