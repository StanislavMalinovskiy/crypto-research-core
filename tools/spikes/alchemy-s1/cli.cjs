'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { fork } = require('node:child_process');
const { createHash } = require('node:crypto');
const { Buffer } = require('node:buffer');
const { diagnostic, fail } = require('./core.cjs');
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
  const value = key => { const i = args.indexOf(key); return i < 0 ? undefined : args[i + 1]; };
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
if (require.main === module) main(process.argv.slice(2)).catch(error => { console.error(diagnostic(error)); process.exitCode = 1; });
module.exports = { main };
