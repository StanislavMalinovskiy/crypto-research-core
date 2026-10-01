'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { validateInventory } = require('../inventory.cjs');
const file = path.resolve(__dirname, '../inventory-candidates.json');
const read = () => JSON.parse(fs.readFileSync(file, 'utf8'));
const alchemy = 'alchemy-solana-account-archive', rpc = 'solana-public-rpc';
const source = (input, id) => input.sources.find(s => s.id === id);
const claims = (input, id) => (source(input, id)?.evidence || []).map(e => e.claim).join(' ');
test('inventory retains four unselected source candidates including historical Alchemy and independent RPC', () => {
  const input = read();
  assert.deepEqual(input.sources.map(s => s.id).sort(), [alchemy, 'dune-solana-dex', rpc, 'sqd-solana-portal'].sort());
  for (const s of input.sources) {
    assert.equal(s.selected, false); assert.equal(s.sourceVersion, null); assert.equal(s.queryVersion, null);
    assert.equal(s.costUpperMicrousd, null); assert.equal(s.retention.status, 'UNVERIFIED');
  }
});
test('historical state candidates link to reserves executable inputs and unresolved mint transitions', () => {
  const input = read();
  for (const id of ['reserves-depth', 'executable-entry-exit', 'mint-decimals-freeze']) {
    const field = input.fields.find(f => f.id === id);
    assert.ok(field.sourceIds.includes(alchemy), `${id} retains Alchemy historical-state candidate`);
    assert.equal(field.status, 'UNVERIFIED');
    if (id !== 'mint-decimals-freeze') {
      assert.ok(field.sourceIds.includes('sqd-solana-portal'));
      assert.match(field.gaps.join(' '), /CLMM|ticks|curve|layout/i);
    } else assert.match(field.gaps.join(' '), /mint.transition/i);
  }
});
test('independent RPC is linked to sampled transaction fields without confirming their semantics', () => {
  for (const id of ['trade-legs', 'spl-transfers', 'sol-transfers', 'base-priority-fees', 'block-time']) {
    const field = read().fields.find(f => f.id === id);
    assert.ok(field.sourceIds.includes(rpc), `${id} retains independent receipt candidate`);
    assert.notEqual(field.status, 'CONFIRMED'); assert.ok(field.gaps.length > 0);
  }
});
test('Alchemy evidence preserves measured B matrix scope and unfulfilled formal S3', () => {
  const claim = claims(read(), alchemy);
  assert.match(claim, /12\/12/); assert.match(claim, /six repeat comparisons match/i);
  assert.match(claim, /one PumpSwap pool and two vaults/i); assert.match(claim, /429644638/); assert.match(claim, /429644639/);
  assert.match(claim, /sampled.*vault|vault.*sampled/i); assert.match(claim, /layout.*undecodable|undecodable.*layout/i);
  assert.match(claim, /formal S3.*unfulfilled/i); assert.match(claim, /INCONCLUSIVE/);
  assert.match(claim, /provider-ab-1/); assert.match(claim, /4c22a24aab2178eff415710226197159160011fe88d0394ac735f38da5e37a87/);
});
test('SQD and RPC retained agreement remains sampled with explicit gaps and 200-trade obligation', () => {
  const input = read(), sqd = claims(input, 'sqd-solana-portal'), independent = claims(input, rpc);
  assert.match(sqd, /sampled.*reconcil|reconcil.*sampled/i); assert.match(sqd, /HTTP 529/);
  assert.match(sqd, /old.*tail.*no full transaction.payload cross.check/i);
  assert.match(sqd, /full signature/); assert.match(sqd, /continuation/i);
  assert.match(independent, /sampled.*reconcil|reconcil.*sampled/i); assert.match(independent, /200/);
  assert.match(independent, /7f028de71b26e8f8fa9ca05ee5cef8647863226bf4cef74301deb0a7ff4a6cae/);
});
test('documentary terms remain dated and Dune explicitly unconnected', () => {
  const input = read(); assert.match(claims(input, 'dune-solana-dex'), /unconnected/i);
  const a = source(input, alchemy), sqd = source(input, 'sqd-solana-portal'), r = source(input, rpc);
  assert.ok(a?.evidence.some(e => e.url === 'https://www.alchemy.com/docs/solana/account-archive'));
  assert.ok(a?.retention.evidence.some(e => e.url === 'https://legal.alchemy.com/' && e.version === '2025-06-27'));
  assert.ok(sqd.retention.evidence.some(e => e.url === 'https://cloud.sqd.dev/terms.pdf' && e.version === null));
  for (const method of ['gettransaction', 'getblock'])
    assert.ok(r?.evidence.some(e => e.url === `https://solana.com/docs/rpc/http/${method}`));
  for (const s of input.sources) for (const e of [...s.evidence, ...s.retention.evidence])
    assert.match(e.retrievedAt, /^2026-10-01T\d{2}:\d{2}:\d{2}Z$/);
});
test('all twelve fields retain unknown full-envelope coverage and blocked D1 status', () => {
  const input = read(), report = validateInventory(input);
  assert.equal(input.fields.length, 12);
  for (const field of input.fields) {
    assert.notEqual(field.status, 'CONFIRMED'); assert.equal(field.coveredFrom, null); assert.equal(field.coveredTo, null);
    assert.ok(report.blockers.some(b => b.code === 'FIELD_NOT_CONFIRMED' && b.fieldId === field.id));
    assert.ok(report.blockers.some(b => b.code === 'FIELD_COVERAGE_INCOMPLETE' && b.fieldId === field.id));
  }
  assert.equal(input.fields.find(f => f.id === 'trade-legs').status, 'DOCUMENTED');
  assert.equal(input.fields.find(f => f.id === 'block-time').status, 'DOCUMENTED');
  for (const id of ['sol-usd', 'tips', 'visibility-latency']) assert.equal(input.fields.find(f => f.id === id).status, 'UNVERIFIED');
  assert.equal(report.classification, 'INVENTORY_BLOCKED'); assert.equal(report.selectedCostUpperMicrousd, '0');
  assert.equal(report.runAuthorized, false); assert.equal(report.d1Passed, false); assert.match(report.fingerprint, /^sha256:[a-f0-9]{64}$/);
  assert.deepEqual(validateInventory(read()), report);
});
test('candidate CLI exits 2 and preserves validator report without extraction authorization', () => {
  const result = spawnSync(process.execPath, [path.resolve(__dirname, '../inventory-cli.cjs'), '--inventory', file], { encoding: 'utf8' });
  assert.equal(result.status, 2); assert.equal(result.stderr, ''); assert.equal(result.stdout.trim().split('\n').length, 1);
  assert.deepEqual(JSON.parse(result.stdout), validateInventory(read()));
});
