'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const http = require('node:http');
const { once } = require('node:events');
const { run, validate } = require('../probe.cjs');
const metadata = { url: 'https://portal.sqd.dev/datasets/solana-mainnet/metadata', method: 'GET' };
const rpc = { url: 'https://api.mainnet-beta.solana.com/', method: 'POST', body: { jsonrpc: '2.0', id: 1, method: 'getSlot', params: [{ commitment: 'finalized' }] } };
const plan = (requests = [metadata]) => ({ referenceEnd: '2026-09-27T00:00:00.000Z', requests });
function setup(t, responses = []) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'public-probe-')); t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  let now = 100000, active = 0; const state = { starts: [], maxActive: 0, aborted: false, free: 1e12 };
  const clock = { now: () => now, sleep: async ms => { now += ms; }, setTimeout, clearTimeout };
  const transport = async (request, { signal, onChunk, onHeaders }) => {
    state.starts.push(now); active++; state.maxActive = Math.max(state.maxActive, active); signal.addEventListener('abort', () => { state.aborted = true; });
    const response = responses[state.starts.length - 1] || {}; onHeaders(response.status || 200);
    try { if (response.advance) now += response.advance; onChunk(response.body === undefined ? Buffer.from('{}') : response.body);
      if (response.error) throw Error('UNSAFE_SECRET_ERROR'); return { ended: response.ended !== false }; }
    finally { active--; }
  };
  return { root, state, clock, transport, free: () => state.free };
}
test('disabled execution makes zero network and filesystem writes', async t => {
  const io = setup(t); const result = await run({ enabled: false, plan: plan(), ...io });
  assert.equal(result.reason, 'DISABLED'); assert.equal(io.state.starts.length, 0); assert.deepEqual(fs.readdirSync(io.root), []);
});
for (const [name, request] of [
  ['host', { ...metadata, url: 'https://evil.invalid/' }], ['path', { ...metadata, url: metadata.url + '/other' }],
  ['userinfo', { ...metadata, url: metadata.url.replace('https://', 'https://secret@') }],
  ['auth', { ...metadata, headers: { Authorization: 'secret' } }], ['query', { ...metadata, url: metadata.url + '?api_key=secret' }],
  ['method', { ...rpc, body: { ...rpc.body, method: 'sendTransaction' } }],
  ['finality', { ...rpc, body: { ...rpc.body, params: [{ commitment: 'processed' }] } }],
  ['unsafe slot', { ...rpc, body: { ...rpc.body, method: 'getBlockTime', params: [9007199254740992] } }]
]) test(`reject ${name} before any outbound call`, async t => {
  const io = setup(t); let rejected = false; try { validate(plan([request])); } catch { rejected = true; }
  assert.equal(rejected, true); const result = await run({ enabled: true, plan: plan([request]), ...io });
  assert.equal(result.reason, 'REQUEST_INVALID'); assert.equal(io.state.starts.length, 0);
});
test('81 requests are rejected without network', async t => {
  const io = setup(t), result = await run({ enabled: true, plan: plan(Array(81).fill(metadata)), ...io });
  assert.equal(result.reason, 'REQUEST_INVALID'); assert.equal(io.state.starts.length, 0);
});
test('declared repeats retain separate exact raw hashes, attempts and two-second serialized starts', async t => {
  const body = Buffer.from('{"quantity":"900719925474099312345"}'), io = setup(t, [{ body }, { body }]);
  const result = await run({ enabled: true, plan: plan([metadata, metadata]), ...io });
  assert.equal(result.attempts, 2); assert.equal(io.state.maxActive, 1); assert.ok(io.state.starts[1] - io.state.starts[0] >= 2000);
  assert.equal(result.records.length, 2); assert.notEqual(result.records[0].raw, result.records[1].raw);
  for (const record of result.records) {
    assert.equal(record.complete, true); assert.equal(record.sha256, crypto.createHash('sha256').update(body).digest('hex'));
    assert.deepEqual(fs.readFileSync(path.join(io.root, 'provider-feasibility', record.raw)), body); assert.ok(record.sentAt && record.receivedAt);
  }
  assert.equal(JSON.stringify(result).includes('PASS'), false);
});
for (const [name, response, reason] of [
  ['empty', { body: Buffer.alloc(0) }, 'EMPTY'], ['malformed', { body: Buffer.from('not-json') }, 'MALFORMED'],
  ['HTTP error', { status: 503, body: Buffer.from('{"error":"unavailable"}') }, 'HTTP_ERROR'],
  ['RPC error', { body: Buffer.from('{"jsonrpc":"2.0","error":{"code":-1}}') }, 'PROVIDER_ERROR'],
  ['partial', { body: Buffer.from('{"partial":'), ended: false }, 'PARTIAL'],
  ['stream error', { body: Buffer.from('{"partial":'), error: true }, 'NETWORK_ERROR'],
  ['oversized', { body: Buffer.alloc(2000001, 32) }, 'RESPONSE_LIMIT'],
  ['redirect', { status: 302, body: Buffer.from('redirect') }, 'REDIRECT'],
  ['auth denied', { status: 401, body: Buffer.from('denied') }, 'AUTH_DENIED']
]) test(`${name} retains bounded incomplete evidence and never retries`, async t => {
  const io = setup(t, [response]); const result = await run({ enabled: true, plan: plan([metadata]), ...io });
  assert.equal(result.attempts, 1); assert.equal(io.state.starts.length, 1); assert.equal(result.records[0].complete, false);
  assert.equal(result.records[0].reason, reason); assert.ok(result.received <= 2000000);
  assert.ok(fs.existsSync(path.join(io.root, 'provider-feasibility', result.records[0].raw)));
});
test('response reservation stops before total byte allowance can be exceeded', async t => {
  const body = Buffer.from('"' + 'x'.repeat(1899998) + '"'), io = setup(t, Array(20).fill({ body }));
  const result = await run({ enabled: true, plan: plan(Array(20).fill(metadata)), ...io });
  assert.equal(result.reason, 'RECEIVED_LIMIT'); assert.equal(result.attempts, 13); assert.equal(result.received, 24700000);
});
test('absolute batch deadline stops subsequent request', async t => {
  const io = setup(t, [{ advance: 1800000 }]); const result = await run({ enabled: true, plan: plan([metadata, metadata]), ...io });
  assert.equal(result.reason, 'BATCH_DEADLINE'); assert.equal(result.attempts, 1);
});
for (const [name, free, used, reason] of [['free', 30049999999, 0, 'FREE_SPACE_LIMIT'], ['disk', 1e12, 9950000001, 'DISK_LIMIT']]) {
  test(`${name} shared reservation refuses launch without altering S1`, async t => {
    const io = setup(t); fs.mkdirSync(path.join(io.root, 'alchemy-s1')); fs.writeFileSync(path.join(io.root, 'alchemy-s1', 'retained'), 'original');
    io.state.free = free; const result = await run({ enabled: true, plan: plan(), ...io, used: () => used });
    assert.equal(result.reason, reason); assert.equal(io.state.starts.length, 0); assert.equal(fs.readFileSync(path.join(io.root, 'alchemy-s1', 'retained'), 'utf8'), 'original');
  });
}
test('free space and storage failures during writes abort and prevent further work', async t => {
  const io = setup(t), transport = async (request, hooks) => { io.state.starts.push(0); io.state.free = 0; hooks.onHeaders(200); hooks.onChunk(Buffer.from('{}')); return { ended: true }; };
  const result = await run({ enabled: true, plan: plan([metadata, metadata]), ...io, transport });
  assert.equal(result.reason, 'FREE_SPACE_LIMIT'); assert.equal(result.attempts, 1);
});
test('exclusive output rejects reuse and active S1; existing files never overwritten', async t => {
  const io = setup(t); fs.mkdirSync(path.join(io.root, 'provider-feasibility')); fs.writeFileSync(path.join(io.root, 'provider-feasibility', 'keep'), 'unchanged');
  const result = await run({ enabled: true, plan: plan(), ...io }); assert.equal(result.reason, 'OUTPUT_REUSE'); assert.equal(io.state.starts.length, 0);
  assert.equal(fs.readFileSync(path.join(io.root, 'provider-feasibility', 'keep'), 'utf8'), 'unchanged');
  const other = setup(t); fs.mkdirSync(path.join(other.root, 'alchemy-s1')); fs.writeFileSync(path.join(other.root, 'alchemy-s1', 'active.lock'), '');
  assert.equal((await run({ enabled: true, plan: plan(), ...other })).reason, 'S1_ACTIVE'); assert.equal(other.state.starts.length, 0);
});
test('timeout cancels outstanding local HTTP request and retains partial evidence', async t => {
  const server = http.createServer((req, res) => { res.writeHead(200); res.write('{'); }); server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); }); const io = setup(t); let closed = false;
  const clock = { ...io.clock, setTimeout: fn => setTimeout(fn, 40) };
  const transport = (spec, { signal, onChunk, onHeaders }) => new Promise((resolve, reject) => {
    const req = http.get(`http://127.0.0.1:${server.address().port}`, res => { onHeaders(res.statusCode); res.on('data', onChunk); res.on('end', () => resolve({ ended: true })); });
    req.on('error', reject); signal.addEventListener('abort', () => { closed = true; req.destroy(Error('cancelled')); });
  });
  const result = await run({ enabled: true, plan: plan([metadata, metadata]), ...io, clock, transport });
  assert.equal(result.reason, 'REQUEST_DEADLINE'); assert.equal(closed, true); assert.equal(result.attempts, 1); assert.equal(result.records[0].complete, false);
});
test('valid finite SQD and public RPC requests are accepted, excessive ranges and GoPlus lists rejected', () => {
  const sqd = { url: 'https://portal.sqd.dev/datasets/solana-mainnet/finalized-stream', method: 'POST', body: { type: 'solana', fromBlock: 100, toBlock: 199, includeAllBlocks: true, fields: { block: { number: true, timestamp: true } } } };
  assert.doesNotThrow(() => validate(plan([sqd, rpc])));
  assert.throws(() => validate(plan([{ ...sqd, body: { ...sqd.body, toBlock: 200 } }])));
  const goplus = { url: 'https://api.gopluslabs.io/api/v1/solana/token_security?contract_addresses=HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3', method: 'GET' };
  assert.throws(() => validate(plan(Array(7).fill(goplus))));
});
test('evidence write ceiling and actual storage failure terminate before further network work', async t => {
  const io = setup(t); let writes = 0;
  const result = await run({ enabled: true, plan: plan([metadata, metadata]), ...io,
    write(file, bytes, flags) { writes++; if (file.endsWith('.raw') && bytes.length) throw Error('disk failed'); fs.writeFileSync(file, bytes, { flag: flags }); } });
  assert.equal(result.reason, 'STORAGE_ERROR'); assert.equal(result.attempts, 1); assert.ok(writes > 0); assert.equal(io.state.starts.length, 1);
  const other = setup(t); const capped = await run({ enabled: true, plan: plan(), ...other,
    used: dir => path.basename(dir) === 'provider-feasibility' ? 49999999 : 0 });
  assert.equal(capped.reason, 'EVIDENCE_LIMIT'); assert.equal(other.state.starts.length, 0);
});

for (const termination of ['timeout', 'connection termination']) test(`production send retains short partial body on ${termination}`, { timeout: 5000 }, async t => {
  const body = Buffer.from('{"partial":12345'), io = setup(t); let serverResponse, expire, calls = 0, closed;
  const server = http.createServer((req, res) => { serverResponse = res; res.writeHead(200); res.write(body); });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  // Redirect only socket creation to loopback; run() still selects its production send implementation.
  t.mock.method(require('node:https'), 'request', (url, options, callback) => {
    calls++; assert.equal(url, metadata.url);
    const request = http.request(`http://127.0.0.1:${server.address().port}`, options, response => {
      callback(response);
      response.once('readable', () => setImmediate(() => {
        if (termination === 'timeout') expire(); else serverResponse.destroy();
      }));
    });
    closed = new Promise(resolve => request.once('close', resolve)); return request;
  });
  const clock = { ...io.clock, setTimeout: fn => { expire = fn; return 1; }, clearTimeout() {} };
  const result = await run({ enabled: true, plan: plan([metadata, metadata]), ...io, clock, transport: undefined });
  await closed;
  const record = result.records[0];
  assert.equal(result.received, body.length, 'received partial bytes must be accounted');
  assert.equal(record.bytes, body.length); assert.equal(record.retainedBytes, body.length);
  assert.deepEqual(fs.readFileSync(path.join(io.root, 'provider-feasibility', record.raw)), body);
  assert.equal(record.sha256, crypto.createHash('sha256').update(body).digest('hex'));
  assert.equal(record.complete, false);
  assert.equal(result.reason, termination === 'timeout' ? 'REQUEST_DEADLINE' : 'NETWORK_ERROR');
  assert.equal(record.reason, result.reason); assert.equal(result.attempts, 1); assert.equal(calls, 1);
});
for (const [name, fragments, reason] of [
  ['short finite JSON', ['{"ok":true}'], 'LIST_EXHAUSTED'],
  ['fragmented finite JSON', ['{"ok":', 'true}'], 'LIST_EXHAUSTED'],
  ['empty completed body', [], 'EMPTY'],
  ['response cap boundary', ['x'.repeat(2000000)], 'RESPONSE_LIMIT']
]) test(`production send completes ${name} without waiting for deadline`, { timeout: 5000 }, async t => {
  const io = setup(t), body = Buffer.from(fragments.join('')); let calls = 0, closed, serverResponse;
  const server = http.createServer((req, res) => {
    serverResponse = res; res.writeHead(200);
    if (fragments.length > 1) res.write(fragments[0]);
    else res.end(fragments[0]);
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  t.mock.method(require('node:https'), 'request', (url, options, callback) => {
    calls++; assert.equal(url, metadata.url);
    const request = http.request(`http://127.0.0.1:${server.address().port}`, options, response => {
      callback(response);
      if (fragments.length > 1) response.once('readable', () => setImmediate(() => serverResponse.end(fragments[1])));
    });
    closed = new Promise(resolve => request.once('close', resolve)); return request;
  });
  const clock = { ...io.clock, setTimeout: fn => setTimeout(fn, 1500) };
  const requests = reason === 'LIST_EXHAUSTED' ? [metadata] : [metadata, metadata];
  const result = await run({ enabled: true, plan: plan(requests), ...io, clock, transport: undefined });
  await closed;
  assert.equal(result.reason, reason, 'completed response must not wait for request deadline');
  const record = result.records[0];
  assert.equal(result.received, body.length); assert.equal(record.bytes, body.length); assert.equal(record.retainedBytes, body.length);
  assert.deepEqual(fs.readFileSync(path.join(io.root, 'provider-feasibility', record.raw)), body);
  assert.equal(record.sha256, crypto.createHash('sha256').update(body).digest('hex'));
  assert.equal(record.complete, reason === 'LIST_EXHAUSTED');
  assert.equal(record.reason, reason === 'LIST_EXHAUSTED' ? 'COMPLETE_RESPONSE' : reason);
  assert.equal(result.attempts, 1); assert.equal(calls, 1);
});

const profilePlan = () => ({ profile: 's2-rpc-c-m-v1', referenceEnd: '2026-09-27T18:44:00.000Z',
  requests: [410100000, 429644639].map((position, i) => ({ ...rpc, body: { jsonrpc: '2.0', id: 16 + i, method: 'getBlock',
    params: [position, { commitment: 'finalized', encoding: 'json', transactionDetails: 'full', rewards: false, maxSupportedTransactionVersion: 1 }] } })) });
const sizedJson = length => {
  const prefix = '{"quantity":900719925474099312345,"pad":"', suffix = '"}';
  return Buffer.from(prefix + 'x'.repeat(length - prefix.length - suffix.length) + suffix);
};
for (const [name, mutate] of [
  ['third request', p => p.requests.push(p.requests[1])], ['missing request', p => p.requests.pop()],
  ['non-RPC request', p => p.requests[0] = metadata], ['wrong slot', p => p.requests[0].body.params[0]++],
  ['wrong second slot', p => p.requests[1].body.params[0]++], ['order', p => p.requests.reverse()],
  ['wrong ID', p => p.requests[0].body.id++], ['reference', p => p.referenceEnd = plan().referenceEnd],
  ['unknown profile', p => p.profile = 'other'], ['null profile', p => p.profile = null],
  ['encoding', p => p.requests[0].body.params[1].encoding = 'base64'], ['version', p => p.requests[0].body.params[1].maxSupportedTransactionVersion = 0],
  ['finality', p => p.requests[0].body.params[1].commitment = 'confirmed'], ['details', p => p.requests[0].body.params[1].transactionDetails = 'accounts'],
  ['rewards', p => p.requests[0].body.params[1].rewards = true], ['method', p => p.requests[0].body.method = 'getBlockTime'],
  ['cap', p => p.responseLimit = 9000000], ['debit', p => p.sunkDebits = { attempts: 0, received: 0 }]
]) test(`fixed RPC profile rejects ${name} before network or output`, async t => {
  const input = profilePlan(); mutate(input); const io = setup(t);
  assert.throws(() => validate(input)); const result = await run({ enabled: true, plan: input, ...io });
  assert.equal(result.reason, 'REQUEST_INVALID'); assert.equal(io.state.starts.length, 0); assert.deepEqual(fs.readdirSync(io.root), []);
});
for (const [name, profiled, length, termination, reason] of [
  ['complete above 2 MB', true, 3000000, 'end', 'LIST_EXHAUSTED'],
  ['two near-cap responses', true, 7999999, 'end', 'LIST_EXHAUSTED'],
  ['ordinary 2 MB stop', false, 3000000, 'end', 'RESPONSE_LIMIT'],
  ['8 MB boundary', true, 8000000, 'end', 'RESPONSE_LIMIT'],
  ['8 MB oversize', true, 8000001, 'end', 'RESPONSE_LIMIT'],
  ['timeout partial above 2 MB', true, 3000000, 'timeout', 'REQUEST_DEADLINE'],
  ['connection termination above 2 MB', true, 3000000, 'close', 'NETWORK_ERROR']
]) test(`production send fixed RPC profile: ${name}`, { timeout: 10000 }, async t => {
  const io = setup(t), body = sizedJson(length), input = profilePlan(); if (!profiled) delete input.profile;
  let calls = 0, expire, serverResponse; const closures = [];
  const server = http.createServer((req, res) => { serverResponse = res; res.writeHead(200); if (termination === 'end') res.end(body); else res.write(body); });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); t.after(() => { server.closeAllConnections(); server.close(); });
  t.mock.method(require('node:https'), 'request', (url, options, callback) => {
    calls++; assert.equal(url, rpc.url);
    const request = http.request(`http://127.0.0.1:${server.address().port}`, options, response => {
      callback(response);
      if (termination !== 'end') response.on('readable', () => setImmediate(() => {
        const raw = path.join(io.root, 'provider-feasibility', '001.raw');
        if (fs.existsSync(raw) && fs.statSync(raw).size === body.length) {
          if (termination === 'timeout') expire(); else serverResponse.destroy();
        }
      }));
    });
    closures.push(new Promise(resolve => request.once('close', resolve))); return request;
  });
  const clock = { ...io.clock, setTimeout: fn => { if (termination !== 'end') { expire = fn; return 1; } return setTimeout(fn, 5000); },
    clearTimeout: timer => { if (termination === 'end') clearTimeout(timer); } };
  const result = await run({ enabled: true, plan: input, ...io, clock, transport: undefined }); await Promise.all(closures);
  assert.equal(result.reason, reason, 'profile transport must apply its exact response ceiling and finish at EOF');
  const expectedCalls = reason === 'LIST_EXHAUSTED' ? 2 : 1, retained = body.subarray(0, profiled ? 8000000 : 2000000);
  assert.equal(calls, expectedCalls); assert.equal(result.attempts, expectedCalls); assert.equal(result.received, retained.length * expectedCalls);
  for (const record of result.records) {
    assert.equal(record.bytes, retained.length); assert.equal(record.retainedBytes, retained.length);
    assert.deepEqual(fs.readFileSync(path.join(io.root, 'provider-feasibility', record.raw)), retained);
    assert.equal(record.sha256, crypto.createHash('sha256').update(retained).digest('hex'));
    assert.equal(record.complete, reason === 'LIST_EXHAUSTED'); assert.equal(record.reason, record.complete ? 'COMPLETE_RESPONSE' : reason);
  }
  if (profiled) {
    assert.deepEqual(result.sunkDebits, { attempts: 25, received: 6995480 });
    assert.equal(result.cumulativeAttempts, 25 + expectedCalls); assert.equal(result.cumulativeReceived, 6995480 + result.received);
    const dir = path.join(io.root, 'provider-feasibility'), manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json')));
    assert.equal(manifest.profile, input.profile); assert.deepEqual(manifest.sunkDebits, result.sunkDebits);
    assert.equal(manifest.effectiveCaps.response, 8000000); assert.equal(manifest.effectiveCaps.evidence, 17000000);
    assert.equal(manifest.caps.received, 25000000); assert.equal(manifest.caps.requests, 80); assert.equal(manifest.evidenceReservation, 50000000);
    for (let i = 1; i <= expectedCalls; i++) {
      const reservation = JSON.parse(fs.readFileSync(path.join(dir, String(i).padStart(3, '0') + '.request.json')));
      assert.equal(reservation.reservedResponse, 8000000); assert.equal(reservation.cumulativeAttempts, 25 + i);
      assert.equal(reservation.cumulativeReceived, 6995480 + (i - 1) * retained.length);
      assert.ok(reservation.cumulativeReceived + reservation.reservedResponse <= 25000000);
    }
    const summary = JSON.parse(fs.readFileSync(path.join(dir, 'summary.json'))); assert.deepEqual(summary, result);
    assert.ok(fs.readdirSync(dir).reduce((total, file) => total + fs.statSync(path.join(dir, file)).size, 0) <= 17000000);
  }
});
test('fixed RPC profile keeps timeout abort, output exclusivity and the 17 MB output bound', async t => {
  const io = setup(t), partial = Buffer.from('{"partial":'), clock = { ...io.clock, setTimeout: fn => setTimeout(fn, 20) };
  const transport = async (request, hooks) => {
    io.state.starts.push(0); hooks.onHeaders(200); hooks.onChunk(partial);
    await new Promise(resolve => hooks.signal.addEventListener('abort', () => { io.state.aborted = true; resolve(); }, { once: true }));
    return { ended: false };
  };
  const result = await run({ enabled: true, plan: profilePlan(), ...io, clock, transport });
  assert.equal(result.reason, 'REQUEST_DEADLINE'); assert.equal(io.state.aborted, true); assert.equal(io.state.starts.length, 1);
  const raw = path.join(io.root, 'provider-feasibility', '001.raw'); assert.deepEqual(fs.readFileSync(raw), partial);
  const retry = await run({ enabled: true, plan: profilePlan(), ...io }); assert.equal(retry.reason, 'OUTPUT_REUSE');
  assert.equal(io.state.starts.length, 1); assert.deepEqual(fs.readFileSync(raw), partial);
  const other = setup(t), capped = await run({ enabled: true, plan: profilePlan(), ...other,
    used: dir => path.basename(dir) === 'provider-feasibility' ? 16999999 : 0 });
  assert.equal(capped.reason, 'EVIDENCE_LIMIT'); assert.equal(other.state.starts.length, 0);
});

const profileV2Plan = () => ({ ...profilePlan(), profile: 's2-rpc-c-m-v2' });
for (const [name, mutate] of [
  ['third', p => p.requests.push(p.requests[1])], ['non-RPC', p => p.requests[0] = metadata], ['slot', p => p.requests[0].body.params[0]++],
  ['order', p => p.requests.reverse()], ['ID', p => p.requests[1].body.id++], ['reference', p => p.referenceEnd = plan().referenceEnd],
  ['encoding', p => p.requests[0].body.params[1].encoding = 'base64'], ['version', p => p.requests[0].body.params[1].maxSupportedTransactionVersion = 0],
  ['method', p => p.requests[0].body.method = 'getBlockTime'], ['cap', p => p.responseLimit = 64000000], ['debit', p => p.sunkDebits = { attempts: 0, received: 0 }]
]) test(`V2 rejects ${name} with zero IO`, async t => {
  const input = profileV2Plan(), io = setup(t); mutate(input); assert.throws(() => validate(input));
  assert.equal((await run({ enabled: true, plan: input, ...io })).reason, 'REQUEST_INVALID');
  assert.equal(io.state.starts.length, 0); assert.deepEqual(fs.readdirSync(io.root), []);
});
for (const [name, length, timeout, reason] of [
  ['complete above 8 MB after 20 seconds', 9000000, false, 'LIST_EXHAUSTED'], ['two near-32 MB bodies', 31999999, false, 'LIST_EXHAUSTED'],
  ['32 MB boundary', 32000000, false, 'RESPONSE_LIMIT'], ['32 MB oversize', 32000001, false, 'RESPONSE_LIMIT'],
  ['120-second timeout', 9000000, true, 'REQUEST_DEADLINE']
]) test(`production send V2: ${name}`, { timeout: 15000 }, async t => {
  const io = setup(t), body = sizedJson(length), dir = path.join(io.root, 'provider-feasibility'); let calls = 0, now = 100000, expire;
  const closures = [], timers = [], server = http.createServer((req, res) => { res.writeHead(200); if (timeout) res.write(body); else res.end(body); });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); t.after(() => { server.closeAllConnections(); server.close(); });
  t.mock.method(require('node:https'), 'request', (url, options, callback) => {
    calls++; assert.equal(url, rpc.url);
    const request = http.request(`http://127.0.0.1:${server.address().port}`, options, response => {
      now += 20000; callback(response);
      if (timeout) response.on('readable', () => setImmediate(() => {
        if (fs.statSync(path.join(dir, '001.raw')).size === body.length) { now += 100000; expire(); }
      }));
    }); closures.push(new Promise(resolve => request.once('close', resolve))); return request;
  });
  const clock = { now: () => now, sleep: async ms => { now += ms; }, setTimeout: (fn, ms) => { timers.push(ms); if (timeout) { expire = fn; return 1; } return setTimeout(fn, 8000); },
    clearTimeout: timer => { if (!timeout) clearTimeout(timer); } };
  const result = await run({ enabled: true, plan: profileV2Plan(), ...io, clock, transport: undefined }); await Promise.all(closures);
  assert.equal(result.reason, reason, 'V2 must use its enlarged response and deadline limits'); const count = reason === 'LIST_EXHAUSTED' ? 2 : 1, retained = body.subarray(0, 32000000);
  assert.equal(calls, count); assert.equal(result.attempts, count); assert.deepEqual(timers, Array(count).fill(120000));
  assert.equal(result.received, retained.length * count); assert.deepEqual(result.sunkDebits, { attempts: 26, received: 14995480 });
  assert.deepEqual(result.newRun, { attempts: count, received: retained.length * count }); assert.equal(result.cumulativeAttempts, 26 + count);
  assert.equal(result.cumulativeReceived, 14995480 + result.received); assert.equal(result.evidenceReservation, 200000000); assert.equal(result.aggregateEvidenceLimit, 200000000);
  const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json')));
  assert.equal(manifest.profile, 's2-rpc-c-m-v2'); assert.deepEqual(manifest.sunkDebits, result.sunkDebits); assert.deepEqual(manifest.newRun, { attempts: 0, received: 0 });
  assert.equal(manifest.caps.response, 2000000); assert.equal(manifest.caps.received, 25000000); assert.equal(manifest.evidenceReservation, 200000000);
  for (const container of [manifest, result]) {
    assert.equal(container.effectiveCaps.response, 32000000); assert.equal(container.effectiveCaps.received, 100000000);
    assert.equal(container.effectiveCaps.evidence, 65000000); assert.equal(container.effectiveCaps.requestMs, 120000); assert.equal(container.effectiveCaps.requests, 80);
  }
  for (const record of result.records) {
    assert.equal(record.bytes, retained.length); assert.equal(record.retainedBytes, retained.length); assert.equal(record.status, 200);
    assert.equal(record.sha256, crypto.createHash('sha256').update(retained).digest('hex')); assert.deepEqual(fs.readFileSync(path.join(dir, record.raw)), retained);
    assert.equal(record.complete, reason === 'LIST_EXHAUSTED'); assert.equal(record.reason, record.complete ? 'COMPLETE_RESPONSE' : reason);
    const reservation = JSON.parse(fs.readFileSync(path.join(dir, String(record.index).padStart(3, '0') + '.request.json')));
    assert.equal(reservation.reservedResponse, 32000000); assert.equal(reservation.requestDeadline - Date.parse(record.sentAt), 120000);
    assert.equal(reservation.cumulativeAttempts, 26 + record.index); assert.equal(reservation.cumulativeReceived, 14995480 + (record.index - 1) * retained.length);
    assert.equal(reservation.evidenceReservation, 200000000); assert.ok(reservation.cumulativeReceived + reservation.reservedResponse <= 100000000);
  }
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir, 'summary.json'))), result);
  assert.ok(fs.readdirSync(dir).reduce((sum, file) => sum + fs.statSync(path.join(dir, file)).size, 0) <= 65000000);
  assert.equal((await run({ enabled: true, plan: profileV2Plan(), ...io })).reason, 'OUTPUT_REUSE'); assert.equal(calls, count);
  assert.deepEqual(fs.readFileSync(path.join(dir, '001.raw')), retained);
});
for (const [name, free, used, reason] of [
  ['65 MB output', 1e12, dir => path.basename(dir) === 'provider-feasibility' ? 64999999 : 0, 'EVIDENCE_LIMIT'],
  ['200 MB free reservation', 30199999999, () => 0, 'FREE_SPACE_LIMIT'], ['200 MB disk reservation', 1e12, () => 9800000001, 'DISK_LIMIT']
]) test(`V2 ${name} refuses before traffic`, async t => {
  const io = setup(t); io.state.free = free; const result = await run({ enabled: true, plan: profileV2Plan(), ...io, used });
  assert.equal(result.reason, reason); assert.equal(io.state.starts.length, 0);
});
