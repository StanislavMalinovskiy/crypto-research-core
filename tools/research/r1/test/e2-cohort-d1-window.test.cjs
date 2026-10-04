'use strict';
require('node:test')('execution lineage pins the actual V2 module, keeping historical test bytes unchanged', () => {
  const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), assert = require('node:assert/strict');
  const w = require('../e2-cohort-d1-window.cjs'), actual = w.lineage();
  const hash = name => 'sha256:' + crypto.createHash('sha256').update(fs.readFileSync(path.join(__dirname, name))).digest('hex');
  assert.equal(actual.scripts['pumpswap-mapper-v2.cjs'], hash('../pumpswap-mapper-v2.cjs'));
  assert.equal(actual.scripts['pumpswap-mapper.cjs'], undefined);
  assert.equal(hash('pumpswap-mapper.test.cjs'), 'sha256:2a6c3a14e28ffc501b8b2a149d941b6b3c8a4447b673c55f929e07cbc2de9621');
});
testLegacyPinDeferred();
function testLegacyPinDeferred() {
  const test = require('node:test'), assert = require('node:assert/strict');
  test('versioned placement preserves the immutable historical mapper bytes', () => {
    const fs = require('node:fs'), crypto = require('node:crypto'), path = require('node:path');
    const actual = crypto.createHash('sha256').update(fs.readFileSync(path.join(__dirname, '../pumpswap-mapper.cjs'))).digest('hex');
    assert.equal(actual, '161783107d93225c3a658c4340c13e8db6a5476b96e13e489c920b8730269a1b');
  });
}
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs');
// Before implementation exercise the existing live history policy, not a missing-module failure.
const target = fs.existsSync(require('node:path').join(__dirname, '../e2-cohort-d1-window.cjs'))
  ? require('../e2-cohort-d1-window.cjs') : require('../helius-probe.cjs');
test('D1 history query uses full actual transactions and the whole permitted preholdout range', () => {
  const q = target.query('11111111111111111111111111111111', null), p = q.body.params[1];
  assert.equal(p.transactionDetails, 'full');
  assert.deepEqual(p.filters.blockTime, { gte: 1775001600, lt: 1788134400 });
  assert.equal(p.encoding, 'json'); assert.equal(p.maxSupportedTransactionVersion, 1);
  assert.equal(p.commitment, 'finalized'); assert.equal(p.sortOrder, 'asc'); assert.equal(p.limit, 1000);
  assert.equal(p.filters.status, 'any'); assert.equal(p.filters.tokenAccounts, 'none');
});
test('holdout data are rejected as a counted unsafe page before admission', () => {
  const raw = Buffer.from(JSON.stringify({ jsonrpc: '2.0', id: 1, result: { data: [{ signature: '1'.repeat(64), slot: 1, transactionIndex: 0, blockTime: 1788134400, confirmationStatus: 'finalized' }], paginationToken: null } }));
  const r = target.admit(raw, target.query('11111111111111111111111111111111', null));
  assert.equal(r.code, 'FORBIDDEN_TIME'); assert.deepEqual(r.rows, []);
});
const w = require('../e2-cohort-d1-window.cjs'), census = require('../e2-offline-mint-count.cjs');
const { digest } = require('../exploratory-probe.cjs');
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function b58(bytes) { let n = BigInt('0x'+bytes.toString('hex')), s = ''; while(n) { s = alphabet[Number(n%58n)]+s; n/=58n; } for(const b of bytes) { if(b) break; s='1'+s; } return s; }
const address = n => { const b = Buffer.alloc(32); b.writeUInt32BE(n,28); return b58(b); };
const sig = n => { const b = Buffer.alloc(64); b.writeUInt32BE(n,60); return b58(b); };
function row(n, month) { return { status:'OBSERVED_DECLARED_CREATE_POOL',layoutApplicability:'UNVERIFIED',slot:census.ROOTS[month].start,
  timestamp:census.DATES[month],transactionIndex:n, instructionPath:[0],cpiDepth:0,accountCount:18,dataLength:59,lineOrdinal:0,
  rawHash:digest(Buffer.from('synthetic-census')),signature:sig(n),pool:address(100000+n),globalConfig:address(2),creator:address(3),baseMint:address(n),quoteMint:census.QUOTES[0] }; }
function selection(reverse=false) { const c = w.collector(), rows=[]; for(let m=0;m<3;m++) for(let i=0;i<101;i++) rows.push([row(1000+m*1000+i,m),m]);
  if(reverse) rows.reverse(); for(const [r,m] of rows) c.add(r,m); return c.finish([]); }
const SELECTED = selection();
const lineage = { runtime:'v24.19.0',source:{commit:'a'.repeat(40),dirty:false},scripts:{runner:digest(Buffer.from('synthetic-lineage'))} };
const at = w.START+1000;
function attestation() { return { plan:'Free',entitlement:'VERIFIED',historyCredits:100,tariffEvidenceHash:digest(Buffer.from('synthetic-tariff')),
  creditsReservedBefore:0,ancillaryStartsBefore:0,publicStartsBefore:0,receivedBefore:0,
  poolIndexCoverage:'DOCUMENTED_INDEXED_POOL_ADDRESSES',poolIndexEvidenceHash:digest(Buffer.from('synthetic-index-documentation')),
  retainedBytes:5000000000,freeBytes:100000000000,observedAt:new Date(at-1000).toISOString(),validUntil:new Date(w.STOP).toISOString(),
  expiry:new Date(w.STOP+86400000).toISOString(),freeze:{protocolHash:digest(Buffer.from('synthetic-protocol')),commit:'b'.repeat(40)} }; }
function memory() { const files = new Map(); let created=false;
  return { files,create(){ assert.equal(created,false,'exclusive root'); created=true; },lock(){ this.write('lock',Buffer.from('lock')); },unlock(){ files.delete('lock'); },
    write(name,bytes,replace=false){ if(!replace) assert.equal(files.has(name),false,'exclusive file'); files.set(name,Buffer.from(bytes)); },
    read(name){ assert.ok(files.has(name),name); return Buffer.from(files.get(name)); },
    publish(s){ files.set('manifest.json',Buffer.from(JSON.stringify(w.seal('manifest',s))+'\n')); },
    list(){ return [...files.keys()]; },size(){ return [...files].filter(([n])=>n!=='lock').reduce((n,[,b])=>n+b.length,0); },free(){ return 100000000000; } }; }
const emptyPage = cursor => Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:[],paginationToken:cursor}}));
test('resource index capacity executes the actual replay signature retention',async t=>{
  if(process.env.WINDOW_RESOURCE_CHILD !== 'index')return;
  const io=memory();let clock=at;
  const deps={store:io,lineage,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>({code:null,status:200,received:emptyPage(null).length,bytes:emptyPage(null)})};
  const first=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);
  const index=w.replay(io,first.manifestHash,{lineage,now:()=>clock}).signatures;
  const start=performance.now(),prefix=b58(Buffer.alloc(64,1)).slice(0,-8);let peak=0,count=0;
  for(;count<4200000;count++) {
    let n=count,s='';do{s=alphabet[n%58]+s;n=Math.floor(n/58);}while(n);const signature=prefix+s.padStart(8,'1');
    index.set(signature,digest(Buffer.from(String(count))));
    if(count%10000===0){const m=process.memoryUsage();peak=Math.max(peak,m.rss);if(m.heapUsed>750000000||m.rss>1750000000)break;}
  }
  t.diagnostic(JSON.stringify({count,peakRss:peak,elapsedMs:performance.now()-start,heap:process.memoryUsage().heapUsed}));
  assert.equal(count,4200000,'actual replay index must fit the complete cumulative signature envelope within bounded memory');
  assert.ok(performance.now()-start<300000);assert.ok(peak<=1750000000);
});
test('resource combined selection and full 4.2M replay envelope',t=>{
  if(process.env.WINDOW_RESOURCE_CHILD!=='combined')return;
  const began=performance.now();let peak=0;
  const measure=()=>{w.resourceCheck(began);peak=Math.max(peak,process.memoryUsage().rss);};
  {
    const c=w.collector(),count=new census.Reducer();let inputBytes=0;
    const left=new census.Lines(b=>c.add(census.parsedRow(b),JSON.parse(b).rootIndex));
    const right=new census.Lines(b=>{const r=census.parsedRow(b);count.add(r,Number(r.rootIndex));});
    for(let n=0;n<200000;n++){
      const m=n%3,r={...row(1000000+n,m),rootIndex:m},bytes=Buffer.from(JSON.stringify(r)+'\n');inputBytes+=bytes.length;
      left.feed(bytes);right.feed(bytes);if(n%1000===0)measure();
    }
    left.finish();right.finish();assert.equal(count.rows,200000);assert.ok(inputBytes<=200000000);
    const selected=c.finish(Array.from({length:15},(_,i)=>({bytes:Math.floor(inputBytes/15)+(i<inputBytes%15?1:0),hash:digest(Buffer.from('synthetic-input-'+i))})));
    assert.equal(selected.tokens.length,300);assert.equal(selected.censusCounts.inputRows,200000);measure();
    t.diagnostic(JSON.stringify({stage:'combined-two-reducer-selection',rows:200000,inputBytes,elapsedMs:performance.now()-began,peakRss:peak}));
  }
  global.gc?.();
  // One nearly 64MB response goes through actual framing, lossless parse, admission
  // and per-transaction mapper together, rather than a raw allocation-only stress.
  const nearRows=Array.from({length:1000},(_,i)=>({slot:410195947,transactionIndex:i,blockTime:1775001600,transaction:{signatures:[sig(i+1)]},padding:'x'.repeat(63500)}));
  let near=Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:nearRows,paginationToken:null}}));
  assert.ok(near.length>63000000&&near.length<=w.LIMITS.response);const nearBytes=near.length;
  const admitted=w.admit(near,w.query(address(123)));assert.equal(admitted.code,null);assert.equal(w.mappingCounts(admitted.rows,admitted.rowBytes,measure).transactions,1000);measure();
  near=null;nearRows.length=0;admitted.rows.length=0;admitted.rowBytes.length=0;global.gc?.();
  const prefix=b58(Buffer.alloc(64,1)).slice(0,-8);
  const signature=n=>{let s='';do{s=alphabet[n%58]+s;n=Math.floor(n/58);}while(n);return prefix+s.padStart(8,'1');};
  const page=i=>Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:Array.from({length:1000},(_,n)=>({slot:410195947,transactionIndex:(i%14)*1000+n,
    blockTime:1775001600,transaction:{signatures:[signature(i*1000+n)]}})),paginationToken:i%14===13?null:'410195947:'+((i%14+1)*1000-1)}}));
  const s=w.initial(SELECTED,attestation(),lineage),names=['manifest.json','selection.json'];let retained=Buffer.byteLength(JSON.stringify(SELECTED)+'\n');
  const mapping=w.mappingCounts(w.admit(page(0),w.query(address(123))).rows);
  for(let i=0;i<4200;i++){
    const token=Math.floor(i/14),q=w.query(SELECTED.tokens[token].addresses[0],s.tokens[token].cursor),raw=page(i),start=at+i*250;
    const r={ordinal:i+1,tokenIndex:token,addressIndex:0,query:q,creditsReserved:100,actualCredits:null,reservedAt:start,start,transportStarted:true,
      end:start+1,received:raw.length,code:null,status:200,responseHash:digest(raw),rawHash:digest(raw),rawBytes:raw.length,mapping,equalScopedRows:0,
      returnedRows:1000,cursor:i%14===13?null:'410195947:'+((i%14+1)*1000-1),outcome:'RETAINED'};
    w.reserve(s,token);w.applyPage(s,token,{code:null,returnedRows:1000,paginationToken:r.cursor});s.records.push(r);s.received+=raw.length;s.actualHistoryStarts++;
    const name=String(i+1).padStart(5,'0');names.push(name+'.raw',name+'.start.json',name+'.receipt.json');
    const initial={ordinal:r.ordinal,tokenIndex:token,addressIndex:0,query:q,creditsReserved:100,actualCredits:null,reservedAt:start,start:null,outcome:'START_RESERVED'};
    retained+=raw.length+Buffer.byteLength(JSON.stringify(initial)+'\n')+Buffer.byteLength(JSON.stringify(r)+'\n');if(i%20===0)measure();
  }
  s.phase='SOURCE_COMPLETE';s.lastStart=at+4199*250;
  const io={list:()=>names,size:()=>retained+Buffer.byteLength(JSON.stringify(w.seal('manifest',s))+'\n'),read(name){
    if(name==='manifest.json')return Buffer.from(JSON.stringify(w.seal('manifest',s))+'\n');if(name==='selection.json')return Buffer.from(JSON.stringify(SELECTED)+'\n');
    const i=Number(name.slice(0,5))-1,r=s.records[i];if(name.endsWith('.raw'))return page(i);
    if(name.endsWith('.receipt.json'))return Buffer.from(JSON.stringify(r)+'\n');
    return Buffer.from(JSON.stringify({ordinal:r.ordinal,tokenIndex:r.tokenIndex,addressIndex:0,query:r.query,creditsReserved:100,actualCredits:null,reservedAt:r.reservedAt,start:null,outcome:'START_RESERVED'})+'\n');
  }};
  for(let i=0;i<4;i++)s.retained=io.size();const result=w.replay(io,w.seal('manifest',s).hash,{lineage,now:()=>at});measure();
  assert.equal(result.uniqueTransactions,4200000);assert.equal(result.rawFiles,4200);assert.equal(result.creditsReserved,420000);
  assert.equal(result.recordedTiming.status,'PASS');assert.equal(result.budgetValidity,'PASS');assert.equal(result.verdict.history.complete,300);
  t.diagnostic(JSON.stringify({stage:'full-generated-replay',rows:4200000,pages:4200,nearResponseBytes:nearBytes,receivedBytes:result.received,elapsedMs:performance.now()-began,peakRss:peak,maxRss:process.resourceUsage().maxRSS*1024}));
});

test('repair actual transport start reserves the full request and publication interval',async()=>{
  const io=memory();let calls=0,late=false;
  const original=io.publish;io.publish=s=>{original(s);if(s.records.length)late=true;};let reads=0;
  const now=()=>late&&++reads>=2?w.STOP:at;
  const r=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},
    {store:io,lineage,now,transport:async()=>{calls++;return{code:'HTTP_ERROR',status:503,received:0};}});
  assert.equal(calls,0);assert.equal(r.code,'TIME_LIMIT');assert.equal(r.creditsReserved,100);
});
test('repair rejects a stale leading continuation row even when the tail advances',()=>{
  const tx=n=>({slot:410195947,transactionIndex:n,blockTime:1775001600,transaction:{signatures:[sig(n)]}});
  for(const cursor of ['410195947:11',null]) {
    const raw=Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:[tx(1),tx(11)],paginationToken:cursor}}));
    const r=w.admit(raw,w.query(address(123),'410195947:10'));
    assert.equal(r.code,'CURSOR_INVALID');assert.deepEqual(r.rows,[]);
  }
});
test('repair bounded worker rejects excess heap before any source or input access',()=>{
  const {spawnSync}=require('node:child_process'),path=require('node:path');
  const child=spawnSync(process.execPath,['--max-old-space-size=2048',path.join(__dirname,'../e2-cohort-d1-window.cjs'),'--bounded-worker','--replay','--head','unused'],{timeout:5000,encoding:'utf8',windowsHide:true});
  assert.equal(child.status,1);assert.equal(JSON.parse(child.stdout).code,'RESOURCE_LIMIT');
  assert.throws(()=>w.resourceCheck(performance.now()-300001),/TIME_LIMIT/);
});
test('configured supervised CLI reaches argument validation within the actual host heap envelope',()=>{
  const {spawnSync}=require('node:child_process'),path=require('node:path');
  const child=spawnSync(process.execPath,[path.join(__dirname,'../e2-cohort-d1-window.cjs'),'--unsupported-offline-command'],{timeout:5000,encoding:'utf8',windowsHide:true});
  assert.equal(child.status,1);const result=JSON.parse(child.stdout);
  assert.equal(result.code,'ARGUMENTS_INVALID');assert.equal(result.d1Passed,false);
  const measured=spawnSync(process.execPath,['--max-old-space-size=1024','-e','console.log(require("node:v8").getHeapStatistics().heap_size_limit)'],{timeout:5000,encoding:'utf8',windowsHide:true});
  assert.equal(measured.status,0);assert.ok(Number(measured.stdout)<=1275068416);
});
test('repair replay reports recorded timing separately from semantic validity',async()=>{
  const io=memory();let clock=at;
  const deps={store:io,lineage,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>({code:null,status:200,received:emptyPage(null).length,bytes:emptyPage(null)})};
  const first=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);
  const m=JSON.parse(io.read('manifest.json')),s=w.verify('manifest',m);s.records[4].end=w.STOP+1;
  io.files.set('00005.receipt.json',Buffer.from(JSON.stringify(s.records[4])+'\n'));
  for(let i=0;i<4;i++){io.publish(s);s.retained=io.size();}io.publish(s);
  const r=w.replay(io,w.seal('manifest',s).hash,{lineage,now:()=>clock});
  assert.equal(r.semanticIntegrity,'PASS');assert.equal(r.recordedTiming.status,'FAIL');assert.equal(r.budgetValidity,'PASS');
});
test('repair external ancillary bytes cannot disappear from a durable reservation',async()=>{
  const io=memory();let clock=at;
  const deps={store:io,lineage,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>({code:null,status:200,received:emptyPage(null).length,bytes:emptyPage(null)})};
  const first=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);
  const prior=w.replay(io,first.manifestHash,{lineage,now:()=>clock}),a=attestation();a.creditsReservedBefore=500;a.receivedBefore=prior.received;
  const receipt={ordinal:1,queryHash:digest(Buffer.from('synthetic-external-query')),start:at+2000,end:at+2100,actualStarts:1,publicStarts:1,received:100,status:200,rawHash:digest(Buffer.alloc(100)),actualCredits:null};
  const reserved=w.reserveAncillary({enabled:true,head:first.manifestHash,attestation:a,credits:1,publicStarts:1,queryHash:receipt.queryHash,purpose:'independent-preholdout-receipt'},deps);
  clock=at+3000;const after={...a,creditsReservedBefore:501,ancillaryStartsBefore:1};
  const reconciled=w.reconcileAncillary({enabled:true,head:reserved.manifestHash,attestation:after,ordinal:1,receiptBytes:Buffer.from(JSON.stringify(receipt))},deps);
  assert.equal(reconciled.code,null);
  const replay=w.replay(io,reconciled.manifestHash,{lineage,now:()=>clock});
  assert.equal(replay.received,prior.received+100);assert.equal(replay.state.publicStarts,1);assert.equal(replay.creditsReserved,501);
  assert.equal(replay.verdict.counters.ancillaryStartsActual,1);assert.equal(replay.actualCredits,null);
  const current={...after,receivedBefore:440,publicStartsBefore:1};
  const again=w.reconcileAncillary({enabled:true,head:reconciled.manifestHash,attestation:current,ordinal:1,receiptBytes:Buffer.from(JSON.stringify(receipt))},deps);
  assert.equal(again.manifestHash,reconciled.manifestHash);assert.equal(again.received,440);assert.equal(again.creditsReserved,501);
  const changed={...receipt,received:101};
  assert.equal(w.reconcileAncillary({enabled:true,head:again.manifestHash,attestation:current,ordinal:1,receiptBytes:Buffer.from(JSON.stringify(changed))},deps).code,'IMMUTABLE_CONFLICT');
  assert.equal(w.reconcileAncillary({enabled:true,head:again.manifestHash,attestation:current,ordinal:1,receiptBytes:Buffer.from(JSON.stringify({...receipt,url:'secret-bearing-url'}))},deps).code,'ARGUMENTS_INVALID');
  const q={status:'APPROVE',manifestHash:again.manifestHash,technicalUsable:true,reviewHash:digest(Buffer.from('synthetic-reviewed-reconciliation'))};
  const continued=await w.runStage({enabled:true,phase:'continue',head:again.manifestHash,attestation:current,qualification:q},deps);
  assert.equal(continued.code,null);assert.equal(continued.creditsReserved,30001);
  const final=w.replay(io,continued.manifestHash,{lineage,now:()=>clock});assert.equal(final.received,300*emptyPage(null).length+100);assert.equal(final.state.publicStarts,1);
});
test('fixed cohort has 100 per actual UTC month, raw32 digest and no iteration dependence',()=>{
  assert.equal(SELECTED.tokens.length,300); assert.deepEqual(selection(true).tokens,SELECTED.tokens);
  assert.deepEqual(SELECTED.tokens.map(t=>t.month).filter((m,i,a)=>!i||m!==a[i-1]),['april','may','june']);
  const actualOrder = Array.from({length:101},(_,i)=>address(1000+i)).sort((a,b)=>Buffer.compare(Buffer.from(digest(census.decode(a,32)).slice(7),'hex'),Buffer.from(digest(census.decode(b,32)).slice(7),'hex')));
  assert.deepEqual(SELECTED.tokens.slice(0,100).map(t=>t.mint),actualOrder.slice(0,100));
  assert.equal(SELECTED.cohortAdmitted,false); assert.equal(SELECTED.limitations.knownAt,'UNKNOWN');
});
test('June28 cutoff, insufficient strata, unsupported census shapes and conflicting pool facts fail closed',()=>{
  const c=w.collector(), june=row(9000,2); june.timestamp=1782604800; assert.throws(()=>c.add(june,2),/PROVENANCE_INVALID/);
  const d=w.collector(); assert.throws(()=>d.finish([]),/INSUFFICIENT_STRATA/);
  const x=row(7000,0); x.accountCount=19; d.add(x,0); assert.throws(()=>d.finish([]),/INSUFFICIENT_STRATA/);
  const e=w.collector(); e.add(row(7001,0),0); const changed=row(7001,0); changed.baseMint=address(8000); assert.throws(()=>e.add(changed,0),/LOCATOR_CONFLICT/);
});
test('different indexed pool addresses share one token cap and first-five charges cannot reset',()=>{
  const s=w.initial(SELECTED,attestation(),lineage); for(let n=0;n<14;n++) w.reserve(s,0);
  assert.equal(s.tokens[0].pages,14); assert.equal(s.tokens[0].credits,1400); assert.equal(s.creditsReserved,1400);
  s.phase='CONTINUING'; s.tokens[0].addressIndex=1; assert.throws(()=>w.reserve(s,0),/TOKEN_CAP/);
  assert.equal(s.creditsReserved,1400); assert.equal(s.historyStarts,14);
});
test('whole window 450000 credits includes bounded ancillary and failed-start reservations',()=>{
  const s=w.initial(SELECTED,attestation(),lineage); s.creditsReserved=449900; w.reserve(s,0);
  assert.equal(s.creditsReserved,450000); assert.throws(()=>w.reserve(s,1),/CREDIT_LIMIT/);
  const t=w.initial(SELECTED,attestation(),lineage); t.ancillaryStarts=30000; assert.throws(()=>w.reserve(t,0,'ancillary',1),/ANCILLARY_CAP/);
  assert.throws(()=>w.reserve(t,0,'history',99),/TARIFF_INVALID/);
  assert.throws(()=>w.reserve(t,0,'ancillary',0),/TARIFF_INVALID/);
});
test('missing Free tariff, unknown disk, expiry and publication reservation block source',()=>{
  for(const [mutate,code] of [[a=>delete a.historyCredits,'TARIFF_INVALID'],[a=>a.plan='Paid','TARIFF_INVALID'],[a=>delete a.retainedBytes,'PREFLIGHT_INVALID'],
    [a=>a.expiry=new Date(at).toISOString(),'EXPIRY'],[a=>a.freeBytes=30000000000,'FREE_SPACE_LIMIT'],[a=>a.retainedBytes=49999999999,'DISK_LIMIT']]) {
    const a=attestation(); mutate(a); assert.throws(()=>w.capacity(a,at),new RegExp(code));
  }
  assert.throws(()=>w.capacity(attestation(),w.STOP-w.LIMITS.publication),/TIME_LIMIT/);
});
test('complete zero history is valid and nonterminal page14 is censored without activity rejection',()=>{
  const s=w.initial(SELECTED,attestation(),lineage), a=w.admit(emptyPage(null),w.query(SELECTED.tokens[0].addresses[0]));
  assert.equal(a.code,null); w.reserve(s,0); w.applyPage(s,0,a); assert.equal(s.tokens[0].status,'COMPLETE'); assert.equal(s.tokens[0].rows,0);
  const t=w.initial(SELECTED,attestation(),lineage); for(let i=0;i<14;i++) w.reserve(t,0);
  w.applyPage(t,0,{code:null,returnedRows:1000,paginationToken:'410195947:1'}); assert.equal(t.tokens[0].status,'INCOMPLETE');
});
test('actual first-five zero histories qualify for review without a made-up activity threshold and replay uses no transport',async()=>{
  const io=memory(); let calls=0,clock=at;
  const deps={store:io,lineage,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>{calls++;const bytes=emptyPage(null);return{code:null,status:200,received:bytes.length,bytes};}};
  const r=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);
  assert.equal(r.code,null); assert.equal(calls,5); assert.equal(r.creditsReserved,500); assert.equal(r.actualCredits,null);
  assert.equal(r.qualification.technicalUsable,true); assert.equal(r.qualification.status,'REVIEW_REQUIRED');
  const before=[...io.files].map(([n,b])=>[n,digest(b)]); const replay=w.replay(io,r.manifestHash,{lineage,now:()=>clock});
  assert.equal(replay.creditsReserved,500); assert.equal(replay.rawFiles,5); assert.equal(replay.verdict.status,'INSUFFICIENT');
  assert.deepEqual([...io.files].map(([n,b])=>[n,digest(b)]),before); assert.equal(calls,5);
  const key=[...io.files.keys()].find(n=>n.endsWith('.raw')); io.files.set(key,emptyPage('0:0')); assert.throws(()=>w.replay(io,r.manifestHash,{lineage,now:()=>clock}),/INTEGRITY_ERROR/);
});
test('reviewed continuation reuses all retained first-five pages and one cumulative ledger',async()=>{
  const io=memory();let calls=0,clock=at;
  const deps={store:io,lineage,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>{calls++;const bytes=emptyPage(null);return{code:null,status:200,received:bytes.length,bytes};}};
  const r=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);
  const q={status:'APPROVE',manifestHash:r.manifestHash,technicalUsable:true,reviewHash:digest(Buffer.from('synthetic-independent-review'))};
  const a=attestation(); const prior=w.replay(io,r.manifestHash,{lineage,now:()=>clock});a.creditsReservedBefore=prior.state.creditsReserved;a.receivedBefore=prior.state.received;
  const next=await w.runStage({enabled:true,phase:'continue',head:r.manifestHash,attestation:a,qualification:q},deps);
  assert.equal(next.code,null);assert.equal(calls,300);assert.equal(next.creditsReserved,30000);
  const replay=w.replay(io,next.manifestHash,{lineage,now:()=>clock});assert.equal(replay.verdict.history.complete,300);
  assert.equal(replay.state.historyStarts,300);assert.equal(replay.state.actualHistoryStarts,300);
});
test('time is rechecked after capacity work and source failures never retry',async()=>{
  const io=memory();let calls=0,clock=at;const orig=io.free;
  io.free=()=>{ clock=w.STOP;return orig(); };
  const deps={store:io,lineage,now:()=>clock,transport:async()=>{calls++;assert.fail('forbidden source');}};
  const r=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);assert.equal(r.code,'TIME_LIMIT');assert.equal(calls,0);
  const store=memory();clock=at;const x=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},
    {store,lineage,now:()=>clock,transport:async()=>{calls++;return{code:'HTTP_ERROR',status:503,received:3};}});
  assert.equal(x.code,'HTTP_ERROR');assert.equal(calls,1);assert.equal(x.creditsReserved,100);
});
test('200 aggregate checks require actual venue/month strata, 95 percent and zero double volume',()=>{
  const checks=Array.from({length:200},(_,i)=>({signature:sig(i+1),instructionPath:[0],venue:i%2?'PumpSwap':'OtherVenue',month:['april','may','june'][i%3],
    rawHash:digest(Buffer.from('synthetic-raw-'+i)),independentReceiptHash:digest(Buffer.from('synthetic-independent-'+i)),agrees:i<190,doubleVolume:false}));
  const strata=['PumpSwap/april','PumpSwap/may','PumpSwap/june','OtherVenue/april','OtherVenue/may','OtherVenue/june'];
  assert.equal(w.crossCheck(checks,strata).status,'PASS');assert.equal(w.crossCheck(checks.slice(0,199),strata).status,'UNMET');
  checks[0].agrees=false;assert.equal(w.crossCheck(checks,strata).status,'UNMET');checks[0].agrees=true;checks[0].doubleVolume=true;
  assert.equal(w.crossCheck(checks,strata).status,'UNMET');assert.equal(w.crossCheck(checks,['AbsentVenue/april']).missingStrata.length,1);
});
test('download counters or a cross-check PASS cannot fill whole-envelope D1 omissions',()=>{
  const s=w.initial(SELECTED,attestation(),lineage);s.crossCheck={status:'PASS',checked:200,agree:200,doubledVolume:0};
  const r=w.summary(s);assert.equal(r.status,'INSUFFICIENT');assert.equal(r.d1Passed,false);assert.equal(r.deployment,'UNVERIFIED');
  for(const key of ['walletLookback','price','tip','depth','protectedPeriodAndTail'])assert.notEqual(r.criteria[key],'PASS');
  assert.equal(r.hypothesis,'INCONCLUSIVE/data insufficient');assert.equal(r.reconstructed.denominator,null);
});
test('default CLI and invalid flags fail before evidence, key or transport access',async()=>{
  assert.equal((await w.runCli([])).code,'DISABLED');assert.equal((await w.runCli(['--first-five'])).code,'ARGUMENTS_INVALID');
  assert.equal((await w.runStage({enabled:false})).code,'DISABLED');
});
test('opening window charges are retained, including preceding keyed and free metadata starts',()=>{
  const a=attestation();a.creditsReservedBefore=300;a.ancillaryStartsBefore=3;a.publicStartsBefore=3;a.receivedBefore=671;
  const s=w.initial(SELECTED,a,lineage);w.reserve(s,0);assert.equal(s.creditsReserved,400);assert.equal(s.ancillaryStarts,3);
  assert.equal(s.publicStarts,3);assert.equal(s.received,671);assert.equal(s.actualCredits,null);
});
test('manual ancillary reservation persists inside the same ledger without calling any source',async()=>{
  const io=memory();let clock=at,calls=0;
  const deps={store:io,lineage,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>{calls++;const bytes=emptyPage(null);return{code:null,status:200,received:bytes.length,bytes};}};
  const first=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);
  const a=attestation();a.creditsReservedBefore=500;a.receivedBefore=w.replay(io,first.manifestHash,{lineage,now:()=>clock}).received;
  const reservation=w.reserveAncillary({enabled:true,head:first.manifestHash,attestation:a,credits:1,queryHash:digest(Buffer.from('synthetic-independent-query')),purpose:'independent-preholdout-receipt'},deps);
  assert.equal(reservation.code,null);assert.equal(reservation.creditsReserved,501);assert.equal(reservation.ancillaryStartsReserved,1);assert.equal(calls,5);
  const replay=w.replay(io,reservation.manifestHash,{lineage,now:()=>clock});assert.equal(replay.state.ancillaryStarts,1);assert.equal(replay.state.creditsReserved,501);
  assert.equal(reservation.reservation.actualStarts,null);assert.equal(reservation.reservation.actualCredits,null);
});
test('terminal responses cannot jump backwards and missing positions never establish complete coverage',()=>{
  const tx={slot:410195947,transactionIndex:1,blockTime:1775001600,transaction:{signatures:[sig(999)]}};
  const raw=Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:[tx],paginationToken:null}}));
  assert.equal(w.admit(raw,w.query(address(123),'410195947:2')).code,'CURSOR_INVALID');
  delete tx.transactionIndex;const missing=Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data:[tx],paginationToken:null}}));
  const p=w.admit(missing,w.query(address(123)));assert.equal(p.code,null);assert.equal(p.coverage,'UNVERIFIED_POSITION');
  const s=w.initial(SELECTED,attestation(),lineage);w.reserve(s,0);w.applyPage(s,0,p);assert.equal(s.tokens[0].status,'INCOMPLETE');
});
test('unsafe signatures, immutable conflicts and nonadvancing cursors reject whole pages',()=>{
  const tx={slot:410195947,transactionIndex:1,blockTime:1775001600,transaction:{signatures:[sig(998)]}};
  const page=(data,cursor)=>Buffer.from(JSON.stringify({jsonrpc:'2.0',id:1,result:{data,paginationToken:cursor}}));
  assert.equal(w.admit(page([tx],'410195947:1'),w.query(address(123),'410195947:1')).code,'CURSOR_INVALID');
  assert.equal(w.admit(page([tx,{...tx,blockTime:1775001601}],null),w.query(address(123))).code,'IMMUTABLE_CONFLICT');
  assert.notEqual(w.admit(page([{...tx,transaction:{signatures:['bad']}}],null),w.query(address(123))).code,null);
});
test('root conflicts and early timers fail without source starts or automatic retries',async()=>{
  const io=memory();io.create=()=>{throw Error('OUTPUT_EXISTS');};let calls=0;
  const deps={store:io,lineage,now:()=>at,transport:async()=>{calls++;assert.fail();}};
  assert.equal((await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps)).code,'OUTPUT_EXISTS');assert.equal(calls,0);
  const store=memory();const r=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},
    {store,lineage,now:()=>at,wait:async()=>{},transport:async()=>{calls++;const bytes=emptyPage(null);return{code:null,status:200,received:bytes.length,bytes};}});
  assert.equal(r.code,'CLOCK_INVALID');assert.equal(calls,1);
});
test('review requirement and changed lineage block continuation with retained first-five charges',async()=>{
  const io=memory();let clock=at,calls=0;
  const deps={store:io,lineage,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>{calls++;const bytes=emptyPage(null);return{code:null,status:200,received:bytes.length,bytes};}};
  const first=await w.runStage({enabled:true,phase:'first-five',selection:SELECTED,attestation:attestation()},deps);
  const prior=w.replay(io,first.manifestHash,{lineage,now:()=>clock}),a=attestation();a.creditsReservedBefore=500;a.receivedBefore=prior.state.received;
  const r=await w.runStage({enabled:true,phase:'continue',head:first.manifestHash,attestation:a},deps);
  assert.equal(r.code,'QUALIFICATION_REQUIRED');assert.equal(r.creditsReserved,500);assert.equal(calls,5);
  const changed={...lineage,scripts:{runner:digest(Buffer.from('different-lineage'))}};
  assert.throws(()=>w.replay(io,first.manifestHash,{lineage:changed,now:()=>clock}),/LINEAGE_CHANGED/);
});
