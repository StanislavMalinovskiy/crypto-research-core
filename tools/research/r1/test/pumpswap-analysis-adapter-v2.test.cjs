'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const h=require('../exploratory-probe.cjs');
const fixturePath=path.join(__dirname,'pumpswap-analysis-adapter-v1.test.cjs'),fixtureModule={exports:{}},local=require('node:module').createRequire(fixturePath);
// Reuse the existing immutable, hash-pinned actual source fragments. Suppress
// registration of that file's tests; do not copy a provider export into Git.
vm.runInNewContext(fs.readFileSync(fixturePath,'utf8'),{require:n=>n==='node:test'?()=>{}:local(n),module:fixtureModule,__dirname,Buffer,structuredClone,TextDecoder});
const fragments=fixtureModule.exports.fragments;
const filename=path.join(__dirname,'../pumpswap-analysis-adapter-v2.cjs');
const adapter=fs.existsSync(filename)?require(filename):require('../pumpswap-analysis-adapter-v1.cjs');
const map=raw=>{const bytes=Buffer.from(raw);return adapter.mapHeliusTransaction(bytes,h.digest(bytes));};
const alphabet='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function decode(s){let n=0n,z=0;for(const c of s){const i=alphabet.indexOf(c);assert.ok(i>=0);n=n*58n+BigInt(i);}while(s[z]==='1')z++;let hex=n.toString(16);return Buffer.concat([Buffer.alloc(z),n?Buffer.from(hex.padStart(Math.ceil(hex.length/2)*2,'0'),'hex'):Buffer.alloc(0)]);}
function encode(b){let n=BigInt('0x'+b.toString('hex')),s='';while(n){s=alphabet[Number(n%58n)]+s;n/=58n;}for(const c of b){if(c)break;s='1'+s;}return s;}
test('real 3myenam created cohort ATA has separately proved zero pre-state',()=>{
 const f=fragments[0];assert.equal(h.digest(Buffer.from(f.raw)),f.hash);const m=map(f.raw),account='2njojjBjHAetokNyPpZ8Ye58vBP4tj4NXhoEjqBTQqQQ';
 assert.equal(m.tokenStates.find(t=>t.account===account).preAmount,null,'accepted raw row remains absent');
 const s=m.analysisProof?.states.find(t=>t.account===account);
 assert.equal(s?.preAmount,'0','actual successful same-account creation must establish zero pre-state');
 assert.equal(s.postAmount,'1217683349498');assert.equal(s.preOwner,'2zNJorfVfr5ugQ4dfhf1LUCeDTsaqpLuUJfbFK4AVWEt');
 assert.ok(s.lifecycle.creationPaths.length>0);assert.equal(m.rawHash,f.hash);
});
test('real 4Tn same-invocation event proves the observed 22-account sell layout',()=>{
 const f=fragments[1];assert.equal(h.digest(Buffer.from(f.raw)),f.hash);const m=map(f.raw);
 assert.equal(m.invocations.length,1,'proved self event is not another economic swap');
 assert.equal(m.invocations[0].reason,null,'actual matching event and exact flows corroborate this layout');
 assert.equal(m.invocations[0].layoutApplicability,'OBSERVED_SAME_INVOCATION_EVENT');
 assert.equal(m.invocations[0].event.protocolFee,'15961');assert.equal(m.invocations[0].event.lpFee,'79805');
});
module.exports={fragments,map,decode,encode,h};
test('synthetic existing owned account with actual matching final CloseAccount derives post zero',()=>{
 const raw=h.parse(Buffer.from(fragments[0].raw)),ctx=adapter.context(raw),account=raw.transaction.message.accountKeys[1],owner=raw.transaction.message.accountKeys[0];
 // A separate setup: account already existed, so remove its create/init records.
 // The exact accepted close instruction and transfer paths remain the oracle.
 ctx.records=ctx.records.filter(r=>!(r.program==='11111111111111111111111111111111'&&r.accounts[1]===account&&r.data.length===52)&&!(r.program==='TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA'&&r.accounts[0]===account&&[1,16,18].includes(r.data[0])));
 const states=adapter.deriveLifecycle(ctx,[{signature:raw.transaction.signatures[0],account,preMint:'So11111111111111111111111111111111111111112',preOwner:owner,preAmount:'764275565',preDecimals:9,postMint:null,postOwner:null,postAmount:null,postDecimals:null}],[]);
 assert.equal(states[0].postAmount,'0','matching observed final close must establish zero post-state');assert.equal(states[0].preAmount,'764275565');
});
