'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const t = require('../e2-monthly-tail.cjs');
test('Tail starts exactly after the pinned failed May prefix without overlapping admitted history', () => {
  assert.equal(t.configuration('may').start, 422954347, 'new May branch must start at missing range only');
});
test('Tail policy permits only new HTTP503 or529 HTTP_ERROR identical-query retries', () => {
  assert.equal(t.retryEligible({ status: 503, code: 'HTTP_ERROR' }), true, 'new tail 503 retry is eligible');
  assert.equal(t.retryEligible({ status: 429, code: 'HTTP_ERROR' }), false);
  assert.equal(t.retryEligible({ status: 503, code: 'TIMEOUT' }), false);
});
test('Tail reserve stops a 46th retry before transport even below original cumulative ceilings', () => {
  const a = { attempts: 45, retries: 45, received: 0, retained: 0 }, prior = { attempts: 40050, retries: 615, received: 444036897, retained: 616843252, elapsedMs: 36006712 };
  assert.throws(() => t.reserve(a, prior, 0, 15000, true), /RETRY_LIMIT/, 'included retry cap is45, not frozen monthly480');
});
const c = require('../e2-monthly-census.cjs'), { digest, fingerprint } = require('../exploratory-probe.cjs');
function memory() { const files = new Map(); let created = false; return { files, absent: () => !created, free: () => 100000000000,
  create() { assert.equal(created, false); created = true; }, write(n, b) { assert.equal(files.has(n), false); files.set(n, Buffer.from(b)); },
  read(n) { assert.ok(files.has(n), n); return files.get(n); }, size: n => files.get(n)?.length ?? 0, list: () => [...files.keys()] }; }
const options = (month = 'may') => ({ enabled: true, month, retainedBefore: '4575291338', newRetainedBefore: '616843252', attemptsBefore: '40050', retriesBefore: '615', elapsedBefore: '36006712' });
function deps(respond) { let time = 0; const store = memory(), calls = [], waits = [];
  const proof = { totals: { attempts: 40050, retries: 615, received: 444036897, retained: 616159495, elapsedMs: 31651332 },
    prefixes: ['april', 'may', 'june'].map(month => ({ month, manifestHash: 'sha256:' + 'a'.repeat(64), summaryHash: 'sha256:' + 'b'.repeat(64) })), preceding: null };
  return { store, calls, waits, proof: () => proof, now: () => time, utcNow: () => Date.parse('2026-10-04T11:40:00Z') + time,
    wait: async ms => { waits.push(ms); time += ms; }, advance: ms => { time += ms; }, source: { commit: 'a'.repeat(40), dirty: true }, runtime: 'v24.19.0',
    transport: async (q, o) => { calls.push({ q: structuredClone(q), start: time }); const raw = Buffer.from(JSON.stringify({ header: { number: q.body.toBlock,
      hash: '11111111111111111111111111111111', parentNumber: q.body.toBlock - 1, parentHash: '11111111111111111111111111111111', timestamp: q.index === 1 ? 1780271999 : 1782604799 } }) + '\n');
      const r = respond?.(q, calls.length) ?? { status: 200, code: null, bytes: raw, received: raw.length }; o.onChunk(r.received ?? r.bytes?.length ?? 0); return r; } };
}
test('Tail actual503/529 identical retry waits reobserve early timer and strict complete replay', async () => {
  for (const status of [503, 529]) { const d = deps((q, n) => n <= 2 ? { status, code: 'HTTP_ERROR', received: 7, retryAfter: '20' } : undefined);
    d.wait = async ms => { d.waits.push(ms); d.advance(ms > 1 ? ms - 1 : ms); };
    const r = await t.run(options(), d); assert.equal(r.code, null); assert.equal(t.exitCode(r), 0); assert.equal(d.calls.length, 3);
    assert.equal(d.calls[0].q.slot, 422954347); assert.deepEqual(d.calls[0].q, d.calls[1].q); assert.deepEqual(d.calls[1].q, d.calls[2].q);
    assert.equal(d.calls[1].start - d.calls[0].start, 20000); assert.equal(d.calls[2].start - d.calls[1].start, 45000); assert.ok(d.waits.includes(1));
    const rr = await t.replay('may', d.store, d); assert.equal(rr.code, null); assert.equal(t.exitCode(rr), 0); assert.equal(rr.summaryHash, r.summaryHash); assert.equal(rr.runBudget, 'UNMEASURED'); }
});
test('Tail third overload,429,timeout,bad or duplicate RetryAfter stops honest partial and replay2', async () => {
  for (const response of [{ status: 503, code: 'HTTP_ERROR', received: 1 }, { status: 529, code: 'HTTP_ERROR', received: 1 },
    { status: 429, code: 'HTTP_ERROR', received: 1 }, { status: 503, code: 'TIMEOUT', received: 1 },
    { status: 503, code: 'HTTP_ERROR', received: 1, retryAfter: '1.5' }, { status: 529, code: 'HTTP_ERROR', received: 1, retryAfterInvalid: true }]) {
    const d = deps(() => response), r = await t.run(options(), d); assert.equal(t.exitCode(r), 2);
    assert.equal(d.calls.length, [503, 529].includes(response.status) && response.code === 'HTTP_ERROR' && !response.retryAfter && !response.retryAfterInvalid ? 3 : 1);
    assert.equal(r.summary.stream.pages, 0); assert.equal([...d.store.files.keys()].some(n => n.endsWith('.raw')), false);
    const rr = await t.replay('may', d.store, d); assert.equal(rr.code, null); assert.equal(t.exitCode(rr), 2); assert.equal(rr.recordedSourceCode, r.code); }
});
test('Tail canonical CLI rejects default unsafe scalars before evidence IO and allows exact literal source/replay', async () => {
  const bad = { proof: () => { throw Error('must not read'); }, transport: () => { throw Error('must not call'); } };
  for (const args of [[], ['--enable-public'], ['--replay', '--month', 'april'], ['--enable-public', '--root', 'anything']]) assert.equal(t.exitCode(await t.runCli(args, bad)), 1);
  for (const o of [{ ...options(), month: 'april' }, { ...options(), retainedBefore: '01' }, { ...options(), arbitrary: 'bad' }]) assert.equal(t.exitCode(await t.run(o, bad)), 1);
  const d = deps(), args = ['--enable-public', '--month', 'may', '--retained-before', '4575291338', '--new-retained-before', '616843252', '--attempts-before', '40050', '--retries-before', '615', '--elapsed-before', '36006712'];
  assert.equal(t.exitCode(await t.runCli(args, d)), 0); assert.equal(t.exitCode(await t.runCli(['--replay', '--month', 'may'], d)), 0);
});
test('Tail cumulative allocation root reservation and post-capacity full timeout are fail-closed', async () => {
  for (const changed of [{ attemptsBefore: '40049' }, { retriesBefore: '614' }, { newRetainedBefore: '616843251' }, { elapsedBefore: '43200000' }, { retainedBefore: '50000000000' }]) {
    const d = deps(); assert.equal(t.exitCode(await t.run({ ...options(), ...changed }, d)), 1); assert.equal(d.calls.length, 0); }
  for (const change of [d => { d.store.absent = () => false; }, d => { d.store.free = () => 30239999999; },
    d => { let n = 0; d.store.free = () => { if (++n === 2) d.advance(1440001); return 100000000000; }; },
    d => { d.utcNow = () => t.HARD - 1000; }, d => { d.now = () => NaN; }]) {
    const d = deps(); change(d); const r = await t.run(options(), d); assert.notEqual(r.code, null); assert.equal(d.calls.length, 0); }
  const d = deps(() => ({ status: 503, code: 'HTTP_ERROR', received: 1 })); d.wait = async () => {};
  const r = await t.run(options(), d); assert.equal(r.code, 'TIME_LIMIT'); assert.equal(d.calls.length, 1);
});
test('Tail unsafe entire responses and rejected overflow are never admitted and replay remains honest', async () => {
  for (const raw of [Buffer.alloc(0), Buffer.from('{"header":{"number":422954347,"number":422954348}}\n'),
    Buffer.from(JSON.stringify({ header: { number: 422954346, hash: '11111111111111111111111111111111', parentNumber: 422954345, parentHash: '11111111111111111111111111111111', timestamp: 1777593600 } }) + '\n')]) {
    const d = deps(() => ({ status: 200, code: null, bytes: raw, received: raw.length })), r = await t.run(options(), d);
    assert.equal(r.summary.stream.pages, 0); assert.equal(t.exitCode(r), 2); assert.equal(d.calls.length, 1); assert.equal((await t.replay('may', d.store, d)).code, null); }
  const d = deps(() => ({ status: 200, code: 'RESPONSE_LIMIT', received: 16000001 })), r = await t.run(options(), d);
  assert.equal(r.code, 'RESPONSE_LIMIT'); assert.equal(r.accounting.received, 16000001); assert.equal((await t.replay('may', d.store, d)).code, null);
});
test('Tail strict replay rejects timing query raw creation summary counter and extra-file corruption', async () => {
  const mutations = [m => { m.records[1].startMs = 14999; }, m => { m.records[1].queryHash = 'sha256:' + '0'.repeat(64); },
    m => { m.accounting.received++; }, m => { m.records[1].rawHash = 'sha256:' + '0'.repeat(64); }, m => { m.lineage.configHash = 'sha256:' + '0'.repeat(64); }];
  for (const mutate of mutations) { const d = deps((q, n) => n === 1 ? { status: 503, code: 'HTTP_ERROR', received: 1 } : undefined); await t.run(options(), d);
    const m = JSON.parse(d.store.read('manifest.json')); mutate(m); d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m) + '\n')); assert.equal(t.exitCode(await t.replay('may', d.store, d)), 1); }
  for (const name of ['creations.jsonl', 'summary.json', '00002.raw']) { const d = deps((q, n) => n === 1 ? { status: 503, code: 'HTTP_ERROR', received: 1 } : undefined); await t.run(options(), d);
    d.store.files.set(name, Buffer.concat([d.store.read(name), Buffer.from('x')])); assert.equal(t.exitCode(await t.replay('may', d.store, d)), 1); }
  const d = deps(); await t.run(options(), d); d.store.files.set('00999.raw', Buffer.alloc(0)); assert.equal(t.exitCode(await t.replay('may', d.store, d)), 1);
});
test('Tail real pinned six metadata controls establish prefix proof without old raw or creations', () => {
  const reads = [], p = t.proof('may', month => { const store = c.fileStore(month); return { read(n) { reads.push(month + '/' + n); assert.ok(['manifest.json', 'summary.json'].includes(n)); return store.read(n); } }; });
  assert.equal(p.totals.attempts, 40050); assert.equal(p.totals.retries, 615); assert.equal(p.totals.received, 444036897);
  assert.equal(reads.length, 6); assert.equal(p.prefixes[2].manifestHash, 'sha256:34742b956c883ca3af487d7ff4cc14e0b9edae68fbe58fe4f9c6c539bda2fa69');
  assert.throws(() => t.proof('may', month => { const store = c.fileStore(month); return { read(n) { const b = store.read(n); return month === 'june' && n === 'summary.json' ? Buffer.concat([b, Buffer.from('x')]) : b; } }; }));
});
test('Tail independent June range remains exact and requires accounted finalized May tail', async () => {
  const d = deps(), may = await t.run(options(), d); assert.equal(may.code, null); const preceding = t.verifyTailMetadata('may', d.store);
  const j = deps(), baseProof = j.proof(); j.proof = () => ({ ...baseProof, totals: { ...baseProof.totals, attempts: 40051, received: 444036897 + may.accounting.received,
    retained: baseProof.totals.retained + may.accounting.retained }, precedingRetained: may.accounting.retained, precedingElapsed: preceding.manifest.publication.elapsedMs,
    preceding: { manifestHash: preceding.manifestHash, summaryHash: preceding.summaryHash } });
  const o = { ...options('june'), attemptsBefore: '40051', retainedBefore: String(4575291338 + may.accounting.retained), newRetainedBefore: String(616843252 + may.accounting.retained) };
  const rejected = await t.run({ ...o, newRetainedBefore: '616843252' }, j); assert.equal(rejected.code, 'ORDER_INVALID'); assert.equal(j.calls.length, 0);
  const r = await t.run(o, j); assert.equal(r.code, null); assert.equal(j.calls[0].q.slot, 429325106); assert.equal(j.calls[0].q.body.toBlock, 429340000);
  assert.equal(t.exitCode(await t.replay('june', j.store, j)), 0);
  const m = JSON.parse(d.store.read('manifest.json')); m.accounting.retainedKnown = false; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
  assert.throws(() => t.verifyTailMetadata('may', d.store));
});
test('Tail repeated page spacing whole-response conflict and ordered nonempty creations are replayed exactly', async () => {
  function b58(bytes) { const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = BigInt('0x' + bytes.toString('hex')), out = '', zeros = 0;
    while (zeros < bytes.length && bytes[zeros] === 0) zeros++; while (n) { out = alphabet[Number(n % 58n)] + out; n /= 58n; } return '1'.repeat(zeros) + out; }
  const key = '11111111111111111111111111111111', sig = '1'.repeat(64), d = deps((q, n) => { if (n > 1) return undefined;
    const block = { header: { number: q.slot, hash: key, parentNumber: q.slot - 1, parentHash: key, timestamp: 1777593600 },
      transactions: [{ transactionIndex: 2, signatures: [sig], err: null }], instructions: [[10], [2], [2, 0]].map(instructionAddress => ({ transactionIndex: 2,
        instructionAddress, programId: c.configuration('may').program, accounts: Array(5).fill(key), data: b58(Buffer.from('e992d18ecf6840bc000102', 'hex')), isCommitted: true, error: null })) };
    const raw = Buffer.from(JSON.stringify(block) + '\n'); return { status: 200, bytes: raw, received: raw.length }; });
  const r = await t.run(options(), d); assert.equal(r.code, null); assert.equal(r.summary.creations, 3); assert.equal(d.calls[1].q.slot, 422954348); assert.equal(d.calls[1].start, 250);
  const creations = d.store.read('creations.jsonl').toString().trim().split('\n').map(JSON.parse); assert.deepEqual(creations.map(v => v.instructionPath), [[2], [2, 0], [10]]);
  const rr = await t.replay('may', d.store, d); assert.equal(rr.code, null); assert.equal(rr.summaryHash, r.summaryHash);
  const m = JSON.parse(d.store.read('manifest.json')); m.records[1].startMs = 249; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m) + '\n'));
  assert.equal(t.exitCode(await t.replay('may', d.store, d)), 1);
  const x = deps((q, n) => { if (n > 1) return undefined; const duplicate = JSON.parse(d.store.read('00001.raw'));
    duplicate.instructions.push({ ...duplicate.instructions[0], accounts: [b58(Buffer.alloc(32, 2)), ...Array(4).fill(key)] }); const raw = Buffer.from(JSON.stringify(duplicate) + '\n'); return { status: 200, bytes: raw, received: raw.length }; });
  const bad = await t.run(options(), x); assert.equal(bad.code, 'IMMUTABLE_CONFLICT'); assert.equal(bad.summary.creations, 0); assert.equal([...x.store.files.keys()].some(n => n.endsWith('.raw')), false);
});
test('Tail symlink root protection makes zero filesystem writes and no source calls', () => {
  const fs = require('node:fs'), root = t.configuration('may').output, exists = fs.existsSync, stat = fs.lstatSync;
  try { fs.existsSync = n => n === root || exists(n); fs.lstatSync = n => n === root ? { isDirectory: () => true, isSymbolicLink: () => true } : stat(n);
    assert.throws(() => t.fileStore('may').absent(), /UNSAFE_PATH/); }
  finally { fs.existsSync = exists; fs.lstatSync = stat; }
});
test('Tail replay consumes remaining branch/global time rather than reserving the completed source again', async () => {
  const d = deps(), r = await t.run(options(), d); assert.equal(r.code, null);
  d.advance(1700000); assert.equal(t.exitCode(await t.replay('may', d.store, d)), 0);
  d.advance(100001); assert.equal(t.exitCode(await t.replay('may', d.store, d)), 1);
});
function fatalCounterResponse() {
  const d = deps(); d.transport = async (q, o) => { o.onChunk(1); return { status: 200, bytes: Buffer.alloc(0), received: 0 }; }; return d;
}
test('Fatal May integrity result cannot authorize June through finalized predecessor metadata', async () => {
  const d = fatalCounterResponse(), r = await t.run(options(), d);
  assert.equal(r.code, 'INTEGRITY_ERROR'); assert.equal(t.exitCode(r), 1);
  assert.throws(() => t.verifyTailMetadata('may', d.store), /INTEGRITY_ERROR/, 'fatal May source must block June metadata authorization');
});
test('Fatal May integrity result cannot become a valid partial replay', async () => {
  const d = fatalCounterResponse(), r = await t.run(options(), d);
  assert.equal(r.code, 'INTEGRITY_ERROR'); assert.equal(t.exitCode(r), 1);
  const rr = await t.replay('may', d.store, d);
  assert.equal(rr.code, 'INTEGRITY_ERROR', 'fatal source integrity remains fatal during replay');
  assert.equal(t.exitCode(rr), 1);
});
