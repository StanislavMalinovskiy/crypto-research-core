'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { reduceTransaction } = require('../e2-trade-reducer-v1.cjs');
// Read-only stable mapper; no runner import and no external raw-history dependency.
const { mapHeliusTransaction } = require('C:/git/crypto-research/crypto-research-core/tools/research/r1/pumpswap-mapper-v2.cjs');
const { parse, digest } = require('../exploratory-probe.cjs');
const PUMP = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
const TOKEN = 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA';
const SYSTEM = '11111111111111111111111111111111';
const ATA = 'ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL';
const CU = 'ComputeBudget111111111111111111111111111111';
const WSOL = 'So11111111111111111111111111111111111111112';
const CHAIN = 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp';
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function b58(bytes) {
  let n = BigInt('0x' + bytes.toString('hex')), out = '';
  while (n) { out = alphabet[Number(n % 58n)] + out; n /= 58n; }
  for (const byte of bytes) { if (byte) break; out = '1' + out; }
  return out;
}
const addr = n => b58(Buffer.alloc(32, n));
const SIG = b58(Buffer.alloc(64, 7));
const canonical = value => Array.isArray(value) ? '[' + value.map(canonical).join(',') + ']' : value && typeof value === 'object'
  ? '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}' : JSON.stringify(value);
const sha = value => 'sha256:' + createHash('sha256').update(canonical(value)).digest('hex');
const documents = [
  { url: 'https://solana.com/docs/core/fees/fee-structure', contentHash: sha('fixture: fee documentation') },
  { url: 'https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/program-runtime/src/prioritization_fee.rs', contentHash: sha('fixture: prioritization source') },
  { url: 'https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/sdk/src/fee.rs', contentHash: sha('fixture: fee source') }
];
function fixture({ sell = false, temporary = false, sponsor = false, secondLeg = false } = {}) {
  const trader = addr(2), payer = sponsor ? addr(30) : trader, base = addr(4), quote = WSOL;
  const keys = sponsor ? [payer, trader] : [payer];
  const key = a => { if (!keys.includes(a)) keys.push(a); return keys.indexOf(a); };
  const instruction = (program, accounts, bytes, stackHeight) => ({ programIdIndex: key(program), accounts: accounts.map(key), data: b58(bytes), ...(stackHeight ? { stackHeight } : {}) });
  const price = Buffer.alloc(9); price[0] = 3; price.writeBigUInt64LE(1000001n, 1);
  const limit = Buffer.alloc(5); limit[0] = 2; limit.writeUInt32LE(3, 1);
  const accounts = Array.from({ length: sell ? 21 : 23 }, (_, n) => addr(n + 1));
  accounts[1] = trader; accounts[4] = quote; accounts[11] = accounts[12] = TOKEN;
  accounts[13] = SYSTEM; accounts[14] = ATA; accounts[16] = PUMP;
  // Exact declared IDL fee-account linkage, rather than arbitrary destinations.
  accounts[10] = addr(25); accounts[17] = addr(26);
  const swap = Buffer.alloc(sell ? 24 : 25);
  Buffer.from(sell ? '33e685a4017f83ad' : '66063d1201daebea', 'hex').copy(swap);
  const instructions = [instruction(CU, [], price), instruction(CU, [], limit)];
  if (temporary) {
    const init = Buffer.alloc(33); init[0] = 18; Buffer.alloc(32, 2).copy(init, 1);
    instructions.push(instruction(TOKEN, [accounts[6], quote], init));
  }
  const outer = instructions.length;
  instructions.push(instruction(PUMP, accounts, swap));
  const balances = [];
  const state = (account, mint, owner, pre, post, decimals = 9) => {
    const accountIndex = key(account);
    balances.push({ accountIndex, mint, owner, pre, post, decimals });
  };
  state(accounts[5], base, trader, '9007199254740993123', sell ? '9007199254740993120' : '9007199254740993126');
  if (!temporary) state(accounts[6], quote, trader, '100', sell ? '110' : '90');
  state(accounts[7], base, accounts[0], '100', sell ? '103' : '97');
  state(accounts[8], quote, accounts[0], '100', sell ? '88' : '109');
  const fees = [addr(25), addr(26)];
  const transfer = (source, destination, authority, amount) => {
    const data = Buffer.alloc(9); data[0] = 3; data.writeBigUInt64LE(BigInt(amount), 1);
    return instruction(TOKEN, [source, destination, authority], data, 2);
  };
  const inner = sell ? [transfer(accounts[8], accounts[6], accounts[0], '10'), transfer(accounts[8], fees[0], accounts[0], '1'), transfer(accounts[8], fees[1], accounts[0], '1')]
    : [transfer(accounts[6], accounts[8], trader, '9'), transfer(accounts[6], fees[0], trader, '1'), transfer(accounts[6], fees[1], trader, '0')];
  inner.push(sell ? transfer(accounts[5], accounts[7], trader, '3') : transfer(accounts[7], accounts[5], accounts[0], '3'));
  const innerGroups = [{ index: outer, instructions: inner }];
  for (const [n, f] of fees.entries()) state(f, quote, accounts[n === 0 ? 9 : 18], '0', sell || n === 0 ? '1' : '0');
  if (temporary) instructions.push(instruction(TOKEN, [accounts[6], trader, trader], Buffer.from([9])));
  if (secondLeg) {
    // Two real legs split the SAME aggregate 3 base / 10 quote movement.
    const other = [...accounts]; other[0] = addr(32); other[7] = addr(33); other[8] = addr(34);
    const secondOuter = instructions.length;
    instructions.push(instruction(PUMP, other, swap));
    inner[0] = sell ? transfer(accounts[8], accounts[6], accounts[0], '6') : transfer(accounts[6], accounts[8], trader, '5');
    inner[3] = sell ? transfer(accounts[5], accounts[7], trader, '2') : transfer(accounts[7], accounts[5], accounts[0], '2');
    balances.find(t => t.accountIndex === keys.indexOf(accounts[7])).post = sell ? '102' : '98';
    balances.find(t => t.accountIndex === keys.indexOf(accounts[8])).post = sell ? '92' : '105';
    state(other[7], base, other[0], '100', sell ? '101' : '99');
    state(other[8], quote, other[0], '100', sell ? '96' : '104');
    innerGroups.push({ index: secondOuter, instructions: sell
      ? [transfer(other[8], accounts[6], other[0], '4'), transfer(accounts[5], other[7], trader, '1')]
      : [transfer(accounts[6], other[8], trader, '4'), transfer(other[7], accounts[5], other[0], '1')] });
  }
  const h = { slot: 410195947, blockTime: 1775001600, version: 0,
    transaction: { signatures: sponsor ? [SIG, b58(Buffer.alloc(64, 8))] : [SIG], message: { header: { numRequiredSignatures: sponsor ? 2 : 1, numReadonlySignedAccounts: 0, numReadonlyUnsignedAccounts: 0 }, accountKeys: keys, instructions } },
    meta: { err: null, fee: sponsor ? '10004' : '5004', innerInstructions: innerGroups, loadedAddresses: { writable: [], readonly: [] },
      preTokenBalances: balances.map(t => ({ accountIndex: t.accountIndex, mint: t.mint, owner: t.owner, uiTokenAmount: { amount: t.pre, decimals: t.decimals } })),
      postTokenBalances: balances.map(t => ({ accountIndex: t.accountIndex, mint: t.mint, owner: t.owner, uiTokenAmount: { amount: t.post, decimals: t.decimals } })),
      preBalances: keys.map(() => '10000000'), postBalances: keys.map(() => '7000000') } };
  const policy = { version: 'fixture-policy-v1', chain: CHAIN, cohortMints: [base], quoteMints: [WSOL], venues: [PUMP],
    feeRegime: { version: 'solana-legacy-v0-explicit-cu-v1', documents },
    protocolFeeAccounts: [{ pool: accounts[0], account: fees[0], kind: 'PROTOCOL', mint: quote, roleIndex: 10 }, { pool: accounts[0], account: fees[1], kind: 'CREATOR', mint: quote, roleIndex: 17 }] };
  return { h, policy, trader, payer, base, quote, accounts, outer, fees };
}
function input(f = fixture(), { proveFees = true } = {}) {
  const bytes = Buffer.from(JSON.stringify(f.h)), rawHash = digest(bytes), raw = parse(bytes);
  const mapping = mapHeliusTransaction(bytes, rawHash);
  assert.notEqual(mapping.status, 'MAPPING_INVALID', 'real V2 fixture must map');
  const lineage = { source: 'SYNTHETIC_TEST_ONLY', rawHash, rawCrossCheck: 'NOT_CHECKED', knownAt: 'UNKNOWN' };
  if (proveFees) {
    // Fictional recorded bank context, not an assertion about April mainnet.
    const context = { chain: CHAIN, slot: String(f.h.slot), transactionRawHash: rawHash,
      runtimeVersion: 'synthetic-runtime-fixture', transactionVersion: String(f.h.version),
      lamportsPerSignature: '5000', maxComputeUnitLimit: '1400000', priorityRule: 'CEIL_PRICE_TIMES_REQUESTED_LIMIT',
      limitRule: 'EXPLICIT_UNCLAMPED', source: 'fixture://recorded-bank-context', sourceHash: sha('fixture: bank receipt') };
    lineage.feeRegimeEvidence = { context, contextHash: sha(context) };
  }
  return { mapping, raw, policy: structuredClone(f.policy), lineage };
}
const run = (f, options) => reduceTransaction(input(f, options));
const provisional = r => { assert.equal(r.status, 'MISSING_LEG'); assert.equal(r.reason, 'FEES_UNDETERMINED'); assert.equal(r.reconstructedCount, 0); assert.equal(r.actions.length, 1); };

test('owned net buy and sell yield exactly one economic action with exact raw units', () => {
  for (const sell of [false, true]) {
    const f = fixture({ sell }), r = run(f);
    assert.equal(r.status, 'RECONSTRUCTED'); assert.equal(r.actions.length, 1);
    assert.equal(r.actions[0].trader, f.trader); assert.equal(r.actions[0].side, sell ? 'SELL' : 'BUY');
    assert.equal(r.actions[0].base.rawDelta, sell ? '-3' : '3'); assert.equal(r.actions[0].quote.rawDelta, sell ? '10' : '-10');
    assert.equal(r.actions[0].base.decimals, 9); assert.equal(r.reconstructedCount, 1);
    assert.equal(r.d1Passed, false); assert.equal(r.cohortAdmitted, false);
  }
});
test('route legs and owned accounts deduplicate, zero-net intermediate has no volume', () => {
  const f = fixture({ secondLeg: true });
  const mid = addr(35), a = addr(36), b = addr(37);
  const pre = (account, amount) => ({ accountIndex: f.h.transaction.message.accountKeys.push(account) - 1, mint: mid, owner: f.trader, uiTokenAmount: { amount, decimals: 6 } });
  const x = pre(a, '15'), y = pre(b, '5'); f.h.meta.preTokenBalances.push(x, y);
  f.h.meta.postTokenBalances.push({ ...x, uiTokenAmount: { amount: '5', decimals: 6 } }, { ...y, uiTokenAmount: { amount: '15', decimals: 6 } });
  const data = Buffer.alloc(10); data[0] = 12; data.writeBigUInt64LE(10n, 1); data[9] = 6;
  const keys = f.h.transaction.message.accountKeys; keys.push(mid);
  f.h.meta.innerInstructions[0].instructions.push({ programIdIndex: keys.indexOf(TOKEN), accounts: [keys.indexOf(a), keys.indexOf(mid), keys.indexOf(b), keys.indexOf(f.trader)], data: b58(data), stackHeight: 2 });
  const i = input(f), r = reduceTransaction(i);
  assert.equal(r.status, 'RECONSTRUCTED'); assert.equal(r.actions.length, 1); assert.equal(r.actions[0].base.rawDelta, '3');
  assert.equal(r.actions[0].quote.rawDelta, '-10'); assert.equal(r.actions[0].legs.length, 2);
  assert.deepEqual(r.actions[0].intermediates, [{ mint: mid, decimals: 6, rawDelta: '0', accounts: [a, b].sort() }]);
  i.mapping.ownedDeltas.push(structuredClone(i.mapping.ownedDeltas[0]));
  assert.deepEqual(reduceTransaction(i).actions, r.actions);
});
test('temporary WSOL ATA proves actual initialization, scoped transfers, vault and protocol/creator fees', () => {
  for (const sell of [false, true]) {
    const f = fixture({ temporary: true, sell }), i = input(f), r = reduceTransaction(i);
    assert.equal(i.mapping.status, 'OFFLINE_MAPPING_PARTIAL');
    assert.equal(r.status, 'RECONSTRUCTED'); assert.equal(r.actions[0].quote.rawDelta, sell ? '10' : '-10');
    assert.equal(r.actions[0].quote.proof, 'SCOPED_TEMPORARY_SPL');
    assert.deepEqual(r.mapperDiagnostics, i.mapping.diagnostics);
  }
});
for (const [name, change] of [
  ['wrong mint', f => f.h.transaction.message.instructions[2].accounts[1] = f.h.transaction.message.accountKeys.indexOf(f.base)],
  ['wrong authority', f => f.h.meta.innerInstructions[0].instructions[0].accounts[2] = f.h.transaction.message.accountKeys.indexOf(PUMP)],
  ['missing initialization', f => f.h.transaction.message.instructions[2].data = b58(Buffer.from([17]))],
  ['unknown CPI path', f => delete f.h.meta.innerInstructions[0].instructions[0].stackHeight],
  ['vault mismatch', f => f.h.meta.postTokenBalances.find(t => t.accountIndex === f.h.transaction.message.accountKeys.indexOf(f.accounts[8])).uiTokenAmount.amount = '108'],
  ['unidentified fee transfer', f => f.policy.protocolFeeAccounts.pop()],
  ['late initialization', f => { const ins = f.h.transaction.message.instructions; [ins[2], ins[4]] = [ins[4], ins[2]]; }]
]) test('temporary quote rejects ' + name, () => {
  const f = fixture({ temporary: true }); change(f); const r = run(f);
  assert.notEqual(r.status, 'RECONSTRUCTED'); assert.equal(r.reconstructedCount, 0);
});
test('rent close tips and native transfers cannot replace missing SPL quote ownership', () => {
  const f = fixture({ temporary: true }); f.h.transaction.message.instructions[2].data = b58(Buffer.from([17]));
  const keys = f.h.transaction.message.accountKeys, key = a => { if (!keys.includes(a)) keys.push(a); return keys.indexOf(a); };
  const native = (source, destination, amount) => {
    const data = Buffer.alloc(12); data.writeUInt32LE(2); data.writeBigUInt64LE(BigInt(amount), 4);
    return { programIdIndex: key(SYSTEM), accounts: [source, destination].map(key), data: b58(data) };
  };
  // Actual SPL quote is20. Wrap20 and unrelated incoming20 cancel, while tip1
  // and unrelated outgoing9 leave nativeDelta+fee=-10. Rent create/close also
  // cannot substitute for missing actual token ownership.
  const create = Buffer.alloc(52); create.writeBigUInt64LE(2039280n, 4); create.writeBigUInt64LE(165n, 12); Buffer.from(decodeAddress(TOKEN)).copy(create, 20);
  f.h.transaction.message.instructions.splice(2, 0, { programIdIndex: key(SYSTEM), accounts: [f.trader, f.accounts[6]].map(key), data: b58(create) });
  f.h.meta.innerInstructions[0].index++;
  f.h.transaction.message.instructions.push(native(f.trader, f.accounts[6], '20'), native(addr(48), f.trader, '20'),
    native(f.trader, addr(49), '1'), native(f.trader, addr(50), '9'));
  const amount = Buffer.alloc(9); amount[0] = 3; amount.writeBigUInt64LE(19n, 1); f.h.meta.innerInstructions[0].instructions[0].data = b58(amount);
  f.h.meta.postTokenBalances.find(t => t.accountIndex === keys.indexOf(f.accounts[8])).uiTokenAmount.amount = '119';
  while (f.h.meta.preBalances.length < keys.length) { f.h.meta.preBalances.push('10000000'); f.h.meta.postBalances.push('10000000'); }
  f.h.meta.preBalances[0] = '10000000'; f.h.meta.postBalances[0] = '9994986';
  assert.equal(BigInt(f.h.meta.postBalances[0]) - BigInt(f.h.meta.preBalances[0]) + BigInt(f.h.meta.fee), -10n);
  const r = run(f); assert.equal(r.status, 'MISSING_LEG'); assert.equal(r.reconstructedCount, 0);
});
test('fee payer remains distinct from actual trader, sponsor cost is not silently attributed', () => {
  const f = fixture({ sponsor: true }), r = run(f);
  assert.equal(r.actions[0].trader, f.trader); assert.equal(r.actions[0].fees.payer, f.payer);
  assert.equal(r.actions[0].fees.traderCost, 'UNKNOWN'); assert.equal(r.status, 'AMBIGUOUS_ROUTE');
  assert.equal(r.reason, 'FEES_UNDETERMINED'); assert.equal(r.reconstructedCount, 0);
});
test('exact total and no historical split proof stays counted provisional FEES_UNDETERMINED', () => {
  const r = run(fixture(), { proveFees: false }); provisional(r);
  assert.equal(r.actions[0].fees.total, '5004'); assert.equal(r.actions[0].fees.base, 'UNKNOWN'); assert.equal(r.actions[0].fees.priority, 'UNKNOWN');
});
test('explicit fee equality uses CEIL, not truncation, with actual required signatures', () => {
  const r = run(fixture()), fees = r.actions[0].fees;
  assert.equal(fees.base, '5000'); assert.equal(fees.priority, '4'); assert.equal(fees.total, '5004'); assert.equal(fees.traderCost, '5004');
  const f = fixture(); f.h.transaction.signatures.push(b58(Buffer.alloc(64, 8))); f.h.transaction.message.header.numRequiredSignatures = 2; f.h.meta.fee = '10004';
  const q = run(f); assert.equal(q.status, 'RECONSTRUCTED'); assert.equal(q.actions[0].fees.base, '10000');
});
for (const [name, change, options] of [
  ['mismatch', f => f.h.meta.fee = '5003'],
  ['missing limit', f => f.h.transaction.message.instructions[1].data = b58(Buffer.from([1]))],
  ['duplicate price', f => f.h.transaction.message.instructions.push(structuredClone(f.h.transaction.message.instructions[0]))],
  ['signature mismatch', f => f.h.transaction.message.header.numRequiredSignatures = 2],
  ['precompile', f => { const keys = f.h.transaction.message.accountKeys; keys.push('Ed25519SigVerify111111111111111111111111111'); f.h.transaction.message.instructions.push({ programIdIndex: keys.length - 1, accounts: [], data: '1' }); }],
  ['v1 format', f => f.h.version = 1],
  ['absent version', f => delete f.h.version],
  ['unproved regime', () => {}, { proveFees: false }],
  ['missing total', f => delete f.h.meta.fee],
  ['implicit clamp', f => { const b = Buffer.alloc(5); b[0] = 2; b.writeUInt32LE(1400001, 1); f.h.transaction.message.instructions[1].data = b58(b); }]
]) test('fee split remains UNKNOWN for ' + name, () => {
  const f = fixture(); change(f); const r = run(f, options); provisional(r);
  assert.equal(r.actions[0].fees.base, 'UNKNOWN'); assert.equal(r.actions[0].fees.priority, 'UNKNOWN');
});
test('current formula flag or fee equation alone does not prove historical regime', () => {
  const i = input(fixture(), { proveFees: false }); i.policy.feeRegime.regimeApproved = true;
  provisional(reduceTransaction(i));
  const j = input(); j.lineage.feeRegimeEvidence.context.slot = '410195948'; provisional(reduceTransaction(j));
  const k = input(); k.policy.feeRegime.documents = []; provisional(reduceTransaction(k));
});
test('unknown tips preserve UNKNOWN with an explicit observability reason', () => {
  const r = run(fixture()); assert.equal(r.actions[0].tips.tipsObservable, false);
  assert.equal(r.actions[0].tips.rawAmount, 'UNKNOWN'); assert.equal(r.actions[0].tips.reason, 'TIP_IDENTITY_HISTORY_UNPROVED');
});
test('multiple actual traders or multiple cohort bases remain ambiguous', () => {
  const i = input(fixture({ secondLeg: true }));
  const v = i.mapping.invocations[1]; v.declaredTrader = addr(40); v.userBase = addr(41); v.userQuote = addr(42);
  for (const [account, mint, pre, post] of [[v.userBase, v.baseMint, '0', '3'], [v.userQuote, v.quoteMint, '10', '0']]) i.mapping.tokenStates.push({ signature: SIG, account, preMint: mint, postMint: mint, preOwner: addr(40), postOwner: addr(40), preAmount: pre, postAmount: post, preDecimals: 9, postDecimals: 9 });
  assert.equal(reduceTransaction(i).status, 'AMBIGUOUS_ROUTE');
  const j = input(); j.policy.cohortMints.push(addr(43));
  j.mapping.tokenStates.push({ signature: SIG, account: addr(44), preMint: addr(43), postMint: addr(43), preOwner: j.mapping.invocations[0].declaredTrader, postOwner: j.mapping.invocations[0].declaredTrader, preAmount: '0', postAmount: '3', preDecimals: 9, postDecimals: 9 });
  assert.equal(reduceTransaction(j).status, 'AMBIGUOUS_ROUTE');
});
test('immutable conflicting balances or owned-delta claims cannot become success', () => {
  const i = input(); i.mapping.tokenStates.push({ ...i.mapping.tokenStates[0], postAmount: '19' });
  assert.equal(reduceTransaction(i).status, 'AMBIGUOUS_ROUTE');
  const j = input(); j.mapping.ownedDeltas[0].ownedDelta = '999'; assert.equal(reduceTransaction(j).status, 'AMBIGUOUS_ROUTE');
});
test('failed unknown paths decimals and unsupported venue retain honest counted statuses', () => {
  const f = fixture(); f.h.meta.err = 'AccountInUse'; assert.equal(run(f).status, 'MISSING_LEG');
  const i = input(); i.mapping.pathStatus = 'PATH_UNKNOWN'; assert.equal(reduceTransaction(i).status, 'MISSING_LEG');
  const j = input(); j.mapping.tokenStates.find(t => t.account === j.mapping.invocations[0].userBase).postDecimals = null;
  assert.equal(reduceTransaction(j).status, 'DECIMALS_UNKNOWN');
  const k = input(); k.policy.venues = []; assert.equal(reduceTransaction(k).status, 'UNSUPPORTED_VENUE');
  const l = input(); l.mapping.invocations[0].reason = 'UNSUPPORTED_LAYOUT'; assert.equal(reduceTransaction(l).status, 'UNSUPPORTED_VENUE');
});
test('pure immutable reduction is deterministic under unordered fact permutation', () => {
  const i = input(fixture({ secondLeg: true })), original = structuredClone(i);
  const freeze = x => { if (x && typeof x === 'object') { Object.values(x).forEach(freeze); Object.freeze(x); } return x; };
  const a = reduceTransaction(freeze(i)); assert.deepEqual(i, original);
  const j = structuredClone(original); for (const k of ['tokenStates', 'ownedDeltas', 'invocations', 'transfers']) j.mapping[k].reverse();
  const b = reduceTransaction(j); assert.deepEqual(b, a);
  assert.match(a.reductionHash, /^sha256:[a-f0-9]{64}$/);
  const changed = structuredClone(original); changed.policy.version = 'fixture-policy-v2';
  assert.notEqual(reduceTransaction(changed).reductionHash, a.reductionHash);
});
test('source linkage mismatches and finite input/output capacities fail explicitly', () => {
  const i = input(); i.lineage.rawHash = 'sha256:' + '0'.repeat(64); assert.equal(reduceTransaction(i).reason, 'SOURCE_CONFLICT');
  const j = input(); j.raw.padding = 'x'.repeat(2000001); assert.equal(reduceTransaction(j).reason, 'INPUT_LIMIT');
  const k = input(); k.mapping.padding = 'x'.repeat(2000001); assert.equal(reduceTransaction(k).reason, 'INPUT_LIMIT');
  const l = input(); l.mapping.tokenStates[0].postAmount = '18446744073709551616'; assert.equal(reduceTransaction(l).reason, 'RAW_AMOUNT_INVALID');
});

test('raw account ownership and invocation roles cannot be replaced by correlated mapper claims', () => {
  const i = input(); const mint = i.mapping.invocations[0].baseMint;
  const t = i.mapping.tokenStates.find(t => t.preMint === mint && t.preOwner === i.mapping.invocations[0].declaredTrader);
  t.postAmount = '9007199254740993130'; i.mapping.ownedDeltas.find(d => d.mint === mint).ownedDelta = '7';
  assert.equal(reduceTransaction(i).status, 'AMBIGUOUS_ROUTE');
  const j = input(); j.mapping.invocations[0].pool = addr(45); j.mapping.invocations[0].vaultChecks.forEach(v => v.owner = addr(45));
  assert.equal(reduceTransaction(j).status, 'AMBIGUOUS_ROUTE');
});
test('temporary account rejects fee identity not linked to the actual declared layout', () => {
  const f = fixture({ temporary: true }); f.policy.protocolFeeAccounts[0].roleIndex = 9;
  assert.notEqual(run(f).status, 'RECONSTRUCTED');
});
test('stable quote missing scoped token-transfer proof is not promoted from a partial mapper', () => {
  const i = input(); i.mapping.transfers[0].reason = 'UNSUPPORTED_TRANSFER';
  assert.equal(reduceTransaction(i).status, 'MISSING_LEG');
});
test('malformed explicit compute-budget instruction keeps the economic action provisional', () => {
  const f = fixture(); f.h.transaction.message.instructions[1].data = '';
  const r = run(f); provisional(r);
});
test('lossless raw amounts reject Number money even when it would happen to be exact', () => {
  const i = input(); i.raw.meta.fee = 5004; const r = reduceTransaction(i);
  assert.notEqual(r.status, 'RECONSTRUCTED'); assert.equal(r.reconstructedCount, 0);
});
test('fee evidence requires explicit pinned historical context, not a self-attested flag', () => {
  for (const mutate of [i => i.lineage.feeRegimeEvidence = { regimeApproved: true },
    i => i.lineage.feeRegimeEvidence.context.sourceHash = 'UNKNOWN',
    i => i.lineage.feeRegimeEvidence.context.transactionRawHash = 'sha256:' + '0'.repeat(64),
    i => i.policy.feeRegime.documents[0].contentHash = 'UNKNOWN']) {
    const i = input(); mutate(i); provisional(reduceTransaction(i));
  }
});
test('result capacity reports an explicit terminal status and never silently truncates evidence', () => {
  const i = input();
  // Accepted mapping is under2MB, but duplicated leg metadata/diagnostics would
  // exceed output2MB. Literal fixture exceeds no input cap.
  i.mapping.diagnostics.push({ code: 'DECLARED_LAYOUT_UNVERIFIED', context: 'x'.repeat(1910000) });
  i.lineage.description = 'y'.repeat(95000); i.policy.description = 'z'.repeat(95000);
  const r = reduceTransaction(i); assert.equal(r.reason, 'OUTPUT_LIMIT'); assert.equal(r.reconstructedCount, 0);
});
test('no source clock randomness or filesystem calls are needed by reduction', () => {
  const i = input(), saved = { fetch: global.fetch, now: Date.now, random: Math.random };
  global.fetch = () => assert.fail('network forbidden'); Date.now = () => assert.fail('clock forbidden'); Math.random = () => assert.fail('random forbidden');
  try { assert.equal(reduceTransaction(i).status, 'RECONSTRUCTED'); }
  finally { global.fetch = saved.fetch; Date.now = saved.now; Math.random = saved.random; }
});
test('transfer raw conflicts and omitted actual transfers never enter an economic action', () => {
  const i = input(); i.mapping.transfers[0].amount = '99'; assert.equal(reduceTransaction(i).status, 'AMBIGUOUS_ROUTE');
  const j = input(); j.mapping.transfers.pop(); assert.equal(reduceTransaction(j).status, 'MISSING_LEG');
});
test('all supplied observed quote fees reconcile to actual fee-account deltas', () => {
  const f = fixture({ temporary: true });
  f.h.meta.postTokenBalances.find(t => t.accountIndex === f.h.transaction.message.accountKeys.indexOf(f.fees[0])).uiTokenAmount.amount = '2';
  assert.equal(run(f).status, 'MISSING_LEG');
});
test('two supported nonzero quotes stay ambiguous and owner disagreement never picks payer', () => {
  const f = fixture(), keys = f.h.transaction.message.accountKeys, account = addr(45), mint = 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v';
  const accountIndex = keys.push(account) - 1; f.policy.quoteMints.push(mint);
  f.h.meta.preTokenBalances.push({ accountIndex, mint, owner: f.trader, uiTokenAmount: { amount: '3', decimals: 6 } });
  f.h.meta.postTokenBalances.push({ accountIndex, mint, owner: f.trader, uiTokenAmount: { amount: '2', decimals: 6 } });
  assert.equal(run(f).status, 'AMBIGUOUS_ROUTE');
  const g = fixture(); for (const side of ['pre', 'post']) g.h.meta[side + 'TokenBalances'][0].owner = addr(46);
  assert.equal(run(g).status, 'AMBIGUOUS_ROUTE');
});
test('fee evidence object property order and all unordered fact arrays preserve result identity', () => {
  const i = input(), j = structuredClone(i);
  j.lineage.feeRegimeEvidence.context = Object.fromEntries(Object.entries(j.lineage.feeRegimeEvidence.context).reverse());
  j.mapping.diagnostics.reverse();
  assert.equal(reduceTransaction(i).status, 'RECONSTRUCTED'); assert.deepEqual(reduceTransaction(j), reduceTransaction(i));
});
test('all required raw owned-account observations survive into economic provenance', () => {
  const f = fixture(), r = run(f), observations = r.actions[0].ownedAccounts;
  assert.ok(Array.isArray(observations), 'raw owned-account observations must be retained');
  assert.equal(observations.find(t => t.account === f.accounts[5]).preAmount, '9007199254740993123');
  assert.equal(observations.find(t => t.account === f.accounts[5]).postAmount, '9007199254740993126');
  assert.equal(observations.find(t => t.account === f.accounts[5]).owner, f.trader);
});
function multiHop(temporary) {
  const f = fixture({ temporary }), mid = addr(35), userBase = addr(38), vaultBase = addr(33), vaultMid = addr(34), pool = addr(32);
  const keys = f.h.transaction.message.accountKeys, key = a => { if (!keys.includes(a)) keys.push(a); return keys.indexOf(a); };
  const first = f.h.transaction.message.instructions[f.outer]; first.accounts[3] = key(mid);
  for (const side of ['pre', 'post']) {
    const rows = f.h.meta[side + 'TokenBalances'];
    const user = rows.find(t => t.accountIndex === keys.indexOf(f.accounts[5])); user.mint = mid; user.uiTokenAmount.amount = '100';
    const vault = rows.find(t => t.accountIndex === keys.indexOf(f.accounts[7])); vault.mint = mid; vault.uiTokenAmount.amount = side === 'pre' ? '100' : '90';
  }
  const other = [...first.accounts]; other[0] = key(pool); other[3] = key(f.base); other[4] = key(mid);
  other[5] = key(userBase); other[6] = key(f.accounts[5]); other[7] = key(vaultBase); other[8] = key(vaultMid);
  other[10] = key(addr(53)); other[17] = key(addr(54));
  const secondOuter = f.outer + 1;
  f.h.transaction.message.instructions.splice(secondOuter, 0, { ...first, accounts: other });
  const state = (account, mint, owner, pre, post) => {
    const accountIndex = key(account);
    for (const [side, amount] of [['pre', pre], ['post', post]]) f.h.meta[side + 'TokenBalances'].push({ accountIndex, mint, owner, uiTokenAmount: { amount, decimals: 9 } });
  };
  state(userBase, f.base, f.trader, '100', '103'); state(vaultBase, f.base, pool, '100', '97'); state(vaultMid, mid, pool, '100', '110');
  state(addr(53), mid, keys[other[9]], '0', '0'); state(addr(54), mid, keys[other[18]], '0', '0');
  const transfer = (source, destination, authority, amount) => {
    const data = Buffer.alloc(9); data[0] = 3; data.writeBigUInt64LE(BigInt(amount), 1);
    return { programIdIndex: key(TOKEN), accounts: [source, destination, authority].map(key), data: b58(data), stackHeight: 2 };
  };
  f.h.meta.innerInstructions[0].instructions[3] = transfer(f.accounts[7], f.accounts[5], f.accounts[0], '10');
  f.h.meta.innerInstructions.push({ index: secondOuter, instructions: [transfer(f.accounts[5], vaultMid, f.trader, '10'), transfer(vaultBase, userBase, pool, '3')] });
  return { f, mid };
}
function decodeAddress(value) {
  let n = 0n; for (const c of value) n = n * 58n + BigInt(alphabet.indexOf(c));
  let hex = n.toString(16); if (hex.length % 2) hex = '0' + hex;
  return Buffer.from(hex.padStart(64, '0'), 'hex');
}
test('actual multi-hop base/intermediate/WSOL route emits one net action, with stable or temporary quote', () => {
  for (const temporary of [false, true]) {
    const { f, mid } = multiHop(temporary), r = run(f);
    assert.equal(r.status, 'RECONSTRUCTED', JSON.stringify({ temporary, reason: r.reason })); assert.equal(r.actions.length, 1);
    assert.equal(r.actions[0].base.rawDelta, '3'); assert.equal(r.actions[0].quote.rawDelta, '-10');
    assert.equal(r.actions[0].legs.length, 2);
    assert.equal(r.actions[0].intermediates.find(x => x.mint === mid).rawDelta, '0');
    assert.equal(r.actions[0].fees.total, '5004');
  }
});
test('temporary diagnostics preserve exact contents and canonical order after permutation', () => {
  const i = input(fixture({ temporary: true })), r = reduceTransaction(i), j = structuredClone(i);
  for (const name of ['diagnostics', 'ownedDeltas', 'transfers', 'tokenStates', 'invocations']) j.mapping[name].reverse();
  assert.deepEqual(reduceTransaction(j), r);
});
test('legacy explicit fee regime is supported without inferring absent format or defaults', () => {
  const f = fixture(); f.h.version = 'legacy'; assert.equal(run(f).status, 'RECONSTRUCTED');
});
test('all three signature precompiles forbid the bounded signature-fee proof', () => {
  for (const program of ['Ed25519SigVerify111111111111111111111111111', 'KeccakSecp256k11111111111111111111111111111', 'Secp256r1SigVerify1111111111111111111111111']) {
    const f = fixture(), keys = f.h.transaction.message.accountKeys;
    f.h.transaction.message.instructions.push({ programIdIndex: keys.push(program) - 1, accounts: [], data: '1' });
    provisional(run(f));
  }
});
test('conflicting pinned fee document interpretations stay provisional and ambiguous', () => {
  const i = input(); i.policy.feeRegime.documents.push({ ...i.policy.feeRegime.documents[0], contentHash: sha('different historical document') });
  const r = reduceTransaction(i); assert.equal(r.status, 'AMBIGUOUS_ROUTE'); assert.equal(r.reason, 'FEES_UNDETERMINED');
  assert.equal(r.actions[0].fees.base, 'UNKNOWN'); assert.equal(r.reconstructedCount, 0);
});
test('missing complete message header or readonly fee payer cannot prove the split', () => {
  for (const mutate of [f => delete f.h.transaction.message.header.numReadonlySignedAccounts,
    f => f.h.transaction.message.header.numReadonlySignedAccounts = 1]) {
    const f = fixture(); mutate(f); provisional(run(f));
  }
});
test('tip name/address list without actual historical account-identity proof never becomes zero', () => {
  const i = input(); i.policy.tipIdentitySnapshot = { chain: CHAIN, fromSlot: 410000000, toSlotExclusive: 411000000,
    accounts: [addr(49)], source: 'fixture://unproved-tip-list', contentHash: sha('tip address list') };
  const t = reduceTransaction(i).actions[0].tips;
  assert.equal(t.tipsObservable, false); assert.equal(t.rawAmount, 'UNKNOWN'); assert.equal(t.reason, 'TIP_ACCOUNT_OWNERSHIP_UNPROVED');
});
test('actual protocol/creator fee recipient ownership must agree with declared account linkage', () => {
  for (const index of [0, 1]) {
    const f = fixture({ temporary: true }), accountIndex = f.h.transaction.message.accountKeys.indexOf(f.fees[index]);
    for (const side of ['pre', 'post']) f.h.meta[side + 'TokenBalances'].find(t => t.accountIndex === accountIndex).owner = addr(27);
    assert.equal(run(f).status, 'MISSING_LEG');
  }
});
test('unrelated SPL quote movement cannot silently become economic trade volume', () => {
  const f = fixture(), keys = f.h.transaction.message.accountKeys, destination = addr(51), accountIndex = keys.push(destination) - 1;
  const data = Buffer.alloc(9); data[0] = 3; data.writeBigUInt64LE(10n, 1);
  f.h.transaction.message.instructions.push({ programIdIndex: keys.indexOf(TOKEN), accounts: [keys.indexOf(f.accounts[6]), accountIndex, keys.indexOf(f.trader)], data: b58(data) });
  for (const [side, amount] of [['pre', '0'], ['post', '10']]) f.h.meta[side + 'TokenBalances'].push({ accountIndex, mint: WSOL, owner: addr(52), uiTokenAmount: { amount, decimals: 9 } });
  f.h.meta.postTokenBalances.find(t => t.accountIndex === keys.indexOf(f.accounts[6])).uiTokenAmount.amount = '80';
  const r = run(f); assert.equal(r.status, 'AMBIGUOUS_ROUTE'); assert.equal(r.reconstructedCount, 0);
});
test('sponsored temporary rent refund does not change SPL trader identity or assign sponsor fees', () => {
  const f = fixture({ temporary: true, sponsor: true });
  f.h.transaction.message.instructions.at(-1).accounts[1] = f.h.transaction.message.accountKeys.indexOf(f.payer);
  const r = run(f); assert.equal(r.status, 'AMBIGUOUS_ROUTE'); assert.equal(r.reason, 'FEES_UNDETERMINED');
  assert.equal(r.actions[0].trader, f.trader); assert.equal(r.actions[0].quote.rawDelta, '-10');
  assert.equal(r.actions[0].fees.base, '10000'); assert.equal(r.actions[0].fees.traderCost, 'UNKNOWN');
});

// Independent review counterexamples: all source changes go through real V2.
for (const [name, mutate] of [
  ['quote vault delta', f => f.h.meta.postTokenBalances.find(t => t.accountIndex === f.h.transaction.message.accountKeys.indexOf(f.accounts[8])).uiTokenAmount.amount = '108'],
  ['protocol fee delta', f => f.h.meta.postTokenBalances.find(t => t.accountIndex === f.h.transaction.message.accountKeys.indexOf(f.fees[0])).uiTokenAmount.amount = '2'],
  ['base delta without base transfer', f => { f.h.meta.innerInstructions[0].instructions.pop(); f.h.meta.postTokenBalances.find(t => t.accountIndex === f.h.transaction.message.accountKeys.indexOf(f.accounts[5])).uiTokenAmount.amount = '9007199254740993223'; }],
  ['second leg vault delta', f => f.h.meta.postTokenBalances.find(t => t.accountIndex === f.h.transaction.message.accountKeys.indexOf(addr(34))).uiTokenAmount.amount = '105'],
  ['base authority', f => f.h.meta.innerInstructions[0].instructions[3].accounts[2] = f.h.transaction.message.accountKeys.indexOf(f.trader)]
]) test('full scoped economic reconciliation rejects ' + name, () => {
  const f = fixture({ secondLeg: name === 'second leg vault delta' }); mutate(f);
  const r = run(f); assert.equal(r.status, 'MISSING_LEG'); assert.equal(r.reconstructedCount, 0);
});
for (const opcode of [1, 4]) test('duplicate ancillary ComputeBudget opcode ' + opcode + ' leaves fees UNKNOWN', () => {
  const f = fixture(), data = Buffer.alloc(5); data[0] = opcode; data.writeUInt32LE(opcode === 1 ? 32768 : 65536, 1);
  const instruction = { programIdIndex: f.h.transaction.message.accountKeys.indexOf(CU), accounts: [], data: b58(data) };
  f.h.transaction.message.instructions.push(instruction, structuredClone(instruction));
  const r = run(f); provisional(r); assert.equal(r.actions[0].fees.splitStatus, 'UNKNOWN');
  assert.equal(r.actions[0].fees.base, 'UNKNOWN'); assert.equal(r.actions[0].fees.priority, 'UNKNOWN');
});
test('actual transfer paths and immutable record identities must be bijective', () => {
  const i = input();
  // Real V2 records first; replace only one supplied record with a second claim
  // of another actual path. Counts still match, so count equality is insufficient.
  i.mapping.transfers[3] = structuredClone(i.mapping.transfers[0]);
  i.mapping.transfers[3].identity.instructionAddress = [99];
  const r = reduceTransaction(i); assert.equal(r.status, 'AMBIGUOUS_ROUTE'); assert.equal(r.reconstructedCount, 0);
});

test('coherent multi-hop route still requires the second leg actual base transfer', () => {
  for (const temporary of [false, true]) {
    const { f } = multiHop(temporary); f.h.meta.innerInstructions[1].instructions.pop();
    const r = run(f); assert.equal(r.status, 'MISSING_LEG'); assert.equal(r.reconstructedCount, 0);
  }
});

for (const [name, mutate] of [
  ['protocol relabelled CREATOR role17', f => Object.assign(f.policy.protocolFeeAccounts[0], { kind: 'CREATOR', roleIndex: 17 })],
  ['valid plus contradictory recipient kind', f => f.policy.protocolFeeAccounts.push({ ...f.policy.protocolFeeAccounts[0], kind: 'CREATOR', roleIndex: 17 })],
  ['valid plus contradictory recipient mint', f => f.policy.protocolFeeAccounts.push({ ...f.policy.protocolFeeAccounts[0], mint: f.base })],
  ['unknown recipient kind', f => f.policy.protocolFeeAccounts[0].kind = 'UNKNOWN'],
  ['duplicate recipient claim', f => f.policy.protocolFeeAccounts.push(structuredClone(f.policy.protocolFeeAccounts[0]))],
  ['missing transferred recipient claim', f => f.policy.protocolFeeAccounts.shift()],
  ['extra false recipient for actual pool', f => f.policy.protocolFeeAccounts.push({ ...f.policy.protocolFeeAccounts[0], account: addr(55) })]
]) test('all relevant fee recipient policy claims must agree: ' + name, () => {
  const f = fixture(); mutate(f);
  const r = run(f); assert.equal(r.status, 'MISSING_LEG'); assert.equal(r.reconstructedCount, 0);
});
