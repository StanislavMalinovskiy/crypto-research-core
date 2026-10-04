'use strict';
const fs = require('node:fs'), path = require('node:path'), https = require('node:https');
const { execFileSync } = require('node:child_process');
const v1 = require('./e2-census.cjs');
const { parse, canonical, digest, fingerprint } = require('./exploratory-probe.cjs');
const MONTHS = ['april', 'may', 'june'], BOUNDS = [410195947, 416762082, 423478907, 429340001];
const ROOT = 'C:\\crypto-research-evidence\\r1-e2', OLD_ROOT = ROOT + '\\exploratory-census-v1';
const EXPIRY = '2026-10-17T11:42:35.392Z';
const LIMITS = Object.freeze({ attempts: 16000, retries: 480, response: 64000000, received: 1000000000,
  retained: 1200000000, metadata: 64000000, summary: 4000000, stdout: 1000000, creationBytes: 512000000,
  creations: 500000, pageInstructions: 100000, instructions: 1000000, headers: 1000000, diagnostics: 1000,
  deadline: 60000, spacing: 250, sourceMs: 14100000, totalMs: 14400000, free: 30000000000, aggregate: 50000000000 });
const TOTAL = Object.freeze({ attempts: 48000, retries: 1440, received: 3000000000, retained: 3600000000, elapsedMs: 43200000 });
const FLAGS = Object.freeze({ classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, cohortAdmitted: false,
  authoritativeCensusComplete: false, fullD1UpperBound: null, fullD1Fits: null });
const PINS = Object.freeze({ 'e2-census.cjs': 'sha256:b7cdf7aa1a27873bf67fd09d128199aec76fb2e744611e62c68d3520b2e190ea',
  'e2-census-cli.cjs': 'sha256:2870f7d709c04e1e5390448149d971e5dd6997f44f041c01b5bc1a7b7a5c0e61',
  'exploratory-probe.cjs': 'sha256:8f8790026241de1bf3bcded6fef3fe99a6b954af34931184d04897ca393b0a47' });
const SEAL = Object.freeze({ manifestBytes: 331576,
  manifestHash: 'sha256:326db45487ba3c3c43f19f5448294d08fd37b166a27abd7c432cd44bee270e55',
  rawHashes: ['5d40776950bd51dd06ec409242ec053406479ed9f70cc9534bd2f31b410f9a36',
    'ef56c928a98e6271a4fba5173c777e38875971bece7c6c7117b326fa4bf5dbcf',
    'b4f209f134d5b4e14be8539a9e63cad4cf77361fa1433b14c88a1acdab681b3e',
    '2422844a9b792ab767e2273ffc186ababb8779ed04f0b6ffea66619274f9d94f',
    '54bed3c1978a2bd6db5a04d9da81520151c92289dc67dbc6e8cead170ef326c0',
    '9b9ef0a4291288994ff32317a274cef84ab8dd6801f747d0a7088ddac416b376',
    'ad7af4a07e587025c0a41a0c4382d802af2acc07656fdda7bfc6f76ac5bb02ac',
    '2f90ee848e646b5f826b41a09f30ff4add2bf369971dba3973595227fe3c4583'] });
const CODES = new Set(['DISABLED', 'ARGUMENTS_INVALID', 'UNSAFE_PATH', 'ROOT_EXISTS', 'EXPIRY', 'RUNTIME_INVALID',
  'SOURCE_IDENTITY_INVALID', 'INTEGRITY_ERROR', 'ORDER_INVALID', 'RESPONSE_INVALID', 'IMMUTABLE_CONFLICT', 'NO_PROGRESS',
  'HTTP_ERROR', 'HTTP_529_EXHAUSTED', 'PARTIAL_RESPONSE', 'TIMEOUT', 'NETWORK_ERROR', 'RESPONSE_LIMIT', 'TIME_LIMIT',
  'FREE_SPACE_LIMIT', 'DISK_LIMIT', 'RECEIVED_LIMIT', 'ATTEMPT_LIMIT', 'RETRY_LIMIT', 'INSTRUCTION_LIMIT',
  'CREATION_LIMIT', 'HEADER_LIMIT', 'METADATA_LIMIT', 'STORAGE_ERROR']);
const need = (yes, code = 'INTEGRITY_ERROR') => { if (!yes) throw Error(code); };
const safe = (e, fallback = 'INTEGRITY_ERROR') => CODES.has(e?.message) ? e.message : fallback;
const uint = (x, code = 'INTEGRITY_ERROR') => { need(Number.isSafeInteger(x) && x >= 0, code); return x; };
const scalar = x => { need(typeof x === 'string' && /^(0|[1-9][0-9]*)$/.test(x), 'ARGUMENTS_INVALID'); return uint(Number(x), 'ARGUMENTS_INVALID'); };
const json = x => Buffer.from(JSON.stringify(x) + '\n');
const error = code => ({ ...FLAGS, status: 'INCOMPLETE', code, runBudget: 'UNMEASURED', preflight: true });
const indexOf = month => { const i = MONTHS.indexOf(month); need(i >= 0, 'ARGUMENTS_INVALID'); return i; };
const output = month => ROOT + '\\exploratory-monthly-census-v1-' + MONTHS[indexOf(month)];
function configuration(month) { return { version: 'e2-monthly-census-v1', canonicalVersion: 'e2-monthly-census-c14n-v1',
  month, output: output(month), months: MONTHS, bounds: BOUNDS, limits: LIMITS, total: TOTAL, seal: SEAL, pins: PINS,
  expiry: EXPIRY, base: v1.CONFIG.base, program: v1.CONFIG.program, schema: v1.CONFIG.schema, idl: v1.CONFIG.idl,
  retention: 'UNVERIFIED', fallback: 'NOT_RUN', cashMicrousd: '0' }; }
function query(month, current = BOUNDS[indexOf(month)]) { return v1.query('data', indexOf(month), current, BOUNDS); }
function admit(bytes, q, month) { try { need(canonical(q) === canonical(query(month, q.slot)), 'RESPONSE_INVALID');
  return v1.admit(bytes, q, BOUNDS); } catch { return { code: 'RESPONSE_INVALID', headers: [], creations: [], counts: {} }; } }
function ancestors(file) { let dir = path.dirname(file); while (true) { if (fs.existsSync(dir)) {
  const s = fs.lstatSync(dir); need(s.isDirectory() && !s.isSymbolicLink() && fs.realpathSync.native(dir).toLowerCase() === dir.toLowerCase(), 'UNSAFE_PATH'); }
  const parent = path.dirname(dir); if (parent === dir) break; dir = parent; } }
function readBounded(file, limit) { ancestors(file); const stat = fs.lstatSync(file);
  need(stat.isFile() && !stat.isSymbolicLink() && stat.size <= limit); const fd = fs.openSync(file, 'r');
  try { need(fs.fstatSync(fd).size === stat.size); const bytes = Buffer.alloc(stat.size + 1); let at = 0;
    while (at < bytes.length) { const n = fs.readSync(fd, bytes, at, bytes.length - at, null); if (!n) break; at += n; }
    need(at === stat.size); return bytes.subarray(0, at); } finally { fs.closeSync(fd); } }
function verifyPins() { for (const [name, hash] of Object.entries(PINS)) need(digest(readBounded(path.join(__dirname, name), 1000000)) === hash); }
function verifyBoundary(read = name => readBounded(path.join(OLD_ROOT, name), name === 'manifest.json' ? SEAL.manifestBytes : 194)) {
  verifyPins(); const bytes = read('manifest.json'); need(bytes.length === SEAL.manifestBytes && digest(bytes) === SEAL.manifestHash);
  const m = parse(bytes, false); need(m.version === 'e2-census-v1' && Array.isArray(m.records));
  for (let i = 0; i < 8; i++) { const name = String(i + 1).padStart(4, '0') + '.raw', size = i < 4 ? 26 : 194;
    const entries = m.records.filter(r => r.raw === name), files = m.files.filter(f => f.name === name);
    need(entries.length === 1 && files.length === 1); const r = entries[0], f = files[0], raw = read(name), hash = 'sha256:' + SEAL.rawHashes[i];
    need(raw.length === size && digest(raw) === hash && r.rawHash === hash && r.rawBytes === size && r.received === size
      && f.bytes === size && f.hash === hash && r.code === null && r.status === 200);
    const q = i < 4 ? v1.query('resolver', i) : v1.query('header', i - 4, null, BOUNDS);
    need(canonical(r.query) === canonical(q)); const a = v1.admit(raw, q, BOUNDS); need(a.code === null);
    if (i < 4) need(a.slot === BOUNDS[i]); else need(a.header.number === q.slot
      && a.header.timestamp === (i === 7 ? v1.CONFIG.dates[3] - 1 : v1.CONFIG.dates[i - 4]));
  }
  return { status: 'VERIFIED', bounds: [...BOUNDS], manifestHash: SEAL.manifestHash, controlBytes: 880 };
}
function fileStore(month) { const root = output(month); const check = () => { need(path.resolve(root) === root, 'UNSAFE_PATH'); ancestors(path.join(root, 'manifest.json')); };
  const file = name => { need(['attempt.json', 'manifest.json', 'summary.json', 'creations.jsonl'].includes(name)
    || /^[0-9]{5}\.raw$/.test(name), 'UNSAFE_PATH'); check(); return path.join(root, name); };
  return { absent() { check(); return !fs.existsSync(root); },
    free() { check(); let dir = root; while (!fs.existsSync(dir)) dir = path.dirname(dir);
      const s = fs.statfsSync(dir, { bigint: true }), n = s.bavail * s.bsize; need(n <= BigInt(Number.MAX_SAFE_INTEGER), 'FREE_SPACE_LIMIT'); return Number(n); },
    create() { check(); need(!fs.existsSync(root), 'ROOT_EXISTS'); fs.mkdirSync(path.dirname(root), { recursive: true }); check(); fs.mkdirSync(root); },
    write(name, bytes) { const fd = fs.openSync(file(name), 'wx'); try { let at = 0;
      while (at < bytes.length) { const n = fs.writeSync(fd, bytes, at, bytes.length - at); need(n > 0, 'STORAGE_ERROR'); at += n; } fs.fsyncSync(fd); } finally { fs.closeSync(fd); } },
    read(name) { return readBounded(file(name), name === 'creations.jsonl' ? LIMITS.creationBytes : name.endsWith('.raw') ? LIMITS.response : LIMITS.metadata); },
    size(name) { try { const s = fs.lstatSync(file(name)); need(s.isFile() && !s.isSymbolicLink()); return s.size; } catch (e) { if (e.code === 'ENOENT') return 0; throw e; } },
    list() { check(); return fs.readdirSync(root); } };
}
function sourceIdentity(deps) { verifyPins(); const cwd = path.resolve(__dirname, '../../..'), source = deps.source ?? {
  commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd, encoding: 'utf8', timeout: 5000 }).trim(),
  dirty: !!execFileSync('git', ['status', '--porcelain'], { cwd, encoding: 'utf8', timeout: 5000 }).trim() };
  need(/^[a-f0-9]{40}$/.test(source.commit) && typeof source.dirty === 'boolean', 'SOURCE_IDENTITY_INVALID');
  const runtime = deps.runtime ?? process.version; need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(runtime), 'RUNTIME_INVALID');
  const scripts = { ...PINS }; for (const n of ['e2-monthly-census.cjs', 'e2-monthly-census-cli.cjs']) scripts[n] = digest(readBounded(path.join(__dirname, n), 1000000));
  return { source, runtime, scripts }; }
function previous(month) { return MONTHS.slice(0, indexOf(month)).map(m => {
  const store = fileStore(m), bytes = store.read('manifest.json'), v = parse(bytes, false);
  const summaryBytes = store.read('summary.json'); need(v.summaryHash === fingerprint(configuration(m).canonicalVersion, parse(summaryBytes, false)));
  return v; }); }
function priorTotals(month, manifests, attempts, retries) {
  const index = indexOf(month); need(Array.isArray(manifests) && manifests.length === index, 'ORDER_INVALID');
  const totals = { attempts: 0, retries: 0, received: 0, retained: 0, elapsedMs: 0 };
  manifests.forEach((m, i) => { need(canonical(m.configuration) === canonical(configuration(MONTHS[i])) && m.version === 'e2-monthly-census-v1'
    && ['SCAN_COMPLETE', 'INCOMPLETE'].includes(m.status) && m.accounting?.retainedKnown === true && m.runBudget !== 'OVERRUN', 'ORDER_INVALID');
    for (const k of Object.keys(totals)) { const n = uint(k === 'elapsedMs' ? m.publicationOperational?.elapsedMs : m.accounting[k], 'ORDER_INVALID');
      need(n <= (k === 'elapsedMs' ? LIMITS.totalMs : LIMITS[k]), 'ORDER_INVALID'); totals[k] += n; }
    need(m.accounting.retries <= m.accounting.attempts && m.accounting.attempts === m.records?.length, 'ORDER_INVALID');
  });
  need(totals.attempts === attempts && totals.retries === retries, 'ORDER_INVALID');
  for (const k of Object.keys(totals)) need(totals[k] + (k === 'elapsedMs' ? LIMITS.totalMs : LIMITS[k]) <= TOTAL[k], 'ORDER_INVALID');
  return totals;
}
function send(q, options) { return new Promise(resolve => { let req, timer, done = false, status = null, retryAfter = null, received = 0, chunks = [];
  const finish = (code, bytes) => { if (done) return; done = true; clearTimeout(timer); resolve({ code, bytes, status, retryAfter, received }); };
  try { need(q.method === 'POST' && q.url === v1.CONFIG.base + 'finalized-stream'); const body = json(q.body);
    req = https.request(q.url, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': body.length } }, res => {
      status = res.statusCode; retryAfter = res.headers['retry-after'] ?? null;
      res.on('data', chunk => { if (done) return; received += chunk.length; try { options.onChunk(chunk.length); }
        catch (e) { finish(safe(e, 'RESPONSE_LIMIT')); res.destroy(); req.destroy(); return; } if (status === 200) chunks.push(chunk); });
      res.on('end', () => finish(null, status === 200 ? Buffer.concat(chunks) : undefined));
      res.on('aborted', () => finish('PARTIAL_RESPONSE')); res.on('error', () => finish('PARTIAL_RESPONSE'));
    }); timer = setTimeout(() => { finish('TIMEOUT'); req.destroy(); }, LIMITS.deadline);
    req.on('error', () => finish('NETWORK_ERROR')); req.end(body);
  } catch { finish('NETWORK_ERROR'); req?.destroy(); }
}); }
const locator = c => `${c.slot}:${c.transactionIndex}:${c.instructionPath.join('.')}:${c.signature}`;
function compare(a, b) { let n = a.slot - b.slot || a.transactionIndex - b.transactionIndex; if (n) return n;
  for (let i = 0; i < Math.min(a.instructionPath.length, b.instructionPath.length); i++) if (a.instructionPath[i] !== b.instructionPath[i]) return a.instructionPath[i] - b.instructionPath[i];
  return a.instructionPath.length - b.instructionPath.length || (a.signature < b.signature ? -1 : a.signature > b.signature ? 1 : 0); }
function initial(month) { const i = indexOf(month); return { month, current: BOUNDS[i], start: BOUNDS[i], end: BOUNDS[i + 1] - 1,
  pages: 0, lastScanned: null, lastMatched: null, matchingHeaders: 0, instructions: 0, success: 0, unknown: 0,
  topLevel: 0, cpi: 0, depth: {}, reasons: {}, headers: new Map(), creations: new Map(), creationBytes: 0, serializedCreations: 0 }; }
// Validate the entire batch, then commit after exclusive raw publication succeeds. O(new batch), not O(all creations/page).
function prepare(state, admitted) {
  need(admitted.code === null, admitted.code); need(admitted.lastScanned >= state.current, 'NO_PROGRESS');
  const headers = new Map(), creations = new Map(); let bytes = 0;
  for (const h of admitted.headers) { const old = state.headers.get(h.number) ?? headers.get(h.number);
    if (old) need(canonical(old) === canonical(h), 'IMMUTABLE_CONFLICT');
    const parent = headers.get(h.parentNumber) ?? state.headers.get(h.parentNumber); if (parent) need(parent.hash === h.parentHash, 'IMMUTABLE_CONFLICT');
    if (!state.headers.has(h.number)) headers.set(h.number, h); }
  need(state.headers.size + headers.size <= LIMITS.headers, 'HEADER_LIMIT');
  for (const c of admitted.creations) { const id = locator(c), old = state.creations.get(id) ?? creations.get(id);
    if (old) need(canonical(old.value) === canonical(c), 'IMMUTABLE_CONFLICT'); else { const b = json(c); bytes += b.length; creations.set(id, { value: c, bytes: b }); } }
  need(state.creations.size + creations.size <= LIMITS.creations && state.creationBytes + bytes <= LIMITS.creationBytes, 'CREATION_LIMIT');
  need(state.instructions + admitted.counts.instructions <= LIMITS.instructions, 'INSTRUCTION_LIMIT');
  const depth = { ...state.depth }, reasons = { ...state.reasons };
  for (const [dest, src] of [[depth, admitted.counts.depth], [reasons, admitted.counts.reasons]]) for (const [k, n] of Object.entries(src)) dest[k] = (dest[k] ?? 0) + n;
  need(Object.keys(depth).length + Object.keys(reasons).length <= LIMITS.diagnostics, 'INSTRUCTION_LIMIT');
  return { headers, creations, bytes, depth, reasons };
}
function apply(state, a, p) { for (const [k, h] of p.headers) state.headers.set(k, h);
  for (const [k, c] of p.creations) state.creations.set(k, c); state.creationBytes += p.bytes; state.serializedCreations += p.creations.size;
  state.depth = p.depth; state.reasons = p.reasons; state.pages++; state.lastScanned = a.lastScanned; state.current = a.lastScanned + 1;
  if (a.lastMatched !== null) { state.lastMatched = a.lastMatched; state.matchingHeaders += a.matchingHeaders; }
  for (const k of ['instructions', 'success', 'unknown', 'topLevel', 'cpi']) state[k] += a.counts[k]; }
function summary(state, code, lineage) { const { headers, creations, serializedCreations, ...stream } = state;
  return { ...FLAGS, version: 'e2-monthly-census-v1', month: state.month, code, status: code ? 'INCOMPLETE' : 'SCAN_COMPLETE',
    stream, creations: creations.size, storedHeaders: headers.size, lineage, sourceCoverage: 'REFERENCE_BOUNDARIES_ONLY',
    fallback: 'NOT_RUN', retention: 'UNVERIFIED', expiry: EXPIRY,
    gaps: ['DEPLOYED_VERSION_UNKNOWN', 'INNER_INGESTION_UNKNOWN', 'GLOBAL_EARLIEST_UNKNOWN', 'ALL_POOL_COVERAGE_UNKNOWN', 'PIT_KNOWN_AT_UNKNOWN', 'RIGHTS_UNKNOWN'] }; }
const summaryHash = s => fingerprint('e2-monthly-census-c14n-v1', s);
function checkBudget(a, prior, creationBytes, metadataBytes, recordBytes, elapsed, pause, retry) {
  need(a.attempts < LIMITS.attempts && prior.attempts + a.attempts < TOTAL.attempts, 'ATTEMPT_LIMIT');
  if (retry) need(a.retries < LIMITS.retries && prior.retries + a.retries < TOTAL.retries, 'RETRY_LIMIT');
  need(a.received + LIMITS.response <= LIMITS.received && prior.received + a.received + LIMITS.response <= TOTAL.received, 'RECEIVED_LIMIT');
  const reserve = LIMITS.response + creationBytes + LIMITS.metadata;
  need(a.retained + reserve <= LIMITS.retained && prior.retained + a.retained + reserve <= TOTAL.retained, 'DISK_LIMIT');
  need(metadataBytes + recordBytes + 131072 + LIMITS.summary + LIMITS.stdout <= LIMITS.metadata, 'METADATA_LIMIT');
  need(elapsed + pause + LIMITS.deadline <= LIMITS.sourceMs && prior.elapsedMs + elapsed + pause + LIMITS.deadline <= TOTAL.elapsedMs, 'TIME_LIMIT');
  return reserve;
}
async function run(options = {}, deps = {}) {
  if (options.enabled !== true) return error('DISABLED');
  const now = deps.now ?? Date.now, utcNow = deps.utcNow ?? Date.now, wait = deps.wait ?? (ms => new Promise(r => setTimeout(r, ms)));
  const start = now(), records = [], files = [], accounting = { attempts: 0, retries: 0, received: 0, retained: 0,
    partialBytes: 0, retainedKnown: true, cashMicrousd: '0', checkpoints: [] };
  let state, config, lineage, prior, store, created = false, code = null, lastStart = -Infinity, metadataBytes = 0, recordBytes = 0;
  const checkpoint = () => { for (const k of ['attempts', 'retries', 'received', 'retained'])
    if ((accounting[k] * 5 >= LIMITS[k] * 4 || (prior[k] + accounting[k]) * 5 >= TOTAL[k] * 4) && !accounting.checkpoints.includes(k)) accounting.checkpoints.push(k); };
  function write(name, bytes) { need(accounting.retained + bytes.length <= LIMITS.retained, 'DISK_LIMIT');
    const metadata = !name.endsWith('.raw') && name !== 'creations.jsonl'; if (metadata) need(metadataBytes + bytes.length + LIMITS.stdout <= LIMITS.metadata, 'METADATA_LIMIT');
    try { store.write(name, bytes); accounting.retained += bytes.length; if (metadata) metadataBytes += bytes.length;
      files.push({ name, bytes: bytes.length, hash: digest(bytes) }); checkpoint(); }
    catch (e) { try { const size = uint(store.size(name), 'STORAGE_ERROR'); accounting.retained += size; accounting.partialBytes += size;
      if (size) files.push({ name, bytes: size, partial: true, hash: digest(store.read(name)) }); }
      catch { accounting.retainedKnown = false; } throw Error('STORAGE_ERROR'); } }
  function room(pause, retry) {
    const reserve = checkBudget(accounting, prior, state.creationBytes, metadataBytes, recordBytes, now() - start, pause, retry);
    need(uint(store.free(), 'FREE_SPACE_LIMIT') - reserve >= LIMITS.free, 'FREE_SPACE_LIMIT');
    checkBudget(accounting, prior, state.creationBytes, metadataBytes, recordBytes, now() - start, pause, retry);
  }
  async function request(q) { for (let retry = 0; retry <= 2; retry++) {
    const ra = retry ? records.at(-1).retryAfterSeconds : 0;
    const pause = retry ? Math.max(retry === 1 ? 15000 : 45000, (ra ?? 0) * 1000) : Math.max(0, LIMITS.spacing - (now() - lastStart));
    room(pause, retry); if (pause) await wait(pause); room(0, retry);
    const record = { ordinal: records.length, query: q, queryHash: fingerprint('e2-monthly-query-v1', q), retry, waitMs: pause,
      status: null, code: null, received: 0, raw: null, startMs: now() - start, endMs: null };
    accounting.attempts++; if (retry) accounting.retries++; records.push(record); lastStart = now(); checkpoint();
    const onChunk = n => { uint(n, 'NETWORK_ERROR'); record.received += n; accounting.received += n; checkpoint();
      need(record.received <= LIMITS.response, 'RESPONSE_LIMIT'); need(accounting.received <= LIMITS.received && prior.received + accounting.received <= TOTAL.received, 'RECEIVED_LIMIT'); };
    try { let response; try { response = await (deps.transport ?? send)(q, { onChunk, deadline: LIMITS.deadline }); }
      catch (e) { throw Error(safe(e, 'NETWORK_ERROR')); } finally { record.endMs = now() - start; }
      need(response && typeof response === 'object', 'NETWORK_ERROR'); const received = uint(response.received ?? response.bytes?.length ?? record.received, 'NETWORK_ERROR');
      if (record.received < received) onChunk(received - record.received); need(record.received === received, 'NETWORK_ERROR'); record.status = response.status ?? null;
      need(now() - start <= LIMITS.sourceMs, 'TIME_LIMIT'); if (response.code) throw Error(safe({ message: response.code }, 'NETWORK_ERROR'));
      if (record.status === 529) { const s = response.retryAfter;
        record.retryAfterSeconds = typeof s === 'string' && /^(0|[1-9][0-9]*)$/.test(s) && Number.isSafeInteger(Number(s)) ? Number(s) : null;
        record.code = 'HTTP_ERROR'; if (retry === 2) throw Error('HTTP_529_EXHAUSTED'); continue; }
      need(record.status === 200, record.status === 204 ? 'NO_PROGRESS' : 'HTTP_ERROR');
      need(Buffer.isBuffer(response.bytes) && response.bytes.length === record.received, 'PARTIAL_RESPONSE');
      const a = admit(response.bytes, q, state.month), p = prepare(state, a);
      need(accounting.retained + response.bytes.length + state.creationBytes + p.bytes + LIMITS.metadata <= LIMITS.retained, 'DISK_LIMIT');
      const name = String(record.ordinal + 1).padStart(5, '0') + '.raw'; write(name, response.bytes);
      record.raw = name; record.rawHash = digest(response.bytes); record.rawBytes = response.bytes.length; apply(state, a, p); return;
    } catch (e) { record.code = safe(e, 'NETWORK_ERROR'); throw e; }
    finally { recordBytes += json(record).length + 256; }
  } }
  try {
    need(Object.keys(options).every(k => ['enabled', 'month', 'retainedBefore', 'attemptsBefore', 'retriesBefore'].includes(k)), 'ARGUMENTS_INVALID');
    const i = indexOf(options.month), retainedBefore = scalar(options.retainedBefore), attemptsBefore = scalar(options.attemptsBefore), retriesBefore = scalar(options.retriesBefore);
    config = configuration(options.month); state = initial(options.month);
    prior = priorTotals(options.month, (deps.previous ?? previous)(options.month), attemptsBefore, retriesBefore);
    const reservation = (3 - i) * LIMITS.retained; need(retainedBefore + reservation <= LIMITS.aggregate, 'DISK_LIMIT');
    need(utcNow() <= Date.parse('2026-10-10T23:59:59.999Z') && utcNow() < Date.parse(EXPIRY), 'EXPIRY');
    const proof = (deps.verifyBoundary ?? verifyBoundary)(); need(proof.status === 'VERIFIED' && canonical(proof.bounds) === canonical(BOUNDS));
    lineage = { ...sourceIdentity(deps), proof: { status: proof.status, bounds: proof.bounds, manifestHash: SEAL.manifestHash, controlBytes: 880 }, configHash: fingerprint(config.canonicalVersion, config),
      priorAccounting: { source: 'MAIN_ATTESTED_AND_FINALIZED_MONTHLY_METADATA', bytes: retainedBefore, ...prior } };
    store = deps.store ?? fileStore(options.month); need(store.absent(), 'ROOT_EXISTS');
    need(uint(store.free(), 'FREE_SPACE_LIMIT') - reservation >= LIMITS.free, 'FREE_SPACE_LIMIT'); store.create(); created = true;
    write('attempt.json', json({ ...FLAGS, month: options.month, startedAt: new Date(utcNow()).toISOString(), lineage, expiry: EXPIRY,
      interruptedDisposition: 'No final manifest is INCOMPLETE; resume forbidden' }));
    while (state.current <= state.end) await request(query(options.month, state.current));
  } catch (e) { code = safe(e); }
  if (!created) return { ...error(code ?? 'INTEGRITY_ERROR'), accounting };
  let result = summary(state, code, lineage), hash = summaryHash(result);
  try {
    const creations = Buffer.concat([...state.creations.values()].sort((a, b) => compare(a.value, b.value)).map(c => c.bytes));
    need(creations.length === state.creationBytes && creations.length <= LIMITS.creationBytes, 'CREATION_LIMIT'); write('creations.jsonl', creations);
    const sb = json(result); need(sb.length <= LIMITS.summary, 'METADATA_LIMIT'); write('summary.json', sb);
    const publicationOperational = { elapsedMs: now() - start, endedAt: new Date(utcNow()).toISOString() };
    const m = { ...FLAGS, version: config.version, configuration: config, lineage, records, files: [...files], code, status: result.status,
      summaryHash: hash, accounting: { ...accounting, manifestBytes: 0 }, publicationOperational,
      runBudget: now() - start > LIMITS.totalMs ? 'OVERRUN' : 'UNMEASURED' };
    let bytes = json(m), settled = false; for (let i = 0; i < 16; i++) { m.accounting.retained = accounting.retained + bytes.length;
      m.accounting.manifestBytes = bytes.length; const next = json(m); if (next.length === bytes.length) { bytes = next; settled = true; break; } bytes = next; }
    need(settled); accounting.manifestBytes = bytes.length; write('manifest.json', bytes);
  } catch (e) { code = safe(e, 'STORAGE_ERROR'); result = summary(state, code, lineage); hash = summaryHash(result); }
  return { ...FLAGS, code, status: code ? 'INCOMPLETE' : 'SCAN_COMPLETE', summary: result, summaryHash: hash, accounting,
    diagnostics: { serializedCreations: state.serializedCreations }, runBudget: now() - start > LIMITS.totalMs ? 'OVERRUN' : 'UNMEASURED', preflight: false };
}
async function replay(month, suppliedStore) { try {
  const config = configuration(month), store = suppliedStore ?? fileStore(month), manifestBytes = store.read('manifest.json');
  need(manifestBytes.length <= LIMITS.metadata); const m = parse(manifestBytes, false);
  need(canonical(m.configuration) === canonical(config) && m.version === config.version && canonical(Object.fromEntries(Object.keys(FLAGS).map(k => [k, m[k]]))) === canonical(FLAGS));
  need(m.lineage.configHash === fingerprint(config.canonicalVersion, config) && m.lineage.proof.status === 'VERIFIED'
    && canonical(m.lineage.proof) === canonical({ status: 'VERIFIED', bounds: BOUNDS, manifestHash: SEAL.manifestHash, controlBytes: 880 })); verifyPins();
  need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(m.lineage.runtime) && /^[a-f0-9]{40}$/.test(m.lineage.source.commit) && typeof m.lineage.source.dirty === 'boolean');
  for (const [name, hash] of Object.entries(sourceIdentity({ runtime: m.lineage.runtime, source: m.lineage.source }).scripts)) need(m.lineage.scripts[name] === hash);
  need(Array.isArray(m.records) && m.records.length <= LIMITS.attempts && Array.isArray(m.files) && m.files.length <= LIMITS.attempts + 3);
  let retained = manifestBytes.length, metadata = manifestBytes.length + LIMITS.stdout; const files = new Map();
  for (const f of m.files) { need(!files.has(f.name) && (['attempt.json', 'summary.json', 'creations.jsonl'].includes(f.name) || /^[0-9]{5}\.raw$/.test(f.name)));
    const b = store.read(f.name); need(!f.partial && b.length === f.bytes && digest(b) === f.hash); retained += b.length;
    if (!f.name.endsWith('.raw') && f.name !== 'creations.jsonl') metadata += b.length; files.set(f.name, { ...f, data: b }); }
  need(retained <= LIMITS.retained && metadata <= LIMITS.metadata && retained === m.accounting.retained && manifestBytes.length === m.accounting.manifestBytes && m.accounting.retainedKnown === true);
  need(canonical(store.list().sort()) === canonical([...files.keys(), 'manifest.json'].sort()));
  const attempt = parse(files.get('attempt.json')?.data, false); need(attempt.month === month && attempt.expiry === EXPIRY && canonical(attempt.lineage) === canonical(m.lineage));
  const prior = m.lineage.priorAccounting; need(prior.source === 'MAIN_ATTESTED_AND_FINALIZED_MONTHLY_METADATA');
  for (const k of Object.keys(TOTAL)) need(uint(prior[k]) + (k === 'elapsedMs' ? uint(m.publicationOperational.elapsedMs) : uint(m.accounting[k])) <= TOTAL[k]);
  need(uint(prior.bytes) + (3 - indexOf(month)) * LIMITS.retained <= LIMITS.aggregate);
  const state = initial(month); let received = 0, retries = 0, prev = null; const referenced = new Set();
  for (const [i, r] of m.records.entries()) {
    const rejectedOverflow = uint(r.received) > LIMITS.response && i === m.records.length - 1
      && r.raw === null && !Object.hasOwn(r, 'rawHash') && !Object.hasOwn(r, 'rawBytes')
      && r.code === 'RESPONSE_LIMIT' && m.code === 'RESPONSE_LIMIT' && m.status === 'INCOMPLETE';
    need(r.ordinal === i && (r.received <= LIMITS.response || rejectedOverflow)
      && uint(r.retry) <= 2 && uint(r.waitMs) >= 0 && uint(r.startMs) <= uint(r.endMs));
    received += r.received; need(received <= LIMITS.received && r.queryHash === fingerprint('e2-monthly-query-v1', r.query));
    if (prev) need(r.startMs >= prev.endMs && r.startMs - prev.startMs >= LIMITS.spacing);
    if (r.retry) { need(prev?.status === 529 && prev.code === 'HTTP_ERROR' && !prev.raw && r.retry === prev.retry + 1
      && canonical(r.query) === canonical(prev.query));
      need(r.waitMs >= Math.max(r.retry === 1 ? 15000 : 45000, (prev.retryAfterSeconds ?? 0) * 1000)
        && r.startMs - prev.endMs >= r.waitMs); retries++; }
    else need(!prev || prev.raw);
    need(canonical(r.query) === canonical(query(month, state.current)));
    if (r.raw) { need(r.raw === String(i + 1).padStart(5, '0') + '.raw' && !referenced.has(r.raw) && r.code === null && r.status === 200);
      const f = files.get(r.raw); need(f && f.hash === r.rawHash && f.bytes === r.rawBytes && f.bytes === r.received); referenced.add(r.raw);
      const a = admit(f.data, r.query, month); apply(state, a, prepare(state, a));
    } else { need(CODES.has(r.code)); if (i < m.records.length - 1) need(r.status === 529 && r.retry < 2 && m.records[i + 1].retry === r.retry + 1);
      if (r.status === 529 && r.retryAfterSeconds !== null) uint(r.retryAfterSeconds); }
    prev = r;
  }
  need([...files.keys()].filter(k => k.endsWith('.raw')).length === referenced.size && retries <= LIMITS.retries);
  need(m.accounting.attempts === m.records.length && m.accounting.retries === retries && m.accounting.received === received);
  need(m.code === null || CODES.has(m.code)); if (m.code === null) need(state.current > state.end); else need(m.status === 'INCOMPLETE');
  if (prev && !prev.raw) need(m.code !== null && (prev.code === m.code || (prev.status === 529 && prev.code === 'HTTP_ERROR')));
  const s = summary(state, m.code, m.lineage), hash = summaryHash(s); need(m.status === s.status && m.summaryHash === hash && files.get('summary.json')?.data.equals(json(s)));
  const cb = Buffer.concat([...state.creations.values()].sort((a, b) => compare(a.value, b.value)).map(c => c.bytes)); need(files.get('creations.jsonl')?.data.equals(cb));
  return { ...FLAGS, code: null, recordedSourceCode: m.code, status: s.status, summary: s, summaryHash: hash, manifestHash: digest(manifestBytes), runBudget: 'UNMEASURED', preflight: false };
} catch { return error('INTEGRITY_ERROR'); } }
function exitCode(r) { if (r.preflight || r.code === 'INTEGRITY_ERROR' || r.runBudget === 'OVERRUN') return 1;
  return r.code === null && r.status === 'SCAN_COMPLETE' ? 0 : r.status === 'INCOMPLETE' ? 2 : 1; }
module.exports = { MONTHS, BOUNDS, LIMITS, TOTAL, SEAL, PINS, configuration, output, query, admit, verifyBoundary, checkBudget,
  fileStore, initial, prepare, apply, priorTotals, run, replay, error, exitCode };
