'use strict';
const frozen = require('./e2-monthly-census.cjs');
const fs = require('node:fs'), path = require('node:path');
const { execFileSync } = require('node:child_process');
const { parse, canonical, digest, fingerprint } = require('./exploratory-probe.cjs');
const VERSION = 'e2-monthly-wait-guard-v2', POLICY = 'OBSERVED_MINIMUM_WAIT_NO_TOLERANCE';
const PINS = Object.freeze({ ...frozen.PINS,
  'e2-monthly-census.cjs': 'sha256:b44c5e652408cd0ac89e104fcba88f808c8e5318c2ebca92f499c0358df28f31',
  'e2-monthly-census-cli.cjs': 'sha256:75aedfcdb8a9cee89bb9f8a366a213f296971b11e4c0b451e71703a73e3a3f42' });
const APRIL = Object.freeze({ manifestBytes: 14427185, manifestHash: 'sha256:398c6c05f02bb174e374d513161e710eab270ea5b128f857e1b160948c780612',
  summaryBytes: 1964, summaryHash: 'sha256:c4f9916f8a1637095f31d1d03917df52a47b6dc570fa27c9853513990b851a7f',
  attempts: 13075, retries: 94, received: 149613720, retained: 206425601, elapsedMs: 8483802 });
const need = (yes, code = 'INTEGRITY_ERROR') => { if (!yes) throw Error(code); };
const integer = n => Number.isSafeInteger(n) && n >= 0;
function fileBytes(name) { const file = path.join(__dirname, name), s = fs.lstatSync(file);
  need(s.isFile() && !s.isSymbolicLink() && s.size <= 1000000); return fs.readFileSync(file); }
function identity() { for (const [n, h] of Object.entries(PINS)) need(digest(fileBytes(n)) === h);
  const scripts = {}; for (const n of ['e2-monthly-wait-guard-v2.cjs', 'e2-monthly-wait-guard-v2-cli.cjs']) scripts[n] = digest(fileBytes(n));
  return { version: VERSION, clock: 'Date.now', policy: POLICY, scripts }; }
function verifyGuard(source) { need(canonical(source?.operationalGuard) === canonical(identity())); }
function verifyMetadata(month, store) {
  const mb = store.read('manifest.json'), sb = store.read('summary.json'); need(mb.length <= frozen.LIMITS.metadata && sb.length <= frozen.LIMITS.summary);
  const m = parse(mb, false), s = parse(sb, false);
  need(m.version === frozen.configuration(month).version && canonical(m.configuration) === canonical(frozen.configuration(month)));
  need(canonical(m.lineage.scripts) === canonical(PINS) && canonical(s.lineage) === canonical(m.lineage)
    && m.summaryHash === fingerprint(frozen.configuration(month).canonicalVersion, s)
    && m.accounting.retainedKnown === true && m.accounting.manifestBytes === mb.length
    && m.accounting.attempts === m.records.length && m.status === s.status && m.code === s.code);
  need(m.files.some(f => f.name === 'summary.json' && f.bytes === sb.length && f.hash === digest(sb)));
  need(new Set(m.files.map(f => f.name)).size === m.files.length
    && m.files.every(f => integer(f.bytes) && /^sha256:[a-f0-9]{64}$/.test(f.hash))
    && m.accounting.retained === mb.length + m.files.reduce((n, f) => n + f.bytes, 0)
    && m.accounting.received === m.records.reduce((n, r) => n + r.received, 0)
    && m.accounting.retries === m.records.filter(r => r.retry > 0).length);
  if (month === 'april') {
    need(mb.length === APRIL.manifestBytes && digest(mb) === APRIL.manifestHash && sb.length === APRIL.summaryBytes && digest(sb) === APRIL.summaryHash);
    for (const k of ['attempts', 'retries', 'received', 'retained']) need(m.accounting[k] === APRIL[k]);
    need(m.publicationOperational.elapsedMs === APRIL.elapsedMs);
  } else verifyGuard(m.lineage.source);
  return m;
}
function prior(month, readStore = frozen.fileStore) {
  need(['may', 'june'].includes(month), 'ARGUMENTS_INVALID');
  return (month === 'may' ? ['april'] : ['april', 'may']).map(m => verifyMetadata(m, readStore(m)));
}
function checkedClockAndWait(clock, sleeper, priorElapsed) {
  let last = null, fault = false, origin = null;
  function now() {
    // A terminal clock fault has already stopped the frozen source loop. Keep its last valid value only for final accounting/publication.
    if (fault) return last ?? 0;
    const n = clock(); if (!integer(n) || (last !== null && n < last)) { fault = true; throw Error('TIME_LIMIT'); }
    last = n; if (origin === null) origin = n; return n;
  }
  async function wait(pause) {
    need(integer(pause), 'TIME_LIMIT'); const begun = now(), deadline = begun + pause; need(integer(deadline), 'TIME_LIMIT');
    let observed = begun;
    while (observed < deadline) {
      const remaining = deadline - observed, elapsed = observed - origin;
      need(elapsed + remaining + frozen.LIMITS.deadline <= frozen.LIMITS.sourceMs
        && priorElapsed + elapsed + remaining + frozen.LIMITS.deadline <= frozen.TOTAL.elapsedMs, 'TIME_LIMIT');
      await sleeper(remaining); const next = now(); need(next > observed, 'TIME_LIMIT'); observed = next;
    }
  }
  return { now, wait, failed: () => fault };
}
function validOptions(o) { return o && o.enabled === true && ['may', 'june'].includes(o.month)
  && Object.keys(o).every(k => ['enabled', 'month', 'retainedBefore', 'attemptsBefore', 'retriesBefore'].includes(k))
  && ['retainedBefore', 'attemptsBefore', 'retriesBefore'].every(k => typeof o[k] === 'string' && /^(0|[1-9][0-9]*)$/.test(o[k]) && integer(Number(o[k]))); }
async function run(options = {}, deps = {}) {
  if (options.enabled !== true) return frozen.error('DISABLED');
  if (!validOptions(options)) return frozen.error('ARGUMENTS_INVALID');
  try {
    const guardIdentity = identity(), preceding = prior(options.month, deps.priorStore ?? frozen.fileStore);
    const totals = frozen.priorTotals(options.month, preceding, Number(options.attemptsBefore), Number(options.retriesBefore));
    const cwd = path.resolve(__dirname, '../../..'), source = deps.source ?? {
      commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd, encoding: 'utf8', timeout: 5000 }).trim(),
      dirty: !!execFileSync('git', ['status', '--porcelain'], { cwd, encoding: 'utf8', timeout: 5000 }).trim() };
    const adapter = checkedClockAndWait(deps.now ?? Date.now, deps.wait ?? (ms => new Promise(r => setTimeout(r, ms))), totals.elapsedMs);
    return await frozen.run(options, { ...deps, now: adapter.now, wait: adapter.wait, previous: () => preceding,
      source: { ...source, operationalGuard: guardIdentity } });
  } catch (e) { return frozen.error(['TIME_LIMIT', 'ORDER_INVALID'].includes(e.message) ? e.message : 'INTEGRITY_ERROR'); }
}
async function replay(month, store) {
  if (!['may', 'june'].includes(month)) return frozen.error('ARGUMENTS_INVALID');
  try { identity(); const input = store ?? frozen.fileStore(month), bytes = input.read('manifest.json');
    need(bytes.length <= frozen.LIMITS.metadata); const m = parse(bytes, false);
    verifyGuard(m.lineage.source); return await frozen.replay(month, input);
  } catch { return frozen.error('INTEGRITY_ERROR'); }
}
module.exports = { VERSION, POLICY, PINS, APRIL, identity, verifyMetadata, prior, checkedClockAndWait, run, replay, exitCode: frozen.exitCode };
