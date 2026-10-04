'use strict';
const c = require('./e2-monthly-census.cjs');
const g = require('./e2-monthly-wait-guard-v2.cjs');
const fs = require('node:fs'), path = require('node:path'), { execFileSync } = require('node:child_process');
const { parse, canonical, digest, fingerprint, sendBounded } = require('./exploratory-probe.cjs');
const VERSION = 'e2-monthly-tail-v1', HARD = Date.parse('2026-10-04T13:16:47.750Z');
const BASE = { attempts: 40050, retries: 615, received: 444036897, retained: 616843252, physical: 4575291338, elapsedMs: 36006712 };
const LIMITS = { attempts: 1500, retries: 45, response: 16000000, received: 100000000, retained: 120000000,
  metadata: 8000000, summary: 1000000, stdout: 1000000, creationBytes: 64000000, creations: 100000,
  instructions: 100000, headers: 100000, diagnostics: 1000, timeout: 60000, spacing: 250 };
const BRANCH = { may: { start: 422954347, end: 423478906, sourceMs: 1500000, totalMs: 1800000,
  manifest: 'sha256:0238c30cebbfed7502b094590595f4866363f3d23ca0c4bd922a38cb18a63395', summary: 'sha256:804ba236db721757fb1767a3749001baaedca41351711b2e32db9ad757093d0f' },
  june: { start: 429325106, end: 429340000, sourceMs: 180000, totalMs: 300000,
    manifest: 'sha256:34742b956c883ca3af487d7ff4cc14e0b9edae68fbe58fe4f9c6c539bda2fa69', summary: 'sha256:28424a78970b3fe4d86c8a3c255d7ef5ce33565fddccbc25cbbe440c5d2cbfe8' } };
const FLAGS = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, cohortAdmitted: false, authoritativeCensusComplete: false, fullD1UpperBound: null, fullD1Fits: null };
const CODES = new Set(['DISABLED', 'ARGUMENTS_INVALID', 'INTEGRITY_ERROR', 'ORDER_INVALID', 'UNSAFE_PATH', 'ROOT_EXISTS', 'TIME_LIMIT', 'EXPIRY', 'ATTEMPT_LIMIT', 'RETRY_LIMIT', 'RECEIVED_LIMIT', 'DISK_LIMIT', 'FREE_SPACE_LIMIT', 'METADATA_LIMIT', 'CREATION_LIMIT', 'INSTRUCTION_LIMIT', 'HEADER_LIMIT', 'HTTP_ERROR', 'HTTP_RETRY_EXHAUSTED', 'RETRY_AFTER_INVALID', 'NETWORK_ERROR', 'TIMEOUT', 'PARTIAL_RESPONSE', 'RESPONSE_LIMIT', 'RESPONSE_INVALID', 'NO_PROGRESS', 'IMMUTABLE_CONFLICT', 'STORAGE_ERROR']);
const FATAL = new Set(['DISABLED', 'ARGUMENTS_INVALID', 'INTEGRITY_ERROR', 'ORDER_INVALID', 'UNSAFE_PATH', 'ROOT_EXISTS']);
const validSourceCode = code => code === null || CODES.has(code) && !FATAL.has(code);
const need = (v, code = 'INTEGRITY_ERROR') => { if (!v) throw Error(code); };
const uint = n => { need(Number.isSafeInteger(n) && n >= 0); return n; };
const scalar = n => { need(typeof n === 'string' && /^(0|[1-9][0-9]*)$/.test(n), 'ARGUMENTS_INVALID'); return uint(Number(n)); };
const json = x => Buffer.from(JSON.stringify(x) + '\n');
const safe = e => CODES.has(e?.message) ? e.message : 'INTEGRITY_ERROR';
const error = code => ({ ...FLAGS, code, status: 'INCOMPLETE', preflight: true, runBudget: 'UNMEASURED' });
function configuration(month) { need(Object.hasOwn(BRANCH, month), 'ARGUMENTS_INVALID'); return { version: VERSION, canonicalVersion: VERSION + '-c14n', month,
  ...BRANCH[month], output: 'C:\\crypto-research-evidence\\r1-e2\\exploratory-monthly-' + month + '-tail-v1', limits: LIMITS, original: c.configuration(month),
  retryPolicy: 'HTTP503_OR529_ONLY_MAX2_OBSERVED15_45_RETRY_AFTER', hardDeadline: '2026-10-04T13:16:47.7502711Z', closingReserveMs: 1200000 }; }
function retryEligible(r) { return r.code === 'HTTP_ERROR' && [503, 529].includes(r.status); }
function reserve(a, prior, elapsed = 0, pause = 0, retry = false, month = 'may', creationBytes = 0, metadata = 0) {
  const b = configuration(month); need(a.attempts < LIMITS.attempts && prior.attempts + a.attempts < c.TOTAL.attempts, 'ATTEMPT_LIMIT');
  if (retry) need(a.retries < LIMITS.retries && prior.retries + a.retries < c.TOTAL.retries, 'RETRY_LIMIT');
  need(a.received + LIMITS.response <= LIMITS.received && prior.received + a.received + LIMITS.response <= c.TOTAL.received, 'RECEIVED_LIMIT');
  const bytes = LIMITS.response + creationBytes + LIMITS.metadata;
  need(a.retained + bytes <= LIMITS.retained && prior.retained + a.retained + bytes <= c.TOTAL.retained, 'DISK_LIMIT');
  need(metadata + 131072 + LIMITS.summary + LIMITS.stdout <= LIMITS.metadata, 'METADATA_LIMIT');
  need(elapsed + pause + LIMITS.timeout <= b.sourceMs && prior.elapsedMs + elapsed + pause + LIMITS.timeout <= c.TOTAL.elapsedMs, 'TIME_LIMIT'); return bytes;
}
function compare(a, b) { let n = a.slot - b.slot || a.transactionIndex - b.transactionIndex; if (n) return n;
  for (let i = 0; i < Math.min(a.instructionPath.length, b.instructionPath.length); i++) if (a.instructionPath[i] !== b.instructionPath[i]) return a.instructionPath[i] - b.instructionPath[i];
  return a.instructionPath.length - b.instructionPath.length || (a.signature < b.signature ? -1 : a.signature > b.signature ? 1 : 0); }
function initial(month) { const state = c.initial(month); state.current = state.start = configuration(month).start; return state; }
function summary(state, code, lineage) { const { headers, creations, serializedCreations, ...stream } = state;
  return { ...FLAGS, version: VERSION, month: state.month, code, status: code ? 'INCOMPLETE' : 'SCAN_COMPLETE', stream, creations: creations.size,
    storedHeaders: headers.size, lineage, coverage: 'SEPARATELY_REFERENCED_MISSING_REFERENCE_BOUNDARY_ONLY', prefixDisposition: 'IMMUTABLE_INCOMPLETE', retention: 'UNVERIFIED', expiry: c.configuration(state.month).expiry }; }
function ancestors(file) { let dir = path.dirname(file); while (true) { if (fs.existsSync(dir)) { const s = fs.lstatSync(dir);
  need(s.isDirectory() && !s.isSymbolicLink() && fs.realpathSync.native(dir).toLowerCase() === dir.toLowerCase(), 'UNSAFE_PATH'); }
  const p = path.dirname(dir); if (p === dir) break; dir = p; } }
function readFile(file, cap) { ancestors(file); const s = fs.lstatSync(file); need(s.isFile() && !s.isSymbolicLink() && uint(s.size) <= cap, 'UNSAFE_PATH');
  const fd = fs.openSync(file, 'r'); try { const stat = fs.fstatSync(fd); need(stat.size === s.size && stat.ino === s.ino && fs.realpathSync.native(file).toLowerCase() === file.toLowerCase(), 'UNSAFE_PATH');
    const b = Buffer.alloc(s.size + 1); let at = 0; while (at < b.length) { const n = fs.readSync(fd, b, at, b.length - at, null); if (!n) break; at += n; } need(at === s.size); return b.subarray(0, at);
  } finally { fs.closeSync(fd); } }
function fileStore(month) { const root = configuration(month).output, check = () => ancestors(path.join(root, 'manifest.json'));
  const name = n => { need(['manifest.json', 'summary.json', 'attempt.json', 'creations.jsonl'].includes(n) || /^[0-9]{5}\.raw$/.test(n), 'UNSAFE_PATH'); check(); return path.join(root, n); };
  return { absent() { check(); return !fs.existsSync(root); }, free() { check(); let dir = root; while (!fs.existsSync(dir)) dir = path.dirname(dir); const s = fs.statfsSync(dir, { bigint: true }); return Number(s.bavail * s.bsize); },
    create() { check(); need(!fs.existsSync(root), 'ROOT_EXISTS'); fs.mkdirSync(root); },
    write(n, b) { const fd = fs.openSync(name(n), 'wx'); try { let at = 0; while (at < b.length) { const k = fs.writeSync(fd, b, at, b.length - at); need(k > 0, 'STORAGE_ERROR'); at += k; } fs.fsyncSync(fd); } finally { fs.closeSync(fd); } },
    read: n => readFile(name(n), n.endsWith('.raw') ? LIMITS.response : n === 'creations.jsonl' ? LIMITS.creationBytes : LIMITS.metadata),
    size: n => fs.lstatSync(name(n)).size, list() { check(); return fs.readdirSync(root); } };
}
function identity() { const guard = g.identity(); return { frozen: { ...g.PINS, ...guard.scripts }, guard, tail: digest(readFile(__filename, 1000000)) }; }
function verifyPrefix(month, m, sb, mb) {
  const b = BRANCH[month], s = parse(sb, false), last = m.records.at(-1);
  need(digest(mb) === b.manifest && digest(sb) === b.summary && canonical(m.configuration) === canonical(c.configuration(month)));
  need(s.stream.lastScanned === b.start - 1 && s.stream.current === b.start && s.stream.end === b.end && s.status === 'INCOMPLETE' && m.status === 'INCOMPLETE' && m.code === 'HTTP_ERROR');
  need(last.status === 503 && last.code === 'HTTP_ERROR' && last.retry === 0 && last.raw === null && canonical(last.query) === canonical(c.query(month, b.start)));
}
function verifyTailMetadata(month, store) {
  const mb = store.read('manifest.json'), sb = store.read('summary.json'); need(mb.length + sb.length <= LIMITS.metadata); const m = parse(mb, false), s = parse(sb, false);
  need(canonical(m.configuration) === canonical(configuration(month)) && canonical(m.lineage.scripts) === canonical(identity()) && m.accounting.retainedKnown === true);
  need(m.summaryHash === fingerprint(configuration(month).canonicalVersion, s) && canonical(s.lineage) === canonical(m.lineage) && s.status === m.status && s.code === m.code);
  need(m.files.some(f => f.name === 'summary.json' && f.bytes === sb.length && f.hash === digest(sb)) && new Set(m.files.map(f => f.name)).size === m.files.length);
  need(m.accounting.manifestBytes === mb.length && m.accounting.retained === mb.length + m.files.reduce((n, f) => n + uint(f.bytes), 0) && m.accounting.attempts === m.records.length);
  need(m.accounting.received === m.records.reduce((n, r) => n + uint(r.received), 0) && m.accounting.retries === m.records.filter(r => r.retry > 0).length);
  for (const k of ['attempts', 'retries', 'received', 'retained']) need(uint(m.accounting[k]) <= LIMITS[k]);
  need(validSourceCode(m.code)); need(['SCAN_COMPLETE', 'INCOMPLETE'].includes(m.status) && m.runBudget !== 'OVERRUN'); return { manifest: m, manifestHash: digest(mb), summaryHash: digest(sb) };
}
function proof(month, readStore = c.fileStore, tailStore = fileStore) {
  let bytes = 0; const manifests = ['april', 'may', 'june'].map(m => { const store = readStore(m), mb = store.read('manifest.json'), sb = store.read('summary.json'); bytes += mb.length + sb.length; need(bytes <= 64000000);
    const v = g.verifyMetadata(m, { read: n => n === 'manifest.json' ? mb : sb }); if (m !== 'april') verifyPrefix(m, v, sb, mb); return v; });
  const totals = { attempts: 0, retries: 0, received: 0, retained: 0, elapsedMs: 0 };
  for (const m of manifests) for (const k of Object.keys(totals)) totals[k] += uint(k === 'elapsedMs' ? m.publicationOperational.elapsedMs : m.accounting[k]);
  need(totals.attempts === BASE.attempts && totals.retries === BASE.retries && totals.received === BASE.received);
  let preceding = null; if (month === 'june') { preceding = verifyTailMetadata('may', tailStore('may')); for (const k of ['attempts', 'retries', 'received', 'retained']) totals[k] += preceding.manifest.accounting[k]; }
  return { totals, precedingRetained: preceding?.manifest.accounting.retained ?? 0, precedingElapsed: preceding?.manifest.publication.elapsedMs ?? 0,
    prefixes: manifests.map((m, i) => ({ month: c.MONTHS[i], manifestHash: i ? BRANCH[c.MONTHS[i]].manifest : g.APRIL.manifestHash, summaryHash: i ? BRANCH[c.MONTHS[i]].summary : g.APRIL.summaryHash })), preceding: preceding && { manifestHash: preceding.manifestHash, summaryHash: preceding.summaryHash } };
}
const fields = ['retainedBefore', 'newRetainedBefore', 'attemptsBefore', 'retriesBefore', 'elapsedBefore'];
function valid(o) { return o.enabled === true && Object.hasOwn(BRANCH, o.month) && Object.keys(o).every(k => ['enabled', 'month', ...fields].includes(k)) && fields.every(k => typeof o[k] === 'string' && /^(0|[1-9][0-9]*)$/.test(o[k]) && Number.isSafeInteger(Number(o[k]))); }
function accountingInput(o, p, utc) {
  const v = Object.fromEntries(fields.map(k => [k, scalar(o[k])])); need(v.attemptsBefore === p.totals.attempts && v.retriesBefore === p.totals.retries, 'ORDER_INVALID');
  const precedingRetained = uint(p.precedingRetained ?? 0), precedingElapsed = uint(p.precedingElapsed ?? 0);
  need(v.newRetainedBefore >= Math.max(BASE.retained + precedingRetained, p.totals.retained) && v.retainedBefore >= Math.max(BASE.physical + precedingRetained, v.newRetainedBefore) && v.elapsedBefore >= BASE.elapsedMs + precedingElapsed, 'ORDER_INVALID');
  const count = o.month === 'may' ? 2 : 1, time = o.month === 'may' ? 2100000 : 300000;
  need(v.retainedBefore + count * LIMITS.retained <= c.LIMITS.aggregate && v.newRetainedBefore + count * LIMITS.retained <= c.TOTAL.retained, 'DISK_LIMIT');
  need(p.totals.attempts + count * LIMITS.attempts <= c.TOTAL.attempts && p.totals.retries + count * LIMITS.retries <= c.TOTAL.retries && p.totals.received + count * LIMITS.received <= c.TOTAL.received, 'ORDER_INVALID');
  need(v.elapsedBefore + time + 1200000 <= c.TOTAL.elapsedMs && utc + time + 1200000 <= HARD && utc <= Date.parse('2026-10-10T23:59:59.999Z') && utc < Date.parse(c.configuration(o.month).expiry), 'TIME_LIMIT');
  return { ...p.totals, retained: v.newRetainedBefore, elapsedMs: v.elapsedBefore, physical: v.retainedBefore };
}
function prepareRecord(state, r, raw) { const a = c.admit(raw, r.query, state.month); const p = c.prepare(state, a);
  need(state.creations.size + p.creations.size <= LIMITS.creations && state.creationBytes + p.bytes <= LIMITS.creationBytes, 'CREATION_LIMIT');
  need(state.headers.size + p.headers.size <= LIMITS.headers, 'HEADER_LIMIT'); need(state.instructions + a.counts.instructions <= LIMITS.instructions, 'INSTRUCTION_LIMIT');
  need(Object.keys(p.depth).length + Object.keys(p.reasons).length <= LIMITS.diagnostics, 'INSTRUCTION_LIMIT'); return { a, p }; }
function reduceRecord(state, r, raw) { const prepared = prepareRecord(state, r, raw); c.apply(state, prepared.a, prepared.p); }
async function run(o = {}, deps = {}) {
  if (!o.enabled) return error('DISABLED'); if (!valid(o)) return error('ARGUMENTS_INVALID');
  let store, created = false, state, lineage, config, prior, adapter, started, code = null, metadata = 0, recordBytes = 0;
  const rawClock = deps.now ?? Date.now, launched = rawClock();
  const records = [], files = [], a = { attempts: 0, retries: 0, received: 0, retained: 0, retainedKnown: true, cashMicrousd: '0', checkpoints: [] };
  const utc = deps.utcNow ?? Date.now, sleeper = deps.wait ?? (ms => new Promise(r => setTimeout(r, ms)));
  const launchedUtc = utc();
  function checkpoint() { for (const k of ['attempts', 'retries', 'received', 'retained']) if ((a[k] * 5 >= LIMITS[k] * 4 || prior && (prior[k] + a[k]) * 5 >= c.TOTAL[k] * 4) && !a.checkpoints.includes(k)) a.checkpoints.push(k); }
  function clock() { const n = adapter.now(); need(!adapter.failed(), 'TIME_LIMIT'); return n; }
  function room(pause, retry) { const elapsed = clock() - started, bytes = reserve(a, prior, elapsed, pause, retry, o.month, state.creationBytes, metadata + recordBytes);
    need(utc() + pause + LIMITS.timeout + 1200000 <= HARD, 'TIME_LIMIT'); need(uint(store.free()) - bytes >= c.LIMITS.free, 'FREE_SPACE_LIMIT'); reserve(a, prior, clock() - started, pause, retry, o.month, state.creationBytes, metadata + recordBytes); need(utc() + pause + LIMITS.timeout + 1200000 <= HARD, 'TIME_LIMIT'); }
  function write(n, b) { need(a.retained + b.length <= LIMITS.retained, 'DISK_LIMIT'); const meta = !n.endsWith('.raw') && n !== 'creations.jsonl';
    if (meta) need(metadata + b.length + LIMITS.stdout <= LIMITS.metadata, 'METADATA_LIMIT');
    try { store.write(n, b); a.retained += b.length; if (meta) metadata += b.length; files.push({ name: n, bytes: b.length, hash: digest(b) }); checkpoint(); }
    catch { a.retainedKnown = false; throw Error('STORAGE_ERROR'); } }
  try {
    config = configuration(o.month); const scripts = identity(), p = (deps.proof ?? proof)(o.month); prior = accountingInput(o, p, utc());
    const source = deps.source ?? { commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: path.resolve(__dirname, '../../..'), encoding: 'utf8', timeout: 5000 }).trim(), dirty: !!execFileSync('git', ['status', '--porcelain'], { cwd: path.resolve(__dirname, '../../..'), encoding: 'utf8', timeout: 5000 }).trim() };
    need(/^[a-f0-9]{40}$/.test(source.commit) && typeof source.dirty === 'boolean'); const runtime = deps.runtime ?? process.version; need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(runtime));
    lineage = { scripts, source, runtime, startedUtc: uint(launchedUtc), configHash: fingerprint(config.canonicalVersion, config), prefixes: p.prefixes, preceding: p.preceding, prior, allocation: Object.fromEntries(fields.map(k => [k, o[k]])) };
    state = initial(o.month); store = deps.store ?? fileStore(o.month); need(store.absent(), 'ROOT_EXISTS'); need(uint(store.free()) - (o.month === 'may' ? 2 : 1) * LIMITS.retained >= c.LIMITS.free, 'FREE_SPACE_LIMIT');
    adapter = g.checkedClockAndWait(rawClock, async ms => { room(ms, !!records.at(-1) && !records.at(-1).raw); await sleeper(ms); }, prior.elapsedMs); started = uint(launched); need(clock() >= started, 'TIME_LIMIT');
    store.create(); created = true; write('attempt.json', json({ ...FLAGS, version: VERSION, month: o.month, lineage, expiry: config.original.expiry }));
    while (state.current <= state.end) {
      const q = c.query(o.month, state.current); let admitted = false;
      for (let retry = 0; retry <= 2; retry++) {
        const prev = records.at(-1), pause = retry ? Math.max(retry === 1 ? 15000 : 45000, (prev.retryAfterSeconds ?? 0) * 1000) : prev ? Math.max(0, LIMITS.spacing - (clock() - started - prev.startMs)) : 0;
        room(pause, retry); if (pause) await adapter.wait(pause); room(0, retry);
        const r = { ordinal: records.length, query: q, queryHash: fingerprint(VERSION + '-query', q), retry, waitMs: pause, status: null, code: null, received: 0, raw: null, startMs: clock() - started, endMs: null };
        records.push(r); a.attempts++; if (retry) a.retries++; checkpoint();
        const onChunk = n => { r.received += uint(n); a.received += n; checkpoint(); need(r.received <= LIMITS.response, 'RESPONSE_LIMIT'); need(a.received <= LIMITS.received && prior.received + a.received <= c.TOTAL.received, 'RECEIVED_LIMIT'); };
        try {
          const response = await (deps.transport ?? ((query, opts) => sendBounded(query, { ...opts, deadlineMs: LIMITS.timeout, responseLimit: LIMITS.response, retryMetadata: true }, { response: LIMITS.response, deadline: LIMITS.timeout })))(q, { onChunk });
          r.endMs = clock() - started; const received = uint(response.received ?? response.bytes?.length ?? r.received); if (r.received < received) onChunk(received - r.received); need(r.received === received); r.status = response.status ?? null;
          need(r.received <= LIMITS.response, 'RESPONSE_LIMIT'); need(a.received <= LIMITS.received && prior.received + a.received <= c.TOTAL.received, 'RECEIVED_LIMIT');
          need(clock() - started <= config.sourceMs && utc() + 1200000 <= HARD, 'TIME_LIMIT');
          const responseCode = response.code ?? (r.status === 200 ? null : 'HTTP_ERROR');
          if (retryEligible({ status: r.status, code: responseCode })) {
            need(!response.retryAfterInvalid && (response.retryAfter === undefined || typeof response.retryAfter === 'string' && /^(0|[1-9][0-9]*)$/.test(response.retryAfter) && Number.isSafeInteger(Number(response.retryAfter)) && Number.isSafeInteger(Number(response.retryAfter) * 1000)), 'RETRY_AFTER_INVALID');
            r.retryAfterSeconds = response.retryAfter === undefined ? null : Number(response.retryAfter); r.code = 'HTTP_ERROR';
            if (retry === 2) throw Error('HTTP_RETRY_EXHAUSTED'); continue;
          }
          if (responseCode) throw Error(CODES.has(responseCode) ? responseCode : 'NETWORK_ERROR'); need(r.status === 200 && Buffer.isBuffer(response.bytes) && response.bytes.length === r.received, 'PARTIAL_RESPONSE');
          const prepared = prepareRecord(state, r, response.bytes);
          need(a.retained + response.bytes.length + state.creationBytes + prepared.p.bytes + LIMITS.metadata <= LIMITS.retained, 'DISK_LIMIT');
          const n = String(r.ordinal + 1).padStart(5, '0') + '.raw'; write(n, response.bytes); r.raw = n; r.rawBytes = response.bytes.length; r.rawHash = digest(response.bytes); c.apply(state, prepared.a, prepared.p); admitted = true; break;
        } catch (e) { r.endMs ??= adapter.now() - started; r.code = safe(e); throw e; }
        finally { recordBytes += json(r).length + 128; }
      }
      need(admitted);
    }
  } catch (e) { code = safe(e); }
  if (!created) return { ...error(code ?? 'INTEGRITY_ERROR'), accounting: a };
  let s = summary(state, code, lineage), hash = fingerprint(config.canonicalVersion, s);
  try {
    const cb = Buffer.concat([...state.creations.values()].sort((a, b) => compare(a.value, b.value)).map(v => v.bytes)); write('creations.jsonl', cb); const sb = json(s); need(sb.length <= LIMITS.summary, 'METADATA_LIMIT'); write('summary.json', sb);
    const elapsedMs = adapter.now() - started; need(elapsedMs <= config.totalMs && prior.elapsedMs + elapsedMs <= c.TOTAL.elapsedMs && utc() <= HARD, 'TIME_LIMIT');
    const m = { ...FLAGS, version: VERSION, configuration: config, lineage, records, files, code, status: s.status, summaryHash: hash, accounting: { ...a, manifestBytes: 0 }, publication: { elapsedMs }, runBudget: 'UNMEASURED' };
    let b = json(m), settled = false; for (let i = 0; i < 16; i++) { m.accounting.retained = a.retained + b.length; m.accounting.manifestBytes = b.length; const next = json(m); if (next.length === b.length) { b = next; settled = true; break; } b = next; }
    need(settled); write('manifest.json', b);
  } catch (e) { code = safe(e); s = summary(state, code, lineage); hash = fingerprint(config.canonicalVersion, s); }
  return { ...FLAGS, code, status: code ? 'INCOMPLETE' : 'SCAN_COMPLETE', summary: s, summaryHash: hash, accounting: a, preflight: false, runBudget: 'UNMEASURED' };
}
async function replay(month, suppliedStore, deps = {}) {
  try {
    const config = configuration(month), store = suppliedStore ?? fileStore(month), began = (deps.now ?? Date.now)(), utc = deps.utcNow ?? Date.now;
    let sourceStart = null, priorElapsed = 0, publicationElapsed = 0, lastClock = began;
    const budget = () => { const n = (deps.now ?? Date.now)(); need(Number.isSafeInteger(n) && n >= lastClock && n - began <= config.totalMs && utc() <= HARD, 'TIME_LIMIT'); lastClock = n;
      if (sourceStart !== null) need(utc() <= sourceStart + config.totalMs && priorElapsed + publicationElapsed + n - began <= c.TOTAL.elapsedMs, 'TIME_LIMIT'); };
    budget(); const names = store.list(); need(names.length <= 1504 && new Set(names).size === names.length); let inputBytes = 0;
    for (const n of names) { need(['manifest.json', 'summary.json', 'attempt.json', 'creations.jsonl'].includes(n) || /^[0-9]{5}\.raw$/.test(n)); inputBytes += uint(store.size(n)); need(inputBytes <= LIMITS.retained); }
    const mb = store.read('manifest.json'); need(mb.length <= LIMITS.metadata); const m = parse(mb, false);
    need(m.version === VERSION && canonical(m.configuration) === canonical(config) && canonical(m.lineage.scripts) === canonical(identity()) && m.accounting.retainedKnown === true && m.runBudget === 'UNMEASURED');
    need(canonical(Object.fromEntries(Object.keys(FLAGS).map(k => [k, m[k]]))) === canonical(FLAGS) && m.lineage.configHash === fingerprint(config.canonicalVersion, config));
    need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(m.lineage.runtime) && /^[a-f0-9]{40}$/.test(m.lineage.source.commit) && typeof m.lineage.source.dirty === 'boolean');
    sourceStart = uint(m.lineage.startedUtc); publicationElapsed = uint(m.publication.elapsedMs);
    const p = (deps.proof ?? proof)(month), o = { enabled: true, month, ...m.lineage.allocation }, prior = accountingInput(o, p, sourceStart); priorElapsed = prior.elapsedMs; budget();
    need(canonical(m.lineage.prefixes) === canonical(p.prefixes) && canonical(m.lineage.preceding) === canonical(p.preceding) && canonical(m.lineage.prior) === canonical(prior));
    need(Array.isArray(m.records) && m.records.length <= LIMITS.attempts && Array.isArray(m.files) && m.files.length <= 1503);
    const files = new Map(); let retained = mb.length, metadata = mb.length + LIMITS.stdout;
    for (const f of m.files) { budget(); need(!files.has(f.name) && f.name !== 'manifest.json' && names.includes(f.name)); const b = store.read(f.name); need(b.length === f.bytes && digest(b) === f.hash);
      retained += b.length; if (!f.name.endsWith('.raw') && f.name !== 'creations.jsonl') metadata += b.length; files.set(f.name, { ...f, data: b }); }
    need(canonical(names.sort()) === canonical([...files.keys(), 'manifest.json'].sort()) && retained === m.accounting.retained && retained <= LIMITS.retained && metadata <= LIMITS.metadata && mb.length === m.accounting.manifestBytes);
    const attempt = parse(files.get('attempt.json')?.data, false); need(attempt.month === month && attempt.expiry === config.original.expiry && canonical(attempt.lineage) === canonical(m.lineage));
    const state = initial(month), referenced = new Set(); let prev = null, received = 0, retries = 0;
    for (const [i, r] of m.records.entries()) { budget(); need(r.ordinal === i && uint(r.retry) <= 2 && uint(r.startMs) <= uint(r.endMs)); uint(r.waitMs); uint(r.received);
      const overflow = r.received > LIMITS.response && i === m.records.length - 1 && r.raw === null && !Object.hasOwn(r, 'rawHash') && !Object.hasOwn(r, 'rawBytes') && r.code === 'RESPONSE_LIMIT' && m.code === 'RESPONSE_LIMIT';
      need(r.received <= LIMITS.response || overflow); received += r.received; need(received <= LIMITS.received && prior.received + received <= c.TOTAL.received);
      need(r.queryHash === fingerprint(VERSION + '-query', r.query) && canonical(r.query) === canonical(c.query(month, state.current)));
      if (prev) need(r.startMs >= prev.endMs && r.startMs - prev.startMs >= LIMITS.spacing);
      if (r.retry) { need(retryEligible(prev) && !prev.raw && r.retry === prev.retry + 1 && canonical(r.query) === canonical(prev.query));
        const required = Math.max(r.retry === 1 ? 15000 : 45000, (prev.retryAfterSeconds ?? 0) * 1000); need(r.waitMs >= required && r.startMs - prev.endMs >= r.waitMs); retries++; }
      else need(!prev || prev.raw);
      if (r.raw) { const f = files.get(r.raw); need(r.raw === String(i + 1).padStart(5, '0') + '.raw' && !referenced.has(r.raw) && r.status === 200 && r.code === null && f && f.bytes <= LIMITS.response && f.hash === r.rawHash && f.bytes === r.rawBytes && f.bytes === r.received);
        referenced.add(r.raw); reduceRecord(state, r, f.data);
      } else { need(CODES.has(r.code)); if (i < m.records.length - 1) need(retryEligible(r) && r.retry < 2 && m.records[i + 1].retry === r.retry + 1);
        if (retryEligible(r) && r.retryAfterSeconds !== null) need(Number.isSafeInteger(uint(r.retryAfterSeconds) * 1000)); }
      prev = r;
    }
    need(referenced.size === [...files.keys()].filter(n => n.endsWith('.raw')).length && retries <= LIMITS.retries && prior.retries + retries <= c.TOTAL.retries && prior.attempts + m.records.length <= c.TOTAL.attempts);
    need(m.accounting.attempts === m.records.length && m.accounting.retries === retries && m.accounting.received === received);
    need(validSourceCode(m.code)); if (m.code === null) need(state.current > state.end); if (prev && !prev.raw) need(m.code !== null && (prev.code === m.code || retryEligible(prev)));
    const s = summary(state, m.code, m.lineage), hash = fingerprint(config.canonicalVersion, s), cb = Buffer.concat([...state.creations.values()].sort((a, b) => compare(a.value, b.value)).map(v => v.bytes));
    need(s.status === m.status && hash === m.summaryHash && files.get('summary.json')?.data.length <= LIMITS.summary && files.get('summary.json')?.data.equals(json(s)) && files.get('creations.jsonl')?.data.equals(cb)); budget();
    need(uint(m.publication.elapsedMs) <= config.totalMs && prior.elapsedMs + m.publication.elapsedMs <= c.TOTAL.elapsedMs && prior.retained + retained <= c.TOTAL.retained);
    return { ...FLAGS, code: null, recordedSourceCode: m.code, status: s.status, summaryHash: hash, manifestHash: digest(mb), runBudget: 'UNMEASURED', preflight: false };
  } catch (e) { return error(safe(e)); }
}
const exitCode = r => r.preflight || !validSourceCode(r.code) ? 1 : r.code === null && r.status === 'SCAN_COMPLETE' ? 0 : r.status === 'INCOMPLETE' ? 2 : 1;
async function runCli(args, deps = {}) {
  if (args.length === 3 && args[0] === '--replay' && args[1] === '--month' && Object.hasOwn(BRANCH, args[2])) return replay(args[2], deps.store, deps);
  const flags = ['--enable-public', '--month', '--retained-before', '--new-retained-before', '--attempts-before', '--retries-before', '--elapsed-before'];
  if (args.length !== 13 || args[0] !== flags[0] || flags.slice(1).some((f, i) => args[i * 2 + 1] !== f)) return error('ARGUMENTS_INVALID');
  return run({ enabled: true, month: args[2], ...Object.fromEntries(fields.map((k, i) => [k, args[i * 2 + 4]])) }, deps);
}
if (require.main === module) runCli(process.argv.slice(2)).then(r => { const out = JSON.stringify(r) + '\n'; process.stdout.write(Buffer.byteLength(out) <= LIMITS.stdout ? out : JSON.stringify(error('METADATA_LIMIT')) + '\n'); process.exitCode = Buffer.byteLength(out) <= LIMITS.stdout ? exitCode(r) : 1; }).catch(() => { process.stdout.write(JSON.stringify(error('INTEGRITY_ERROR')) + '\n'); process.exitCode = 1; });
module.exports = { VERSION, HARD, BASE, LIMITS, BRANCH, configuration, retryEligible, reserve, initial, summary, compare, fileStore, identity, verifyPrefix, verifyTailMetadata, proof, accountingInput, run, replay, runCli, exitCode };
