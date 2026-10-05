'use strict';
// New analysis view over immutable V1 extraction; captured raw/state/diagnostics
// remain unchanged. No provider access or historical deployment inference.
const legacy=require('./pumpswap-analysis-adapter-v1.cjs'),h=require('./exploratory-probe.cjs');
const PUMP='pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA',TOKEN='TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',SYSTEM='11111111111111111111111111111111';
const WSOL='So11111111111111111111111111111111111111112',ATA='ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL';
const alphabet='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const ANALYSIS={...legacy.ANALYSIS,version:'pumpswap-analysis-adapter-v2',proofVersion:'owned-lifecycle-event-v2',deployment:'UNVERIFIED'};
const need=(v,c='INPUT_INVALID')=>{if(!v)throw Error(c);};
const integer=v=>{const n=Number(v);need(/^(0|[1-9][0-9]*)$/.test(String(v))&&Number.isSafeInteger(n));return n;};
const same=(a,b)=>h.canonical(a)===h.canonical(b);
function decode(s,max=768){need(typeof s==='string'&&s.length<=1024);let n=0n,z=0;for(const c of s){const d=alphabet.indexOf(c);need(d>=0);n=n*58n+BigInt(d);}while(s[z]==='1')z++;let x=n.toString(16);const b=n?Buffer.from(x.padStart(Math.ceil(x.length/2)*2,'0'),'hex'):Buffer.alloc(0);need(z+b.length<=max);return Buffer.concat([Buffer.alloc(z),b]);}
function encode(b){let n=BigInt('0x'+b.toString('hex')),s='';while(n){s=alphabet[Number(n%58n)]+s;n/=58n;}for(const c of b){if(c)break;s='1'+s;}return s;}
function compare(a,b){for(let n=0;n<Math.min(a.length,b.length);n++)if(a[n]!==b[n])return a[n]-b[n];return a.length-b.length;}
const ancestor=(a,b)=>a.length<b.length&&a.every((n,i)=>n===b[i]);
// Exact documentary layouts pinned by the unchanged V1 configuration. This
// establishes schema/roles, not deployment applicability or guessed tail roles.
function idlLayout(r){
 const spec={'33e685a4017f83ad':['sell',24,21],'66063d1201daebea':['buy',25,23],'c62e1552b4d9e870':['buy_exact_quote_in',25,23]}[r.data.subarray(0,8).toString('hex')];
 if(r.program!==PUMP||!spec||r.data.length!==spec[1]||r.accounts.length!==spec[2]||spec[1]===25&&![0,1].includes(r.data[24]))return null;
 if(r.accounts[11]!==TOKEN||r.accounts[12]!==TOKEN||r.accounts[13]!==SYSTEM||r.accounts[14]!==ATA||r.accounts[16]!==PUMP||r.accounts[3]===r.accounts[4]||new Set([r.accounts[5],r.accounts[6],r.accounts[7],r.accounts[8]]).size!==4)return null;
 return {variant:spec[0],idlHash:'sha256:5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6',applicability:'PINNED_IDL_EXACT_SCHEMA_OBSERVED_SOURCE_ROLES',deployment:'UNVERIFIED'};
}
function context(raw){
 const msg=raw.transaction.message,meta=raw.meta,loaded=meta.loadedAddresses??{writable:[],readonly:[]};
 const keys=[...msg.accountKeys.map(k=>typeof k==='string'?k:k.pubkey),...loaded.writable,...loaded.readonly];need(keys.length<=128&&keys.every(k=>decode(k,32).length===32));
 const records=[],byPath=new Map();const add=(i,path)=>{const program=keys[integer(i.programIdIndex)],accounts=i.accounts.map(n=>keys[integer(n)]);need(program&&accounts.every(Boolean));const r={program,accounts,path,data:decode(i.data)};records.push(r);byPath.set(path.join('.'),r);};
 msg.instructions.forEach((i,n)=>add(i,[n]));const groups=new Set();
 for(const g of meta.innerInstructions??[]){const outer=integer(g.index);need(outer<msg.instructions.length&&!groups.has(outer),'PATH_UNKNOWN');groups.add(outer);const stack=[[outer]],children=new Map();
  for(const i of g.instructions){const height=integer(i.stackHeight);need(height>=2&&height<=64&&height<=stack.length+1,'PATH_UNKNOWN');stack.length=height-1;const p=stack.at(-1),key=p.join('.'),n=children.get(key)??0;children.set(key,n+1);const path=[...p,n];stack.push(path);add(i,path);}}
 need(records.length<=10000);records.sort((a,b)=>compare(a.path,b.path));return {keys,records,byPath,raw};
}
const stable=t=>t&&['Mint','Owner','Decimals','Amount'].every(k=>t['pre'+k]!=null&&t['post'+k]!=null)&&t.preMint===t.postMint&&t.preOwner===t.postOwner&&t.preDecimals===t.postDecimals;
function lifecycle(ctx,rawStates,invocations){
 const states=rawStates.map(t=>({...t,lifecycle:null}));
 // Include transient WSOL accounts only with actual create+init+close proof.
 for(const v of invocations)for(const account of [v.userBase,v.userQuote])if(account&&!states.some(t=>t.account===account))states.push({signature:ctx.raw.transaction.signatures[0],account,preMint:null,preOwner:null,preAmount:null,preDecimals:null,postMint:null,postOwner:null,postAmount:null,postDecimals:null,lifecycle:null});
 for(const s of states){if(stable(s))continue;
  const uses=ctx.records.filter(r=>r.program===TOKEN&&[3,12].includes(r.data[0])&&(r.accounts[0]===s.account||r.accounts[r.data[0]===12?2:1]===s.account));
  if(!uses.length)for(const v of invocations)if(v.event&&[v.creatorAccount,v.event.protocolAccount].includes(s.account))uses.push({path:v.event.instructionPath});
  if(!uses.length)continue;
  const inits=ctx.records.filter(r=>r.program===TOKEN&&r.accounts[0]===s.account&&[1,16,18].includes(r.data[0]));
  const creates=ctx.records.filter(r=>r.program===SYSTEM&&r.accounts[1]===s.account&&r.data.length===52&&r.data.readUInt32LE(0)===0&&encode(r.data.subarray(20))===TOKEN&&r.data.readBigUInt64LE(12)===165n);
  const closes=ctx.records.filter(r=>r.program===TOKEN&&r.accounts[0]===s.account&&r.data[0]===9);
  const missingPost=['Mint','Owner','Amount','Decimals'].every(k=>s['post'+k]===null);
  if(missingPost&&['Mint','Owner','Amount','Decimals'].every(k=>s['pre'+k]!==null)&&inits.length===0&&creates.length===0&&closes.length===1){
   const close=closes[0];if(close.data.length===1&&close.accounts.length===3&&close.accounts[2]===s.preOwner&&compare(close.path,uses.at(-1).path)>0){
    for(const k of ['Mint','Owner','Decimals'])s['post'+k]=s['pre'+k];s.postAmount='0';s.lifecycle={creationPaths:[],closePath:close.path,transient:false,sourceRawHash:null};
   }continue;
  }
  if(inits.length!==1||creates.length!==1||closes.length>1)continue;
  const init=inits[0],create=creates[0],owner=init.data[0]===1?init.accounts[2]:init.data.length===33?encode(init.data.subarray(1)):null,mint=init.accounts[1];
  if(!owner||!mint||compare(create.path,init.path)>=0||compare(init.path,uses[0].path)>=0)continue;
  if(init.data[0]===18&&init.accounts.length!==2||init.data[0]===16&&init.accounts.length!==3||init.data[0]===1&&(init.accounts.length!==4||init.data.length!==1))continue;
  const present=s.preMint!==null?'pre':s.postMint!==null?'post':null;
  let decimals=present?s[present+'Decimals']:mint===WSOL?9:null;if(decimals===null)continue;
  if(present&&(s[present+'Mint']!==mint||s[present+'Owner']!==owner))continue;
  const close=closes[0];if(close&&(close.data.length!==1||close.accounts.length!==3||close.accounts[2]!==owner||compare(close.path,uses.at(-1).path)<=0))continue;
  const preMissing=['Mint','Owner','Amount','Decimals'].every(k=>s['pre'+k]===null),postMissing=['Mint','Owner','Amount','Decimals'].every(k=>s['post'+k]===null);
  if(!preMissing)continue; // Do not reinterpret existing pre balances/partial rows.
  if(postMissing&&!close)continue;
  s.preMint=mint;s.preOwner=owner;s.preAmount='0';s.preDecimals=decimals;
  if(postMissing){s.postMint=mint;s.postOwner=owner;s.postAmount='0';s.postDecimals=decimals;}
  s.lifecycle={creationPaths:[create.path,init.path],closePath:close?.path??null,transient:postMissing,sourceRawHash:null};
 }
 return states;
}
function event(r,parent){
 if(r.program!==PUMP||r.accounts.length!==1||!same(r.path.slice(0,-1),parent.path)||parent.accounts[15]!==r.accounts[0]||parent.accounts[16]!==PUMP)return null;
 const b=r.data;if(b.length<16||b.subarray(0,8).toString('hex')!=='e445a52e51cb9a1d')return null;
 const tag=b.subarray(8,16).toString('hex'),sell=tag==='3e2f370aa503dc2a',buy=tag==='67f4521f2cf57777';if(!sell&&!buy)return null;
 if(sell&&![368,384].includes(b.length))return null;
 if(buy){if(b.length<416||b[368]>1)return null;const len=b.readUInt32LE(409);if(len>18||![413+len,429+len].includes(b.length))return null;const name=b.subarray(413,413+len).toString('utf8');if(!['buy','buy_exact_quote_in'].includes(name))return null;}
 const names=['timestamp',sell?'baseAmountIn':'baseAmountOut',sell?'minQuoteAmountOut':'maxQuoteAmountIn','userBaseReserves','userQuoteReserves','poolBaseReserves','poolQuoteReserves',sell?'quoteAmountOut':'quoteAmountIn','lpBasisPoints','lpFee','protocolBasisPoints','protocolFee',sell?'quoteWithoutLpFee':'quoteWithLpFee',sell?'userQuoteAmountOut':'userQuoteAmountIn'];
const e={kind:sell?'SellEvent':'BuyEvent',instructionPath:r.path,sourceHash:h.digest(b),idlHash:ANALYSIS.idlHashes[b.length===368?0:1]};names.forEach((n,i)=>e[n]=b.readBigUInt64LE(16+i*8).toString());
 for(const [n,i] of [['pool',0],['user',1],['userBase',2],['userQuote',3],['protocolRecipient',4],['protocolAccount',5],['coinCreator',6]])e[n]=encode(b.subarray(128+i*32,160+i*32));
 e.creatorBasisPoints=b.readBigUInt64LE(352).toString();e.creatorFee=b.readBigUInt64LE(360).toString();e.cashback=sell&&b.length===384?b.readBigUInt64LE(376).toString():buy&&b.length===429+b.readUInt32LE(409)?b.readBigUInt64LE(b.length-8).toString():'0';
 if(e.pool!==parent.accounts[0]||e.user!==parent.accounts[1]||e.userBase!==parent.accounts[5]||e.userQuote!==parent.accounts[6]||e.protocolRecipient!==parent.accounts[9]||e.protocolAccount!==parent.accounts[10])return null;
 return e;
}
function transfers(ctx,invocations){return ctx.records.filter(r=>r.program===TOKEN&&[3,12].includes(r.data[0])).map(r=>{const checked=r.data[0]===12;need(r.data.length===(checked?10:9)&&r.accounts.length===(checked?4:3),'TRANSFER_LAYOUT_UNSUPPORTED');const parents=invocations.filter(v=>ancestor(v.instructionAddress,r.path));return {instructionAddress:r.path,source:r.accounts[0],destination:r.accounts[checked?2:1],declaredAuthority:r.accounts[checked?3:2],mint:checked?r.accounts[1]:null,decimals:checked?r.data[9]:null,amount:r.data.readBigUInt64LE(1).toString(),associatedInvocation:parents.length===1?parents[0].identity:null};});}
function eventFlows(v,tx,states){
 const e=v.event;if(!e)return false;const scoped=tx.filter(t=>same(t.associatedInvocation,v.identity));if(!scoped.length||e.cashback!=='0')return false;
 const flow=(account,mint,decimals)=>scoped.reduce((n,t)=>{if(t.source!==account&&t.destination!==account)return n;if(t.mint!==null&&(t.mint!==mint||t.decimals!==decimals))throw Error('EVENT_TRANSFER_MISMATCH');return n+(t.destination===account?BigInt(t.amount):0n)-(t.source===account?BigInt(t.amount):0n);},0n);
 const b=states.find(t=>t.account===v.poolBaseVault),q=states.find(t=>t.account===v.poolQuoteVault);if(!stable(b)||!stable(q)||b.preMint!==v.baseMint||q.preMint!==v.quoteMint||b.preOwner!==v.pool||q.preOwner!==v.pool)return false;
 const sell=v.variant==='sell';if(sell!==(e.kind==='SellEvent'))return false;
 const base=BigInt(sell?e.baseAmountIn:e.baseAmountOut),quote=BigInt(sell?e.userQuoteAmountOut:e.userQuoteAmountIn),poolQuote=BigInt(sell?e.quoteWithoutLpFee:e.quoteWithLpFee);
 if(flow(v.userBase,v.baseMint,b.preDecimals)!==(sell?-base:base)||flow(v.poolBaseVault,v.baseMint,b.preDecimals)!==(sell?base:-base)||flow(v.userQuote,v.quoteMint,q.preDecimals)!==(sell?quote:-quote)||flow(v.poolQuoteVault,v.quoteMint,q.preDecimals)!==(sell?-poolQuote:poolQuote))return false;
 if(flow(e.protocolAccount,v.quoteMint,q.preDecimals)!==BigInt(e.protocolFee)||flow(v.creatorAccount,v.quoteMint,q.preDecimals)!==BigInt(e.creatorFee))return false;
 if(sell?BigInt(e.quoteAmountOut)-BigInt(e.lpFee)!==poolQuote||poolQuote-BigInt(e.protocolFee)-BigInt(e.creatorFee)!==quote:BigInt(e.quoteAmountIn)+BigInt(e.lpFee)!==poolQuote||poolQuote+BigInt(e.protocolFee)+BigInt(e.creatorFee)!==quote)return false;
 return true;
}
function mapHeliusTransaction(bytes,rawHash){
 const m=legacy.mapHeliusTransaction(bytes,rawHash);if(m.status==='MAPPING_INVALID')return m;
 try{const raw=h.parse(bytes),ctx=context(raw);m.originalInvocations=structuredClone(m.invocations);m.originalDiagnostics=structuredClone(m.diagnostics);const invocations=[],events=[];
  for(const r of ctx.records.filter(r=>r.program===PUMP)){
   const variant={'33e685a4017f83ad':'sell','66063d1201daebea':'buy','c62e1552b4d9e870':'buy_exact_quote_in'}[r.data.subarray(0,8).toString('hex')];if(!variant)continue;
   const old=m.invocations.find(v=>same(v.instructionAddress,r.path)),v={...old,variant,identity:{chain:m.configuration.chain,signature:raw.transaction.signatures[0],instructionAddress:r.path},instructionAddress:r.path,event:null,layoutProof:idlLayout(r)};
   for(const [name,index] of Object.entries({pool:0,declaredTrader:1,baseMint:3,quoteMint:4,userBase:5,userQuote:6,poolBaseVault:7,poolQuoteVault:8,creatorAccount:17,creatorAuthority:18}))v[name]=r.accounts[index]??null;
   const matches=ctx.records.map(x=>event(x,r)).filter(Boolean);if(matches.length===1){v.event=matches[0];events.push(matches[0]);}
   invocations.push(v);
  }
  const states=lifecycle(ctx,m.tokenStates,invocations),tx=transfers(ctx,invocations);
  for(const v of invocations){const r=ctx.byPath.get(v.instructionAddress.join('.'));if(raw.meta.err!==null){v.reason='FAILED_TRANSACTION';continue;}if(r.accounts[11]!==TOKEN||r.accounts[12]!==TOKEN){v.reason='UNSUPPORTED_TOKEN_PROGRAM';continue;}
   const prefixValid=r.accounts[13]===SYSTEM&&r.accounts[14]===ATA&&r.accounts[16]===PUMP&&v.baseMint!==v.quoteMint&&new Set([v.userBase,v.userQuote,v.poolBaseVault,v.poolQuoteVault]).size===4;
   if(v.event&&prefixValid&&eventFlows(v,tx,states)){v.reason=null;v.layoutApplicability='OBSERVED_SAME_INVOCATION_EVENT';}
   else if(!v.event&&v.layoutProof){v.reason=null;v.layoutApplicability=v.layoutProof.applicability;}
   else if(v.reason===null&&v.event)v.reason='EVENT_FLOW_MISMATCH';
  }
  // Unknown non-event Pump instructions stay counted; proven self-events alone
  // disappear from the economic invocation list.
  for(const old of m.originalInvocations)if(!invocations.some(v=>same(v.instructionAddress,old.instructionAddress))&&!events.some(e=>same(e.instructionPath,old.instructionAddress)))invocations.push(old);
  invocations.sort((a,b)=>compare(a.instructionAddress,b.instructionAddress));for(const s of states)if(s.lifecycle)s.lifecycle.sourceRawHash=rawHash;
  m.invocations=invocations;m.nonEconomicFrames=events;m.analysisProof={version:ANALYSIS.proofVersion,states,transfers:tx,rawHash};m.version=ANALYSIS.version;m.analysisConfiguration=ANALYSIS;
  m.factsHash=h.fingerprint(ANALYSIS.version+'-facts',{rawHash,invocations,proof:m.analysisProof,events,originalFactsHash:m.factsHash});delete m.reportHash;m.reportHash=h.fingerprint(ANALYSIS.version,m);need(Buffer.byteLength(JSON.stringify(m))<=2000000,'OUTPUT_LIMIT');return m;
 }catch(e){return {...m,status:'OFFLINE_MAPPING_PARTIAL',analysisFailure:e.message??'INPUT_INVALID'};}
}
module.exports={mapBlock:legacy.mapBlock,mapHeliusTransaction,ANALYSIS,context,decode,encode,compare,ancestor,stable,eventFlows,deriveLifecycle:lifecycle,deriveTransfers:transfers,decodeEvent:event,idlLayout};
