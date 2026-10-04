'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const c = require('../e2-monthly-wait-guard-v2.cjs'), frozen = require('../e2-monthly-census.cjs');
// Synthetic prior accounting exercises the wait contract only; the pinned historical artifact check lives under test/local.
const syntheticApril = { version: 'e2-monthly-census-v1', configuration: frozen.configuration('april'), status: 'INCOMPLETE',
  records: Array.from({ length: 13075 }, () => ({ received: 0, retry: 0 })),
  accounting: { attempts: 13075, retries: 94, received: 0, retained: 0, retainedKnown: true },
  publicationOperational: { elapsedMs: 0 }, runBudget: 'UNMEASURED' };
function memory() { const files = new Map(); let created = false; return { files, free: () => 100000000000, absent: () => !created,
  create() { assert.equal(created, false); created = true; }, write(n, b) { assert.equal(files.has(n), false); files.set(n, Buffer.from(b)); },
  read(n) { assert.ok(files.has(n), n); return files.get(n); }, list: () => [...files.keys()], size: n => files.get(n)?.length ?? 0 }; }
const opts = () => ({ enabled: true, month: 'may', retainedBefore: '4200000000', attemptsBefore: '13075', retriesBefore: '94' });
function fixture(kind) { let time = 0; const calls = [], waits = [], store = memory();
  const deps = { store, calls, waits, now: () => time, utcNow: () => Date.parse('2026-10-04T04:30:00Z'),
    source: { commit: 'a'.repeat(40), dirty: true }, runtime: 'v24.19.0', verifyBoundary: () => ({ status: 'VERIFIED', bounds: frozen.BOUNDS }),
    wait: async ms => { waits.push(ms); time += ms > 1 ? ms - 1 : ms; },
    transport: async (q, o) => { calls.push({ start: time, q });
      if (kind !== 'spacing' && calls.length <= (kind === 'second' ? 2 : 1)) { o.onChunk(1); return { status: 529, received: 1, retryAfter: kind === 'retryAfter' ? '20' : null }; }
      const end = kind === 'spacing' && calls.length === 1 ? q.slot : q.body.toBlock;
      const bytes = Buffer.from(JSON.stringify({ header: { number: end, hash: '1'.repeat(32), parentNumber: end - 1,
        parentHash: '1'.repeat(32), timestamp: 1777593600 } }) + '\n'); o.onChunk(bytes.length); return { status: 200, bytes, received: bytes.length }; } };
  return deps;
}
async function guardedRun(options, deps, preceding = [syntheticApril]) {
  const priorElapsed = preceding.reduce((sum, manifest) => sum + manifest.publicationOperational.elapsedMs, 0);
  const clock = c.checkedClockAndWait(deps.now, deps.wait, priorElapsed);
  try { return await frozen.run(options, { ...deps, now: clock.now, wait: clock.wait, previous: () => preceding,
    source: { ...deps.source, operationalGuard: c.identity() } }); }
  catch (error) { return frozen.error(['TIME_LIMIT', 'ORDER_INVALID'].includes(error.message) ? error.message : 'INTEGRITY_ERROR'); }
}
for (const [kind, expected, at] of [['first', 15000, 1], ['second', 60000, 2], ['retryAfter', 20000, 1], ['spacing', 250, 1]]) {
  test('Wait guard observes full deadline ' + kind + ' through frozen runner', async () => {
    const d = fixture(kind), result = await guardedRun(opts(), d); assert.equal(result.code, null);
    assert.equal(d.calls[at].start, expected, 'transport starts only after the same recorded clock reaches the full requested wait');
  });
}
test('Guard source lineage pins operational wrappers and strict replay is read only', async () => {
  const d = fixture('second'), r = await guardedRun(opts(), d); assert.equal(r.code, null); assert.ok(d.waits.includes(1));
  const guard = r.summary.lineage.source.operationalGuard; assert.deepEqual(guard, c.identity());
  assert.equal(r.summary.version, 'e2-monthly-census-v1'); assert.equal(guard.version, 'e2-monthly-wait-guard-v2');
  const ro = { read: d.store.read, list: d.store.list }, rr = await c.replay('may', ro);
  assert.equal(rr.code, null); assert.equal(rr.summaryHash, r.summaryHash); assert.equal(rr.runBudget, 'UNMEASURED'); assert.equal(c.exitCode(rr), 0);
  const saved = JSON.parse(d.store.read('manifest.json'));
  for (const change of [m => { delete m.lineage.source.operationalGuard; }, m => { m.lineage.source.operationalGuard.policy = 'TOLERANCE'; },
    m => { m.lineage.source.operationalGuard.scripts['e2-monthly-wait-guard-v2.cjs'] = 'sha256:' + '0'.repeat(64); }]) {
    const m = structuredClone(saved); change(m); d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
    assert.equal((await c.replay('may', ro)).code, 'INTEGRITY_ERROR'); }
});
test('Guard stalls invalid clocks and rollback stop before additional transport', async () => {
  for (const n of [NaN, Infinity, 1.5, -1]) { const d = fixture('first'); d.now = () => n;
    assert.equal((await guardedRun(opts(), d)).code, 'TIME_LIMIT'); assert.equal(d.calls.length, 0); }
  const stall = fixture('first'); stall.wait = async () => {};
  assert.equal((await guardedRun(opts(), stall)).code, 'TIME_LIMIT'); assert.equal(stall.calls.length, 1);
  let clock = 100, reads = 0; const rollback = fixture('spacing'); rollback.now = () => clock;
  rollback.store.free = () => { if (++reads === 2) clock = 99; return 100000000000; };
  const r = await guardedRun(opts(), rollback); assert.equal(r.code, 'TIME_LIMIT'); assert.equal(rollback.calls.length, 0);
  assert.equal(r.accounting.attempts, 0);
});
test('Guard extra wait reserves unchanged full timeout and cumulative time', async () => {
  let time = 0, waits = 0; const adapter = c.checkedClockAndWait(() => time, async ms => { waits++; time += ms - 1; }, 0);
  await adapter.wait(1).then(() => assert.fail('stalled wait must stop'), e => assert.equal(e.message, 'TIME_LIMIT'));
  assert.equal(waits, 1);
  const bounded = c.checkedClockAndWait(() => time, async ms => { time += ms; }, frozen.TOTAL.elapsedMs - frozen.LIMITS.deadline);
  await assert.rejects(bounded.wait(1), /TIME_LIMIT/);
  const d = fixture('first'); let late = 0, count = 0; d.now = () => late;
  d.store.free = () => { if (++count === 2) late = frozen.LIMITS.sourceMs - frozen.LIMITS.deadline + 1; return 100000000000; };
  const r = await guardedRun(opts(), d); assert.equal(r.code, 'TIME_LIMIT'); assert.equal(d.calls.length, 0);
});
test('Guard June requires finalized guarded May lineage and matching cumulative counters', async () => {
  const d = fixture('spacing'); const r = await guardedRun(opts(), d); assert.equal(r.code, null);
  const jm = c.verifyMetadata('may', d.store); assert.deepEqual(jm.lineage.source.operationalGuard, c.identity());
  const june = fixture('spacing');
  const o = { ...opts(), month: 'june', attemptsBefore: String(13075 + r.accounting.attempts), retriesBefore: '94' };
  // The inline fixture's May timestamp is intentionally not a June response: preparation/order must succeed, then parser rejects scope.
  const jr = await guardedRun(o, june, [syntheticApril, jm]); assert.equal(jr.code, 'RESPONSE_INVALID'); assert.equal(june.calls.length, 1);
  const m = JSON.parse(d.store.read('manifest.json')); delete m.lineage.source.operationalGuard;
  d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
  assert.throws(() => c.verifyMetadata('may', d.store), /INTEGRITY_ERROR/);
  const bad = fixture('first'); const br = await guardedRun({ ...opts(), attemptsBefore: '0' }, bad);
  assert.equal(br.code, 'ORDER_INVALID'); assert.equal(bad.calls.length, 0);
});
test('Guard CLI disabled invalid April and unsafe scalar flags do zero I/O', async () => {
  const cli = require('../e2-monthly-wait-guard-v2-cli.cjs'), forbidden = { priorStore() { assert.fail('unexpected I/O'); }, transport() { assert.fail('unexpected source'); } };
  for (const args of [[], ['--replay', '--month', 'april'], ['--enable-public', '--month', 'april', '--retained-before', '0', '--attempts-before', '0', '--retries-before', '0'],
    ['--enable-public', '--month', 'may', '--retained-before', '01', '--attempts-before', '13075', '--retries-before', '94'], ['--enable-public', '--host', 'other']]) {
    assert.equal(c.exitCode(await cli.runCli(args, forbidden)), 1); }
  assert.equal((await c.replay('april', { read() { assert.fail('April replay forbidden'); } })).code, 'ARGUMENTS_INVALID');
});
