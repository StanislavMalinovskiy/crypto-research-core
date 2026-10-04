'use strict';
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { parse, canonical, digest, fingerprint } = require('./exploratory-probe.cjs');
const monthly = require('./e2-monthly-census.cjs'), tail = require('./e2-monthly-tail.cjs');
const VERSION = 'e2-offline-mint-count-v1';
const QUOTES = Object.freeze(['So11111111111111111111111111111111111111112', 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v', 'Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB']);
const LIMITS = Object.freeze({ files: 15, file: 64000000, bytes: 200000000, rows: 200000, line: 16384, diagnostics: 1000, elapsedMs: 300000, output: 1000000, retained: 2000000 });
const DATES = [1775001600, 1777593600, 1780272000, 1782604800];
const ROOT = 'C:\\crypto-research-evidence\\r1-e2';
const ROOTS = Object.freeze([
  { suffix: 'exploratory-monthly-census-v1-april', month: 'april', start: 410195947, end: 416762081, rows: 60340,
    pins: [[14427185, '398c6c05f02bb174e374d513161e710eab270ea5b128f857e1b160948c780612'], [1964, 'c4f9916f8a1637095f31d1d03917df52a47b6dc570fa27c9853513990b851a7f'], [42396676, 'ac04355822478b08d8048d96e1ce7a0b60969bcacf5a6025e3fe36b983d9b3a7']] },
  { suffix: 'exploratory-monthly-census-v1-may', month: 'may', start: 416762082, end: 422954346, rows: 55991,
    pins: [[14306577, '0238c30cebbfed7502b094590595f4866363f3d23ca0c4bd922a38cb18a63395'], [2340, '804ba236db721757fb1767a3749001baaedca41351711b2e32db9ad757093d0f'], [39337817, '66a7a8fda83d96698d91e78ecc13f0782e5ebf82abfc6f51edeedcae07a731fc']] },
  { suffix: 'exploratory-monthly-census-v1-june', month: 'june', start: 423478907, end: 429325105, rows: 65973,
    pins: [[15404589, '34742b956c883ca3af487d7ff4cc14e0b9edae68fbe58fe4f9c6c539bda2fa69'], [2345, '28424a78970b3fe4d86c8a3c255d7ef5ce33565fddccbc25cbbe440c5d2cbfe8'], [46339012, 'baf64e16452b4bc98569c246b5afed25e241c983970d784c1e48cc8e3098fdac']] },
  { suffix: 'exploratory-monthly-may-tail-v1', month: 'may', start: 422954347, end: 423478906, rows: 6724,
    pins: [[1150302, '5fe9d26ce92042097eea611146ab1fcc03b54b1195ab7271224760a9dec732a5'], [3043, 'a6bcdf9713a68c3b61c94d5a657898dde9899193318643df8022a775d530d4b8'], [4723201, 'd523fc78b8378ad397d913dd06af05d946e26273c79756923374ebe38fafaa42']] },
  { suffix: 'exploratory-monthly-june-tail-v1', month: 'june', start: 429325106, end: 429340000, rows: 99,
    pins: [[48171, '7e6468bb6aecd66be2a93a58f9f9aa502591b48c3f87e678e1578688d78e989d'], [3175, 'ab5480dd27aa246c3fe05a8e697e3b1db28abe829108b1f3f00446b2aad5b287'], [69556, '4bf4554b62e94117ea243e8010555b10ccf5a1c0b6dfa134374e8f1dd31a2174']] }
]);
const NAMES = ['manifest.json', 'summary.json', 'creations.jsonl'];
const FLAGS = Object.freeze({ classification: 'EXPLORATORY', predicate: 'PROPOSED', cohortAdmitted: false, d1Evidence: false, d1Passed: false,
  authoritativeCensusComplete: false, selectionPerformed: false, confirmedCohortCounts: 'NOT_ESTABLISHED', admittedCohortCounts: 'NOT_ESTABLISHED' });
const UNKNOWN = Object.freeze({ deployedLayout: 'UNKNOWN', argumentValues: 'UNKNOWN', eventProof: 'UNKNOWN', pdaLinkage: 'UNKNOWN', globalEarliest: 'UNKNOWN', allPoolHistory: 'UNKNOWN',
  historicalLabels: 'UNKNOWN', knownAt: 'UNKNOWN', pointInTimeEligibility: 'UNKNOWN', rights: 'UNKNOWN', fullWallet: 'UNKNOWN', ownedTrader: 'UNKNOWN', depth: 'UNKNOWN', price: 'UNKNOWN', holdoutAdequacy: 'UNKNOWN' });
const DOCUMENTARY = Object.freeze({ receipt: 'sha256:1a011853f374eb3ac7bceb6538c55c496a36be59b6511e236ade5e5594a8c8fc',
  januaryIdl: 'sha256:773a430d6727446721c0f5028710e65484643fbf9c0cac50f0ba117594e5514b', februaryIdl: 'sha256:5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6',
  circle: 'sha256:23e3b27d32587a3b852ff5cf66709fbb561d012fca5911213c16cec1aae6d182', tether: 'sha256:02714d14714454a158a62b2c2c385055cc492e418759254f898cb53718bc9da7' });
const DISPOSITIONS = Object.freeze({ april: { semanticIntegrity: 'PASS', recordedTiming: 'FAIL', historicalStrictReplay: 'INTEGRITY_ERROR' },
  originalMay: 'INCOMPLETE', originalJune: 'INCOMPLETE', juneTailWholeAllWrite: 'TIME_LIMIT', juneTailAdministrativeOverrunMs: 176236 });
const need = (v, code = 'INTEGRITY_ERROR') => { if (!v) throw Error(code); };
const uint = x => Number.isSafeInteger(x) && x >= 0;
const hash = x => typeof x === 'string' && /^sha256:[a-f0-9]{64}$/.test(x);
const policy = () => ({ version: VERSION, quotes: QUOTES, dates: DATES, roots: ROOTS, limits: LIMITS, documentary: DOCUMENTARY,
  shape: '18_ACCOUNTS_59_OR60_BYTES_DOCUMENTARY_ONLY', locator: 'SIGNATURE_NUMERIC_PATH', order: 'TIMESTAMP_SLOT_TX_NUMERIC_PATH_UNSIGNED_SIGNATURE_POOL', selection: 'NOT_PERFORMED', dispositions: DISPOSITIONS });
// Frozen decoders are private. This local decoder is bounded and returns canonical unsigned address bytes only.
function decode(text, size) {
  need(typeof text === 'string' && text.length > 0 && text.length <= (size === 32 ? 44 : 88) && /^[1-9A-HJ-NP-Za-km-z]+$/.test(text), 'UNSUPPORTED_REPRESENTATION');
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = 0n, zero = 0;
  for (const ch of text) n = n * 58n + BigInt(alphabet.indexOf(ch)); while (zero < text.length && text[zero] === '1') zero++;
  const hex = n.toString(16), b = n ? Buffer.from(hex.padStart(Math.ceil(hex.length / 2) * 2, '0'), 'hex') : Buffer.alloc(0);
  need(zero + b.length === size, 'UNSUPPORTED_REPRESENTATION'); return Buffer.concat([Buffer.alloc(zero), b]);
}
function orient(row) {
  try { decode(row.baseMint, 32); decode(row.quoteMint, 32); } catch { return { reason: 'UNSUPPORTED_REPRESENTATION', mint: null }; }
  if (row.baseMint === row.quoteMint) return { reason: 'SAME_MINT', mint: null };
  const a = QUOTES.includes(row.baseMint), b = QUOTES.includes(row.quoteMint);
  return a && b ? { reason: 'TWO_SUPPORTED_QUOTES', mint: null } : !a && !b ? { reason: 'NO_SUPPORTED_QUOTE', mint: null }
    : { reason: 'PROVISIONAL_STUDIED_MINT', mint: a ? row.quoteMint : row.baseMint };
}
function compare(a, b) {
  let n = a.timestamp - b.timestamp || a.slot - b.slot || a.transactionIndex - b.transactionIndex; if (n) return n;
  for (let i = 0; i < Math.min(a.instructionPath.length, b.instructionPath.length); i++) if (a.instructionPath[i] !== b.instructionPath[i]) return a.instructionPath[i] - b.instructionPath[i];
  return a.instructionPath.length - b.instructionPath.length || Buffer.compare(decode(a.signature, 64), decode(b.signature, 64)) || Buffer.compare(decode(a.pool, 32), decode(b.pool, 32));
}
function parsedRow(bytes) {
  const row = parse(bytes); for (const k of ['slot', 'timestamp', 'transactionIndex', 'cpiDepth', 'accountCount', 'dataLength', 'lineOrdinal']) {
    need(typeof row[k] === 'string' && /^(0|[1-9][0-9]*)$/.test(row[k]) && uint(Number(row[k])), 'PROVENANCE_INVALID'); row[k] = Number(row[k]); }
  if (Array.isArray(row.instructionPath)) row.instructionPath = row.instructionPath.map(v => { need(typeof v === 'string' && /^(0|[1-9][0-9]*)$/.test(v) && uint(Number(v)), 'PROVENANCE_INVALID'); return Number(v); });
  return row;
}
class Reducer {
  constructor(roots = ROOTS) { this.roots = roots; this.locators = new Map(); this.pools = new Map(); this.mints = new Map(); this.rows = 0; this.duplicates = 0;
    this.diagnostics = Object.create(null); this.exposures = roots.map(r => ({ root: r.suffix, rows: 0, locators: 0, pools: 0, provisionalMints: 0, documentaryShape: 0, unknownShape: 0, orientation: {} })); }
  reason(reason, at) { need(Object.keys(this.diagnostics).length < LIMITS.diagnostics || Object.hasOwn(this.diagnostics, reason), 'DIAGNOSTIC_LIMIT');
    this.diagnostics[reason] = (this.diagnostics[reason] ?? 0) + 1; const v = this.exposures[at].orientation; v[reason] = (v[reason] ?? 0) + 1; }
  add(row, at, provenance) {
    need(uint(at) && at < this.roots.length, 'PROVENANCE_INVALID'); const root = this.roots[at], month = ['april', 'may', 'june'].indexOf(root.month), exp = this.exposures[at];
    need(++this.rows <= LIMITS.rows, 'ROW_LIMIT'); exp.rows++;
    need(uint(row.slot) && row.slot >= root.start && row.slot <= root.end && uint(row.timestamp) && row.timestamp >= DATES[month] && row.timestamp < DATES[month + 1]
      && hash(row.rawHash) && uint(row.lineOrdinal), 'PROVENANCE_INVALID');
    if (provenance) need(provenance.has(row.rawHash) && row.lineOrdinal < provenance.get(row.rawHash), 'PROVENANCE_INVALID');
    let valid = row.status === 'OBSERVED_DECLARED_CREATE_POOL' && row.layoutApplicability === 'UNVERIFIED' && uint(row.transactionIndex) && Array.isArray(row.instructionPath)
      && row.instructionPath.length > 0 && row.instructionPath.length <= 64 && row.instructionPath.every(uint) && uint(row.cpiDepth) && row.cpiDepth === row.instructionPath.length - 1;
    try { for (const k of ['pool', 'globalConfig', 'creator', 'baseMint', 'quoteMint']) decode(row[k], 32); decode(row.signature, 64); } catch { valid = false; }
    if (!valid) { exp.unknownShape++; this.reason('UNSUPPORTED_REPRESENTATION', at); return; }
    const id = row.signature + ':' + row.instructionPath.join('.'), bit = 1 << at;
    const immutable = fingerprint(VERSION + '-locator', [row.slot, row.timestamp, row.transactionIndex, row.pool, row.globalConfig, row.creator, row.baseMint, row.quoteMint, row.accountCount, row.dataLength, row.cpiDepth]);
    const reference = at + ':' + row.rawHash + ':' + row.lineOrdinal;
    const old = this.locators.get(id); if (old) { need(old[0] === immutable, 'LOCATOR_CONFLICT'); this.duplicates++; old[2].push(reference); if (!(old[1] & bit)) { old[1] |= bit; exp.locators++; } }
    else { this.locators.set(id, [immutable, bit, [reference]]); exp.locators++; }
    const poolFact = fingerprint(VERSION + '-pool', [id, immutable]), pool = this.pools.get(row.pool);
    if (pool) { need(pool[0] === poolFact, 'POOL_CREATION_CONFLICT'); if (!(pool[1] & bit)) { pool[1] |= bit; exp.pools++; } }
    else { this.pools.set(row.pool, [poolFact, bit]); exp.pools++; }
    if (row.accountCount !== 18 || ![59, 60].includes(row.dataLength)) { exp.unknownShape++; this.reason('UNSUPPORTED_DOCUMENTARY_SHAPE', at); return; }
    exp.documentaryShape++; const oriented = orient(row); this.reason(oriented.reason, at); if (!oriented.mint) return;
    const mint = this.mints.get(oriented.mint), order = { timestamp: row.timestamp, slot: row.slot, transactionIndex: row.transactionIndex,
      instructionPath: row.instructionPath, signature: row.signature, pool: row.pool };
    if (mint) { if (compare(order, mint[0]) < 0) mint[0] = order; if (!(mint[1] & bit)) { mint[1] |= bit; exp.provisionalMints++; } }
    else { this.mints.set(oriented.mint, [order, bit]); exp.provisionalMints++; }
  }
  result() { const monthlyObservedFirstMints = { april: 0, may: 0, june: 0 };
    for (const [order] of this.mints.values()) { const at = DATES.findIndex((v, i) => i < 3 && order.timestamp >= v && order.timestamp < DATES[i + 1]); need(at >= 0); monthlyObservedFirstMints[['april', 'may', 'june'][at]]++; }
    return { ...FLAGS, version: VERSION, code: null, status: 'QUALIFIED_OFFLINE_COUNT', inputRows: this.rows, uniqueLocators: this.locators.size,
      duplicateLocators: this.duplicates, provenanceReferences: this.rows, distinctObservedPools: this.pools.size, provisionalStudiedMints: this.mints.size,
      monthlyObservedFirstMints, roots: this.exposures, reasons: this.diagnostics, conflicts: 0, unknown: UNKNOWN, dispositions: DISPOSITIONS };
  }
}
class Lines {
  constructor(consume) { this.consume = consume; this.pending = Buffer.alloc(0); }
  feed(chunk) { let at = 0; while (at < chunk.length) { const lf = chunk.indexOf(10, at), end = lf < 0 ? chunk.length : lf;
      need(this.pending.length + end - at + (lf < 0 ? 0 : 1) <= LIMITS.line, 'LINE_LIMIT'); const part = chunk.subarray(at, end);
      this.pending = this.pending.length ? Buffer.concat([this.pending, part]) : Buffer.from(part);
      if (lf < 0) break; need(this.pending.length > 0, 'INTEGRITY_ERROR'); this.consume(this.pending); this.pending = Buffer.alloc(0); at = lf + 1; } }
  finish() { need(this.pending.length === 0, 'INTEGRITY_ERROR'); }
}
function filePath(root, name) { need(ROOTS.some(v => v.suffix === root.suffix) && NAMES.includes(name), 'UNSAFE_PATH'); return path.join(ROOT, root.suffix, name); }
function safePath(file) { let at = file; while (true) { const s = fs.lstatSync(at); need(!s.isSymbolicLink() && fs.realpathSync.native(at).toLowerCase() === at.toLowerCase(), 'UNSAFE_PATH');
    if (at === file) need(s.isFile(), 'UNSAFE_PATH'); else need(s.isDirectory(), 'UNSAFE_PATH'); const parent = path.dirname(at); if (parent === at) break; at = parent; } }
function reader(root, name) { const file = filePath(root, name); safePath(file); const s = fs.lstatSync(file), fd = fs.openSync(file, 'r');
  const stat = fs.fstatSync(fd); try { need(stat.size === s.size && stat.ino === s.ino, 'UNSAFE_PATH'); } catch (e) { fs.closeSync(fd); throw e; }
  const buffer = Buffer.alloc(65536); return { size: s.size, read() { const n = fs.readSync(fd, buffer, 0, buffer.length, null); return buffer.subarray(0, n); },
    close() { try { const end = fs.fstatSync(fd); safePath(file); need(end.size === stat.size && end.ino === stat.ino && end.mtimeMs === stat.mtimeMs && fs.lstatSync(file).ino === stat.ino); } finally { fs.closeSync(fd); } } };
}
function metadata(root, at, mb, sb) {
  // Exact immutable pins are already checked. Ordinary parsing of the large pinned manifest avoids duplicating its entire lossless encoding.
  const m = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(mb)), s = parse(sb, false);
  const config = at < 3 ? monthly.configuration(root.month) : tail.configuration(root.month);
  need(canonical(m.configuration) === canonical(config) && m.summaryHash === fingerprint(config.canonicalVersion, s) && s.creations === root.rows && s.status === m.status && s.code === m.code);
  need(m.accounting.retainedKnown === true && m.lineage.runtime === 'v24.19.0' && /^[a-f0-9]{40}$/.test(m.lineage.source.commit) && m.lineage.source.dirty === false);
  for (const k of ['cohortAdmitted', 'd1Evidence', 'd1Passed', 'authoritativeCensusComplete']) need(m[k] === false && s[k] === false);
  need(m.code === (at === 1 || at === 2 ? 'HTTP_ERROR' : null) && m.status === (m.code ? 'INCOMPLETE' : 'SCAN_COMPLETE'));
  const cf = m.files.filter(f => f.name === 'creations.jsonl'), sf = m.files.filter(f => f.name === 'summary.json');
  need(cf.length === 1 && cf[0].bytes === root.pins[2][0] && cf[0].hash === 'sha256:' + root.pins[2][1] && sf.length === 1 && sf[0].bytes === sb.length && sf[0].hash === digest(sb));
  const provenance = new Map(); for (const f of m.files) if (/^[0-9]{5}\.raw$/.test(f.name)) { need(hash(f.hash) && uint(f.bytes)); provenance.set(f.hash, f.bytes); }
  need(provenance.size > 0); return provenance;
}
function serialize(result) { const out = JSON.stringify(result) + '\n'; need(Buffer.byteLength(out) <= LIMITS.output, 'OUTPUT_LIMIT'); return out; }
function failure(e) { const allowed = new Set(['DISABLED', 'ARGUMENTS_INVALID', 'INTEGRITY_ERROR', 'UNSAFE_PATH', 'PROVENANCE_INVALID', 'LOCATOR_CONFLICT', 'POOL_CREATION_CONFLICT', 'FILE_LIMIT', 'INPUT_LIMIT', 'ROW_LIMIT', 'LINE_LIMIT', 'DIAGNOSTIC_LIMIT', 'TIME_LIMIT', 'OUTPUT_LIMIT', 'HEAP_LIMIT', 'EXPIRY']);
  return { ...FLAGS, version: VERSION, code: allowed.has(e?.message) ? e.message : 'INTEGRITY_ERROR', status: 'STOPPED', conflicts: ['LOCATOR_CONFLICT', 'POOL_CREATION_CONFLICT'].includes(e?.message) ? 1 : 0, unknown: UNKNOWN, dispositions: DISPOSITIONS }; }
function inspect(deps = {}) {
  const now = deps.now ?? Date.now, begun = now(), roots = deps.roots ?? ROOTS, open = deps.reader ?? reader; let last = begun, actualBytes = 0, peakHeap = 0, peakRss = 0;
  const check = () => { const n = now(); need(uint(n) && n >= last && n - begun <= LIMITS.elapsedMs, 'TIME_LIMIT'); last = n;
    const m = process.memoryUsage(); peakHeap = Math.max(peakHeap, m.heapUsed); peakRss = Math.max(peakRss, m.rss); };
  const inputs = [], reduction = new Reducer(roots);
  try {
    check(); need(roots.length === 5 && roots.length * NAMES.length === LIMITS.files, 'FILE_LIMIT'); let declared = 0;
    for (const root of roots) { need(root.pins.length === 3); for (const [bytes, sha] of root.pins) { need(uint(bytes) && bytes <= LIMITS.file && /^[a-f0-9]{64}$/.test(sha), 'FILE_LIMIT'); declared += bytes; } }
    need(declared <= LIMITS.bytes, 'INPUT_LIMIT');
    function read(root, index, consume) { check(); const r = open(root, NAMES[index]); let total = 0; const sha = crypto.createHash('sha256'), chunks = [];
      try { need(uint(r.size) && r.size === root.pins[index][0] && r.size <= LIMITS.file); for (;;) { check(); const b = r.read(); need(Buffer.isBuffer(b)); if (!b.length) break;
          total += b.length; actualBytes += b.length; need(total <= r.size && total <= LIMITS.file && actualBytes <= LIMITS.bytes, 'INPUT_LIMIT'); sha.update(b);
          if (consume) consume(b); else chunks.push(Buffer.from(b)); }
        need(total === r.size && sha.digest('hex') === root.pins[index][1]); inputs.push({ root: root.suffix, name: NAMES[index], bytes: total, hash: 'sha256:' + root.pins[index][1] });
        return consume ? null : Buffer.concat(chunks);
      } finally { r.close(); check(); } }
    for (const [at, root] of roots.entries()) {
      const mb = read(root, 0), sb = read(root, 1), proof = metadata(root, at, mb, sb); check(); const rowsBefore = reduction.rows;
      const lines = new Lines(b => { check(); reduction.add(parsedRow(b), at, proof); }); read(root, 2, b => lines.feed(b)); lines.finish(); need(reduction.rows - rowsBefore === root.rows); check();
    }
    need(inputs.length === LIMITS.files && actualBytes === declared); check(); const result = { ...reduction.result(), policyHash: fingerprint(VERSION + '-policy', policy()),
      inputs, inputHash: fingerprint(VERSION + '-inputs', inputs), resultHash: fingerprint(VERSION + '-result', reduction.result()), scriptHash: digest(fs.readFileSync(__filename)), documentary: DOCUMENTARY, accounting: { files: inputs.length, bytes: actualBytes, elapsedMs: last - begun,
        peakHeapBytes: peakHeap, peakRssBytes: peakRss, networkCalls: 0, sourceCalls: 0, replayCalls: 0, cashMicrousd: '0', runBudget: 'MAIN_MEASURES_ALL_WRITE' } };
    serialize(result); check(); result.accounting.elapsedMs = last - begun; serialize(result); check(); return result;
  } catch (e) { return failure(e); }
}
function runCli(args, deps = {}) { if (args.length === 0) return failure(Error('DISABLED'));
  if (args.length !== 1 || args[0] !== '--enable-offline') return failure(Error('ARGUMENTS_INVALID'));
  if (!process.execArgv.includes('--max-old-space-size=1024')) return failure(Error('HEAP_LIMIT'));
  if (Date.now() > Date.parse('2026-10-10T23:59:59.999Z')) return failure(Error('EXPIRY'));
  return inspect(deps); }
const exitCode = r => r.code === null && r.status === 'QUALIFIED_OFFLINE_COUNT' ? 2 : 1;
if (require.main === module) { const r = runCli(process.argv.slice(2)); try { process.stdout.write(serialize(r)); process.exitCode = exitCode(r); }
  catch (e) { process.stdout.write(serialize(failure(e))); process.exitCode = 1; } }
module.exports = { VERSION, QUOTES, ROOTS, NAMES, LIMITS, DATES, FLAGS, UNKNOWN, DOCUMENTARY, DISPOSITIONS, policy, decode, orient, compare, parsedRow, Reducer, Lines, reader, metadata, serialize, inspect, runCli, exitCode };
