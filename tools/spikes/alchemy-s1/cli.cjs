'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { fork, execFileSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const { Buffer } = require('node:buffer');
const { diagnostic, fail, config, hash } = require('./core.cjs');
const { Journal, atomic, free, size } = require('./journal.cjs');
const { supervise } = require('./watchdog.cjs');
function canonicalRoot(value, repository) {
  const requested = path.resolve(value), suffix = []; let ancestor = requested;
  while (!fs.existsSync(ancestor)) {
    const parent = path.dirname(ancestor); if (parent === ancestor) fail('UNSAFE_PATH');
    suffix.unshift(path.basename(ancestor)); ancestor = parent;
  }
  const canonical = path.join(fs.realpathSync(ancestor), ...suffix);
  const normalize = input => process.platform === 'win32' ? input.replace(/^\\\\\?\\/, '').toLowerCase() : input;
  const repo = normalize(fs.realpathSync(repository));
  for (const candidate of [requested, canonical]) {
    const normalized = normalize(candidate);
    if (normalized === repo || normalized.startsWith(repo + path.sep) || /(?:^|[\\/])target(?:[\\/]|$)/i.test(candidate)) fail('UNSAFE_PATH');
  }
  return canonical;
}
function evidenceFingerprint(dir) {
  const digest = createHash('sha256'), buffer = Buffer.alloc(65536);
  const visit = relative => {
    for (const entry of fs.readdirSync(path.join(dir, relative), { withFileTypes: true }).sort((a, b) => a.name < b.name ? -1 : a.name > b.name ? 1 : 0)) {
      const name = path.join(relative, entry.name); if (name === 'inspection.json' || name === 'inspection.json.tmp') continue;
      if (entry.isSymbolicLink()) fail('UNSAFE_PATH');
      if (entry.isDirectory()) { visit(name); continue; }
      const file = path.join(dir, name), length = fs.statSync(file).size;
      digest.update(JSON.stringify([name, length])); const fd = fs.openSync(file, 'r');
      try { for (;;) { const count = fs.readSync(fd, buffer, 0, buffer.length, null); if (!count) break; digest.update(buffer.subarray(0, count)); } }
      finally { fs.closeSync(fd); }
    }
  };
  visit(''); return digest.digest('hex');
}
function smokeEvidence(dir, allowUnresolved = false) {
  const journal = new Journal(dir, { resume: true, readOnly: true });
  const summaryFile = path.join(dir, 'summary.json');
  const summary = fs.existsSync(summaryFile) ? JSON.parse(fs.readFileSync(summaryFile)) : null;
  const incomplete = journal.state.incomplete || fs.existsSync(path.join(dir, 'watchdog.json')) || !summary || summary.abrupt !== false
    || (!allowUnresolved && !!journal.state.unresolved?.length) || JSON.stringify(summary) !== JSON.stringify(journal.state.summary);
  return { journal, summary, incomplete: !!incomplete, fingerprint: evidenceFingerprint(dir) };
}
function retryDebit(root) {
  const dir = path.join(root, 'smoke'), evidence = smokeEvidence(dir, true), summary = evidence.summary;
  if (evidence.incomplete || summary.mode !== 'smoke' || summary.outcome !== 'INCONCLUSIVE' || summary.reason !== 'SCHEMA_INVALID') fail('RETRY_INELIGIBLE');
  const ledger = JSON.parse(fs.readFileSync(path.join(root, 'shared-budget.json'))), budget = summary.budget;
  const launch = JSON.parse(fs.readFileSync(path.join(dir, 'launch.json')));
  if (!budget || ledger.activeRun !== 'smoke' || ledger.stopped !== 'SCHEMA_INVALID' || budget.stopped !== 'SCHEMA_INVALID'
    || ledger.smoke?.retryReserved || JSON.stringify(budget) !== JSON.stringify(evidence.journal.state.budget)) fail('RETRY_ACCOUNTING_INVALID');
  for (const field of ['rpc', 'received', 'grpcBytes', 'rpcBytes', 'streamStarts']) {
    if (!Number.isSafeInteger(budget[field]) || budget[field] < 0) fail('RETRY_ACCOUNTING_INVALID');
  }
  if (ledger.rpc !== budget.rpc || ledger.receivedReserved !== budget.received || ledger.grpcBytes !== budget.grpcBytes || ledger.rpcBytes !== budget.rpcBytes
    || budget.received !== budget.grpcBytes + budget.rpcBytes || budget.received < 10967 || budget.rpc !== 2 || budget.streamStarts !== 1) fail('RETRY_ACCOUNTING_INVALID');
  const start = Date.parse(launch.startedAt), end = Date.parse(summary.endedAt), collected = Date.parse(summary.startedAt);
  if (launch.mode !== 'smoke' || !Number.isFinite(start) || !Number.isFinite(end) || !Number.isFinite(collected)
    || collected < start || collected > end || launch.deadline !== start + 300000) fail('RETRY_ACCOUNTING_INVALID');
  const usedMs = Math.max(3451, Math.ceil(end - start));
  if (usedMs >= 300000 || budget.received >= 100000000 || budget.rpc >= 20000) fail('RETRY_BUDGET_EXHAUSTED');
  // Journal.verify already checked all frame boundaries and checksums; now cross-check body accounting.
  const raw = fs.openSync(path.join(dir, 'raw.bin'), 'r'); let offset = 0, grpcBytes = 0, rpcBytes = 0;
  try {
    while (offset < evidence.journal.state.offset) {
      const header = Buffer.alloc(8); fs.readSync(raw, header, 0, 8, offset);
      const length = header.readUInt32BE(0), bytes = header.readUInt32BE(4), metadata = Buffer.alloc(length);
      fs.readSync(raw, metadata, 0, length, offset + 8); const source = JSON.parse(metadata).source;
      if (source === 'grpc') grpcBytes += bytes; else if (source === 'rpc') rpcBytes += bytes; else fail('RETRY_ACCOUNTING_INVALID');
      offset += 8 + length + bytes;
    }
  } finally { fs.closeSync(raw); }
  if (grpcBytes !== budget.grpcBytes || rpcBytes !== budget.rpcBytes) fail('RETRY_ACCOUNTING_INVALID');
  return { ledger, smoke: { usedMs, received: budget.received, grpcBytes, rpcBytes, rpc: budget.rpc, streamStarts: budget.streamStarts, predecessor: evidence.fingerprint } };
}
async function main(args) {
  if (!args.includes('--enable-live') && !args.includes('--inspect')) { console.log('LIVE_DISABLED'); return; }
  if (args.includes('state-probe')) return stateMain(args);
  const value = key => { const i = args.indexOf(key); return i < 0 ? undefined : args[i + 1]; };
  if (['a-live-replay', 'b-history'].includes(value('--mode'))) return providerMain(args);
  const allowed = new Set(['--enable-live', '--mode', '--root', '--secrets-file', '--inspect', '--retry-smoke']);
  for (let i = 0; i < args.length; i++) { if (!allowed.has(args[i])) fail('ARGUMENT_INVALID'); if (!['--enable-live', '--retry-smoke'].includes(args[i])) i++; }
  const mode = value('--inspect') || value('--mode'); if (!['smoke', 'full'].includes(mode)) fail('ARGUMENT_INVALID');
  const retry = args.includes('--retry-smoke'); if (retry && mode !== 'smoke') fail('ARGUMENT_INVALID');
  const repo = path.resolve(__dirname, '../../..');
  const root = canonicalRoot(value('--root') || 'C:/crypto-research-evidence/alchemy-s1', repo);
  fs.mkdirSync(root, { recursive: true });
  if (fs.realpathSync(root).toLowerCase() !== root.toLowerCase()) fail('UNSAFE_PATH');
  const selectedSmoke = fs.existsSync(path.join(root, 'smoke-retry-1')) ? 'smoke-retry-1' : 'smoke';
  if (args.includes('--inspect')) {
    if (retry && selectedSmoke !== 'smoke-retry-1') fail('RETRY_REQUIRED');
    const dir = path.join(root, mode === 'smoke' ? selectedSmoke : mode), evidence = smokeEvidence(dir), { journal } = evidence;
    console.log(JSON.stringify({ mode, incomplete: evidence.incomplete, completeSlot: journal.state.complete,
      unresolved: journal.state.unresolved, budget: journal.state.budget, summary: journal.state.summary || null }));
    // Inspection is local-only. This receipt gates the separate authorized full launch.
    atomic(path.join(dir, 'inspection.json'), { at: new Date().toISOString(), incomplete: evidence.incomplete, fingerprint: evidence.fingerprint }); return;
  }
  if (value('--secrets-file') && path.resolve(value('--secrets-file')).toLowerCase() !== path.join(repo, 'config/application-managed-secrets.properties').toLowerCase()) fail('CONFIG_INVALID');
  if (free(root) < 30000000000 + 64 * 1024 * 1024 || size(root) + 64 * 1024 * 1024 > 10000000000) fail('DISK_PREFLIGHT');
  const lock = path.join(root, 'active.lock'); const fd = fs.openSync(lock, 'wx'); fs.closeSync(fd);
  let dir;
  try {
    dir = path.join(root, retry ? 'smoke-retry-1' : mode); if (fs.existsSync(dir)) fail('PATH_REUSE');
    let debit;
    if (retry) {
      if (fs.existsSync(path.join(root, 'full'))) fail('RETRY_INELIGIBLE');
      debit = retryDebit(root);
    }
    if (mode === 'full') {
      const file = path.join(root, selectedSmoke, 'summary.json'); if (!fs.existsSync(file)) fail('SMOKE_REQUIRED');
      if (!fs.existsSync(path.join(root, selectedSmoke, 'inspection.json'))) fail('SMOKE_INSPECTION_REQUIRED');
      const inspection = JSON.parse(fs.readFileSync(path.join(root, selectedSmoke, 'inspection.json')));
      const evidence = smokeEvidence(path.join(root, selectedSmoke)), smoke = evidence.summary;
      if (inspection.incomplete !== false || evidence.incomplete || inspection.fingerprint !== evidence.fingerprint) fail('SMOKE_FAILED');
      if (smoke.outcome === 'FAIL' || !smoke.stats?.messages || !smoke.stats?.recoveries?.some(r => r.complete) || !['WALL_LIMIT', 'RECEIVED_LIMIT'].includes(smoke.reason)) fail('SMOKE_FAILED');
    }
    fs.mkdirSync(dir); const launchedAt = Date.now(), deadline = launchedAt + (mode === 'smoke' ? 300000 - (debit?.smoke.usedMs || 0) : 36000000);
    if (debit) atomic(path.join(root, 'shared-budget.json'), { ...debit.ledger, activeRun: 'smoke-retry-1',
      smoke: { ...debit.smoke, retryReserved: true, retryDeadline: deadline } });
    atomic(path.join(dir, 'launch.json'), { mode, deadline, startedAt: new Date(launchedAt).toISOString(), predecessor: debit?.smoke.predecessor || null });
    const child = fork(path.join(__dirname, 'collector.cjs'), [], { stdio: ['ignore', 'ignore', 'ignore', 'ipc'], windowsHide: true });
    const watch = supervise(child, { dir, deadline, floor: 30000000000 });
    child.on('message', message => { if (/^[A-Z_]+$/.test(message.reason || '')) console.log(message.reason); });
    child.send({ root, dir, mode, deadline, launchedAt, secretsFile: value('--secrets-file') ? path.resolve(value('--secrets-file')) : null });
    await new Promise(resolve => child.once('exit', resolve));
    if (watch.connected) watch.send({ finished: true });
    console.log(`S1_STOPPED ${mode}`);
  } finally { fs.unlinkSync(lock); }
}
async function providerMain(args) {
  const parsed = new Map();
  for (let i = 0; i < args.length; i++) {
    const key = args[i]; if (!['--enable-live', '--mode', '--root', '--debit-root', '--secrets-file'].includes(key) || parsed.has(key)) fail('ARGUMENT_INVALID');
    if (key === '--enable-live') { parsed.set(key, true); continue; }
    const value = args[++i]; if (!value || value.startsWith('--')) fail('ARGUMENT_INVALID'); parsed.set(key, value);
  }
  const value = key => parsed.get(key);
  const mode = value('--mode'), providerA = mode === 'a-live-replay';
  const expectedRoot = providerA ? 'C:/crypto-research-evidence/provider-a-final-1' : 'C:/crypto-research-evidence/provider-ab-1';
  const expectedDebit = 'C:/crypto-research-evidence/alchemy-s1';
  if (!parsed.has('--enable-live') || !['a-live-replay', 'b-history'].includes(mode)
    || (providerA ? value('--root') !== expectedRoot : value('--root') && path.resolve(value('--root')).toLowerCase() !== path.resolve(expectedRoot).toLowerCase())
    || (providerA && value('--debit-root') && value('--debit-root') !== expectedDebit)
    || (value('--debit-root') && path.resolve(value('--debit-root')).toLowerCase() !== path.resolve(expectedDebit).toLowerCase())) fail('ARGUMENT_INVALID');
  const repo = path.resolve(__dirname, '../../..'), root = canonicalRoot(expectedRoot, repo), debitRoot = canonicalRoot(expectedDebit, repo);
  if (path.dirname(root).toLowerCase() !== path.dirname(debitRoot).toLowerCase()
    || root.toLowerCase() !== path.resolve(expectedRoot).toLowerCase() || debitRoot.toLowerCase() !== path.resolve(expectedDebit).toLowerCase()) fail('UNSAFE_PATH');
  if (providerA) {
    const expectedParent = path.resolve('C:/crypto-research-evidence'), parent = path.dirname(root);
    let parentReal, existingRoot = null;
    try { parentReal = fs.realpathSync(parent); } catch { fail('UNSAFE_PATH'); }
    if (parent.toLowerCase() !== expectedParent.toLowerCase() || parentReal.toLowerCase() !== expectedParent.toLowerCase()) fail('UNSAFE_PATH');
    try { existingRoot = fs.lstatSync(root); } catch (error) { if (error.code !== 'ENOENT') fail('UNSAFE_PATH'); }
    if (existingRoot) fail(existingRoot.isSymbolicLink() ? 'UNSAFE_PATH' : 'PATH_REUSE');
  }
  const secretsFile = value('--secrets-file') ? path.resolve(value('--secrets-file')) : null;
  if (secretsFile && secretsFile.toLowerCase() !== path.join(repo, 'config/application-managed-secrets.properties').toLowerCase()) fail('CONFIG_INVALID');
  config(process.env, secretsFile ? fs.readFileSync(secretsFile, 'utf8') : '');
  const sourceDebit = stateDebit(debitRoot);
  const pids = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    `Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne ${process.pid} -and $_.Name -match '^node(?:\\.exe)?$' -and $_.CommandLine -match '(alchemy-s1[\\\\/](cli|collector|watchdog)\\.cjs|provider-ab-1)' } | ForEach-Object { $_.ProcessId }`],
  { encoding: 'utf8', windowsHide: true, timeout: 10000 }).trim();
  if (pids) fail('STATE_ACTIVE');
  const parent = path.dirname(root);
  const outputLimit = mode === 'a-live-replay' ? 16000000000 : 5000000;
  if (free(parent) - outputLimit - 1048576 < 30000000000) fail('FREE_SPACE_LIMIT');
  if (providerA) fs.mkdirSync(root);
  else if (!fs.existsSync(root)) fs.mkdirSync(root);
  if (fs.realpathSync(root).toLowerCase() !== root.toLowerCase() || fs.lstatSync(root).isSymbolicLink()) fail('UNSAFE_PATH');
  const lock = path.join(root, 'active.lock'); let fd;
  try { fd = fs.openSync(lock, 'wx'); } catch (e) { if (e.code === 'EEXIST') fail('STATE_ACTIVE'); throw e; }
  fs.closeSync(fd);
  try {
    const verifyTree = directory => {
      for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
        const item = path.join(directory, entry.name), stat = fs.lstatSync(item);
        if (stat.isSymbolicLink()) fail('UNSAFE_PATH');
        if (stat.isDirectory()) verifyTree(item); else if (!stat.isFile()) fail('UNSAFE_PATH');
      }
    };
    verifyTree(root);
    const ledgerFile = path.join(root, 'shared-budget.json'), name = mode === 'a-live-replay' ? 'a-live-replay' : 'b-history', dir = path.join(root, name);
    if (fs.existsSync(dir)) fail('PATH_REUSE');
    if (fs.existsSync(ledgerFile)) {
      const ledger = JSON.parse(fs.readFileSync(ledgerFile));
      if (ledger.version !== 1 || JSON.stringify(ledger.sourceDebit) !== JSON.stringify(sourceDebit)
        || !ledger.budget || ledger.activeRun) fail('ACCOUNTING_INVALID');
    } else {
      atomic(ledgerFile, { version: 1, sourceDebit, budget: { received: 0, disk: 0, rpc: 0, stopped: null, grpcBytes: 0,
        rpcBytes: 0, streamStarts: 0, spendPicoUsd: 0 } });
    }
    const ledger = JSON.parse(fs.readFileSync(ledgerFile));
    if ([root, path.join(root, 'shared-budget.json')].some(p => fs.lstatSync(p).isSymbolicLink())) fail('UNSAFE_PATH');
    if (ledger.budget.stopped && !['MATRIX_COMPLETE', 'A_COMPLETE', 'A_INCOMPLETE'].includes(ledger.budget.stopped)) fail('BUDGET_STOP');
    fs.mkdirSync(dir); const launchedAt = Date.now(), deadline = launchedAt + (mode === 'a-live-replay' ? 7200000 : 1800000);
    atomic(path.join(dir, 'launch.json'), { mode, startedAt: new Date(launchedAt).toISOString(), deadline, sourceDebit });
    const child = fork(path.join(__dirname, 'collector.cjs'), [], { stdio: ['ignore', 'ignore', 'ignore', 'ipc'], windowsHide: true });
    const watch = supervise(child, { dir, deadline, floor: 30000000000 }); let childReason;
    child.on('message', message => { if (/^[A-Z_]+$/.test(message.reason || '')) childReason = message.reason; });
    const completion = new Promise(resolve => { child.once('error', () => resolve({ code: 1, signal: null })); child.once('exit', (code, signal) => resolve({ code, signal })); });
    child.send({ root, dir, mode, debitRoot, deadline, launchedAt, secretsFile, sourceDebit }); const result = await completion;
    if (watch.connected) watch.send({ finished: true });
    if (JSON.stringify(stateDebit(debitRoot)) !== JSON.stringify(sourceDebit)) fail('STATE_DEBIT_INVALID');
    if (result.signal || result.code === null) fail(childReason || 'STATE_CHILD_ABRUPT');
    if (result.code !== 0 || childReason) fail(childReason || 'STATE_CHILD_FAILED');
    const completedLedger = JSON.parse(fs.readFileSync(ledgerFile)); completedLedger.activeRun = null; completedLedger.lastMode = mode;
    atomic(ledgerFile, completedLedger);
    console.log(`AB_STOPPED ${mode}`);
  } finally { fs.unlinkSync(lock); }
}
const STATE_SEED = { rpc: 14, received: 75861230, grpcBytes: 47638937, rpcBytes: 28222293, streamStarts: 2 };
function stateDebit(root) {
  try {
    if (fs.existsSync(path.join(root, 'active.lock'))) fail('STATE_ACTIVE');
    const names = ['smoke', 'smoke-retry-1'], fingerprints = {}, prior = { rpc: 0, received: 0, grpcBytes: 0, rpcBytes: 0, streamStarts: 0 };
    for (const name of names) {
      const dir = path.join(root, name);
      if (!fs.existsSync(dir) || !fs.lstatSync(dir).isDirectory()) fail('STATE_DEBIT_INVALID');
      for (const file of ['raw.bin', 'checkpoint.json', 'summary.json']) {
        const required = path.join(dir, file); if (!fs.existsSync(required) || !fs.lstatSync(required).isFile()) fail('STATE_DEBIT_INVALID');
      }
      const e = smokeEvidence(dir, true), s = e.summary, b = s?.budget;
      if (e.incomplete || s.mode !== 'smoke' || s.outcome !== 'INCONCLUSIVE' || s.reason !== (name === 'smoke' ? 'SCHEMA_INVALID' : 'RECEIVED_LIMIT')
        || b.stopped !== s.reason || JSON.stringify(b) !== JSON.stringify(e.journal.state.budget)) fail('STATE_DEBIT_INVALID');
      const totals = { grpc: 0, rpc: 0 }, fd = fs.openSync(e.journal.raw, 'r'); let offset = 0;
      try { while (offset < e.journal.state.offset) {
        const h = Buffer.alloc(8); fs.readSync(fd, h, 0, 8, offset); const n = h.readUInt32BE(0), count = h.readUInt32BE(4), meta = Buffer.alloc(n);
        fs.readSync(fd, meta, 0, n, offset + 8); const source = JSON.parse(meta).source;
        if (!Object.hasOwn(totals, source)) fail('STATE_DEBIT_INVALID'); totals[source] += count; offset += 8 + n + count;
      } } finally { fs.closeSync(fd); }
      for (const field of Object.keys(STATE_SEED)) {
        const legacyFirstSummary = name === 'smoke' && s.attempt === undefined;
        if (!Number.isSafeInteger(b[field]) || b[field] < prior[field]
          || (!legacyFirstSummary && s.attempt?.[field] !== b[field] - prior[field])) fail('STATE_DEBIT_INVALID');
      }
      if (name === 'smoke' && Object.entries({ rpc: 2, received: 10967, grpcBytes: 10881, rpcBytes: 86, streamStarts: 1 }).some(([k, v]) => b[k] !== v)) fail('STATE_DEBIT_INVALID');
      if (totals.grpc !== b.grpcBytes - prior.grpcBytes || totals.rpc !== b.rpcBytes - prior.rpcBytes || b.received !== b.grpcBytes + b.rpcBytes) fail('STATE_DEBIT_INVALID');
      Object.assign(prior, Object.fromEntries(Object.keys(STATE_SEED).map(k => [k, b[k]])));
      fingerprints[name] = { evidence: e.fingerprint, unresolved: e.journal.state.unresolved,
        inspection: fs.existsSync(path.join(dir, 'inspection.json')) ? hash(fs.readFileSync(path.join(dir, 'inspection.json'))) : null };
    }
    const bytes = fs.readFileSync(path.join(root, 'shared-budget.json')), ledger = JSON.parse(bytes);
    if (ledger.activeRun !== 'smoke-retry-1' || ledger.stopped !== 'RECEIVED_LIMIT') fail('STATE_DEBIT_INVALID');
    for (const [key, value] of Object.entries(STATE_SEED)) {
      if (prior[key] !== value || ledger.smoke?.[key] !== value || (key !== 'streamStarts' && ledger[key === 'received' ? 'receivedReserved' : key] !== value)) fail('STATE_DEBIT_INVALID');
    }
    return { counters: { ...STATE_SEED }, fingerprints, ledger: hash(bytes) };
  } catch (e) { if (e.safeCode === 'STATE_ACTIVE') throw e; fail('STATE_DEBIT_INVALID'); }
}
async function stateMain(args) {
  const values = {}, flags = new Set(['--mode', '--root', '--debit-root', '--secrets-file']);
  for (let i = 0; i < args.length; i++) {
    const key = args[i]; if (Object.hasOwn(values, key)) fail('ARGUMENT_INVALID');
    if (key === '--enable-live') values[key] = true;
    else { if (!flags.has(key) || !args[i + 1] || args[i + 1].startsWith('--')) fail('ARGUMENT_INVALID'); values[key] = args[++i]; }
  }
  if (!values['--enable-live'] || values['--mode'] !== 'state-probe' || values['--root'] !== 'C:/crypto-research-evidence/provider-s3-archive-1'
    || values['--debit-root'] !== 'C:/crypto-research-evidence/alchemy-s1') fail('ARGUMENT_INVALID');
  const repo = path.resolve(__dirname, '../../..'), root = canonicalRoot(values['--root'], repo), debitRoot = canonicalRoot(values['--debit-root'], repo);
  if (path.dirname(root) !== path.dirname(debitRoot)) fail('UNSAFE_PATH');
  if (fs.existsSync(root)) fail('PATH_REUSE');
  const secretsFile = values['--secrets-file'] ? path.resolve(values['--secrets-file']) : null;
  if (secretsFile && secretsFile.toLowerCase() !== path.join(repo, 'config/application-managed-secrets.properties').toLowerCase()) fail('CONFIG_INVALID');
  config(process.env, secretsFile ? fs.readFileSync(secretsFile, 'utf8') : '');
  const pids = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    `Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne ${process.pid} -and $_.Name -match '^node(?:\\.exe)?$' -and $_.CommandLine -match '(alchemy-s1[\\\\/](cli|collector|watchdog)\\.cjs|provider-feasibility[\\\\/](cli|probe)\\.cjs)' } | ForEach-Object { $_.ProcessId }`],
  { encoding: 'utf8', windowsHide: true, timeout: 10000 }).trim();
  if (pids) fail('STATE_ACTIVE');
  const inherited = stateDebit(debitRoot), parent = path.dirname(root);
  if (root.toLowerCase() !== path.resolve(values['--root']).toLowerCase() || debitRoot.toLowerCase() !== path.resolve(values['--debit-root']).toLowerCase()) fail('UNSAFE_PATH');
  if (free(parent) - 5065536 < 30000000000) fail('FREE_SPACE_LIMIT');
  if (size(parent) + 5065536 > 10000000000) fail('DISK_LIMIT');
  fs.mkdirSync(root); const lock = path.join(root, 'active.lock'); fs.closeSync(fs.openSync(lock, 'wx'));
  try {
    const dir = path.join(root, 'state-probe'), launchedAt = Date.now(), deadline = launchedAt + 1800000; fs.mkdirSync(dir);
    atomic(path.join(root, 'shared-budget.json'), { inherited, budget: { ...inherited.counters, stopped: null }, rpc: 14, activeRun: 'state-probe' });
    atomic(path.join(dir, 'launch.json'), { mode: 'state-probe', launchedAt, deadline, inherited });
    const child = fork(path.join(__dirname, 'collector.cjs'), [], { stdio: ['ignore', 'ignore', 'ignore', 'ipc'], windowsHide: true });
    let childReason;
    child.on('message', message => { if (message && Object.hasOwn(message, 'reason')) childReason = diagnostic({ safeCode: message.reason }); });
    const completion = new Promise(resolve => {
      child.once('error', () => resolve({ code: 1, signal: null }));
      child.once('exit', (code, signal) => resolve({ code, signal }));
    });
    const watch = supervise(child, { dir, deadline, floor: 30000000000 });
    child.send({ root, dir, mode: 'state-probe', debitRoot, deadline, launchedAt, secretsFile }); const status = await completion;
    if (watch.connected) watch.send({ finished: true });
    if (JSON.stringify(stateDebit(debitRoot)) !== JSON.stringify(inherited)) fail('STATE_DEBIT_INVALID');
    if (status.signal || status.code === null) fail(childReason || 'STATE_CHILD_ABRUPT');
    if (status.code !== 0 || childReason) fail(childReason || 'STATE_CHILD_FAILED');
    console.log('S3_STOPPED state-probe');
  } finally { fs.unlinkSync(lock); }
}
module.exports = { main, stateDebit };
if (require.main === module) main(process.argv.slice(2)).catch(error => { console.error(diagnostic(error)); process.exitCode = 1; });
