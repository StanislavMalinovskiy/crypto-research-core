'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const p = require('../e2-path-probe.cjs'), h = require('../helius-probe.cjs');
const { digest } = require('../exploratory-probe.cjs'), { runCli } = require('../e2-path-probe-cli.cjs');
const PUMP = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function encode(bytes) { let n = BigInt('0x' + bytes.toString('hex')), out = '', zeros = 0;
  while (zeros < bytes.length && bytes[zeros] === 0) zeros++; while (n) { out = alphabet[Number(n % 58n)] + out; n /= 58n; } return '1'.repeat(zeros) + out; }
const key = n => encode(Buffer.alloc(32, n)), sig = n => encode(Buffer.alloc(64, n));
const token = (accountIndex, amount, extra = {}) => ({ accountIndex, mint: key(4), owner: key(3), uiTokenAmount: { amount, decimals: 6 }, ...extra });
const row = (n = 1, extra = {}) => ({ slot: 410195947 + n, transactionIndex: n, blockTime: 1775001600,
  transaction: { signatures: [sig(n)], message: { accountKeys: [key(1), key(2)], instructions: [] } },
  meta: { err: null, preTokenBalances: [token(1, '9007199254740993123')], postTokenBalances: [token(1, '9007199254740993128')],
    preBalances: ['100', '1'], postBalances: ['90', '1'], innerInstructions: [], fee: '10' }, ...extra });
const body = (rows = [row()], cursor = null) => Buffer.from(JSON.stringify({ jsonrpc: '2.0', id: 1, result: { data: rows, paginationToken: cursor } }));
function memory() { const files = new Map(); let created = false; return { files, free: () => 100000000000,
  create() { assert.equal(created, false); created = true; }, write(n, b) { assert.equal(files.has(n), false); files.set(n, Buffer.from(b)); },
  read(n) { assert.ok(files.has(n)); return files.get(n); }, list: () => [...files.keys()], size: n => files.get(n)?.length ?? 0 }; }
const options = () => ({ enabled: true, creditsRemaining: '960190', output: p.OUTPUT });
function deps(response = () => body()) { let time = 0; const calls = [], store = memory();
  return { calls, store, now: () => time, utcNow: () => Date.parse('2026-10-03T14:00:00Z'), wait: async ms => { time += ms; },
    source: { commit: 'a'.repeat(40), dirty: false }, runtime: 'v24.19.0', prepare: () => p.SEEDS.map(s => ({ ...s, signature: sig(1), baseMint: key(4), quoteMint: key(5) })),
    preflight: { retainedBytes: 10000000, reconciled: true, outputAbsent: true, accessReviewed: true },
    transport: async (q, o) => { calls.push({ q, start: time }); time++; const r = response(q, calls.length);
      if (Buffer.isBuffer(r)) { o.onChunk(r.length); return { code: null, status: 200, received: r.length, bytes: r, attempted: true }; }
      o.onChunk(r.received ?? 0); return r; } };
}
test('E2 enabled fixed finite round robin stops at nine requests and preserves conditional quota', async () => {
  const d = deps((q, n) => body([row(n)], `${410195947 + n}:${n}`)), r = await p.run(options(), d);
  assert.equal(r.code, null, 'enabled path executes the admitted finite probe'); assert.equal(d.calls.length, 9);
  assert.deepEqual(d.calls.map(c => c.q.body.params[0]), [...p.SEEDS, ...p.SEEDS, ...p.SEEDS].map(s => s.pool));
  for (let i = 0; i < d.calls.length; i++) { const c = d.calls[i]; assert.deepEqual(c.q, h.query3(p.SEEDS[i % 3].pool, 0, i < 3 ? null : `${410195947 + i - 2}:${i - 2}`));
    if (i) assert.ok(c.start - d.calls[i - 1].start >= 250); }
  assert.equal(r.accounting.attempts, 9); assert.equal(r.accounting.creditsReserved, 900); assert.equal(r.accounting.retries, 0);
  assert.ok(r.summary.streams.every(s => s.pages === 3 && s.status === 'CENSORED' && s.creation.status === 'CREATION_UNKNOWN'));
  for (const flag of ['d1Evidence', 'd1Passed', 'cohortAdmitted', 'actualTraderConfirmed', 'economicReconstructed']) assert.equal(r.summary[flag], false);
  const m = JSON.parse(d.store.read('manifest.json')); assert.equal(m.quota.decision, '0a1b5e93-58e6-437d-b893-531b3bb5cbec');
  assert.equal(m.quota.formula, '1000000-10-39800=960190'); assert.equal(m.quota.actualH3Debit, 'UNKNOWN'); assert.equal(m.quota.unrelatedUsage, 'UNVERIFIED');
  assert.equal(m.quota.autoTransition, 'UNVERIFIED'); assert.equal(m.quota.measuredCurrentBalance, false); assert.equal(r.summary.fullD1UpperBound, null);
});
test('E2 seed failure and unreconciled accounting block before requests', async () => {
  const bad = deps(); bad.prepare = () => { throw 'INTEGRITY_ERROR'; }; const r = await p.run(options(), bad);
  assert.equal(r.code, 'INTEGRITY_ERROR'); assert.equal(bad.calls.length, 0); assert.equal(bad.store.files.size, 0);
  for (const extra of [{ retainedBytes: null }, { reconciled: false }, { accessReviewed: false }, { outputAbsent: false }]) {
    const d = deps(); Object.assign(d.preflight, extra); assert.notEqual((await p.run(options(), d)).code, null); assert.equal(d.calls.length, 0);
  }
  const raw = Buffer.from(JSON.stringify({ header: { number: 1 }, transactions: [], instructions: [] })), manifest = Buffer.from('{}');
  assert.throws(() => p.verifySeeds({ manifest, raws: { '004.raw': raw } }), e => e === 'INTEGRITY_ERROR');
});
test('E2 whole unsafe page never becomes retained raw coverage', async () => {
  for (const blockTime of [undefined, null, 1775001599, 1782777600, 1788134400]) {
    const d = deps(() => body([row(), row(2, { blockTime })])), r = await p.run(options(), d);
    assert.equal(r.code, 'RESPONSE_INVALID'); assert.equal(d.calls.length, 1); assert.equal(r.summary.rows, 0);
    assert.equal([...d.store.files.keys()].some(n => n.endsWith('.raw')), false); assert.ok(r.accounting.received > 0);
  }
});
test('E2 nonadvancing cursor and immutable conflict terminate without retry', async () => {
  const d = deps(() => body([row()], '410195948:1')), r = await p.run(options(), d);
  assert.equal(r.code, 'CURSOR_INVALID'); assert.equal(d.calls.length, 4);
  const conflict = deps((q, n) => body([row(1, { memo: String(n) })])); assert.equal((await p.run(options(), conflict)).code, 'IMMUTABLE_CONFLICT'); assert.equal(conflict.calls.length, 2);
  for (const code of ['HTTP_ERROR', 'TIMEOUT', 'NETWORK_ERROR', 'SECRET_EXPOSURE']) {
    const refused = deps(() => ({ code, status: 403, received: 7, bytes: Buffer.from('https://bad/?api-key=private-body') })), r2 = await p.run(options(), refused);
    assert.equal(r2.code, code); assert.equal(refused.calls.length, 1); assert.equal(r2.accounting.received, 7); assert.equal(r2.accounting.creditsReserved, 100);
    assert.equal(JSON.stringify([...refused.store.files].map(([n, b]) => [n, b.toString()])).includes('private-body'), false);
  }
});
test('E2 full reservation blocks disk free credits response and capacity-delayed transport', async () => {
  const late = deps(); let checks = 0; late.store.free = () => { if (++checks === 2) late.wait(840001); return 100000000000; };
  assert.equal((await p.run(options(), late)).code, 'TIME_LIMIT'); assert.equal(late.calls.length, 0);
  const disk = deps(); disk.preflight.retainedBytes = 49000000001; assert.equal((await p.run(options(), disk)).code, 'DISK_LIMIT'); assert.equal(disk.calls.length, 0);
  const free = deps(); free.store.free = () => 30999999999; assert.equal((await p.run(options(), free)).code, 'FREE_SPACE_LIMIT'); assert.equal(free.calls.length, 0);
  const credit = deps(); assert.equal((await p.run({ ...options(), creditsRemaining: '899' }, credit)).code, 'CREDIT_LIMIT'); assert.equal(credit.calls.length, 0);
  const bytes = deps(() => ({ code: 'RESPONSE_LIMIT', received: 64000001 })); assert.equal((await p.run(options(), bytes)).code, 'RESPONSE_LIMIT'); assert.equal(bytes.calls.length, 1);
  const delayed = deps(); delayed.wait = async ms => { await late.wait(ms); await delayed.bump?.(); };
  const partial = deps(); const write = partial.store.write; partial.store.write = (n, b) => { if (n.endsWith('.raw')) { partial.store.files.set(n, b.subarray(0, 7)); throw 'STORAGE_ERROR'; } write(n, b); };
  const r = await p.run(options(), partial); assert.equal(r.code, 'STORAGE_ERROR'); assert.equal(r.accounting.partialBytes, 7); assert.equal(r.summary.rows, 0);
});
test('E2 genuine static and loaded ownership uses exact deltas and missing sides stay unknown', () => {
  const r = row(); r.meta.loadedAddresses = { writable: [key(6)], readonly: [key(7)] }; r.meta.preTokenBalances.push(token(2, '20')); r.meta.postTokenBalances.push(token(2, '3'));
  const d = p.diagnose(r, p.SEEDS[0], 'sha256:' + 'a'.repeat(64));
  assert.deepEqual(d.observations.map(o => o.delta), ['5', '-17']); assert.equal(d.observations[1].account, key(6));
  assert.equal(d.observations[0].owner, key(3)); assert.notEqual(d.observations[0].owner, r.transaction.message.accountKeys[0]);
  assert.equal(d.payload.nativePreCount, 2); assert.ok(d.gaps.FEE_ROUTE_TIP_UNRESOLVED); assert.ok(d.gaps.UNLINKED_OWNED_ACCOUNT);
  for (const mutate of [r => { r.meta.postTokenBalances = []; }, r => { r.meta.postTokenBalances[0].owner = key(8); },
    r => { r.meta.postTokenBalances[0].uiTokenAmount.decimals = 9; }, r => { r.meta.preTokenBalances[0].accountIndex = 20; }, r => { r.meta.err = 'AccountInUse'; }]) {
    const x = row(); mutate(x); const unknown = p.diagnose(x, p.SEEDS[0], 'sha256:' + 'a'.repeat(64)); assert.equal(unknown.observations.length, 0); assert.ok(Object.keys(unknown.gaps).length);
  }
});
function creationRow(pool = p.SEEDS[0].pool) {
  const keys = [pool, key(9), key(3), key(4), key(5), key(10), key(2), key(11), key(12), key(13), key(14), '11111111111111111111111111111111',
    'TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb', 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA', 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',
    'ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL', key(15), PUMP];
  const ix = Buffer.alloc(60); Buffer.from('e992d18ecf6840bc', 'hex').copy(ix); const event = Buffer.alloc(334);
  Buffer.from('b1310cd2a076a774', 'hex').copy(event); event.writeBigInt64LE(1775001600n, 8);
  for (const [offset, n] of [[18, 3], [50, 4], [82, 5], [205, 10], [237, 2], [269, 11]]) Buffer.alloc(32, n).copy(event, offset);
  let v = 0n; for (const c of pool) v = v * 58n + BigInt(alphabet.indexOf(c)); Buffer.from(v.toString(16).padStart(64, '0'), 'hex').copy(event, 173);
  const r = row(); r.transaction.message = { accountKeys: keys, instructions: [{ programIdIndex: 17, accounts: keys.map((_, i) => i), data: encode(ix) }] };
  r.meta.logMessages = [`Program ${PUMP} invoke [1]`, 'Program data: ' + event.toString('base64'), `Program ${PUMP} success`]; return r;
}
test('E2 creation requires explicit successful linked discriminator layout pool and mint evidence', async () => {
  const proof = creationRow(), d = p.diagnose(proof, { ...p.SEEDS[0], baseMint: key(4), quoteMint: key(5) }, 'sha256:' + 'a'.repeat(64));
  assert.equal(d.creation.status, 'OBSERVED_DECLARED_CREATION'); assert.equal(d.creation.layoutApplicability, 'DECLARED_UNVERIFIED');
  for (const mutate of [r => { r.meta.err = {}; }, r => { r.meta.logMessages = []; }, r => { r.transaction.message.instructions[0].programIdIndex = 0; },
    r => { r.transaction.message.instructions[0].data = encode(Buffer.alloc(60)); }, r => { r.transaction.message.accountKeys[3] = key(8); }]) {
    const x = creationRow(); mutate(x); assert.equal(p.diagnose(x, { ...p.SEEDS[0], baseMint: key(4), quoteMint: key(5) }, 'sha256:' + 'a'.repeat(64)).creation.status, 'CREATION_UNKNOWN');
  }
  const run = deps(q => { const creation = creationRow(q.body.params[0]);
    creation.transaction.signatures = [sig(p.SEEDS.findIndex(s => s.pool === q.body.params[0]) + 1)]; return body([creation], '410195948:1'); });
  assert.equal((await p.run(options(), run)).code, null); assert.equal(run.calls.length, 3);
});
test('E2 replay validates raw query quota and lineage without writes key or network', async () => {
  const d = deps(), r = await p.run(options(), d); assert.equal(r.code, null);
  const readOnly = { read: d.store.read, list: d.store.list }; const replayed = await p.replay(readOnly); assert.equal(replayed.code, null); assert.equal(replayed.semanticHash, r.semanticHash);
  const m = JSON.parse(d.store.read('manifest.json')); m.operational.elapsedMs += 123; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
  assert.equal((await p.replay(readOnly)).semanticHash, r.semanticHash);
  m.quota.unrelatedUsage = 'NONE'; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m))); assert.equal((await p.replay(readOnly)).code, 'INTEGRITY_ERROR');
  m.quota.unrelatedUsage = 'UNVERIFIED'; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m))); const raw = [...d.store.files.keys()].find(n => n.endsWith('.raw'));
  d.store.files.set(raw, Buffer.from('altered')); assert.equal((await p.replay(readOnly)).code, 'INTEGRITY_ERROR');
  const a = deps(() => body([row(1), row(2)])), b = deps(() => body([row(2), row(1)])), ar = await p.run(options(), a), br = await p.run(options(), b);
  const domain = observations => observations.map(({ rawHash, ...fields }) => fields); assert.deepEqual(domain(ar.summary.observations), domain(br.summary.observations));
  for (const [run, result] of [[a, ar], [b, br]]) for (const observation of result.summary.observations) {
    const retained = [...run.store.files].filter(([name]) => name.endsWith('.raw')).map(([, bytes]) => bytes);
    assert.ok(retained.some(bytes => digest(bytes) === observation.rawHash && JSON.parse(bytes).result.data.some(row => row.transaction.signatures[0] === observation.signature)));
  }
  const other = deps(); other.source.commit = 'b'.repeat(40); assert.notEqual((await p.run(options(), other)).semanticHash, r.semanticHash);
});
test('E2 CLI disabled has no reads and strictly allows only reviewed explicit arguments', async () => {
  const forbidden = new Proxy({}, { get() { assert.fail('disabled CLI accessed dependency'); } }); assert.equal((await runCli([], forbidden)).code, 'DISABLED');
  assert.equal((await runCli(['--enable-free'], forbidden)).code, 'ARGUMENTS_INVALID');
  const d = deps(); assert.equal((await runCli(['--enable-free', '--credits-remaining', '960190'], d)).code, null); assert.equal(d.calls.length, 3);
});
