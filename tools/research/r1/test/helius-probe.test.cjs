'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), http = require('node:http');
const p = require('../helius-probe.cjs'), { runCli } = require('../helius-probe-cli.cjs'), { digest } = require('../exploratory-probe.cjs');
const address = 'EiYg44SdUBXFJNq1MvPHd71LjMb9b2J5pCSqpjMStRva', signature = '1'.repeat(64);
function signatureOf(n) { const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let value = BigInt('0x' + Buffer.alloc(64, n).toString('hex')), out = ''; while (value) { out = alphabet[Number(value % 58n)] + out; value /= 58n; } return out; }
const row = (extra = {}) => ({ signature, slot: 410195947, transactionIndex: 18, blockTime: 1775001600, err: null, confirmationStatus: 'finalized', ...extra });
const body = (rows = [row()], token = null) => Buffer.from(JSON.stringify({ jsonrpc: '2.0', id: 1, result: { data: rows, paginationToken: token } }));
function memory() { const files = new Map(); let created = false; return { files, free: () => 100000000000,
  create() { if (created) throw Error('OUTPUT_EXISTS'); created = true; }, write(name, bytes) { if (files.has(name)) throw Error('OUTPUT_EXISTS'); files.set(name, Buffer.from(bytes)); },
  read(name) { if (!files.has(name)) throw Error('FILE_MISSING'); return files.get(name); }, list: () => [...files.keys()] }; }
const options = creditsRemaining => ({ enabled: true, stage: 'H1', creditsRemaining: creditsRemaining ?? '10', output: p.OUTPUT });
function deps(response = body()) { let time = 0; const calls = [], store = memory(); return { calls, store, now: () => time,
  source: { commit: 'a'.repeat(40), dirty: false }, runtime: 'v24.19.0',
  preflight: { checkpointMetadataBytes: 1234, observedAt: '2026-10-03T00:00:00Z' },
  transport: async (q, o) => { calls.push(q); time += 1; if (response.code) { o.onChunk(response.received ?? 0); return response; }
    o.onChunk(response.length); return { code: null, status: 200, received: response.length, bytes: response }; } }; }
test('H1 fixed server filter excludes holdout and whole unsafe page is discarded before raw/counts', async () => {
  const q = p.query(); assert.deepEqual(q.body.params, [address, { commitment: 'finalized', transactionDetails: 'signatures', sortOrder: 'asc', limit: 1000,
    filters: { blockTime: { gte: 1775001600, lt: 1782777600 }, status: 'any', tokenAccounts: 'none' } }]);
  for (const blockTime of [null, undefined, 1788134400, 1790553600, 1782777600, 1775001599]) {
    const d = deps(body([row(), row({ signature: signatureOf(2), blockTime })])), r = await p.runProbe(options(), d);
    assert.equal(r.code, 'RESPONSE_INVALID'); assert.equal(r.summary.rows, 0); assert.equal(d.calls.length, 1);
    assert.equal([...d.store.files.keys()].some(n => n.endsWith('.raw')), false); assert.ok(r.accounting.received > 0);
  }
  await replayCases();
});
test('H1 reserves ten Free credits and rejects insufficient quota before I/O or secret loading', async () => {
  const d = deps(), r = await p.runProbe(options('9'), d); assert.equal(r.code, 'CREDIT_LIMIT'); assert.equal(d.calls.length, 0); assert.equal(d.store.files.size, 0);
  const good = deps(), g = await p.runProbe(options(), good); assert.equal(g.code, null); assert.equal(g.accounting.creditsReserved, 10);
  assert.equal(g.accounting.actualCredits, null); assert.equal(g.summary.estimatedCredits, 10); assert.equal(g.accounting.cashMicrousd, '0');
  assert.equal(g.accounting.cumulative.pre.received, 8280342); assert.equal(g.accounting.cumulative.pre.retained, 6397448 + 1234);
  assert.equal(g.accounting.cumulative.post.attempts, 100);
});
test('default and malformed CLI are key-free and actual transport never exposes synthetic authentication', async t => {
  const d = deps(); assert.equal((await runCli([], d)).code, 'DISABLED'); assert.equal(d.calls.length, 0); assert.equal(d.store.files.size, 0);
  assert.equal((await runCli(['--enable-free', '--stage', 'H3', '--credits-remaining', '10', '--output', p.OUTPUT], d)).code, 'ARGUMENTS_INVALID');
  const secret = 'synthetic-authentication-sentinel', s = http.createServer((req, res) => res.end(body([row({ memo: secret })])));
  await new Promise(resolve => s.listen(0, '127.0.0.1', resolve)); t.after(() => new Promise(resolve => { s.closeAllConnections(); s.close(resolve); }));
  let loads = 0; const r = await p.send(p.query(), { loopback: `http://127.0.0.1:${s.address().port}`, secretLoader: () => { loads++; return secret; } });
  assert.equal(r.code, 'SECRET_EXPOSURE'); assert.equal(r.bytes, undefined); assert.equal(loads, 1); assert.equal(JSON.stringify(r).includes(secret), false);
  await transportCases(t);
});
test('fixed cohort selection is outcome-blind unique ASCII ordered and original-index anchored', () => {
  const second = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
  const rows = [{ transactionIndex: 21, accountKeys: [second], err: null }, { transactionIndex: 18, accountKeys: ['1'.repeat(32)], err: { synthetic: true } },
    { transactionIndex: 20, accountKeys: [second], err: null }];
  const raw = Buffer.from(JSON.stringify({ header: { number: 410195947, timestamp: 1775001600 }, transactions: rows }) + '\n'), manifest = Buffer.from('synthetic-manifest');
  const r = p.selectCohort(raw, digest(raw), manifest, digest(manifest)); assert.deepEqual(r.addresses, ['1'.repeat(32), second]);
  assert.equal(r.first, '1'.repeat(32)); const reversed = Buffer.from(JSON.stringify({ header: { number: 410195947, timestamp: 1775001600 }, transactions: [...rows].reverse() }) + '\n');
  assert.equal(p.selectCohort(reversed, digest(reversed), manifest, digest(manifest)).cohortHash, r.cohortHash);
});
test('H1 has exactly one attempt zero retries and refusal discards body charges bytes', async () => {
  const d = deps({ code: 'HTTP_ERROR', status: 403, received: 12 }), r = await p.runProbe(options(), d);
  assert.equal(r.code, 'HTTP_ERROR'); assert.equal(d.calls.length, 1); assert.equal(r.accounting.attempts, 1);
  assert.equal(r.accounting.received, 12); assert.equal(r.accounting.creditsReserved, 10); assert.equal(r.summary.rows, 0);
  assert.equal([...d.store.files.keys()].some(n => n.endsWith('.raw')), false);
  await exclusiveCases();
});
async function replayCases() {
  const d = deps(body([row({ memo: '9007199254740993123' }), row({ signature: signatureOf(2), transactionIndex: 19, err: {} }),
    row({ signature: signatureOf(3), transactionIndex: 20, err: 'unknown' })], '410195947:20'));
  const r = await p.runProbe(options(), d); assert.equal(r.code, null); assert.equal(r.status, 'COMPLETE');
  assert.deepEqual(r.summary.statusCounts, { success: 1, failed: 1, unknown: 1 }); assert.equal(r.summary.paginationToken, '410195947:20');
  assert.equal(r.summary.historyComplete, false); assert.equal(r.d1Evidence, false); assert.equal(r.d1Passed, false);
  const replay = await p.replay(d.store); assert.equal(replay.code, null); assert.equal(replay.summaryHash, r.summaryHash);
  const manifest = JSON.parse(d.store.files.get('manifest.json')); manifest.operational.elapsedMs += 123; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(manifest)));
  assert.equal((await p.replay(d.store)).summaryHash, r.summaryHash);
  const raw = [...d.store.files.keys()].find(n => n.endsWith('.raw')); d.store.files.set(raw, Buffer.from('changed')); assert.equal((await p.replay(d.store)).code, 'INTEGRITY_ERROR');
}
async function transportCases(t) {
  const secret = 'synthetic-sentinel', servers = [];
  for (const [handler, code, settings] of [[(req, res) => res.end(Buffer.alloc(50)), 'RESPONSE_LIMIT', { responseLimit: 10 }],
    [(req, res) => res.write('partial'), 'TIMEOUT', { deadlineMs: 15 }], [(req, res) => { res.statusCode = 403; res.end(secret); }, 'HTTP_ERROR', {}],
    [(req, res) => res.end(body([row({ memo: secret })]).toString().replace('synthetic', '\\u0073ynthetic')), 'SECRET_EXPOSURE', {}]]) {
    const s = http.createServer(handler); servers.push(s); await new Promise(resolve => s.listen(0, '127.0.0.1', resolve));
    const r = await p.send(p.query(), { loopback: `http://127.0.0.1:${s.address().port}`, secretLoader: () => secret, ...settings });
    assert.equal(r.code, code); assert.equal(r.bytes, undefined); assert.equal(JSON.stringify(r).includes(secret), false);
  }
  t.after(() => Promise.all(servers.map(s => new Promise(resolve => { s.closeAllConnections(); s.close(resolve); }))));
}
async function exclusiveCases() {
  const d = deps(); assert.equal((await p.runProbe(options(), d)).code, null); const count = d.calls.length;
  assert.equal((await p.runProbe(options(), d)).code, 'OUTPUT_EXISTS'); assert.equal(d.calls.length, count); assert.throws(() => p.fileStore('C:\\git\\secret'), /UNSAFE_PATH/);
  const conflict = deps(body([row(), row({ memo: 'different immutable content' })])); assert.equal((await p.runProbe(options(), conflict)).code, 'IMMUTABLE_CONFLICT');
  assert.equal([...conflict.store.files.keys()].some(n => n.endsWith('.raw')), false);
}
test('metadata scan is fixed stat-only bounded and rejects aliases errors and exhaustion', () => {
  const roots = p.SCAN_ROOTS; let accesses = 0;
  const stat = (directory, size = 0, link = false) => ({ isDirectory: () => directory, isFile: () => !directory, isSymbolicLink: () => link, size });
  const make = change => ({ now: () => 0, fs: { lstatSync(file) { accesses++; if (file === roots[0]) return change?.stat ?? stat(true);
    if (file === roots[0] + '\\sample') return stat(false, 7); const error = Error('secret path'); error.code = change?.error ?? 'ENOENT'; throw error; },
    realpathSync: { native: file => change?.alias ? file + '-alias' : file }, readdirSync: () => change?.names ?? ['sample'] } });
  const report = p.scanMetadata(make()); assert.equal(report.retainedBytes, 7); assert.equal(report.roots[1].status, 'ABSENT');
  assert.equal(report.roots[1].bytes, 0); assert.ok(accesses < 100);
  for (const change of [{ stat: stat(true, 0, true) }, { alias: true }, { error: 'EACCES' }, { names: Array(10001).fill('sample') }])
    assert.throws(() => p.scanMetadata(make(change)), error => ['PREFLIGHT_ERROR', 'PREFLIGHT_LIMIT'].includes(String(error)) && !String(error).includes('secret'));
  let time = 0; const slow = make(); slow.now = () => time += 16000; assert.throws(() => p.scanMetadata(slow), error => error === 'PREFLIGHT_LIMIT');
});
test('pre-transport attempt-file failure reports zero actual attempts throughout accounting', async () => {
  const d = deps(), write = d.store.write;
  d.store.write = (name, bytes) => { if (name === 'attempt.json') throw Error('synthetic storage failure'); write(name, bytes); };
  const r = await p.runProbe(options(), d); assert.equal(r.code, 'STORAGE_ERROR'); assert.equal(d.calls.length, 0);
  assert.equal(r.accounting.attempts, 0); assert.equal(r.summary.attempts, 0); assert.equal(r.accounting.received, 0);
  assert.equal(r.accounting.cumulative.post.attempts, 99);
  assert.equal(JSON.parse(d.store.files.get('manifest.json')).records[0].attempted, false);
  const thrown = deps(); thrown.transport = async () => { thrown.calls.push(true); throw Error('synthetic transport failure'); };
  const failed = await p.runProbe(options(), thrown); assert.equal(failed.code, 'TRANSPORT_ERROR'); assert.equal(failed.accounting.attempts, 1);
});
test('replay bounds original recorded metadata separately from mutable manifest packaging', async () => {
  const d = deps(), r = await p.runProbe(options(), d); assert.equal(r.code, null);
  const original = Buffer.from(d.store.files.get('manifest.json')), manifest = JSON.parse(original);
  const rawBytes = d.store.files.get('000.raw').length;
  assert.ok(r.accounting.retained < rawBytes + p.CONFIG.limits.metadata);
  manifest.accounting.retained = 9999999;
  manifest.accounting.cumulative.step.retained = 9999999;
  manifest.accounting.cumulative.post.retained = manifest.accounting.cumulative.pre.retained + 9999999;
  d.store.files.set('manifest.json', Buffer.from(JSON.stringify(manifest)));
  assert.equal((await p.replay(d.store)).code, 'INTEGRITY_ERROR');
  manifest.accounting.retained = rawBytes + p.CONFIG.limits.metadata;
  manifest.accounting.cumulative.step.retained = manifest.accounting.retained;
  manifest.accounting.cumulative.post.retained = manifest.accounting.cumulative.pre.retained + manifest.accounting.retained;
  d.store.files.set('manifest.json', Buffer.from(JSON.stringify(manifest)));
  assert.equal((await p.replay(d.store)).summaryHash, r.summaryHash);
  manifest.accounting.retained++;
  manifest.accounting.cumulative.step.retained++;
  manifest.accounting.cumulative.post.retained++;
  d.store.files.set('manifest.json', Buffer.from(JSON.stringify(manifest)));
  assert.equal((await p.replay(d.store)).code, 'INTEGRITY_ERROR');
  const telemetry = JSON.parse(original); telemetry.operational.elapsedMs += 123;
  d.store.files.set('manifest.json', Buffer.from(JSON.stringify(telemetry)));
  assert.equal((await p.replay(d.store)).summaryHash, r.summaryHash);
});
