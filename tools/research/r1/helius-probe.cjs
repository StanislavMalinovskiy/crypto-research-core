'use strict';
const { parse, canonical, digest, fingerprint, integer } = require('./exploratory-probe.cjs');
const fs = require('node:fs'), path = require('node:path'), https = require('node:https'), http = require('node:http');
const { execFileSync } = require('node:child_process');
const OUTPUT = 'C:\\crypto-research-evidence\\r1-d1\\exploratory-helius-h1-v1';
const FLAGS = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, runAuthorized: false };
const CONFIG = { stage: 'H1', queryVersion: 'helius-history-query-v1', summaryVersion: 'helius-sample-counts-v1', canonicalizationVersion: 'helius-config-c14n-v1',
  chain: 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp', endpoint: 'https://mainnet.helius-rpc.com/', method: 'getTransactionsForAddress',
  address: 'EiYg44SdUBXFJNq1MvPHd71LjMb9b2J5pCSqpjMStRva', range: { from: 1775001600, to: 1782777600 },
  holdout: { from: 1788134400, to: 1790640000 }, limits: { attempts: 1, retries: 0, credits: 10, received: 16000000, response: 16000000,
    disk: 20000000, metadata: 4000000, deadlineMs: 45000, elapsedMs: 600000, concurrency: 1, free: 30000000000 },
  selector: { version: 'helius-feepayer-004-ascii-v1', rawHash: 'sha256:c1595f12107d76dd511df1e12dc4189868d434b73038ce025ff013a02e1b602f',
    manifestHash: 'sha256:fc1fe02982ec49885e7553b2e35331c9f5444e2fca5924327bd0e4d4eed74a4a',
    cohortHash: 'sha256:f82f9e10e90ac35f847221549f200b3a4459ec915405a3d2f04fbb214f503bc5', count: 36, proxy: 'DOCUMENTARY_FEE_PAYER_NOT_TRADER' },
  tariff: { signaturesCredits: 10, actualCredits: 'DASHBOARD_RECONCILIATION_REQUIRED', checkedAt: '2026-10-03',
    documentation: 'https://www.helius.dev/docs/rpc/gettransactionsforaddress', credits: 'https://www.helius.dev/docs/billing/credits',
    plans: 'https://www.helius.dev/docs/billing/plans' }, retention: { status: 'UNVERIFIED', exploratoryDecision: 'e928ceb5-6768-45d9-a454-e26ef87f78ed', days: 14 },
  baseline: { attempts: 99, received: 8280342, retained: 6397448, elapsedMs: 196221, cashMicrousd: '0', v3PublicAttempts: 0 } };
const fail = code => { throw code; }, demand = (value, code = 'RESPONSE_INVALID') => { if (!value) fail(code); };
const fixedError = code => ({ ...FLAGS, status: 'INCOMPLETE', code });
function base58(value, size) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; demand(typeof value === 'string' && value.length > 0 && value.length <= 88);
  let n = 0n; for (const c of value) { const at = alphabet.indexOf(c); demand(at >= 0); n = n * 58n + BigInt(at); }
  let zeros = 0; while (value[zeros] === '1') zeros++; const bytes = n ? Math.ceil(n.toString(16).length / 2) : 0;
  demand(zeros + bytes === size); return value;
}
function query() { return { version: CONFIG.queryVersion, method: 'POST', url: CONFIG.endpoint,
  body: { jsonrpc: '2.0', id: 1, method: CONFIG.method, params: [CONFIG.address, { commitment: 'finalized', transactionDetails: 'signatures', sortOrder: 'asc', limit: 1000,
    filters: { blockTime: { gte: CONFIG.range.from, lt: CONFIG.range.to }, status: 'any', tokenAccounts: 'none' } }] } }; }
const validQuery = q => canonical(q) === canonical(query());
function admit(bytes, q = query()) {
  try {
    demand(validQuery(q)); demand(Buffer.isBuffer(bytes) && bytes.length <= CONFIG.limits.response);
    const response = parse(bytes); demand(response && response.jsonrpc === '2.0' && integer(response.id) === 1);
    if (Object.hasOwn(response, 'error')) fail('RPC_ERROR');
    demand(response.result && typeof response.result === 'object' && !Array.isArray(response.result));
    const data = response.result.data; demand(Array.isArray(data) && data.length <= 1000);
    const unique = new Map();
    for (const row of data) {
      demand(row && typeof row === 'object' && !Array.isArray(row)); const signature = base58(row.signature, 64);
      const slot = integer(row.slot), transactionIndex = integer(row.transactionIndex), blockTime = integer(row.blockTime);
      demand(blockTime >= CONFIG.range.from && blockTime < CONFIG.range.to && row.confirmationStatus === 'finalized');
      const content = canonical(row); if (unique.has(signature)) demand(unique.get(signature).content === content, 'IMMUTABLE_CONFLICT');
      else unique.set(signature, { content, row, slot, transactionIndex, signature });
    }
    const rows = [...unique.values()].sort((a, b) => a.slot - b.slot || a.transactionIndex - b.transactionIndex || (a.signature < b.signature ? -1 : a.signature > b.signature ? 1 : 0));
    const token = response.result.paginationToken;
    demand(token === null || (typeof token === 'string' && token.length <= 40 && /^(0|[1-9][0-9]*):(0|[1-9][0-9]*)$/.test(token)));
    if (token !== null) { const [slot, position] = token.split(':').map(integer), last = rows.at(-1); demand(last && slot === last.slot && position === last.transactionIndex); }
    const statusCounts = { success: 0, failed: 0, unknown: 0 };
    for (const item of rows) { const err = item.row.err; const state = err === null ? 'success' : err && typeof err === 'object' && !Array.isArray(err) ? 'failed' : 'unknown'; statusCounts[state]++; }
    return { code: null, rows: rows.map(item => item.row), statusCounts, paginationToken: token, returnedRows: data.length };
  } catch (code) { return { code: ['RPC_ERROR', 'IMMUTABLE_CONFLICT'].includes(code) ? code : 'RESPONSE_INVALID', rows: [] }; }
}
function selectCohort(raw, expectedRawHash = CONFIG.selector.rawHash, manifest, expectedManifestHash = CONFIG.selector.manifestHash) {
  demand(Buffer.isBuffer(raw) && raw.length <= 2000000 && Buffer.isBuffer(manifest) && manifest.length <= 4000000, 'INPUT_LIMIT');
  demand(digest(raw) === expectedRawHash && digest(manifest) === expectedManifestHash, 'INTEGRITY_ERROR');
  const block = parse(raw); demand(integer(block.header.number) === 410195947 && integer(block.header.timestamp) === 1775001600);
  demand(Array.isArray(block.transactions) && block.transactions.length > 0 && block.transactions.length <= 1000);
  const indexed = block.transactions.map(tx => ({ index: integer(tx.transactionIndex), address: base58(tx.accountKeys?.[0], 32) }));
  indexed.sort((a, b) => a.index - b.index || (a.address < b.address ? -1 : a.address > b.address ? 1 : 0));
  const addresses = [...new Set(indexed.map(x => x.address))].sort();
  return { version: CONFIG.selector.version, addresses, first: indexed[0].address, cohortHash: fingerprint('helius-address-selection-v1', addresses), rawHash: digest(raw), manifestHash: digest(manifest) };
}
function containsSecret(value, secret) {
  if (typeof value === 'string') return value.includes(secret);
  if (value && typeof value === 'object') return Object.entries(value).some(([key, entry]) => key.includes(secret) || containsSecret(entry, secret));
  return false;
}
function readSecret() {
  const file = path.resolve(__dirname, '../../../config/application-managed-secrets.properties'); let fd;
  try {
    demand(!fs.lstatSync(file).isSymbolicLink(), 'SECRET_UNAVAILABLE'); fd = fs.openSync(file, 'r'); const stat = fs.fstatSync(fd);
    demand(stat.isFile() && stat.size <= 1000000, 'SECRET_UNAVAILABLE'); const bytes = Buffer.alloc(1000001); let n = 0;
    while (n < bytes.length) { const count = fs.readSync(fd, bytes, n, bytes.length - n, n); if (!count) break; n += count; }
    demand(n <= 1000000, 'SECRET_UNAVAILABLE'); const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, n));
    const matches = text.split(/\r?\n/).filter(line => /^\s*CRYPTO_HELIUS_API_KEY\s*[=:]/.test(line)); demand(matches.length === 1, 'SECRET_UNAVAILABLE');
    const key = matches[0].replace(/^\s*CRYPTO_HELIUS_API_KEY\s*[=:]\s*/, '').trim(); demand(/^[A-Za-z0-9_-]{8,256}$/.test(key), 'SECRET_UNAVAILABLE'); return key;
  } catch { fail('SECRET_UNAVAILABLE'); } finally { if (fd !== undefined) fs.closeSync(fd); }
}
async function send(q, options = {}) {
  let secret, target;
  try { demand(validQuery(q), 'QUERY_INVALID'); if (options.loopback) { target = new URL(options.loopback); demand(target.protocol === 'http:' && target.hostname === '127.0.0.1' && !target.username && !target.password && !target.search && !target.hash, 'QUERY_INVALID'); }
    else target = new URL(CONFIG.endpoint); secret = (options.secretLoader ?? readSecret)(); demand(typeof secret === 'string' && secret.length >= 8 && secret.length <= 256, 'SECRET_UNAVAILABLE');
  } catch (code) { return { code: code === 'QUERY_INVALID' ? code : 'SECRET_UNAVAILABLE', received: 0, attempted: false }; }
  const limit = Math.min(options.responseLimit ?? CONFIG.limits.response, CONFIG.limits.response), deadline = Math.min(options.deadlineMs ?? CONFIG.limits.deadlineMs, CONFIG.limits.deadlineMs);
  if (!Number.isSafeInteger(limit) || limit < 1 || !Number.isSafeInteger(deadline) || deadline < 1) return { code: 'QUERY_INVALID', received: 0, attempted: false };
  return new Promise(resolve => {
    let request, response, timer, ended = false, received = 0, status = null; const chunks = [];
    const finish = (code, bytes) => { if (ended) return; ended = true; clearTimeout(timer); if (code) { chunks.length = 0; response?.destroy(); request?.destroy(); }
      resolve({ code, status, received, attempted: true, ...(code ? {} : { bytes }) }); };
    try {
      target.searchParams.set('api-key', secret); const client = options.loopback ? http : https;
      request = client.request(target, { method: 'POST', headers: { 'Content-Type': 'application/json' }, agent: false }, res => {
        response = res; status = res.statusCode;
        res.on('readable', () => { let chunk; while (!ended && (chunk = res.read(Math.min(65536, limit + 1 - received))) !== null) {
          received += chunk.length; try { options.onChunk?.(chunk.length); } catch { return finish('RECEIVED_LIMIT'); }
          if (received > limit) return finish('RESPONSE_LIMIT'); if (status === 200) chunks.push(chunk);
        } });
        res.on('aborted', () => finish('PARTIAL_RESPONSE')); res.on('error', () => finish('PARTIAL_RESPONSE'));
        res.on('end', () => { if (status !== 200) return finish('HTTP_ERROR'); const bytes = Buffer.concat(chunks);
          try { if (bytes.includes(Buffer.from(secret)) || containsSecret(parse(bytes), secret)) return finish('SECRET_EXPOSURE'); } catch { /* Admission owns malformed successful JSON. */ }
          finish(null, bytes); });
      });
      request.on('error', () => finish('NETWORK_ERROR')); timer = setTimeout(() => finish('TIMEOUT'), deadline); request.end(JSON.stringify(q.body));
    } catch { finish('NETWORK_ERROR'); }
  });
}
function fileStore(root = OUTPUT) {
  const full = path.resolve(root), expected = path.resolve(OUTPUT); if (full !== expected || root !== OUTPUT) fail('UNSAFE_PATH');
  const parent = path.dirname(full);
  const ancestors = () => { let current = parent; while (true) { const stat = fs.lstatSync(current); demand(stat.isDirectory() && !stat.isSymbolicLink(), 'UNSAFE_PATH');
    demand(fs.realpathSync.native(current).toLowerCase() === current.toLowerCase(), 'UNSAFE_PATH'); const up = path.dirname(current); if (up === current) break; current = up; } };
  const nameGuard = name => { demand(typeof name === 'string' && /^(manifest\.json|summary\.json|attempt\.json|000\.raw)$/.test(name), 'INTEGRITY_ERROR'); return path.join(full, name); };
  const checkRoot = () => { ancestors(); const stat = fs.lstatSync(full); demand(stat.isDirectory() && !stat.isSymbolicLink() && fs.realpathSync.native(full).toLowerCase() === full.toLowerCase(), 'UNSAFE_PATH'); };
  return { free() { ancestors(); const stat = fs.statfsSync(parent, { bigint: true }); const value = stat.bavail * stat.bsize; demand(value <= BigInt(Number.MAX_SAFE_INTEGER), 'DISK_LIMIT'); return Number(value); },
    create() { ancestors(); try { fs.mkdirSync(full); } catch (error) { fail(error.code === 'EEXIST' ? 'OUTPUT_EXISTS' : 'STORAGE_ERROR'); } checkRoot(); },
    write(name, bytes) { checkRoot(); const file = nameGuard(name); let fd; try { fd = fs.openSync(file, 'wx'); fs.writeFileSync(fd, bytes); fs.fsyncSync(fd); }
      catch { fail('STORAGE_ERROR'); } finally { if (fd !== undefined) fs.closeSync(fd); } },
    read(name) { checkRoot(); const file = nameGuard(name); demand(!fs.lstatSync(file).isSymbolicLink(), 'INTEGRITY_ERROR'); let fd; try { fd = fs.openSync(file, 'r'); const stat = fs.fstatSync(fd);
      const limit = name.endsWith('.raw') ? CONFIG.limits.response : CONFIG.limits.metadata; demand(stat.isFile() && stat.size <= limit, 'INTEGRITY_ERROR');
      const buffer = Buffer.alloc(limit + 1); let n = 0; while (n < buffer.length) { const count = fs.readSync(fd, buffer, n, buffer.length - n, n); if (!count) break; n += count; }
      demand(n <= limit, 'INTEGRITY_ERROR'); return buffer.subarray(0, n); } finally { if (fd !== undefined) fs.closeSync(fd); } },
    list() { checkRoot(); return fs.readdirSync(full); }, size(name) { checkRoot(); const file = nameGuard(name); const stat = fs.lstatSync(file); demand(stat.isFile() && !stat.isSymbolicLink(), 'INTEGRITY_ERROR'); return stat.size; } };
}
const SCAN_ROOTS = [
  ...['exploratory-sqd-v1', 'exploratory-sqd-v2', 'exploratory-helius-h1-v1', 'exploratory-helius-v1'].map(name => 'C:\\crypto-research-evidence\\r1-d1\\' + name),
  ...['r1-d1-exploratory-preflight-20261003', 'r1-d1-exploratory-v2-preflight-20261003', 'r1-d1-exploratory-v3-preflight-20261003',
    'r1-d1-v3-stop-preflight-20261003', 'r1-d1-offline-batch-gate-20261003', 'r1-d1-helius-h1-gate-20261003', 'r1-d1-helius-h3-gate-20261003']
    .map(name => 'C:\\crypto-research-evidence\\' + name),
  'C:\\Users\\stasm\\AppData\\Local\\Temp\\d1-batch-commits-846b3fdd320648e8a3af758ec8ed37ff'];
function scanMetadata(io = { fs, now: Date.now }) {
  const started = io.now(), files = new Set(), roots = []; let entries = 0, total = 0;
  const check = () => demand(++entries <= 10000 && io.now() - started <= 15000, 'PREFLIGHT_LIMIT');
  const inspect = (file, boundary, depth) => {
    check(); demand(depth <= 8 && (file === boundary || file.startsWith(boundary + '\\')), 'PREFLIGHT_LIMIT');
    const stat = io.fs.lstatSync(file); demand(!stat.isSymbolicLink(), 'PREFLIGHT_ERROR');
    const real = io.fs.realpathSync.native(file); demand(real.toLowerCase() === file.toLowerCase(), 'PREFLIGHT_ERROR');
    if (stat.isDirectory()) {
      const names = io.fs.readdirSync(file); demand(names.length <= 10000 - entries, 'PREFLIGHT_LIMIT');
      let size = 0; for (const name of names.sort()) { demand(typeof name === 'string' && name !== '.' && name !== '..' && !/[\\/:]/.test(name), 'PREFLIGHT_ERROR'); size += inspect(path.win32.join(file, name), boundary, depth + 1); demand(Number.isSafeInteger(size), 'PREFLIGHT_LIMIT'); } return size;
    }
    demand(stat.isFile() && Number.isSafeInteger(stat.size) && stat.size >= 0, 'PREFLIGHT_ERROR');
    const key = real.toLowerCase(); if (files.has(key)) return 0; files.add(key); total += stat.size; demand(Number.isSafeInteger(total) && total <= 10000000000, 'DISK_LIMIT'); return stat.size;
  };
  try {
    for (const root of SCAN_ROOTS) {
      const canonicalRoot = path.win32.resolve(root); demand(canonicalRoot === root, 'PREFLIGHT_ERROR');
      let present; try { io.fs.lstatSync(root); present = true; } catch (error) { if (error.code === 'ENOENT') present = false; else throw error; }
      if (!present) { check(); roots.push({ root, status: 'ABSENT', bytes: 0 }); }
      else roots.push({ root, status: 'PRESENT', bytes: inspect(root, root, 0) });
    }
    return { retainedBytes: total, roots, entries, observedAt: new Date(io.now()).toISOString(), scope: 'FIXED_WHITELIST_STAT_ONLY_MAIN_VERIFICATION_REQUIRED' };
  } catch (code) { fail(['PREFLIGHT_LIMIT', 'DISK_LIMIT'].includes(code) ? code : 'PREFLIGHT_ERROR'); }
}
function sourceIdentity() {
  try { const cwd = path.resolve(__dirname, '../../..'); return { commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd, encoding: 'utf8', timeout: 5000 }).trim(),
    dirty: execFileSync('git', ['status', '--porcelain'], { cwd, encoding: 'utf8', timeout: 5000 }).trim().length > 0 }; } catch { fail('SOURCE_IDENTITY_INVALID'); }
}
function provenance(deps) {
  const source = deps.source ?? sourceIdentity(); demand(source && /^[a-f0-9]{40}$/.test(source.commit) && typeof source.dirty === 'boolean', 'SOURCE_IDENTITY_INVALID');
  const runtime = deps.runtime ?? process.version; demand(/^v24\./.test(runtime), 'RUNTIME_INVALID');
  const scripts = Object.fromEntries(['helius-probe.cjs', 'helius-probe-cli.cjs', 'exploratory-probe.cjs'].map(name => [name, digest(fs.readFileSync(path.join(__dirname, name)))]));
  return { source, runtime, scripts };
}
function cumulative(preflight, counters) {
  const retained = preflight.retainedBytes ?? CONFIG.baseline.retained + preflight.checkpointMetadataBytes;
  demand(Number.isSafeInteger(retained) && retained >= CONFIG.baseline.retained && retained <= 10000000000, 'PREFLIGHT_ERROR');
  const pre = { ...CONFIG.baseline, retained }, post = { ...pre, attempts: pre.attempts + counters.attempts, received: pre.received + counters.received,
    retained: pre.retained + counters.retained, elapsedMs: pre.elapsedMs + counters.elapsedMs, creditsReserved: counters.creditsReserved, actualCredits: null };
  return { pre, step: { ...counters }, post };
}
function summaryFor(code, admitted, received, raw, lineage) {
  return { ...FLAGS, version: CONFIG.summaryVersion, stage: 'H1', status: code ? 'INCOMPLETE' : 'COMPLETE', code,
    rows: admitted?.rows.length ?? 0, returnedRows: admitted?.returnedRows ?? 0, statusCounts: admitted?.statusCounts ?? { success: 0, failed: 0, unknown: 0 },
    paginationToken: admitted?.paginationToken ?? null, historyComplete: !code && admitted?.paginationToken === null, paginationObservedOnly: true,
    estimatedCredits: code ? null : 10, actualCredits: null, creditsReserved: 10, attempts: 1, received, retainedRawBytes: raw?.size ?? 0,
    rawHash: raw?.sha256 ?? null, configHash: fingerprint(CONFIG.canonicalizationVersion, CONFIG), lineage,
    cohort: CONFIG.selector, address: CONFIG.address, rangesUnmeasured: ['validation1', 'validation2', 'embargo', 'holdout', 'tail'], fullD1UpperBound: null };
}
async function runProbe(options = {}, deps = {}) {
  if (!options.enabled) return fixedError('DISABLED');
  if (Object.keys(options).some(key => !['enabled', 'stage', 'creditsRemaining', 'output'].includes(key)) || options.enabled !== true || options.stage !== 'H1' || options.output !== OUTPUT ||
    typeof options.creditsRemaining !== 'string' || !/^(0|[1-9][0-9]{0,6})$/.test(options.creditsRemaining) || BigInt(options.creditsRemaining) > 1000000n) return fixedError('ARGUMENTS_INVALID');
  if (BigInt(options.creditsRemaining) < 10n) return fixedError('CREDIT_LIMIT');
  const now = deps.now ?? Date.now, began = now(); let store, preflight, lineage;
  try {
    lineage = provenance(deps); preflight = deps.preflight ?? scanMetadata(); const baseline = cumulative(preflight, { attempts: 0, received: 0, retained: 0, elapsedMs: 0, creditsReserved: 0 });
    demand(baseline.pre.retained + CONFIG.limits.disk <= 10000000000, 'DISK_LIMIT');
    if (preflight.roots) demand(preflight.roots.find(x => x.root === OUTPUT)?.status === 'ABSENT', 'OUTPUT_EXISTS');
    store = deps.store ?? fileStore(); demand(store.free() - CONFIG.limits.disk >= CONFIG.limits.free, 'FREE_SPACE_LIMIT');
    demand(now() - began + CONFIG.limits.deadlineMs <= CONFIG.limits.elapsedMs, 'TIME_LIMIT'); store.create();
  } catch (error) { const code = typeof error === 'string' ? error : error?.message;
    return fixedError(['SOURCE_IDENTITY_INVALID', 'RUNTIME_INVALID', 'PREFLIGHT_ERROR', 'PREFLIGHT_LIMIT', 'DISK_LIMIT', 'OUTPUT_EXISTS', 'FREE_SPACE_LIMIT', 'TIME_LIMIT', 'UNSAFE_PATH'].includes(code) ? code : 'STORAGE_ERROR'); }
  let received = 0, retained = 0, response, admitted, raw = null, code = null, storageFailed = false, transportInvoked = false;
  const files = [], partialFiles = [];
  const size = name => { try { return store.size ? store.size(name) : store.read(name).length; } catch { return 0; } };
  const write = (name, bytes) => { try { store.write(name, bytes); retained += bytes.length; files.push({ name, size: bytes.length, sha256: digest(bytes) }); }
    catch { const partial = size(name); retained += partial; if (partial) partialFiles.push({ name, size: partial }); storageFailed = true; fail('STORAGE_ERROR'); } };
  try {
    write('attempt.json', Buffer.from(JSON.stringify({ stage: 'H1', query: query(), creditsReserved: 10 }) + '\n'));
    const transport = deps.transport ?? send;
    transportInvoked = true;
    response = await transport(query(), { onChunk: n => { demand(Number.isSafeInteger(n) && n >= 0, 'RECEIVED_LIMIT'); received += n; demand(received <= CONFIG.limits.received, 'RECEIVED_LIMIT'); } });
    code = response.code ?? null;
    demand(Number.isSafeInteger(response.received) && response.received === received, 'RESPONSE_INVALID');
    if (!code) { demand(response.status === 200 && Buffer.isBuffer(response.bytes) && response.bytes.length === received, 'RESPONSE_INVALID');
      admitted = admit(response.bytes); code = admitted.code;
      if (!code) { demand(now() - began <= CONFIG.limits.elapsedMs && retained + response.bytes.length + CONFIG.limits.metadata <= CONFIG.limits.disk, 'TIME_LIMIT');
        write('000.raw', response.bytes); raw = files.at(-1); } else admitted = null;
    }
  } catch (error) { code = ['STORAGE_ERROR', 'RECEIVED_LIMIT', 'RESPONSE_INVALID', 'TIME_LIMIT'].includes(error) ? error : 'TRANSPORT_ERROR'; admitted = null; }
  if (now() - began > CONFIG.limits.elapsedMs) code = 'TIME_LIMIT';
  code = code === null || ['HTTP_ERROR', 'NETWORK_ERROR', 'TIMEOUT', 'PARTIAL_RESPONSE', 'RESPONSE_LIMIT', 'RECEIVED_LIMIT', 'RESPONSE_INVALID', 'RPC_ERROR',
    'IMMUTABLE_CONFLICT', 'SECRET_EXPOSURE', 'SECRET_UNAVAILABLE', 'STORAGE_ERROR', 'TIME_LIMIT', 'TRANSPORT_ERROR'].includes(code) ? code : 'TRANSPORT_ERROR';
  const attempted = transportInvoked && response?.attempted !== false, counters = { attempts: attempted ? 1 : 0, received, retained: 0, elapsedMs: now() - began, creditsReserved: 10, actualCredits: null, cashMicrousd: '0', concurrency: 1 };
  const summary = summaryFor(code, admitted, received, raw, lineage); summary.attempts = counters.attempts;
  const summaryBytes = Buffer.from(JSON.stringify(summary) + '\n'), summaryHash = digest(summaryBytes);
  const utcEnd = (deps.utcNow ?? Date.now)(), endedAt = new Date(utcEnd).toISOString(), expiry = new Date(utcEnd + 14 * 86400000).toISOString();
  const manifest = { version: 'helius-h1-manifest-v1', ...FLAGS, configuration: CONFIG, query: query(), lineage, preflight,
    freeQuotaEvidence: { type: 'OWNER_PASTED_TEXT', date: '2026-10-03', remainingSupplied: options.creditsRemaining, resetsInDays: 17, exactPasteTime: null,
      directDashboardAccess: false, paidAutoscalingVerifiedAbsent: false, decision: 'f038a7d6-5332-4b66-bd2f-99b3081d7e35' },
    records: [{ ordinal: 0, attempted, code, status: response?.status ?? null, received, creditsReserved: 10, raw }], files, partialFiles,
    summaryHash, accounting: null, operational: { startedAt: new Date(utcEnd - counters.elapsedMs).toISOString(), endedAt, elapsedMs: counters.elapsedMs, rawExpiresAt: expiry, actualDashboardDebit: null }, checkpoints: [] };
  let manifestStored = false;
  try {
    write('summary.json', summaryBytes);
    const refresh = manifestSize => { counters.retained = retained + manifestSize; const accounting = { ...counters, cumulative: cumulative(preflight, counters) }; manifest.accounting = accounting;
      manifest.checkpoints = ['attempts', 'credits', ...(received >= 12800000 ? ['received'] : []), ...(accounting.retained >= 16000000 ? ['disk'] : []), ...(counters.elapsedMs >= 480000 ? ['elapsed'] : [])]; };
    let manifestBytes = Buffer.alloc(0); for (let i = 0; i < 8; i++) { refresh(manifestBytes.length); manifestBytes = Buffer.from(JSON.stringify(manifest) + '\n'); }
    demand(manifestBytes.length + summaryBytes.length <= CONFIG.limits.metadata && retained + manifestBytes.length <= CONFIG.limits.disk, 'STORAGE_ERROR');
    // Manifest is self-sized, but not self-hashed or listed in its own immutable file list.
    store.write('manifest.json', manifestBytes); manifestStored = true; retained += manifestBytes.length; counters.retained = retained;
    demand(manifest.accounting.retained === retained, 'STORAGE_ERROR');
  } catch { storageFailed = true; code = 'STORAGE_ERROR'; counters.retained = retained + (manifestStored ? 0 : size('manifest.json')); }
  const accounting = { ...counters, cumulative: cumulative(preflight, counters) };
  return { ...FLAGS, status: code ? 'INCOMPLETE' : 'COMPLETE', code, summary, summaryHash: storageFailed ? null : summaryHash, accounting };
}
async function replay(store = fileStore(), options = {}) {
  try {
    const manifestBytes = store.read('manifest.json'), manifest = parse(manifestBytes, false);
    demand(manifest.version === 'helius-h1-manifest-v1' && canonical(manifest.configuration) === canonical(CONFIG) && validQuery(manifest.query), 'INTEGRITY_ERROR');
    demand(manifest.lineage && /^[a-f0-9]{40}$/.test(manifest.lineage.source?.commit) && typeof manifest.lineage.source.dirty === 'boolean' && /^v24\./.test(manifest.lineage.runtime), 'INTEGRITY_ERROR');
    demand(Object.keys(manifest.lineage.scripts).length === 3 && ['helius-probe.cjs', 'helius-probe-cli.cjs', 'exploratory-probe.cjs'].every(name => /^sha256:[a-f0-9]{64}$/.test(manifest.lineage.scripts[name])), 'INTEGRITY_ERROR');
    const expiry = Date.parse(manifest.operational.rawExpiresAt), end = Date.parse(manifest.operational.endedAt);
    demand(Number.isFinite(expiry) && Number.isFinite(end) && expiry - end === 14 * 86400000, 'INTEGRITY_ERROR');
    if ((options.utcNow ?? Date.now)() >= expiry) return fixedError('REPLAY_EXPIRED');
    demand(Array.isArray(manifest.records) && manifest.records.length === 1 && manifest.records[0].ordinal === 0 && manifest.partialFiles.length === 0, 'INTEGRITY_ERROR');
    const record = manifest.records[0], files = manifest.files; demand(Array.isArray(files) && files.length >= 2 && files.length <= 3, 'INTEGRITY_ERROR');
    demand(Number.isSafeInteger(record.received) && record.received >= 0 && record.received <= CONFIG.limits.received + 1 && record.creditsReserved === 10 && typeof record.attempted === 'boolean', 'INTEGRITY_ERROR');
    const allowed = new Set(['manifest.json']), loaded = new Map(); let retained = manifestBytes.length;
    for (const file of files) { demand(['attempt.json', 'summary.json', '000.raw'].includes(file.name) && !allowed.has(file.name), 'INTEGRITY_ERROR'); allowed.add(file.name);
      const bytes = store.read(file.name); demand(bytes.length === file.size && digest(bytes) === file.sha256, 'INTEGRITY_ERROR'); loaded.set(file.name, bytes); retained += bytes.length; }
    demand(store.list().every(name => allowed.has(name)) && loaded.has('attempt.json') && loaded.has('summary.json'), 'INTEGRITY_ERROR');
    const attempt = parse(loaded.get('attempt.json'), false); demand(canonical(attempt) === canonical({ stage: 'H1', query: query(), creditsReserved: 10 }), 'INTEGRITY_ERROR');
    let admitted = null, raw = null;
    if (record.code === null) { demand(record.status === 200 && record.attempted && loaded.has('000.raw'), 'INTEGRITY_ERROR'); const bytes = loaded.get('000.raw');
      demand(record.received === bytes.length && record.raw?.name === '000.raw', 'INTEGRITY_ERROR'); admitted = admit(bytes); demand(admitted.code === null, 'INTEGRITY_ERROR');
      raw = files.find(file => file.name === '000.raw'); demand(canonical(raw) === canonical(record.raw), 'INTEGRITY_ERROR'); }
    else demand(!loaded.has('000.raw') && record.raw === null, 'INTEGRITY_ERROR');
    const summary = summaryFor(record.code, admitted, record.received, raw, manifest.lineage); summary.attempts = record.attempted ? 1 : 0;
    const bytes = Buffer.from(JSON.stringify(summary) + '\n'); demand(digest(bytes) === manifest.summaryHash && digest(loaded.get('summary.json')) === manifest.summaryHash, 'INTEGRITY_ERROR');
    const a = manifest.accounting, op = manifest.operational;
    const immutableRawBytes = loaded.get('000.raw')?.length ?? 0;
    demand(Number.isSafeInteger(op.elapsedMs) && op.elapsedMs >= 0 && op.elapsedMs <= CONFIG.limits.elapsedMs && Number.isFinite(Date.parse(op.startedAt)) && Number.isFinite(Date.parse(op.endedAt)), 'INTEGRITY_ERROR');
    demand(a.attempts === summary.attempts && a.received === record.received && Number.isSafeInteger(a.retained) && a.retained >= retained - manifestBytes.length && a.retained <= CONFIG.limits.disk &&
      Number.isSafeInteger(a.elapsedMs) && a.elapsedMs >= 0 && a.elapsedMs <= CONFIG.limits.elapsedMs && a.creditsReserved === 10 && a.actualCredits === null && a.cashMicrousd === '0' && a.concurrency === 1,
      'INTEGRITY_ERROR'); demand(retained <= CONFIG.limits.disk && a.retained - immutableRawBytes <= CONFIG.limits.metadata &&
        retained - immutableRawBytes <= CONFIG.limits.metadata, 'INTEGRITY_ERROR');
    const counters = { attempts: a.attempts, received: a.received, retained: a.retained, elapsedMs: a.elapsedMs, creditsReserved: a.creditsReserved, actualCredits: null, cashMicrousd: '0', concurrency: 1 };
    demand(canonical(cumulative(manifest.preflight, counters)) === canonical(a.cumulative), 'INTEGRITY_ERROR');
    demand(a.cumulative.post.retained <= 10000000000 && a.cumulative.pre.retained + retained <= 10000000000, 'INTEGRITY_ERROR');
    return { ...FLAGS, status: summary.status, code: null, summary, summaryHash: manifest.summaryHash, accounting: a };
  } catch { return fixedError('INTEGRITY_ERROR'); }
}
module.exports = { OUTPUT, CONFIG, query, admit, runProbe, selectCohort, replay, send, fileStore, scanMetadata, SCAN_ROOTS };
