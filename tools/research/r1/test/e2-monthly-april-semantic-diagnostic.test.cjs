'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const c = require('../e2-monthly-census.cjs'), d = require('../e2-monthly-april-semantic-diagnostic.cjs');
const { canonical, digest, fingerprint } = require('../exploratory-probe.cjs');
const fs = require('node:fs'), path = require('node:path');
const json = x => Buffer.from(JSON.stringify(x) + '\n');
function fixture(early = false) {
  const config = c.configuration('april'), files = new Map(), key = '11111111111111111111111111111111';
  const scripts = { ...c.PINS };
  for (const n of ['e2-monthly-census.cjs', 'e2-monthly-census-cli.cjs']) scripts[n] = digest(fs.readFileSync(path.join(__dirname, '..', n)));
  const lineage = { runtime: 'v24.19.0', source: { commit: 'a'.repeat(40), dirty: true }, scripts,
    configHash: fingerprint(config.canonicalVersion, config), proof: { status: 'VERIFIED', bounds: c.BOUNDS, manifestHash: c.SEAL.manifestHash, controlBytes: 880 },
    priorAccounting: { source: 'MAIN_ATTESTED_AND_FINALIZED_MONTHLY_METADATA', bytes: 1000000, attempts: 0, retries: 0, received: 0, retained: 0, elapsedMs: 0 } };
  const raw = json({ header: { number: 416762081, hash: key, parentNumber: 416762080, parentHash: key, timestamp: 1777593599 } });
  const q = c.query('april'), state = c.initial('april'), a = c.admit(raw, q, 'april'); c.apply(state, a, c.prepare(state, a));
  const { headers, creations, serializedCreations, ...stream } = state;
  const flags = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, cohortAdmitted: false, authoritativeCensusComplete: false, fullD1UpperBound: null, fullD1Fits: null };
  const summary = { ...flags, version: config.version, month: 'april', code: null, status: 'SCAN_COMPLETE', stream, creations: 0, storedHeaders: 1, lineage,
    sourceCoverage: 'REFERENCE_BOUNDARIES_ONLY', fallback: 'NOT_RUN', retention: 'UNVERIFIED', expiry: config.expiry,
    gaps: ['DEPLOYED_VERSION_UNKNOWN', 'INNER_INGESTION_UNKNOWN', 'GLOBAL_EARLIEST_UNKNOWN', 'ALL_POOL_COVERAGE_UNKNOWN', 'PIT_KNOWN_AT_UNKNOWN', 'RIGHTS_UNKNOWN'] };
  files.set('attempt.json', json({ ...flags, month: 'april', startedAt: '2026-10-04T01:51:30.407Z', lineage, expiry: config.expiry, interruptedDisposition: 'No final manifest is INCOMPLETE; resume forbidden' }));
  files.set('00002.raw', raw); files.set('summary.json', json(summary)); files.set('creations.jsonl', Buffer.alloc(0));
  const common = { query: q, queryHash: fingerprint('e2-monthly-query-v1', q), received: 0, raw: null, startMs: 0, endMs: 0 };
  const records = [{ ...common, ordinal: 0, retry: 0, waitMs: 0, status: 529, code: 'HTTP_ERROR', retryAfterSeconds: null },
    { ...common, ordinal: 1, retry: 1, waitMs: 15000, startMs: early ? 14996 : 15000, endMs: 15001, status: 200, code: null, received: raw.length, raw: '00002.raw', rawHash: digest(raw), rawBytes: raw.length }];
  const manifest = { ...flags, version: config.version, configuration: config, lineage, records, files: [], code: null, status: 'SCAN_COMPLETE',
    summaryHash: fingerprint(config.canonicalVersion, summary), accounting: { attempts: 2, retries: 1, received: raw.length, retained: 0, partialBytes: 0, retainedKnown: true, cashMicrousd: '0', checkpoints: [], manifestBytes: 0 },
    publicationOperational: { elapsedMs: 15001, endedAt: '2026-10-04T01:51:45.408Z' }, runBudget: 'UNMEASURED' };
  function settle() { manifest.files = [...files].filter(([n]) => n !== 'manifest.json').map(([name, b]) => ({ name, bytes: b.length, hash: digest(b) }));
    for (let i = 0; i < 20; i++) { const b = json(manifest); manifest.accounting.manifestBytes = b.length; manifest.accounting.retained = b.length + manifest.files.reduce((n, f) => n + f.bytes, 0); }
    files.set('manifest.json', json(manifest)); }
  settle(); return { files, manifest, settle, read: n => { assert.ok(files.has(n), n); return files.get(n); }, list: () => [...files.keys()], size: n => files.get(n)?.length };
}
test('Diagnostic rejects rehashed creation corruption rather than accepting metadata hashes alone', () => {
  const store = fixture(); store.files.set('creations.jsonl', Buffer.from('{"invented":true}\n')); store.settle();
  assert.equal(d.inspect(store).semanticIntegrity, 'FAIL', 'ordered creations must reproduce admitted raw semantics');
});
test('Diagnostic preserves semantic PASS while reporting exact early recorded wait FAIL', () => {
  const r = d.inspect(fixture(true));
  assert.equal(r.semanticIntegrity, 'PASS');
  assert.equal(r.recordedTiming, 'FAIL', '14996ms observed is below exact 15000ms retry deadline');
});
module.exports = { fixture };
test('Diagnostic literal summary formatter, creation comparator and byte identities match frozen format', () => {
  const store = fixture(), state = c.initial('april'), raw = store.read('00002.raw'), a = c.admit(raw, c.query('april'), 'april');
  c.apply(state, a, c.prepare(state, a));
  assert.ok(json(d.summary(state, null, store.manifest.lineage)).equals(store.read('summary.json')));
  const ordered = [{ slot: 1, transactionIndex: 1, instructionPath: [2], signature: 'z' },
    { slot: 1, transactionIndex: 1, instructionPath: [2, 0], signature: 'a' },
    { slot: 1, transactionIndex: 1, instructionPath: [10], signature: 'a' },
    { slot: 1, transactionIndex: 2, instructionPath: [0], signature: 'a' },
    { slot: 2, transactionIndex: 0, instructionPath: [0], signature: 'a' }];
  assert.deepEqual([...ordered].reverse().sort(d.compare), ordered);
  assert.ok(d.compare({ ...ordered[0], signature: 'A' }, { ...ordered[0], signature: 'a' }) < 0);
  const r = d.inspect(store); assert.equal(r.semanticIntegrity, 'PASS'); assert.equal(r.recordedTiming, 'PASS'); assert.equal(d.exitCode(r), 2);
  assert.equal(r.overallStrictPass, false); assert.equal(r.historicalStrictReplay, 'INTEGRITY_ERROR');
  assert.equal(r.manifestHash, digest(store.read('manifest.json'))); assert.equal(r.summaryHash, digest(store.read('summary.json')));
  assert.equal(r.creationsHash, digest(Buffer.alloc(0))); assert.equal(r.semanticSummaryHash, store.manifest.summaryHash);
  assert.equal(r.inspectedFiles, 5); assert.equal(r.inspectedBytes, store.manifest.accounting.retained);
  assert.equal(r.d1Passed, false); assert.equal(r.cohortAdmitted, false);
});
test('Diagnostic semantic defect matrix cannot hide behind genuine timing FAIL', () => {
  const changes = [s => s.files.delete('00002.raw'), s => s.files.set('extra.raw', Buffer.alloc(0)),
    s => s.files.set('00002.raw', Buffer.concat([s.read('00002.raw'), Buffer.from('!')])),
    s => { s.manifest.records[1].queryHash = 'sha256:' + '0'.repeat(64); },
    s => { s.manifest.records[1].query.body.includeAllBlocks = true; for (const r of s.manifest.records) r.queryHash = fingerprint('e2-monthly-query-v1', r.query); },
    s => { s.manifest.accounting.received++; }, s => { s.manifest.records[1].ordinal = 2; },
    s => { s.manifest.records[1].retry = 2; }, s => { s.manifest.records[1].waitMs = 14999; },
    s => { s.manifest.records[1].startMs = 1.5; }, s => { s.manifest.lineage.source.commit = 'invalid'; },
    s => { s.manifest.lineage.scripts['e2-monthly-census.cjs'] = 'sha256:' + '0'.repeat(64); },
    s => { s.manifest.lineage.proof.controlBytes++; }, s => { s.manifest.configuration = structuredClone(s.manifest.configuration); s.manifest.configuration.limits.response++; },
    s => { s.manifest.accounting.retainedKnown = false; }, s => { s.manifest.files.push({ ...s.manifest.files[0] }); },
    s => { const v = JSON.parse(s.read('summary.json')); v.stream.pages++; s.files.set('summary.json', json(v)); s.manifest.summaryHash = fingerprint(c.configuration('april').canonicalVersion, v); },
    s => { const v = JSON.parse(s.read('attempt.json')); v.expiry = '2099-01-01'; s.files.set('attempt.json', json(v)); }];
  changes.forEach((change, index) => { const s = fixture(true); change(s);
    // Rehash changed bytes; this challenges semantic checking, not merely stale file digests.
    if (index !== 15) s.settle(); else s.files.set('manifest.json', json(s.manifest));
    const r = d.inspect(s); assert.equal(r.semanticIntegrity, 'FAIL', `semantic defect ${index}`); assert.equal(d.exitCode(r), 1); assert.equal(r.overallStrictPass, false);
    assert.equal(Object.hasOwn(r, 'semanticSummaryHash'), false);
  });
});
test('Diagnostic reports unchanged exact 15/45/RetryAfter, spacing and chronology inequalities', () => {
  const early = fixture(true), r = d.inspect(early);
  assert.deepEqual(r.timing.failures, [{ ordinal: 1, check: 'ACTUAL_RETRY_WAIT', required: 15000, actual: 14996, deficit: 4 }]);
  for (const [after, retry, recorded, required] of [[null, 1, 14996, 15000], ['20', 1, 19999, 20000], [null, 2, 44998, 45000]]) {
    const s = fixture(); const records = s.manifest.records;
    if (retry === 2) { records.splice(1, 0, { ...structuredClone(records[0]), ordinal: 1, retry: 1, waitMs: 15000, startMs: 15000, endMs: 15000 });
      records[2].ordinal = 2; records[2].retry = 2; s.files.set('00003.raw', s.read('00002.raw')); s.files.delete('00002.raw'); records[2].raw = '00003.raw'; s.manifest.accounting.attempts = 3; s.manifest.accounting.retries = 2; }
    const p = records.at(-2), last = records.at(-1); p.retryAfterSeconds = after === null ? null : Number(after);
    last.waitMs = required; last.startMs = p.endMs + recorded; last.endMs = last.startMs; s.manifest.publicationOperational.elapsedMs = last.endMs; s.settle();
    const result = d.inspect(s); assert.equal(result.semanticIntegrity, 'PASS'); assert.equal(result.recordedTiming, 'FAIL');
    assert.ok(result.timing.failures.some(f => f.check === 'ACTUAL_RETRY_WAIT' && f.required === required && f.actual === recorded));
  }
  const bad = fixture(); bad.manifest.records[0].endMs = 15001; bad.manifest.records[1].startMs = 100; bad.manifest.records[1].endMs = 99; bad.settle();
  const b = d.inspect(bad); assert.equal(b.semanticIntegrity, 'PASS');
  assert.deepEqual(b.timing.failures.map(f => f.check), ['START_END_CHRONOLOGY', 'PREVIOUS_END_START', 'MINIMUM_START_SPACING', 'ACTUAL_RETRY_WAIT']);
});
test('Diagnostic finite input and elapsed guards stop without identity or successful result', () => {
  for (const setup of [s => { s.list = () => ['manifest.json', 'manifest.json']; }, s => { s.list = () => Array(16001).fill('manifest.json'); },
    s => { s.size = () => 64000001; }, s => { s.list = () => ['../manifest.json']; }]) {
    const s = fixture(); let reads = 0; const read = s.read; s.read = n => { reads++; return read(n); }; setup(s);
    const r = d.inspect(s); assert.equal(r.semanticIntegrity, 'FAIL'); assert.equal(reads, 0); assert.equal(d.exitCode(r), 1);
  }
  for (const clock of [() => NaN, () => Infinity, () => 0.5, (() => { let n = 0; return () => n++ ? 1800001 : 0; })(), (() => { let n = 0; return () => n++ ? 0 : 1; })()]) {
    const r = d.inspect(fixture(), { now: clock }); assert.equal(r.semanticIntegrity, 'FAIL'); assert.equal(r.code, 'TIME_LIMIT'); assert.equal(Object.hasOwn(r, 'manifestHash'), false);
  }
});
test('Diagnostic disabled and invalid CLI is zero evidence IO; exact enable enforces April pins and deadline', () => {
  let calls = 0; const deps = { now: () => 0, store: () => { calls++; return fixture(); } };
  for (const args of [[], ['--enable-offline', '--month', 'april'], ['--replay'], ['--enable-offline', '--root', 'other']]) {
    const r = d.runCli(args, deps); assert.equal(d.exitCode(r), 1); assert.equal(r.semanticIntegrity, 'UNKNOWN'); }
  assert.equal(calls, 0);
  assert.equal(d.exitCode(d.runCli(['--enable-offline'], deps)), 1); assert.equal(calls, 1, 'synthetic manifest cannot impersonate pinned real April');
  assert.equal(d.exitCode(d.runCli(['--enable-offline'], { ...deps, now: () => d.DEADLINE + 1 })), 1); assert.equal(calls, 1);
});
test('Diagnostic positive linked creations reproduce exact ordered nonempty bytes and reject reordered or conflicting evidence', () => {
  function b58(bytes) { const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let value = BigInt('0x' + bytes.toString('hex')), result = '', zeros = 0;
    while (zeros < bytes.length && bytes[zeros] === 0) zeros++; while (value) { result = alphabet[Number(value % 58n)] + result; value /= 58n; } return '1'.repeat(zeros) + result; }
  const s = fixture(), key = '11111111111111111111111111111111', signature = '1'.repeat(64), paths = [[10], [2, 0], [2]];
  const block = { header: { number: 416762081, hash: key, parentNumber: 416762080, parentHash: key, timestamp: 1777593599 },
    transactions: [{ transactionIndex: 2, signatures: [signature], err: null }],
    instructions: paths.map(instructionAddress => ({ transactionIndex: 2, instructionAddress, programId: s.manifest.configuration.program,
      accounts: Array(5).fill(key), data: b58(Buffer.from('e992d18ecf6840bc000102', 'hex')), isCommitted: true, error: null })) };
  const raw = json(block), a = c.admit(raw, c.query('april'), 'april'), state = c.initial('april'); assert.equal(a.code, null); assert.equal(a.creations.length, 3);
  c.apply(state, a, c.prepare(state, a));
  const literal = [[2], [2, 0], [10]].map(instructionPath => ({ status: 'OBSERVED_DECLARED_CREATE_POOL', slot: 416762081, timestamp: 1777593599,
    signature, transactionIndex: 2, instructionPath, cpiDepth: instructionPath.length - 1, pool: key, globalConfig: key, creator: key, baseMint: key, quoteMint: key,
    accountCount: 5, dataLength: 11, rawHash: digest(raw), lineOrdinal: 1, layoutApplicability: 'UNVERIFIED' }));
  const { headers, creations, serializedCreations, ...stream } = state, summary = JSON.parse(s.read('summary.json'));
  summary.stream = stream; summary.creations = 3; summary.storedHeaders = 1;
  s.files.set('00002.raw', raw); s.files.set('creations.jsonl', Buffer.concat(literal.map(json))); s.files.set('summary.json', json(summary));
  s.manifest.summaryHash = fingerprint(s.manifest.configuration.canonicalVersion, summary);
  Object.assign(s.manifest.records[1], { received: raw.length, rawBytes: raw.length, rawHash: digest(raw) }); s.manifest.accounting.received = raw.length; s.settle();
  const r = d.inspect(s); assert.equal(r.semanticIntegrity, 'PASS'); assert.equal(r.creations, 3); assert.equal(d.exitCode(r), 2);
  s.files.set('creations.jsonl', Buffer.concat([...literal].reverse().map(json))); s.settle(); assert.equal(d.inspect(s).semanticIntegrity, 'FAIL');
  const conflict = structuredClone(block); conflict.instructions.push({ ...conflict.instructions[0], accounts: [b58(Buffer.alloc(32, 2)), ...Array(4).fill(key)] });
  const badRaw = json(conflict); s.files.set('00002.raw', badRaw); Object.assign(s.manifest.records[1], { received: badRaw.length, rawBytes: badRaw.length, rawHash: digest(badRaw) });
  s.manifest.accounting.received = badRaw.length; s.settle(); assert.equal(d.inspect(s).semanticIntegrity, 'FAIL');
});
