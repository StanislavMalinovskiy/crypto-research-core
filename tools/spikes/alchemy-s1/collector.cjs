'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { Budget, Timeline, config, diagnostic, PROGRAMS, reconcile, fail, hash, exact } = require('./core.cjs');
const { Journal, atomic, free, size } = require('./journal.cjs');
const { rpc, stream, decode, definitions, sleep } = require('./transport.cjs');
const TERMINAL_RESERVE = 1024 * 1024, INFLIGHT_DISK = 48 * 1024 * 1024;
const supportedVersions = { versions: ['legacy', 0, 1], maxSupportedTransactionVersion: 1, detection: 'config-first', v1Addresses: 'inline', config: 'opaque-uint64-string' };
function base58(bytes) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let n = BigInt(`0x${Buffer.from(bytes).toString('hex') || '0'}`), value = '';
  while (n) { value = alphabet[Number(n % 58n)] + value; n /= 58n; }
  for (const b of bytes) { if (b) break; value = '1' + value; } return value;
}
function provenance(mode) {
  const files = ['package-lock.json', 'proto/geyser.proto', 'proto/solana-storage.proto', 'core.cjs', 'journal.cjs', 'transport.cjs', 'collector.cjs', 'watchdog.cjs', 'cli.cjs'];
  const hashes = Object.fromEntries(files.map(file => [file, hash(fs.readFileSync(path.join(__dirname, file)))]));
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: __dirname, encoding: 'utf8', windowsHide: true }).trim();
  const dirty = !!execFileSync('git', ['status', '--porcelain'], { cwd: __dirname, encoding: 'utf8', windowsHide: true }).trim();
  return { version: 's1-v1', mode, revision, dirty, hashes, node: process.version, supportedVersions,
    protoRevision: '9fd6a582df9b5e89bc0ac7da0b4d71c1096f63ed', programs: PROGRAMS,
    filter: { vote: false, failed: false, account_include: PROGRAMS, commitment: 2 },
    accounting: 'Original gRPC protobuf application payload and RPC response body bytes; excludes HTTP/2/TLS overhead and is not provider billable usage',
    latency: 'UTC receive time minus block_meta.block_time (chain seconds); separately UTC receive time minus created_at; live/replay histograms in milliseconds',
    configFingerprint: hash(JSON.stringify({ mode, supportedVersions, programs: PROGRAMS, commitment: 2, gapLiveMs: [600000, 1200000], gapMs: [300000, 3600000] })) };
}
function histogram() { return { count: 0, unavailable: 0, min: null, max: null, sum: 0, boundsMs: [0, 1000, 5000, 10000, 30000, 60000, 120000, 300000, 3600000], buckets: Array(10).fill(0) }; }
function sample(hist, received, seconds, nanos = 0) {
  if (seconds === undefined || seconds === null) { hist.unavailable++; return; }
  if (!/^-?\d+$/.test(String(seconds)) || !Number.isSafeInteger(Number(seconds))) fail('SCHEMA_INVALID');
  const latency = received - Number(seconds) * 1000 - Number(nanos) / 1e6;
  hist.count++; hist.sum += latency; hist.min = hist.min === null ? latency : Math.min(hist.min, latency); hist.max = hist.max === null ? latency : Math.max(hist.max, latency);
  const bucket = hist.boundsMs.findIndex(bound => latency <= bound); hist.buckets[bucket < 0 ? 9 : bucket]++;
}
async function finalComparison({ line, elapsed, upper, stats, budget, journal, call, handle, wait = sleep }) {
  let ended, active = true;
  const completion = handle.done.then(result => {
    ended = result;
    if (active && typeof budget.stop === 'function') budget.stop(result.reason);
  });
  const check = () => { if (ended) fail(ended.reason); budget.check(); };
  const guardedCall = async (method, params) => { check(); const result = await call(method, params); check(); return result; };
  try {
    line.transition('REPLAY', elapsed()); await Promise.resolve(); check();
    const high = await upper(), low = String(BigInt(high) - 4n); check();
    while (BigInt(stats.finalized || '0') <= BigInt(high)) { check(); await Promise.race([wait(100), completion]); check(); }
    check(); const result = await reconcile({ from: low, to: high, journal, call: guardedCall, sampled: true }); check(); return result;
  } finally { active = false; }
}
async function collect(options) {
  const { root, dir, mode, deadline } = options; const now = Date.now, elapsed = () => performance.now();
  const properties = options.secretsFile ? fs.readFileSync(options.secretsFile, 'utf8') : '';
  const settings = config(process.env, properties); // No settings enter diagnostics or manifests.
  const ledgerPath = path.join(root, 'shared-budget.json');
  const shared = fs.existsSync(ledgerPath) ? JSON.parse(fs.readFileSync(ledgerPath)) : { rpc: 0 };
  const retry = path.basename(dir) === 'smoke-retry-1';
  if (retry && (!shared.smoke?.retryReserved || shared.smoke.retryDeadline !== deadline || shared.activeRun !== 'smoke-retry-1')) fail('RETRY_ACCOUNTING_INVALID');
  if (mode === 'full' && (!fs.existsSync(ledgerPath) || !Number.isSafeInteger(shared.rpc) || shared.rpc < 0)) fail('ACCOUNTING_INVALID');
  if (mode === 'full' && shared.smoke && (shared.smoke.rpc !== shared.rpc || shared.smoke.received !== shared.receivedReserved
    || shared.smoke.grpcBytes !== shared.grpcBytes || shared.smoke.rpcBytes !== shared.rpcBytes)) fail('ACCOUNTING_INVALID');
  const initial = retry ? { received: shared.smoke.received, grpcBytes: shared.smoke.grpcBytes, rpcBytes: shared.smoke.rpcBytes,
    streamStarts: shared.smoke.streamStarts, rpc: shared.rpc } : { received: 0, grpcBytes: 0, rpcBytes: 0, rpc: shared.rpc, streamStarts: 0 };
  const limits = { received: mode === 'smoke' ? 100000000 : 100000000000, disk: 10000000000,
    rpc: 20000, floor: 30000000000, deadline };
  const budget = new Budget(limits, { ...initial, disk: size(root) });
  const line = new Timeline(elapsed()); const started = now(); let journal, handle, terminal = false, tick;
  const stats = { messages: 0, transactions: 0, unique: 0, duplicates: 0, outOfOrder: 0, unknown: 0, highest: null,
    finalized: null, live: { chain: histogram(), provider: histogram() }, replay: { chain: histogram(), provider: histogram() },
    gaps: [], recoveries: [], sampledRanges: [], providerQuota: 'UNVERIFIED', dashboard: 'MISSING' };
  let reservedDisk = INFLIGHT_DISK + TERMINAL_RESERVE;
  const guard = bytes => {
    // Writes already admitted by transport reservations may finish after cancellation.
    // New network work still calls Budget.check/reserve before every read/request.
    const headroom = budget.state.stopped ? TERMINAL_RESERVE : reservedDisk;
    if (free(root) - bytes - headroom < limits.floor) { budget.stop('FREE_SPACE_LIMIT'); fail('FREE_SPACE_LIMIT'); }
    if (budget.state.disk + bytes + headroom > limits.disk) { budget.stop('DISK_LIMIT'); fail('DISK_LIMIT'); }
    budget.state.disk += bytes;
  };
  budget.persist = () => {
    const smoke = mode === 'smoke' ? { ...shared.smoke, usedMs: (retry ? shared.smoke.usedMs : 0) + Math.max(0, now() - (options.launchedAt || started)),
      received: budget.state.received, grpcBytes: budget.state.grpcBytes, rpcBytes: budget.state.rpcBytes,
      rpc: budget.state.rpc, streamStarts: budget.state.streamStarts } : shared.smoke;
    atomic(ledgerPath, { rpc: budget.state.rpc, activeRun: path.basename(dir), stopped: budget.state.stopped, smoke,
      receivedReserved: budget.state.received, grpcBytes: budget.state.grpcBytes, rpcBytes: budget.state.rpcBytes });
  };
  const snapshot = () => {
    if (!journal || terminal) return;
    journal.state.budget = { ...budget.state }; journal.state.timeline = { ...line }; journal.state.stats = stats;
    journal.checkpoint();
  };
  const onRpcRaw = (raw, meta) => journal.append(raw, { ...meta, session: 'rpc' });
  const call = (method, params) => rpc({ endpoint: settings.rpcEndpoint, method, params, budget, onRaw: onRpcRaw });
  const upper = async () => exact(await call('getSlot', [{ commitment: 'finalized' }]));
  const position = () => journal.state.unresolved[0]?.from || (journal.state.complete === null ? journal.state.anchor : String(BigInt(journal.state.complete) + 1n));
  const stop = reason => { line.transition('STOPPED', elapsed()); line.stop(reason); budget.stop(reason); if (handle) handle.cancel(reason); };
  const processRaw = (raw, meta) => {
    journal.append(raw, { ...meta, session: String(budget.state.streamStarts), phase: line.mode });
    const update = decode(raw), received = Date.parse(meta.receivedAt); stats.messages++;
    const latency = line.mode === 'LIVE' ? stats.live : stats.replay;
    sample(latency.provider, received, update.created_at?.seconds, update.created_at?.nanos || 0);
    const event = update.transaction || update.slot || update.block_meta;
    if (event) {
      const slot = exact(event.slot || '0');
      if (stats.highest !== null && BigInt(slot) < BigInt(stats.highest)) stats.outOfOrder++;
      if (stats.highest === null || BigInt(slot) > BigInt(stats.highest)) stats.highest = slot;
      if (update.slot && update.slot.status === 2 || update.block_meta) {
        if (stats.finalized === null || BigInt(slot) > BigInt(stats.finalized)) stats.finalized = slot;
      }
      if (update.transaction) {
        const tx = update.transaction.transaction;
        if (!tx || !tx.transaction?.message || !tx.meta || tx.is_vote || tx.meta.err || !Buffer.isBuffer(tx.signature) || tx.signature.length !== 64) fail('SCHEMA_INVALID');
        const message = tx.transaction.message;
        const version = message.config != null ? 1 : message.versioned ? 0 : 'legacy';
        if (version === 1 && ((message.address_table_lookups || []).length || (tx.meta.loaded_writable_addresses || []).length || (tx.meta.loaded_readonly_addresses || []).length)) fail('SCHEMA_INVALID');
        const keys = [...(tx.transaction.message.account_keys || []), ...(tx.meta.loaded_writable_addresses || []), ...(tx.meta.loaded_readonly_addresses || [])].map(base58);
        if (!keys.some(key => PROGRAMS.includes(key))) fail('FILTER_MISMATCH');
        const result = journal.transaction({ slot, signature: base58(tx.signature), index: exact(tx.index || '0'),
          hash: hash(definitions['geyser.SubscribeUpdateTransactionInfo'].serialize(tx)) });
        stats.transactions++; if (result === 'duplicate') stats.duplicates++; else stats.unique++;
      }
      if (update.block_meta) sample(latency.chain, received, update.block_meta.block_time?.timestamp);
    } else if (update.ping) { if (handle) handle.ping(); }
    else if (!update.pong) stats.unknown++;
    if (stats.messages % 100 === 0 || now() - (journal.state.savedAt || 0) >= 250) { journal.state.savedAt = now(); snapshot(); }
  };
  const open = async () => {
    for (let attempt = 0; attempt < 3; attempt++) {
      budget.check(); await sleep(1000 * 2 ** attempt); budget.check();
      handle = await stream({ endpoint: settings.grpcEndpoint, key: settings.key, from: position(), budget, onRaw: processRaw });
      let early; handle.done.then(value => { early = value; }); await sleep(100);
      if (!early) return;
      if (early.reason === 'AUTH_FAILED') fail('AUTH_FAILED');
      if (!['STREAM_CONNECT', 'STREAM_ENDED', 'STREAM_ERROR', 'STREAM_STALLED'].includes(early.reason)) fail(early.reason);
    } fail('RECONNECT_EXHAUSTED');
  };
  const catchUp = async (from, target, label) => {
    const start = now(); let ended; handle.done.then(value => { ended = value; });
    line.transition('REPLAY', elapsed());
    if (BigInt(target) < BigInt(from)) fail('UPPER_BOUND_NOT_ADVANCED');
    while (!ended && (stats.finalized === null || BigInt(stats.finalized) <= BigInt(target))) { budget.check(); await sleep(100); }
    if (ended) fail(ended.reason);
    const result = await reconcile({ from, to: target, journal, call });
    stats.recoveries.push({ label, startedAt: new Date(start).toISOString(), durationMs: now() - start, ...result });
    if (!result.complete) { if (result.outcome === 'FAIL') line.fail(result.reason); fail(result.reason); }
    // Reconciliation can take minutes; require a fresh finalized bound before LIVE.
    const fresh = await upper();
    if (BigInt(fresh) < BigInt(target)) fail('UPPER_BOUND_NOT_ADVANCED');
    while (!ended && (stats.finalized === null || BigInt(stats.finalized) <= BigInt(fresh))) { budget.check(); await sleep(100); }
    if (ended) fail(ended.reason); line.transition('LIVE', elapsed()); snapshot();
  };
  try {
    guard(65536); atomic(path.join(dir, 'manifest.json'), { ...provenance(mode), startedAt: new Date(started).toISOString(), limits,
      attempt: path.basename(dir), predecessor: retry ? shared.smoke.predecessor : null, initialCounters: initial });
    journal = new Journal(dir, { guard, anchor: '0' });
    tick = setInterval(() => {
      try {
        // Files include all runs, indexes, checkpoints, logs, and ledger temporary files.
        budget.state.disk = size(root); guard(0);
        // Allow fsync/cancellation a small head start; the independent process
        // still owns the unchanged absolute deadline if this loop stalls.
        if (now() >= deadline - 1500) { stop('WALL_LIMIT'); return; }
        line.tick(elapsed()); snapshot();
      } catch (e) { stop(diagnostic(e)); }
    }, 1000);
    process.once('SIGINT', () => stop('INTERRUPTED')); process.once('SIGTERM', () => stop('INTERRUPTED'));
    journal.state.anchor = await upper(); journal.state.unresolved = [{ from: journal.state.anchor }]; snapshot();
    await open(); await catchUp(journal.state.anchor, await upper(), 'initial');
    let disconnected; handle.done.then(result => { disconnected = result; });
    while (!budget.state.stopped) {
      await sleep(100); budget.check(); line.tick(elapsed());
      if (disconnected) {
        if (disconnected.reason === 'STREAM_STALLED' && line.mode === 'LIVE') line.liveMs = Math.max(0, line.liveMs - (now() - disconnected.lastReceived));
        line.transition('REPLAY', elapsed()); journal.event({ type: 'disconnect', reason: disconnected.reason, at: new Date().toISOString() });
        if (!['STREAM_CONNECT', 'STREAM_ENDED', 'STREAM_ERROR', 'STREAM_STALLED'].includes(disconnected.reason)) fail(disconnected.reason);
        const from = position(); journal.state.unresolved = [{ from }]; snapshot();
        stats.finalized = null; await open(); await catchUp(from, await upper(), 'unplanned');
        disconnected = null; handle.done.then(result => { disconnected = result; });
      }
      const gapIndex = stats.gaps.length;
      if (mode === 'full' && gapIndex < 2 && line.liveMs >= [600000, 1200000][gapIndex]) {
        const from = position(), duration = [300000, 3600000][gapIndex];
        line.transition('GAP', elapsed()); journal.state.unresolved = [{ from }]; snapshot(); handle.cancel('FORCED_GAP');
        const at = now(); stats.gaps.push({ durationMs: duration, startedAt: new Date(at).toISOString(), from });
        while (now() - at < duration) { budget.check(); await sleep(250); }
        stats.finalized = null; await open(); await catchUp(from, await upper(), `forced_${duration}`);
        disconnected = null; handle.done.then(result => { disconnected = result; });
      }
      if (mode === 'full' && line.liveMs >= 21600000) {
        const result = await finalComparison({ line, elapsed, upper, stats, budget, journal, call, handle }); stats.sampledRanges.push(result);
        if (result.outcome === 'FAIL') line.fail(result.reason);
        stop(result.complete ? 'REQUIRED_EXTERNAL_EVIDENCE_MISSING' : result.reason); break;
      }
    }
  } catch (e) {
    const code = diagnostic(e);
    if (['IMMUTABLE_CONFLICT', 'CORRUPT_EVIDENCE', 'FILTER_MISMATCH', 'UNEXPLAINED_LOSS'].includes(code)) line.fail(code);
    stop(code);
  } finally {
    terminal = true; clearInterval(tick); if (handle) handle.cancel(); reservedDisk = TERMINAL_RESERVE;
    const summary = { outcome: line.outcome, reason: line.reason || budget.state.stopped, abrupt: false, startedAt: new Date(started).toISOString(), endedAt: new Date().toISOString(),
      liveMs: line.liveMs, mode, budget: budget.state, stats, requiredMissing: ['dashboard_usage_comparison', 'provider_quota_exhaustion'],
      attempt: { name: path.basename(dir), received: budget.state.received - initial.received, grpcBytes: budget.state.grpcBytes - initial.grpcBytes,
        rpcBytes: budget.state.rpcBytes - initial.rpcBytes, rpc: budget.state.rpc - initial.rpc, streamStarts: budget.state.streamStarts - initial.streamStarts },
      scope: 'Recovery and sampled ranges only; not whole-run completeness. Smoke contributes no full live time.' };
    // Terminal writes use reserved headroom, never restart work or reset a stop.
    if (journal) { journal.guard = () => {}; journal.state.summary = summary; journal.state.budget = budget.state; journal.checkpoint(); }
    atomic(path.join(dir, 'summary.json'), summary); budget.persist();
  }
}
if (require.main === module) process.once('message', options => collect(options).then(() => process.exit(0)).catch(error => {
  if (process.send) process.send({ reason: diagnostic(error) }); process.exit(1);
}));
module.exports = { collect, provenance, base58, finalComparison };
