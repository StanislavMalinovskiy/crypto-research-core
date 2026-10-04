'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const api = require('../e2-synthetic-selection-budget.cjs');
const D = ['credits', 'pages', 'requests', 'timeMs', 'receivedBytes', 'retainedBytes'];
const charge = n => Object.fromEntries(D.map(d => [d, n]));
const mint = n => { const b = Buffer.alloc(32); b.writeUInt32BE(n, 28); return b; };
function opening() { return api.createSyntheticBudget({ mode: 'SYNTHETIC_ONLY', ledgerId: 'synthetic-ledger', policy: { policyId: 'explicit-caps', perToken: charge(1), aggregate: charge(10) }, openingCharges: { tokens: [], aggregate: charge(0) } }); }
test('exact decoded mint bytes SHA256 does not hash hex text or add seed framing', () => {
  assert.equal(api.hashMintBytes(Buffer.alloc(32)).toString('hex'), '66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925');
});
test('each per-token cap rejects a distinct retry atomically despite aggregate room', () => {
  const a = opening();
  const b = api.reserveSyntheticBudget({ ledger: a, expectedHeadHash: a.headHash, mintBytes: mint(1), attemptId: 'first', cost: charge(1) });
  assert.throws(() => api.reserveSyntheticBudget({ ledger: b, expectedHeadHash: b.headHash, mintBytes: mint(1), attemptId: 'retry', cost: charge(1) }), /PER_TOKEN_CAP/);
});
test('same attempt identity with changed immutable mint is conflict not free idempotence', () => {
  const a = opening();
  const b = api.reserveSyntheticBudget({ ledger: a, expectedHeadHash: a.headHash, mintBytes: mint(1), attemptId: 'first', cost: charge(1) });
  assert.throws(() => api.reserveSyntheticBudget({ ledger: b, expectedHeadHash: b.headHash, mintBytes: mint(2), attemptId: 'first', cost: charge(1) }), /ATTEMPT_CONFLICT/);
});

const select = records => api.selectSynthetic({ mode: 'SYNTHETIC_ONLY', records, eligibilityPolicyId: 'future-owner-policy', eligibilitySetHash: 'a'.repeat(64) });
const records = (n = 100) => [0, 1, 2].flatMap(s => Array.from({ length: n }, (_, i) => ({ mintBytes: mint(s * n + i + 1), createdAtSeconds: api.DATES[s], eligible: true })));
const budget = (perToken = 10, aggregate = 100, tokens = []) => api.createSyntheticBudget({ mode: 'SYNTHETIC_ONLY', ledgerId: 'carried-ledger', policy: { policyId: 'all-six-explicit', perToken: charge(perToken), aggregate: charge(aggregate) }, openingCharges: { tokens, aggregate: charge(tokens.reduce((sum, t) => sum + t.cost.credits, 0)) } });
const reserve = (ledger, id, n = 1, c = charge(1), extra = {}) => api.reserveSyntheticBudget({ ledger, expectedHeadHash: ledger.headHash, mintBytes: mint(n), attemptId: id, cost: c, ...extra });
const clone = value => JSON.parse(JSON.stringify(value));
const canon = value => Array.isArray(value) ? '[' + value.map(canon).join(',') + ']' : value && typeof value === 'object' ? '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canon(value[k])).join(',') + '}' : JSON.stringify(value);
function rehead(ledger) { const { headHash, ...body } = ledger; ledger.headHash = createHash('sha256').update(canon(body)).digest('hex'); return ledger; }

test('synthetic selection permutation is byte-total with independent raw32 hash oracle and detached aliases', () => {
  const r = records(110), a = select(r), b = select([...r].reverse());
  assert.deepEqual(a, b); assert.deepEqual(a.counts, [110, 110, 110]);
  for (let s = 0; s < 3; s++) {
    const expected = r.filter(x => x.createdAtSeconds === api.DATES[s]).sort((x, y) => Buffer.compare(createHash('sha256').update(x.mintBytes).digest(), createHash('sha256').update(y.mintBytes).digest()) || Buffer.compare(x.mintBytes, y.mintBytes)).slice(0, 100);
    assert.deepEqual(a.syntheticSelected[s].map(x => x.mintBytes), expected.map(x => Array.from(x.mintBytes)));
  }
  const before = JSON.stringify(a); r[0].mintBytes.fill(255); assert.equal(JSON.stringify(a), before);
  assert.throws(() => { a.syntheticSelected[0][0].mintBytes[0] = 1; }, TypeError);
  assert.equal(a.realSelection, false); assert.equal(a.cohortAdmitted, false); assert.equal(a.d1Passed, false);
  const input = mint(7), h = api.hashMintBytes(input); h.fill(0); assert.deepEqual(api.hashMintBytes(input), createHash('sha256').update(input).digest());
});
test('exact100 each,99 insufficiency,explicit eligibility and UTC exclusive June28 never replace', () => {
  const r = records(), a = select(r); assert.deepEqual(a.syntheticSelected.map(s => s.length), [100, 100, 100]);
  r[0].eligible = false; const b = select(r); assert.equal(b.status, 'INSUFFICIENT_SYNTHETIC_ELIGIBILITY'); assert.deepEqual(b.counts, [99, 100, 100]); assert.equal('syntheticSelected' in b, false);
  for (const t of api.DATES.slice(0, 3)) assert.equal(select([{ mintBytes: mint(1), createdAtSeconds: t, eligible: false }]).status, 'INSUFFICIENT_SYNTHETIC_ELIGIBILITY');
  assert.deepEqual(select([{ mintBytes: mint(1), createdAtSeconds: api.DATES[3] - 1, eligible: true }]).counts, [0, 0, 1]);
  for (const t of [api.DATES[0] - 1, api.DATES[3]]) assert.throws(() => select([{ mintBytes: mint(1), createdAtSeconds: t, eligible: true }]), /DATE_ENVELOPE/);
  assert.throws(() => select([{ mintBytes: mint(1), createdAtSeconds: api.DATES[0] }]), /SNAPSHOT/);
  assert.throws(() => select([{ mintBytes: mint(1), createdAtSeconds: api.DATES[0], eligible: 'UNKNOWN' }]), /ELIGIBILITY_REQUIRED/);
});
test('duplicate byte identities,changed facts,malformed bytes and missing policy are explicit rejection', () => {
  const r = records(); assert.throws(() => select([r[0], { ...r[0], mintBytes: Buffer.from(r[0].mintBytes), createdAtSeconds: api.DATES[1] }]), /DUPLICATE_MINT/);
  for (const bad of ['a'.repeat(32), Buffer.alloc(31), Buffer.alloc(33), Array(32).fill(256), Array(32)]) assert.throws(() => api.hashMintBytes(bad), /MINT_BYTES/);
  for (const bad of [undefined, false, 'REAL']) assert.throws(() => api.selectSynthetic({ mode: bad, records: [], eligibilityPolicyId: 'x', eligibilitySetHash: 'a'.repeat(64) }), /SYNTHETIC_MODE_REQUIRED/);
  assert.throws(() => api.selectSynthetic({ mode: api.MODE, records: [], eligibilitySetHash: 'a'.repeat(64) }), /IDENTIFIER/);
  assert.throws(() => api.selectSynthetic({ mode: api.MODE, records: [], eligibilityPolicyId: 'x', eligibilitySetHash: 'unknown' }), /HASH/);
});
test('all six exact cap boundaries,aggregate denial,overflow,zero charges and atomic failures', () => {
  for (let at = 0; at < 6; at++) {
    const c = charge(0); c[D[at]] = 1; const a = budget(1, 1), before = JSON.stringify(a), b = reserve(a, 'exact', 1, c);
    assert.equal(b.aggregate[at], 1); assert.equal(JSON.stringify(a), before);
    assert.throws(() => reserve(b, 'distinct', 1, c), /PER_TOKEN_CAP/);
    assert.throws(() => reserve(b, 'another-token', 2, c), /AGGREGATE_CAP/);
  }
  const z = reserve(budget(0, 0), 'explicit-zero', 1, charge(0)); assert.deepEqual(z.aggregate, [0, 0, 0, 0, 0, 0]);
  const max = Number.MAX_SAFE_INTEGER, a = budget(max, max, [{ mintBytes: mint(1), cost: charge(max) }]);
  assert.throws(() => reserve(a, 'overflow', 1, charge(1)), /ARITHMETIC_OVERFLOW/);
  for (const n of [-1, 1.1, max + 1, NaN, Infinity]) assert.throws(() => reserve(budget(), 'bad', 1, charge(n)), /SAFE_INTEGER/);
  const c = charge(1); delete c.requests; assert.throws(() => reserve(budget(), 'missing', 1, c), /SNAPSHOT/);
});
test('equal attempt,distinct retry,carried snapshot/head and display aliases preserve prior charges', () => {
  const a = budget(), b = reserve(a, 'first'), before = JSON.stringify(b);
  const equal = reserve(clone(b), 'first', 1, charge(1), { sessionLabel: 'renamed', tokenDisplayName: 'alias' }); assert.deepEqual(equal, b);
  const retry = reserve(clone(b), 'retry', 1, charge(1)); assert.deepEqual(retry.aggregate, [2, 2, 2, 2, 2, 2]); assert.equal(JSON.stringify(b), before);
  assert.throws(() => reserve(b, 'first', 1, charge(0)), /ATTEMPT_CONFLICT/);
  assert.throws(() => reserve(b, 'retry', 1, charge(1), { expectedHeadHash: a.headHash }), /HEAD_CONFLICT/);
  const modified = clone(b); modified.policy.perToken[0] = 100; assert.throws(() => reserve(modified, 'retry'), /POLICY_CONFLICT/);
  const counters = clone(b); counters.aggregate[0] = 0; rehead(counters); assert.throws(() => reserve(counters, 'retry'), /CHARGE_CONFLICT/);
  const snapshot = clone(b); const c = reserve(snapshot, 'retry'); snapshot.attempts[0][2][0] = 999; assert.equal(c.aggregate[0], 2);
  assert.match(c.durability, /NOT_PROVED/); assert.throws(() => { c.aggregate[0] = 0; }, TypeError);
});
test('opening totals,immutable caps,identifier byte limits and malformed snapshots reject', () => {
  const p = { policyId: 'x', perToken: charge(1), aggregate: charge(2) }, tokens = [{ mintBytes: mint(1), cost: charge(1) }];
  const input = { mode: api.MODE, ledgerId: 'l', policy: p, openingCharges: { tokens, aggregate: charge(1) } }, a = api.createSyntheticBudget(input);
  p.aggregate.credits = 99; tokens[0].mintBytes.fill(255); assert.equal(a.policy.aggregate[0], 2);
  assert.throws(() => api.createSyntheticBudget({ ...input, openingCharges: { tokens: [], aggregate: charge(1) } }), /OPENING_TOTAL_CONFLICT/);
  const b = budget(); assert.throws(() => reserve(b, 'é'.repeat(33)), /IDENTIFIER/); assert.doesNotThrow(() => reserve(b, 'é'.repeat(32)));
  for (const changed of [ { ...b, session: 'new' }, { ...b, attempts: [[[]]] }, { ...b, aggregate: Array(6) }, { ...b, cohortAdmitted: true } ]) assert.throws(() => reserve(changed, 'bad'), /SNAPSHOT|SAFE_INTEGER/);
});
test('10000-record and largest valid combined ledger envelopes fit256MB;overlimits reject nottruncate', () => {
  const start = performance.now(); const r = Array.from({ length: 10000 }, (_, i) => ({ mintBytes: mint(i + 1), createdAtSeconds: api.DATES[i % 3], eligible: true }));
  const selected = select(r); assert.deepEqual(selected.syntheticSelected.map(s => s.length), [100, 100, 100]);
  assert.throws(() => select([...r, { ...r[0], mintBytes: mint(10001) }]), /RECORD_LIMIT/);
  const many = Array.from({ length: 10000 }, (_, i) => ({ mintBytes: mint(i + 1), cost: charge(0) }));
  assert.throws(() => budget(0, 0, [...many, { mintBytes: mint(10001), cost: charge(0) }]), /TOKEN_LIMIT/);
  assert.throws(() => budget(0, 0, many), /RESULT_LIMIT/);
  // Largest valid opening charge count under the joint1MB cap, not a false10k-state PASS.
  let low = 0, high = 10000, largest;
  while (low + 1 < high) { const n = Math.floor((low + high) / 2); try { largest = budget(0, 0, many.slice(0, n)); low = n; } catch (e) { assert.match(e.message, /RESULT_LIMIT/); high = n; } }
  largest = budget(0, 0, many.slice(0, low)); assert.throws(() => budget(0, 0, many.slice(0, low + 1)), /RESULT_LIMIT/);
  let nearLimit = largest, reservations = 0;
  for (;;) {
    const before = JSON.stringify(nearLimit);
    try { nearLimit = reserve(nearLimit, 'joint-limit-' + reservations++, 1, charge(0)); }
    catch (e) { assert.match(e.message, /RESULT_LIMIT/); assert.equal(JSON.stringify(nearLimit), before); break; }
    assert.ok(reservations < 5);
  }
  const carried = clone(budget(0, 0)); carried.tokens = [[mint(1).toString('hex'), [0, 0, 0, 0, 0, 0]]];
  carried.attempts = Array.from({ length: 10000 }, (_, i) => ['a' + i, mint(1).toString('hex'), [0, 0, 0, 0, 0, 0]]); rehead(carried);
  const equal = reserve(carried, 'a9999', 1, charge(0)); assert.equal(equal.attempts.length, 10000);
  assert.throws(() => reserve(carried, 'a10000', 1, charge(0)), /ATTEMPT_LIMIT/);
  const oversized = clone(carried); oversized.attempts.push(['a10000', mint(1).toString('hex'), [0, 0, 0, 0, 0, 0]]); assert.throws(() => reserve(oversized, 'new', 1, charge(0)), /ATTEMPT_LIMIT/);
  const elapsedMs = Math.ceil(performance.now() - start), resultBytes = Buffer.byteLength(JSON.stringify(selected)), stateBytes = Buffer.byteLength(JSON.stringify(largest)), attemptsBytes = Buffer.byteLength(JSON.stringify(equal));
  assert.ok(elapsedMs < 120000); assert.ok(Math.max(resultBytes, stateBytes, attemptsBytes) <= 1000000);
  console.log(JSON.stringify({ syntheticEnvelope: true, records: 10000, largestValidOpeningTokens: low, attempts: 10000, resultBytes, stateBytes, attemptsBytes, elapsedMs, peakRssBytes: process.resourceUsage().maxRSS * 1024, heapUsedBytes: process.memoryUsage().heapUsed, heapFlag: process.execArgv.find(a => a.startsWith('--max-old-space-size=')) || 'NOT_BOUND_IN_ALL_SUITE' }));
});
test('direct invocation is library-only,no file or network integration', () => {
  const { spawnSync } = require('node:child_process');
  const run = spawnSync(process.execPath, [require.resolve('../e2-synthetic-selection-budget.cjs')], { encoding: 'utf8', timeout: 10000 });
  assert.equal(run.status, 1); assert.equal(run.stdout, ''); assert.match(run.stderr, /^LIBRARY_ONLY\r?\n$/);
});
