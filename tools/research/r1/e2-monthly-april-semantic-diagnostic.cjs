'use strict';
const fs = require('node:fs'), path = require('node:path');
const c = require('./e2-monthly-census.cjs');
const { parse, canonical, digest, fingerprint } = require('./exploratory-probe.cjs');
const VERSION = 'e2-monthly-april-semantic-diagnostic-v1';
const ROOT = 'C:\\crypto-research-evidence\\r1-e2\\exploratory-monthly-census-v1-april';
const DEADLINE = Date.parse('2026-10-04T05:51:30.407Z');
const PINS = { ...c.PINS, 'e2-monthly-census.cjs': 'sha256:b44c5e652408cd0ac89e104fcba88f808c8e5318c2ebca92f499c0358df28f31',
  'e2-monthly-census-cli.cjs': 'sha256:75aedfcdb8a9cee89bb9f8a366a213f296971b11e4c0b451e71703a73e3a3f42' };
const APRIL = { manifestBytes: 14427185, manifestHash: 'sha256:398c6c05f02bb174e374d513161e710eab270ea5b128f857e1b160948c780612',
  summaryBytes: 1964, summaryHash: 'sha256:c4f9916f8a1637095f31d1d03917df52a47b6dc570fa27c9853513990b851a7f' };
const FLAGS = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, cohortAdmitted: false,
  authoritativeCensusComplete: false, fullD1UpperBound: null, fullD1Fits: null };
const CODES = new Set(['DISABLED', 'ARGUMENTS_INVALID', 'UNSAFE_PATH', 'ROOT_EXISTS', 'EXPIRY', 'RUNTIME_INVALID',
  'SOURCE_IDENTITY_INVALID', 'INTEGRITY_ERROR', 'ORDER_INVALID', 'RESPONSE_INVALID', 'IMMUTABLE_CONFLICT', 'NO_PROGRESS',
  'HTTP_ERROR', 'HTTP_529_EXHAUSTED', 'PARTIAL_RESPONSE', 'TIMEOUT', 'NETWORK_ERROR', 'RESPONSE_LIMIT', 'TIME_LIMIT',
  'FREE_SPACE_LIMIT', 'DISK_LIMIT', 'RECEIVED_LIMIT', 'ATTEMPT_LIMIT', 'RETRY_LIMIT', 'INSTRUCTION_LIMIT',
  'CREATION_LIMIT', 'HEADER_LIMIT', 'METADATA_LIMIT', 'STORAGE_ERROR']);
const need = (v, code = 'INTEGRITY_ERROR') => { if (!v) throw Error(code); };
const uint = v => { need(Number.isSafeInteger(v) && v >= 0); return v; };
const json = v => Buffer.from(JSON.stringify(v) + '\n');
const allowed = n => ['attempt.json', 'manifest.json', 'summary.json', 'creations.jsonl'].includes(n) || /^[0-9]{5}\.raw$/.test(n);
function compare(a, b) {
  let n = a.slot - b.slot || a.transactionIndex - b.transactionIndex; if (n) return n;
  for (let i = 0; i < Math.min(a.instructionPath.length, b.instructionPath.length); i++) if (a.instructionPath[i] !== b.instructionPath[i]) return a.instructionPath[i] - b.instructionPath[i];
  return a.instructionPath.length - b.instructionPath.length || (a.signature < b.signature ? -1 : a.signature > b.signature ? 1 : 0);
}
function summary(state, code, lineage) {
  const { headers, creations, serializedCreations, ...stream } = state;
  return { ...FLAGS, version: 'e2-monthly-census-v1', month: 'april', code, status: code ? 'INCOMPLETE' : 'SCAN_COMPLETE', stream,
    creations: creations.size, storedHeaders: headers.size, lineage, sourceCoverage: 'REFERENCE_BOUNDARIES_ONLY', fallback: 'NOT_RUN', retention: 'UNVERIFIED', expiry: c.configuration('april').expiry,
    gaps: ['DEPLOYED_VERSION_UNKNOWN', 'INNER_INGESTION_UNKNOWN', 'GLOBAL_EARLIEST_UNKNOWN', 'ALL_POOL_COVERAGE_UNKNOWN', 'PIT_KNOWN_AT_UNKNOWN', 'RIGHTS_UNKNOWN'] };
}
function ancestors(file) {
  let dir = path.dirname(file); while (true) { const s = fs.lstatSync(dir);
    need(s.isDirectory() && !s.isSymbolicLink() && fs.realpathSync.native(dir).toLowerCase() === dir.toLowerCase(), 'UNSAFE_PATH');
    const parent = path.dirname(dir); if (parent === dir) break; dir = parent; }
}
function readFile(file, cap) {
  ancestors(file); const s = fs.lstatSync(file); need(s.isFile() && !s.isSymbolicLink() && uint(s.size) <= cap, 'UNSAFE_PATH');
  const fd = fs.openSync(file, 'r'); try { const current = fs.fstatSync(fd); need(current.isFile() && current.size === s.size && current.ino === s.ino);
    need(fs.realpathSync.native(file).toLowerCase() === file.toLowerCase(), 'UNSAFE_PATH');
    const b = Buffer.alloc(s.size + 1); let at = 0;
    while (at < b.length) { const n = fs.readSync(fd, b, at, b.length - at, null); if (!n) break; at += n; }
    need(at === s.size && fs.fstatSync(fd).size === s.size); return b.subarray(0, at);
  } finally { fs.closeSync(fd); }
}
function fixedStore(budget = () => {}) {
  ancestors(path.join(ROOT, 'manifest.json'));
  const names = fs.readdirSync(ROOT); need(names.length <= 16000 && new Set(names).size === names.length);
  for (const n of names) { budget(); need(allowed(n), 'UNSAFE_PATH'); const s = fs.lstatSync(path.join(ROOT, n));
    need(s.isFile() && !s.isSymbolicLink() && uint(s.size) <= c.LIMITS.response, 'UNSAFE_PATH'); }
  return { list: () => fs.readdirSync(ROOT), size: n => { need(allowed(n)); return fs.lstatSync(path.join(ROOT, n)).size; },
    read: n => { need(allowed(n)); return readFile(path.join(ROOT, n), c.LIMITS.response); } };
}
function verifyPins() { for (const [n, h] of Object.entries(PINS)) need(digest(readFile(path.join(__dirname, n), 1000000)) === h); }
function inspect(store, options = {}) {
  const clock = options.now ?? Date.now, start = options.startedAt ?? clock(); let last = start, inspectedFiles = 0, inspectedBytes = 0;
  const failures = [], checks = {};
  const budget = () => { const t = clock(); need(Number.isSafeInteger(t) && t >= last, 'TIME_LIMIT'); last = t;
    need(t - start <= 1800000 && (!options.fixedApril || t <= DEADLINE), 'TIME_LIMIT'); };
  const temporal = (ordinal, check, required, actual) => { checks[check] = (checks[check] ?? 0) + 1;
    if (actual < required) { need(failures.length < 40000, 'METADATA_LIMIT'); failures.push({ ordinal, check, required, actual, deficit: required - actual }); } };
  const read = n => { budget(); const b = store.read(n); need(Buffer.isBuffer(b) && b.length <= c.LIMITS.response);
    inspectedFiles++; inspectedBytes += b.length; need(inspectedFiles <= 16000 && inspectedBytes <= c.LIMITS.retained); budget(); return b; };
  const base = { ...FLAGS, version: VERSION, historicalStrictReplay: 'INTEGRITY_ERROR', overallStrictPass: false };
  try {
    budget(); need(Number.isSafeInteger(start)); verifyPins(); const names = store.list(); need(Array.isArray(names) && names.length <= 16000 && new Set(names).size === names.length && names.every(allowed));
    let inputBytes = 0;
    for (const n of names) { budget(); const size = uint(store.size(n)); need(size <= c.LIMITS.response && (n !== 'summary.json' || size <= c.LIMITS.summary));
      inputBytes += size; need(inputBytes <= c.LIMITS.retained); }
    const mb = read('manifest.json'), m = parse(mb, false), config = c.configuration('april');
    if (options.fixedApril) need(mb.length === APRIL.manifestBytes && digest(mb) === APRIL.manifestHash);
    need(canonical(m.configuration) === canonical(config) && m.version === config.version && canonical(Object.fromEntries(Object.keys(FLAGS).map(k => [k, m[k]]))) === canonical(FLAGS));
    need(m.lineage.configHash === fingerprint(config.canonicalVersion, config) && canonical(m.lineage.proof) === canonical({ status: 'VERIFIED', bounds: c.BOUNDS, manifestHash: c.SEAL.manifestHash, controlBytes: 880 }));
    need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(m.lineage.runtime) && /^[a-f0-9]{40}$/.test(m.lineage.source.commit) && typeof m.lineage.source.dirty === 'boolean');
    need(canonical(m.lineage.scripts) === canonical(PINS));
    need(Array.isArray(m.records) && m.records.length <= c.LIMITS.attempts && Array.isArray(m.files) && m.files.length <= c.LIMITS.attempts + 3);
    const files = new Map(); let retained = mb.length, metadata = mb.length + c.LIMITS.stdout;
    for (const f of m.files) { need(allowed(f.name) && f.name !== 'manifest.json' && !files.has(f.name) && !f.partial && uint(f.bytes) <= c.LIMITS.response && /^sha256:[a-f0-9]{64}$/.test(f.hash));
      need(store.size(f.name) === f.bytes); retained += f.bytes; if (!f.name.endsWith('.raw') && f.name !== 'creations.jsonl') metadata += f.bytes; files.set(f.name, f); }
    need(canonical(names.sort()) === canonical([...files.keys(), 'manifest.json'].sort()));
    need(retained <= c.LIMITS.retained && metadata <= c.LIMITS.metadata && retained === m.accounting.retained && mb.length === m.accounting.manifestBytes && m.accounting.retainedKnown === true);
    const verified = n => { const f = files.get(n); need(f); const b = read(n); need(b.length === f.bytes && digest(b) === f.hash); return b; };
    const ab = verified('attempt.json'), sb = verified('summary.json'), cb = verified('creations.jsonl');
    need(sb.length <= c.LIMITS.summary && cb.length <= c.LIMITS.creationBytes);
    if (options.fixedApril) need(sb.length === APRIL.summaryBytes && digest(sb) === APRIL.summaryHash);
    const attempt = parse(ab, false); need(attempt.month === 'april' && attempt.expiry === config.expiry && canonical(attempt.lineage) === canonical(m.lineage));
    const prior = m.lineage.priorAccounting; need(prior.source === 'MAIN_ATTESTED_AND_FINALIZED_MONTHLY_METADATA');
    for (const k of Object.keys(c.TOTAL)) { const own = uint(k === 'elapsedMs' ? m.publicationOperational.elapsedMs : m.accounting[k]);
      need(uint(prior[k]) + own <= c.TOTAL[k] && own <= (k === 'elapsedMs' ? c.LIMITS.totalMs : c.LIMITS[k])); need(prior[k] === 0); }
    need(uint(prior.bytes) + 3 * c.LIMITS.retained <= c.LIMITS.aggregate);
    const state = c.initial('april'), referenced = new Set(); let received = 0, retries = 0, prev = null;
    for (const [i, r] of m.records.entries()) {
      budget(); const rejectedOverflow = uint(r.received) > c.LIMITS.response && i === m.records.length - 1 && r.raw === null && !Object.hasOwn(r, 'rawHash') && !Object.hasOwn(r, 'rawBytes') && r.code === 'RESPONSE_LIMIT' && m.code === 'RESPONSE_LIMIT' && m.status === 'INCOMPLETE';
      need(r.ordinal === i && (r.received <= c.LIMITS.response || rejectedOverflow) && uint(r.retry) <= 2); uint(r.waitMs); uint(r.startMs); uint(r.endMs);
      temporal(i, 'START_END_CHRONOLOGY', 0, r.endMs - r.startMs);
      received += r.received; need(received <= c.LIMITS.received && r.queryHash === fingerprint('e2-monthly-query-v1', r.query));
      if (prev) { temporal(i, 'PREVIOUS_END_START', 0, r.startMs - prev.endMs); temporal(i, 'MINIMUM_START_SPACING', c.LIMITS.spacing, r.startMs - prev.startMs); }
      if (r.retry) { need(prev?.status === 529 && prev.code === 'HTTP_ERROR' && !prev.raw && r.retry === prev.retry + 1 && canonical(r.query) === canonical(prev.query));
        const minimum = Math.max(r.retry === 1 ? 15000 : 45000, (prev.retryAfterSeconds ?? 0) * 1000); need(Number.isSafeInteger(minimum) && r.waitMs >= minimum);
        temporal(i, 'ACTUAL_RETRY_WAIT', r.waitMs, r.startMs - prev.endMs); retries++; }
      else need(!prev || prev.raw);
      need(canonical(r.query) === canonical(c.query('april', state.current)));
      if (r.raw) { need(r.raw === String(i + 1).padStart(5, '0') + '.raw' && !referenced.has(r.raw) && r.code === null && r.status === 200);
        const f = files.get(r.raw); need(f && f.hash === r.rawHash && f.bytes === r.rawBytes && f.bytes === r.received); referenced.add(r.raw);
        const raw = verified(r.raw), a = c.admit(raw, r.query, 'april'); c.apply(state, a, c.prepare(state, a));
      } else { need(CODES.has(r.code)); if (i < m.records.length - 1) need(r.status === 529 && r.retry < 2 && m.records[i + 1].retry === r.retry + 1);
        if (r.status === 529 && r.retryAfterSeconds !== null) uint(r.retryAfterSeconds); }
      prev = r;
    }
    need([...files.keys()].filter(n => n.endsWith('.raw')).length === referenced.size && retries <= c.LIMITS.retries);
    need(m.accounting.attempts === m.records.length && m.accounting.retries === retries && m.accounting.received === received);
    need(m.code === null || CODES.has(m.code)); if (m.code === null) need(state.current > state.end); else need(m.status === 'INCOMPLETE');
    if (prev && !prev.raw) need(m.code !== null && (prev.code === m.code || (prev.status === 529 && prev.code === 'HTTP_ERROR')));
    const s = summary(state, m.code, m.lineage), hash = fingerprint(config.canonicalVersion, s);
    need(m.status === s.status && m.summaryHash === hash && sb.equals(json(s)));
    const reconstructed = Buffer.concat([...state.creations.values()].sort((a, b) => compare(a.value, b.value)).map(v => v.bytes)); need(cb.equals(reconstructed));
    budget(); need(canonical(store.list().sort()) === canonical(names));
    const result = { ...base, code: null, configHash: m.lineage.configHash, manifestHash: digest(mb), summaryHash: digest(sb), creationsHash: digest(cb), semanticSummaryHash: hash,
      semanticIntegrity: 'PASS', recordedTiming: failures.length ? 'FAIL' : 'PASS', timing: { checks, failureCount: failures.length, failures }, inspectedFiles, inspectedBytes, records: m.records.length, creations: state.creations.size };
    need(json(result).length <= c.LIMITS.stdout, 'METADATA_LIMIT'); return result;
  } catch (e) { return { ...base, code: ['TIME_LIMIT', 'UNSAFE_PATH', 'METADATA_LIMIT'].includes(e.message) ? e.message : 'INTEGRITY_ERROR', semanticIntegrity: 'FAIL', recordedTiming: 'UNKNOWN', inspectedFiles, inspectedBytes }; }
}
function runCli(args, deps = {}) {
  if (args.length !== 1 || args[0] !== '--enable-offline') return { ...FLAGS, version: VERSION, code: 'DISABLED', semanticIntegrity: 'UNKNOWN', recordedTiming: 'UNKNOWN', historicalStrictReplay: 'INTEGRITY_ERROR', overallStrictPass: false };
  try { const now = deps.now ?? Date.now, startedAt = now(); let last = startedAt;
    const budget = () => { const t = now(); need(Number.isSafeInteger(t) && t >= last && t - startedAt <= 1800000 && t <= DEADLINE, 'TIME_LIMIT'); last = t; };
    budget(); return inspect((deps.store ?? fixedStore)(budget), { fixedApril: true, now, startedAt }); }
  catch { return { ...FLAGS, version: VERSION, code: 'INTEGRITY_ERROR', semanticIntegrity: 'FAIL', recordedTiming: 'UNKNOWN', historicalStrictReplay: 'INTEGRITY_ERROR', overallStrictPass: false }; }
}
const exitCode = r => r.semanticIntegrity === 'PASS' && r.code === null ? 2 : 1;
if (require.main === module) { const r = runCli(process.argv.slice(2)); process.stdout.write(JSON.stringify(r) + '\n'); process.exitCode = exitCode(r); }
module.exports = { VERSION, ROOT, DEADLINE, PINS, APRIL, inspect, runCli, exitCode, summary, compare };
