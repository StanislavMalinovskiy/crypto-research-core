'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { validateInventory } = require('../inventory.cjs');
const { runCli } = require('../inventory-cli.cjs');
const ids = ['trade-legs', 'spl-transfers', 'sol-transfers', 'base-priority-fees', 'tips',
  'reserves-depth', 'liquidity-events', 'mint-decimals-freeze', 'executable-entry-exit',
  'sol-usd', 'block-time', 'visibility-latency'];
const from = '2026-04-01T00:00:00Z', to = '2026-09-29T04:02:00Z';
const ref = () => ({ url: 'https://example.invalid/documentation', retrievedAt: '2026-10-01T00:00:00Z',
  version: 'synthetic-v1', claim: 'Synthetic test claim, not provider evidence.' });
function complete() {
  return { schemaVersion: 'r1-d1-inventory-v1', canonicalizationVersion: 'r1-d1-inventory-c14n-v1',
    protocolVersion: '1.0.0', freezeEntry: 1, envelope: { from, to },
    sources: [{ id: 'synthetic-source', selected: true, sourceVersion: 'source-v1', queryVersion: 'query-v1',
      costUpperMicrousd: '100000000', retention: { status: 'CONFIRMED', evidence: [ref()] }, evidence: [ref()] }],
    fields: ids.map(id => ({ id, status: 'CONFIRMED', sourceIds: ['synthetic-source'],
      coveredFrom: from, coveredTo: to, granularity: 'per-event', gaps: [], evidence: [ref()] })) };
}
function fakeFs(content, size = Buffer.byteLength(content)) {
  const bytes = Buffer.from(content), state = { reads: 0, requested: 0, closes: 0, opens: 0 };
  const fs = { openSync(path, flags) { assert.equal(flags, 'r'); state.opens++; return 7; },
    fstatSync() { return { size, isFile: () => true }; },
    readSync(fd, buffer, offset, length, position) {
      state.reads++; state.requested += length;
      return bytes.copy(buffer, offset, position, Math.min(bytes.length, position + length));
    }, closeSync() { state.closes++; },
    writeFileSync() { assert.fail('inventory must not write'); } };
  return { fs, state };
}
function codes(report) { return report.blockers.map(b => b.code); }
test('complete inventory at exact ceiling never authorizes extraction or passes D1', () => {
  const report = validateInventory(complete());
  assert.equal(report.classification, 'INVENTORY_COMPLETE');
  assert.equal(report.selectedCostUpperMicrousd, '100000000');
  assert.deepEqual(report.blockers, []);
  assert.equal(report.runAuthorized, false); assert.equal(report.d1Passed, false);
  assert.match(report.fingerprint, /^sha256:[a-f0-9]{64}$/);
  assert.deepEqual(Object.keys(report).sort(), ['blockers', 'canonicalizationVersion', 'classification',
    'd1Passed', 'fingerprint', 'runAuthorized', 'schemaVersion', 'selectedCostUpperMicrousd']);
});
test('one micro-USD over ceiling and values beyond Number precision are exact', () => {
  const input = complete(); input.sources[0].costUpperMicrousd = '100000001';
  const overflow = validateInventory(input);
  assert.equal(overflow.selectedCostUpperMicrousd, '100000001');
  assert.equal(overflow.classification, 'INVENTORY_BLOCKED');
  assert.ok(codes(overflow).includes('COST_ABOVE_CEILING'));
  input.sources[0].costUpperMicrousd = '999999999999999999';
  input.sources.push({ ...input.sources[0], id: 'second-source', costUpperMicrousd: '2' });
  assert.equal(validateInventory(input).selectedCostUpperMicrousd, '1000000000000000001');
});
test('all cost retention and unavailable-field blockers survive together', () => {
  const input = complete(); input.sources[0].costUpperMicrousd = null;
  input.sources[0].retention.status = 'UNVERIFIED'; input.fields[5].status = 'UNAVAILABLE';
  const report = validateInventory(input);
  assert.ok(codes(report).includes('SOURCE_COST_UNKNOWN'));
  assert.equal(report.classification, 'INVENTORY_BLOCKED');
  assert.equal(report.selectedCostUpperMicrousd, null);
  assert.ok(codes(report).includes('SOURCE_COST_UNKNOWN'));
  assert.ok(codes(report).includes('SOURCE_RETENTION_UNCONFIRMED'));
  assert.ok(report.blockers.some(b => b.code === 'FIELD_NOT_CONFIRMED' && b.fieldId === 'reserves-depth'));
});
test('documented trade legs cannot establish depth or measured completeness', () => {
  const input = complete(); input.fields[0].status = 'DOCUMENTED'; input.fields[5].status = 'UNVERIFIED';
  const report = validateInventory(input);
  assert.equal(report.classification, 'INVENTORY_BLOCKED');
  for (const fieldId of ['trade-legs', 'reserves-depth'])
    assert.ok(report.blockers.some(b => b.code === 'FIELD_NOT_CONFIRMED' && b.fieldId === fieldId));
  assert.equal(report.d1Passed, false);
});
test('unknown selected versions and documentary revisions remain blockers', () => {
  const input = complete(); input.sources[0].sourceVersion = null; input.sources[0].queryVersion = null;
  input.sources[0].evidence[0].version = null; input.fields[0].evidence[0].version = null;
  const report = validateInventory(input);
  assert.equal(report.classification, 'INVENTORY_BLOCKED');
  for (const code of ['SOURCE_VERSION_UNKNOWN', 'QUERY_VERSION_UNKNOWN', 'SOURCE_EVIDENCE_VERSION_UNKNOWN',
    'FIELD_EVIDENCE_VERSION_UNKNOWN']) assert.ok(codes(report).includes(code));
});
test('unselected costs do not inflate sum but candidates remain in fingerprint', () => {
  const input = complete(), original = validateInventory(input);
  input.sources.push({ ...input.sources[0], id: 'unselected-source', selected: false,
    sourceVersion: null, queryVersion: null, costUpperMicrousd: null, retention: { status: 'UNVERIFIED', evidence: [] } });
  const report = validateInventory(input);
  assert.equal(report.classification, 'INVENTORY_COMPLETE');
  assert.equal(report.selectedCostUpperMicrousd, '100000000');
  assert.notEqual(report.fingerprint, original.fingerprint);
});
test('permuted objects and set lists have identical full reports', () => {
  const input = complete(); input.sources.push({ ...input.sources[0], id: 'second-source', selected: false });
  for (const field of input.fields) { field.sourceIds.push('second-source'); field.evidence.push({ ...ref(), claim: 'Other claim.' }); }
  function reverse(value) {
    if (Array.isArray(value)) return value.map(reverse).reverse();
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).reverse().map(([k, v]) => [k, reverse(v)]));
    return value;
  }
  const report = validateInventory(input);
  assert.equal(report.classification, 'INVENTORY_COMPLETE');
  assert.deepEqual(validateInventory(reverse(input)), report);
});
test('every content category participates in fingerprint including Unicode byte lengths', () => {
  const base = complete(), fingerprint = validateInventory(base).fingerprint;
  const mutations = [i => i.sources[0].queryVersion = 'query-v2', i => i.sources[0].sourceVersion = 'source-v2',
    i => i.sources[0].costUpperMicrousd = '99999999', i => i.fields[0].coveredFrom = '2026-03-31T00:00:00Z',
    i => i.fields[0].status = 'DOCUMENTED', i => i.fields[0].granularity = 'slot',
    i => i.fields[0].gaps.push('known gap'), i => i.fields[0].evidence[0].claim = 'é:🙂',
    i => i.sources[0].evidence[0].retrievedAt = '2026-10-01T01:00:00Z',
    i => i.sources[0].retention.evidence[0].version = 'terms-v2'];
  for (const mutate of mutations) { const input = complete(); mutate(input); const report = validateInventory(input);
    assert.notEqual(report.classification, 'INVENTORY_INVALID'); assert.notEqual(report.fingerprint, fingerprint); }
});
test('unsupported canonicalization version rejects without inventing a fingerprint', () => {
  const input = complete(); input.canonicalizationVersion = 'r1-d1-inventory-c14n-v2';
  const report = validateInventory(input);
  assert.equal(report.classification, 'INVENTORY_INVALID');
  assert.equal(report.code, 'UNSUPPORTED_CANONICALIZATION_VERSION');
  assert.equal(Object.hasOwn(report, 'fingerprint'), false);
});
test('freeze entry is exactly numeric integer one', () => {
  for (const freezeEntry of ['1', 2, 1.5, null]) {
    const input = complete(); input.freezeEntry = freezeEntry;
    assert.equal(validateInventory(input).classification, 'INVENTORY_INVALID');
  }
  assert.equal(validateInventory(complete()).classification, 'INVENTORY_COMPLETE');
});
test('maximum legal inventory retains all blockers within report limits with no network or writes', () => {
  const input = complete();
  input.sources = Array.from({ length: 32 }, (_, n) => ({ ...input.sources[0], id: `source-${n}`,
    sourceVersion: null, queryVersion: null, costUpperMicrousd: null, retention: { status: 'UNVERIFIED', evidence: [] },
    evidence: Array.from({ length: 16 }, (_, m) => ({ ...ref(), version: null, claim: `claim ${m}` })) }));
  for (const field of input.fields) { field.sourceIds = input.sources.map(s => s.id); field.status = 'UNVERIFIED';
    field.coveredFrom = null; field.coveredTo = null; field.granularity = null; field.gaps = ['unmeasured'];
    field.evidence = Array.from({ length: 16 }, (_, m) => ({ ...ref(), version: null, claim: `claim ${m}` })); }
  const io = fakeFs(JSON.stringify(input)), originalFetch = global.fetch;
  io.fs = new Proxy(io.fs, { get(target, name) {
    assert.ok(['openSync', 'fstatSync', 'readSync', 'closeSync'].includes(name), 'read only inventory file');
    return target[name];
  } });
  global.fetch = () => assert.fail('no network calls');
  try {
    const result = runCli(['--inventory', 'synthetic'], io);
    assert.equal(result.exitCode, 2); assert.ok(result.report.blockers.length >= 32 * 4 + 12 * 4);
    assert.ok(result.report.blockers.length <= 2048);
    assert.ok(Buffer.byteLength(JSON.stringify(result.report) + '\n') <= 262144);
    const keys = result.report.blockers.map(b => b.code + '\0' + (b.sourceId || b.fieldId || ''));
    assert.deepEqual(keys, [...keys].sort()); assert.equal(new Set(keys).size, keys.length);
  } finally { global.fetch = originalFetch; }
});
test('incomplete coverage known gaps and missing selected supplier all block', () => {
  const input = complete(); input.fields[0].coveredTo = '2026-09-29T04:01:59Z';
  input.fields[1].gaps.push('gap'); input.sources[0].selected = false;
  const report = validateInventory(input);
  assert.equal(report.classification, 'INVENTORY_BLOCKED');
  for (const code of ['FIELD_COVERAGE_INCOMPLETE', 'FIELD_GAPS', 'FIELD_SELECTED_SOURCE_MISSING']) assert.ok(codes(report).includes(code));
});
const invalidCases = [
  ['missing field', i => i.fields.pop()], ['duplicate field', i => i.fields[1] = i.fields[0]],
  ['unknown field', i => i.fields[0].id = 'secret-invalid-field'], ['unknown property', i => i.secret = 'secret'],
  ['unknown nested property', i => i.sources[0].retention.token = 'secret'],
  ['leading-zero money', i => i.sources[0].costUpperMicrousd = '010'], ['numeric money', i => i.sources[0].costUpperMicrousd = 100],
  ['negative money', i => i.sources[0].costUpperMicrousd = '-1'], ['oversized money', i => i.sources[0].costUpperMicrousd = '1'.repeat(19)],
  ['invalid date', i => i.fields[0].coveredFrom = '2026-02-30T00:00:00Z'],
  ['wrong envelope', i => i.envelope.to = '2026-09-29T04:01:59Z'],
  ['userinfo URL', i => i.fields[0].evidence[0].url = 'https://secret@example.invalid/doc'],
  ['query URL', i => i.sources[0].evidence[0].url += '?key=secret'],
  ['fragment URL', i => i.sources[0].evidence[0].url += '#secret'],
  ['duplicate reference', i => i.fields[0].evidence.push(i.fields[0].evidence[0])],
  ['duplicate source ID', i => i.sources.push(i.sources[0])],
  ['duplicate supplier', i => i.fields[0].sourceIds.push('synthetic-source')],
  ['unknown supplier', i => i.fields[0].sourceIds.push('unknown')],
  ['confirmed without source', i => i.fields[0].sourceIds = []],
  ['confirmed without evidence', i => i.fields[0].evidence = []],
  ['confirmed without granularity', i => i.fields[0].granularity = null],
  ['33 sources', i => i.sources = Array.from({ length: 33 }, (_, n) => ({ ...i.sources[0], id: `source-${n}` }))],
  ['17 references', i => i.fields[0].evidence = Array.from({ length: 17 }, (_, n) => ({ ...ref(), claim: `claim ${n}` }))],
  ['33 gaps', i => i.fields[0].gaps = Array.from({ length: 33 }, (_, n) => `gap ${n}`)],
  ['oversized claim', i => i.fields[0].evidence[0].claim = 'x'.repeat(1001)]
];
for (const [name, mutate] of invalidCases) test(`closed schema rejects ${name} safely`, () => {
  const input = complete(); mutate(input); const report = validateInventory(input);
  assert.equal(report.classification, 'INVENTORY_INVALID');
  assert.equal(report.runAuthorized, false); assert.equal(report.d1Passed, false);
  assert.match(report.code, /^[A-Z_]+$/); assert.equal(JSON.stringify(report).includes('secret'), false);
});
test('CLI returns complete blocked invalid exit codes and bounds actual reads', () => {
  for (const [modify, exitCode] of [[() => {}, 0], [i => i.fields[0].status = 'UNVERIFIED', 2]]) {
    const input = complete(); modify(input); const io = fakeFs(JSON.stringify(input));
    const result = runCli(['--inventory', 'synthetic.json'], io);
    assert.equal(result.exitCode, exitCode); assert.equal(io.state.opens, 1);
    assert.equal(io.state.closes, 1); assert.ok(io.state.requested <= 1000001);
    assert.equal(result.report.runAuthorized, false); assert.equal(result.report.d1Passed, false);
  }
  const io = fakeFs('{secret-invalid-json'); const result = runCli(['--inventory', 'secret-path'], io);
  assert.equal(result.exitCode, 1); assert.equal(result.report.code, 'JSON_INVALID');
  assert.equal(JSON.stringify(result).includes('secret'), false);
});
test('CLI rejects overflow before parse and reads at most one overflow byte on growth', () => {
  const declared = fakeFs(' ', 1000001);
  assert.equal(runCli(['--inventory', 'synthetic'], declared).report.code, 'INPUT_LIMIT');
  assert.equal(declared.state.reads, 0);
  const growing = fakeFs(' '.repeat(1000001), 1);
  assert.equal(runCli(['--inventory', 'synthetic'], growing).report.code, 'INPUT_LIMIT');
  assert.equal(growing.state.requested, 1000001); assert.equal(growing.state.closes, 1);
});
test('CLI fixed argument and filesystem errors never echo sensitive text', () => {
  for (const args of [[], ['--inventory'], ['--token', 'secret'], ['--inventory', 'secret', '--extra']]) {
    const result = runCli(args); assert.equal(result.exitCode, 1); assert.equal(result.report.code, 'ARGUMENTS_INVALID');
    assert.equal(JSON.stringify(result).includes('secret'), false);
  }
  const io = { fs: { openSync() { throw Error('secret failure'); } } };
  const result = runCli(['--inventory', 'secret-path'], io);
  assert.equal(result.report.code, 'FILE_READ_ERROR'); assert.equal(JSON.stringify(result).includes('secret'), false);
});
test('executable CLI emits exactly one sanitized JSON line and exit 1 for missing file', () => {
  const result = spawnSync(process.execPath, [require.resolve('../inventory-cli.cjs'), '--inventory', 'secret-missing-file'], { encoding: 'utf8' });
  assert.equal(result.status, 1); assert.equal(result.stderr, '');
  assert.equal(result.stdout.trim().split('\n').length, 1);
  const report = JSON.parse(result.stdout); assert.equal(report.code, 'FILE_READ_ERROR');
  assert.equal(result.stdout.includes('secret'), false);
});
