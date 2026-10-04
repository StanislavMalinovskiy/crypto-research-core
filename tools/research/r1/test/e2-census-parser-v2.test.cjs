'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs');
const legacy = require('../e2-census.cjs');
const target = '../e2-census-parser-v2.cjs';
const v2 = fs.existsSync(require('node:path').join(__dirname, target)) ? require(target) : null;
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function encode(b) { let n = BigInt('0x' + b.toString('hex')), s = '', z = 0; while (b[z] === 0) z++;
  while (n) { s = alphabet[Number(n % 58n)] + s; n /= 58n; } return '1'.repeat(z) + s; }
const key = n => encode(Buffer.alloc(32, n)), sig = n => encode(Buffer.alloc(64, n));
const data = () => { const b = Buffer.alloc(60); Buffer.from('e992d18ecf6840bc', 'hex').copy(b); return encode(b); };
function block(index = 2, paths = [[0]], slot = 410195947) { return {
  header: { number: slot, parentNumber: slot - 1, hash: key(1), parentHash: key(2), timestamp: 1775001600 },
  transactions: [{ transactionIndex: index, signatures: [sig(1)], err: null }],
  instructions: paths.map(p => ({ transactionIndex: index, instructionAddress: p, programId: legacy.CONFIG.program,
    accounts: Array.from({ length: 18 }, (_, i) => key(i + 3)), data: data(), isCommitted: true, error: null })) }; }
const raw = (...b) => Buffer.from(b.map(x => JSON.stringify(x)).join('\n') + '\n');
// Before v2 exists, execute the unchanged historical admission behavior as the contradiction oracle.
function subject(options) { if (v2) return v2.createParser(options);
  return { admit: bytes => legacy.admit(bytes, legacy.query('data', 0, 410195947,
    [410195947, 416000000, 423000000, 429000000]), [410195947, 416000000, 423000000, 429000000]) }; }
test('signature maps to only one index across pages', () => { const p = subject();
  assert.equal(p.admit(raw(block())).code, null);
  assert.equal(p.admit(raw(block(3))).code, 'IMMUTABLE_CONFLICT'); });
test('signature maps to only one slot across pages', () => { const p = subject();
  assert.equal(p.admit(raw(block())).code, null);
  assert.equal(p.admit(raw(block(2, [[1]], 410195949))).code, 'IMMUTABLE_CONFLICT'); });
test('same signature and path contents conflict across pages', () => { const p = subject();
  assert.equal(p.admit(raw(block())).code, null); const b = block(); b.instructions[0].accounts[0] = key(30);
  assert.equal(p.admit(raw(b)).code, 'IMMUTABLE_CONFLICT'); });
test('layout ceiling rejects oversized instruction before decoding', () => { const p = subject(); const b = block();
  b.instructions[0].data = encode(Buffer.concat([Buffer.from('e992d18ecf6840bc', 'hex'), Buffer.alloc(100)]));
  assert.equal(p.admit(raw(b)).code, 'INSTRUCTION_DATA_LIMIT'); });
test('valid multiple paths and equal page retry retain deterministic facts', () => { const p = subject();
  const b = raw(block(2, [[10], [2], [2, 0]])); const first = p.admit(b); assert.equal(first.code, null);
  assert.deepEqual(first.creations.map(c => c.instructionPath), [[2], [2, 0], [10]]);
  const before = p.snapshot(); assert.equal(p.admit(b).code, null); assert.deepEqual(p.snapshot(), before); });
test('transaction content conflict even on another instruction path is rejected', () => { const p = subject();
  p.admit(raw(block())); const b = block(2, [[1]]); b.transactions[0].err = 'AccountInUse';
  assert.equal(p.admit(raw(b)).code, 'IMMUTABLE_CONFLICT'); });
test('whole transition limits reject without partial state', () => { for (const limits of [
  { creations: 1 }, { stateBytes: 50 }, { diagnostics: 1 }, { instructions: 1 }, { signatures: 1 }, { pages: 1 }]) {
  const p = subject({ limits }); const before = p.snapshot(); const b = block(2, [[0], [0, 1]]);
  if (limits.signatures) b.transactions.push({ transactionIndex: 3, signatures: [sig(2)], err: null });
  if (limits.diagnostics) { b.instructions[0].error = {}; b.instructions[1].isCommitted = false; }
  if (limits.pages) { assert.equal(p.admit(raw(block())).code, null); const stable = p.snapshot();
    assert.notEqual(p.admit(raw(block(2, [[1]]))).code, null); assert.deepEqual(p.snapshot(), stable); }
  else { assert.notEqual(p.admit(raw(b)).code, null); assert.deepEqual(p.snapshot(), before); }
} });
test('one raw page digest is passed to all creations', () => { let hashes = 0;
  const p = subject({ pageDigest: b => { hashes++; return v2.digest(b); } }); const bytes = raw(block(2, [[0], [1], [2]]));
  const r = p.admit(bytes); assert.equal(r.code, null); assert.equal(hashes, 1);
  assert.ok(r.creations.every(c => c.rawHash === v2.digest(bytes))); });
test('decoded ceiling and bool grammar reject unsupported forms without admission', () => {
  for (const length of [59, 61]) { const b = block(); const bytes = Buffer.alloc(length);
    Buffer.from('e992d18ecf6840bc', 'hex').copy(bytes); b.instructions[0].data = encode(bytes);
    const r = subject().admit(raw(b)); assert.equal(r.creations.length, 0); assert.notEqual(r.code, null); }
  const b = block(), bytes = Buffer.alloc(60); Buffer.from('e992d18ecf6840bc', 'hex').copy(bytes); bytes[59] = 2;
  b.instructions[0].data = encode(bytes); const r = subject().admit(raw(b)); assert.equal(r.code, null);
  assert.equal(r.counts.unknown, 1); assert.equal(r.creations.length, 0);
});
test('pinned layout derivation is exact and expressly not deployment proof', () => {
  assert.equal(v2.LAYOUT.decodedBytes, 60); assert.equal(v2.LAYOUT.encodedCharacters, 82);
  assert.deepEqual(v2.LAYOUT.fields.map(f => f.bytes), [8, 2, 8, 8, 32, 1, 1]);
  assert.equal(v2.LAYOUT.revision, '82dacacf15ca93dc0444ab38714f2226210a0a3d');
  assert.equal(v2.LAYOUT.deployment, 'UNVERIFIED');
});
test('exact JSON tokens duplicate fields numeric paths and total ordering', () => {
  const bytes = Buffer.from('{"amount":18446744073709551615,"path":[2,10]}');
  assert.equal(v2.stringifyExact(v2.parseExact(bytes)), bytes.toString());
  assert.throws(() => v2.parseExact(Buffer.from('{"a":1,"\\u0061":2}')), /RESPONSE_INVALID/);
  for (const token of ['2.0', '2e0', '"2"', '-0']) { const b = raw(block()).toString().replace('"transactionIndex":2', '"transactionIndex":' + token);
    assert.notEqual(subject().admit(Buffer.from(b)).code, null); }
  const p = subject(), b = block(); b.transactions[0].amount = 'placeholder';
  const exact = Buffer.from(raw(b).toString().replace('"placeholder"', '18446744073709551615'));
  assert.equal(p.admit(exact).code, null); assert.ok(JSON.stringify(p.snapshot()).includes('18446744073709551615'));
});
test('time row byte and nesting bounds are finite and atomic', () => {
  let now = 0; const p = subject({ now: () => now, limits: { timeMs: 1 } }); const before = p.snapshot(); now = 2;
  assert.equal(p.admit(raw(block())).code, 'TIME_LIMIT'); assert.deepEqual(p.snapshot(), before);
  assert.notEqual(subject({ limits: { pageBytes: 10 } }).admit(raw(block())).code, null);
  assert.notEqual(subject({ limits: { pageRows: 1 } }).admit(raw(block(), block(3))).code, null);
  assert.throws(() => v2.parseExact(Buffer.from('['.repeat(100) + '0' + ']'.repeat(100))), /INPUT_LIMIT/);
});
test('one transaction position cannot change signature or immutable contents', () => {
  const p = subject(); assert.equal(p.admit(raw(block())).code, null); const before = p.snapshot();
  const b = block(2, [[1]]); b.transactions[0].signatures = [sig(2)];
  assert.equal(p.admit(raw(b)).code, 'IMMUTABLE_CONFLICT'); assert.deepEqual(p.snapshot(), before);
});
test('header ancestry and block order contradictions reject atomically', () => {
  const p = subject(); const a = block(), b = block(3, [[1]], 410195948); b.transactions[0].signatures = [sig(2)];
  b.header.parentHash = key(30); const before = p.snapshot();
  assert.equal(p.admit(raw(a, b)).code, 'IMMUTABLE_CONFLICT'); assert.deepEqual(p.snapshot(), before);
  assert.notEqual(subject().admit(raw(b, a)).code, null);
});
module.exports = { block, raw, encode, key, sig };
for (const order of ['parent-first', 'child-first']) {
  test('cross-page ancestry rejects contradictory hash atomically ' + order, () => {
    const parent = block(), child = block(3, [[1]], 410195948);
    child.transactions[0].signatures = [sig(2)]; child.header.parentHash = key(30);
    const pages = order === 'parent-first' ? [parent, child] : [child, parent];
    const p = subject(); assert.equal(p.admit(raw(pages[0])).code, null); const before = p.snapshot();
    const rejected = p.admit(raw(pages[1])); assert.equal(rejected.code, 'IMMUTABLE_CONFLICT');
    assert.deepEqual(rejected.creations, []); assert.deepEqual(p.snapshot(), before);
  });
  test('cross-page ancestry accepts consistent hash ' + order, () => {
    const parent = block(), child = block(3, [[1]], 410195948);
    child.transactions[0].signatures = [sig(2)]; child.header.parentHash = parent.header.hash;
    const pages = order === 'parent-first' ? [parent, child] : [child, parent]; const p = subject();
    for (const page of pages) assert.equal(p.admit(raw(page)).code, null);
    assert.equal(p.snapshot().creations.length, 2);
  });
}
for (const field of ['pool', 'instructionPath']) {
  test('returned creation mutation cannot change snapshot or equal retry ' + field, () => {
    const p = subject(), bytes = raw(block(2, [[0, 1]])); const admitted = p.admit(bytes);
    assert.equal(admitted.code, null); const before = p.snapshot();
    if (field === 'pool') admitted.creations[0].pool = key(30);
    else admitted.creations[0].instructionPath[0] = 99;
    assert.deepEqual(p.snapshot(), before);
    const retry = p.admit(bytes); assert.equal(retry.code, null); assert.deepEqual(p.snapshot(), before);
    assert.equal(retry.creations[0].pool, before.creations[0].pool);
    assert.deepEqual(retry.creations[0].instructionPath, before.creations[0].instructionPath);
    retry.creations[0].pool = key(31); retry.creations[0].instructionPath.push(99);
    assert.deepEqual(p.snapshot(), before);
  });
}
