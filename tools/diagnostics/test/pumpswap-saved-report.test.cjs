'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const { readReport, POOLS, MAX_BYTES } = require('../pumpswap-saved-report.cjs');
const cli = path.resolve(__dirname, '../pumpswap-saved-report.cjs');
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

test('pinned local receipt is presented truthfully, read-only and with sanitized failures', () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'pumpswap-saved-report-'));
  const input = path.join(folder, 'receipt.json');
  const receipt = {
    sourceSha256: 'a'.repeat(64),
    chainTime: {
      algorithmVersion: 'pumpswap-chain-time-candles-v1',
      interpretation: { readiness: 'DIAGNOSTIC_ONLY_NOT_ADMITTED', coverage: 'INCOMPLETE', unseenLoss: 'UNKNOWN', gapCoverage: 'GAP_COVERAGE_NOT_BOUND' },
      request: { fromInclusive: '2026-10-10T05:50:00Z', toExclusive: '2026-10-10T11:50:00Z', knowledgeCutoff: '2030-01-01T00:00:00Z' },
      reconciliation: { transactionCount: 124, operationCount: 68 },
      candles: [{ pool: { pool: POOLS[0] }, activity: { operationCount: 2 } }, { pool: { pool: 'unconfigured' }, activity: { operationCount: 99 } }]
    },
    pairs: [
      { request: { pool: { pool: POOLS[0] } }, status: 'NO_TRIGGER', reasons: [] },
      { request: { pool: { pool: POOLS[0] } }, status: 'INSUFFICIENT', reasons: ['MISSING_CURRENT_BUCKET', 'MISSING_PRIOR_BUCKET'] },
      { request: { pool: { pool: 'unconfigured' } }, status: 'TRIGGER', reasons: [] }
    ]
  };
  try {
    const bytes = Buffer.from(JSON.stringify(receipt));
    fs.writeFileSync(input, bytes);
    const sha = digest(bytes);
    const result = readReport(input, sha);
    assert.match(result, /свечей 1, операций 2; срабатываний 0, нет сигнала 1, недостаточно данных 1/);
    assert.match(result, /нет текущей свечи: 1/);
    assert.match(result, /нет предыдущей свечи: 1/);
    assert.match(result, /Причины могут пересекаться/);
    for (const pool of POOLS) assert.ok(result.includes(pool));
    assert.match(result, /DIAGNOSTIC_ONLY_NOT_ADMITTED/);
    assert.match(result, /разрывы не подтверждены как закрытые/);
    assert.ok(!result.includes('unconfigured'));
    assert.deepEqual(fs.readFileSync(input), bytes);
    assert.equal(readReport(input, sha), result);
    assert.throws(() => readReport(input, 'b'.repeat(64)), /INVALID_RECEIPT/);
    const launch = spawnSync(process.execPath, [cli, input, sha], { encoding: 'utf8' });
    assert.equal(launch.status, 0);
    assert.equal(launch.stdout, result);
    assert.equal(launch.stderr, '');
    for (const mutate of [
      r => { r.chainTime.reconciliation.operationCount = Number.MAX_SAFE_INTEGER + 1; },
      r => { r.pairs[0].status = 'NOT_PROVEN'; },
      r => { r.chainTime.interpretation.readiness = 'ADMITTED'; },
      r => { r.chainTime.request.knowledgeCutoff = 'sensitive-canary'; }
    ]) {
      const invalid = structuredClone(receipt); mutate(invalid);
      fs.writeFileSync(input, JSON.stringify(invalid));
      assert.throws(() => readReport(input, digest(fs.readFileSync(input))), /INVALID_RECEIPT/);
    }
    fs.writeFileSync(input, '{"sensitive-canary":');
    const failed = spawnSync(process.execPath, [cli, input, digest(fs.readFileSync(input))], { encoding: 'utf8' });
    assert.equal(failed.status, 1);
    assert.equal(failed.stdout, '');
    assert.ok(!failed.stderr.includes('sensitive-canary'));
    assert.ok(!failed.stderr.includes(input));
    fs.truncateSync(input, MAX_BYTES + 1);
    assert.throws(() => readReport(input, sha), /INVALID_RECEIPT/);
    assert.deepEqual(fs.readdirSync(folder), ['receipt.json']);
  } finally { fs.rmSync(folder, { recursive: true, force: true }); }
});
