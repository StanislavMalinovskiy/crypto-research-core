'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { Budget } = require('../core.cjs');
const { decode } = require('../transport.cjs');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const grpc = require('@grpc/grpc-js');
const loader = require('@grpc/proto-loader');
const api = require('../core.cjs');
const programs = ['6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P',
  'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', '675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8'];
const temp = t => { const dir = fs.mkdtempSync(path.join(os.tmpdir(), 's1-')); t.after(() => fs.rmSync(dir, { recursive: true, force: true })); return dir; };
const tx = (signature, keys = programs, version = 'legacy') => ({ version, transaction: {
  signatures: [signature], message: { accountKeys: keys, instructions: [] }
}, meta: { err: null, loadedAddresses: { writable: [], readonly: [] } } });
const generous = () => new Budget({ received: 1e9, disk: 1e9, rpc: 20, deadline: 1e15, floor: 0 });
const journal = (t, opts = {}) => new api.Journal(temp(t), { anchor: '10', ...opts });
async function server(t, responder) {
  const s = http.createServer(responder); s.listen(0, '127.0.0.1'); await once(s, 'listening');
  t.after(() => s.closeAllConnections()); t.after(() => s.close());
  return `http://127.0.0.1:${s.address().port}`;
}

const caps = { received: 100, disk: 1000, rpc: 2, deadline: 1000, floor: 500 };

for (const [name, state, request, reason] of [
  ['received', { received: 95 }, [8, 10, 100, 10000], 'RECEIVED_LIMIT'],
  ['disk', { disk: 995 }, [1, 10, 100, 10000], 'DISK_LIMIT'],
  ['wall', {}, [1, 10, 1000, 10000], 'WALL_LIMIT'],
  ['free space', {}, [1, 10, 100, 499], 'FREE_SPACE_LIMIT']
]) {
  test(`${name} reservation stops permanently and survives recovery`, () => {
    const budget = new Budget(caps, state);
    assert.equal(budget.reserve(...request), false, 'must reject before reserving past a cap');
    assert.equal(budget.state.stopped, reason);
    const recovered = new Budget(caps, JSON.parse(JSON.stringify(budget.state)));
    assert.equal(recovered.reserve(0, 0, 0, 100000), false, 'restart cannot reopen a stopped run');
    assert.equal(recovered.rpc(), false, 'stopped run cannot start another RPC');
  });
}

test('RPC attempt ceiling includes attempts retained across recovery', () => {
  const budget = new Budget(caps);
  assert.equal(budget.rpc(), true);
  assert.equal(budget.rpc(), true);
  const recovered = new Budget(caps, JSON.parse(JSON.stringify(budget.state)));
  assert.equal(recovered.rpc(), false, 'third attempt must be rejected');
  assert.equal(recovered.state.rpc, 2);
  assert.equal(recovered.state.stopped, 'RPC_LIMIT');
});

test('disabled or missing configuration makes zero outbound calls; diagnostics exclude synthetic secrets', async () => {
  let calls = 0;
  const connect = () => { calls++; throw Error('FAKE_SECRET https://fake.invalid/FAKE_SECRET'); };
  assert.deepEqual(await api.launch({ enabled: false, env: {}, connect }), { reason: 'LIVE_DISABLED' });
  assert.deepEqual(await api.launch({ enabled: true, env: {}, connect }), { reason: 'CONFIG_INVALID' });
  assert.equal(calls, 0);
  assert.equal(api.diagnostic(Error('FAKE_SECRET https://fake.invalid/FAKE_SECRET')), 'INTERNAL_ERROR');
});

test('configuration rejects duplicate/blank settings, redirects and unexpected credential targets', () => {
  const env = { ALCHEMY_API_KEY: 'FAKE_SECRET', ALCHEMY_SOLANA_RPC_ENDPOINT: 'https://solana-mainnet.g.alchemy.com/v2/FAKE_SECRET',
    ALCHEMY_SOLANA_GRPC_ENDPOINT: 'solana-mainnet.g.alchemy.com', ALCHEMY_SOLANA_GRPC_PORT: '443' };
  assert.throws(() => api.config(env, 'ALCHEMY_API_KEY=x\nALCHEMY_API_KEY=y'), /CONFIG_INVALID/);
  assert.throws(() => api.config({ ...env, ALCHEMY_API_KEY: ' ' }), /CONFIG_INVALID/);
  assert.throws(() => api.config({ ...env, ALCHEMY_SOLANA_RPC_ENDPOINT: 'https://user:secret@evil.invalid/' }), /CONFIG_INVALID/);
  assert.throws(() => api.config({ ...env, ALCHEMY_SOLANA_GRPC_ENDPOINT: 'evil.invalid' }), /CONFIG_INVALID/);
  assert.equal(api.config(env, 'DATABASE_PASSWORD=ignored').key, 'FAKE_SECRET');
});

test('raw frames are durable before checkpoint, equal replay deduplicates and immutable conflict fails', t => {
  const j = journal(t); t.after(() => j.close());
  const raw = Buffer.from([0, 10, 255]);
  j.append(raw, { source: 'grpc', receivedAt: '2026-01-01T00:00:00.000Z', session: '1' });
  assert.ok(j.state.offset > raw.length, 'frame boundary includes metadata/checksum');
  assert.equal(j.transaction({ slot: '12', signature: 'sig', index: '9007199254740993', hash: 'abc' }), 'new');
  assert.equal(j.transaction({ slot: '12', signature: 'sig', index: '9007199254740993', hash: 'abc' }), 'duplicate');
  assert.throws(() => j.transaction({ slot: '12', signature: 'sig', index: '9007199254740993', hash: 'def' }), /IMMUTABLE_CONFLICT/);
  assert.deepEqual(j.signatures('12'), [{ signature: 'sig', index: '9007199254740993', hash: 'abc' }]);
  assert.equal(j.state.complete, null, 'highest seen slot never proves completeness');
});

test('partial raw writes and failed checkpoint preserve last durable recovery position', t => {
  const dir = temp(t); let fail = false;
  const j = new api.Journal(dir, { anchor: '10', beforeCheckpoint: () => { if (fail) throw Error('IO_FAILURE'); } });
  j.append(Buffer.from('first'), { source: 'grpc' }); j.checkpoint();
  const committed = j.state.offset; fail = true;
  j.append(Buffer.from('second'), { source: 'grpc' });
  assert.throws(() => j.checkpoint(), /IO_FAILURE/);
  j.close();
  fs.appendFileSync(path.join(dir, 'raw.bin'), Buffer.from([0, 0, 0]));
  const recovered = new api.Journal(dir, { resume: true }); t.after(() => recovered.close());
  assert.equal(recovered.state.offset, committed);
  assert.equal(recovered.state.incomplete, true);
  assert.ok(fs.statSync(path.join(dir, 'raw.bin')).size > committed, 'tail retained');
});

test('later-slot-first delivery does not advance complete slot; verified skipped/empty slots do', t => {
  const j = journal(t); t.after(() => j.close());
  j.transaction({ slot: '12', signature: 'later', index: '0', hash: 'a' });
  assert.equal(j.verifyRange('12', '12', []), true);
  assert.equal(j.state.complete, null);
  assert.equal(j.verifyRange('10', '11', []), true);
  assert.equal(j.state.complete, '12');
});

test('RPC filtering uses loaded addresses, excludes failed/vote transactions and rejects unsupported versions', () => {
  const loaded = tx('loaded', []); loaded.meta.loadedAddresses.readonly = [programs[1]];
  const failed = tx('failed'); failed.meta.err = { InstructionError: [0, 'error'] };
  const vote = tx('vote', ['Vote111111111111111111111111111111111111111']);
  assert.deepEqual(api.filterBlock({ transactions: [loaded, tx('static'), failed, vote] }).map(x => x.signature), ['loaded', 'static']);
  assert.throws(() => api.filterBlock({ transactions: [tx('unsupported', programs, 2)] }), /UNSUPPORTED/);
  assert.throws(() => api.filterBlock(null), /BLOCK_UNAVAILABLE/);
});

test('reconciliation enumerates produced slots, accepts empty/skipped slots and records missing signatures', async t => {
  const j = journal(t); t.after(() => j.close()); const calls = [];
  const call = async (method, params) => { calls.push([method, params]); return method === 'getBlocks' ? [10, 12] : { transactions: [] }; };
  assert.equal((await api.reconcile({ from: '10', to: '12', journal: j, call })).complete, true);
  assert.equal(j.state.complete, '12'); assert.equal(calls.filter(x => x[0] === 'getBlock').length, 2);
  const other = journal(t); t.after(() => other.close());
  const missing = await api.reconcile({ from: '10', to: '10', journal: other,
    call: async method => method === 'getBlocks' ? [10] : { transactions: [tx('missing')] } });
  assert.equal(missing.outcome, 'FAIL'); assert.equal(other.state.complete, null);
});

test('unavailable and unsupported blocks remain unresolved, range enumeration never exceeds 1000 slots', async t => {
  const j = journal(t); t.after(() => j.close()); const widths = [];
  const result = await api.reconcile({ from: '10', to: '2010', journal: j, call: async (method, params) => {
    if (method === 'getBlocks') { widths.push(params[1] - params[0] + 1); return params[0] === 10 ? [] : [params[0]]; }
    return null;
  } });
  assert.equal(result.reason, 'BLOCK_UNAVAILABLE'); assert.ok(widths.length >= 2);
  assert.ok(widths.every(n => n <= 1000)); assert.equal(j.state.complete, '1009');
});

test('local HTTP retries are finite, body bounded and redirects never followed', async t => {
  let calls = 0;
  const endpoint = await server(t, (req, res) => { calls++; res.writeHead(503); res.end('FAKE_SECRET'); });
  const budget = generous();
  await assert.rejects(api.rpc({ endpoint, method: 'getSlot', params: [], budget, sleep: async () => {}, allowLocal: true }), /RPC_UNAVAILABLE/);
  assert.equal(calls, 3); assert.equal(budget.state.rpc, 3);
  const huge = await server(t, (req, res) => res.end(Buffer.alloc(1025)));
  await assert.rejects(api.rpc({ endpoint: huge, method: 'getSlot', params: [], budget: generous(), maxBody: 1024, allowLocal: true }), /RPC_BODY_LIMIT/);
  const redirect = await server(t, (req, res) => { res.writeHead(302, { Location: endpoint }); res.end(); });
  await assert.rejects(api.rpc({ endpoint: redirect, method: 'getSlot', params: [], budget: generous(), allowLocal: true }), /RPC_REDIRECT/);
  assert.equal(calls, 3);
});

test('budget stop cancels an outstanding local HTTP request and forbids later calls', async t => {
  let started; const ready = new Promise(resolve => { started = resolve; });
  const endpoint = await server(t, () => started()); const budget = generous();
  const pending = api.rpc({ endpoint, method: 'getSlot', params: [], budget, allowLocal: true });
  // The seam must start the request; race avoids a hanging RED run.
  assert.equal(await Promise.race([ready.then(() => true), pending.then(() => false)]), true);
  budget.stop('DISK_LIMIT'); await assert.rejects(pending, /DISK_LIMIT/);
  await assert.rejects(api.rpc({ endpoint, method: 'getSlot', params: [], budget, allowLocal: true }), /DISK_LIMIT/);
});

test('fake gRPC retains original bytes and applies finalized OR filter with bounded cancellation', async t => {
  const defs = loader.loadSync(path.join(__dirname, '../proto/geyser.proto'), { keepCase: true, longs: String, bytes: Buffer });
  const service = grpc.loadPackageDefinition(defs).geyser.Geyser.service; let subscription;
  const s = new grpc.Server();
  s.addService(service, { subscribe(call) { call.on('error', () => {}); call.on('data', req => {
    subscription = req; call.write({ slot: { slot: '9007199254740993', status: 2 } });
  }); } });
  const port = await new Promise((resolve, reject) => s.bindAsync('127.0.0.1:0', grpc.ServerCredentials.createInsecure(), (err, p) => err ? reject(err) : resolve(p)));
  t.after(() => s.forceShutdown()); let seen; const budget = generous();
  const handle = await api.stream({ endpoint: `127.0.0.1:${port}`, key: 'fake', from: '10', budget, allowLocal: true,
    onRaw: raw => { seen = raw; budget.stop('RECEIVED_LIMIT'); } });
  assert.ok(handle, 'stream handle required');
  await handle.done;
  assert.ok(Buffer.isBuffer(seen));
  assert.equal(subscription.commitment, 2); assert.equal(subscription.from_slot, '10');
  assert.deepEqual(subscription.transactions.watched.account_include, programs);
  assert.equal(subscription.transactions.watched.vote, false); assert.equal(subscription.transactions.watched.failed, false);
  assert.equal(budget.state.stopped, 'RECEIVED_LIMIT');
});

async function capturedSubscription(t, request) {
  const defs = loader.loadSync(path.join(__dirname, '../proto/geyser.proto'), { keepCase: true, longs: String, bytes: Buffer });
  const service = grpc.loadPackageDefinition(defs).geyser.Geyser.service; let observed;
  const s = new grpc.Server();
  s.addService(service, { subscribe(call) { call.on('error', () => {}); call.on('data', value => {
    observed = value; call.write({ slot: { slot: '900', status: 2 } });
  }); } });
  const port = await new Promise((resolve, reject) => s.bindAsync('127.0.0.1:0', grpc.ServerCredentials.createInsecure(), (err, p) => err ? reject(err) : resolve(p)));
  t.after(() => s.forceShutdown()); const budget = generous();
  const handle = await api.stream({ endpoint: `127.0.0.1:${port}`, key: 'fake', from: '10', subscription: request,
    budget, allowLocal: true, onRaw: () => budget.stop('RECEIVED_LIMIT') });
  await handle.done; return observed;
}
test('A finalized LIVE filter omits failed so failed watched transactions remain subscribed', async t => {
  const request = { transactions: { watched: { vote: false, account_include: programs } },
    slots: { finalized: { filter_by_commitment: true } }, blocks_meta: { finalized: {} }, commitment: 2, from_slot: '900' };
  const observed = await capturedSubscription(t, request);
  assert.equal(observed.transactions.watched.failed, undefined, 'expected failed to be absent; transport sent false');
});
test('A replay subscription requests only finalized slots at the exact from_slot', async t => {
  const request = { slots: { finalized: { filter_by_commitment: true } }, commitment: 2, from_slot: '429635638' };
  const observed = await capturedSubscription(t, request);
  assert.deepEqual(observed, request, 'replay must not subscribe to transactions, accounts, or blocks');
});
test('A replay probe stops after the first finalized slot and retains no interval-complete claim', async t => {
  const defs = loader.loadSync(path.join(__dirname, '../proto/geyser.proto'), { keepCase: true, longs: String, bytes: Buffer });
  const service = grpc.loadPackageDefinition(defs).geyser.Geyser.service; const server = new grpc.Server();
  server.addService(service, { subscribe(call) { call.on('error', () => {}); call.on('data', () => {
    call.write({ slot: { slot: '429635637', status: 1 } });
    call.write({ slot: { slot: '429635638', status: 2 } });
    call.write({ slot: { slot: '429635639', status: 2 } }); call.end();
  }); } });
  const port = await new Promise((resolve, reject) => server.bindAsync('127.0.0.1:0', grpc.ServerCredentials.createInsecure(), (err, p) => err ? reject(err) : resolve(p)));
  t.after(() => server.forceShutdown()); const seen = [], budget = generous();
  const handle = await api.stream({ endpoint: `127.0.0.1:${port}`, key: 'fake', from: '429635638',
    subscription: { slots: { finalized: { filter_by_commitment: true } }, commitment: 2, from_slot: '429635638' },
    stopOnFirstFinalizedSlot: true, budget, allowLocal: true, onRaw: raw => seen.push(decode(raw).slot.slot) });
  await handle.done;
  assert.deepEqual(seen, ['429635637', '429635638'], 'the first finalized sample must end the replay stream');
});
test('A offset arithmetic stays exact for the two fixed finalized first-slot probes', () => {
  const starts = typeof api.slotOffset === 'function'
    ? [api.slotOffset('429644638', 9000), api.slotOffset('429644638', 60000)] : [];
  assert.deepEqual(starts, ['429635638', '429584638']);
});
test('shared estimate admits the maximum A byte/CU envelope under $3 and rejects excess work', () => {
  const limits = { received: 15_000_000_000, disk: 1e12, rpc: 15, deadline: 1e15, floor: 0,
    spendPicoUsd: 3_000_000_000_000, picoUsdPerGrpcByte: 75, picoUsdPerCu: 525_000 };
  const live = new Budget(limits);
  assert.equal(live.reserve(15_000_000_000, 0, 0, Infinity, 'grpc'), true);
  live.settle(15_000_000_000, 15_000_000_000, 'grpc');
  for (let i = 0; i < 3; i++) assert.equal(live.rpc(20), true);
  const history = new Budget({ ...limits, received: 4_000_000, rpc: 12 },
    { ...live.state, received: 0, rpc: 0, grpcBytes: 0, rpcBytes: 0, stopped: null });
  for (let i = 0; i < 12; i++) assert.equal(history.rpc(10), true);
  assert.equal(history.state.spendPicoUsd, 1_125_094_500_000);
  assert.equal(history.state.spendPicoUsd <= limits.spendPicoUsd, true);
  assert.equal(live.reserve(1, 0, 0, Infinity, 'grpc'), false, 'the 15 GB ceiling rejects before more bytes');
  const capped = new Budget(limits, { spendPicoUsd: limits.spendPicoUsd - 75 });
  assert.equal(capped.reserve(2, 0, 0, Infinity, 'grpc'), false, 'shared $3 headroom is checked before reservation');
  assert.equal(capped.state.stopped, 'SPEND_LIMIT');
});
test('A counts exactly 60 clean LIVE minutes after catch-up and excludes replay time', () => {
  const line = new api.Timeline(0);
  line.transition('CATCHUP', 0); line.tick(60000); line.transition('LIVE', 60000);
  line.tick(3660000); line.transition('REPLAY', 3660000); line.tick(4260000);
  assert.equal(line.liveMs, 3600000);
});

test('live accumulation excludes gaps/replay/stall and FAIL survives budget stop', () => {
  const line = new api.Timeline(0);
  line.transition('LIVE', 0); line.tick(600000); line.transition('GAP', 600000);
  line.transition('REPLAY', 900000); line.transition('LIVE', 1200000); line.tick(1800000);
  assert.equal(line.liveMs, 1200000);
  line.transition('STALLED', 1800000); line.tick(9999999); assert.equal(line.liveMs, 1200000);
  line.fail('UNEXPLAINED_LOSS'); line.stop('WALL_LIMIT'); assert.equal(line.outcome, 'FAIL');
});

test('queue rejects overflow without dropping admitted bytes', () => {
  const q = new api.Queue(10); const first = Buffer.alloc(8);
  assert.equal(q.push(first), true); assert.equal(q.push(Buffer.alloc(3)), false);
  assert.equal(q.shift(), first); assert.equal(q.bytes, 0);
});

test('restart retains terminal budgets and checksums detect corrupt raw evidence', t => {
  const dir = temp(t); const j = new api.Journal(dir, { anchor: '10' });
  j.append(Buffer.from('original'), { source: 'grpc' });
  j.state.budget = { received: 200, disk: 500, rpc: 20, stopped: 'RPC_LIMIT' }; j.checkpoint(); j.close();
  const recovered = new api.Journal(dir, { resume: true });
  assert.deepEqual(recovered.state.budget, { received: 200, disk: 500, rpc: 20, stopped: 'RPC_LIMIT' }); recovered.close();
  const raw = fs.readFileSync(path.join(dir, 'raw.bin')); raw[raw.length - 1] ^= 1; fs.writeFileSync(path.join(dir, 'raw.bin'), raw);
  assert.throws(() => new api.Journal(dir, { resume: true }), /CORRUPT_EVIDENCE/);
});

test('independent watchdog kills a deliberately stalled owned collector', async t => {
  const dir = temp(t);
  const child = spawn(process.execPath, ['-e', 'process.send("ready"); for (;;) {}'], { stdio: ['ignore', 'ignore', 'ignore', 'ipc'], windowsHide: true });
  t.after(() => { try { child.kill('SIGKILL'); } catch {} });
  await once(child, 'message');
  const exited = once(child, 'exit');
  const watchdog = api.supervise(child, { dir, deadline: Date.now() + 250, floor: 0 });
  t.after(() => { if (watchdog) watchdog.kill(); });
  assert.equal(await Promise.race([exited.then(() => true), new Promise(resolve => setTimeout(() => resolve(false), 2500))]), true,
    'absolute deadline must kill even when collector cannot process timers');
  assert.equal(JSON.parse(fs.readFileSync(path.join(dir, 'watchdog.json'))).reason, 'WALL_LIMIT');
});
