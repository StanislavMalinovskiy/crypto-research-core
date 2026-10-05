'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {fragments,map,h}=require('./pumpswap-analysis-adapter-v2.test.cjs');
const filename=path.join(__dirname,'../e2-trade-reducer-v2.cjs'),reducer=fs.existsSync(filename)?require(filename):require('../e2-trade-reducer-v1.cjs');
const cohort='sBxXTU2hTjDd7secesEoLJmMUvWrjNnUGKNoxUt4mG6',WSOL='So11111111111111111111111111111111111111112',PUMP='pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
const policy={version:'real-regression-v2',chain:'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',cohortMints:[cohort],quoteMints:[WSOL],venues:[PUMP]};
function reduce(text){const mapping=map(text);return reducer.reduceTransaction({mapping,raw:h.parse(Buffer.from(text)),policy,lineage:{rawHash:h.digest(Buffer.from(text)),source:'retained-real-regression',knownAt:'UNKNOWN'}});}
for(const [index,base,quote,priority] of [[0,'1217683349498','-764275565','0'],[1,'31826212','-20000','1000']])test('real '+fragments[index].row+' economic cohort BUY is independent of AMM sell orientation',()=>{
 const r=reduce(fragments[index].raw);
 assert.equal(r.actions.length,1,'source-proved owned economic action must exist');const a=r.actions[0];
 assert.equal(a.side,'BUY');assert.equal(a.base.mint,cohort);assert.equal(a.base.rawDelta,base);assert.equal(a.quote.mint,WSOL);assert.equal(a.quote.rawDelta,quote);
 assert.equal(a.legs.length,1);assert.equal(a.legs[0].variant,'sell');assert.equal(a.fees.base,'5000');assert.equal(a.fees.priority,priority);assert.equal(a.fees.splitStatus,'PROVEN');
 assert.equal(a.tips.rawAmount,'UNKNOWN','unobserved tips must not be zero');assert.equal(r.reconstructedCount,1);
});
module.exports={reduce,policy};
// Literal documentary-layout fixtures are imported without registering their
// tests. These are SYNTHETIC_TEST_ONLY compatibility cases, not April evidence.
const vm=require('node:vm'),documentaryPath=path.join(__dirname,'e2-trade-reducer-v1.test.cjs'),documentaryModule={exports:{}},documentaryRequire=require('node:module').createRequire(documentaryPath);
vm.runInNewContext(fs.readFileSync(documentaryPath,'utf8')+'\nmodule.exports={fixture,input};',{require:n=>n==='node:test'?()=>{}:documentaryRequire(n),module:documentaryModule,Buffer,structuredClone,TextDecoder});
function documentary(options={},opposite=false){const f=documentaryModule.exports.fixture(options);
 if(opposite){const swap=x=>x===f.base?f.quote:x===f.quote?f.base:x;f.h.transaction.message.accountKeys=f.h.transaction.message.accountKeys.map(swap);for(const side of ['pre','post'])for(const t of f.h.meta[side+'TokenBalances'])t.mint=swap(t.mint);for(const p of f.policy.protocolFeeAccounts)p.mint=swap(p.mint);}
 const bytes=Buffer.from(JSON.stringify(f.h)),raw=h.parse(bytes),rawHash=h.digest(bytes);return {mapping:map(bytes.toString()),raw,policy:f.policy,lineage:{source:'SYNTHETIC_DOCUMENTARY_COMPATIBILITY_ONLY',rawHash,knownAt:'UNKNOWN'}};
}
module.exports.documentary=documentary;
for(const sell of [false,true])for(const opposite of [false,true])for(const secondLeg of [false,true])test('literal pinned-IDL '+(sell?'sell':'buy')+' preserves '+(opposite?'opposite':'ordinary')+' orientation '+(secondLeg?'multi-leg':'single-leg')+' action without event',()=>{
 const input=documentary({sell,secondLeg},opposite),r=reducer.reduceTransaction(input);
 assert.equal(input.mapping.invocations.length,secondLeg?2:1);assert.equal(input.mapping.invocations[0].event,null);
 assert.equal(r.actions.length,1,'exact pinned-IDL roles and reconciled source transfers must not require an event');const a=r.actions[0];
 assert.equal(a.side,opposite?(sell?'BUY':'SELL'):(sell?'SELL':'BUY'));assert.equal(a.base.rawDelta,opposite?(sell?'10':'-10'):(sell?'-3':'3'));assert.equal(a.quote.rawDelta,opposite?(sell?'-3':'3'):(sell?'10':'-10'));
 assert.equal(a.legs.length,secondLeg?2:1);assert.equal(r.reconstructedCount,1);assert.equal(a.fees.total,'5004');assert.equal(a.fees.priority,'4');
 assert.equal(a.legs[0].layoutApplicability,'PINNED_IDL_EXACT_SCHEMA_OBSERVED_SOURCE_ROLES');assert.equal(a.legs[0].feeEvidence.lp,'UNKNOWN','layout/transfer proof does not invent an event LP fee');
});
for(const [name,mutate]of [
 ['invented layout proof',i=>{i.mapping.invocations[0].layoutProof.idlHash='sha256:'+'0'.repeat(64);}],
 ['wrong exact IDL account count',i=>{i.raw.transaction.message.instructions.at(-1).accounts.push(0);const bytes=Buffer.from(JSON.stringify(i.raw));i.mapping=map(bytes.toString());i.raw=h.parse(bytes);i.lineage.rawHash=h.digest(bytes);}],
 ['fee transfer from wrong actual authority',i=>{i.raw.meta.innerInstructions[0].instructions[1].accounts[2]=i.raw.transaction.message.instructions.at(-1).accounts[0];const bytes=Buffer.from(JSON.stringify(i.raw));i.mapping=map(bytes.toString());i.raw=h.parse(bytes);i.lineage.rawHash=h.digest(bytes);}],
 ['raw vault delta not reconciled',i=>{i.raw.meta.postTokenBalances.find(t=>t.accountIndex===i.raw.transaction.message.instructions.at(-1).accounts[7]).uiTokenAmount.amount='96';const bytes=Buffer.from(JSON.stringify(i.raw));i.mapping=map(bytes.toString());i.raw=h.parse(bytes);i.lineage.rawHash=h.digest(bytes);}]
])test('literal IDL route rejects '+name,()=>{const input=documentary();mutate(input);assert.equal(reducer.reduceTransaction(input).actions.length,0);});
const {decode,encode}=require('./pumpswap-analysis-adapter-v2.test.cjs');
function changed(index,fn){const raw=JSON.parse(fragments[index].raw);fn(raw);return JSON.stringify(raw);}
for(const [name,mutate]of [
 ['missing actual creation',r=>{r.meta.innerInstructions.find(g=>g.index===5).instructions=r.meta.innerInstructions.find(g=>g.index===5).instructions.filter(i=>r.transaction.message.accountKeys[i.programIdIndex]!=='11111111111111111111111111111111');}],
 ['idempotent create without SPL initialization',r=>{r.meta.innerInstructions.find(g=>g.index===5).instructions=r.meta.innerInstructions.find(g=>g.index===5).instructions.filter(i=>decode(i.data)[0]!==18);}],
 ['wrong initialized owner',r=>{const i=r.meta.innerInstructions.find(g=>g.index===5).instructions.find(i=>decode(i.data)[0]===18),b=decode(i.data);b[1]^=1;i.data=encode(b);}],
 ['wrong initialized mint',r=>{r.meta.innerInstructions.find(g=>g.index===5).instructions.find(i=>decode(i.data)[0]===18).accounts[1]=10;}],
 ['missing temporary WSOL closure',r=>{r.transaction.message.instructions[7].data='J';r.transaction.message.instructions[7].accounts=[1];}],
 ['wrong close authority',r=>{r.transaction.message.instructions[7].accounts[2]=17;}],
 ['closure before last use',r=>{r.transaction.message.instructions[3]=r.transaction.message.instructions[7];r.transaction.message.instructions[7]={programIdIndex:8,accounts:[],data:'EvcRSF',stackHeight:1};}],
 ['close followed by recreation',r=>{const i=structuredClone(r.meta.innerInstructions.find(g=>g.index===2).instructions.find(i=>decode(i.data)[0]===18));delete i.stackHeight;r.transaction.message.instructions.push(i);}],
 ['conflicting owned decimals',r=>{r.meta.postTokenBalances.find(t=>t.accountIndex===2).uiTokenAmount.decimals=9;}],
 ['failed transaction',r=>{r.meta.err={InstructionError:[6,{Custom:6004}]};}],
 ['wrong same-invocation event user',r=>{const i=r.meta.innerInstructions.find(g=>g.index===6).instructions.at(-1),b=decode(i.data);b[160]^=1;i.data=encode(b);}],
 ['wrong event quantity',r=>{const i=r.meta.innerInstructions.find(g=>g.index===6).instructions.at(-1),b=decode(i.data);b[24]^=1;i.data=encode(b);}],
 ['unknown event discriminator',r=>{const i=r.meta.innerInstructions.find(g=>g.index===6).instructions.at(-1),b=decode(i.data);b[8]^=1;i.data=encode(b);}],
 ['unassociated event path',r=>{r.meta.innerInstructions.find(g=>g.index===6).instructions.at(-1).stackHeight=3;}],
])test('real-source counterexample fails closed: '+name,()=>{const r=reduce(changed(0,mutate));assert.equal(r.reconstructedCount,0);assert.equal(r.actions.length,0,name);});
for(const [name,mutate]of [
 ['missing explicit price',r=>{r.transaction.message.instructions[1]={programIdIndex:9,accounts:[],data:'Fj2Eoy',stackHeight:1};}],
 ['fee equation mismatch',r=>{r.meta.fee=6001;}],
 ['unsupported transaction version',r=>{r.version=1;}],
 ['signature precompile',r=>{r.transaction.message.accountKeys.push('Ed25519SigVerify111111111111111111111111111');r.transaction.message.instructions.push({programIdIndex:r.transaction.message.accountKeys.length-1,accounts:[],data:'1',stackHeight:1});}],
])test('real-source unproved fee keeps provisional exact action: '+name,()=>{const r=reduce(changed(1,mutate));assert.equal(r.actions.length,1);assert.equal(r.reason,'FEES_UNDETERMINED');assert.equal(r.reconstructedCount,0);assert.equal(r.actions[0].fees.splitStatus,'UNKNOWN');});
test('actual fee payer disagreement is not an actual trader proxy',()=>{const text=changed(1,r=>{const a=r.transaction.message.accountKeys;a.unshift('2zNJorfVfr5ugQ4dfhf1LUCeDTsaqpLuUJfbFK4AVWEt');for(const i of [...r.transaction.message.instructions,...r.meta.innerInstructions.flatMap(g=>g.instructions)]){i.programIdIndex++;i.accounts=i.accounts.map(n=>n+1);}for(const side of ['pre','post'])r.meta[side+'TokenBalances'].forEach(t=>t.accountIndex++);r.meta.preBalances.unshift(100000);r.meta.postBalances.unshift(94000);});const r=reduce(text);assert.equal(r.actions.length,1);assert.equal(r.reason,'FEES_UNDETERMINED');assert.equal(r.status,'AMBIGUOUS_ROUTE');assert.equal(r.actions[0].trader,'AYEdbUCTSzEJc4GzDMJ31kJoipF2pft34ga72FVWzSuj');assert.equal(r.reconstructedCount,0);});
test('explicit priority fee uses ceiling and requested limit rather than consumed units',()=>{const text=changed(1,r=>{const price=Buffer.alloc(9);price[0]=3;price.writeBigUInt64LE(1n,1);const limit=Buffer.alloc(5);limit[0]=2;limit.writeUInt32LE(3,1);r.transaction.message.instructions[0].data=encode(limit);r.transaction.message.instructions[1].data=encode(price);r.meta.fee=5001;r.meta.computeUnitsConsumed=2;});const r=reduce(text);assert.equal(r.reconstructedCount,1);assert.equal(r.actions[0].fees.priority,'1');assert.equal(r.actions[0].fees.proof.requestedLimit,'3');});
test('genuine raw/mapping and derived-state conflicts remain rejected',()=>{const text=fragments[1].raw,m=map(text),raw=h.parse(Buffer.from(text));m.analysisProof.states[0].postAmount='999999';const r=reducer.reduceTransaction({mapping:m,raw,policy,lineage:{rawHash:h.digest(Buffer.from(text))}});assert.equal(r.reason,'DERIVED_SOURCE_CONFLICT');assert.equal(r.reconstructedCount,0);});
