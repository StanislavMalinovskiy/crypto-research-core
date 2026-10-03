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
  const fullMode = validQuery3(q);
  try { demand(validQuery(q) || fullMode, 'QUERY_INVALID'); if (options.loopback) { target = new URL(options.loopback); demand(target.protocol === 'http:' && target.hostname === '127.0.0.1' && !target.username && !target.password && !target.search && !target.hash, 'QUERY_INVALID'); }
    else target = new URL(CONFIG.endpoint); secret = (options.secretLoader ?? readSecret)(); demand(typeof secret === 'string' && secret.length >= 8 && secret.length <= 256, 'SECRET_UNAVAILABLE');
  } catch (code) { return { code: code === 'QUERY_INVALID' ? code : 'SECRET_UNAVAILABLE', received: 0, attempted: false }; }
  const caps = fullMode ? CONFIG3.limits : CONFIG.limits;
  const limit = Math.min(options.responseLimit ?? caps.response, caps.response), deadline = Math.min(options.deadlineMs ?? caps.deadlineMs, caps.deadlineMs);
  if (!Number.isSafeInteger(limit) || limit < 1 || !Number.isSafeInteger(deadline) || deadline < 1) return { code: 'QUERY_INVALID', received: 0, attempted: false };
  return new Promise(resolve => {
    let request, response, timer, ended = false, received = 0, status = null, retryAfter, retryAfterInvalid = false; const chunks = [];
    const finish = (code, bytes) => { if (ended) return; ended = true; clearTimeout(timer); if (code) { chunks.length = 0; response?.destroy(); request?.destroy(); }
      resolve({ code, status, received, attempted: true, ...(fullMode ? { ownedDeadline: code === 'TIMEOUT', retryAfter: retryAfter ?? null, retryAfterInvalid } : {}), ...(code ? {} : { bytes }) }); };
    try {
      target.searchParams.set('api-key', secret); const client = options.loopback ? http : https;
      request = client.request(target, { method: 'POST', headers: { 'Content-Type': 'application/json' }, agent: false }, res => {
        response = res; status = res.statusCode;
        if (fullMode) { const values = []; for (let n = 0; n < res.rawHeaders.length; n += 2) if (res.rawHeaders[n].toLowerCase() === 'retry-after') values.push(res.rawHeaders[n + 1]);
          retryAfterInvalid = values.length > 1 || values.some(v => !/^[0-9]{1,128}$/.test(v) || v.includes(secret)); if (values.length === 1 && !retryAfterInvalid) retryAfter = values[0]; }
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
  const fullMode = root === OUTPUT3, full = path.resolve(root), expected = path.resolve(fullMode ? OUTPUT3 : OUTPUT); if (full !== expected || ![OUTPUT, OUTPUT3].includes(root)) fail('UNSAFE_PATH');
  const parent = path.dirname(full);
  const ancestors = () => { let current = parent; while (true) { const stat = fs.lstatSync(current); demand(stat.isDirectory() && !stat.isSymbolicLink(), 'UNSAFE_PATH');
    demand(fs.realpathSync.native(current).toLowerCase() === current.toLowerCase(), 'UNSAFE_PATH'); const up = path.dirname(current); if (up === current) break; current = up; } };
  const nameGuard = name => { demand(typeof name === 'string' && (fullMode ? /^(manifest\.json|summary\.json|attempt\.json|0[0-9]{3}\.raw)$/ : /^(manifest\.json|summary\.json|attempt\.json|000\.raw)$/).test(name), 'INTEGRITY_ERROR'); return path.join(full, name); };
  const checkRoot = () => { ancestors(); const stat = fs.lstatSync(full); demand(stat.isDirectory() && !stat.isSymbolicLink() && fs.realpathSync.native(full).toLowerCase() === full.toLowerCase(), 'UNSAFE_PATH'); };
  return { free() { ancestors(); const stat = fs.statfsSync(parent, { bigint: true }); const value = stat.bavail * stat.bsize; demand(value <= BigInt(Number.MAX_SAFE_INTEGER), 'DISK_LIMIT'); return Number(value); },
    create() { ancestors(); try { fs.mkdirSync(full); } catch (error) { fail(error.code === 'EEXIST' ? 'OUTPUT_EXISTS' : 'STORAGE_ERROR'); } checkRoot(); },
    write(name, bytes) { checkRoot(); const file = nameGuard(name); let fd; try { fd = fs.openSync(file, 'wx'); fs.writeFileSync(fd, bytes); fs.fsyncSync(fd); }
      catch { fail('STORAGE_ERROR'); } finally { if (fd !== undefined) fs.closeSync(fd); } },
    read(name) { checkRoot(); const file = nameGuard(name); demand(!fs.lstatSync(file).isSymbolicLink(), 'INTEGRITY_ERROR'); let fd; try { fd = fs.openSync(file, 'r'); const stat = fs.fstatSync(fd);
      const caps = fullMode ? CONFIG3.limits : CONFIG.limits;
      const limit = name.endsWith('.raw') ? caps.response : caps.metadata; demand(stat.isFile() && stat.size <= limit, 'INTEGRITY_ERROR');
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
const OUTPUT3 = 'C:\\crypto-research-evidence\\r1-d1\\exploratory-helius-v1';
const CONFIG3 = { stage: 'H3', queryVersion: 'helius-full-history-query-v1', summaryVersion: 'helius-full-sample-counts-v1', canonicalizationVersion: 'helius-full-config-c14n-v1',
  chain: CONFIG.chain, endpoint: CONFIG.endpoint, method: CONFIG.method, selector: CONFIG.selector, holdout: CONFIG.holdout, tariff: CONFIG.tariff, retention: CONFIG.retention,
  ranges: [{ id: 'warmup', from: 1775001600, to: 1782777600 }, { id: 'validation1', from: 1782777600, to: 1785369600 }, { id: 'validation2', from: 1785369600, to: 1787961600 }],
  limits: { attempts: 1000, retries: 3, globalRetries: 150, credits: 100000, reservation: 100, received: 4000000000, disk: 4100000000,
    metadata: 100000000, response: 64000000, deadlineMs: 60000, elapsedMs: 7200000, concurrency: 1, spacingMs: 250, free: 30000000000, pagesPerStream: 250 },
  timing: { criterion: 'PRE_PUBLICATION_WITH_INDEPENDENT_MAIN_BUDGET', sourceCutoffMs: 6900000, publicationReserveMs: 300000, nominalBudgetMs: 7200000 },
  backoffMs: [5000, 15000, 45000], datasetRevision: null, output: OUTPUT3,
  h1: { manifestHash: 'sha256:5cc326892ab985d09d2b7f287beaa28dff41ac7ae8a4ab00f3eab81033671910',
    rawHash: 'sha256:847e3e6d1505bdb9ebb2ef559aaa01b1993e2c71117debeac97ad071165ed490',
    summaryHash: 'sha256:33cdc9ff7408e997cafc3261f37d99656e3e69bf2e5ffa4242ff445b8967538f', attempts: 1, received: 224743, retained: 232491, elapsedMs: 543,
    actualCredits: 10, historicalManifestActualCredits: null, evidence: 'OWNER_PASTED_TEXT_2026-10-03_MAIN_RECORDED_11:51:04Z', remaining: 999990 } };
function query3(address, rangeIndex, cursor = null) {
  base58(address, 32); demand(Number.isSafeInteger(rangeIndex) && rangeIndex >= 0 && rangeIndex < 3, 'QUERY_INVALID');
  if (cursor !== null) cursorPosition(cursor);
  const range = CONFIG3.ranges[rangeIndex]; return { version: CONFIG3.queryVersion, method: 'POST', url: CONFIG3.endpoint,
    body: { jsonrpc: '2.0', id: 1, method: CONFIG3.method, params: [address, { commitment: 'finalized', transactionDetails: 'full', encoding: 'json', maxSupportedTransactionVersion: 1,
      sortOrder: 'asc', limit: 1000, filters: { blockTime: { gte: range.from, lt: range.to }, status: 'any', tokenAccounts: 'all' }, ...(cursor === null ? {} : { paginationToken: cursor }) }] } };
}
function validQuery3(q) {
  try { const params = q.body.params, filter = params[1].filters.blockTime, range = CONFIG3.ranges.findIndex(r => r.from === filter.gte && r.to === filter.lt);
    return canonical(q) === canonical(query3(params[0], range, params[1].paginationToken ?? null)); } catch { return false; }
}
function cursorPosition(token) {
  demand(typeof token === 'string' && token.length <= 40 && /^(0|[1-9][0-9]*):(0|[1-9][0-9]*)$/.test(token), 'CURSOR_INVALID');
  return token.split(':').map(integer);
}
const positionCompare = (a, b) => a[0] - b[0] || a[1] - b[1];
const errorState = err => err === null ? 'success' : err && typeof err === 'object' && !Array.isArray(err) ? 'failed' : 'unknown';
function admit3(bytes, q) {
  try {
    demand(validQuery3(q) && Buffer.isBuffer(bytes) && bytes.length <= CONFIG3.limits.response);
    const page = parse(bytes); demand(page && page.jsonrpc === '2.0' && integer(page.id) === 1);
    if (Object.hasOwn(page, 'error')) fail('RPC_ERROR');
    demand(page.result && typeof page.result === 'object' && !Array.isArray(page.result) && Array.isArray(page.result.data) && page.result.data.length <= 1000);
    const range = q.body.params[1].filters.blockTime, unique = new Map();
    for (const row of page.result.data) {
      demand(row && typeof row === 'object' && !Array.isArray(row));
      const slot = integer(row.slot), index = integer(row.transactionIndex), time = integer(row.blockTime); demand(time >= range.gte && time < range.lt);
      demand(row.transaction && typeof row.transaction === 'object' && !Array.isArray(row.transaction) && Array.isArray(row.transaction.signatures) && row.transaction.signatures.length > 0 && row.transaction.signatures.length <= 256);
      for (const sig of row.transaction.signatures) base58(sig, 64);
      demand(row.transaction.message && typeof row.transaction.message === 'object' && !Array.isArray(row.transaction.message));
      demand(row.meta && typeof row.meta === 'object' && !Array.isArray(row.meta));
      const signature = row.transaction.signatures[0], content = digest(Buffer.from(canonical(row))), prior = unique.get(signature);
      demand(!prior || prior.content === content, 'IMMUTABLE_CONFLICT');
      if (!prior) unique.set(signature, { signature, row, slot, index, content, state: errorState(row.meta.err) });
    }
    const rows = [...unique.values()].sort((a, b) => a.slot - b.slot || a.index - b.index || (a.signature < b.signature ? -1 : a.signature > b.signature ? 1 : 0));
    const token = page.result.paginationToken, previous = q.body.params[1].paginationToken ?? null;
    if (token !== null) { const at = cursorPosition(token), last = rows.at(-1); demand(last && positionCompare(at, [last.slot, last.index]) === 0, 'CURSOR_INVALID');
      if (previous !== null) demand(positionCompare(at, cursorPosition(previous)) > 0, 'CURSOR_INVALID'); }
    else if (previous !== null && rows.length) demand(positionCompare([rows.at(-1).slot, rows.at(-1).index], cursorPosition(previous)) > 0, 'CURSOR_INVALID');
    return { code: null, rows, paginationToken: token, returnedRows: page.result.data.length };
  } catch (code) { return { code: ['RPC_ERROR', 'IMMUTABLE_CONFLICT', 'CURSOR_INVALID'].includes(code) ? code : 'RESPONSE_INVALID', rows: [] }; }
}
function readSelectorFile(file, limit) {
  let fd; try { let parent = path.dirname(file); while (true) { const stat = fs.lstatSync(parent); demand(stat.isDirectory() && !stat.isSymbolicLink() && fs.realpathSync.native(parent).toLowerCase() === parent.toLowerCase(), 'UNSAFE_PATH');
      const next = path.dirname(parent); if (next === parent) break; parent = next; }
    demand(!fs.lstatSync(file).isSymbolicLink(), 'INTEGRITY_ERROR'); fd = fs.openSync(file, 'r'); const stat = fs.fstatSync(fd);
    demand(stat.isFile() && stat.size <= limit, 'INPUT_LIMIT'); const bytes = Buffer.alloc(limit + 1); let n = 0;
    while (n < bytes.length) { const count = fs.readSync(fd, bytes, n, bytes.length - n, n); if (!count) break; n += count; }
    demand(n <= limit, 'INPUT_LIMIT'); return bytes.subarray(0, n);
  } finally { if (fd !== undefined) fs.closeSync(fd); }
}
function cohort3(deps) {
  const hashes = deps.selectorHashes ?? CONFIG.selector;
  const raw = deps.selectorRaw ?? readSelectorFile('C:\\crypto-research-evidence\\r1-d1\\exploratory-sqd-v1\\004.raw', 2000000);
  const manifest = deps.selectorManifest ?? readSelectorFile('C:\\crypto-research-evidence\\r1-d1\\exploratory-sqd-v1\\manifest.json', 4000000);
  const cohort = selectCohort(raw, hashes.rawHash, manifest, hashes.manifestHash);
  demand(cohort.addresses.length === 36 && cohort.cohortHash === hashes.cohortHash, 'INTEGRITY_ERROR'); return cohort;
}
function ledger3(preflight, counters) {
  const retained = preflight.retainedBytes; demand(Number.isSafeInteger(retained) && retained >= CONFIG.baseline.retained + CONFIG3.h1.retained && retained <= 10000000000, 'PREFLIGHT_ERROR');
  const pre = { ...CONFIG.baseline, attempts: 100, received: 8505085, retained, elapsedMs: 196764, heliusActualCredits: 10 };
  const post = { ...pre, attempts: pre.attempts + counters.attempts, received: pre.received + counters.received, retained: pre.retained + counters.retained,
    elapsedMs: pre.elapsedMs + counters.elapsedMs, creditsReserved: counters.creditsReserved, heliusActualCredits: null };
  if (counters.elapsedScope === 'PRE_PUBLICATION') { post.elapsedScope = 'PRE_PUBLICATION'; post.prePublicationElapsedMs = post.elapsedMs; }
  demand(post.retained <= 10000000000 && post.elapsedMs <= 14 * 86400000, 'DISK_LIMIT'); return { pre, step: { ...counters }, post };
}
const SAFE3 = new Set(['HTTP_ERROR', 'NETWORK_ERROR', 'TIMEOUT', 'PARTIAL_RESPONSE', 'RESPONSE_LIMIT', 'RECEIVED_LIMIT', 'RESPONSE_INVALID', 'RPC_ERROR',
  'IMMUTABLE_CONFLICT', 'CURSOR_INVALID', 'SECRET_EXPOSURE', 'SECRET_UNAVAILABLE', 'STORAGE_ERROR', 'TIME_LIMIT', 'TRANSPORT_ERROR', 'ATTEMPT_LIMIT', 'RETRY_LIMIT',
  'CREDIT_LIMIT', 'DISK_LIMIT', 'FREE_SPACE_LIMIT', 'PAGE_LIMIT', 'RETRY_AFTER_INVALID']);
const safe3 = code => code === null || SAFE3.has(code) ? code : 'TRANSPORT_ERROR';
function retryWait3(record, retryOrdinal) {
  if (!record.attempted || !((record.code === 'HTTP_ERROR' && (record.status === 429 || record.status >= 500 && record.status <= 599)) ||
    (record.code === 'TIMEOUT' && record.ownedDeadline === true))) return null;
  demand(!record.retryAfterInvalid, 'RETRY_AFTER_INVALID');
  let header = 0n; if (record.retryAfter !== null && record.retryAfter !== undefined) {
    demand(typeof record.retryAfter === 'string' && /^[0-9]{1,128}$/.test(record.retryAfter), 'RETRY_AFTER_INVALID'); header = BigInt(record.retryAfter) * 1000n;
  }
  const wait = header > BigInt(CONFIG3.backoffMs[retryOrdinal - 1]) ? header : BigInt(CONFIG3.backoffMs[retryOrdinal - 1]);
  demand(wait <= BigInt(CONFIG3.limits.elapsedMs), 'TIME_LIMIT'); return Number(wait);
}
function state3(cohort) {
  return { streams: cohort.addresses.flatMap((address, addressIndex) => CONFIG3.ranges.map((range, rangeIndex) => ({ id: addressIndex * 3 + rangeIndex,
    address, range: range.id, rangeIndex, cursor: null, status: 'UNQUERIED', pages: 0, rows: 0, returnedRows: 0, received: 0, rawBytes: 0, estimatedCredits: 0,
    statusCounts: { success: 0, failed: 0, unknown: 0 }, seen: new Set() }))), unique: new Map(), rawFiles: new Map() };
}
function applyPage3(state, stream, page) {
  for (const item of page.rows) demand(!state.unique.has(item.signature) || state.unique.get(item.signature).content === item.content, 'IMMUTABLE_CONFLICT');
  // Validate the whole page against existing immutable content before any mutation/publication.
  for (const item of page.rows) {
    if (!state.unique.has(item.signature)) state.unique.set(item.signature, { content: item.content, state: item.state });
    if (!stream.seen.has(item.signature)) { stream.seen.add(item.signature); stream.rows++; stream.statusCounts[item.state]++; }
  }
  stream.pages++; stream.returnedRows += page.returnedRows; stream.estimatedCredits += 10 * Math.max(1, Math.ceil(page.returnedRows / 100));
  stream.cursor = page.paginationToken; stream.status = page.paginationToken === null ? 'COMPLETE' : 'CENSORED';
}
function summary3(state, code, counters, cohort, lineage, records) {
  const statuses = { success: 0, failed: 0, unknown: 0 }; for (const item of state.unique.values()) statuses[item.state]++;
  return { ...FLAGS, version: CONFIG3.summaryVersion, stage: 'H3', status: code ? 'INCOMPLETE' : 'COMPLETE', code, configHash: fingerprint(CONFIG3.canonicalizationVersion, CONFIG3),
    lineage, cohort, rows: state.unique.size, statusCounts: statuses, membershipRows: state.streams.reduce((n, s) => n + s.rows, 0),
    streams: state.streams.map(({ seen, ...s }) => s), attempts: counters.attempts, retries: counters.retries, received: counters.received, creditsReserved: counters.creditsReserved,
    actualCredits: null, estimatedCredits: state.streams.reduce((n, s) => n + s.estimatedCredits, 0), retainedRawBytes: [...state.rawFiles.values()].reduce((n, f) => n + f.size, 0),
    attemptsHash: fingerprint('helius-attempt-semantics-v1', records.map(({ startMs, endMs, waitMs, ...r }) => r)),
    overlapRows: state.streams.reduce((n, s) => n + s.rows, 0) - state.unique.size,
    unmeasured: ['complete filtered candidate discovery', 'pool activity', 'independent200', 'ancillary state/tips/SOL-USD/visibility', 'September/embargo/tail', 'CLMM ticks/DLMM bins'], fullD1UpperBound: null };
}
async function runH3(options = {}, deps = {}) {
  if (!options.enabled) return fixedError('DISABLED');
  if (Object.keys(options).some(k => !['enabled', 'stage', 'creditsRemaining', 'output'].includes(k)) || options.enabled !== true || options.stage !== 'H3' || options.output !== OUTPUT3 ||
    typeof options.creditsRemaining !== 'string' || !/^(0|[1-9][0-9]{0,6})$/.test(options.creditsRemaining) || BigInt(options.creditsRemaining) > BigInt(CONFIG3.h1.remaining)) return fixedError('ARGUMENTS_INVALID');
  if (BigInt(options.creditsRemaining) < 100n) return fixedError('CREDIT_LIMIT');
  const caps = CONFIG3.limits, creditCap = Math.min(caps.credits, Number(options.creditsRemaining)), now = deps.now ?? Date.now, began = now();
  const wait = deps.wait ?? (ms => new Promise(resolve => setTimeout(resolve, ms))); let store, preflight, cohort, lineage;
  try {
    lineage = provenance(deps); preflight = deps.preflight ?? scanMetadata(); ledger3(preflight, { attempts: 0, received: 0, retained: 0, elapsedMs: 0, creditsReserved: 0 });
    demand(preflight.retainedBytes + caps.disk <= 10000000000, 'DISK_LIMIT'); if (preflight.roots) demand(preflight.roots.find(r => r.root === OUTPUT3)?.status === 'ABSENT', 'OUTPUT_EXISTS');
    store = deps.store ?? fileStore(OUTPUT3); demand(store.free() - caps.disk >= caps.free, 'FREE_SPACE_LIMIT');
    demand(now() - began >= 0 && now() - began + caps.deadlineMs <= CONFIG3.timing.sourceCutoffMs, 'TIME_LIMIT'); cohort = cohort3(deps); store.create();
  } catch (error) { const code = typeof error === 'string' ? error : error?.message; return fixedError(['SOURCE_IDENTITY_INVALID', 'RUNTIME_INVALID', 'PREFLIGHT_ERROR', 'PREFLIGHT_LIMIT',
    'DISK_LIMIT', 'OUTPUT_EXISTS', 'FREE_SPACE_LIMIT', 'TIME_LIMIT', 'UNSAFE_PATH', 'INTEGRITY_ERROR', 'INPUT_LIMIT'].includes(code) ? code : 'STORAGE_ERROR'); }
  const state = state3(cohort), records = [], files = [], partialFiles = [], checkpoints = []; let retained = 0, received = 0, attempts = 0, retries = 0, creditsReserved = 0, code = null, lastStart = -Infinity;
  const size = name => { try { return store.size ? store.size(name) : store.read(name).length; } catch { return 0; } };
  const write = (name, bytes) => { demand(retained + bytes.length <= caps.disk, 'DISK_LIMIT'); try { store.write(name, bytes); retained += bytes.length;
      const entry = { name, size: bytes.length, sha256: digest(bytes) }; files.push(entry); return entry;
    } catch { const partial = size(name); retained += partial; if (partial) partialFiles.push({ name, size: partial }); fail('STORAGE_ERROR'); } };
  const checkpoint = () => { for (const [name, value, max] of [['attempts', attempts, caps.attempts], ['credits', creditsReserved, creditCap], ['received', received, caps.received], ['disk', retained, caps.disk], ['elapsed', now() - began, caps.elapsedMs]])
    if (value >= max * 0.8 && !checkpoints.some(c => c.name === name)) checkpoints.push({ name, ordinal: records.length, value }); };
  const capacity = delay => {
    demand(attempts < caps.attempts, 'ATTEMPT_LIMIT'); demand(creditsReserved + 100 <= creditCap, 'CREDIT_LIMIT'); demand(received + caps.response <= caps.received, 'RECEIVED_LIMIT');
    demand(retained + caps.response + caps.metadata <= caps.disk && preflight.retainedBytes + retained + caps.response + caps.metadata <= 10000000000, 'DISK_LIMIT');
    demand(now() - began >= 0 && now() - began + delay + caps.deadlineMs <= CONFIG3.timing.sourceCutoffMs, 'TIME_LIMIT'); demand(store.free() - caps.response - caps.metadata >= caps.free, 'FREE_SPACE_LIMIT');
  };
  try {
    write('attempt.json', Buffer.from(JSON.stringify({ stage: 'H3', configurationHash: fingerprint(CONFIG3.canonicalizationVersion, CONFIG3), cohort }) + '\n'));
    while (state.streams.some(s => s.status !== 'COMPLETE')) {
      for (const stream of state.streams) {
        if (stream.status === 'COMPLETE') continue; demand(stream.pages < caps.pagesPerStream, 'PAGE_LIMIT'); const q = query3(stream.address, stream.rangeIndex, stream.cursor);
        let retryOrdinal = 0, plannedWait = 0;
        while (true) {
          const delay = Math.max(plannedWait, Math.max(0, lastStart + caps.spacingMs - now())); capacity(delay);
          if (delay) { const before = now(); await wait(delay); demand(now() - before >= delay, 'TIME_LIMIT'); }
          capacity(0); const record = { ordinal: records.length, stream: stream.id, query: q, retryOrdinal, startMs: now() - began, endMs: null, waitMs: delay,
            attempted: false, code: null, status: null, received: 0, creditsReserved: 100, ownedDeadline: false, retryAfter: null, retryAfterInvalid: false, raw: null };
          records.push(record); creditsReserved += 100; lastStart = now(); let response, invoked = false, chunks = 0;
          try { invoked = true; response = await (deps.transport ?? send)(q, { onChunk: n => { demand(Number.isSafeInteger(n) && n >= 0, 'RECEIVED_LIMIT'); chunks += n; received += n;
              demand(received <= caps.received && chunks <= caps.response + 1, 'RECEIVED_LIMIT'); } });
            record.attempted = response.attempted !== false; record.code = safe3(response.code ?? null); record.status = Number.isSafeInteger(response.status) ? response.status : null;
            record.ownedDeadline = response.ownedDeadline === true; record.retryAfterInvalid = response.retryAfterInvalid === true;
            if (response.retryAfter != null) { if (typeof response.retryAfter === 'string' && /^[0-9]{1,128}$/.test(response.retryAfter)) record.retryAfter = response.retryAfter; else record.retryAfterInvalid = true; }
            demand(Number.isSafeInteger(response.received) && response.received === chunks, 'RESPONSE_INVALID');
          } catch (error) { record.attempted = invoked && response?.attempted !== false; record.code = safe3(typeof error === 'string' ? error : 'TRANSPORT_ERROR'); }
          record.received = chunks; record.endMs = now() - began; if (record.attempted) attempts++; if (retryOrdinal && record.attempted) retries++;
          stream.received += chunks; checkpoint(); demand(record.endMs >= record.startMs && record.endMs <= caps.elapsedMs, 'TIME_LIMIT');
          if (!record.code) {
            demand(record.status === 200 && Buffer.isBuffer(response?.bytes) && response.bytes.length === chunks, 'RESPONSE_INVALID');
            const page = admit3(response.bytes, q); if (page.code) { record.code = page.code; fail(page.code); }
            for (const item of page.rows) if (state.unique.has(item.signature)) demand(state.unique.get(item.signature).content === item.content, 'IMMUTABLE_CONFLICT');
            const hash = digest(response.bytes); let raw = state.rawFiles.get(hash);
            if (!raw) { raw = write(String(record.ordinal).padStart(4, '0') + '.raw', response.bytes); state.rawFiles.set(hash, raw); }
            record.raw = raw; stream.rawBytes += raw.size; applyPage3(state, stream, page); break;
          }
          if (retryOrdinal >= caps.retries) fail(record.code);
          const backoff = retryWait3(record, retryOrdinal + 1); if (backoff === null) fail(record.code);
          demand(retries < caps.globalRetries, 'RETRY_LIMIT'); plannedWait = backoff; retryOrdinal++;
        }
      }
    }
  } catch (error) { code = safe3(typeof error === 'string' ? error : 'TRANSPORT_ERROR');
    const last = records.at(-1); if (last && last.code === null && last.raw === null) last.code = code; }
  checkpoint(); const prePublicationElapsedMs = now() - began;
  const counters = { attempts, retries, received, retained: 0, elapsedScope: 'PRE_PUBLICATION', prePublicationElapsedMs,
    elapsedMs: prePublicationElapsedMs, creditsReserved, actualCredits: null, cashMicrousd: '0', concurrency: 1 };
  const summary = summary3(state, code, counters, cohort, lineage, records), summaryBytes = Buffer.from(JSON.stringify(summary) + '\n'), summaryHash = digest(summaryBytes);
  const utc = (deps.utcNow ?? Date.now)(), manifest = { version: 'helius-h3-manifest-v1', ...FLAGS, configuration: CONFIG3, lineage, cohort, preflight,
    quota: { remainingSupplied: options.creditsRemaining, h1Reconciliation: CONFIG3.h1, type: 'OWNER_PASTED_TEXT', actualH3Debit: null },
    files, partialFiles, records, code, summaryHash, runBudget: 'UNMEASURED', accounting: null, checkpoints, operational: {
      elapsedScope: 'PRE_PUBLICATION', prePublicationElapsedMs, startedAt: new Date(utc - counters.elapsedMs).toISOString(),
      endedAt: new Date(utc).toISOString(), elapsedMs: counters.elapsedMs, rawExpiresAt: new Date(utc + 14 * 86400000).toISOString() } };
  let manifestStored = false;
  try {
    write('summary.json', summaryBytes); let bytes = Buffer.alloc(0);
    for (let n = 0; n < 8; n++) { counters.retained = retained + bytes.length; manifest.accounting = { ...counters, cumulative: ledger3(preflight, counters) }; bytes = Buffer.from(JSON.stringify(manifest) + '\n'); }
    demand(retained + bytes.length <= caps.disk && retained + bytes.length - summary.retainedRawBytes <= caps.metadata, 'STORAGE_ERROR');
    store.write('manifest.json', bytes); manifestStored = true; retained += bytes.length; demand(manifest.accounting.retained === retained, 'STORAGE_ERROR');
  } catch { code = 'STORAGE_ERROR'; counters.retained = retained + (manifestStored ? 0 : size('manifest.json')); }
  counters.retained = manifestStored ? retained : counters.retained;
  return { ...FLAGS, status: code ? 'INCOMPLETE' : 'COMPLETE', code, runBudget: 'UNMEASURED', summary, summaryHash: code === 'STORAGE_ERROR' ? null : summaryHash,
    accounting: { ...counters, cumulative: ledger3(preflight, counters) } };
}
async function replay3(store = fileStore(OUTPUT3), options = {}) {
  try {
    const manifestBytes = store.read('manifest.json'), m = parse(manifestBytes, false), caps = CONFIG3.limits;
    demand(m.version === 'helius-h3-manifest-v1' && m.runBudget === 'UNMEASURED' && canonical(m.configuration) === canonical(CONFIG3), 'INTEGRITY_ERROR');
    demand(m.lineage && /^[a-f0-9]{40}$/.test(m.lineage.source?.commit) && typeof m.lineage.source.dirty === 'boolean' && /^v24\./.test(m.lineage.runtime), 'INTEGRITY_ERROR');
    demand(Object.keys(m.lineage.scripts).length === 3 && ['helius-probe.cjs', 'helius-probe-cli.cjs', 'exploratory-probe.cjs'].every(n => /^sha256:[a-f0-9]{64}$/.test(m.lineage.scripts[n])), 'INTEGRITY_ERROR');
    const expiry = Date.parse(m.operational.rawExpiresAt), end = Date.parse(m.operational.endedAt);
    demand(Number.isFinite(end) && Number.isFinite(expiry) && expiry - end === 14 * 86400000, 'INTEGRITY_ERROR'); if ((options.utcNow ?? Date.now)() >= expiry) return fixedError('REPLAY_EXPIRED');
    demand(m.partialFiles.length === 0 && Array.isArray(m.files) && m.files.length >= 2 && m.files.length <= 1002 && Array.isArray(m.records) && m.records.length <= caps.attempts, 'INTEGRITY_ERROR');
    demand(m.cohort.version === CONFIG.selector.version && m.cohort.addresses.length === 36 && new Set(m.cohort.addresses).size === 36 &&
      canonical(m.cohort.addresses) === canonical([...m.cohort.addresses].sort()) && fingerprint('helius-address-selection-v1', m.cohort.addresses) === m.cohort.cohortHash, 'INTEGRITY_ERROR');
    for (const a of m.cohort.addresses) base58(a, 32);
    const allowed = new Set(['manifest.json']), metadata = new Map(); let actualRetained = manifestBytes.length, rawBytes = 0;
    for (const file of m.files) { demand(/^(attempt\.json|summary\.json|0[0-9]{3}\.raw)$/.test(file.name) && !allowed.has(file.name), 'INTEGRITY_ERROR');
      allowed.add(file.name); const bytes = store.read(file.name); demand(bytes.length === file.size && digest(bytes) === file.sha256, 'INTEGRITY_ERROR'); actualRetained += bytes.length;
      if (file.name.endsWith('.raw')) rawBytes += file.size; else metadata.set(file.name, bytes); }
    demand(store.list().length === allowed.size && store.list().every(n => allowed.has(n)) && metadata.has('attempt.json') && metadata.has('summary.json'), 'INTEGRITY_ERROR');
    demand(canonical(parse(metadata.get('attempt.json'), false)) === canonical({ stage: 'H3', configurationHash: fingerprint(CONFIG3.canonicalizationVersion, CONFIG3), cohort: m.cohort }), 'INTEGRITY_ERROR');
    demand(m.quota.type === 'OWNER_PASTED_TEXT' && canonical(m.quota.h1Reconciliation) === canonical(CONFIG3.h1) && m.quota.actualH3Debit === null &&
      typeof m.quota.remainingSupplied === 'string' && /^(0|[1-9][0-9]{0,6})$/.test(m.quota.remainingSupplied) && Number(m.quota.remainingSupplied) >= 100 && Number(m.quota.remainingSupplied) <= CONFIG3.h1.remaining, 'INTEGRITY_ERROR');
    demand(m.preflight.retainedBytes + caps.disk <= 10000000000, 'INTEGRITY_ERROR');
    const state = state3(m.cohort), counters = { attempts: 0, retries: 0, received: 0, retained: m.accounting.retained, elapsedScope: 'PRE_PUBLICATION',
      prePublicationElapsedMs: m.accounting.prePublicationElapsedMs, elapsedMs: m.accounting.elapsedMs,
      creditsReserved: 0, actualCredits: null, cashMicrousd: '0', concurrency: 1 }, referenced = new Set(); let turn = 0, prior = null;
    for (const record of m.records) {
      while (state.streams[turn % 108].status === 'COMPLETE') { turn++; demand(turn < 30000, 'INTEGRITY_ERROR'); }
      const stream = state.streams[turn % 108]; demand(record.ordinal === counters.creditsReserved / 100 && record.stream === stream.id && record.creditsReserved === 100 &&
        typeof record.attempted === 'boolean' && Number.isSafeInteger(record.received) && record.received >= 0 && record.received <= caps.response + 1 &&
        Number.isSafeInteger(record.retryOrdinal) && record.retryOrdinal >= 0 && record.retryOrdinal <= 3 && Number.isSafeInteger(record.startMs) && Number.isSafeInteger(record.endMs) &&
        record.startMs >= 0 && record.endMs >= record.startMs && record.endMs <= caps.elapsedMs && Number.isSafeInteger(record.waitMs) && record.waitMs >= 0 && record.waitMs <= caps.elapsedMs,
        'INTEGRITY_ERROR');
      const priorRawBytes = [...state.rawFiles.values()].reduce((n, f) => n + f.size, 0);
      demand(counters.attempts < caps.attempts && counters.creditsReserved + 100 <= Math.min(caps.credits, Number(m.quota.remainingSupplied)) && counters.received + caps.response <= caps.received &&
        record.startMs + caps.deadlineMs <= CONFIG3.timing.sourceCutoffMs && priorRawBytes + metadata.get('attempt.json').length + caps.response + caps.metadata <= caps.disk, 'INTEGRITY_ERROR');
      demand(canonical(record.query) === canonical(query3(stream.address, stream.rangeIndex, stream.cursor)) && stream.pages < caps.pagesPerStream &&
        (record.code === null || SAFE3.has(record.code)) && (record.status === null || Number.isSafeInteger(record.status)), 'INTEGRITY_ERROR');
      if (prior) {
        demand(record.startMs >= prior.endMs && record.startMs - prior.startMs >= caps.spacingMs, 'INTEGRITY_ERROR');
        if (prior.code !== null) { demand(record.retryOrdinal === prior.retryOrdinal + 1 && canonical(record.query) === canonical(prior.query), 'INTEGRITY_ERROR');
          const wait = retryWait3(prior, record.retryOrdinal); demand(wait !== null && record.waitMs >= wait && record.startMs - prior.endMs >= wait, 'INTEGRITY_ERROR'); }
        else demand(record.retryOrdinal === 0, 'INTEGRITY_ERROR');
      } else demand(record.retryOrdinal === 0, 'INTEGRITY_ERROR');
      counters.attempts += record.attempted ? 1 : 0; counters.retries += record.attempted && record.retryOrdinal ? 1 : 0; counters.received += record.received; counters.creditsReserved += 100;
      stream.received += record.received;
      if (record.code === null) {
        demand(record.attempted && record.status === 200 && record.raw && record.received === record.raw.size, 'INTEGRITY_ERROR');
        const file = m.files.find(f => f.name === record.raw.name); demand(file && file.name.endsWith('.raw') && canonical(file) === canonical(record.raw), 'INTEGRITY_ERROR');
        const page = admit3(store.read(file.name), record.query); demand(page.code === null, 'INTEGRITY_ERROR');
        if (state.rawFiles.has(file.sha256)) demand(canonical(state.rawFiles.get(file.sha256)) === canonical(file), 'INTEGRITY_ERROR'); else state.rawFiles.set(file.sha256, file);
        referenced.add(file.name); stream.rawBytes += file.size; applyPage3(state, stream, page); turn++;
      } else demand(record.raw === null, 'INTEGRITY_ERROR'); prior = record;
    }
    demand(m.files.filter(f => f.name.endsWith('.raw')).every(f => referenced.has(f.name)) && counters.attempts <= caps.attempts && counters.retries <= caps.globalRetries &&
      counters.received <= caps.received + 1 && counters.creditsReserved <= Math.min(caps.credits, Number(m.quota.remainingSupplied)), 'INTEGRITY_ERROR');
    demand(m.code === null ? state.streams.every(s => s.status === 'COMPLETE') : SAFE3.has(m.code), 'INTEGRITY_ERROR');
    const summary = summary3(state, m.code, counters, m.cohort, m.lineage, m.records), bytes = Buffer.from(JSON.stringify(summary) + '\n');
    demand(digest(bytes) === m.summaryHash && digest(metadata.get('summary.json')) === m.summaryHash, 'INTEGRITY_ERROR');
    const a = m.accounting, op = m.operational;
    demand(Number.isSafeInteger(a.retained) && a.retained >= actualRetained - manifestBytes.length && a.retained <= caps.disk && a.retained - rawBytes <= caps.metadata &&
      actualRetained <= caps.disk && actualRetained - rawBytes <= caps.metadata && Number.isSafeInteger(a.elapsedMs) && a.elapsedMs >= 0 &&
      a.elapsedScope === 'PRE_PUBLICATION' && a.prePublicationElapsedMs === a.elapsedMs && a.elapsedMs >= (m.records.at(-1)?.endMs ?? 0) &&
      op.elapsedScope === 'PRE_PUBLICATION' && op.prePublicationElapsedMs === a.elapsedMs &&
      Number.isSafeInteger(op.elapsedMs) && op.elapsedMs >= 0 && Number.isFinite(Date.parse(op.startedAt)), 'INTEGRITY_ERROR');
    demand(canonical({ ...counters, cumulative: ledger3(m.preflight, counters) }) === canonical(a) && a.cumulative.pre.retained + actualRetained <= 10000000000, 'INTEGRITY_ERROR');
    return { ...FLAGS, status: summary.status, code: null, runBudget: 'UNMEASURED', summary, summaryHash: m.summaryHash, accounting: a };
  } catch { return fixedError('INTEGRITY_ERROR'); }
}
function forecast(summary, accounting) {
  try {
    demand(summary?.version === CONFIG3.summaryVersion && summary.streams.length === 108, 'INTEGRITY_ERROR');
    const complete = []; for (let n = 0; n < 36; n++) { const streams = summary.streams.slice(n * 3, n * 3 + 3); if (streams.every(s => s.status === 'COMPLETE')) complete.push({
      rows: streams.reduce((v, s) => v + BigInt(s.rows), 0n), received: streams.reduce((v, s) => v + BigInt(s.received), 0n),
      retained: streams.reduce((v, s) => v + BigInt(s.rawBytes), 0n), credits: streams.reduce((v, s) => v + BigInt(s.estimatedCredits), 0n), pages: streams.reduce((v, s) => v + BigInt(s.pages), 0n) }); }
    const receipt = { ...FLAGS, version: 'helius-wallet-history-sensitivity-v1', runBudget: 'UNMEASURED', completeAddresses: complete.length, censoredAddresses: 36 - complete.length,
      assumptions: ['SAMPLE_SENSITIVITY_NOT_POPULATION_BOUNDS', '180/150_SAME_WORKLOAD', 'NO_ROLLOVER_CYCLE_QUOTA', 'PER_PAGE_10_CREDIT_MINIMUM', 'LOGICAL_RAW_WITHOUT_CROSS_ADDRESS_SHARING',
        'OBSERVED_METADATA_EQUALLY_ALLOCATED_ACROSS_36_SAMPLE_ADDRESSES', 'ANCILLARY_HOLDOUT_TAIL_COSTS_NOT_PROJECTED', 'NO_EXECUTION_EXTENSION_BEYOND_14_DAYS'], sample: { uniqueRows: summary.rows, membershipRows: summary.membershipRows,
        overlapRows: summary.overlapRows, failed: summary.statusCounts.failed, unknown: summary.statusCounts.unknown, rawPhysicalBytes: summary.retainedRawBytes,
        actualRetainedBytes: accounting?.retained ?? null, actualCredits: null,
        failedShare: { numerator: String(summary.statusCounts.failed), denominator: String(summary.rows) }, unknownShare: { numerator: String(summary.statusCounts.unknown), denominator: String(summary.rows) } },
      operational: accounting ? { elapsedScope: accounting.elapsedScope, prePublicationElapsedMs: accounting.prePublicationElapsedMs,
        elapsedMs: accounting.elapsedMs, receivedBytesPerMs: accounting.elapsedMs > 0 ? { numerator: String(accounting.received), denominator: String(accounting.elapsedMs) } : null } : null,
      fullD1UpperBound: null, scenarios: null };
    if (!complete.length) return receipt;
    const ceil = (n, d) => (n + d - 1n) / d, sum = k => complete.reduce((n, r) => n + r[k], 0n);
    const rates = {}; for (const k of ['rows', 'received', 'retained', 'credits', 'pages']) rates[k] = { min: complete.reduce((n, r) => r[k] < n ? r[k] : n, complete[0][k]),
      pooled: sum(k), max: complete.reduce((n, r) => r[k] > n ? r[k] : n, complete[0][k]) };
    receipt.rates = Object.fromEntries(Object.entries(rates).map(([k, r]) => [k, Object.fromEntries(Object.entries(r).map(([mode, value]) => [mode, { numerator: value.toString(), denominator: String(150 * (mode === 'pooled' ? complete.length : 1)) }]))]));
    const metadataBytes = accounting ? BigInt(accounting.retained - summary.retainedRawBytes) : null;
    if (metadataBytes !== null) demand(metadataBytes >= 0n && metadataBytes <= BigInt(CONFIG3.limits.metadata), 'INTEGRITY_ERROR');
    receipt.scenarios = [1000, 5000, 20000].map(wallets => {
      const scenario = { wallets }; for (const mode of ['min', 'pooled', 'max']) {
        const denom = BigInt(150 * (mode === 'pooled' ? complete.length : 1)), project = k => ceil(rates[k][mode] * 180n * BigInt(wallets), denom);
        const roundedPagesPerWallet = ceil(rates.pages[mode] * 180n, denom);
        const metadataShare = metadataBytes === null ? null : metadataBytes * BigInt(mode === 'pooled' ? complete.length : 1);
        scenario[mode] = { transactions: project('rows').toString(), receivedBytes: project('received').toString(), retainedRawBytes: project('retained').toString(),
          retainedBytes: metadataShare === null ? null : ceil((rates.retained[mode] * 36n + metadataShare) * 180n * BigInt(wallets), denom * 36n).toString(),
          estimatedCredits: project('credits').toString(), projectedPages: project('pages').toString(), pageRoundedCreditMinimum: (roundedPagesPerWallet * 10n * BigInt(wallets)).toString() };
      }
      scenario.months = [1, 2, 3].map(months => ({ months, allowanceCredits: String(months * 1000000), walletHistoryFits: BigInt(scenario.pooled.estimatedCredits) <= BigInt(months * 1000000) &&
        BigInt(scenario.pooled.pageRoundedCreditMinimum) <= BigInt(months * 1000000), sampleSensitivityFits: Object.fromEntries(['min', 'pooled', 'max'].map(mode => [mode,
          BigInt(scenario[mode].estimatedCredits) <= BigInt(months * 1000000) && BigInt(scenario[mode].pageRoundedCreditMinimum) <= BigInt(months * 1000000)])), fullD1Fits: null })); return scenario;
    }); return receipt;
  } catch { return fixedError('INTEGRITY_ERROR'); }
}
module.exports = { OUTPUT, OUTPUT3, CONFIG, CONFIG3, query, query3, admit, admit3, runProbe, runH3, selectCohort, replay, replay3, forecast, send, fileStore, scanMetadata, SCAN_ROOTS };
