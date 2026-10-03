'use strict';
const v2 = require('./exploratory-probe-v2.cjs');
const { canonical, fingerprint } = require('./exploratory-probe.cjs');
const POLICY = Object.freeze({ version: 'exploratory-backoff-v1', retriesPerQuery: 4, globalRetries: 150,
  delaysMs: [15000, 45000, 120000, 300000], statuses: [529, 503, 429], deadlineMs: 45000, retryAfterAsciiBytes: 128 });
const PRIOR = Object.freeze([
  { version: 'exploratory-sqd-v1', manifestHash: 'sha256:fc1fe02982ec49885e7553b2e35331c9f5444e2fca5924327bd0e4d4eed74a4a', attempts: 29, received: 7962660, disk: 6025734, elapsed: 57272, cashMicrousd: '0' },
  { version: 'exploratory-sqd-v2', manifestHash: 'sha256:5e8dc296b2ce20c9a20e7b9728c202278153516d702bdbbe01762bc45cffb224', attempts: 70, received: 317682, disk: 371714, elapsed: 138949, cashMicrousd: '0' }]);
function cumulative(current) {
  const pre = { attempts: 99, received: 8280342, disk: 6397448, elapsed: 196221, cashMicrousd: '0' }, post = { ...pre };
  for (const key of ['attempts', 'received', 'disk', 'elapsed']) post[key] += current[key];
  return { priorRuns: PRIOR, pre, v3: Object.fromEntries(['attempts', 'received', 'disk', 'elapsed', 'cashMicrousd'].map(k => [k, current[k]])), post };
}
function eligible(r) {
  return r.code === 'HTTP_ERROR' && POLICY.statuses.includes(r.status)
    || r.code === 'TIMEOUT' && r.deadlineOwned === true && [null, 200, ...POLICY.statuses].includes(r.status);
}
function delta(r) {
  if (r.retryAfterInvalid) throw Error('RETRY_AFTER_INVALID');
  if (r.retryAfter === undefined) return null;
  if (typeof r.retryAfter !== 'string' || r.retryAfter.length > 128 || !/^[\x20-\x7e\t]*$/.test(r.retryAfter) || !/^\d+$/.test(r.retryAfter.trim())) throw Error('RETRY_AFTER_INVALID');
  return BigInt(r.retryAfter.trim()).toString();
}
async function retryGroup(q, context, state = { retries: 0, groups: 0 }) {
  const began = context.now(), identity = fingerprint('exploratory-retry-query-v1', q), group = ++state.groups;
  let meta = { group, queryIdentity: identity, ordinal: 0, globalRetryOrdinal: 0, scheduledWaitMs: 0, retryAfterSeconds: null, effectiveWaitMs: '0', actualWaitMs: 0 };
  let received = 0, attempts = 0;
  function result(r) { return { ...r, received, attempts, elapsedMs: Math.max(0, context.now() - began) }; }
  for (;;) {
    const r = await context.attempt(meta), record = r.record ?? r;
    received += r.received || 0; if (r.attempted) { attempts++; if (meta.ordinal) state.retries++; }
    if (!eligible(r)) { record.disposition = 'FINAL'; return result(r); }
    let seconds; try { seconds = delta(r); } catch {
      record.disposition = 'RETRY_AFTER_INVALID'; const prevented = context.prevent({ ...meta, ordinal: meta.ordinal + 1, disposition: 'RETRY_AFTER_INVALID' }, 'RETRY_AFTER_INVALID'); return result(prevented);
    }
    record.normalizedRetryAfterSeconds = seconds;
    if (meta.ordinal >= POLICY.retriesPerQuery || state.retries >= POLICY.globalRetries) {
      record.disposition = meta.ordinal >= POLICY.retriesPerQuery ? 'LOCAL_EXHAUSTED' : 'GLOBAL_EXHAUSTED'; return result(r);
    }
    const scheduled = POLICY.delaysMs[meta.ordinal], headerWait = BigInt(seconds ?? '0') * 1000n;
    const wait = headerWait > BigInt(scheduled) ? headerWait : BigInt(scheduled);
    meta = { group, queryIdentity: identity, ordinal: meta.ordinal + 1, globalRetryOrdinal: state.retries + 1,
      scheduledWaitMs: scheduled, retryAfterSeconds: seconds, effectiveWaitMs: wait.toString(), actualWaitMs: 0 };
    const reservation = wait > 7200000n ? 'TIME_LIMIT' : context.room(Number(wait));
    const code = typeof reservation === 'object' && reservation !== null ? reservation.code : reservation;
    if (typeof reservation === 'object' && reservation !== null) meta.preWaitFreeBytes = reservation.freeBytes;
    if (code) { record.disposition = 'PREVENTED'; return result(context.prevent({ ...meta, disposition: 'BUDGET_PREVENTED' }, code)); }
    record.disposition = 'RETRY'; const before = context.now(); await context.sleep(Number(wait)); meta.actualWaitMs = context.now() - before;
    if (meta.actualWaitMs < Number(wait)) throw Error('INTEGRITY_ERROR');
  }
}
// The same policy is executed with a virtual clock during replay, never real waits or I/O.
async function replayGroup(q, context, state) {
  let time = context.time.value; const touched = [];
  const take = (meta, code, attempted) => {
    const r = context.peek(); if (!r) throw Error('INTEGRITY_ERROR');
    for (const [key, value] of Object.entries(meta)) if (canonical(r[key]) !== canonical(value)) throw Error('INTEGRITY_ERROR');
    if (r.attempted !== attempted || code && r.code !== code || !Number.isSafeInteger(r.startMs) || !Number.isSafeInteger(r.endMs)
      || r.startMs < time || r.endMs < r.startMs || r.endMs > 7200000 || r.elapsedMs < r.endMs - r.startMs) throw Error('INTEGRITY_ERROR');
    const expectedSpacing = attempted || meta.ordinal === 0 ? Math.max(0, 2000 - (time - context.time.lastStart)) : 0;
    if (r.startMs !== time + expectedSpacing || r.elapsedMs !== r.endMs - time) throw Error('INTEGRITY_ERROR');
    if (attempted) { if (r.requestDeadlineMs !== 45000 || r.code === 'TIMEOUT' && (!r.deadlineOwned || r.endMs - r.startMs < 45000)) throw Error('INTEGRITY_ERROR'); context.time.lastStart = r.startMs; }
    const result = context.take(q, attempted); time = r.endMs; context.time.value = time; touched.push([r, result.record ?? result]); return result;
  };
  const result = await retryGroup(q, { now: () => time,
    sleep: async ms => { const r = context.peek(); if (!r || !Number.isSafeInteger(r.actualWaitMs) || r.actualWaitMs < ms) throw Error('INTEGRITY_ERROR');
      time += r.actualWaitMs; },
    room: wait => context.room(time, wait, true),
    attempt: meta => {
      const r = context.peek(); if (r?.attempted === false) { const code = context.room(time + Math.max(0, 2000 - (time - context.time.lastStart)), 0);
        if (!code || code !== r.code) throw Error('INTEGRITY_ERROR'); return take(meta, code, false); }
      return take(meta, null, true);
    }, prevent: (meta, code) => take(meta, code, false) }, state);
  for (const [stored, calculated] of touched) for (const key of ['disposition', 'normalizedRetryAfterSeconds'])
    if (canonical(stored[key] ?? null) !== canonical(calculated[key] ?? null)) throw Error('INTEGRITY_ERROR');
  context.time.value = time; return result;
}
module.exports = { ...v2.createV3({ POLICY, PRIOR, retryGroup, replayGroup, cumulative }), retryGroup };
