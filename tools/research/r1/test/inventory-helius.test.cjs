'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const { createHash } = require('node:crypto'), { spawnSync } = require('node:child_process');
const { validateInventory } = require('../inventory.cjs');
const original = path.resolve(__dirname, '../inventory-candidates.json'), snapshot = path.resolve(__dirname, '../inventory-helius-candidates.json');
const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));
// The existing valid candidate is the RED baseline, not a missing-file/discovery failure.
const candidate = () => read(fs.existsSync(snapshot) ? snapshot : original);
const id = 'helius-free-wallet-history', linked = ['trade-legs', 'spl-transfers', 'sol-transfers', 'base-priority-fees', 'reserves-depth', 'block-time'];
const baselineFingerprint = 'sha256:f9f2fb5450ce37e923892979e5b6a180e7e82fcbfe25ef44c7362fb2c972888f';
test('separate five-source snapshot preserves original bytes and four candidates without promoting Helius', () => {
  const old = read(original), input = candidate(); assert.equal(input.sources.length, 5);
  assert.equal(createHash('sha256').update(fs.readFileSync(original)).digest('hex'), 'bb11f6ee62e894cbd234b2f3b78ba61078895150aef9a41d1febfa517acd6d79');
  assert.equal(validateInventory(old).fingerprint, baselineFingerprint);
  assert.deepEqual(input.sources.filter(s => s.id !== id), old.sources);
  for (const key of ['schemaVersion', 'canonicalizationVersion', 'protocolVersion', 'freezeEntry', 'envelope']) assert.deepEqual(input[key], old[key]);
  const source = input.sources.find(s => s.id === id); assert.equal(source.selected, false);
  for (const key of ['sourceVersion', 'queryVersion', 'costUpperMicrousd']) assert.equal(source[key], null);
  assert.equal(source.retention.status, 'UNVERIFIED');
  assert.ok(source.retention.evidence.some(e => e.url === 'https://www.helius.dev/terms' && e.version === '2026-09-28'));
  for (const ref of [...source.evidence, ...source.retention.evidence]) assert.match(ref.retrievedAt, /^2026-10-03T\d{2}:\d{2}:\d{2}Z$/);
  assert.ok(source.evidence.some(e => e.url === 'https://www.helius.dev/docs/rpc/gettransactionsforaddress' && e.version === null));
  const claims = source.evidence.map(e => e.claim).join(' ');
  assert.match(claims, /getTransaction.*1.credit/i); assert.match(claims, /200.*separate.*PLAN/i);
  assert.match(claims, /full.envelope.*unknown/i);
});
test('Helius has six documentary links only with vault depth and unresolved fee split and ancillary inputs', () => {
  const input = candidate(), old = read(original); assert.equal(input.fields.length, 12);
  for (const field of input.fields) {
    const before = old.fields.find(f => f.id === field.id);
    if (!linked.includes(field.id)) { assert.deepEqual(field, before); continue; }
    assert.ok(field.sourceIds.includes(id), field.id + ' Helius documentary linkage');
    assert.equal(field.status, 'DOCUMENTED'); assert.equal(field.coveredFrom, null); assert.equal(field.coveredTo, null);
    assert.equal(field.granularity, before.granularity);
    assert.deepEqual(field.sourceIds.filter(s => s !== id), before.sourceIds);
    for (const gap of before.gaps) assert.ok(field.gaps.includes(gap));
    for (const ref of before.evidence) assert.ok(field.evidence.some(e => JSON.stringify(e) === JSON.stringify(ref)));
    assert.ok(field.evidence.some(e => e.url === 'https://www.helius.dev/docs/rpc/gettransactionsforaddress' && e.version === null));
  }
  const claims = fieldId => input.fields.find(f => f.id === fieldId).evidence.map(e => e.claim).join(' ');
  assert.match(claims('reserves-depth'), /vault.*pre\/post/i);
  assert.match(claims('reserves-depth'), /CLMM.*ticks.*DLMM.*bins/i);
  assert.match(claims('base-priority-fees'), /meta\.fee.*total.*base.*priority.*unresolved/i);
  for (const fieldId of ['tips', 'sol-usd', 'visibility-latency']) {
    const field = input.fields.find(f => f.id === fieldId); assert.equal(field.status, 'UNVERIFIED'); assert.equal(field.sourceIds.length, 0);
  }
});
test('new candidate keeps all availability diagnostics source-admission guards canonical identity and bounded CLI', () => {
  const input = candidate(), report = validateInventory(input), oldReport = validateInventory(read(original));
  assert.notEqual(report.fingerprint, baselineFingerprint); assert.equal(report.classification, 'INVENTORY_BLOCKED');
  assert.equal(report.runAuthorized, false); assert.equal(report.d1Passed, false); assert.equal(report.selectedCostUpperMicrousd, '0');
  assert.deepEqual(report.availabilityDiagnostics, oldReport.availabilityDiagnostics); assert.deepEqual(report.blockers, oldReport.blockers);
  const reversed = structuredClone(input); reversed.sources.reverse(); reversed.fields.reverse();
  for (const field of reversed.fields) { field.sourceIds.reverse(); field.gaps.reverse(); field.evidence.reverse(); }
  assert.deepEqual(validateInventory(reversed), report);
  const selected = structuredClone(input); selected.sources.find(s => s.id === id).selected = true;
  const blocked = validateInventory(selected); assert.equal(blocked.classification, 'INVENTORY_BLOCKED');
  assert.equal(blocked.runAuthorized, false); assert.equal(blocked.d1Passed, false); assert.equal(blocked.selectedCostUpperMicrousd, null);
  for (const code of ['SOURCE_VERSION_UNKNOWN', 'QUERY_VERSION_UNKNOWN', 'SOURCE_COST_UNKNOWN', 'SOURCE_EVIDENCE_VERSION_UNKNOWN', 'SOURCE_RETENTION_UNCONFIRMED'])
    assert.ok(blocked.blockers.some(b => b.sourceId === id && b.code === code));
  const unsafe = structuredClone(input); unsafe.sources.find(s => s.id === id).evidence[0].url += '?api-key=synthetic';
  assert.equal(validateInventory(unsafe).code, 'URL_INVALID');
  const cli = spawnSync(process.execPath, [path.resolve(__dirname, '../inventory-cli.cjs'), '--inventory', snapshot], { encoding: 'utf8', timeout: 10000 });
  assert.equal(cli.status, 2); assert.equal(cli.stderr, ''); assert.ok(Buffer.byteLength(cli.stdout) <= 262144);
  assert.deepEqual(JSON.parse(cli.stdout), report);
  assert.ok(fs.statSync(snapshot).size <= 1000000);
});
