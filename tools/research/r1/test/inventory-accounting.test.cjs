'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), { validateInventory } = require('../inventory.cjs');
const { runCli } = require('../inventory-cli.cjs');
const candidate = () => JSON.parse(fs.readFileSync(require.resolve('../inventory-candidates.json'), 'utf8'));
function admitted() {
  const input = candidate(), source = input.sources[0];
  Object.assign(source, { selected: true, sourceVersion: 'synthetic-v1', queryVersion: 'synthetic-v1', costUpperMicrousd: '100000000' });
  const ref = { url: 'https://example.invalid/synthetic', retrievedAt: '2026-10-01T00:00:00Z', version: 'v1', claim: 'Synthetic only.' };
  source.evidence = [ref]; source.retention = { status: 'CONFIRMED', evidence: [ref] };
  for (const field of input.fields) Object.assign(field, { status: 'UNAVAILABLE', sourceIds: [], coveredFrom: null,
    coveredTo: null, granularity: null, gaps: ['Unavailable; full-envelope dates and granularity unknown.'], evidence: [] });
  return input;
}
const codes = list => list.map(b => b.code);
test('explicit unavailable partial and gapped accounting is complete but never measured D1', () => {
  const input = admitted(); Object.assign(input.fields[0], { status: 'DOCUMENTED', sourceIds: [input.sources[0].id],
    coveredFrom: input.envelope.from, coveredTo: '2026-05-01T00:00:00Z', granularity: 'per-event', evidence: input.sources[0].evidence });
  const r = validateInventory(input);
  assert.equal(r.classification, 'INVENTORY_COMPLETE'); assert.equal(r.accountingComplete, true);
  assert.equal(r.reportVersion, 'r1-d1-inventory-report-v2'); assert.equal(r.availabilityStatus, 'DECLARED_INCOMPLETE_UNMEASURED');
  assert.deepEqual(r.blockers, []); assert.equal(r.runAuthorized, false); assert.equal(r.d1Passed, false);
  for (const code of ['FIELD_NOT_CONFIRMED', 'FIELD_SELECTED_SOURCE_MISSING', 'FIELD_COVERAGE_INCOMPLETE',
    'FIELD_GRANULARITY_UNKNOWN', 'FIELD_GAPS', 'FIELD_EVIDENCE_MISSING']) assert.ok(codes(r.availabilityDiagnostics).includes(code));
});
test('missing unavailable reason or unexplained unknown metadata blocks accounting', () => {
  for (const mutate of [f => f.status = 'UNVERIFIED', f => f.gaps = [],
    f => { f.sourceIds = ['dune-solana-dex']; f.status = 'UNVERIFIED'; f.gaps = []; }]) {
    const input = admitted(); mutate(input.fields[0]); const r = validateInventory(input);
    assert.equal(r.accountingComplete, false); assert.equal(r.classification, 'INVENTORY_BLOCKED');
    assert.ok(r.blockers.some(b => b.code === 'FIELD_ACCOUNTING_INCOMPLETE' && b.fieldId === input.fields[0].id));
  }
});
test('source admission and exact cost remain independent of complete accounting', () => {
  for (const [mutate, code] of [[s => s.selected = false, 'SOURCE_SELECTION_MISSING'],
    [s => s.costUpperMicrousd = '100000001', 'COST_ABOVE_CEILING'], [s => s.costUpperMicrousd = null, 'SOURCE_COST_UNKNOWN'],
    [s => s.sourceVersion = null, 'SOURCE_VERSION_UNKNOWN'], [s => s.queryVersion = null, 'QUERY_VERSION_UNKNOWN'],
    [s => s.evidence = [], 'SOURCE_EVIDENCE_MISSING'], [s => s.retention.status = 'UNVERIFIED', 'SOURCE_RETENTION_UNCONFIRMED']]) {
    const input = admitted(); mutate(input.sources[0]); const r = validateInventory(input);
    assert.equal(r.accountingComplete, true); assert.equal(r.classification, 'INVENTORY_BLOCKED'); assert.ok(codes(r.blockers).includes(code));
  }
  const r = validateInventory(candidate()); assert.equal(r.fingerprint, 'sha256:f9f2fb5450ce37e923892979e5b6a180e7e82fcbfe25ef44c7362fb2c972888f');
  assert.equal(r.selectedCostUpperMicrousd, '0'); assert.ok(codes(r.blockers).includes('SOURCE_SELECTION_MISSING'));
});
test('versioned C-3 MODELED sixty-second default is declared not observed', () => {
  const input = admitted(), field = input.fields.find(f => f.id === 'visibility-latency');
  field.gaps = ['Observed historical visibility unavailable; R1 1.0.0 C-3 MODELED default 60 seconds, not measurement.'];
  const r = validateInventory(input); assert.equal(r.accountingComplete, true); assert.equal(r.classification, 'INVENTORY_COMPLETE');
  assert.ok(r.availabilityDiagnostics.some(b => b.fieldId === field.id && b.code === 'FIELD_NOT_CONFIRMED'));
  assert.equal(field.status, 'UNAVAILABLE'); assert.equal(r.d1Passed, false);
  field.gaps = ['Observed visibility unavailable.']; assert.notEqual(validateInventory(input).fingerprint, r.fingerprint);
});
test('lists are deterministic and CLI is bounded read-only sanitized corrected default', () => {
  const input = admitted(), r = validateInventory(input);
  assert.deepEqual(validateInventory({ ...input, sources: [...input.sources].reverse(), fields: [...input.fields].reverse() }), r);
  assert.equal(Array.isArray(r.availabilityDiagnostics), true);
  for (const list of [r.blockers, r.availabilityDiagnostics]) {
    const keys = list.map(b => b.code + '\0' + (b.sourceId || b.fieldId || '')); assert.deepEqual(keys, [...keys].sort());
  }
  const bytes = Buffer.from(JSON.stringify(input)); let requested = 0, closes = 0;
  const io = { fs: { openSync(p, flags) { assert.equal(flags, 'r'); return 1; }, fstatSync() { return { size: bytes.length, isFile: () => true }; },
    readSync(fd, target, offset, length, position) { requested += length; return bytes.copy(target, offset, position, position + length); }, closeSync() { closes++; } } };
  const originalFetch = global.fetch; global.fetch = () => assert.fail('no network');
  try { const result = runCli(['--inventory', 'synthetic'], io); assert.equal(result.exitCode, 0); assert.deepEqual(result.report, r); }
  finally { global.fetch = originalFetch; }
  assert.equal(closes, 1); assert.ok(requested <= 1000001); assert.ok(Buffer.byteLength(JSON.stringify(r) + '\n') <= 262144);
  assert.equal(JSON.stringify(runCli(['--secret', 'secret'])).includes('secret'), false);
});
