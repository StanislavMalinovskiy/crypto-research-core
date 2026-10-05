'use strict';
const adapter=require('./pumpswap-analysis-adapter-v2.cjs'),legacy=require('./e2-trade-reducer-v1.cjs'),h=require('./exploratory-probe.cjs');
const VERSION='e2-trade-reducer-v2',PUMP='pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA',TOKEN='TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA';
const CU='ComputeBudget111111111111111111111111111111',WSOL='So11111111111111111111111111111111111111112';
const QUOTES=[WSOL,'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v','Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB'];
const PRECOMPILES=new Set(['Ed25519SigVerify111111111111111111111111111','KeccakSecp256k11111111111111111111111111111','Secp256r1SigVerify1111111111111111111111111']);
const FEE_SOURCES=['https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/program-runtime/src/compute_budget_processor.rs','https://solana.com/docs/core/fees/fee-structure','https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/program-runtime/src/prioritization_fee.rs','https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/sdk/src/fee.rs'];
const same=(a,b)=>h.canonical(a)===h.canonical(b),need=(v,reason,status='MISSING_LEG')=>{if(!v)throw {reason,status};};
const DERIVATION_REVISION='global-reconcile-all-owned-data-only-compute-budget-v5';
const number=v=>{const n=Number(v);need(/^(0|[1-9][0-9]*)$/.test(String(v))&&Number.isSafeInteger(n),'INPUT_INVALID');return n;};
const amount=v=>{need(typeof v==='string'&&/^(0|[1-9][0-9]{0,19})$/.test(v)&&BigInt(v)<=18446744073709551615n,'RAW_AMOUNT_INVALID');return BigInt(v);};
function fees(raw,ctx,trader){
 const f={total:'UNKNOWN',base:'UNKNOWN',priority:'UNKNOWN',payer:ctx.keys[0],traderCost:'UNKNOWN',splitStatus:'UNKNOWN',attributionStatus:'UNKNOWN',reason:'FEES_UNDETERMINED'};
 try{f.total=amount(raw.meta.fee).toString();if(f.payer===trader){f.traderCost=f.total;f.attributionStatus='ACTUAL_PAYER_IS_TRADER';}
  need(['legacy','0'].includes(String(raw.version)),'FEE_MESSAGE_FORMAT_UNPROVED');const header=raw.transaction.message.header,n=number(header.numRequiredSignatures);
  need(n>0&&n===raw.transaction.signatures.length&&new Set(raw.transaction.signatures).size===n&&raw.transaction.signatures.every(s=>adapter.decode(s,64).length===64),'FEE_SIGNATURES_UNPROVED');
  need(n<=raw.transaction.message.accountKeys.length&&number(header.numReadonlySignedAccounts)<n&&number(header.numReadonlyUnsignedAccounts)<=raw.transaction.message.accountKeys.length-n,'FEE_SIGNATURES_UNPROVED');
  need(!ctx.records.some(r=>PRECOMPILES.has(r.program)),'FEE_PRECOMPILE_UNPROVED');
  const cb=ctx.records.filter(r=>r.program===CU),p=cb.filter(r=>r.data[0]===3),l=cb.filter(r=>r.data[0]===2);
  need(cb.every(r=>r.path.length===1)&&new Set(cb.map(r=>r.data[0])).size===cb.length&&p.length===1&&l.length===1&&p[0].data.length===9&&l[0].data.length===5&&cb.every(r=>[2,3].includes(r.data[0])||[1,4].includes(r.data[0])&&r.data.length===5),'FEE_COMPUTE_BUDGET_UNPROVED');
  const price=p[0].data.readBigUInt64LE(1),limit=BigInt(l[0].data.readUInt32LE(1));need(limit>0n&&limit<=1400000n,'FEE_COMPUTE_LIMIT_UNPROVED');const base=5000n*BigInt(n),priority=(price*limit+999999n)/1000000n;
  need(base+priority===BigInt(f.total),'FEE_EQUATION_MISMATCH');f.base=base.toString();f.priority=priority.toString();f.splitStatus='PROVEN';f.reason=null;
  f.proof={version:'explicit-compute-budget-data-only-observed-equality-v3',requiredSignatures:n,priceMicroLamports:price.toString(),requestedLimit:limit.toString(),pricePath:p[0].path,limitPath:l[0].path,formula:'5000*signatures+ceil(price*requestedLimit/1000000)',sources:FEE_SOURCES};
 }catch(e){f.reason=e.reason??'FEE_TOTAL_MISSING_OR_INVALID';}return f;
}
function reduceTransaction(input){
 const {mapping:m,raw,policy,lineage}=input;let result={version:VERSION,derivationRevision:DERIVATION_REVISION,classification:'EXPLORATORY',cohortAdmitted:false,d1Passed:false,status:'MISSING_LEG',reason:'INPUT_INVALID',actions:[],reconstructedCount:0};
 try{
  need(Buffer.byteLength(JSON.stringify(input))<=4200000,'INPUT_LIMIT');need(m.rawHash===lineage.rawHash&&/^sha256:[a-f0-9]{64}$/.test(lineage.rawHash),'SOURCE_CONFLICT','AMBIGUOUS_ROUTE');
  need(m.status!=='MAPPING_INVALID',m.code??'MAPPER_INVALID');need(policy.chain===m.configuration.chain&&policy.venues.includes(PUMP)&&Array.isArray(policy.cohortMints)&&Array.isArray(policy.quoteMints),'POLICY_INVALID');
  need(raw.meta.err===null,raw.meta.err===undefined?'TRANSACTION_STATE_UNKNOWN':'FAILED_TRANSACTION');need(m.pathStatus!=='PATH_UNKNOWN'&&Array.isArray(raw.meta.innerInstructions),'PATH_UNKNOWN');
  const ctx=adapter.context(raw),signature=raw.transaction.signatures[0];need(adapter.decode(signature,64).length===64,'SOURCE_CONFLICT');
  need(m.sourceFacts.slot===number(raw.slot)&&m.sourceFacts.blockTime===number(raw.blockTime)&&same(m.sourceFacts.accountKeys,ctx.keys)&&same(m.sourceFacts.err,raw.meta.err)&&m.sourceFacts.fee===raw.meta.fee,'SOURCE_CONFLICT','AMBIGUOUS_ROUTE');
  result.source={signature,slot:number(raw.slot),blockTime:number(raw.blockTime),transactionIndex:m.sourceFacts.transactionIndex,knownAt:lineage.knownAt??'UNKNOWN',source:lineage.source??'UNKNOWN',rawHash:lineage.rawHash,rawCrossCheck:lineage.rawCrossCheck??'NOT_CHECKED'};
  // Untouched documentary-only layouts keep their original pure algorithm.
  // The source-backed route below is the new event/lifecycle derivation.
  if(!m.analysisProof){const old=legacy.reduceTransaction(input);return {...old,version:VERSION};}
  for(const side of ['pre','post']){const rows=raw.meta[side+'TokenBalances'];need(Array.isArray(rows),'TOKEN_STATE_RAW_MISSING');const seen=new Set();
   for(const row of rows){const account=ctx.keys[number(row.accountIndex)];need(account&&!seen.has(account),'TOKEN_STATE_RAW_CONFLICT','AMBIGUOUS_ROUTE');seen.add(account);const t=m.tokenStates.find(t=>t.account===account);need(t&&t.signature===signature,'TOKEN_STATE_RAW_CONFLICT','AMBIGUOUS_ROUTE');
    for(const [k,value]of Object.entries({Mint:row.mint??null,Owner:row.owner??null,Amount:row.uiTokenAmount?.amount??null,Decimals:row.uiTokenAmount?.decimals==null?null:number(row.uiTokenAmount.decimals)}))need(t[side+k]===value,'TOKEN_STATE_RAW_CONFLICT','AMBIGUOUS_ROUTE');}
   for(const t of m.tokenStates)if(!seen.has(t.account))need(['Mint','Owner','Amount','Decimals'].every(k=>t[side+k]===null),'TOKEN_STATE_RAW_CONFLICT','AMBIGUOUS_ROUTE');}
  const inv=m.invocations;need(inv.length>0,'NO_SWAP_INVOCATION');const paths=new Set();for(const v of inv){need(!v.reason,v.reason,v.reason?.startsWith('UNSUPPORTED')?'UNSUPPORTED_VENUE':'MISSING_LEG');need(v.identity.signature===signature&&!paths.has(v.instructionAddress.join('.')),'SOURCE_CONFLICT','AMBIGUOUS_ROUTE');paths.add(v.instructionAddress.join('.'));
   const r=ctx.byPath.get(v.instructionAddress.join('.'));need(r?.program===PUMP,'SOURCE_CONFLICT','AMBIGUOUS_ROUTE');for(const [n,i]of Object.entries({pool:0,declaredTrader:1,baseMint:3,quoteMint:4,userBase:5,userQuote:6,poolBaseVault:7,poolQuoteVault:8,creatorAccount:17,creatorAuthority:18}))need(v[n]===r.accounts[i],'SOURCE_CONFLICT','AMBIGUOUS_ROUTE');
   const actualEvents=ctx.records.map(x=>adapter.decodeEvent(x,r)).filter(Boolean);need(adapter.eventFrames(ctx,r).length===actualEvents.length&&actualEvents.length===(v.event?1:0),'EVENT_SOURCE_CONFLICT','AMBIGUOUS_ROUTE');
   if(v.event){need(actualEvents.length===1&&same(actualEvents[0],v.event),'EVENT_SOURCE_CONFLICT','AMBIGUOUS_ROUTE');}
   else{const proof=adapter.idlLayout(r);need(proof&&same(proof,v.layoutProof)&&proof.variant===v.variant,'IDL_LAYOUT_SOURCE_UNPROVED');}}
  const states=adapter.deriveLifecycle(ctx,m.tokenStates,inv);for(const t of states)if(t.lifecycle)t.lifecycle.sourceRawHash=lineage.rawHash;
  const tx=adapter.deriveTransfers(ctx,inv);need(same(states,m.analysisProof.states)&&same(tx,m.analysisProof.transfers),'DERIVED_SOURCE_CONFLICT','AMBIGUOUS_ROUTE');
  const owners=new Set(inv.map(v=>v.declaredTrader)),byAccount=new Map(states.map(t=>[t.account,t])),flows=new Map(),required=new Set(),infrastructure=new Set(inv.flatMap(v=>[v.poolBaseVault,v.poolQuoteVault,ctx.byPath.get(v.instructionAddress.join('.')).accounts[10],v.creatorAccount]));
  const add=(a,n)=>flows.set(a,(flows.get(a)??0n)+n);
  for(const v of inv){
   const trader=v.declaredTrader;need(![v.userBase,v.userQuote].some(a=>infrastructure.has(a)),'SHARED_OWNED_ACCOUNT_UNPROVED','AMBIGUOUS_ROUTE');
   for(const [account,mint,owner]of [[v.userBase,v.baseMint,trader],[v.userQuote,v.quoteMint,trader],[v.poolBaseVault,v.baseMint,v.pool],[v.poolQuoteVault,v.quoteMint,v.pool]]){const t=byAccount.get(account);need(adapter.stable(t)&&t.preMint===mint&&t.preOwner===owner,'SCOPED_ACCOUNT_UNPROVED');required.add(account);}
   const b=byAccount.get(v.poolBaseVault),q=byAccount.get(v.poolQuoteVault);if(v.event)need(adapter.eventFlows(v,tx,states),'EVENT_FLOW_UNPROVED');
   need(byAccount.get(v.userBase).preDecimals===b.preDecimals&&byAccount.get(v.userQuote).preDecimals===q.preDecimals,'SCOPED_DECIMALS_CONFLICT','DECIMALS_UNKNOWN');
   const swap=ctx.byPath.get(v.instructionAddress.join('.')),recipients=[[swap.accounts[10],swap.accounts[9]],[v.creatorAccount,v.creatorAuthority]];
   for(const [account,owner]of recipients){const t=byAccount.get(account);need(adapter.stable(t)&&t.preMint===v.quoteMint&&t.preOwner===owner&&t.preDecimals===q.preDecimals,'FEE_ACCOUNT_UNPROVED');required.add(account);}
   const allowed=new Map([[v.userBase,[v.baseMint,b.preDecimals,trader]],[v.poolBaseVault,[v.baseMint,b.preDecimals,v.pool]],[v.userQuote,[v.quoteMint,q.preDecimals,trader]],[v.poolQuoteVault,[v.quoteMint,q.preDecimals,v.pool]],...recipients.map(([a,o])=>[a,[v.quoteMint,q.preDecimals,o]])]);
   let baseFlow=0n,quoteFlow=0n;const feeAccounts=new Set(recipients.map(([a])=>a));
   for(const t of tx.filter(t=>same(t.associatedInvocation,v.identity))){const src=allowed.get(t.source),dst=allowed.get(t.destination);need(src&&dst&&src[0]===dst[0]&&src[1]===dst[1]&&t.declaredAuthority===src[2],'SCOPED_TRANSFER_UNPROVED');need(t.mint===null||t.mint===src[0]&&t.decimals===src[1],'SCOPED_TRANSFER_MINT_UNPROVED');const n=amount(t.amount);
    if(!v.event){const basePair=t.source===v.userBase&&t.destination===v.poolBaseVault||t.source===v.poolBaseVault&&t.destination===v.userBase,quotePair=t.source===v.userQuote&&(t.destination===v.poolQuoteVault||feeAccounts.has(t.destination))||t.source===v.poolQuoteVault&&(t.destination===v.userQuote||feeAccounts.has(t.destination));need(basePair||quotePair,'IDL_TRANSFER_ENDPOINT_UNPROVED');}
    if(t.source===v.userBase)baseFlow-=n;if(t.destination===v.userBase)baseFlow+=n;if(t.source===v.userQuote)quoteFlow-=n;if(t.destination===v.userQuote)quoteFlow+=n;add(t.source,-n);add(t.destination,n);}
   need(baseFlow!==0n&&quoteFlow!==0n&&(baseFlow>0n)!==(quoteFlow>0n)&&(baseFlow<0n)===(v.variant==='sell'),'SCOPED_LEG_DIRECTION_UNPROVED');
  }
  // Every owned state later contributing to net must be covered globally,
  // not only the accounts named in invocation roles. Missing flow is zero,
  // never permission to add an unexplained raw balance change to an action.
  for(const t of states)if(owners.has(t.preOwner)||owners.has(t.postOwner)){need(adapter.stable(t),'OWNED_TOKEN_TRANSITION_AMBIGUOUS','AMBIGUOUS_ROUTE');required.add(t.account);}
  for(const account of required){const t=byAccount.get(account);if(!t.lifecycle?.transient)need(amount(t.postAmount)-amount(t.preAmount)===(flows.get(account)??0n),'SCOPED_FLOW_RECONCILIATION_FAILED');}
  // Global shared-vault reconciliation above is never repeated per trader.
  for(const t of tx)if(!t.associatedInvocation&&(required.has(t.source)||required.has(t.destination))&&amount(t.amount)!==0n)throw{reason:'UNSCOPED_SHARED_TRANSFER',status:'AMBIGUOUS_ROUTE'};
  const transactionFees=fees(raw,ctx,ctx.keys[0]),actions=[];
  for(const trader of [...owners].sort()){
  const traderInv=inv.filter(v=>v.declaredTrader===trader);
  const owned=states.filter(t=>t.preOwner===trader||t.postOwner===trader);for(const t of owned)need(adapter.stable(t),'OWNED_TOKEN_TRANSITION_AMBIGUOUS','AMBIGUOUS_ROUTE');
  const touched=new Set(owned.map(t=>t.account));for(const t of tx)if(!t.associatedInvocation&&(touched.has(t.source)||touched.has(t.destination))&&amount(t.amount)!==0n)throw{reason:'UNSCOPED_OWNED_TRANSFER',status:'AMBIGUOUS_ROUTE'};
  const groups=new Map();for(const t of owned){const g=groups.get(t.preMint)??{mint:t.preMint,decimals:t.preDecimals,delta:0n,accounts:[],proof:'STABLE_OWNED_SPL_NET'};need(g.decimals===t.preDecimals,'MINT_DECIMALS_CONFLICT','DECIMALS_UNKNOWN');
   const delta=t.lifecycle?.transient?(flows.get(t.account)??0n):amount(t.postAmount)-amount(t.preAmount);g.delta+=delta;g.accounts.push(t.account);if(t.lifecycle?.transient)g.proof='SCOPED_TEMPORARY_SPL';groups.set(t.preMint,g);}
  const nonzero=[...groups.values()].filter(g=>g.delta!==0n),bases=nonzero.filter(g=>policy.cohortMints.includes(g.mint)),quotes=nonzero.filter(g=>policy.quoteMints.includes(g.mint)&&QUOTES.includes(g.mint));
  need(bases.length===1&&quotes.length===1&&nonzero.length===2,'ECONOMIC_MINT_AMBIGUITY','AMBIGUOUS_ROUTE');const base=bases[0],quote=quotes[0];need((base.delta>0n)!==(quote.delta>0n),'NONOPPOSITE_QUOTE','AMBIGUOUS_ROUTE');
  const f=trader===transactionFees.payer?{...transactionFees}:{total:'UNKNOWN',base:'UNKNOWN',priority:'UNKNOWN',payer:transactionFees.payer,traderCost:'UNKNOWN',splitStatus:'UNKNOWN',attributionStatus:'UNKNOWN',reason:'FEES_UNDETERMINED'};
  const status=f.attributionStatus==='UNKNOWN'?'AMBIGUOUS_ROUTE':f.splitStatus==='PROVEN'?'RECONSTRUCTED':'MISSING_LEG';
  const value=g=>({mint:g.mint,decimals:g.decimals,rawDelta:g.delta.toString(),accounts:g.accounts.sort(),proof:g.proof});
  const action={signature,trader,side:base.delta>0n?'BUY':'SELL',status,reason:status==='RECONSTRUCTED'?null:'FEES_UNDETERMINED',base:value(base),quote:value(quote),
   legs:traderInv.map(v=>({venue:PUMP,pool:v.pool,identity:v.identity,variant:v.variant,baseMint:v.baseMint,quoteMint:v.quoteMint,userBase:v.userBase,userQuote:v.userQuote,declaredTrader:v.declaredTrader,poolBaseVault:v.poolBaseVault,poolQuoteVault:v.poolQuoteVault,layoutApplicability:v.layoutApplicability,layoutProof:v.layoutProof,event:v.event,feeEvidence:v.event?{asset:v.quoteMint,lp:v.event.lpFee,protocol:v.event.protocolFee,creator:v.event.creatorFee,source:'EXACT_SAME_INVOCATION_EVENT_AND_TRANSFERS'}:{asset:v.quoteMint,lp:'UNKNOWN',protocol:tx.filter(t=>same(t.associatedInvocation,v.identity)&&t.destination===ctx.byPath.get(v.instructionAddress.join('.')).accounts[10]).reduce((n,t)=>n+amount(t.amount),0n).toString(),creator:tx.filter(t=>same(t.associatedInvocation,v.identity)&&t.destination===v.creatorAccount).reduce((n,t)=>n+amount(t.amount),0n).toString(),source:'EXACT_IDL_ROLE_SCOPED_TRANSFERS_LP_UNOBSERVED'}})),
   ownedAccounts:owned.map(t=>({...t,rawDelta:(t.lifecycle?.transient?(flows.get(t.account)??0n):amount(t.postAmount)-amount(t.preAmount)).toString()})),transfers:tx.filter(t=>t.associatedInvocation&&traderInv.some(v=>same(t.associatedInvocation,v.identity))),fees:f,tips:{tipsObservable:false,rawAmount:'UNKNOWN',reason:'TIP_IDENTITY_HISTORY_UNPROVED'},source:result.source};
  actions.push(action);}
  const status=actions.every(a=>a.status==='RECONSTRUCTED')?'RECONSTRUCTED':actions.some(a=>a.status==='AMBIGUOUS_ROUTE')?'AMBIGUOUS_ROUTE':'MISSING_LEG';
  result={...result,status,reason:status==='RECONSTRUCTED'?null:'FEES_UNDETERMINED',actions,reconstructedCount:actions.filter(a=>a.status==='RECONSTRUCTED').length,transactionFees:{...transactionFees,scope:'TRANSACTION_CONTEXT_NOT_PER_ACTION_EXPENSE'},feeExpenseAttribution:{payer:transactionFees.payer,actionTrader:actions.find(a=>a.trader===transactionFees.payer)?.trader??null,chargedActions:actions.filter(a=>a.fees.traderCost!=='UNKNOWN').length}};
 }catch(e){result.status=e.status??'MISSING_LEG';result.reason=e.reason??e.message??'INPUT_INVALID';}
 result.inputHashes={mapping:m?.factsHash??null,raw:h.fingerprint(VERSION+'-raw',raw),policy:h.fingerprint(VERSION+'-policy',policy),lineage:h.fingerprint(VERSION+'-lineage',lineage)};
 result.reductionHash=h.fingerprint(VERSION,result);return result;
}
module.exports={VERSION,DERIVATION_REVISION,reduceTransaction,fees};
