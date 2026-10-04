'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const c = require('../e2-monthly-census.cjs');
const fs = require('node:fs'), path = require('node:path');
const bounds = [410195947, 416762082, 423478907, 429340001];
const dates = [1775001600, 1777593600, 1780272000, 1782604800];
const key = '11111111111111111111111111111111';
const header = (number, timestamp) => ({ number, hash: key, parentNumber: number - 1, parentHash: key, timestamp });
const nd = blocks => Buffer.from(blocks.map(x => JSON.stringify(x)).join('\n') + '\n');
function memory() { const files = new Map(); let created = false; return { files, free: () => 100000000000,
  absent: () => !created, create() { assert.equal(created, false); created = true; },
  write(n, b) { assert.equal(files.has(n), false); files.set(n, Buffer.from(b)); },
  read(n) { assert.ok(files.has(n), n); return files.get(n); }, size: n => files.get(n)?.length ?? 0, list: () => [...files.keys()] }; }
function deps(respond) { let time = 0; const store = memory(), calls = [];
  return { store, calls, now: () => time, utcNow: () => Date.parse('2026-10-04T10:00:00Z'),
    wait: async ms => { time += ms; }, runtime: 'v24.19.0', source: { commit: 'a'.repeat(40), dirty: true },
    // Real pinned proof is separately tested; runner tests do not read any old data pages.
    verifyBoundary: () => ({ bounds, status: 'VERIFIED' }), previous: () => [],
    transport: async (q, o) => { calls.push({ q, start: time }); const bytes = nd([{ header: header(q.body.toBlock, dates[q.index + 1] - 1) }]);
      const r = respond?.(q, calls.length) ?? { status: 200, bytes, received: bytes.length };
      o.onChunk(r.received ?? r.bytes?.length ?? 0); return r; } };
}
const options = (month = 'april') => ({ enabled: true, month, retainedBefore: '1000000', attemptsBefore: '0', retriesBefore: '0' });
test('Monthly enabled April admits sparse terminal boundary with one actual start and no controls', async () => {
  const d = deps(), r = await c.run(options(), d);
  assert.equal(r.status, 'SCAN_COMPLETE', 'explicitly enabled safe monthly scan reaches its returned terminal boundary');
  assert.equal(r.code, null); assert.equal(d.calls.length, 1);
  assert.equal(d.calls[0].q.body.fromBlock, 410195947); assert.equal(d.calls[0].q.body.toBlock, 416762081);
  assert.equal(d.calls[0].q.kind, 'data'); assert.equal(r.accounting.attempts, 1);
});
test('Monthly exact ranges roots and narrow query reject other months and out of range', () => {
  for (const [i, month] of ['april', 'may', 'june'].entries()) {
    const q = c.query(month); assert.equal(q.body.fromBlock, bounds[i]); assert.equal(q.body.toBlock, bounds[i + 1] - 1);
    assert.equal(q.index, i); assert.equal(q.body.includeAllBlocks, false);
    assert.equal(q.url, 'https://portal.sqd.dev/datasets/solana-mainnet/finalized-stream');
    assert.equal(c.output(month), 'C:\\crypto-research-evidence\\r1-e2\\exploratory-monthly-census-v1-' + month);
    assert.deepEqual(Object.keys(q.body.fields), ['block', 'transaction', 'instruction']);
    assert.throws(() => c.query(month, bounds[i] - 1)); assert.throws(() => c.query(month, bounds[i + 1]));
    assert.equal(c.admit(nd([{ header: header(bounds[i], dates[i]) }]), q, month).code, null);
    assert.notEqual(c.admit(nd([{ header: header(bounds[i], dates[i]) }]), q, ['may', 'june', 'april'][i]).code, null);
  } assert.throws(() => c.query('july'));
});
test('Monthly real positive pinned manifest plus eight controls proof and corrupted control fail', () => {
  assert.deepEqual(c.verifyBoundary(), { status: 'VERIFIED', bounds, manifestHash: c.SEAL.manifestHash, controlBytes: 880 });
  const root = 'C:\\crypto-research-evidence\\r1-e2\\exploratory-census-v1', readNames = [];
  assert.throws(() => c.verifyBoundary(name => { readNames.push(name); const bytes = fs.readFileSync(path.join(root, name));
    return name === '0008.raw' ? Buffer.concat([bytes, Buffer.from('!')]) : bytes; }));
  assert.deepEqual(readNames, ['manifest.json', ...Array.from({ length: 8 }, (_, i) => String(i + 1).padStart(4, '0') + '.raw')]);
});
test('Monthly disabled unsafe CLI root order pin expiry and physical reservation make zero calls', async () => {
  const cli = require('../e2-monthly-census-cli.cjs');
  for (const args of [[], ['--enable-public'], ['--replay', '--month', '../april'],
    ['--enable-public', '--month', 'april', '--retained-before', '01', '--attempts-before', '0', '--retries-before', '0']]) {
    const d = deps(); assert.equal(c.exitCode(await cli.runCli(args, d)), 1); assert.equal(d.calls.length, 0); }
  for (const opts of [{ ...options(), output: 'elsewhere' }, options('may'), { ...options(), attemptsBefore: '302' },
    { ...options(), retainedBefore: '46400000001' }, { ...options(), retriesBefore: '-1' }]) {
    const d = deps(); assert.equal(c.exitCode(await c.run(opts, d)), 1); assert.equal(d.calls.length, 0); }
  for (const change of [d => { d.store.absent = () => false; }, d => { d.store.free = () => 33599999999; },
    d => { d.verifyBoundary = () => { throw Error('INTEGRITY_ERROR'); }; }, d => { d.utcNow = () => Date.parse('2026-10-11T00:00:00Z'); },
    d => { d.runtime = 'v23.1.0'; }]) { const d = deps(); change(d); assert.equal(c.exitCode(await c.run(options(), d)), 1); assert.equal(d.calls.length, 0); }
});
test('Monthly 529 identical retries honor 15/45 and RetryAfter; private errors are not retained', async () => {
  const d = deps((q, n) => n <= 2 ? { status: 529, received: 7, retryAfter: '20', bytes: Buffer.from('private') } : undefined);
  const r = await c.run(options(), d); assert.equal(r.code, null); assert.equal(r.accounting.attempts, 3); assert.equal(r.accounting.retries, 2);
  assert.deepEqual(d.calls[0].q, d.calls[1].q); assert.deepEqual(d.calls[1].q, d.calls[2].q);
  assert.equal(d.calls[1].start - d.calls[0].start, 20000); assert.equal(d.calls[2].start - d.calls[1].start, 45000);
  assert.equal([...d.store.files.values()].some(b => b.toString().includes('private')), false);
  const rr = await c.replay('april', d.store); assert.equal(rr.code, null); assert.equal(rr.summaryHash, r.summaryHash);
  const x = deps(() => ({ status: 529, received: 7 })), y = await c.run(options(), x);
  assert.equal(y.code, 'HTTP_529_EXHAUSTED'); assert.equal(y.accounting.attempts, 3); assert.equal(y.accounting.retries, 2);
  const replay = await c.replay('april', x.store); assert.equal(replay.code, null); assert.equal(replay.recordedSourceCode, 'HTTP_529_EXHAUSTED'); assert.equal(c.exitCode(replay), 2);
});
test('Monthly valid partial replay exits2 complete0 and integrity1 with literal CLI arguments', async () => {
  const cli = require('../e2-monthly-census-cli.cjs'), args = ['--enable-public', '--month', 'april', '--retained-before', '1000000', '--attempts-before', '0', '--retries-before', '0'];
  for (const status of [204, 301, 401, 403, 429, 500]) {
    const d = deps(() => ({ status, received: 5 })), r = await cli.runCli(args, d);
    assert.equal(c.exitCode(r), 2); assert.equal(d.calls.length, 1); assert.equal(r.accounting.received, 5);
    const rr = await cli.runCli(['--replay', '--month', 'april'], { store: d.store }); assert.equal(rr.code, null); assert.equal(c.exitCode(rr), 2);
  }
  const d = deps(), r = await cli.runCli(args, d); assert.equal(c.exitCode(r), 0);
  assert.equal(c.exitCode(await cli.runCli(['--replay', '--month', 'april'], { store: d.store })), 0);
  const m = JSON.parse(d.store.read('manifest.json')); m.records[0].query.body.toBlock++;
  d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m))); assert.equal(c.exitCode(await c.replay('april', d.store)), 1);
});
test('Monthly whole-response rejection precedes retained raw counters or scope advance', async () => {
  for (const raw of [Buffer.alloc(0), nd([{ header: header(bounds[0], dates[0]) }, { header: header(bounds[0] + 1, 1788134400) }]),
    Buffer.from('{"header":{"number":1,"number":2}}'), nd([{ header: header(bounds[0] - 1, dates[0]) }])]) {
    const d = deps(() => ({ status: 200, bytes: raw, received: raw.length })), r = await c.run(options(), d);
    assert.equal(c.exitCode(r), 2); assert.equal(r.summary.stream.pages, 0); assert.equal(r.summary.creations, 0);
    assert.equal([...d.store.files.keys()].some(n => n.endsWith('.raw')), false); assert.equal(r.accounting.received, raw.length);
    assert.equal((await c.replay('april', d.store)).code, null);
  }
});
test('Monthly post-capacity time and full retry wait reserve prevent next actual start', async () => {
  const late = deps(); let n = 0; late.store.free = () => { if (++n === 2) late.wait(c.LIMITS.sourceMs - c.LIMITS.deadline + 1); return 100000000000; };
  const lr = await c.run(options(), late); assert.equal(lr.code, 'TIME_LIMIT'); assert.equal(late.calls.length, 0);
  const d = deps(() => ({ status: 529, received: 4, retryAfter: '20000' }));
  assert.equal((await c.run(options(), d)).code, 'TIME_LIMIT'); assert.equal(d.calls.length, 1);
  const slow = deps(); slow.transport = async (q, o) => { slow.calls.push(q); o.onChunk(7); await slow.wait(c.LIMITS.sourceMs + 1); return { status: 200, received: 7 }; };
  assert.equal((await c.run(options(), slow)).code, 'TIME_LIMIT'); assert.equal(slow.calls.length, 1);
});
test('Monthly cumulative finalized metadata permits only April then May then June without reset', async () => {
  const manifests = []; let attempts = 0, retries = 0;
  for (const month of ['april', 'may', 'june']) { const d = deps(); d.previous = () => manifests;
    const r = await c.run({ ...options(month), attemptsBefore: String(attempts), retriesBefore: String(retries) }, d);
    assert.equal(r.code, null); assert.equal(d.calls[0].q.index, manifests.length);
    assert.equal((await c.replay(month, d.store)).code, null); const m = JSON.parse(d.store.read('manifest.json'));
    manifests.push(m); attempts += r.accounting.attempts; retries += r.accounting.retries;
  }
  const d = deps(); d.previous = () => [manifests[1]]; assert.equal((await c.run({ ...options('may'), attemptsBefore: '1' }, d)).code, 'ORDER_INVALID'); assert.equal(d.calls.length, 0);
  for (const [k, value] of [['received', 1000000001], ['retained', 1200000001], ['retries', 481], ['attempts', 16001]]) {
    const m = structuredClone(manifests[0]); m.accounting[k] = value;
    assert.throws(() => c.priorTotals('may', [m], m.accounting.attempts, m.accounting.retries)); }
  const m = structuredClone(manifests[0]); m.runBudget = 'OVERRUN'; assert.throws(() => c.priorTotals('may', [m], 1, 0));
});
test('Monthly 16000 actual cap five-digit raw and manifest above4MB independently replay', async () => {
  const d = deps(q => { const bytes = nd([{ header: header(q.slot, dates[0]) }]); return { status: 200, bytes, received: bytes.length }; });
  const r = await c.run(options(), d); assert.equal(r.code, 'ATTEMPT_LIMIT'); assert.equal(d.calls.length, 16000);
  assert.equal(r.accounting.attempts, 16000); assert.equal(r.accounting.retries, 0);
  assert.ok(d.store.files.has('10000.raw')); assert.ok(d.store.files.has('16000.raw')); assert.equal(d.store.files.has('0001.raw'), false);
  assert.ok(d.store.read('manifest.json').length > 4000000); assert.ok(d.store.read('manifest.json').length < 64000000);
  const rr = await c.replay('april', d.store); assert.equal(rr.code, null); assert.equal(rr.summaryHash, r.summaryHash); assert.equal(c.exitCode(rr), 2);
});
test('Monthly 480 retry cap charges every actual start and stops on the next 529', async () => {
  let within = 0; const d = deps(q => { within++;
    if (within % 2 !== 0) return { status: 529, received: 2 };
    const bytes = nd([{ header: header(q.slot, dates[0]) }]); return { status: 200, bytes, received: bytes.length }; });
  const r = await c.run(options(), d); assert.equal(r.code, 'RETRY_LIMIT'); assert.equal(r.accounting.retries, 480);
  assert.equal(r.accounting.attempts, 961); assert.equal(d.calls.length, 961); assert.equal((await c.replay('april', d.store)).code, null);
});
function emptyAdmission(n = bounds[0]) { return { code: null, lastScanned: n, lastMatched: null,
  headers: [header(n, dates[0])], creations: [], counts: { instructions: 0, success: 0, unknown: 0, topLevel: 0, cpi: 0, depth: {}, reasons: {} }, matchingHeaders: 0 }; }
test('Monthly incremental growth immutable duplicates and count byte header instruction bounds', () => {
  const s = c.initial('april'); for (let i = 0; i < 1000; i++) { const a = emptyAdmission(bounds[0] + i);
    a.creations = [{ slot: bounds[0] + i, transactionIndex: 0, instructionPath: [0], signature: 'synthetic', rawHash: 'sha256:' + '1'.repeat(64) }];
    c.apply(s, a, c.prepare(s, a)); }
  assert.equal(s.creations.size, 1000); assert.equal(s.serializedCreations, 1000);
  assert.equal(s.creationBytes, [...s.creations.values()].reduce((n, c) => n + c.bytes.length, 0));
  const a = emptyAdmission(s.current); a.creations = [s.creations.values().next().value.value]; const plan = c.prepare(s, a);
  assert.equal(plan.creations.size, 0); a.creations[0] = { ...a.creations[0], rawHash: 'changed' }; assert.throws(() => c.prepare(s, a), /IMMUTABLE_CONFLICT/);
  const b = emptyAdmission(); const oversized = c.initial('april'); oversized.creationBytes = c.LIMITS.creationBytes + 1;
  assert.throws(() => c.prepare(oversized, b), /CREATION_LIMIT/);
  const many = c.initial('april'); many.headers = { get: () => null, has: () => false, size: 1000000 };
  assert.throws(() => c.prepare(many, b), /HEADER_LIMIT/);
  const instructions = c.initial('april'); instructions.instructions = 1000001; assert.throws(() => c.prepare(instructions, b), /INSTRUCTION_LIMIT/);
  const creations = c.initial('april'); creations.creations = { get: () => null, size: 500001 }; assert.throws(() => c.prepare(creations, b), /CREATION_LIMIT/);
  b.counts.depth = Object.fromEntries(Array.from({ length: 1001 }, (_, i) => [i, 1])); assert.throws(() => c.prepare(c.initial('april'), b), /INSTRUCTION_LIMIT/);
});
test('Monthly response received retained and metadata ceilings plus failures remain honest and sanitized', async () => {
  const large = deps(() => ({ status: 500, received: 64000001 })), r = await c.run(options(), large);
  assert.equal(r.code, 'RESPONSE_LIMIT'); assert.equal(r.accounting.received, 64000001); assert.equal(large.calls.length, 1);
  const x = deps(); x.transport = async (q, o) => { x.calls.push(q); o.onChunk(17); throw Error('private credential-bearing failure'); };
  const y = await c.run(options(), x); assert.equal(y.code, 'NETWORK_ERROR'); assert.equal(y.accounting.received, 17); assert.equal(JSON.stringify(y).includes('private'), false);
  const partial = deps(); const normalWrite = partial.store.write; partial.store.write = (n, b) => {
    if (n.endsWith('.raw')) { partial.store.files.set(n, b.subarray(0, 7)); throw Error('private disk error'); } normalWrite(n, b); };
  const p = await c.run(options(), partial); assert.equal(p.code, 'STORAGE_ERROR'); assert.equal(p.accounting.partialBytes, 7); assert.equal(p.summary.stream.pages, 0);
  const fail = deps(); const write = fail.store.write; fail.store.write = (n, b) => { if (n === 'manifest.json') throw Error('private'); write(n, b); };
  const f = await c.run(options(), fail); assert.equal(f.code, 'STORAGE_ERROR'); assert.equal(f.accounting.retainedKnown, true); assert.equal(fail.store.files.has('manifest.json'), false);
  assert.equal((await c.replay('april', fail.store)).code, 'INTEGRITY_ERROR');
  assert.equal(c.LIMITS.metadata, 64000000); assert.equal(c.LIMITS.retained, 1200000000); assert.equal(c.TOTAL.received, 3000000000);
});
test('Monthly offline replay never writes or networks and rejects hash counter file query identity tampering', async () => {
  const d = deps(); await c.run(options(), d); const saved = new Map(d.store.files), ro = { read: d.store.read, list: d.store.list };
  assert.equal((await c.replay('april', ro)).code, null);
  for (const mutate of [m => { m.accounting.attempts++; }, m => { m.accounting.retries++; }, m => { m.accounting.received++; },
    m => { m.configuration.month = 'may'; }, m => { m.lineage.scripts['e2-census.cjs'] = 'x'; }, m => { m.summaryHash = 'x'; },
    m => { m.records[0].raw = '../00001.raw'; }, m => { m.records[0].query.url = 'https://private.invalid'; }]) {
    const m = JSON.parse(saved.get('manifest.json')); mutate(m); d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
    assert.equal((await c.replay('april', ro)).code, 'INTEGRITY_ERROR'); }
  d.store.files.set('manifest.json', saved.get('manifest.json')); d.store.files.set('extra.raw', Buffer.from('extra'));
  assert.equal((await c.replay('april', ro)).code, 'INTEGRITY_ERROR'); d.store.files.delete('extra.raw');
  d.store.files.set('00001.raw', Buffer.from('corrupt')); assert.equal((await c.replay('april', ro)).code, 'INTEGRITY_ERROR');
});
test('Monthly actual pre-start budget policy reserves monthly and combined bytes time metadata', () => {
  const a = { attempts: 0, retries: 0, received: 0, retained: 0 }, p = { ...a, elapsedMs: 0 };
  const call = (usage = a, prior = p, cb = 0, mb = 0, rb = 0, elapsed = 0, pause = 0, retry = 0) => c.checkBudget(usage, prior, cb, mb, rb, elapsed, pause, retry);
  assert.equal(call(), 128000000);
  for (const [field, value, code] of [['attempts', 16000, 'ATTEMPT_LIMIT'], ['received', 936000001, 'RECEIVED_LIMIT'],
    ['retained', 1072000001, 'DISK_LIMIT']]) assert.throws(() => call({ ...a, [field]: value }), new RegExp(code));
  assert.throws(() => call({ ...a, retries: 480 }, p, 0, 0, 0, 0, 0, 1), /RETRY_LIMIT/);
  assert.throws(() => call(a, p, 0, 0, 59000000), /METADATA_LIMIT/);
  assert.throws(() => call(a, p, 0, 0, 0, 14040001), /TIME_LIMIT/);
  for (const [field, value, code] of [['attempts', 48000, 'ATTEMPT_LIMIT'], ['received', 2936000001, 'RECEIVED_LIMIT'],
    ['retained', 3472000001, 'DISK_LIMIT']]) assert.throws(() => call(a, { ...p, [field]: value }), new RegExp(code));
  assert.throws(() => call(a, { ...p, retries: 1440 }, 0, 0, 0, 0, 0, 1), /RETRY_LIMIT/);
  assert.throws(() => call(a, { ...p, elapsedMs: 43140001 }), /TIME_LIMIT/);
});
test('Monthly wrapper actual linked CPI creation UNKNOWN and sparse scanned boundary survive independent replay', async () => {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = BigInt('0xe992d18ecf6840bc000102'), encoded = '';
  while (n) { encoded = alphabet[Number(n % 58n)] + encoded; n /= 58n; }
  const instruction = { transactionIndex: 0, instructionAddress: [2, 1], programId: 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA',
    accounts: [key, key, key, key, key, key], data: encoded, isCommitted: true, error: null };
  const raw = nd([{ header: header(bounds[0], dates[0]), transactions: [{ transactionIndex: 0, signatures: ['1'.repeat(64)], err: null }],
    instructions: [instruction, { ...instruction, instructionAddress: [3], isCommitted: false }] },
  { header: header(bounds[1] - 1, dates[1] - 1) }]);
  const d = deps(() => ({ status: 200, bytes: raw })), r = await c.run(options(), d);
  assert.equal(r.code, null); assert.equal(r.summary.creations, 1); assert.equal(r.summary.stream.cpi, 1); assert.equal(r.summary.stream.unknown, 1);
  assert.equal(r.summary.stream.lastMatched, bounds[0]); assert.equal(r.summary.stream.lastScanned, bounds[1] - 1);
  const creation = JSON.parse(d.store.read('creations.jsonl')); assert.equal(creation.cpiDepth, 1); assert.equal(creation.accountCount, 6);
  assert.equal(creation.layoutApplicability, 'UNVERIFIED'); assert.equal(r.summary.cohortAdmitted, false); assert.equal(r.summary.authoritativeCensusComplete, false);
  const rr = await c.replay('april', d.store); assert.equal(rr.code, null); assert.equal(rr.summaryHash, r.summaryHash);
});
function replaceManifest(store, m) {
  const retained = m.files.reduce((n, f) => n + f.bytes, 0); let bytes;
  for (let i = 0; i < 16; i++) { bytes = Buffer.from(JSON.stringify(m) + '\n');
    if (m.accounting.manifestBytes === bytes.length && m.accounting.retained === retained + bytes.length) break;
    m.accounting.manifestBytes = bytes.length; m.accounting.retained = retained + bytes.length; }
  store.files.set('manifest.json', Buffer.from(JSON.stringify(m) + '\n'));
}
test('Monthly repair1 terminal rejected response overflow remains valid partial replay with actual bytes', async () => {
  const d = deps(() => ({ status: 500, received: 64000001 })), source = await c.run(options(), d);
  assert.equal(source.code, 'RESPONSE_LIMIT'); assert.equal(c.exitCode(source), 2);
  assert.equal(source.accounting.received, 64000001); assert.equal(source.summary.stream.pages, 0);
  assert.equal([...d.store.files.keys()].some(n => n.endsWith('.raw')), false);
  const replay = await c.replay('april', d.store);
  assert.equal(replay.code, null, 'honest terminal rejected overflow is semantically valid partial evidence');
  assert.equal(c.exitCode(replay), 2); assert.equal(replay.recordedSourceCode, 'RESPONSE_LIMIT');
  assert.equal(replay.summaryHash, source.summaryHash);
  const saved = JSON.parse(d.store.read('manifest.json'));
  for (const mutate of [m => { m.accounting.received--; }, m => { m.records[0].code = 'HTTP_ERROR'; },
    m => { m.records.push({ ...m.records[0], ordinal: 1 }); m.accounting.attempts++; m.accounting.received *= 2; },
    m => { m.records[0].raw = '00001.raw'; }]) {
    const m = structuredClone(saved); mutate(m); replaceManifest(d.store, m);
    assert.equal((await c.replay('april', d.store)).code, 'INTEGRITY_ERROR'); }
});
test('Monthly repair1 retry timeline cannot claim15second wait while actually starting10seconds after529', async () => {
  const d = deps((q, n) => n === 1 ? { status: 529, received: 1 } : undefined);
  const source = await c.run(options(), d); assert.equal(source.code, null);
  assert.equal((await c.replay('april', d.store)).code, null);
  const m = JSON.parse(d.store.read('manifest.json')); assert.equal(m.records[0].endMs, 0); assert.equal(m.records[1].waitMs, 15000);
  m.records[1].startMs = 10000; m.records[1].endMs = 10000; replaceManifest(d.store, m);
  assert.equal((await c.replay('april', d.store)).code, 'INTEGRITY_ERROR', 'recorded actual retry interval must honor the full mandatory wait');
});
test('Monthly repair1 rejects admitted overflow and shortened second retry or RetryAfter timelines', async () => {
  const complete = deps(); await c.run(options(), complete);
  const cm = JSON.parse(complete.store.read('manifest.json')); cm.records[0].received = 64000001; cm.accounting.received = 64000001;
  replaceManifest(complete.store, cm); assert.equal((await c.replay('april', complete.store)).code, 'INTEGRITY_ERROR');
  const d = deps((q, n) => n <= 2 ? { status: 529, received: 1, retryAfter: '20' } : undefined);
  await c.run(options(), d); const saved = JSON.parse(d.store.read('manifest.json'));
  for (const [index, elapsed] of [[1, 19999], [2, 44999]]) {
    const m = structuredClone(saved); m.records[index].startMs = m.records[index - 1].endMs + elapsed;
    m.records[index].endMs = m.records[index].startMs; replaceManifest(d.store, m);
    assert.equal((await c.replay('april', d.store)).code, 'INTEGRITY_ERROR');
  }
});
