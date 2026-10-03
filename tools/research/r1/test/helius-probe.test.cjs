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
function addressOf(n) { const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let x = BigInt('0x' + Buffer.alloc(32, n).toString('hex')), out = '';
  while (x) { out = alphabet[Number(x % 58n)] + out; x /= 58n; } return out; }
const ranges3 = [[1775001600, 1782777600], [1782777600, 1785369600], [1785369600, 1787961600]];
const fullRow = (extra = {}) => ({ slot: 410195947, transactionIndex: 18, blockTime: 1775001600,
  transaction: { signatures: [signature], message: { accountKeys: [address], instructions: [] } }, meta: { err: null, fee: '__fee__', preBalances: [], postBalances: [] }, ...extra });
const fullBody = (rows = [], token = null) => body(rows, token).toString().replaceAll('"__fee__"', '9007199254740993123');
const fullBytes = (rows = [], token = null) => Buffer.from(fullBody(rows, token));
const options3 = credits => ({ enabled: true, stage: 'H3', creditsRemaining: credits ?? '999990', output: p.OUTPUT3 });
function deps3(handler) {
  let time = 0; const d = deps(); d.preflight = { retainedBytes: 9000000, observedAt: '2026-10-03T12:00:00Z' };
  d.now = () => time; d.wait = async ms => { time += ms; }; d.utcNow = () => Date.parse('2026-10-03T12:00:00Z') + time;
  d.selectorRaw = Buffer.from(JSON.stringify({ header: { number: 410195947, timestamp: 1775001600 },
    transactions: Array.from({ length: 36 }, (_, n) => ({ transactionIndex: n + 18, accountKeys: [addressOf(n + 1)] })) }));
  d.selectorManifest = Buffer.from('synthetic selector manifest'); const selection = p.selectCohort(d.selectorRaw, digest(d.selectorRaw), d.selectorManifest, digest(d.selectorManifest));
  d.selectorHashes = { rawHash: digest(d.selectorRaw), manifestHash: digest(d.selectorManifest), cohortHash: selection.cohortHash };
  d.transport = async (q, o) => { const call = { q, start: time }; d.calls.push(call); time++;
    const r = handler ? handler(q, d.calls.length, call) : fullBytes();
    if (r.code) { o.onChunk(r.received ?? 0); return { attempted: true, ...r }; }
    o.onChunk(r.length); return { code: null, status: 200, received: r.length, bytes: r, attempted: true }; };
  return d;
}
test('H3 full-mode fixed filters and full response reject whole unsafe page before raw/counts', async () => {
  const q = p.query3(addressOf(1), 0, null); assert.equal(q.body.params[1].transactionDetails, 'full');
  assert.deepEqual(q.body.params[1], { commitment: 'finalized', transactionDetails: 'full', encoding: 'json', maxSupportedTransactionVersion: 1,
    sortOrder: 'asc', limit: 1000, filters: { blockTime: { gte: 1775001600, lt: 1782777600 }, status: 'any', tokenAccounts: 'all' } });
  const good = p.admit3(fullBytes([fullRow()]), q); assert.equal(good.code, null); assert.equal(good.rows.length, 1);
  for (const blockTime of [null, undefined, 1788134400, 1790553600, 1782777600, 1775001599]) {
    const d = deps3(() => fullBytes([fullRow(), fullRow({ blockTime })])), r = await p.runH3(options3(), d);
    assert.equal(r.code, 'RESPONSE_INVALID'); assert.equal(r.summary.rows, 0); assert.equal(d.calls.length, 1);
    assert.equal([...d.store.files.keys()].some(n => n.endsWith('.raw')), false); assert.ok(r.accounting.received > 0);
  }
  assert.equal(p.admit3(fullBytes([fullRow({ transaction: { signatures: ['invalid'], message: {} } })]), q).code, 'RESPONSE_INVALID');
});
test('H3 credit reservation and finite capacity stop before unfit starts without resetting H1', async () => {
  const d = deps3(), r = await p.runH3(options3('99'), d); assert.equal(r.code, 'CREDIT_LIMIT'); assert.equal(d.calls.length, 0);
  const limited = deps3(), stop = await p.runH3(options3('100'), limited); assert.equal(stop.code, 'CREDIT_LIMIT'); assert.equal(limited.calls.length, 1);
  assert.equal(stop.accounting.creditsReserved, 100); assert.equal(stop.accounting.cumulative.pre.attempts, 100);
  assert.equal(stop.accounting.cumulative.pre.received, 8505085); assert.equal(stop.accounting.cumulative.pre.elapsedMs, 196764);
  assert.equal(stop.accounting.cumulative.post.attempts, 101); assert.equal(stop.accounting.cumulative.post.received, 8505085 + stop.accounting.received);
  assert.ok(stop.summary.streams.some(s => s.status === 'UNQUERIED')); assert.equal(stop.summary.fullD1UpperBound, null);
  const disk = deps3(); disk.preflight.retainedBytes = 7000000000; assert.equal((await p.runH3(options3(), disk)).code, 'DISK_LIMIT'); assert.equal(disk.calls.length, 0);
});
test('H3 actual transport keeps synthetic authentication out of successful/error outputs and bounds timeout/caps', async t => {
  const secret = 'synthetic-h3-secret', server = http.createServer((req, res) => res.end(fullBytes([fullRow({ memo: secret })])));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const q = p.query3(addressOf(1), 0, null), r = await p.send(q, { loopback: `http://127.0.0.1:${server.address().port}`, secretLoader: () => secret });
  assert.equal(r.code, 'SECRET_EXPOSURE'); assert.equal(r.bytes, undefined); assert.equal(JSON.stringify(r).includes(secret), false);
  const tooLarge = await p.send(q, { loopback: `http://127.0.0.1:${server.address().port}`, secretLoader: () => secret, responseLimit: 10 }); assert.equal(tooLarge.code, 'RESPONSE_LIMIT');
  const delayed = http.createServer((req, res) => res.write('partial')); await new Promise(resolve => delayed.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => { delayed.closeAllConnections(); delayed.close(resolve); }));
  const timed = await p.send(q, { loopback: `http://127.0.0.1:${delayed.address().port}`, secretLoader: () => secret, deadlineMs: 15 });
  assert.equal(timed.code, 'TIMEOUT'); assert.equal(timed.ownedDeadline, true); assert.equal(timed.bytes, undefined);
});
test('H3 fixed cohort round-robin membership dedup raw replay and H4 exact censored sensitivity', async () => {
  const d = deps3(q => fullBytes([fullRow({ blockTime: q.body.params[1].filters.blockTime.gte,
    transaction: { signatures: [signatureOf(ranges3.findIndex(r => r[0] === q.body.params[1].filters.blockTime.gte) + 1)], message: { accountKeys: [address], instructions: [] } } })]));
  const r = await p.runH3(options3(), d); assert.equal(r.code, null); assert.equal(d.calls.length, 108);
  assert.equal(r.summary.rows, 3); assert.equal(r.summary.membershipRows, 108); assert.equal(r.summary.statusCounts.success, 3);
  assert.deepEqual(d.calls.slice(0, 3).map(c => c.q.body.params[1].filters.blockTime), ranges3.map(([gte, lt]) => ({ gte, lt })));
  assert.ok(d.calls.every((c, n) => !n || c.start - d.calls[n - 1].start >= 250));
  assert.equal(r.accounting.creditsReserved, 10800); assert.equal(r.summary.estimatedCredits, 1080); assert.equal(r.accounting.actualCredits, null);
  const replay = await p.replay3(d.store, { utcNow: d.utcNow }); assert.equal(replay.code, null); assert.equal(replay.summaryHash, r.summaryHash);
  const manifest = JSON.parse(d.store.files.get('manifest.json')); manifest.operational.elapsedMs += 123; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(manifest)));
  assert.equal((await p.replay3(d.store, { utcNow: d.utcNow })).summaryHash, r.summaryHash);
  const f = p.forecast(replay.summary); assert.equal(f.fullD1UpperBound, null); assert.equal(f.completeAddresses, 36);
  assert.equal(f.scenarios.find(s => s.wallets === 1000).pooled.transactions, '3600');
  assert.equal(f.scenarios.find(s => s.wallets === 1000).pooled.estimatedCredits, '36000');
  assert.equal(f.scenarios.find(s => s.wallets === 1000).months[0].walletHistoryFits, true);
  const incomplete = deps3(), partial = await p.runH3(options3('100'), incomplete); assert.equal(p.forecast(partial.summary).scenarios, null);
  const raw = [...d.store.files.keys()].find(n => n.endsWith('.raw')); d.store.files.set(raw, Buffer.from('tampered')); assert.equal((await p.replay3(d.store, { utcNow: d.utcNow })).code, 'INTEGRITY_ERROR');
  const conflict = deps3((q, n) => fullBytes([fullRow({ blockTime: q.body.params[1].filters.blockTime.gte, memo: String(n) })]));
  const bad = await p.runH3(options3(), conflict); assert.equal(bad.code, 'IMMUTABLE_CONFLICT'); assert.equal(conflict.calls.length, 2); assert.equal(bad.summary.rows, 1);
});
test('H3 same-query retries obey eligible failures backoff Retry-After and hard retry ceilings', async () => {
  const d = deps3((q, n) => n < 4 ? { code: 'HTTP_ERROR', status: 529, received: 7 } : fullBytes());
  const r = await p.runH3(options3(), d); assert.equal(r.code, null); assert.equal(r.accounting.retries, 3); assert.equal(r.accounting.attempts, 111);
  assert.equal(r.accounting.creditsReserved, 11100); assert.equal(r.accounting.received, 21 + 108 * fullBytes().length);
  for (const [n, wait] of [[1, 5000], [2, 15000], [3, 45000]]) {
    assert.deepEqual(d.calls[n].q, d.calls[0].q); assert.ok(d.calls[n].start - d.calls[n - 1].start >= wait + 1);
  }
  assert.equal((await p.replay3(d.store, { utcNow: d.utcNow })).summaryHash, r.summaryHash);
  for (const response of [{ code: 'HTTP_ERROR', status: 403, received: 3 }, { code: 'NETWORK_ERROR', received: 3 },
    { code: 'HTTP_ERROR', status: 429, received: 3, retryAfterInvalid: true }, { code: 'HTTP_ERROR', status: 503, received: 3, retryAfter: '99999999999999999999999' }]) {
    const refused = deps3(() => response), bad = await p.runH3(options3(), refused); assert.equal(refused.calls.length, 1); assert.equal(bad.status, 'INCOMPLETE');
    assert.equal(bad.accounting.received, 3); assert.equal(bad.accounting.creditsReserved, 100);
  }
  const exhausted = deps3(() => ({ code: 'HTTP_ERROR', status: 503, received: 1 })); assert.equal((await p.runH3(options3(), exhausted)).code, 'HTTP_ERROR'); assert.equal(exhausted.calls.length, 4);
  const honored = deps3((q, n) => n === 1 ? { code: 'HTTP_ERROR', status: 429, received: 1, retryAfter: '9' } : fullBytes());
  assert.equal((await p.runH3(options3(), honored)).code, null); assert.ok(honored.calls[1].start >= 9001);
  const cursor = deps3(q => fullBytes([fullRow({ blockTime: q.body.params[1].filters.blockTime.gte,
    transaction: { signatures: [signatureOf(ranges3.findIndex(r => r[0] === q.body.params[1].filters.blockTime.gte) + 1)], message: { accountKeys: [address], instructions: [] } } })], '410195947:18'));
  const stuck = await p.runH3(options3(), cursor); assert.equal(stuck.code, 'CURSOR_INVALID'); assert.equal(cursor.calls.length, 109);
});
test('H3 ordinary status lexemes hard budgets Retry-After headers and offline forecast checks', async t => {
  const ordinalSignature = n => { const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz', bytes = Buffer.alloc(64); bytes.writeUInt32BE(n, 60);
    let x = BigInt('0x' + bytes.toString('hex')), out = '', zeros = 0; while (bytes[zeros] === 0) zeros++;
    while (x) { out = alphabet[Number(x % 58n)] + out; x /= 58n; } return '1'.repeat(zeros) + out; };
  const item = (q, n, err = null) => fullRow({ transactionIndex: n, blockTime: q.body.params[1].filters.blockTime.gte,
    transaction: { signatures: [ordinalSignature(n)], message: { accountKeys: [address], instructions: [] } }, meta: { err, fee: '__fee__' } });
  const statuses = deps3(q => { const offset = ranges3.findIndex(r => r[0] === q.body.params[1].filters.blockTime.gte) * 3;
    return fullBytes([item(q, offset + 1, null), item(q, offset + 2, {}), item(q, offset + 3, 'unknown')]); });
  const good = await p.runH3(options3(), statuses); assert.deepEqual(good.summary.statusCounts, { success: 3, failed: 3, unknown: 3 });
  assert.ok([...statuses.store.files].some(([name, bytes]) => name.endsWith('.raw') && bytes.includes(Buffer.from('9007199254740993123'))));
  const result = await p.replay3(statuses.store, { utcNow: statuses.utcNow }); assert.equal(result.code, null);
  const projection = p.forecast(result.summary, result.accounting); assert.equal(projection.scenarios[0].pooled.transactions, '10800');
  assert.ok(BigInt(projection.scenarios[0].pooled.retainedBytes) >= BigInt(projection.scenarios[0].pooled.retainedRawBytes));
  assert.equal(projection.scenarios[0].pooled.pageRoundedCreditMinimum, '40000'); assert.equal(projection.sample.failedShare.numerator, '3');
  assert.equal((await runCli(['--forecast', p.OUTPUT3 + '\\manifest.json'], { store: statuses.store, utcNow: statuses.utcNow })).fullD1UpperBound, null);
  const altered = JSON.parse(statuses.store.files.get('manifest.json')); altered.records[0].received++;
  statuses.store.files.set('manifest.json', Buffer.from(JSON.stringify(altered))); assert.equal((await p.replay3(statuses.store, { utcNow: statuses.utcNow })).code, 'INTEGRITY_ERROR');
  const total = deps3((q, n) => fullBytes([item(q, n)], '410195947:' + n)), cap = await p.runH3(options3(), total);
  assert.equal(cap.code, 'ATTEMPT_LIMIT'); assert.equal(total.calls.length, 1000); assert.equal(cap.accounting.creditsReserved, 100000);
  assert.ok(JSON.parse(total.store.files.get('manifest.json')).checkpoints.some(c => c.name === 'attempts' && c.value === 800));
  assert.equal((await p.replay3(total.store, { utcNow: total.utcNow })).summaryHash, cap.summaryHash);
  const retryCap = deps3((q, n) => n % 4 === 0 ? fullBytes() : { code: 'HTTP_ERROR', status: 503, received: 1 });
  const retryStop = await p.runH3(options3(), retryCap); assert.equal(retryStop.code, 'RETRY_LIMIT'); assert.equal(retryStop.accounting.retries, 150); assert.equal(retryCap.calls.length, 201);
  const before = deps3(), write = before.store.write; before.store.write = (name, bytes) => { if (name === 'attempt.json') throw Error('synthetic'); write(name, bytes); };
  const storage = await p.runH3(options3(), before); assert.equal(storage.code, 'STORAGE_ERROR'); assert.equal(before.calls.length, 0); assert.equal(storage.accounting.attempts, 0);
  for (const header of [['5', '7'], 'Wed, 01 Jan 2030 00:00:00 GMT', '9', '8'.repeat(129)]) {
    const server = http.createServer((req, res) => { res.statusCode = 503; res.setHeader('Retry-After', header); res.end('discarded'); });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
    const response = await p.send(p.query3(addressOf(1), 0), { loopback: `http://127.0.0.1:${server.address().port}`, secretLoader: () => 'synthetic-secret' });
    assert.equal(response.code, 'HTTP_ERROR'); assert.equal(response.bytes, undefined); assert.equal(response.retryAfterInvalid, header !== '9');
    assert.equal(response.retryAfter, header === '9' ? '9' : null);
  }
});
test('H3 prepublication elapsed has explicit scope and consistent cumulative aliases', async () => {
  const d = deps3(), write = d.store.write; let boundary;
  d.store.write = (name, bytes) => { if (name === 'summary.json') boundary = d.now(); write(name, bytes); if (name === 'summary.json' || name === 'manifest.json') d.wait(17); };
  const r = await p.runH3(options3(), d);
  assert.equal(r.accounting.elapsedScope, 'PRE_PUBLICATION'); assert.equal(r.accounting.prePublicationElapsedMs, boundary);
  assert.equal(r.accounting.elapsedMs, boundary);
  assert.equal(r.accounting.cumulative.step.elapsedMs, boundary);
  assert.equal(r.accounting.cumulative.post.elapsedMs, 196764 + boundary);
});
test('H3 late final publication preserves semantic data while overall time budget stays independently measured', async () => {
  const d = deps3(), write = d.store.write;
  d.store.write = (name, bytes) => { write(name, bytes); if (name === 'manifest.json') d.wait(7200001); };
  const r = await p.runH3(options3(), d);
  assert.equal(r.code, null); assert.equal(r.status, 'COMPLETE'); assert.ok(r.summaryHash);
  assert.equal(r.accounting.elapsedScope, 'PRE_PUBLICATION'); assert.ok(r.accounting.prePublicationElapsedMs < d.now());
  const replay = await p.replay3(d.store, { utcNow: d.utcNow });
  assert.equal(replay.code, null); assert.equal(replay.status, 'COMPLETE'); assert.equal(replay.summaryHash, r.summaryHash);
  assert.equal(replay.runBudget, 'UNMEASURED'); assert.equal(p.forecast(replay.summary, replay.accounting).runBudget, 'UNMEASURED');
  assert.equal(d.now() <= 7200000 ? 'PASS' : 'OVERRUN', 'OVERRUN'); assert.notEqual(replay.runBudget, 'PASS');
});
test('H3 failed transport terminal elapsed uses truthful prepublication scope', async () => {
  const d = deps3(() => ({ code: 'HTTP_ERROR', status: 403, received: 3 })), write = d.store.write; let boundary;
  d.store.write = (name, bytes) => { if (name === 'summary.json') boundary = d.now(); write(name, bytes); if (name === 'summary.json' || name === 'manifest.json') d.wait(17); };
  const r = await p.runH3(options3(), d);
  assert.equal(r.code, 'HTTP_ERROR'); assert.equal(d.calls.length, 1);
  assert.equal(r.accounting.elapsedScope, 'PRE_PUBLICATION'); assert.equal(r.accounting.prePublicationElapsedMs, boundary);
  assert.equal(r.accounting.elapsedMs, boundary); assert.equal(r.accounting.cumulative.step.elapsedMs, boundary);
  assert.equal(r.accounting.cumulative.post.elapsedMs, 196764 + boundary);
  assert.equal(r.accounting.received, 3); assert.equal(r.accounting.creditsReserved, 100);
});
test('H3 reserves full pending wait and timeout inside source cutoff and rechecks after wait', async () => {
  const late = deps3(), lateWrite = late.store.write;
  late.store.write = (name, bytes) => { lateWrite(name, bytes); if (name === 'attempt.json') late.wait(6840001); };
  assert.equal((await p.runH3(options3(), late)).code, 'TIME_LIMIT'); assert.equal(late.calls.length, 0);
  const retry = deps3(() => ({ code: 'HTTP_ERROR', status: 503, received: 1 })), retryWrite = retry.store.write;
  retry.store.write = (name, bytes) => { retryWrite(name, bytes); if (name === 'attempt.json') retry.wait(6835000); };
  const rejected = await p.runH3(options3(), retry); assert.equal(rejected.code, 'TIME_LIMIT'); assert.equal(retry.calls.length, 1);
  assert.equal(retry.now(), 6835001, 'unfit full backoff must stop before waiting');
  const oversleep = deps3(() => ({ code: 'HTTP_ERROR', status: 503, received: 1 })), wait = oversleep.wait;
  oversleep.wait = async ms => { await wait(ms + 6900000); };
  assert.equal((await p.runH3(options3(), oversleep)).code, 'TIME_LIMIT'); assert.equal(oversleep.calls.length, 1);
});
test('H3 scoped raw-write timing replay consistency and H4 never imply overall budget PASS', async () => {
  const d = deps3(), write = d.store.write;
  d.store.write = (name, bytes) => { write(name, bytes); if (name.endsWith('.raw')) d.wait(17); };
  const r = await p.runH3(options3(), d), manifest = JSON.parse(d.store.files.get('manifest.json'));
  assert.equal(r.accounting.elapsedScope, 'PRE_PUBLICATION'); assert.equal(r.accounting.prePublicationElapsedMs, d.now());
  assert.deepEqual(manifest.accounting, r.accounting); assert.equal(manifest.operational.elapsedScope, 'PRE_PUBLICATION');
  assert.equal(manifest.operational.prePublicationElapsedMs, r.accounting.elapsedMs);
  assert.equal((await p.replay3(d.store, { utcNow: d.utcNow })).runBudget, 'UNMEASURED');
  assert.equal(p.forecast(r.summary, r.accounting).runBudget, 'UNMEASURED');
  manifest.accounting.prePublicationElapsedMs++; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(manifest)));
  assert.equal((await p.replay3(d.store, { utcNow: d.utcNow })).code, 'INTEGRITY_ERROR');
});
