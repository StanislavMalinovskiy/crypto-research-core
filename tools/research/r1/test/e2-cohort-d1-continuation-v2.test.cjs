'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const w=require('../e2-cohort-d1-window.cjs'),h=require('../exploratory-probe.cjs'),census=require('../e2-offline-mint-count.cjs');
const file=path.join(__dirname,'../e2-cohort-d1-continuation-v2.cjs');
// Execute the existing dispatch and initialization until the new version exists:
// an unsupported dispatch or reset ledger is a behavioral failure, not discovery RED.
const target=fs.existsSync(file)?require(file):{runCli:w.runCli,initialContinuation:(s,a,l)=>w.initial(s.selection,a,l)};
const alphabet='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function addr(n,size=32){const b=Buffer.alloc(size);b.writeUInt32BE(n,size-4);let x=BigInt('0x'+b.toString('hex')),s='';while(x){s=alphabet[Number(x%58n)]+s;x/=58n;}for(const v of b){if(v)break;s='1'+s;}return s;}
const sha=h.digest,json=v=>Buffer.from(JSON.stringify(v)+'\n');
function fixture(){
 const c=w.collector();for(let m=0;m<3;m++)for(let i=0;i<100;i++)c.add({status:'OBSERVED_DECLARED_CREATE_POOL',layoutApplicability:'UNVERIFIED',slot:census.ROOTS[m].start,timestamp:census.DATES[m],transactionIndex:i,instructionPath:[0],cpiDepth:0,accountCount:18,dataLength:59,lineOrdinal:0,rawHash:sha(Buffer.from('synthetic-census')),signature:addr(1000+m*100+i,64),pool:addr(10000+m*100+i),globalConfig:addr(2),creator:addr(3),baseMint:addr(1000+m*100+i),quoteMint:census.QUOTES[0]},m);
 const selection=c.finish([]),lineage={runtime:process.version,source:{commit:'1f8b07d1d2d2fa5e914f25c85bf1aaa25ec3d63a',dirty:false},scripts:{synthetic:sha(Buffer.from('synthetic-capture'))}};
 const a={creditsReservedBefore:0,ancillaryStartsBefore:0,publicStartsBefore:3,receivedBefore:671,freeze:{protocolHash:sha(Buffer.from('synthetic-protocol'))}};
 const s=w.initial(selection,a,lineage),files=new Map([['selection.json',json(selection)]]);
 for(const [tokenIndex,pages]of [14,14,14,3,1].entries())for(let p=0;p<pages;p++){
  const ordinal=s.records.length+1,start=w.START+1000+ordinal*250,terminal=tokenIndex>=3&&p===pages-1;
  const row={slot:410195947+ordinal,transactionIndex:0,blockTime:1775001600,transaction:{signatures:[addr(ordinal,64)],message:{accountKeys:[addr(9)],instructions:[]}},meta:{err:null,fee:0,innerInstructions:[],preTokenBalances:[],postTokenBalances:[],preBalances:[0],postBalances:[0]}};
  const raw=json({jsonrpc:'2.0',id:1,result:{data:[row],paginationToken:terminal?null:row.slot+':0'}}),q=w.query(selection.tokens[tokenIndex].addresses[0],s.tokens[tokenIndex].cursor),admitted=w.admit(raw,q);assert.equal(admitted.code,null);
  w.reserve(s,tokenIndex);w.applyPage(s,tokenIndex,admitted);s.actualHistoryStarts++;s.received+=raw.length;
  const reserved={ordinal,tokenIndex,addressIndex:0,query:q,creditsReserved:100,actualCredits:null,reservedAt:start,start:null,outcome:'START_RESERVED'};
  const r={...reserved,start,transportStarted:true,end:start+1,received:raw.length,status:200,code:null,responseHash:sha(raw),rawHash:sha(raw),rawBytes:raw.length,mapping:w.mappingCounts(admitted.rows,admitted.rowBytes),equalScopedRows:0,returnedRows:1,cursor:admitted.paginationToken,outcome:'RETAINED'};
  s.records.push(r);const n=String(ordinal).padStart(5,'0');files.set(n+'.raw',raw);files.set(n+'.start.json',json(reserved));files.set(n+'.receipt.json',json(r));
 }
 s.phase='QUALIFICATION_PENDING';s.lastStart=s.records.at(-1).start;
 const oldStore=store(files);for(let i=0;i<6;i++){files.set('manifest.json',json(w.seal('manifest',s)));s.retained=oldStore.size();}files.set('manifest.json',json(w.seal('manifest',s)));
 const head=w.seal('manifest',s).hash,report={version:'e2-read-only-verifier-v3',verification:{manifestHash:head,semanticIntegrity:'PASS',budgetValidity:'PASS',recordedTiming:{status:'PASS'},scope:{tokens:5}},qualification:{eligibleForReview:true,technicalUsable:true},analysis:{cliHash:sha(Buffer.from('cli')),mapperHash:sha(Buffer.from('mapper')),reducerHash:sha(Buffer.from('reducer')),configHash:sha(Buffer.from('analysis-config'))},semanticHash:sha(Buffer.from('semantic'))};
 report.hash=sha(json(report));const reportBytes=json(report),now=w.STOP+86400000;
 const config={version:'e2-cohort-d1-continuation-v2',oldHead:head,oldManifestFileHash:sha(oldStore.read('manifest.json')),windowStart:now,windowEnd:now+86400000,delegateDecision:'552fd850',attestation:{...a,plan:'Free',entitlement:'VERIFIED',historyCredits:100,tariffEvidenceHash:sha(Buffer.from('tariff')),poolIndexCoverage:'DOCUMENTED_INDEXED_POOL_ADDRESSES',poolIndexEvidenceHash:sha(Buffer.from('index')),creditsReservedBefore:4600,ancillaryStartsBefore:0,publicStartsBefore:3,receivedBefore:s.received,retainedBytes:5000000000,freeBytes:100000000000,observedAt:new Date(now).toISOString(),validUntil:new Date(now+86400000).toISOString(),expiry:new Date(now+172800000).toISOString()},qualification:{status:'APPROVE',manifestHash:head,technicalUsable:true,reviewHash:sha(Buffer.from('review')),analysisReportHash:sha(reportBytes),analysisSemanticHash:report.semanticHash,analysis:report.analysis}};
 return {s,oldStore,head,config,reportBytes,now,ctx:{w,h,Index:require('../e2-reduce-cli.cjs').Index,capture:lineage,operational:{commit:'a'.repeat(40),dirty:false,scripts:{continuation:sha(Buffer.from('synthetic-operational'))}}}};
}
function store(files=new Map()){let created=false;return {files,read:n=>Buffer.from(files.get(n)),list:()=>[...files.keys()],size:()=>[...files].filter(([n])=>n!=='lock').reduce((a,[,b])=>a+b.length,0),free:()=>100000000000,create(){assert.equal(created,false);created=true;},write(n,b,replace=false){if(!replace)assert.equal(files.has(n),false,n);files.set(n,Buffer.from(b));},publish(b){files.set('manifest.json',Buffer.from(b));},lock(){this.write('lock',json({pid:1}));},unlock(){files.delete('lock');}};}
test('continuation initializer carries 4600 credits and 46 starts, preserving terminal first-five states',()=>{
 const f=fixture(),s=target.initialContinuation(f.s,f.config.attestation,f.ctx.operational,f.config);
 assert.equal(s.historyStarts,46,'first-five starts must not reset');assert.equal(s.actualHistoryStarts,46);assert.equal(s.creditsReserved,4600);
 assert.deepEqual(s.tokens.slice(0,5),f.s.tokens.slice(0,5));assert.equal(s.tokens.slice(5).every(t=>t.pages===0),true);
});
test('actual continuation dispatch uses new current window and fetches exactly remaining 295 without old-five refetch',async()=>{
 const f=fixture(),io=store(),before=[...f.oldStore.files].map(([n,b])=>[n,sha(b)]);let clock=f.now,calls=0;
 const args=['--continue','--enable','true','--config','synthetic-config','--config-hash',sha(json(f.config))];
 const r=await target.runCli(args,{configBytes:json(f.config),qualificationBytes:f.reportBytes,oldStore:f.oldStore,store:io,context:f.ctx,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async q=>{assert.ok(!f.s.selection.tokens.slice(0,5).some(t=>t.addresses.includes(q.body.params[0])),'old first-five address must not be fetched');calls++;const bytes=json({jsonrpc:'2.0',id:1,result:{data:[],paginationToken:null}});return {code:null,status:200,received:bytes.length,bytes};}});
 assert.equal(r.code,null,'new continuation command must execute after old STOP');assert.equal(calls,295);assert.equal(r.creditsReserved,34100);assert.equal(r.historyStarts,341);
 assert.deepEqual([...f.oldStore.files].map(([n,b])=>[n,sha(b)]),before,'original evidence is immutable');
 const m=JSON.parse(io.read('manifest.json'));assert.equal(m.version,'e2-cohort-d1-continuation-v2');assert.equal(m.records[0].ordinal,47);assert.equal(m.records[0].tokenIndex,5);
});
module.exports={fixture,store,json,sha,addr};

test('default and malformed CLI stay disabled before all evidence/transport access',async()=>{
 let calls=0;const deps={configBytes:null,transport:()=>{calls++;assert.fail('forbidden');}};
 assert.equal((await target.runCli([],deps)).code,'DISABLED');assert.equal((await target.runCli(['--continue'],deps)).code,'ARGUMENTS_INVALID');assert.equal(calls,0);
});
test('current window, physical reservations and cumulative credit/token ceilings are exact',()=>{
 const f=fixture();assert.equal(target.capacity(f.config,f.now,0,f.s.received,100000000000),'WITHIN_LIMIT');
 assert.throws(()=>target.capacity(f.config,f.config.windowEnd-359999,0,f.s.received),/TIME_LIMIT/);
 assert.throws(()=>target.capacity({...f.config,windowEnd:f.config.windowEnd+1},f.now),/PREFLIGHT_INVALID/);
 for(const [mutate,code]of [[a=>a.retainedBytes=49999999999,'DISK_LIMIT'],[a=>a.freeBytes=30000000000,'FREE_SPACE_LIMIT'],[a=>a.plan='Paid','TARIFF_INVALID']]){const c=structuredClone(f.config);mutate(c.attestation);assert.throws(()=>target.capacity(c,f.now),new RegExp(code));}
 const s=target.initialContinuation(f.s,f.config.attestation,f.ctx.operational,f.config);s.creditsReserved=449900;w.reserve(s,5);assert.equal(s.creditsReserved,450000);assert.throws(()=>w.reserve(s,6),/CREDIT_LIMIT/);
 assert.throws(()=>w.reserve(target.initialContinuation(f.s,f.config.attestation,f.ctx.operational,f.config),0),/TOKEN_CAP/);
});
test('independent analysis report/semantic/code pins cannot be replaced by an approval boolean',()=>{
 const f=fixture();assert.doesNotThrow(()=>target.qualification(f.config,f.reportBytes,f.s,f.ctx));
 for(const mutate of [q=>delete q.analysisReportHash,q=>q.analysisSemanticHash=sha(Buffer.from('wrong')),q=>q.analysis.mapperHash=sha(Buffer.from('wrong')),q=>q.technicalUsable=false]){const c=structuredClone(f.config);mutate(c.qualification);assert.throws(()=>target.qualification(c,f.reportBytes,f.s,f.ctx),/QUALIFICATION_REQUIRED/);}
});
test('zero live filesystem free bytes cannot bypass the thirty GB physical guard',()=>{
 const f=fixture();assert.throws(()=>target.capacity(f.config,f.now,0,f.s.received,0),/FREE_SPACE_LIMIT/);
 assert.equal(target.capacity(f.config,f.now,0,f.s.received,30096000000),'WITHIN_LIMIT');
 assert.throws(()=>target.capacity(f.config,f.now,0,f.s.received,30095999999),/FREE_SPACE_LIMIT/);
});
test('invalid supplied live free measurement is rejected while historical proof explicitly omits it',()=>{
 const f=fixture();for(const free of [NaN,undefined,null,false,'100000000000',Infinity,-1,1.5,Number.MAX_SAFE_INTEGER+1])assert.throws(()=>target.capacity(f.config,f.now,0,f.s.received,free),/PREFLIGHT_INVALID/);
 assert.equal(target.capacity(f.config,f.now,0,f.s.received),'WITHIN_LIMIT');
});
test('actual source dispatch requires measured free bytes before any transport',async()=>{
 for(const [free,code]of [[0,'FREE_SPACE_LIMIT'],[undefined,'PREFLIGHT_INVALID'],[NaN,'PREFLIGHT_INVALID']]){
  const f=fixture(),io=store();let calls=0;io.free=()=>free;
  const r=await target.runCli(['--continue','--enable','true','--config','synthetic-config','--config-hash',sha(json(f.config))],{configBytes:json(f.config),qualificationBytes:f.reportBytes,oldStore:f.oldStore,store:io,context:f.ctx,now:()=>f.now,transport:async()=>{calls++;return {code:'HTTP_ERROR',status:503,received:0};}});
  assert.equal(r.code,code,'live measurement must fail closed');assert.equal(calls,0);assert.equal(io.files.size,0);
 }
});
test('observed high RSS is reported without an artificial source-preflight STOP',async()=>{
 const f=fixture(),io=store(),original=process.memoryUsage;let calls=0;
 try{process.memoryUsage=()=>({...original(),rss:2000000000});
  const r=await target.run(f.config,{oldStore:f.oldStore,store:io,context:f.ctx,qualificationBytes:f.reportBytes,now:()=>f.now,transport:async()=>{calls++;return {code:'HTTP_ERROR',status:503,received:0};}});
  assert.equal(r.code,'HTTP_ERROR','source must not stop solely on arbitrary RSS threshold');assert.equal(calls,1);
  const m=JSON.parse(io.read('manifest.json'));assert.equal(m.sourcePreflight.peakRssBytes,2000000000);assert.equal(m.observedPeakRssBytes,2000000000);
 }finally{process.memoryUsage=original;}
});
test('dual-root verifier streams exact linked old-five and admitted new pages, preserving partial source failure',async()=>{
 const f=fixture(),io=store();let clock=f.now,calls=0;
 const r=await target.run(f.config,{oldStore:f.oldStore,store:io,context:f.ctx,qualificationBytes:f.reportBytes,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>{calls++;if(calls===4)return {code:'HTTP_ERROR',status:503,received:3};const bytes=json({jsonrpc:'2.0',id:1,result:{data:[],paginationToken:null}});return {code:null,status:200,received:bytes.length,bytes};}});
 assert.equal(r.code,'HTTP_ERROR');assert.equal(calls,4);assert.equal(r.creditsReserved,5000);assert.equal(io.files.has('00050.raw'),false);
 const consumed=[],v=target.verifySnapshot({oldStore:f.oldStore,newStore:io,oldHead:f.head,newHead:r.manifestHash,consume:(row,raw,record)=>consumed.push([record.ordinal,row.transaction.signatures[0],sha(raw)])},f.ctx);
 assert.equal(v.semanticIntegrity,'PASS');assert.equal(v.budgetValidity,'PASS');assert.equal(v.recordedTiming.status,'PASS');assert.equal(v.scope.records,50);assert.equal(v.rawFiles,49);assert.equal(consumed.length,46);assert.equal(v.state.phase,'STOPPED');assert.equal(v.state.code,'HTTP_ERROR');assert.equal(v.verdict.d1Passed,false);
 const original=io.read('00047.raw');io.files.set('00047.raw',Buffer.from('tampered'));assert.throws(()=>target.verifySnapshot({oldStore:f.oldStore,newStore:io,oldHead:f.head,newHead:r.manifestHash},f.ctx),/INTEGRITY_ERROR/);io.files.set('00047.raw',original);
 io.files.set('00099.raw',Buffer.from('orphan'));assert.throws(()=>target.verifySnapshot({oldStore:f.oldStore,newStore:io,oldHead:f.head,newHead:r.manifestHash},f.ctx),/INTEGRITY_ERROR/);
});
test('unsafe holdout page is counted once, rejected before raw retention, and never retried',async()=>{
 const f=fixture(),io=store();let calls=0;const raw=json({jsonrpc:'2.0',id:1,result:{data:[{slot:1,transactionIndex:0,blockTime:1788134400,transaction:{signatures:[addr(999,64)]}}],paginationToken:null}});
 const r=await target.run(f.config,{oldStore:f.oldStore,store:io,context:f.ctx,qualificationBytes:f.reportBytes,now:()=>f.now,transport:async()=>{calls++;return {code:null,status:200,received:raw.length,bytes:raw};}});
 assert.equal(r.code,'FORBIDDEN_TIME');assert.equal(calls,1);assert.equal(r.creditsReserved,4700);assert.equal(io.files.has('00047.raw'),false);const m=JSON.parse(io.read('manifest.json'));assert.equal(m.received,f.s.received+raw.length);assert.equal(m.records[0].outcome,'STOPPED');
});
test('source rechecks real deadline after disk work and preserves a charged reservation without a call',async()=>{
 const f=fixture(),io=store();let clock=f.now,calls=0,reads=0;const orig=io.free;io.free=()=>{reads++;if(reads===4)clock=f.config.windowEnd;return orig();};
 const r=await target.run(f.config,{oldStore:f.oldStore,store:io,context:f.ctx,qualificationBytes:f.reportBytes,now:()=>clock,transport:async()=>{calls++;assert.fail('late start');}});
 assert.equal(r.code,'TIME_LIMIT');assert.equal(calls,0);assert.equal(r.creditsReserved,4700);const m=JSON.parse(io.read('manifest.json'));assert.equal(m.actualHistoryStarts,46);assert.equal(m.records[0].transportStarted,false);assert.equal(m.records[0].start,null);assert.equal(m.records[0].outcome,'STOPPED');
 const v=target.verifySnapshot({oldStore:f.oldStore,newStore:io,oldHead:f.head,newHead:r.manifestHash},f.ctx);assert.equal(v.state.actualHistoryStarts,46);assert.equal(v.state.historyStarts,47);assert.equal(v.state.creditsReserved,4700);assert.equal(v.rawFiles,46);
});

test('existing root, unsafe roots and early stalled waits prevent further starts',async()=>{
 assert.throws(()=>target.fileStore('C:\\unapproved-root'),/UNSAFE_PATH/);assert.throws(()=>target.fileStore(target.OLD_ROOT,true).write('manifest.json',json({})),/UNSAFE_PATH/);
 const f=fixture(),io=store();let calls=0;io.create=()=>{throw Error('OUTPUT_EXISTS');};
 const stopped=await target.run(f.config,{oldStore:f.oldStore,store:io,context:f.ctx,qualificationBytes:f.reportBytes,now:()=>f.now,transport:async()=>{calls++;assert.fail();}});assert.equal(stopped.code,'OUTPUT_EXISTS');assert.equal(calls,0);
 const bytes=json({jsonrpc:'2.0',id:1,result:{data:[],paginationToken:null}}),early=await target.run(f.config,{oldStore:f.oldStore,store:store(),context:f.ctx,qualificationBytes:f.reportBytes,now:()=>f.now,wait:async()=>{},transport:async()=>{calls++;return {code:null,status:200,received:bytes.length,bytes};}});
 assert.equal(early.code,'CLOCK_INVALID');assert.equal(calls,1);assert.equal(early.creditsReserved,4700);
});
test('bounded actual HTTPS transport suppresses plaintext and JSON-escaped key reflections',async()=>{
 const vm=require('node:vm'),{EventEmitter}=require('node:events'),source=fs.readFileSync(file,'utf8'),key='synthetic_key_123';
 for(const raw of [json({error:key}),Buffer.from('{"error":"'+key.replaceAll('_','\\u005f')+'"}')]){
  const fakeHttps={request(url,options,callback){const req=new EventEmitter();req.destroy=()=>{};req.end=()=>{const res=new EventEmitter();res.statusCode=200;res.complete=true;let sent=false;Object.defineProperty(res,'readableLength',{get:()=>sent?0:raw.length});res.read=n=>{if(!n||sent)return null;sent=true;return raw;};res.destroy=()=>{};callback(res);queueMicrotask(()=>{res.emit('readable');res.emit('end');});};return req;}};
  const module={exports:{}};vm.runInNewContext(source,{require:n=>n==='node:https'?fakeHttps:require(n),module,exports:module.exports,__dirname:path.dirname(file),__filename:file,Buffer,process,setTimeout,clearTimeout,queueMicrotask},{filename:file});
  const r=await module.exports.send(w.query(addr(9)),key,{h});assert.equal(r.code,'SECRET_EXPOSURE');assert.equal(r.bytes,null);assert.equal(r.received,raw.length);
 }
});

test('linked signature dedup is exact and conflicting new content is rejected before raw retention',async()=>{
 for(const conflicting of [false,true]){const f=fixture(),io=store();let clock=f.now,calls=0;const oldRow=JSON.parse(f.oldStore.read('00001.raw')).result.data[0];if(conflicting)oldRow.blockTime++;
  const bytes=json({jsonrpc:'2.0',id:1,result:{data:[oldRow],paginationToken:null}});
  const r=await target.run(f.config,{oldStore:f.oldStore,store:io,context:f.ctx,qualificationBytes:f.reportBytes,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async()=>{calls++;return calls===1?{code:null,status:200,received:bytes.length,bytes}:{code:'HTTP_ERROR',status:503,received:0};}});
  assert.equal(r.code,conflicting?'IMMUTABLE_CONFLICT':'HTTP_ERROR');assert.equal(calls,conflicting?1:2);assert.equal(io.files.has('00047.raw'),!conflicting);
  let consumed=0;const v=target.verifySnapshot({oldStore:f.oldStore,newStore:io,oldHead:f.head,newHead:r.manifestHash,consume:()=>{consumed++;}},f.ctx);assert.equal(consumed,46);assert.equal(v.uniqueTransactions,46);
  if(!conflicting){assert.equal(v.state.records[0].equalScopedRows,1);const m=JSON.parse(io.read('manifest.json')),original=Buffer.from(io.read('manifest.json'));
   for(const mutate of [s=>s.records[0].ordinal=48,s=>s.records[0].query.body.params[1].paginationToken='410195947:0',s=>s.historyStarts++,s=>s.version='e2-cohort-d1-window-v1',s=>s.inherited.rawReferences[0].rawHash=sha(Buffer.from('changed'))]){const body=target.verify(structuredClone(m),f.ctx);mutate(body);const changed=target.seal(body,f.ctx);io.files.set('manifest.json',json(changed));assert.throws(()=>target.verifySnapshot({oldStore:f.oldStore,newStore:io,oldHead:f.head,newHead:changed.hash},f.ctx));io.files.set('manifest.json',original);}
  }
 }
});
test('all selected addresses share fourteen pages and page14 nonterminal remains incomplete',async()=>{
 const f=fixture(),io=store();let clock=f.now,calls=0;
 const r=await target.run(f.config,{oldStore:f.oldStore,store:io,context:f.ctx,qualificationBytes:f.reportBytes,now:()=>clock,wait:async ms=>{clock+=ms;},transport:async q=>{calls++;if(calls===15){assert.equal(q.body.params[0],f.s.selection.tokens[6].addresses[0]);return {code:'HTTP_ERROR',status:503,received:0};}const row={slot:410196100+calls,transactionIndex:0,blockTime:1775001600,transaction:{signatures:[addr(10000+calls,64)],message:{accountKeys:[addr(9)],instructions:[]}},meta:{err:null,fee:0,innerInstructions:[],preTokenBalances:[],postTokenBalances:[],preBalances:[0],postBalances:[0]}};const bytes=json({jsonrpc:'2.0',id:1,result:{data:[row],paginationToken:row.slot+':0'}});return {code:null,status:200,received:bytes.length,bytes};}});
 assert.equal(r.code,'HTTP_ERROR');assert.equal(calls,15);const v=target.verifySnapshot({oldStore:f.oldStore,newStore:io,oldHead:f.head,newHead:r.manifestHash},f.ctx);assert.equal(v.state.tokens[5].pages,14);assert.equal(v.state.tokens[5].credits,1400);assert.equal(v.state.tokens[5].status,'INCOMPLETE');assert.equal(v.state.tokens[6].pages,1);
});
test('read-only callback cannot replace admitted row or exact raw bytes',()=>{
 const f=fixture();const before=[...f.oldStore.files].map(([n,b])=>[n,sha(b)]);
 assert.throws(()=>target.verifySnapshot({oldStore:f.oldStore,oldHead:f.head,consume:row=>{row.blockTime++;}},f.ctx),/IMMUTABLE_CONFLICT/);
 assert.throws(()=>target.verifySnapshot({oldStore:f.oldStore,oldHead:f.head,consume:(_row,raw)=>{raw[0]=0;}},f.ctx),/IMMUTABLE_CONFLICT/);
 assert.deepEqual([...f.oldStore.files].map(([n,b])=>[n,sha(b)]),before);
});
