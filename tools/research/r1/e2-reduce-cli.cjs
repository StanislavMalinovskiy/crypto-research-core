'use strict';
// Read-only composition of the immutable capture's exported checks. No source
// operation, secret access, replay guard override or economic implementation.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {execFileSync,spawnSync}=require('node:child_process');
const {performance}=require('node:perf_hooks');
const VERSION='e2-read-only-verifier-v2',END=Date.parse('2026-10-05T04:12:00Z');
const CAPTURE='C:/git/crypto-research/worktrees/e2-window-exec-20261005';
const COMMIT='1f8b07d1d2d2fa5e914f25c85bf1aaa25ec3d63a';
const GATE='C:/crypto-research-evidence/r1-e2-path-v1-gate-20261003';
const RUNNER_SHA='sha256:d5ca569de622cef985c79503291572e63be49adf8071c1a37741d6b6bf2713bc';
const REDUCER_SHA='sha256:06662533e841226aebee48e993899fe3e3ebdf8421787ebf489c853ecf657137';
const need=(v,c='INTEGRITY_ERROR')=>{if(!v)throw Error(c);};
const uint=v=>Number.isSafeInteger(v)&&v>=0;
const sha=b=>'sha256:'+crypto.createHash('sha256').update(b).digest('hex');
const json=v=>Buffer.from(JSON.stringify(v)+'\n');
function safe(file){const absolute=path.resolve(file);for(let p=absolute;;p=path.dirname(p)){const s=fs.lstatSync(p);need(!s.isSymbolicLink(),'UNSAFE_PATH');if(p===path.dirname(p))break;}need(fs.realpathSync(absolute).toLowerCase()===absolute.toLowerCase(),'UNSAFE_PATH');return absolute;}
function git(root,args){return execFileSync('git',args,{cwd:root,timeout:5000,maxBuffer:32000000});}
function loadCapture(file,commit=COMMIT){
 const expected=path.resolve(CAPTURE,'tools/research/r1/e2-cohort-d1-window.cjs');need(safe(file).toLowerCase()===expected.toLowerCase()&&commit===COMMIT,'LINEAGE_CHANGED');
 need(git(CAPTURE,['rev-parse','HEAD']).toString().trim()===commit&&!git(CAPTURE,['status','--porcelain']).length,'LINEAGE_CHANGED');
 const scripts={};for(const n of git(CAPTURE,['ls-files','tools/research/r1/*.cjs']).toString().trim().split(/\r?\n/)){
  const bytes=fs.readFileSync(safe(path.join(CAPTURE,n))),blob=git(CAPTURE,['cat-file','blob',commit+':'+n]);need(bytes.equals(blob),'LINEAGE_CHANGED');scripts[n]=sha(bytes);
 }
 need(sha(fs.readFileSync(file))===RUNNER_SHA,'LINEAGE_CHANGED');
 const w=require(file),h=require(path.join(path.dirname(file),'exploratory-probe.cjs'));
 const identity=w.lineage();need(identity.source.commit===commit&&identity.source.dirty===false,'LINEAGE_CHANGED');
 return {w,h,identity,provenance:{path:expected,commit,scripts}};
}
// Bounded exact signature/content index: 88 ASCII key bytes plus SHA256 content;
// collision probing compares the complete key, never only its digest.
class Index{
 constructor(){this.size=0;this.table=null;this.chunks=[];}
 add(key,hash){need(typeof key==='string'&&key.length<=88&&/^[1-9A-HJ-NP-Za-km-z]+$/.test(key)&&/^sha256:[a-f0-9]{64}$/.test(hash));let slot=crypto.createHash('sha256').update(key).digest().readUInt32LE(0)&8388607;
  const value=Buffer.from(hash.slice(7),'hex');this.table??=new Uint32Array(8388608);
  while(this.table[slot]){const id=this.table[slot]-1,b=this.chunks[Math.floor(id/10000)],at=id%10000*121;
   if(b[at]===key.length&&b.subarray(at+1,at+1+key.length).toString('ascii')===key){need(b.subarray(at+89,at+121).equals(value),'IMMUTABLE_CONFLICT');return false;}slot=(slot+1)&8388607;}
  need(this.size<4200000,'INPUT_LIMIT');const chunk=Math.floor(this.size/10000),at=this.size%10000*121;this.chunks[chunk]??=Buffer.alloc(1210000);
  const b=this.chunks[chunk];b[at]=key.length;b.write(key,at+1,'ascii');value.copy(b,at+89);this.table[slot]=++this.size;return true;
 }
}
function context(){return loadCapture(path.resolve(CAPTURE,'tools/research/r1/e2-cohort-d1-window.cjs'));}
function verifyScope(io,o,ctx=context()){
 const {w,h,identity}=ctx,{parse,canonical,digest,fingerprint}=h,eq=(a,b)=>canonical(a)===canonical(b);
 const snapshot=parse(io.read('manifest.json'),false),s=w.verify('manifest',snapshot);need(snapshot.hash===o.head);
 need(s.version===w.VERSION&&eq(s.lineage.scripts,identity.scripts)&&s.lineage.source.commit===COMMIT&&!s.lineage.source.dirty,'LINEAGE_CHANGED');
 w.validSelection(s.selection);need(eq(parse(io.read('selection.json'),false),s.selection));
 const capturedBytes=s.records.reduce((n,r)=>{if(!r.rawHash)return n;need(uint(r.rawBytes)&&r.rawBytes<=64000000);return n+r.rawBytes;},0);need(capturedBytes<=50000000000&&(o.scopeBytes===undefined||o.scopeBytes===capturedBytes),'RESOURCE_LIMIT');
 need(uint(o.tokens)&&o.tokens>0&&o.tokens<=300,'ARGUMENTS_INVALID');
 const terminal=t=>t.status==='COMPLETE'||t.status==='INCOMPLETE'&&t.pages===14;
 need(s.tokens.slice(0,o.tokens).every(terminal),'INFLIGHT_SCOPE');
 const cut=s.records.findIndex(r=>r.tokenIndex>=o.tokens),records=s.records.slice(0,cut<0?s.records.length:cut);
 need(s.records.length<=4200&&s.records.every((r,i)=>r.ordinal===i+1&&uint(r.tokenIndex)&&r.tokenIndex<300&&(!i||r.tokenIndex>=s.records[i-1].tokenIndex)));
 need(s.records.slice(records.length).every(r=>r.tokenIndex>=o.tokens),'IMMUTABLE_CONFLICT');
 const names=new Set(['manifest.json','selection.json']),checked={'selection.json':digest(io.read('selection.json'))};
 const read=n=>{const b=io.read(n);checked[n]=digest(b);return b;};
 const rebuilt=w.initial(s.selection,s.attestation,s.lineage),index=new Index(),timing=[];let last=null,rawBytes=0;
 need(Array.isArray(s.ancillary)&&s.ancillary.length<=w.LIMITS.ancillary);
 const applyAncillary=after=>{for(const [i,r] of s.ancillary.entries())if(r.afterHistoryRecords===after){
  const n='ancillary-'+String(i+1).padStart(5,'0')+'.json',{externalReceipt,...reserved}=r;names.add(n);
  need(eq(parse(read(n),false),reserved)&&r.ordinal===i+1&&uint(r.reservedAt)&&r.reservedAt+w.LIMITS.timeout+w.LIMITS.publication<=w.STOP&&/^sha256:[a-f0-9]{64}$/.test(r.queryHash));
  need([0,1].includes(r.publicStartsReserved)&&rebuilt.publicStarts+r.publicStartsReserved<=300);w.reserve(rebuilt,0,'ancillary',r.credits);rebuilt.ancillary.push(r);
  if(externalReceipt){const rn=n.replace('.json','.receipt.json');names.add(rn);const bytes=read(rn),e=parse(bytes,false);
   need(bytes.length<=4096&&Object.keys(e).sort().join(',')==='actualCredits,actualStarts,end,ordinal,publicStarts,queryHash,rawHash,received,start,status');
   need(e.ordinal===r.ordinal&&e.queryHash===r.queryHash&&uint(e.start)&&uint(e.end)&&e.end>=e.start&&[0,1].includes(e.actualStarts)&&[0,1].includes(e.publicStarts)&&e.publicStarts<=r.publicStartsReserved&&e.actualStarts+e.publicStarts>0&&uint(e.received)&&e.received<=w.LIMITS.response+1&&e.actualCredits===null);
   need(uint(e.status)&&e.status<=599&&/^sha256:[a-f0-9]{64}$/.test(e.rawHash)&&digest(bytes)===externalReceipt.hash&&eq(e,externalReceipt.body));
   rebuilt.received+=e.received;rebuilt.publicStarts+=e.publicStarts;need(rebuilt.received<=w.LIMITS.received&&rebuilt.publicStarts<=300);
   if(e.start<r.reservedAt||e.start+w.LIMITS.timeout+w.LIMITS.publication>w.STOP||e.end-e.start>w.LIMITS.timeout||e.end+w.LIMITS.publication>w.STOP)timing.push({ancillaryOrdinal:r.ordinal,code:'ANCILLARY_TIMING'});
  }
 }};
 applyAncillary(0);
 for(const r of records){o.check?.();need(r.ordinal===rebuilt.records.length+1&&uint(r.start));
  if(r.start<w.START||r.start+w.LIMITS.timeout+w.LIMITS.publication>w.STOP)timing.push({ordinal:r.ordinal,code:'START_RESERVATION'});
  if(last!==null&&r.start-last<w.LIMITS.spacing)timing.push({ordinal:r.ordinal,code:'START_SPACING'});last=r.start;
  const t=rebuilt.tokens[r.tokenIndex],address=s.selection.tokens[r.tokenIndex].addresses[t.addressIndex];
  need(eq(r.query,w.query(address,t.cursor))&&r.addressIndex===t.addressIndex&&r.creditsReserved===100);w.reserve(rebuilt,r.tokenIndex);
  const n=String(r.ordinal).padStart(5,'0');names.add(n+'.start.json');need(uint(r.reservedAt)&&r.start>=r.reservedAt);
  need(eq(parse(read(n+'.start.json'),false),{ordinal:r.ordinal,tokenIndex:r.tokenIndex,addressIndex:r.addressIndex,query:r.query,creditsReserved:100,actualCredits:null,reservedAt:r.reservedAt,start:null,outcome:'START_RESERVED'}));
  need(r.outcome!=='START_RESERVED','INCOMPLETE_START');names.add(n+'.receipt.json');need(eq(parse(read(n+'.receipt.json'),false),r));
  need(uint(r.end)&&r.end>=r.start&&uint(r.received)&&r.transportStarted===true);rebuilt.actualHistoryStarts++;rebuilt.received+=r.received;need(r.received<=w.LIMITS.response+1&&rebuilt.received<=w.LIMITS.received);
  if(r.end-r.start>w.LIMITS.timeout||r.end+w.LIMITS.publication>w.STOP)timing.push({ordinal:r.ordinal,code:'RESPONSE_PUBLICATION'});
  if(r.rawHash){names.add(n+'.raw');const raw=read(n+'.raw');rawBytes+=raw.length;need(raw.length===r.rawBytes&&raw.length===r.received&&digest(raw)===r.rawHash);
   const a=w.admit(raw,r.query);need(a.code===null&&r.code===null&&eq(w.mappingCounts(a.rows,a.rowBytes,o.check),r.mapping));need(r.returnedRows===a.returnedRows&&r.cursor===a.paginationToken);
   const before=index.size;const order=a.rows.map((row,i)=>({row,i})).sort((x,y)=>h.integer(x.row.slot)-h.integer(y.row.slot)||(x.row.transactionIndex===undefined?0:h.integer(x.row.transactionIndex))-(y.row.transactionIndex===undefined?0:h.integer(y.row.transactionIndex))||(x.row.transaction.signatures[0]<y.row.transaction.signatures[0]?-1:x.row.transaction.signatures[0]>y.row.transaction.signatures[0]?1:0));
   for(const {row,i} of order)if(index.add(row.transaction.signatures[0],fingerprint(w.VERSION+'-tx',row)))o.consume?.(row,a.rowBytes[i],r,i);
   need(r.equalScopedRows===a.rows.length-(index.size-before));w.applyPage(rebuilt,r.tokenIndex,a);
  }else need(r.code!==null&&r.outcome==='STOPPED');
  rebuilt.records.push(r);applyAncillary(rebuilt.records.length);
 }
 for(let i=0;i<o.tokens;i++){const t=rebuilt.tokens[i];if(t.pages>=14&&t.status!=='COMPLETE')t.status='INCOMPLETE';need(eq(t,s.tokens[i]));}
 // All current names must be explained, even during a later in-flight append.
 for(const r of s.records.slice(records.length)){const n=String(r.ordinal).padStart(5,'0');names.add(n+'.start.json');if(r.outcome!=='START_RESERVED')names.add(n+'.receipt.json');if(r.rawHash)names.add(n+'.raw');}
 for(const [i,r] of s.ancillary.entries()){need(uint(r.afterHistoryRecords)&&r.afterHistoryRecords<=s.records.length);const n='ancillary-'+String(i+1).padStart(5,'0')+'.json';names.add(n);if(r.externalReceipt)names.add(n.replace('.json','.receipt.json'));}
 const actual=io.list().filter(n=>n!=='lock');need(actual.length===names.size&&actual.every(n=>names.has(n)),'ORPHAN_FILE');need(s.retained===io.size());
 const whole=o.tokens===300; if(whole){need(!io.list().includes('lock'),'INFLIGHT_SCOPE');for(const k of ['historyStarts','actualHistoryStarts','creditsReserved','ancillaryStarts','publicStarts','received'])need(s[k]===rebuilt[k]);need(s.actualCredits===null&&rebuilt.ancillary.length===s.ancillary.length);}
 // Prefix global counters are derived from ALL reserved metadata, never from a
 // subset pretending to be the global ledger. Original cap helpers enforce caps.
 const global=w.initial(s.selection,s.attestation,s.lineage);for(const r of s.records){w.reserve(global,r.tokenIndex);if(r.transportStarted){global.actualHistoryStarts++;need(uint(r.received));global.received+=r.received;}}
 for(const r of s.ancillary){w.reserve(global,0,'ancillary',r.credits);if(r.externalReceipt){global.received+=r.externalReceipt.body.received;global.publicStarts+=r.externalReceipt.body.publicStarts;}}
 for(const k of ['historyStarts','actualHistoryStarts','creditsReserved','ancillaryStarts','publicStarts','received'])need(s[k]===global[k]);need(s.actualCredits===null&&s.received<=w.LIMITS.received&&s.publicStarts<=300);
 const result={...w.FLAGS,version:VERSION,status:whole?'WHOLE_SCOPE_VERIFIED':'PREFIX_VERIFIED',semanticIntegrity:'PASS',budgetValidity:'PASS',recordedTiming:{status:timing.length?'FAIL':'PASS',violations:timing},
  manifestHash:snapshot.hash,phase:s.phase,creditsReserved:s.creditsReserved,actualCredits:s.actualCredits,received:s.received,rawFiles:records.filter(r=>r.rawHash).length,uniqueTransactions:index.size,qualification:w.qualify(s),verdict:w.summary(s),
  scope:{tokens:o.tokens,unverifiedTokens:300-o.tokens,records:records.length,rawBytes,whole,cohortDenominator:300},snapshot,checked,originalFullReplay:{status:'NOT_RUN',reason:'ORIGINAL_300000MS_GUARD'}};
 result.scopeHash=fingerprint(VERSION+'-scope',{selection:s.selection.hash,lineage:s.lineage,records,tokens:s.tokens.slice(0,o.tokens),checked});
 return result;
}
function recheck(io,r,ctx=context()){
 const {h,w}=ctx,s=w.verify('manifest',h.parse(io.read('manifest.json'),false));
 need(h.canonical(s.selection)===h.canonical(r.snapshot.selection)&&h.canonical(s.lineage)===h.canonical(r.snapshot.lineage)&&h.canonical(s.attestation)===h.canonical(r.snapshot.attestation),'IMMUTABLE_CONFLICT');
 need(h.canonical(s.records.slice(0,r.scope.records))===h.canonical(r.snapshot.records.slice(0,r.scope.records))&&h.canonical(s.tokens.slice(0,r.scope.tokens))===h.canonical(r.snapshot.tokens.slice(0,r.scope.tokens)),'IMMUTABLE_CONFLICT');
 const priorAncillary=r.snapshot.ancillary.filter(x=>x.afterHistoryRecords<=r.scope.records);need(h.canonical(s.ancillary.slice(0,priorAncillary.length))===h.canonical(priorAncillary),'IMMUTABLE_CONFLICT');
 for(const [n,hash] of Object.entries(r.checked))need(h.digest(io.read(n))===hash,'IMMUTABLE_CONFLICT');
 for(const k of ['creditsReserved','historyStarts','actualHistoryStarts','ancillaryStarts','publicStarts','received','retained'])need(s[k]>=r.snapshot[k],'IMMUTABLE_CONFLICT');
 const names=new Set(['manifest.json','selection.json']);for(const x of s.records){const n=String(x.ordinal).padStart(5,'0');names.add(n+'.start.json');if(x.outcome!=='START_RESERVED')names.add(n+'.receipt.json');if(x.rawHash)names.add(n+'.raw');}
 s.ancillary.forEach((x,i)=>{const n='ancillary-'+String(i+1).padStart(5,'0')+'.json';names.add(n);if(x.externalReceipt)names.add(n.replace('.json','.receipt.json'));});
 const actual=io.list().filter(n=>n!=='lock');need(actual.length===names.size&&actual.every(n=>names.has(n))&&s.retained===io.size(),'ORPHAN_FILE');
 if(r.scope.whole)need(h.canonical(w.seal('manifest',s))===h.canonical(r.snapshot)&&!io.list().includes('lock'),'IMMUTABLE_CONFLICT');return s;
}
function allowance(bytes){need(uint(bytes)&&bytes<=50000000000,'INPUT_LIMIT');return Number([10800000n,3n*((149560n*BigInt(bytes)+690794561n)/690794562n)].reduce((a,b)=>a<b?a:b));}
function budget(b,now){need(uint(b.scopeBytes)&&uint(b.elapsedMs)&&uint(b.rawReadBytes)&&uint(b.metadataBytes),'RESOURCE_LIMIT');need(b.elapsedMs<allowance(b.scopeBytes)&&b.rawReadBytes<=Math.min(150000000000,3*b.scopeBytes)&&b.metadataBytes<=64000000&&now<END,'TIME_LIMIT');need(process.memoryUsage().rss<=1750000000,'RESOURCE_LIMIT');}
function qualification(v,current,original,hasReduction){
 const eligible=hasReduction&&v.scope.tokens===5&&v.phase==='QUALIFICATION_PENDING'&&current.hash===v.manifestHash&&v.snapshot.code===null&&v.snapshot.ancillary.every(x=>x.externalReceipt)&&v.qualification.technicalUsable&&v.recordedTiming.status==='PASS'&&original?.manifestHash===v.manifestHash&&original.status==='SEMANTIC_REPLAY_VALID'&&original.semanticIntegrity==='PASS'&&original.budgetValidity==='PASS'&&original.recordedTiming?.status==='PASS'&&original.phase==='QUALIFICATION_PENDING'&&original.qualification?.technicalUsable===true;
 return {status:eligible?'INDEPENDENT_REVIEW_REQUIRED':'NOT_QUALIFIED',eligibleForReview:!!eligible,manifestHash:v.manifestHash,technicalUsable:!!eligible,qualificationPacket:'ONLY_MAIN_AFTER_INDEPENDENT_REVIEW',d1Passed:false};
}
function boundedRead(file,limit,onBytes=()=>{},onReserve=()=>{}){
 const p=safe(file),size=fs.statSync(p).size;need(size<=limit,'INPUT_LIMIT');onReserve(size+1);const fd=fs.openSync(p,'r'),bytes=Buffer.alloc(size+1);let used=0;
 try{while(used<bytes.length){const n=fs.readSync(fd,bytes,used,Math.min(2000000,bytes.length-used),null);if(!n)break;used+=n;}}
 finally{fs.closeSync(fd);onBytes(used);}need(used===size,'IMMUTABLE_CONFLICT');return bytes.subarray(0,used);
}
function diskIO(root,charge){safe(root);return {read(n){need(/^(manifest|selection)\.json$|^\d{5}\.(raw|start\.json|receipt\.json)$|^ancillary-\d{5}(\.receipt)?\.json$/.test(n),'UNSAFE_PATH');return boundedRead(path.join(root,n),n.endsWith('.raw')?64000000:32000000,size=>charge(n,size,'received'),size=>charge(n,size,'reserve'));},list:()=>fs.readdirSync(root),size:()=>fs.readdirSync(root).filter(n=>n!=='lock').reduce((a,n)=>a+fs.statSync(safe(path.join(root,n))).size,0)};}
function reductionConsumer(policy,ctx=context(),feeEvidenceByRawHash={}){
 const {w,h}=ctx,mapper=require(path.join(path.dirname(ctx.provenance.path),'pumpswap-mapper-v2.cjs')),reducer=require('./e2-trade-reducer-v1.cjs');
 need(sha(fs.readFileSync(path.join(__dirname,'e2-trade-reducer-v1.cjs')))===REDUCER_SHA,'LINEAGE_CHANGED');
 const counts={},reasons={},references=[],byStratum={},fields={feeTotalObserved:0,feeSplitProven:0,feeSplitUnknown:0,tipsObservable:0,tipsUnknown:0,knownAtUnknown:0};let reconstructed=0,uniqueTransactions=0;
 return {consume(row,raw,rec,i){const rawHash=h.digest(raw),signature=row.transaction.signatures[0],mapping=mapper.mapHeliusTransaction(raw,rawHash),lineage={source:'helius-getTransactionsForAddress',rawHash,rawCrossCheck:'CHECKED',knownAt:'UNKNOWN'};
   // Forward only exact transaction-bound facts to the unchanged fee prover;
   // no generic approval boolean or inferred historical regime is supplied.
   if(Object.hasOwn(feeEvidenceByRawHash,rawHash))lineage.feeRegimeEvidence=feeEvidenceByRawHash[rawHash];
   const result=reducer.reduceTransaction({mapping,raw:row,policy,lineage});
   uniqueTransactions++;if(result.reason)reasons[result.reason]=(reasons[result.reason]??0)+1;
   for(const action of result.actions){if(/^[0-9]+$/.test(action.fees?.total??''))fields.feeTotalObserved++;if(action.fees?.splitStatus==='PROVEN')fields.feeSplitProven++;else fields.feeSplitUnknown++;if(action.tips?.tipsObservable)fields.tipsObservable++;else fields.tipsUnknown++;}if(result.source?.knownAt==='UNKNOWN')fields.knownAtUnknown++;
   counts[result.status]=(counts[result.status]??0)+1;reconstructed+=result.reconstructedCount;const stratum=['april','may','june'][Math.floor(rec.tokenIndex/100)];
   const venues=[...new Set(result.actions.flatMap(a=>a.legs.map(l=>l.venue)))];const key=(venues.length===1?venues[0]:'UNKNOWN')+':'+stratum+':'+result.status;byStratum[key]=(byStratum[key]??0)+1;
   if(references.length<200)references.push({ordinal:rec.ordinal,row:i,signature,rawHash:rec.rawHash,status:result.status,reason:result.reason,reductionHash:result.reductionHash});
  },finish(r){need(h.canonical(policy.cohortMints.slice().sort())===h.canonical(r.snapshot.selection.tokens.map(t=>t.mint).sort()),'POLICY_INVALID');
   const provenance=r.snapshot.records.slice(0,r.scope.records).filter(x=>x.rawHash).map(x=>({ordinal:x.ordinal,rawHash:x.rawHash,returnedRows:x.returnedRows,equalScopedRows:x.equalScopedRows}));
   return {counts,reasons,byStratum,fields,reconstructed,uniqueTransactions,references,provenance,cohortDenominator:300,cohortAdmitted:false,d1Passed:false};}};
}
async function run(args){
 const started=performance.now(),wall=Date.now();let b,prior=null,output,ctx,io,result;
 const flags=new Map();need(args.length%2===0,'ARGUMENTS_INVALID');for(let i=0;i<args.length;i+=2){need(!flags.has(args[i]),'ARGUMENTS_INVALID');flags.set(args[i],args[i+1]);}
 need(flags.get('--enable')==='true','DISABLED');need(['verify-v2','reduce'].includes(flags.get('--mode')),'ARGUMENTS_INVALID');
 const allowed=['--enable','--mode','--capture','--capture-commit','--head','--raw-root','--tokens','--output','--config','--config-hash','--resources','--resources-hash','--prior','--prior-hash'];need([...flags.keys()].every(k=>allowed.includes(k)),'ARGUMENTS_INVALID');
 need(allowed.slice(0,-2).every(k=>flags.has(k))&&flags.has('--prior')===flags.has('--prior-hash'),'ARGUMENTS_INVALID');
 const pinned=(f,hash)=>{const p=safe(f);need(path.dirname(p).toLowerCase()===path.resolve(GATE).toLowerCase(),'UNSAFE_PATH');const bytes=boundedRead(p,32000000);need(sha(bytes)===hash,'LINEAGE_CHANGED');return JSON.parse(bytes);};
 const config=pinned(flags.get('--config'),flags.get('--config-hash')),resources=pinned(flags.get('--resources'),flags.get('--resources-hash'));
 need(config.version===VERSION&&/^sha256:[a-f0-9]{64}$/.test(config.reviewHash)&&config.cliHash===sha(fs.readFileSync(__filename))&&config.reducerHash===REDUCER_SHA,'LINEAGE_CHANGED');
 b={...config.budget};if(flags.has('--prior')){prior=pinned(flags.get('--prior'),flags.get('--prior-hash'));const {hash,...body}=prior;need(sha(json(body))===hash&&prior.version===VERSION);for(const k of ['elapsedMs','rawReadBytes','metadataBytes'])need(b[k]>=prior.budget[k],'RESOURCE_LIMIT');}
 budget(b,wall);ctx=loadCapture(flags.get('--capture'),flags.get('--capture-commit'));
 output=path.resolve(flags.get('--output'));need(path.dirname(output).toLowerCase()===path.resolve(GATE).toLowerCase()&&!fs.existsSync(output)&&!fs.existsSync(output+'.reservation.json'),'OUTPUT_EXISTS');safe(path.dirname(output));
 const rawRoot=safe(flags.get('--raw-root'));need(!output.toLowerCase().startsWith(rawRoot.toLowerCase()+path.sep),'UNSAFE_PATH');
 need(uint(resources.retainedBytes)&&uint(resources.freeBytes)&&Date.parse(resources.observedAt)<=wall&&Date.parse(resources.validUntil)>=wall&&resources.retainedBytes+64000000<50000000000&&resources.freeBytes-64000000>=30000000000,'DISK_LIMIT');
 const free=fs.statfsSync(path.dirname(output));need(Number(free.bavail)*Number(free.bsize)-64000000>=30000000000,'FREE_SPACE_LIMIT');
 const originalElapsed=b.elapsedMs,check=()=>{b.elapsedMs=originalElapsed+Math.ceil(performance.now()-started);budget(b,Date.now());};
 const sourceBytesBefore=fs.readdirSync(rawRoot).filter(n=>n!=='lock').reduce((n,f)=>n+fs.statSync(safe(path.join(rawRoot,f))).size,0);
 const physical=()=>{const now=Date.now(),free=fs.statfsSync(path.dirname(output)),sourceBytes=fs.readdirSync(rawRoot).filter(n=>n!=='lock').reduce((n,f)=>n+fs.statSync(safe(path.join(rawRoot,f))).size,0);
  need(now<=Date.parse(resources.validUntil)&&resources.retainedBytes+Math.max(0,sourceBytes-sourceBytesBefore)+64000000<50000000000,'DISK_LIMIT');need(Number(free.bavail)*Number(free.bsize)-64000000>=30000000000,'FREE_SPACE_LIMIT');
  return resources.retainedBytes+Math.max(0,sourceBytes-sourceBytesBefore)+64000000>=40000000000?'CHECKPOINT_80':'WITHIN_LIMIT';};
 // An immutable launch reservation survives a killed worker. Without a final
 // receipt, its conservative full remaining charges must carry forward.
 const reservation={version:VERSION,status:'READER_RESERVED',capture:ctx.provenance,manifestHash:flags.get('--head'),rawRoot,mode:flags.get('--mode'),requestedTokens:Number(flags.get('--tokens')),allowanceMs:allowance(b.scopeBytes),configHash:flags.get('--config-hash'),startedAt:new Date(wall).toISOString(),priorHash:flags.get('--prior-hash')??null,
  budget:{...b,elapsedMs:allowance(b.scopeBytes),rawReadBytes:Math.min(150000000000,3*b.scopeBytes),metadataBytes:64000000}};
 const reservationBytes=json({...reservation,hash:sha(json(reservation))});need(b.metadataBytes+reservationBytes.length+32000000+1000000<=64000000,'OUTPUT_LIMIT');
 physical();fs.writeFileSync(output+'.reservation.json',reservationBytes,{flag:'wx'});b.metadataBytes+=reservationBytes.length;
 try{
 io=diskIO(flags.get('--raw-root'),(n,size,phase)=>{if(n.endsWith('.raw')){if(phase==='received')b.rawReadBytes+=size;else budget({...b,rawReadBytes:b.rawReadBytes+size},Date.now());}check();});
 const consumer=flags.get('--mode')==='reduce'?reductionConsumer(config.policy,ctx,config.feeEvidenceByRawHash??{}):null;
 result=verifyScope(io,{head:flags.get('--head'),tokens:Number(flags.get('--tokens')),scopeBytes:b.scopeBytes,check,consume:consumer?.consume},ctx);
 if(prior){need(prior.capture.commit===ctx.provenance.commit);const p=prior.verification;if(p){need(ctx.h.canonical(result.snapshot.records.slice(0,p.scope.records))===ctx.h.canonical(p.snapshot.records.slice(0,p.scope.records))&&ctx.h.canonical(result.snapshot.tokens.slice(0,p.scope.tokens))===ctx.h.canonical(p.snapshot.tokens.slice(0,p.scope.tokens)),'IMMUTABLE_CONFLICT');for(const [n,hash] of Object.entries(p.checked))need(result.checked[n]===hash,'IMMUTABLE_CONFLICT');}}
 let reduction=null;if(consumer){need(result.recordedTiming.status==='PASS','TIME_LIMIT');reduction=consumer.finish(result);}
 const current=recheck(io,result,ctx);check();const original=config.originalFirstFiveReplay?pinned(config.originalFirstFiveReplay.path,config.originalFirstFiveReplay.fileHash):null;
 const qualificationResult=qualification(result,ctx.w.seal('manifest',current),original,!!reduction);
 const repo=path.resolve(__dirname,'../../..'),analysis={commit:git(repo,['rev-parse','HEAD']).toString().trim(),dirty:!!git(repo,['status','--porcelain']).length,cliHash:sha(fs.readFileSync(__filename)),reducerHash:REDUCER_SHA,reducerVersion:'e2-trade-reducer-v1',mapperHash:ctx.identity.scripts['pumpswap-mapper-v2.cjs'],mapperVersion:'helius-tx-adapter-v1',configHash:flags.get('--config-hash'),policyVersion:config.policy?.version??'NOT_APPLICABLE'};
 const semanticHash=ctx.h.fingerprint(VERSION+'-analysis',{scopeHash:result.scopeHash,analysis,reduction});
 const report={version:VERSION,...ctx.w.FLAGS,mode:flags.get('--mode'),capture:ctx.provenance,analysis,verification:result,reduction,qualification:qualificationResult,originalFirstFiveReplay:config.originalFirstFiveReplay??{status:'NOT_CHECKED'},semanticHash,budget:b,allowanceMs:allowance(b.scopeBytes),physical:physical(),resourcesHash:flags.get('--resources-hash'),startedAt:new Date(wall).toISOString(),finishedAt:new Date().toISOString(),peakRss:process.resourceUsage().maxRSS*1024,priorHash:flags.get('--prior-hash')??null,reservationHash:sha(reservationBytes)};
 check();b.elapsedMs+=1000;budget(b,Date.now()+1000);
 // Record exact report bytes plus the supervised stdout reservation separately;
 // keep the latter charged until Main reconciles actual external log bytes.
 report.stdoutReservedBytes=1000000;const before=b.metadataBytes;let bytes;for(let i=0;i<8;i++){bytes=json({...report,hash:sha(json(report))});b.metadataBytes=before+bytes.length+report.stdoutReservedBytes;}
 bytes=json({...report,hash:sha(json(report))});need(bytes.length<=32000000,'OUTPUT_LIMIT');budget(b,Date.now()+1000);fs.writeFileSync(output,bytes,{flag:'wx'});return {status:'ANALYSIS_RECORDED',output,hash:sha(json(report)),fileHash:sha(bytes),semanticHash,scope:result.scope,d1Passed:false};
 }catch(e){
  b.elapsedMs=originalElapsed+Math.ceil(performance.now()-started)+1000;
  const failure={version:VERSION,status:'STOPPED',code:/^[A-Z_]+$/.test(e.message)?e.message:'INTEGRITY_ERROR',capture:ctx.provenance,budget:b,checkedScope:result?.scope??null,unverifiedCoverage:'UNKNOWN',d1Passed:false,reservationHash:sha(reservationBytes)};
  const before=b.metadataBytes;let bytes;for(let i=0;i<8;i++){bytes=json({...failure,hash:sha(json(failure))});b.metadataBytes=before+bytes.length+1000000;}
  bytes=json({...failure,hash:sha(json(failure))});if(bytes.length<=32000000&&b.metadataBytes<=64000000&&!fs.existsSync(output))fs.writeFileSync(output,bytes,{flag:'wx'});throw e;
 }
}
if(require.main===module){const args=process.argv.slice(2);if(args[0]==='--bounded-reader'){
 const heap=require('node:v8').getHeapStatistics().heap_size_limit;try{need(process.execArgv.filter(x=>x.startsWith('--max-old-space-size=')).join()==='--max-old-space-size=1024'&&heap<=1275068416,'RESOURCE_LIMIT');run(args.slice(1)).then(r=>{process.stdout.write(JSON.stringify(r)+'\n');process.exitCode=2;}).catch(e=>{process.stdout.write(JSON.stringify({status:'STOPPED',code:/^[A-Z_]+$/.test(e.message)?e.message:'INTEGRITY_ERROR',d1Passed:false})+'\n');process.exitCode=1;});}catch{process.stdout.write('{"status":"STOPPED","code":"RESOURCE_LIMIT","d1Passed":false}\n');process.exitCode=1;}
 }else{let remaining=Math.min(10800000,END-Date.now());try{
  const arg=n=>args[args.indexOf(n)+1];if(args.includes('--enable')&&arg('--enable')==='true'&&['verify-v2','reduce'].includes(arg('--mode'))&&args.includes('--config')){
   const file=safe(arg('--config'));need(path.dirname(file).toLowerCase()===path.resolve(GATE).toLowerCase(),'UNSAFE_PATH');const bytes=boundedRead(file,32000000);need(sha(bytes)===arg('--config-hash'),'LINEAGE_CHANGED');const b=JSON.parse(bytes).budget;budget(b,Date.now());remaining=Math.min(remaining,allowance(b.scopeBytes)-b.elapsedMs-1000);
  }
  if(remaining<=0)throw Error('TIME_LIMIT');const began=performance.now(),child=spawnSync(process.execPath,['--max-old-space-size=1024',__filename,'--bounded-reader',...args],{timeout:Math.floor(remaining),maxBuffer:1000000,windowsHide:true});
  if(child.status===null||child.error||!child.stdout?.length){process.stdout.write(JSON.stringify({status:'STOPPED',code:'RESOURCE_LIMIT',supervisorElapsedMs:Math.ceil(performance.now()-began),unreconciledReservation:'CHARGE_FULL_RESERVED_ENVELOPE',d1Passed:false})+'\n');process.exitCode=1;}else{process.stdout.write(child.stdout);process.exitCode=child.status;}
 }catch(e){process.stdout.write(JSON.stringify({status:'STOPPED',code:/^[A-Z_]{1,64}$/.test(e.message)?e.message:'INTEGRITY_ERROR',d1Passed:false})+'\n');process.exitCode=1;}}
}
module.exports={VERSION,END,loadCapture,verifyScope,recheck,allowance,budget,qualification,reductionConsumer,boundedRead,Index,run};
