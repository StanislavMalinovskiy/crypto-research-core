'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const { EventEmitter } = require('node:events');
const { Journal, atomic } = require('../journal.cjs');
const { reconcile, Timeline, fail } = require('../core.cjs');
const { finalComparison } = require('../collector.cjs');
const cliFile = path.resolve(__dirname, '../cli.cjs');
const temp = t => { const dir = fs.mkdtempSync(path.join(os.tmpdir(), 's1-repair-')); t.after(() => fs.rmSync(dir, { recursive: true, force: true })); return dir; };
function launcher(denyWrites = false) {
  const realRequire = createRequire(cliFile); const state = { launched: 0, writes: 0 };
  const intercepted = name => {
    if (name === 'node:fs') return { ...fs, mkdirSync(...args) { state.writes++; if (denyWrites) fail('WRITE_ATTEMPT'); return fs.mkdirSync(...args); } };
    if (name === './journal.cjs') return { ...realRequire(name), free: () => 1e12 };
    if (name === './watchdog.cjs') return { supervise: () => ({ connected: false }) };
    if (name === 'node:child_process') return { fork() {
      state.launched++; const child = new EventEmitter(); child.send = () => setImmediate(() => child.emit('exit', 0)); return child;
    } };
    return realRequire(name);
  };
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(cliFile, 'utf8'), { require: intercepted, module, __dirname: path.dirname(cliFile), console: { log() {} }, process });
  return { main: module.exports.main, state };
}
async function inspectedSmoke(t, launch) {
  const root = temp(t), dir = path.join(root, 'smoke'); const journal = new Journal(dir, { anchor: '10' });
  journal.append(Buffer.from('durable'), { source: 'grpc' }); journal.verifyRange('10', '10', []);
  const summary = { mode: 'smoke', outcome: 'INCONCLUSIVE', reason: 'WALL_LIMIT', abrupt: false,
    stats: { messages: 1, recoveries: [{ complete: true }] } };
  journal.state.summary = summary; journal.checkpoint(); atomic(path.join(dir, 'summary.json'), summary);
  await launch.main(['--inspect', 'smoke', '--root', root]); return { root, dir };
}
for (const scenario of ['receipt-incomplete', 'partial-tail', 'corrupt', 'watchdog', 'changed-summary']) {
  test(`full launch rejects ${scenario} smoke evidence before collector launch`, async t => {
    const launch = launcher(), { root, dir } = await inspectedSmoke(t, launch);
    if (scenario === 'receipt-incomplete') atomic(path.join(dir, 'inspection.json'), { incomplete: true });
    if (scenario === 'partial-tail') fs.appendFileSync(path.join(dir, 'raw.bin'), Buffer.from([0, 0]));
    if (scenario === 'corrupt') { const raw = fs.readFileSync(path.join(dir, 'raw.bin')); raw[raw.length - 1] ^= 1; fs.writeFileSync(path.join(dir, 'raw.bin'), raw); }
    if (scenario === 'watchdog') atomic(path.join(dir, 'watchdog.json'), { abrupt: true, reason: 'WALL_LIMIT' });
    if (scenario === 'changed-summary') { const file = path.join(dir, 'summary.json'); const summary = JSON.parse(fs.readFileSync(file)); summary.stats.messages++; atomic(file, summary); }
    let rejected = false; try { await launch.main(['--enable-live', '--mode', 'full', '--root', root]); } catch { rejected = true; }
    assert.equal(rejected, true, 'invalid inspected smoke must reject'); assert.equal(launch.state.launched, 0);
  });
}
test('unchanged complete inspected smoke remains eligible for the separate full launch', async t => {
  const launch = launcher(), { root } = await inspectedSmoke(t, launch);
  await launch.main(['--enable-live', '--mode', 'full', '--root', root]); assert.equal(launch.state.launched, 1);
});
for (const to of ['100', '99']) {
  test(`upper bound ${to} below unresolved 101 never claims reconciliation complete`, async t => {
    const journal = new Journal(temp(t), { anchor: '101' }); journal.state.unresolved = [{ from: '101' }]; journal.checkpoint(); let calls = 0;
    const result = await reconcile({ from: '101', to, journal, call: async () => { calls++; return []; } });
    assert.equal(result.complete, false); assert.equal(result.reason, 'UPPER_BOUND_NOT_ADVANCED');
    assert.equal(journal.state.complete, null); assert.equal(journal.state.unresolved[0].from, '101'); assert.equal(calls, 0);
  });
}
test('equal reconciliation bounds verify their one declared slot', async t => {
  const journal = new Journal(temp(t), { anchor: '101' }); let calls = 0;
  const result = await reconcile({ from: '101', to: '101', journal, call: async () => { calls++; return []; } });
  assert.equal(result.complete, true); assert.equal(journal.state.complete, '101'); assert.equal(calls, 1);
});
for (const reason of ['AUTH_FAILED', 'SCHEMA_INVALID', 'STREAM_ERROR']) {
  test(`final comparison terminates promptly on ${reason} without subsequent RPC`, async t => {
    const journal = new Journal(temp(t), { anchor: '10' }); let resolveEnd, waits = 0, calls = 0;
    const handle = { done: new Promise(resolve => { resolveEnd = resolve; }) };
    const line = new Timeline(0); line.transition('LIVE', 0);
    let actual;
    try { await finalComparison({ line, elapsed: () => 100, upper: async () => '100', stats: { finalized: '99' }, journal, handle,
      budget: { check() { if (waits >= 3) fail('WALL_LIMIT'); } },
      wait: async () => { waits++; resolveEnd({ reason }); }, call: async () => { calls++; return []; } }); }
    catch (error) { actual = error.safeCode; }
    assert.equal(actual, reason, 'stream terminal reason must win over later wall stop'); assert.ok(waits <= 1); assert.equal(calls, 0);
  });
}
test('Windows case and junction aliases of repository roots reject before writes or launch', async t => {
  const repo = path.resolve(__dirname, '../../../..'), tempDir = temp(t), alias = path.join(tempDir, 'repository-alias');
  fs.symlinkSync(repo, alias, 'junction');
  for (const root of [repo.toUpperCase(), alias]) {
    const launch = launcher(true); let actual;
    try { await launch.main(['--enable-live', '--mode', 'smoke', '--root', root]); } catch (error) { actual = error.safeCode; }
    assert.equal(actual, 'UNSAFE_PATH'); assert.equal(launch.state.writes, 0, 'containment must precede mkdir'); assert.equal(launch.state.launched, 0);
  }
});
test('external canonical root remains eligible', async t => {
  const root = temp(t), launch = launcher();
  await launch.main(['--enable-live', '--mode', 'smoke', '--root', root]); assert.equal(launch.state.launched, 1);
});

const configApi = require('../core.cjs');
const syntheticKey = 'SYNTHETIC_round2-key';
const template = 'https://solana-mainnet.g.alchemy.com/v2/${ALCHEMY_API_KEY}';
const syntheticProperties = endpoint => `ALCHEMY_API_KEY=${syntheticKey}\nALCHEMY_SOLANA_RPC_ENDPOINT=${endpoint}\nALCHEMY_SOLANA_GRPC_ENDPOINT=solana-mainnet.g.alchemy.com\nALCHEMY_SOLANA_GRPC_PORT=443`;
test('exact RPC key template resolves the effective key before connecting', async () => {
  let connections = 0;
  const result = await configApi.launch({ enabled: true, env: {}, properties: syntheticProperties(template), connect(settings) {
    connections++; assert.equal(settings.key, syntheticKey);
    assert.equal(settings.rpcEndpoint, `https://solana-mainnet.g.alchemy.com/v2/${syntheticKey}`); return { reason: 'CONNECTED' };
  } });
  assert.equal(result.reason, 'CONNECTED'); assert.equal(connections, 1);
});
test('template uses environment precedence while direct endpoint support remains unchanged', () => {
  const key = 'ENV_SYNTHETIC_key';
  let settings;
  assert.doesNotThrow(() => { settings = configApi.config({ ALCHEMY_API_KEY: key }, syntheticProperties(template)); });
  assert.equal(settings.rpcEndpoint, `https://solana-mainnet.g.alchemy.com/v2/${key}`);
  const direct = `https://solana-mainnet.g.alchemy.com/v2/${key}`;
  assert.equal(configApi.config({ ALCHEMY_API_KEY: key, ALCHEMY_SOLANA_RPC_ENDPOINT: direct }, syntheticProperties(template)).rpcEndpoint, direct);
  assert.equal(configApi.config({}, syntheticProperties(`https://solana-mainnet.g.alchemy.com/v2/${syntheticKey}`)).key, syntheticKey);
});
for (const endpoint of [
  'https://solana-mainnet.g.alchemy.com/v2/${OTHER_KEY}',
  template + '${ALCHEMY_API_KEY}', template + '/${ALCHEMY_API_KEY}',
  'https://${ALCHEMY_API_KEY}.alchemy.com/v2/' + syntheticKey,
  'https://solana-mainnet.g.alchemy.com/${ALCHEMY_API_KEY}/v2',
  template + '?key=${ALCHEMY_API_KEY}', template + '#${ALCHEMY_API_KEY}',
  'https://solana-mainnet.g.alchemy.com/v2/${ALCHEMY_API_KEY',
  'https://solana-mainnet.g.alchemy.com/v2/%24%7BALCHEMY_API_KEY%7D',
  template.replace('https:', 'http:'), template.replace('.com/', '.com:444/'),
  template.replace('solana-mainnet.g.alchemy.com', 'evil.invalid')
]) {
  test(`invalid RPC template ${endpoint} fails redacted before connection`, async () => {
    let connections = 0;
    const result = await configApi.launch({ enabled: true, env: {}, properties: syntheticProperties(endpoint), connect() { connections++; } });
    assert.deepEqual(result, { reason: 'CONFIG_INVALID' }); assert.equal(connections, 0);
    assert.equal(JSON.stringify(result).includes(syntheticKey), false); assert.equal(JSON.stringify(result).includes(endpoint), false);
  });
}
test('template cannot expand an invalid or recursive effective key and preserves duplicate and blank rejection', async () => {
  for (const key of ['', ' ', '${OTHER_KEY}', '${ALCHEMY_API_KEY}', 'key/segment']) {
    let connections = 0;
    const result = await configApi.launch({ enabled: true, env: { ALCHEMY_API_KEY: key }, properties: syntheticProperties(template), connect() { connections++; } });
    assert.deepEqual(result, { reason: 'CONFIG_INVALID' }); assert.equal(connections, 0);
  }
  assert.throws(() => configApi.config({}, syntheticProperties(template) + '\nALCHEMY_API_KEY=duplicate'), /^Error: CONFIG_INVALID$/);
  assert.equal(configApi.diagnostic(new Error(template + syntheticKey)), 'INTERNAL_ERROR');
});

const transport = require('../transport.cjs');
const clockAt = Date.parse('2026-09-27T20:00:00Z');
class FixedDate extends Date { constructor(...args) { super(...(args.length ? args : [clockAt])); } static now() { return clockAt; } }
function loadFake(file, intercept, extra = {}) {
  const actual = createRequire(file), module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(file, 'utf8'), { module, __dirname: path.dirname(file), Buffer, Date: FixedDate,
    require: name => intercept(name, actual) || actual(name), console: { log() {} }, process, performance,
    setInterval: () => 1, clearInterval() {}, ...extra }); return module.exports;
}
function from58(value) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = 0n;
  for (const c of value) n = n * 58n + BigInt(alphabet.indexOf(c));
  let hex = n.toString(16); if (hex.length % 2) hex = '0' + hex; return Buffer.from(hex, 'hex');
}
function wireTx(version, id = 1, fee = '9007199254740993') {
  const message = { account_keys: [from58(configApi.PROGRAMS[0])], versioned: version === 0 };
  if (version === 1) message.config = { priority_fee: fee, compute_unit_limit: 200000 };
  return { transaction: { slot: '100', transaction: { signature: Buffer.alloc(64, id), index: String(id - 1),
    transaction: { message }, meta: {} } } };
}
async function offlineCollector(t, updates, options = {}) {
  const root = options.root || temp(t), dir = options.dir || path.join(root, 'smoke'); fs.mkdirSync(dir, { recursive: true });
  let initial, calls = 0;
  const collector = loadFake(path.resolve(__dirname, '../collector.cjs'), (name, actual) => {
    if (name === './journal.cjs') return { ...actual(name), free: () => 1e12 };
    if (name === './transport.cjs') return { ...transport, sleep: async () => {}, rpc: async ({ budget }) => {
      initial ||= { ...budget.state }; calls++; budget.check(); budget.rpc(); return '100';
    }, stream: async ({ onRaw, budget }) => {
      budget.state.streamStarts = (budget.state.streamStarts || 0) + 1;
      for (const update of updates) onRaw(transport.encode(update), { source: 'grpc', receivedAt: new FixedDate().toISOString() });
      fail('OFFLINE_DONE');
    } };
  }, { process: { env: Object.fromEntries(syntheticProperties(template.replace('${ALCHEMY_API_KEY}', syntheticKey)).split('\n').map(s => s.split('='))), once() {}, version: process.version } });
  await collector.collect({ root, dir, mode: options.mode || 'smoke', deadline: clockAt + 300000, ...options });
  return { root, dir, initial, calls, summary: JSON.parse(fs.readFileSync(path.join(dir, 'summary.json'))), journal: new Journal(dir, { resume: true, readOnly: true }) };
}
test('actual collector retains V1 uint64 raw config, config-first detection, legacy/V0 and equal replay', async t => {
  const v1 = wireTx(1); const run = await offlineCollector(t, [wireTx('legacy', 2), wireTx(0, 3), v1, v1]);
  assert.equal(run.summary.reason, 'OFFLINE_DONE'); assert.equal(run.summary.stats.unique, 3); assert.equal(run.summary.stats.duplicates, 1);
  assert.ok(fs.readFileSync(path.join(run.dir, 'raw.bin')).includes(transport.encode(v1)));
  assert.equal(transport.decode(transport.encode(v1)).transaction.transaction.transaction.message.config.priority_fee, '9007199254740993');
  assert.equal(run.journal.signatures('100').length, 3);
  const manifest = JSON.parse(fs.readFileSync(path.join(run.dir, 'manifest.json')));
  assert.equal(manifest.supportedVersions.maxSupportedTransactionVersion, 1);
});
test('actual collector includes V1 config in immutable identity and retains conflicting raw', async t => {
  const changed = wireTx(1, 1, '9007199254740994'); const run = await offlineCollector(t, [wireTx(1), changed]);
  assert.equal(run.summary.reason, 'IMMUTABLE_CONFLICT'); assert.equal(run.summary.outcome, 'FAIL');
  assert.ok(fs.readFileSync(path.join(run.dir, 'raw.bin')).includes(transport.encode(changed))); assert.equal(run.journal.signatures('100').length, 1);
});
for (const field of ['lookup', 'writable', 'readonly']) test(`collector rejects V1 ${field} address contradiction after raw retention`, async t => {
  const update = wireTx(1), tx = update.transaction.transaction;
  if (field === 'lookup') tx.transaction.message.address_table_lookups = [{ account_key: Buffer.alloc(32) }];
  else tx.meta[`loaded_${field}_addresses`] = [Buffer.alloc(32)];
  const run = await offlineCollector(t, [update]); assert.equal(run.summary.reason, 'SCHEMA_INVALID');
  assert.ok(fs.readFileSync(path.join(run.dir, 'raw.bin')).includes(transport.encode(update))); assert.equal(run.journal.signatures('100').length, 0);
});
function rpcTx(version = 1) {
  const tx = { version, transaction: { signatures: ['v1-signature'], message: { accountKeys: [configApi.PROGRAMS[0]], instructions: [] } }, meta: { err: null, loadedAddresses: { writable: [], readonly: [] } } };
  if (version === 1) tx.transaction.message.transactionConfig = { priorityFee: '9007199254740993' }; return tx;
}
test('RPC V1 coexists with legacy and loaded-address V0', () => {
  const v0 = rpcTx(0); v0.transaction.message.accountKeys = []; v0.meta.loadedAddresses.readonly = [configApi.PROGRAMS[0]];
  let filtered; assert.doesNotThrow(() => { filtered = configApi.filterBlock({ transactions: [rpcTx('legacy'), v0, rpcTx(1)] }); });
  assert.deepEqual(filtered.map(t => t.index), ['0', '1', '2']);
});
for (const shape of ['missing', 'null', 'array', 'legacy-config', 'v0-config', 'future', 'lookup', 'loaded']) test(`RPC V1 rejects ${shape} shape`, () => {
  const tx = rpcTx(), message = tx.transaction.message;
  if (shape === 'missing') delete message.transactionConfig;
  if (shape === 'null') message.transactionConfig = null;
  if (shape === 'array') message.transactionConfig = [];
  if (shape === 'legacy-config') tx.version = 'legacy';
  if (shape === 'v0-config') tx.version = 0;
  if (shape === 'future') tx.version = 2;
  if (shape === 'lookup') message.addressTableLookups = [{}];
  if (shape === 'loaded') tx.meta.loadedAddresses.writable = ['address'];
  assert.throws(() => configApi.filterBlock({ transactions: [tx] }), /UNSUPPORTED/);
});
test('V1 reconciliation declares integer ceiling one and retains missing evidence gaps', async t => {
  const journal = new Journal(temp(t), { anchor: '100' }); journal.transaction({ slot: '100', signature: 'v1-signature', index: '0', hash: 'hash' });
  const ceilings = []; const call = async (method, params) => { if (method === 'getBlocks') return [100]; ceilings.push(params[1].maxSupportedTransactionVersion); return { transactions: [rpcTx()] }; };
  const result = await reconcile({ from: '100', to: '100', journal, call });
  assert.equal(result.complete, true); assert.deepEqual(ceilings, [1]); assert.equal(journal.state.complete, '100');
  const missing = new Journal(temp(t), { anchor: '100' }); const absent = await reconcile({ from: '100', to: '100', journal: missing, call });
  assert.equal(absent.complete, false); assert.equal(absent.reason, 'UNEXPLAINED_LOSS'); assert.equal(missing.state.unresolved.length, 1);
});
function retryLauncher(failLedger = false) {
  const state = { launched: 0, options: null, watch: null, atSpawn: null };
  const api = loadFake(cliFile, (name, actual) => {
    if (name === './journal.cjs') return { ...actual(name), free: () => 1e12, atomic(file, value) { if (failLedger && path.basename(file) === 'shared-budget.json') fail('WRITE_FAILED'); atomic(file, value); } };
    if (name === './watchdog.cjs') return { supervise(child, options) { state.watch = options; return { connected: false }; } };
    if (name === 'node:child_process') return { fork() { state.launched++; const child = new EventEmitter(); child.send = options => {
      state.options = options; state.atSpawn = JSON.parse(fs.readFileSync(path.join(options.root, 'shared-budget.json'))); setImmediate(() => child.emit('exit', 0));
    }; return child; } };
  }); return { ...api, state };
}
function retryFixture(t) {
  const root = temp(t), dir = path.join(root, 'smoke'), journal = new Journal(dir, { anchor: '100' });
  journal.append(Buffer.alloc(10881), { source: 'grpc' }); journal.append(Buffer.alloc(86), { source: 'rpc' }); journal.state.unresolved = [{ from: '100' }];
  const budget = { rpc: 2, received: 10967, grpcBytes: 10881, rpcBytes: 86, streamStarts: 1, stopped: 'SCHEMA_INVALID' };
  const summary = { mode: 'smoke', outcome: 'INCONCLUSIVE', reason: 'SCHEMA_INVALID', abrupt: false, budget,
    startedAt: '2026-09-27T18:21:33.007Z', endedAt: '2026-09-27T18:21:36.458Z', stats: { messages: 3, recoveries: [] } };
  journal.state.budget = budget; journal.state.summary = summary; journal.checkpoint(); atomic(path.join(dir, 'summary.json'), summary);
  atomic(path.join(dir, 'launch.json'), { mode: 'smoke', startedAt: summary.startedAt, deadline: Date.parse(summary.startedAt) + 300000 });
  atomic(path.join(dir, 'inspection.json'), { incomplete: true, historical: true });
  atomic(path.join(root, 'shared-budget.json'), { rpc: 2, activeRun: 'smoke', stopped: 'SCHEMA_INVALID', receivedReserved: 10967, grpcBytes: 10881, rpcBytes: 86 });
  return { root, dir, journal, summary };
}
const retryArgs = root => ['--enable-live', '--mode', 'smoke', '--retry-smoke', '--root', root];
const fileHashes = dir => Object.fromEntries(fs.readdirSync(dir, { recursive: true }).filter(n => fs.statSync(path.join(dir, n)).isFile()).map(n => [n, configApi.hash(fs.readFileSync(path.join(dir, n)))]));
test('one explicit retry preserves predecessor bytes and reserves aggregate debit before spawn', async t => {
  const { root, dir } = retryFixture(t), before = fileHashes(dir), launch = retryLauncher(); let error;
  try { await launch.main(retryArgs(root)); } catch (e) { error = e.safeCode; }
  assert.equal(error, undefined); assert.equal(launch.state.launched, 1); assert.deepEqual(fileHashes(dir), before);
  assert.equal(path.basename(launch.state.options.dir), 'smoke-retry-1'); assert.equal(launch.state.watch.deadline, clockAt + 296549);
  const debit = launch.state.atSpawn.smoke;
  assert.equal(debit.usedMs, 3451); assert.equal(debit.received, 10967); assert.equal(debit.rpc, 2); assert.equal(debit.streamStarts, 1);
  assert.equal(debit.retryDeadline, launch.state.watch.deadline); assert.equal(debit.retryReserved, true); assert.match(debit.predecessor, /^[a-f0-9]{64}$/);
  await assert.rejects(launch.main(retryArgs(root))); assert.equal(launch.state.launched, 1);
});
for (const issue of ['missing-ledger', 'corrupt-ledger', 'mismatch-ledger', 'missing-summary', 'partial-raw', 'corrupt-raw', 'mismatch-summary', 'fail', 'abrupt', 'wrong-reason', 'budget-stop', 'rpc-exhausted', 'received-exhausted', 'wall-exhausted', 'watchdog', 'active-lock', 'existing-full']) {
  test(`retry refuses ${issue} without collector/network launch`, async t => {
    const fixture = retryFixture(t), { root, dir, journal, summary } = fixture, launch = retryLauncher();
    const ledgerFile = path.join(root, 'shared-budget.json'), ledger = JSON.parse(fs.readFileSync(ledgerFile));
    if (issue === 'missing-ledger') fs.unlinkSync(ledgerFile);
    if (issue === 'corrupt-ledger') fs.writeFileSync(ledgerFile, '{');
    if (issue === 'mismatch-ledger') { ledger.rpc++; atomic(ledgerFile, ledger); }
    if (issue === 'missing-summary') fs.unlinkSync(path.join(dir, 'summary.json'));
    if (issue === 'partial-raw') fs.appendFileSync(path.join(dir, 'raw.bin'), Buffer.from([0]));
    if (issue === 'corrupt-raw') { const file = path.join(dir, 'raw.bin'), bytes = fs.readFileSync(file); bytes[bytes.length - 1] ^= 1; fs.writeFileSync(file, bytes); }
    if (issue === 'mismatch-summary') { summary.stats.messages++; atomic(path.join(dir, 'summary.json'), summary); }
    if (issue === 'fail') summary.outcome = 'FAIL';
    if (issue === 'abrupt') summary.abrupt = true;
    if (issue === 'wrong-reason' || issue === 'budget-stop') summary.reason = summary.budget.stopped = issue === 'budget-stop' ? 'RECEIVED_LIMIT' : 'AUTH_FAILED';
    if (issue === 'rpc-exhausted') summary.budget.rpc = 20000;
    if (issue === 'received-exhausted') summary.budget.received = 100000000;
    if (issue === 'wall-exhausted') summary.endedAt = '2026-09-27T18:26:33.007Z';
    if (['fail', 'abrupt', 'wrong-reason', 'budget-stop', 'rpc-exhausted', 'received-exhausted', 'wall-exhausted'].includes(issue)) {
      journal.state.summary = summary; journal.state.budget = summary.budget; journal.checkpoint(); atomic(path.join(dir, 'summary.json'), summary);
      atomic(ledgerFile, { ...ledger, rpc: summary.budget.rpc, receivedReserved: summary.budget.received, stopped: summary.budget.stopped });
    }
    if (issue === 'watchdog') atomic(path.join(dir, 'watchdog.json'), { abrupt: true });
    if (issue === 'active-lock') fs.writeFileSync(path.join(root, 'active.lock'), '');
    if (issue === 'existing-full') fs.mkdirSync(path.join(root, 'full'));
    await assert.rejects(launch.main(retryArgs(root))); assert.equal(launch.state.launched, 0);
  });
}
test('retry ledger reservation write failure cannot spawn and invalid mode combination rejects', async t => {
  const { root } = retryFixture(t), launch = retryLauncher(true);
  await assert.rejects(launch.main(retryArgs(root))); assert.equal(launch.state.launched, 0);
  await assert.rejects(launch.main(['--enable-live', '--mode', 'full', '--retry-smoke', '--root', root])); assert.equal(launch.state.launched, 0);
});
test('retry collector retains cumulative bytes RPC starts and reports per-attempt deltas', async t => {
  const { root } = retryFixture(t), launch = retryLauncher(); let error;
  try { await launch.main(retryArgs(root)); } catch (e) { error = e.safeCode; }
  assert.equal(error, undefined);
  const run = await offlineCollector(t, [wireTx(1)], launch.state.options);
  assert.equal(run.initial.received, 10967); assert.equal(run.initial.rpc, 2); assert.equal(run.initial.streamStarts, 1);
  assert.equal(run.summary.budget.received, 10967); assert.equal(run.summary.budget.rpc, 3); assert.equal(run.summary.budget.streamStarts, 2);
  assert.equal(run.summary.attempt.rpc, 1); assert.equal(run.summary.attempt.received, 0);
  const manifest = JSON.parse(fs.readFileSync(path.join(run.dir, 'manifest.json'))); assert.match(manifest.predecessor, /^[a-f0-9]{64}$/);
  const ledger = JSON.parse(fs.readFileSync(path.join(root, 'shared-budget.json'))); assert.equal(ledger.smoke.rpc, 3); assert.equal(ledger.smoke.received, 10967);
});
test('full and inspection select retry, reject old/changed receipts and retain smoke totals into full', async t => {
  const { root, dir } = retryFixture(t), launch = retryLauncher();
  const retryDir = path.join(root, 'smoke-retry-1'), journal = new Journal(retryDir, { anchor: '100' }); journal.verifyRange('100', '100', []);
  const summary = { mode: 'smoke', outcome: 'INCONCLUSIVE', reason: 'WALL_LIMIT', abrupt: false, stats: { messages: 3, recoveries: [{ complete: true }] } };
  journal.state.summary = summary; journal.checkpoint(); atomic(path.join(retryDir, 'summary.json'), summary);
  const before = fileHashes(dir); await assert.rejects(launch.main(['--enable-live', '--mode', 'full', '--root', root])); assert.equal(launch.state.launched, 0);
  let inspectionError; try { await launch.main(['--inspect', 'smoke', '--retry-smoke', '--root', root]); } catch (e) { inspectionError = e.safeCode; }
  assert.equal(inspectionError, undefined); assert.deepEqual(fileHashes(dir), before);
  assert.equal(JSON.parse(fs.readFileSync(path.join(retryDir, 'inspection.json'))).incomplete, false);
  summary.stats.messages++; journal.state.summary = summary; journal.checkpoint(); atomic(path.join(retryDir, 'summary.json'), summary);
  await assert.rejects(launch.main(['--enable-live', '--mode', 'full', '--root', root])); assert.equal(launch.state.launched, 0);
  await launch.main(['--inspect', 'smoke', '--root', root]);
  const smoke = { usedMs: 300000, received: 20000, grpcBytes: 19000, rpcBytes: 1000, rpc: 5, streamStarts: 2, retryReserved: true };
  atomic(path.join(root, 'shared-budget.json'), { rpc: 5, activeRun: 'smoke-retry-1', stopped: 'WALL_LIMIT', receivedReserved: 20000, grpcBytes: 19000, rpcBytes: 1000, smoke });
  await launch.main(['--enable-live', '--mode', 'full', '--root', root]); assert.equal(launch.state.launched, 1);
  const run = await offlineCollector(t, [], launch.state.options);
  assert.equal(run.initial.rpc, 5); assert.equal(run.initial.received, 0);
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(root, 'shared-budget.json'))).smoke, smoke);
});
