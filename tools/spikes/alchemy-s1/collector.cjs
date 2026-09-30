'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { Budget, Timeline, config, diagnostic, PROGRAMS, reconcile, fail, hash, exact, slotOffset } = require('./core.cjs');
const { Journal, atomic, free, size } = require('./journal.cjs');
const { rpc, stream, decode, definitions, sleep } = require('./transport.cjs');
const TERMINAL_RESERVE = 1024 * 1024, INFLIGHT_DISK = 48 * 1024 * 1024;
// Match the transport's inactivity stop so an unobserved interval is never credited.
const LIVE_RECEIPT_GAP_MS = 30000;
const supportedVersions = { versions: ['legacy', 0, 1], maxSupportedTransactionVersion: 1, detection: 'config-first', v1Addresses: 'inline', config: 'opaque-uint64-string' };
function base58(bytes) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let n = BigInt(`0x${Buffer.from(bytes).toString('hex') || '0'}`), value = '';
  while (n) { value = alphabet[Number(n % 58n)] + value; n /= 58n; }
  for (const b of bytes) { if (b) break; value = '1' + value; } return value;
}
function tokenAccount(bytes) {
  if (!Buffer.isBuffer(bytes) || bytes.length !== 165) return null;
  return { layout: 'SPL_TOKEN_ACCOUNT_165', mint: base58(bytes.subarray(0, 32)), authority: base58(bytes.subarray(32, 64)),
    amount: bytes.readBigUInt64LE(64).toString() };
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
  if (options.mode === 'state-probe') return collectState(options);
  if (options.mode === 'b-history') return collectState(options);
  if (options.mode === 'a-live-replay') return collectProviderA(options);
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
  if (mode === 'a-live-replay') Object.assign(stats, { successful: 0, failed: 0 });
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
        const abMode = mode === 'a-live-replay', failed = tx?.meta?.err !== undefined && tx?.meta?.err !== null;
        if (!tx || !tx.transaction?.message || !tx.meta || tx.is_vote || (!abMode && failed) || !Buffer.isBuffer(tx.signature) || tx.signature.length !== 64) fail('SCHEMA_INVALID');
        const message = tx.transaction.message;
        const version = message.config != null ? 1 : message.versioned ? 0 : 'legacy';
        if (version === 1 && ((message.address_table_lookups || []).length || (tx.meta.loaded_writable_addresses || []).length || (tx.meta.loaded_readonly_addresses || []).length)) fail('SCHEMA_INVALID');
        const keys = [...(tx.transaction.message.account_keys || []), ...(tx.meta.loaded_writable_addresses || []), ...(tx.meta.loaded_readonly_addresses || [])].map(base58);
        if (!keys.some(key => PROGRAMS.includes(key))) fail('FILTER_MISMATCH');
        const result = journal.transaction({ slot, signature: base58(tx.signature), index: exact(tx.index || '0'),
          hash: hash(definitions['geyser.SubscribeUpdateTransactionInfo'].serialize(tx)) });
        stats.transactions++; if (result === 'duplicate') stats.duplicates++; else {
          stats.unique++; if (abMode) stats[failed ? 'failed' : 'successful']++;
        }
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
async function collectProviderA(options) {
  const { root, dir, deadline } = options, abLedgerPath = path.join(root, 'shared-budget.json');
  if (!Number.isSafeInteger(deadline)) fail('ARGUMENT_INVALID');
  if (options.sourceDebit) {
    const expectedRoot = path.resolve('C:/crypto-research-evidence/provider-a-final-1'), expectedParent = path.dirname(expectedRoot);
    const expectedDebit = path.resolve('C:/crypto-research-evidence/alchemy-s1');
    let rootInfo, debitInfo, rootReal, debitReal, parentReal;
    try {
      rootInfo = fs.lstatSync(root); debitInfo = fs.lstatSync(options.debitRoot);
      rootReal = fs.realpathSync(root); debitReal = fs.realpathSync(options.debitRoot); parentReal = fs.realpathSync(path.dirname(root));
    } catch { fail('STATE_DEBIT_INVALID'); }
    if (root !== expectedRoot || dir !== path.join(expectedRoot, 'a-live-replay') || options.debitRoot !== expectedDebit
      || path.dirname(root) !== expectedParent || path.dirname(options.debitRoot) !== expectedParent || parentReal !== expectedParent
      || rootReal !== expectedRoot || debitReal !== expectedDebit || !rootInfo.isDirectory() || rootInfo.isSymbolicLink()
      || !debitInfo.isDirectory() || debitInfo.isSymbolicLink()
      || JSON.stringify(require('./cli.cjs').stateDebit(options.debitRoot)) !== JSON.stringify(options.sourceDebit)) fail('STATE_DEBIT_INVALID');
  }
  const settings = config(process.env, options.secretsFile ? fs.readFileSync(options.secretsFile, 'utf8') : '');
  const inheritedLedger = fs.existsSync(abLedgerPath) ? JSON.parse(fs.readFileSync(abLedgerPath)) : { budget: {} };
  const previous = inheritedLedger.budget || {}, initial = { received: 0, disk: 0, rpc: 0, stopped: null, grpcBytes: 0,
    rpcBytes: 0, streamStarts: 0, spendPicoUsd: 0, ...previous };
  if (initial.stopped && !['MATRIX_COMPLETE', 'A_COMPLETE', 'A_INCOMPLETE'].includes(initial.stopped)) fail('BUDGET_STOP');
  initial.stopped = null;
  const limits = { received: initial.received + 15000000000, disk: 16000000000, rpc: initial.rpc + 15,
    floor: 30000000000, deadline, spendPicoUsd: 3000000000000, picoUsdPerGrpcByte: 75, picoUsdPerCu: 525000 };
  const budget = new Budget(limits, { ...initial, disk: size(root) });
  const startedAt = new Date().toISOString(), elapsed = () => performance.now(), line = new Timeline(elapsed());
  const observed = { messages: 0, bytes: 0, transactions: 0, unique: 0, successful: 0, failed: 0, perProgram: Object.fromEntries(PROGRAMS.map(x => [x, 0])), overlaps: 0 };
  const stats = { observed, live: { messages: 0, bytes: 0, transactions: 0, unique: 0, successful: 0, failed: 0, perProgram: Object.fromEntries(PROGRAMS.map(x => [x, 0])), overlaps: 0 },
    replay: [], lagMs: [], lagSamplesOmitted: 0, missingLag: 0, highestFinalized: null, liveStarted: false, streamStopReason: null, reconnects: 0, stalls: 0, gaps: 0 };
  const probes = [];
  const guard = bytes => {
    if (free(root) - bytes - 1048576 < limits.floor) fail('FREE_SPACE_LIMIT');
    if (size(root) + bytes + 1048576 > limits.disk) fail('DISK_LIMIT');
    budget.state.disk = size(root) + bytes;
  };
  let journal, handle, tick, phase = 'CATCHUP', lastLiveEvidenceAt = null;
  const interrupt = () => budget.stop('INTERRUPTED');
  const persist = () => {
    inheritedLedger.activeRun = 'a-live-replay'; inheritedLedger.budget = { ...budget.state };
    inheritedLedger.updatedAt = new Date().toISOString(); atomic(abLedgerPath, inheritedLedger);
  };
  budget.persist = persist;
  process.once('SIGINT', interrupt); process.once('SIGTERM', interrupt);
  const liveFilter = { transactions: { watched: { vote: false, account_include: PROGRAMS } }, slots: { finalized: { filter_by_commitment: true } },
    blocks_meta: { finalized: {} }, commitment: 2 };
  const replayFilter = { slots: { finalized: { filter_by_commitment: true } }, commitment: 2 };
  const onRaw = (raw, meta = {}) => {
    const receivedAtMono = elapsed(), receivedAt = Date.parse(meta.receivedAt || new Date().toISOString());
    journal.append(raw, { ...meta, session: phase, phase });
    const update = decode(raw); let enteredLive = false;
    if (phase === 'CATCHUP' || phase === 'LIVE') { observed.messages++; observed.bytes += raw.length; }
    if (phase === 'LIVE') { stats.live.messages++; stats.live.bytes += raw.length; }
    else if (phase.startsWith('REPLAY_')) {
      const probe = probes[probes.length - 1]; probe.bytes += raw.length; probe.messages++;
      const slotUpdate = update.slot && (update.slot.status === 2 || update.slot.status === 'SLOT_FINALIZED');
      if (slotUpdate && probe.firstSlot === null) probe.firstSlot = exact(update.slot.slot);
    }
    const slotEvent = update.slot || update.block_meta;
    if (slotEvent && (update.slot?.status === 2 || update.slot?.status === 'SLOT_FINALIZED' || update.block_meta)) {
      const slot = exact(slotEvent.slot); if (stats.highestFinalized === null || BigInt(slot) > BigInt(stats.highestFinalized)) stats.highestFinalized = slot;
      if (phase === 'CATCHUP' && BigInt(slot) >= BigInt(journal.state.anchor)) {
        stats.liveStarted = true; phase = 'LIVE'; enteredLive = true;
        line.transition('LIVE', receivedAtMono); lastLiveEvidenceAt = receivedAtMono;
      }
    }
    if (update.transaction) {
      const info = update.transaction.transaction, tx = info;
      if (!tx || !tx.transaction?.message || !tx.meta || tx.is_vote || !Buffer.isBuffer(tx.signature) || tx.signature.length !== 64) fail('SCHEMA_INVALID');
      const message = tx.transaction.message, keys = [...(message.account_keys || []), ...(tx.meta.loaded_writable_addresses || []), ...(tx.meta.loaded_readonly_addresses || [])].map(base58);
      const matched = PROGRAMS.filter(program => keys.includes(program)); if (!matched.length) fail('FILTER_MISMATCH');
      const slot = exact(update.transaction.slot || slotEvent?.slot || '0'), signature = base58(tx.signature), index = exact(tx.index || '0');
      const failed = tx.meta.err !== undefined && tx.meta.err !== null;
      const result = journal.transaction({ slot, signature, index, hash: hash(definitions['geyser.SubscribeUpdateTransactionInfo'].serialize(info)) });
      if (phase === 'CATCHUP' || phase === 'LIVE') observed.transactions++;
      if (phase === 'LIVE') stats.live.transactions++;
      if (result !== 'duplicate') {
        if (phase === 'CATCHUP' || phase === 'LIVE') {
          observed.unique++; observed[failed ? 'failed' : 'successful']++;
          for (const program of matched) observed.perProgram[program]++;
          if (matched.length > 1) observed.overlaps++;
        }
        if (phase === 'LIVE') {
          stats.live.unique++; stats.live[failed ? 'failed' : 'successful']++;
          for (const program of matched) stats.live.perProgram[program]++;
          if (matched.length > 1) stats.live.overlaps++;
        }
      }
    }
    if (phase === 'LIVE') {
      const seconds = update.created_at?.seconds;
      if (seconds === undefined || !Number.isSafeInteger(Number(seconds))) stats.missingLag++;
      else if (stats.lagMs.length < 100000) stats.lagMs.push(receivedAt - Number(seconds) * 1000 - Number(update.created_at?.nanos || 0) / 1e6);
      else stats.lagSamplesOmitted++;
    }
    const finalizedReceipt = (update.slot && (update.slot.status === 2 || update.slot.status === 'SLOT_FINALIZED'))
      || !!update.block_meta || !!update.transaction;
    if (phase === 'LIVE' && finalizedReceipt && !enteredLive) {
      if (lastLiveEvidenceAt === null) lastLiveEvidenceAt = receivedAtMono;
      else {
        const gap = receivedAtMono - lastLiveEvidenceAt;
        if (budget.streamBusy && gap >= 0 && gap < LIVE_RECEIPT_GAP_MS) line.tick(receivedAtMono);
        else {
          line.mode = 'GAP'; line.at = receivedAtMono; line.transition('LIVE', receivedAtMono); stats.gaps++;
        }
        lastLiveEvidenceAt = receivedAtMono;
      }
    }
    if (stats.live.messages % 100 === 0) { journal.state.stats = stats; journal.checkpoint(); }
  };
  const getSlot = () => rpc({ endpoint: settings.rpcEndpoint, method: 'getSlot', params: [{ commitment: 'finalized' }], budget,
    maxAttempts: 1, cuPerAttempt: 20, maxBody: 1048576, onRaw: (raw, meta) => journal.append(raw, { ...meta, session: 'rpc', phase }) });
  try {
    guard(65536);
    journal = new Journal(dir, { guard, anchor: '0' });
    const anchor = exact(await getSlot()); journal.state.anchor = anchor; journal.checkpoint();
    atomic(path.join(dir, 'manifest.json'), { ...provenance('a-live-replay'), mode: 'a-live-replay', startedAt, limits,
      filter: { live: { ...liveFilter, from_slot: anchor }, replay: { ...replayFilter, from_slot: '<fresh-tip-minus-offset>' } },
      sourceDebit: inheritedLedger.sourceDebit || null, intervalRecovery: 'NOT_CLAIMED' });
    const streamOptions = { endpoint: settings.grpcEndpoint, key: settings.key, from: anchor, budget, onRaw, maxStreamStarts: initial.streamStarts + 3 };
    handle = await stream({ ...streamOptions, subscription: { ...liveFilter, from_slot: anchor } });
    let ended; handle.done.then(value => { ended = value; });
    tick = setInterval(() => { try { budget.check(); if (Date.now() >= deadline) budget.stop('WALL_LIMIT'); } catch {} }, 250);
    while (line.liveMs < 3600000 && !ended && !budget.state.stopped) {
      await sleep(250);
      if (Date.now() >= deadline) { budget.stop('WALL_LIMIT'); break; }
    }
    stats.streamStopReason = line.liveMs >= 3600000 ? 'A_LIVE_COMPLETE' : ended?.reason || budget.state.stopped || 'A_LIVE_INCOMPLETE';
    if (stats.streamStopReason === 'STREAM_STALLED') stats.stalls++;
    if (ended && line.liveMs < 3600000) stats.gaps++;
    if (handle) handle.cancel(stats.streamStopReason);
    if (line.mode === 'LIVE') line.transition('REPLAY', lastLiveEvidenceAt ?? line.at);
    if (!budget.state.stopped) for (const offset of [9000, 60000]) {
      const probe = { tip: null, offset, fromSlot: null, firstSlot: null, bytes: 0, messages: 0, elapsedMs: 0, result: 'EMPTY_OR_TIMEOUT' };
      probes.push(probe); phase = `REPLAY_${offset}`; const began = Date.now(); probe.tip = exact(await getSlot()); probe.fromSlot = slotOffset(probe.tip, offset);
      const replay = await stream({ endpoint: settings.grpcEndpoint, key: settings.key, from: probe.fromSlot, budget, onRaw,
        subscription: { ...replayFilter, from_slot: probe.fromSlot }, stopOnFirstFinalizedSlot: true, timeoutMs: 60000,
        maxReceivedBytes: 10000000, maxStreamStarts: initial.streamStarts + 3 });
      const result = await replay.done; probe.elapsedMs = Date.now() - began;
      probe.result = probe.firstSlot !== null && result.reason === 'FIRST_FINALIZED_SLOT' ? 'SAMPLE_RECEIVED'
        : result.reason === 'STREAM_TIMEOUT' || result.reason === 'STREAM_STALLED' ? 'EMPTY_OR_TIMEOUT'
          : budget.state.stopped || result.reason === 'SESSION_RECEIVED_LIMIT' ? 'BUDGET_STOP' : 'PROVIDER_ERROR';
      stats.replay.push({ ...probe }); phase = 'REPLAY';
      if (budget.state.stopped) break;
    }
  } catch (e) { stats.reason = diagnostic(e); }
  finally {
    clearInterval(tick); process.removeListener?.('SIGINT', interrupt); process.removeListener?.('SIGTERM', interrupt); if (handle) handle.cancel();
    const ordered = [...stats.lagMs].sort((a, b) => a - b), percentile = p => ordered.length ? ordered[Math.max(0, Math.ceil(p * ordered.length) - 1)] : null;
    const liveHours = line.liveMs / 3600000, bytesPerHour = liveHours > 0 ? stats.live.bytes / liveHours : null;
    const modeBudget = { received: budget.state.received - initial.received, grpcBytes: budget.state.grpcBytes - initial.grpcBytes,
      rpcBytes: budget.state.rpcBytes - initial.rpcBytes, rpc: budget.state.rpc - initial.rpc, spendPicoUsd: budget.state.spendPicoUsd - initial.spendPicoUsd };
    const summary = { mode: 'a-live-replay', outcome: 'INCONCLUSIVE', reason: stats.reason || budget.state.stopped || (line.liveMs >= 3600000 ? 'A_COMPLETE' : 'A_INCOMPLETE'),
      abrupt: false, startedAt, endedAt: new Date().toISOString(), cleanLiveMs: line.liveMs, budget: { ...budget.state }, modeBudget,
      rawJournalBytes: journal ? journal.state.offset : 0, stats: { ...stats.observed },
      live: { ...stats.live, failureShare: stats.live.unique ? stats.live.failed / stats.live.unique : null, p50LagMs: percentile(.5), p95LagMs: percentile(.95),
        lagSource: 'provider created_at', lagSamples: ordered.length, lagSamplesOmitted: stats.lagSamplesOmitted, missingLag: stats.missingLag, eventsPerHour: liveHours ? stats.live.messages / liveHours : null,
        uniqueTransactionsPerHour: liveHours ? stats.live.unique / liveHours : null, bytesPerHour,
        projectedGbPerDay: bytesPerHour === null ? null : bytesPerHour * 24 / 1e9,
        projectedTbPer30Days: bytesPerHour === null ? null : bytesPerHour * 24 * 30 / 1e12,
        projectionLowConfidence: line.liveMs < 3600000, streamStopReason: stats.streamStopReason, reconnects: 0, stalls: stats.stalls, gaps: stats.gaps, dbGrowth: 'N/A' },
      replay: probes, intervalRecovery: 'NOT_CLAIMED', scope: 'First-slot samples only; no interval transaction recovery or completeness claim' };
    if (journal) { journal.state.summary = summary; journal.state.budget = budget.state; journal.state.stats = { ...stats, lagMs: undefined }; journal.checkpoint(); }
    guard(8192); atomic(path.join(dir, 'summary.json'), summary); persist();
  }
}
async function collectState(options) {
  const { root, dir, debitRoot, deadline } = options, mode = options.mode || 'state-probe', isAb = mode === 'b-history', { stateDebit } = require('./cli.cjs');
  if ((!isAb && path.basename(root) !== 'provider-s3-archive-1') || (isAb && path.basename(root) !== 'provider-ab-1')
    || path.basename(debitRoot) !== 'alchemy-s1' || path.dirname(root) !== path.dirname(debitRoot)
    || dir !== path.join(root, isAb ? 'b-history' : 'state-probe') || !Number.isSafeInteger(deadline)) fail('ARGUMENT_INVALID');
  const inherited = stateDebit(debitRoot), settings = config(process.env, options.secretsFile ? fs.readFileSync(options.secretsFile, 'utf8') : '');
  if (isAb && JSON.stringify(inherited) !== JSON.stringify(options.sourceDebit)) fail('STATE_DEBIT_INVALID');
  const parent = path.dirname(root), ledgerPath = path.join(root, 'shared-budget.json');
  const shared = isAb ? JSON.parse(fs.readFileSync(ledgerPath)) : null;
  const initial = isAb ? { received: 0, disk: 0, rpc: 0, stopped: null, grpcBytes: 0, rpcBytes: 0, streamStarts: 0,
    spendPicoUsd: 0, ...shared.budget } : { ...inherited.counters, streamStarts: 2 };
  if (isAb && initial.stopped && !['MATRIX_COMPLETE', 'A_COMPLETE', 'A_INCOMPLETE'].includes(initial.stopped)) fail('BUDGET_STOP');
  initial.stopped = null;
  const limits = isAb ? { received: initial.received + 4000000, rpc: initial.rpc + 12, disk: 16000000000, floor: 30000000000,
    deadline, spendPicoUsd: 3000000000000, picoUsdPerGrpcByte: 75, picoUsdPerCu: 525000 }
    : { received: 79861230, rpc: 26, disk: 10000000000, floor: 30000000000, deadline };
  if ([root, debitRoot, parent].some(p => fs.realpathSync(p).toLowerCase() !== path.resolve(p).toLowerCase())) fail('UNSAFE_PATH');
  if (free(parent) - 5065536 < limits.floor) fail('FREE_SPACE_LIMIT');
  if (size(parent) + 5065536 > limits.disk) fail('DISK_LIMIT');
  const budget = new Budget(limits, { ...initial, disk: isAb ? size(root) : size(parent) });
  const matrix = [], addresses = ['9rPogiERgqQPCYJ5hbXu7LsUjXA5G9DcA3d9pX1bvUox', 'BWquordxHk39m9d7LRyQeg5tGmu1z19ismnJTTiWHJ7F', 'CUhM4HepHThb6zcTj4BSiA7RovTokQwgQCvQLWQpqz7e'];
  for (const repeat of [1, 2]) for (const slot of [429644638, 429644639]) for (const address of addresses) matrix.push({ repeat, slot, address, status: 'UNEXECUTED', attempts: 0 });
  let terminal = false, journal, tick, reason = 'MATRIX_COMPLETE', current, bodyParts = new Map(); const startedAt = new Date().toISOString();
  const guard = bytes => {
    const reserve = terminal ? 0 : 65536;
    if (free(parent) - bytes - reserve < limits.floor) fail('FREE_SPACE_LIMIT');
    if (size(parent) + bytes + reserve > limits.disk) fail('DISK_LIMIT');
    if (size(isAb ? dir : root) + bytes + reserve > 5000000) fail('EVIDENCE_LIMIT');
    budget.state.disk = isAb ? size(root) + bytes : size(parent) + bytes;
  };
  // The output ceiling is separate from transport's cumulative received-byte reservation.
  guard(0);
  budget.persist = () => isAb
    ? atomic(ledgerPath, { ...shared, activeRun: 'b-history', stopped: budget.state.stopped, budget: { ...budget.state } })
    : atomic(ledgerPath, { inherited, rpc: budget.state.rpc, activeRun: 'state-probe', stopped: budget.state.stopped,
      receivedReserved: budget.state.received, grpcBytes: budget.state.grpcBytes, rpcBytes: budget.state.rpcBytes, budget: { ...budget.state } });
  const snapshot = () => { journal.state.budget = { ...budget.state }; journal.state.matrix = matrix; journal.checkpoint(); };
  const stop = code => { reason = code; budget.stop(code); };
  try {
    guard(8192); atomic(path.join(dir, 'manifest.json'), { ...provenance(mode), mode, inherited, sourceDebit: isAb ? shared.sourceDebit || null : undefined, limits, maxBody: 131072,
      newLimits: { rpc: 12, received: 4000000, output: 5000000, publishedCU: 120 }, matrix, startedAt, scope: 'Raw historical collection only; no LIVE or recovery proof' });
    journal = new Journal(dir, { guard }); budget.persist(); snapshot();
    tick = setInterval(() => { try { guard(0); if (Date.now() >= deadline) stop('WALL_LIMIT'); } catch (e) { stop(diagnostic(e)); } }, 1000);
    process.once('SIGINT', () => stop('INTERRUPTED')); process.once('SIGTERM', () => stop('INTERRUPTED'));
    for (const entry of matrix) {
      budget.check(); guard(196608); current = entry; const before = budget.state.rpc; entry.status = 'INCOMPLETE'; bodyParts = new Map();
      try {
        await rpc({ endpoint: settings.rpcEndpoint, method: 'getAccountInfo', params: [entry.address, { encoding: 'base64', commitment: 'finalized', slot: entry.slot }], budget, maxBody: 131072,
          cuPerAttempt: isAb ? 10 : 0,
          onRaw(raw, meta) {
            const kept = raw.subarray(0, Math.max(0, 131072 - meta.bodyOffset));
            if (kept.length) { journal.append(kept, { ...meta, repeat: entry.repeat, slot: entry.slot, address: entry.address,
              admittedChunkBytes: raw.length, truncated: kept.length !== raw.length });
              const parts = bodyParts.get(meta.request) || []; parts.push(kept); bodyParts.set(meta.request, parts); }
            if (meta.bodyOffset + raw.length > 131072) fail('RPC_BODY_LIMIT');
          } });
        // Validate only the protocol envelope. Financial values are never reserialized or used.
        const text = Buffer.concat(bodyParts.get(budget.state.rpc) || []).toString('utf8');
        const tokens = text.match(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}\[\]:,]|true|false|null/g) || []; let depth = 0, ids = 0;
        for (let i = 0; i < tokens.length; i++) {
          const token = tokens[i];
          if (depth === 1 && token.startsWith('"') && JSON.parse(token) === 'id' && tokens[i + 1] === ':') {
            ids++; if (!/^-?\d/.test(tokens[i + 2]) || Number(tokens[i + 2]) !== budget.state.rpc) fail('RPC_INVALID'); }
          if (token === '{' || token === '[') depth++; if (token === '}' || token === ']') depth--;
        }
        let envelope; try { envelope = JSON.parse(text.replace(/("(?:\\.|[^"\\])*")|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g,
          (match, string, number) => string || `"${number}"`)); } catch { fail('RPC_INVALID'); }
        if (ids !== 1 || envelope.jsonrpc !== '2.0' || !Object.hasOwn(envelope, 'result') || Object.hasOwn(envelope, 'error')) fail('RPC_INVALID');
        if (isAb) {
          const value = envelope.result?.value, accountData = Array.isArray(value?.data) && value.data[1] === 'base64' ? Buffer.from(value.data[0], 'base64') : null;
          entry.contextSlot = envelope.result?.context?.slot ?? null; entry.accountOwner = value?.owner ?? null;
          entry.dataBytes = accountData?.length ?? null; entry.dataHash = accountData ? hash(accountData) : null;
          entry.tokenAccount = tokenAccount(accountData);
        }
        entry.status = 'COLLECTED';
      } finally { entry.attempts = budget.state.rpc - before; }
      snapshot(); current = null;
    }
    stop('MATRIX_COMPLETE');
  } catch (e) { stop(diagnostic(e)); }
  finally {
    terminal = true; clearInterval(tick);
    if (current && !current.attempts) current.status = 'UNEXECUTED';
    const baseline = isAb ? initial : inherited.counters;
    const newRun = Object.fromEntries(Object.keys(isAb ? initial : inherited.counters).filter(k => k !== 'disk' && k !== 'stopped')
      .map(k => [k, (budget.state[k] || 0) - (baseline[k] || 0)]));
    const repeatComparisons = [], parentChildComparisons = [];
    if (isAb) {
      for (const address of addresses) for (const slot of [429644638, 429644639]) {
        const left = matrix.find(x => x.repeat === 1 && x.slot === slot && x.address === address), right = matrix.find(x => x.repeat === 2 && x.slot === slot && x.address === address);
        repeatComparisons.push({ address, slot, result: !left || !right || left.status !== 'COLLECTED' || right.status !== 'COLLECTED'
          ? 'INCOMPLETE' : left.dataHash === null || right.dataHash === null ? 'MISSING_ACCOUNT_DATA'
            : left.dataHash === right.dataHash ? 'MATCH' : 'DIFFERENT' });
      }
      for (const repeat of [1, 2]) for (const address of addresses) {
        const parentCell = matrix.find(x => x.repeat === repeat && x.slot === 429644638 && x.address === address);
        const childCell = matrix.find(x => x.repeat === repeat && x.slot === 429644639 && x.address === address);
        const a = parentCell?.tokenAccount, b = childCell?.tokenAccount;
        parentChildComparisons.push({ repeat, address, result: !a || !b ? 'UNDECODABLE_OR_INCOMPLETE'
          : JSON.stringify([parentCell.accountOwner, a.layout, a.mint, a.authority, a.amount])
            === JSON.stringify([childCell.accountOwner, b.layout, b.mint, b.authority, b.amount]) ? 'MATCH' : 'DIFFERENT' });
      }
    }
    const summary = { mode, outcome: 'INCONCLUSIVE', abrupt: false, reason, startedAt, endedAt: new Date().toISOString(), liveMs: 0,
      inherited, sourceDebit: isAb ? shared.sourceDebit || null : undefined, newRun, budget: { ...budget.state }, matrix,
      repeatComparisons, parentChildComparisons, publishedCU: newRun.rpc * 10, complete: reason === 'MATRIX_COMPLETE',
      rawIncomplete: reason !== 'MATRIX_COMPLETE', scope: 'Raw collection only; slot echo is not historical-state proof' };
    if (journal) { journal.state.summary = summary; journal.state.budget = { ...budget.state }; snapshot(); }
    guard(8192); atomic(path.join(dir, 'summary.json'), summary); budget.persist();
    if (JSON.stringify(stateDebit(debitRoot)) !== JSON.stringify(inherited)) fail('STATE_DEBIT_INVALID');
  }
}
if (require.main === module) process.once('message', options => collect(options).then(() => process.exit(0)).catch(error => {
  if (process.send) process.send({ reason: diagnostic(error) }); process.exit(1);
}));
module.exports = { collect, provenance, base58, finalComparison };
