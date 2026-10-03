'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const p = require('../exploratory-probe.cjs');
const { runCli } = require('../exploratory-probe-cli.cjs');
const anchor = '2026-04-01T00:00:00Z';
const header = { number: 100, hash: 'safeHash', parentNumber: 99, parentHash: 'parentHash', timestamp: 1775001600 };
const block = h => ({ header: h, transactions: [{ transactionIndex: 0, signatures: ['sig'], err: null }],
  instructions: [{ transactionIndex: 0, instructionAddress: [0], programId: p.PROGRAMS[0], data: 'raw' }],
  balances: [{ account: 0, pre: 900719925474099312345n.toString(), post: null }], tokenBalances: [] });
const body = h => Buffer.from(JSON.stringify(block(h)) + '\n');
function memory() {
  const files = new Map(); let created = false;
  return { files, free: () => 100000000000, create: () => { if (created) throw Error('OUTPUT_EXISTS'); created = true; },
    write: (name, bytes) => { if (files.has(name)) throw Error('OUTPUT_EXISTS'); files.set(name, Buffer.from(bytes)); },
    read: name => { if (!files.has(name)) throw Error('FILE_MISSING'); return files.get(name); } };
}
function deps(store = memory(), alter) {
  let time = 0; const calls = [];
  return { store, calls, now: () => time, sleep: async ms => { time += ms; }, source: { commit: 'a'.repeat(40), dirty: true },
    transport: async (spec, options) => {
      calls.push({ spec, time }); const unix = spec.anchor ? Date.parse(spec.anchor) / 1000 : header.timestamp;
      const h = { ...header, number: spec.slot || 100, timestamp: unix };
      let bytes = spec.kind === 'resolver' ? Buffer.from('{"block_number":100}') : body(h);
      if (alter) bytes = alter(spec, bytes, h);
      options.onChunk(bytes.length); return { code: null, status: 200, bytes, received: bytes.length };
    } };
}
test('disabled and invalid CLI perform zero I/O and sanitized diagnostics', async () => {
  const d = deps(); const disabled = await runCli([], d);
  assert.equal(disabled.code, 'DISABLED'); assert.equal(d.calls.length, 0); assert.equal(d.store.files.size, 0);
  for (const args of [['--enable-public'], ['--enable-public', '--output', 'C:\\secret'], ['--replay', p.OUTPUT + '\\manifest.json', '--replay', 'secret']]) {
    const r = await runCli(args, d); assert.equal(r.code, 'ARGUMENTS_INVALID'); assert.ok(!JSON.stringify(r).includes('secret'));
  }
  assert.equal(d.calls.length, 0); assert.equal(d.store.files.size, 0);
});
test('fixed single-slot queries reject forbidden anchors and unknown programs', () => {
  const q = p.query('payload', anchor, 100, p.PROGRAMS[0]);
  assert.equal(q.body.fromBlock, 100); assert.equal(q.body.toBlock, 100);
  assert.deepEqual(q.body.instructions[0].programId, [p.PROGRAMS[0]]);
  assert.throws(() => p.query('payload', '2026-09-28T00:00:00Z', 100, p.PROGRAMS[0]), /QUERY_INVALID/);
  assert.throws(() => p.query('payload', anchor, 100, 'unknown'), /QUERY_INVALID/);
});
test('safe response observes counts only and preserves quantities above Number precision', () => {
  const bytes = body(header), r = p.admit(bytes, { kind: 'payload', anchor, slot: 100, header });
  assert.equal(r.code, null); assert.equal(r.rows, 3); assert.equal(r.counts.balances.rows, 1);
  assert.equal(r.counts.balances.fields.post.null, 1); assert.equal(r.counts.balances.fields.pre.present, 1);
  assert.ok(bytes.includes(Buffer.from('900719925474099312345')));
});
test('forbidden extra missing-time mismatched and malformed responses publish zero rows', () => {
  const variants = [body({ ...header, timestamp: 1790553600 }), Buffer.concat([body(header), body(header)]),
    body({ ...header, timestamp: undefined }), body({ ...header, hash: 'different' }), body({ ...header, number: 101 }),
    Buffer.from('{"header":'), Buffer.from('{}\n'), Buffer.from('{"header":null}\n')];
  for (const bytes of variants) { const r = p.admit(bytes, { kind: 'payload', anchor, slot: 100, header });
    assert.equal(r.code, 'RESPONSE_INVALID'); assert.equal(r.rows, 0); assert.equal(r.counts, undefined); }
});
test('header guard prevents payload calls and rejected raw retention', async () => {
  const d = deps(memory(), (spec, bytes, h) => spec.kind === 'header' ? body({ ...h, timestamp: 1790553600 }) : bytes);
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.status, 'INCOMPLETE'); assert.equal(d.calls.length, 12);
  assert.equal(d.calls.filter(c => c.spec.kind === 'payload').length, 0);
  assert.equal([...d.store.files.keys()].filter(n => n.endsWith('.raw')).length, 6);
  assert.ok(r.summary.strata.every(s => s.status === 'UNEXECUTED'));
});
test('fixed sample runs thirty sequential spaced requests with false D1 flags and replay', async () => {
  const d = deps(), r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.status, 'COMPLETE'); assert.equal(d.calls.length, 30); assert.equal(r.summary.strata.length, 18);
  assert.equal(r.classification, 'EXPLORATORY'); assert.equal(r.d1Evidence, false); assert.equal(r.d1Passed, false);
  assert.equal(r.accounting.cashMicrousd, '0'); assert.equal(r.summary.fullD1UpperBound, null);
  for (let i = 1; i < d.calls.length; i++) assert.ok(d.calls[i].time - d.calls[i - 1].time >= 2000);
  const result = p.replay(d.store); assert.equal(result.code, null); assert.equal(result.summaryHash, r.summaryHash);
  const raw = [...d.store.files.keys()].find(n => n.endsWith('.raw'));
  d.store.files.set(raw, Buffer.from('changed')); assert.equal(p.replay(d.store).code, 'INTEGRITY_ERROR');
});
test('payload guard retains no rejected raw or rows and accounts every received byte', async () => {
  const d = deps(memory(), (spec, bytes, h) => spec.kind === 'payload' ? body({ ...h, number: 101 }) : bytes);
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.status, 'INCOMPLETE'); assert.equal(d.calls.length, 30);
  assert.equal([...d.store.files.keys()].filter(n => n.endsWith('.raw')).length, 12);
  assert.ok(r.summary.strata.every(s => s.rows === 0 && s.code === 'RESPONSE_INVALID'));
  assert.ok(r.accounting.received > 0); assert.equal(p.replay(d.store).summaryHash, r.summaryHash);
});
test('cumulative reservation limits and 80-percent checkpoint never reset', () => {
  for (const [limits, code] of [[{ attempts: 1 }, 'ATTEMPT_LIMIT'], [{ received: 3999999 }, 'RECEIVED_LIMIT'],
    [{ disk: 3999999 }, 'DISK_LIMIT'], [{ elapsed: 1 }, 'TIME_LIMIT']]) {
    const b = p.budget(limits, 0); assert.equal(b.reserve(0, 100000000000), null); b.charge(2000000, 2000000);
    assert.equal(b.reserve(2, 100000000000), code);
  }
  const b = p.budget({ attempts: 5 }, 0); for (let i = 0; i < 4; i++) { assert.equal(b.reserve(i, 100000000000), null); b.charge(1, 1); }
  assert.ok(b.state.checkpoints.includes('attempts')); assert.equal(b.reserve(4, 30000000000), 'FREE_SPACE_LIMIT');
});
test('transport fault stops without retry and sanitizes provider body', async () => {
  const d = deps(); d.transport = async (spec, opts) => { d.calls.push(spec); opts.onChunk(6); return { code: 'HTTP_ERROR', status: 403, received: 6, bytes: Buffer.from('secret') }; };
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.status, 'INCOMPLETE'); assert.equal(d.calls.length, 1); assert.equal(r.accounting.received, 6);
  assert.equal([...d.store.files.keys()].filter(n => n.endsWith('.raw')).length, 0);
  assert.ok(!JSON.stringify(r).includes('secret')); assert.ok(r.summary.strata.every(s => s.status === 'UNEXECUTED'));
});
test('exclusive output rejects reuse before any additional request', async () => {
  const d = deps(); await p.runProbe({ enabled: true, output: p.OUTPUT }, d); const n = d.calls.length;
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.code, 'OUTPUT_EXISTS'); assert.equal(d.calls.length, n);
  assert.equal((await p.runProbe({ enabled: true, output: 'C:\\git\\target' }, d)).code, 'UNSAFE_PATH');
});
test('filesystem storage rejects unsafe paths without creating outputs', () => {
  assert.throws(() => p.fileStore(path.resolve('.')), /UNSAFE_PATH/);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'exploratory-path-'));
  try { assert.throws(() => p.fileStore(dir), /UNSAFE_PATH/); assert.deepEqual(fs.readdirSync(dir), []); }
  finally { fs.rmdirSync(dir); }
});
test('runtime mismatch and unsafe options perform zero calls or writes', async () => {
  const d = deps(); d.runtime = 'v20.18.0';
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.code, 'RUNTIME_INVALID'); assert.equal(d.calls.length, 0); assert.equal(d.store.files.size, 0);
});
test('missing replay file and unsafe manifest names reject with fixed integrity error', async () => {
  const d = deps(); await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  const raw = [...d.store.files.keys()].find(n => n.endsWith('.raw')); d.store.files.delete(raw);
  assert.equal(p.replay(d.store).code, 'INTEGRITY_ERROR');
  assert.equal(p.replay(memory()).code, 'INTEGRITY_ERROR');
});
test('oversized response and invalid UTF8 are rejected before counts', () => {
  for (const bytes of [Buffer.alloc(2000001), Buffer.from([0xff])]) {
    const r = p.admit(bytes, { kind: 'payload', anchor, slot: 100, header });
    assert.equal(r.code, 'RESPONSE_INVALID'); assert.equal(r.rows, 0);
  }
});
async function server(t, handle) {
  const s = http.createServer(handle); await new Promise(resolve => s.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => { s.closeAllConnections(); s.close(resolve); }));
  return `http://127.0.0.1:${s.address().port}`;
}
test('real transport accumulates fragmented EOF and charges exact bytes', async t => {
  const url = await server(t, (req, res) => { res.write('abc'); setTimeout(() => res.end('def'), 5); });
  let charged = 0; const r = await p.send(p.query('resolver', anchor), { loopback: url, deadlineMs: 1000, responseLimit: 20, onChunk: n => { charged += n; } });
  assert.equal(r.code, null); assert.equal(r.bytes.toString(), 'abcdef'); assert.equal(charged, 6);
});
test('real transport caps partial bodies and aborts finite timeout without retained body', async t => {
  const url = await server(t, (req, res) => { res.write('1234567890123456789012345'); });
  let charged = 0; const cap = await p.send(p.query('resolver', anchor), { loopback: url, deadlineMs: 1000, responseLimit: 10, onChunk: n => { charged += n; } });
  assert.equal(cap.code, 'RESPONSE_LIMIT'); assert.equal(cap.bytes, undefined); assert.ok(charged <= 11);
  const idle = await server(t, (req, res) => { res.write('a'); });
  const timeout = await p.send(p.query('resolver', anchor), { loopback: idle, deadlineMs: 20, responseLimit: 10, onChunk: () => {} });
  assert.equal(timeout.code, 'TIMEOUT'); assert.equal(timeout.bytes, undefined); assert.equal(timeout.received, 1);
});
test('real transport partial EOF and HTTP refusal retain no body', async t => {
  const url = await server(t, (req, res) => { res.writeHead(200, { 'Content-Length': '100' }); res.write('abc'); setTimeout(() => res.destroy(), 5); });
  const r = await p.send(p.query('resolver', anchor), { loopback: url, deadlineMs: 1000, responseLimit: 200, onChunk: () => {} });
  assert.equal(r.code, 'PARTIAL_RESPONSE'); assert.equal(r.bytes, undefined); assert.equal(r.received, 3);
  const refused = await server(t, (req, res) => { res.writeHead(403); res.end('secret'); });
  const error = await p.send(p.query('resolver', anchor), { loopback: refused, deadlineMs: 1000, responseLimit: 200, onChunk: () => {} });
  assert.equal(error.code, 'HTTP_ERROR'); assert.equal(error.bytes, undefined); assert.ok(!JSON.stringify(error).includes('secret'));
});
test('duplicate JSON keys cannot hide forbidden headers or reach raw retention', async () => {
  const forbidden = JSON.stringify({ ...header, timestamp: 1790553600 }), safe = JSON.stringify(header);
  const variants = [Buffer.from(`{"header":${forbidden},"header":${safe},"transactions":[{}]}\n`),
    Buffer.from(`{"header":${forbidden},"he\\u0061der":${safe},"transactions":[{}]}\n`),
    Buffer.from(`{"header":${safe.replace('"timestamp":1775001600', '"timestamp":1790553600,"timestamp":1775001600')}}\n`)];
  for (const bytes of variants) {
    const r = p.admit(bytes, { kind: 'payload', anchor, slot: 100, header });
    assert.equal(r.code, 'RESPONSE_INVALID'); assert.equal(r.rows, 0); assert.equal(r.counts, undefined);
  }
  const d = deps(memory(), (spec, bytes) => spec.kind === 'header' ? variants[0] : bytes);
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.status, 'INCOMPLETE'); assert.equal(d.calls.filter(c => c.spec.kind === 'payload').length, 0);
  assert.equal([...d.store.files.keys()].filter(n => n.endsWith('.raw')).length, 6);
});
test('partial raw writes retain exact byte accounting and cannot pass offline integrity', async () => {
  const store = memory(), write = store.write;
  store.write = (name, bytes) => { if (name.endsWith('.raw')) { store.files.set(name, bytes.subarray(0, 5)); throw Error('STORAGE_ERROR'); } write(name, bytes); };
  const d = deps(store), r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  const actual = [...store.files.values()].reduce((total, bytes) => total + bytes.length, 0);
  assert.equal(r.accounting.retained, actual); assert.equal(r.status, 'INCOMPLETE'); assert.equal(d.calls.length, 1);
  assert.equal(p.replay(store).code, 'INTEGRITY_ERROR');
});
