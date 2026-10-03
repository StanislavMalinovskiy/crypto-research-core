'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const { mapBlock } = require('../pumpswap-mapper.cjs'), { digest } = require('../exploratory-probe.cjs');
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function b58(bytes) { let n = BigInt('0x' + bytes.toString('hex')), s = ''; while (n) { s = alphabet[Number(n % 58n)] + s; n /= 58n; }
  for (const byte of bytes) { if (byte) break; s = '1' + s; } return s; }
const address = n => b58(Buffer.alloc(32, n)), signature = b58(Buffer.alloc(64, 7));
const P = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', T = 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA';
const S = '11111111111111111111111111111111', A = 'ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL';
function fixture(variant = 'sell') {
  const data = Buffer.alloc(variant === 'sell' ? 24 : 25); Buffer.from({ sell: '33e685a4017f83ad', buy: '66063d1201daebea', buy_exact_quote_in: 'c62e1552b4d9e870' }[variant], 'hex').copy(data);
  const accounts = Array.from({ length: variant === 'sell' ? 21 : 23 }, (_, n) => address(n + 1));
  accounts[11] = accounts[12] = T; accounts[13] = S; accounts[14] = A; accounts[16] = P;
  const token = (account, mint, owner, pre, post) => ({ transactionIndex: 5, account, preMint: mint, postMint: mint,
    preOwner: owner, postOwner: owner, preAmount: pre, postAmount: post, preDecimals: 9, postDecimals: 9 });
  return { header: { number: 410195947, parentNumber: 410195946, hash: address(28), parentHash: address(29), timestamp: 1775001600 },
    transactions: [{ transactionIndex: 5, signatures: [signature], err: null }],
    instructions: [{ transactionIndex: 5, instructionAddress: [4], programId: P, accounts, data: b58(data), isCommitted: true, error: null }],
    tokenBalances: [token(accounts[5], accounts[3], accounts[1], '9007199254740993123', '9007199254740993120'),
      token(accounts[6], accounts[4], accounts[1], '1', '18446744073709551615'),
      token(accounts[7], accounts[3], accounts[0], '0', '3'), token(accounts[8], accounts[4], accounts[0], '10', '9')], balances: [] };
}
const bytes = b => Buffer.from(JSON.stringify(b) + '\n'), run = b => { const raw = bytes(b); return mapBlock(raw, digest(raw)); };
const diagnostic = (r, code) => r.diagnostics.some(d => d.code === code);
function transfer(b, tag = 3, path = [4, 0]) {
  const a = b.instructions[0].accounts, data = Buffer.alloc(tag === 3 ? 9 : 10); data[0] = tag; data.writeBigUInt64LE(9007199254740993123n, 1); if (tag === 12) data[9] = 9;
  return { transactionIndex: 5, instructionAddress: path, programId: T, accounts: tag === 3 ? [a[5], a[7], a[1]] : [a[5], a[3], a[7], a[1]],
    data: b58(data), isCommitted: true, error: null };
}
test('three pinned variants retain declared roles both asset sides and unverified layout', () => {
  for (const variant of ['buy', 'buy_exact_quote_in', 'sell']) {
    const b = fixture(variant), r = run(b); assert.equal(r.status, 'OFFLINE_MAPPING_COMPLETE');
    const invocation = r.invocations[0], a = b.instructions[0].accounts;
    assert.equal(invocation.variant, variant); assert.equal(invocation.declaredTrader, a[1]); assert.equal(invocation.pool, a[0]);
    assert.equal(invocation.baseMint, a[3]); assert.equal(invocation.quoteMint, a[4]); assert.equal(invocation.poolBaseVault, a[7]);
    assert.equal(invocation.poolQuoteVault, a[8]); assert.equal(invocation.layoutApplicability, 'DECLARED_UNVERIFIED');
    assert.equal(invocation.identity.chain, 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp'); assert.equal(invocation.identity.signature, signature); assert.deepEqual(invocation.identity.instructionAddress, [4]);
    assert.ok(diagnostic(r, 'DECLARED_LAYOUT_UNVERIFIED')); assert.equal(r.classification, 'OFFLINE_DEVELOPMENT');
    for (const key of ['d1Evidence', 'd1Passed', 'runAuthorized']) assert.equal(r[key], false);
    assert.equal(r.configuration.sources.pumpIdl.sha256, '5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6');
  }
});
test('owned deltas are exact signed transaction facts and ambiguous state never zero fills', () => {
  const b = fixture(), r = run(b); assert.equal(r.status, 'OFFLINE_MAPPING_COMPLETE');
  assert.equal(r.ownedDeltas.find(x => x.mint === b.instructions[0].accounts[3]).ownedDelta, '-3');
  assert.equal(r.ownedDeltas.find(x => x.mint === b.instructions[0].accounts[4]).ownedDelta, '18446744073709551614');
  for (const mutate of [t => t.preAmount = null, t => t.preOwner = null, t => t.postOwner = address(31),
    t => t.postMint = address(31), t => t.postDecimals = 8]) {
    const x = fixture(); mutate(x.tokenBalances[0]); const q = run(x); assert.equal(q.status, 'OFFLINE_MAPPING_PARTIAL');
    assert.equal(q.ownedDeltas.find(d => d.mint === x.instructions[0].accounts[3]).ownedDelta, null);
    assert.ok(diagnostic(q, 'AMBIGUOUS_TOKEN_TRANSITION'));
  }
  const x = fixture(); x.tokenBalances.pop(); assert.ok(diagnostic(run(x), 'MISSING_VAULT_STATE'));
  const y = fixture(); y.tokenBalances[3].postOwner = address(31); assert.ok(diagnostic(run(y), 'VAULT_STATE_MISMATCH'));
});
test('classic transfers retain exact facts and proper descendant association only', () => {
  const b = fixture(); b.instructions.push(transfer(b), transfer(b, 12, [4, 1]), transfer(b, 3, [5]));
  const r = run(b); assert.equal(r.status, 'OFFLINE_MAPPING_COMPLETE'); assert.equal(r.transfers.length, 3);
  assert.equal(r.transfers[0].amount, '9007199254740993123'); assert.deepEqual(r.transfers[0].associatedInvocation.instructionAddress, [4]);
  assert.equal(r.transfers[1].decimals, 9); assert.equal(r.transfers[2].associatedInvocation, null);
  const other = { ...b.instructions[0], instructionAddress: [4, 0] }; b.instructions.push(other); b.instructions[1].instructionAddress = [4, 0, 0];
  const q = run(b); assert.equal(q.transfers.find(t => t.identity.instructionAddress.length === 3).associatedInvocation, null);
  assert.ok(diagnostic(q, 'AMBIGUOUS_TRANSFER_ASSOCIATION'));
  const x = fixture(); x.instructions.push({ ...transfer(x), accounts: [...transfer(x).accounts, address(30)] });
  assert.ok(diagnostic(run(x), 'UNSUPPORTED_TRANSFER'));
});
test('failed uncommitted unknown and unsupported layouts remain explicit invocation records', () => {
  for (const [mutate, code] of [[b => b.transactions[0].err = 'synthetic', 'FAILED_TRANSACTION'],
    [b => b.instructions[0].error = 'synthetic', 'FAILED_INSTRUCTION'], [b => b.instructions[0].isCommitted = false, 'UNCOMMITTED_INSTRUCTION'],
    [b => b.instructions[0].data = b58(Buffer.alloc(24, 1)), 'UNSUPPORTED_VARIANT'],
    [b => b.instructions[0].accounts.pop(), 'UNSUPPORTED_LAYOUT'], [b => b.instructions[0].accounts[11] = address(30), 'UNSUPPORTED_TOKEN_PROGRAM'],
    [b => b.instructions[0].data = '0', 'UNSUPPORTED_DATA'], [b => b.instructions[0].data = '1'.repeat(611), 'UNSUPPORTED_DATA']]) {
    const b = fixture(); mutate(b); const r = run(b); assert.equal(r.status, 'OFFLINE_MAPPING_PARTIAL');
    assert.equal(r.invocations.length, 1); assert.ok(diagnostic(r, code)); assert.equal(r.invocations[0].reason, code);
  }
  const b = fixture(); b.transactions[0].signatures = []; assert.ok(diagnostic(run(b), 'AMBIGUOUS_SIGNATURE'));
});
test('equal immutable duplicates deduplicate and conflicts invalidate without partial facts', () => {
  const b = fixture(), original = run(b); assert.equal(original.status, 'OFFLINE_MAPPING_COMPLETE');
  b.transactions.push(structuredClone(b.transactions[0])); b.instructions.push(structuredClone(b.instructions[0])); b.tokenBalances.push(structuredClone(b.tokenBalances[0]));
  const r = run(b); assert.equal(r.factsHash, original.factsHash); assert.equal(r.invocations.length, 1);
  for (const mutate of [x => x.transactions[1].err = 'conflict', x => x.instructions[1].data = b58(Buffer.alloc(24, 1)),
    x => x.tokenBalances.at(-1).postAmount = '4']) {
    const x = structuredClone(b); mutate(x); for (const order of [x, { ...x, transactions: [...x.transactions].reverse(), instructions: [...x.instructions].reverse(), tokenBalances: [...x.tokenBalances].reverse() }]) {
      const q = run(order); assert.equal(q.status, 'MAPPING_INVALID'); assert.equal(q.code, 'IMMUTABLE_CONFLICT'); assert.equal(q.invocations, undefined);
    }
  }
});
test('semantic ordering preserves facts hash while raw bytes change lineage', () => {
  const b = fixture(); b.instructions.push(transfer(b, 3, [4, 10]), transfer(b, 12, [4, 2]));
  const r = run(b); assert.equal(r.status, 'OFFLINE_MAPPING_COMPLETE'); const x = structuredClone(b);
  x.instructions.reverse(); x.tokenBalances.reverse(); const q = run(x);
  assert.equal(q.factsHash, r.factsHash); assert.notEqual(q.rawHash, r.rawHash); assert.notEqual(q.reportHash, r.reportHash);
  assert.deepEqual(q.transfers.map(t => t.identity.instructionAddress), [[4, 2], [4, 10]]);
});
test('hash time shape and row limits reject safely and pure mapper performs no I/O', () => {
  const raw = bytes(fixture()); assert.equal(mapBlock(raw, 'sha256:' + '0'.repeat(64)).code, 'HASH_MISMATCH');
  for (const [mutate, code] of [[b => b.header.timestamp = 1788134400, 'INPUT_INVALID'], [b => b.instructions[0].instructionAddress = [], 'INPUT_INVALID'],
    [b => b.instructions[0].accounts[0] = '0', 'INPUT_INVALID'], [b => b.transactions = Array(1001).fill(b.transactions[0]), 'INPUT_LIMIT'],
    [b => b.tokenBalances[0].preAmount = '18446744073709551616', 'INPUT_INVALID']]) { const b = fixture(); mutate(b); assert.equal(run(b).code, code); }
  const large = Buffer.alloc(2000001); assert.equal(mapBlock(large, digest(large)).code, 'INPUT_LIMIT');
  const two = Buffer.concat([raw, raw]); assert.equal(mapBlock(two, digest(two)).code, 'INPUT_INVALID');
  const original = global.fetch; global.fetch = () => assert.fail('network forbidden');
  try { assert.equal(run(fixture()).status, 'OFFLINE_MAPPING_COMPLETE'); } finally { global.fetch = original; }
});
test('unlinked same-account different-state rows retain null linkage and deterministic facts under permutation', () => {
  const b = fixture(), first = { ...b.tokenBalances[0], transactionIndex: 50, preOwner: null, postOwner: null, postAmount: '4' },
    second = { ...first, transactionIndex: 51, postAmount: '8' };
  b.tokenBalances.push(first, second); const r = run(b), reversed = { ...b, tokenBalances: [...b.tokenBalances].reverse() }, q = run(reversed);
  assert.equal(r.status, 'OFFLINE_MAPPING_PARTIAL'); assert.equal(q.status, 'OFFLINE_MAPPING_PARTIAL');
  assert.equal(q.factsHash, r.factsHash); assert.deepEqual(q.tokenStates, r.tokenStates);
  const ambiguous = r.tokenStates.filter(t => t.signature === null);
  assert.equal(ambiguous.length, 2); assert.deepEqual(ambiguous.map(t => t.postAmount).sort(), ['4', '8']);
  for (const t of ambiguous) { assert.equal(t.account, first.account); assert.equal(t.preOwner, null); assert.equal(t.postOwner, null);
    assert.equal(t.preAmount, first.preAmount); assert.equal(t.preMint, first.preMint); assert.equal(t.postMint, first.postMint); }
  assert.ok(diagnostic(r, 'AMBIGUOUS_SIGNATURE')); assert.notEqual(q.rawHash, r.rawHash); assert.notEqual(q.reportHash, r.reportHash);
});
