'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const p = require('../exploratory-probe-v3.cjs');
const v2 = require('../exploratory-probe-v2.cjs');
const anchor = p.WINDOWS[0], unix = Date.parse(anchor) / 1000;
const query = () => p.query('resolver', anchor, undefined, undefined, unix);
function group(responses, initialTime = 0, denied = null, state = { retries: 0, groups: 0 }) {
  let time = initialTime; const calls = [], waits = [], records = [];
  const context = { now: () => time, sleep: async ms => { waits.push(ms); time += ms; },
    room: ms => time + ms + 45000 > 7200000 ? 'TIME_LIMIT' : denied,
    attempt: async meta => { calls.push(meta); const r = { attempted: true, received: 7, status: 529, code: 'HTTP_ERROR', ...responses[Math.min(calls.length - 1, responses.length - 1)], ...meta, startMs: time };
      time += r.duration || 1; r.endMs = time; records.push(r); return r; },
    prevent: (meta, code) => { const r = { ...meta, code, attempted: false, received: 0, status: null, startMs: time, endMs: time }; records.push(r); return r; } };
  return { calls, waits, records, state, run: () => p.retryGroup(query(), context, state) };
}
test('eligible status and owned deadline retries preserve exact identity and failed-byte accounting', async () => {
  for (const failure of [{ code: 'HTTP_ERROR', status: 529 }, { code: 'HTTP_ERROR', status: 503 }, { code: 'HTTP_ERROR', status: 429 },
    ...[null, 200, 529, 503, 429].map(status => ({ code: 'TIMEOUT', status, deadlineOwned: true, duration: 45000 }))]) {
    const g = group([failure, { code: null, status: 200, received: 11, size: 11 }]); const r = await g.run();
    assert.equal(r.code, null); assert.equal(g.calls.length, 2); assert.deepEqual(g.waits, [15000]); assert.equal(r.received, 18);
    assert.equal(r.attempts, 2); assert.equal(r.size, 11); assert.equal(g.calls[0].queryIdentity, g.calls[1].queryIdentity);
    assert.equal(g.records[0].disposition, 'RETRY'); assert.equal(g.records[1].disposition, 'FINAL');
  }
});
test('four retry and global150 exhaustion never retry terminal transport or admission failures', async () => {
  const g = group([{ code: 'HTTP_ERROR', status: 529 }]); assert.equal((await g.run()).code, 'HTTP_ERROR');
  assert.equal(g.calls.length, 5); assert.deepEqual(g.waits, [15000, 45000, 120000, 300000]); assert.equal(g.state.retries, 4);
  const global = group([{ code: 'HTTP_ERROR', status: 529 }], 0, null, { retries: 149, groups: 0 }); await global.run();
  assert.equal(global.calls.length, 2); assert.equal(global.state.retries, 150); assert.equal(global.records.at(-1).disposition, 'GLOBAL_EXHAUSTED');
  for (const failure of [{ code: 'HTTP_ERROR', status: 500 }, { code: 'TIMEOUT', status: 200, deadlineOwned: false },
    { code: 'TIMEOUT', status: 404, deadlineOwned: true }, ...['NETWORK_ERROR', 'PARTIAL_RESPONSE', 'RESPONSE_INVALID', 'RESPONSE_LIMIT', 'STORAGE_ERROR'].map(code => ({ code, status: 200 }))]) {
    const n = group([failure]); assert.equal((await n.run()).code, failure.code); assert.equal(n.calls.length, 1); assert.deepEqual(n.waits, []);
  }
});
test('Retry-After is exact bounded delta seconds with invalid and huge headers stopping safely', async () => {
  const good = group([{ code: 'HTTP_ERROR', status: 429, retryAfter: ' 00060 ' }, { code: null, status: 200 }]);
  assert.equal((await good.run()).code, null); assert.deepEqual(good.waits, [60000]); assert.equal(good.calls[1].retryAfterSeconds, '60');
  for (const retryAfter of ['tomorrow', '-1', '1.5', 'Wed, 21 Oct 2026 07:28:00 GMT', '1'.repeat(129), ['15', '45'], '\u0000']) {
    const bad = group([{ retryAfter }]); assert.equal((await bad.run()).code, 'RETRY_AFTER_INVALID'); assert.equal(bad.calls.length, 1); assert.deepEqual(bad.waits, []);
  }
  const huge = group([{ retryAfter: '9'.repeat(128) }]); assert.equal((await huge.run()).code, 'TIME_LIMIT'); assert.deepEqual(huge.waits, []);
});
test('full wait plus45 seconds and every remaining capacity must fit before sleeping', async () => {
  const short = group([{}], 7200000 - 59999); assert.equal((await short.run()).code, 'TIME_LIMIT'); assert.deepEqual(short.waits, []);
  for (const code of ['ATTEMPT_LIMIT', 'RECEIVED_LIMIT', 'DISK_LIMIT', 'FREE_SPACE_LIMIT', 'STORAGE_ERROR']) {
    const g = group([{}], 0, code); assert.equal((await g.run()).code, code); assert.deepEqual(g.waits, []);
    assert.equal(g.calls.length, 1); assert.equal(g.records.at(-1).attempted, false); assert.equal(g.records.at(-1).disposition, 'BUDGET_PREVENTED');
  }
});
function memory() { const files = new Map(); let made = false; return { files, free: () => 100000000000,
  create() { if (made) throw Error('OUTPUT_EXISTS'); made = true; }, write(name, b) { if (files.has(name)) throw Error('OUTPUT_EXISTS'); files.set(name, Buffer.from(b)); },
  read(name) { if (!files.has(name)) throw Error('FILE_MISSING'); return files.get(name); } }; }
function deps(alter) {
  let time = 0; const calls = [], store = memory();
  return { store, calls, source: { commit: 'a'.repeat(40), dirty: false }, runtime: 'v24.99.1', now: () => time, sleep: async ms => { time += ms; },
    transport: async (q, options) => { calls.push(q); const start = Date.parse(q.anchor) / 1000;
      const headers = [100, 101, 102, 103].map((number, i) => ({ number, timestamp: start + [0, 300, 599, 600][i], hash: `h${number}`, parentNumber: number - 1, parentHash: `h${number - 1}` }));
      const bytes = q.kind === 'resolver' ? Buffer.from(JSON.stringify({ block_number: q.time === start ? 100 : 103 }))
        : Buffer.from(headers.filter(h => h.number >= q.from && h.number <= q.to).map(header => JSON.stringify({ header })).join('\n') + '\n');
      const response = alter?.(q, calls.length); if (response) { options.onChunk(response.received || 0); return response; }
      options.onChunk(bytes.length); return { code: null, status: 200, bytes }; } };
}
test('v3 retains only recovered bodies and replays retry grouping timing cumulative counters without I/O', async () => {
  let failed = false; const d = deps(q => { if (q.kind === 'A' && !failed) { failed = true; return { code: 'HTTP_ERROR', status: 529, received: 23 }; } });
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d); assert.equal(r.status, 'COMPLETE');
  assert.equal(r.accounting.retries, 1); assert.equal(r.summary.windows[0].A.requests, 2); assert.equal(r.summary.windows[0].A.rejectedBytes, 23);
  assert.ok(r.summary.windows[0].A.elapsedMs >= 15000); assert.equal(r.summary.windows[0].A.processedSlots, 3);
  assert.equal(r.accounting.cumulative.pre.attempts, 99); assert.equal(r.accounting.cumulative.post.attempts, 99 + r.accounting.attempts);
  assert.equal(r.accounting.cumulative.post.received, 8280342 + r.accounting.received);
  const original = d.store.files.get('manifest.json'); const m = JSON.parse(original); assert.equal(m.config.retryPolicy.version, 'exploratory-backoff-v1');
  assert.equal(m.records.filter(x => x.code === 'HTTP_ERROR').length, 1); assert.ok(m.records.filter(x => x.code).every(x => !x.raw));
  const replay = await p.replay(d.store); assert.equal(replay.code, null); assert.equal(replay.summaryHash, r.summaryHash); assert.deepEqual(replay.accounting, r.accounting);
  for (const change of [m => m.records.reverse(), m => { m.records.find(x => x.ordinal === 1).actualWaitMs = 0; },
    m => { m.records.splice(m.records.findIndex(x => x.code === 'HTTP_ERROR'), 1); }, m => { m.accounting.retries = 0; },
    m => { m.accounting.cumulative.post.received++; }]) {
    const changed = JSON.parse(original); change(changed); d.store.files.set('manifest.json', Buffer.from(JSON.stringify(changed) + '\n'));
    assert.equal((await p.replay(d.store)).code, 'INTEGRITY_ERROR');
  }
});
test('bounded loopback captures only sanitized Retry-After and owned timer timeout', async t => {
  let mode = 'headers'; const server = http.createServer((req, res) => { if (mode === 'headers') { res.writeHead(529, { 'Retry-After': '60', 'X-Secret': 'never-retain' }); res.end('discard-me'); }
    else if (mode === 'duplicates') { res.writeHead(429, ['Retry-After', '15', 'Retry-After', '45']); res.end('discard'); }
    else { res.writeHead(200); res.write('partial'); } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); t.after(() => { server.closeAllConnections(); server.close(); });
  const options = { loopback: `http://127.0.0.1:${server.address().port}/`, deadlineMs: 30, onChunk() {} };
  const r = await p.send(query(), options); assert.equal(r.code, 'HTTP_ERROR'); assert.equal(r.retryAfter, '60'); assert.equal(r.bytes, undefined); assert.equal(JSON.stringify(r).includes('never-retain'), false);
  mode = 'duplicates'; assert.equal((await p.send(query(), options)).retryAfterInvalid, true);
  mode = 'timeout'; const timed = await p.send(query(), options); assert.equal(timed.code, 'TIMEOUT'); assert.equal(timed.deadlineOwned, true); assert.equal(timed.bytes, undefined);
});
test('fixed v3 guards and provenance preserve v2 defaults and holdout rejection', async () => {
  assert.equal(p.CONFIG.queryVersion, 'exploratory-sqd-v3'); assert.equal(p.CONFIG.limits.retries, 4); assert.equal(v2.CONFIG.limits.retries, 0);
  assert.equal(v2.CONFIG.queryVersion, 'exploratory-sqd-v2'); assert.deepEqual(p.CONFIG.windows, v2.CONFIG.windows); assert.deepEqual(p.CONFIG.programs, v2.CONFIG.programs);
  const d = deps(); assert.equal((await p.runProbe({}, d)).code, 'DISABLED'); assert.equal(d.calls.length, 0);
  assert.equal((await p.runProbe({ enabled: true, output: v2.OUTPUT }, d)).code, 'UNSAFE_PATH'); assert.throws(() => p.fileStore('C:\\git\\unsafe'), /UNSAFE_PATH/);
  const bad = Buffer.from(JSON.stringify({ header: { number: 100, parentNumber: 99, hash: 'h100', parentHash: 'h99', timestamp: 1790553600 } }));
  assert.equal(p.admit(bad, p.query('header', anchor, 100, 100, unix)).code, 'RESPONSE_INVALID');
});
