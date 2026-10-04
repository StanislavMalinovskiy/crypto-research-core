'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), os = require('node:os');
const { spawnSync } = require('node:child_process');
const target = path.join(__dirname, '../e2-historical-creation-audit-v1.cjs');
const audit = fs.existsSync(target) ? require(target) : null;
const legacy = require('../e2-census.cjs');
const creation = (extra = {}) => ({ status: 'OBSERVED_DECLARED_CREATE_POOL', slot: 410195947, timestamp: 1775001600,
  transactionIndex: 2, signature: '1'.repeat(64), instructionPath: [0], cpiDepth: 0,
  pool: '1'.repeat(32), globalConfig: '1'.repeat(32), creator: '1'.repeat(32), baseMint: '1'.repeat(32), quoteMint: '1'.repeat(32),
  accountCount: 18, dataLength: 60, rawHash: 'sha256:' + 'a'.repeat(64), lineOrdinal: 1, layoutApplicability: 'UNVERIFIED', ...extra });
function subject(rows) { if (audit) return audit.auditRows(rows);
  // The actual unchanged legacy parser accepts each contradictory historical relationship independently.
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = BigInt('0xe992d18ecf6840bc' + '00'.repeat(52)), data = '';
  while (n) { data = alphabet[Number(n % 58n)] + data; n /= 58n; }
  for (const r of rows) { const b = { header: { number: r.slot, parentNumber: r.slot - 1, hash: '1'.repeat(32), parentHash: '1'.repeat(32), timestamp: r.timestamp },
    transactions: [{ transactionIndex: r.transactionIndex, signatures: [r.signature], err: null }],
    instructions: [{ transactionIndex: r.transactionIndex, instructionAddress: r.instructionPath, programId: legacy.CONFIG.program,
      accounts: [r.pool, r.globalConfig, r.creator, r.baseMint, r.quoteMint], data, error: null, isCommitted: true }] };
    const bounds = [410195947, 416000000, 423000000, 429000000];
    assert.equal(legacy.admit(Buffer.from(JSON.stringify(b)), legacy.query('data', 0, 410195947, bounds), bounds).code, null);
  } return { conflicts: 0 }; }
test('historical signature index contradiction is counted', () => {
  assert.equal(subject([creation(), creation({ transactionIndex: 3, instructionPath: [1] })]).conflicts, 1); });
test('historical signature slot contradiction is counted', () => {
  assert.equal(subject([creation(), creation({ slot: 410195949, instructionPath: [1] })]).conflicts, 1); });
test('historical same path immutable contradiction is counted', () => {
  assert.equal(subject([creation(), creation({ dataLength: 59 })]).conflicts, 1); });
test('historical valid multipath and changed raw provenance are equal facts', () => {
  const r = subject([creation(), creation({ instructionPath: [10] }), creation({ instructionPath: [2] }),
    creation({ rawHash: 'sha256:' + 'b'.repeat(64), lineOrdinal: 2 })]);
  assert.equal(r.conflicts, 0); assert.equal(r.rowsExamined, 4); assert.equal(r.distinctPaths, 3);
  assert.equal(r.multiplePathSignatures, 1); assert.equal(r.equalRetries, 1); assert.equal(r.coverage.rawReplayed, false);
});
function fixture(t, rows) { const root = fs.mkdtempSync(path.join(os.tmpdir(), 'b5-audit-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true })); const input = path.join(root, 'creations.jsonl');
  fs.writeFileSync(input, rows.map(JSON.stringify).join('\n') + '\n'); return { root, input, output: path.join(root, 'output') }; }
test('synthetic CLI receipt hashes input before after and reserves fresh external output', t => {
  const f = fixture(t, [creation(), creation({ instructionPath: [2] })]); const bytes = fs.readFileSync(f.input);
  const result = spawnSync(process.execPath, [target, '--input', f.input, '--output', f.output, '--retained-before', '1000000'], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr + result.stdout); const r = JSON.parse(fs.readFileSync(path.join(f.output, 'report.json')));
  assert.equal(r.status, 'RELATIONSHIPS_CONSISTENT'); assert.equal(r.rowsExamined, 2); assert.equal(r.multiplePathSignatures, 1);
  assert.equal(r.inputs[0].hashBefore, r.inputs[0].hashAfter); assert.deepEqual(fs.readFileSync(f.input), bytes);
  assert.equal(r.coverage.globalCensusProven, false); assert.equal(r.d1Evidence, false);
  const again = spawnSync(process.execPath, [target, '--input', f.input, '--output', f.output, '--retained-before', '1000000'], { encoding: 'utf8' });
  assert.notEqual(again.status, 0); assert.match(again.stdout, /ROOT_EXISTS/);
});
test('stream row state byte time and global budgets fail explicitly', t => {
  const f = fixture(t, [creation(), creation({ instructionPath: [2] })]);
  for (const limits of [{ rows: 1 }, { stateBytes: 1 }, { lineBytes: 10 }, { outputBytes: 10 }]) {
    const r = audit.run({ inputs: [f.input], output: path.join(f.root, 'out-' + Object.keys(limits)[0]), retainedBefore: '1000000', limits });
    assert.equal(r.status, 'INCOMPLETE'); assert.notEqual(r.code, null); }
  assert.equal(audit.run({ inputs: [f.input], output: path.join(f.root, 'late'), retainedBefore: '1000000',
    utcNow: () => Date.parse('2026-10-05T04:42:41Z') }).code, 'ABSOLUTE_STOP');
  assert.equal(audit.run({ inputs: [f.input], output: path.join(f.root, 'space'), retainedBefore: '1000000', freeBytes: () => 1n }).code, 'FREE_SPACE_LIMIT');
  assert.equal(audit.run({ inputs: [f.input], output: path.join(f.root, 'global'), retainedBefore: '50000000000' }).code, 'RETAINED_LIMIT');
  assert.equal(audit.run({ inputs: [f.input], output: path.join(f.root, 'time'), retainedBefore: '1000000', now: (() => { let n = 0; return () => n++ * 1800001; })() }).code, 'TIME_LIMIT');
});
test('unsafe paths duplicate inputs and malformed lexemes cannot pass', t => {
  const f = fixture(t, [creation()]);
  assert.equal(audit.run({ inputs: [f.input, f.input], output: f.output, retainedBefore: '1000000' }).status, 'INCOMPLETE');
  const inside = path.resolve(__dirname, '../b5-forbidden-output');
  assert.equal(audit.run({ inputs: [f.input], output: inside, retainedBefore: '1000000' }).code, 'UNSAFE_PATH');
  assert.equal(fs.existsSync(inside), false);
  for (const content of ['{"signature":"a","signature":"b"}', JSON.stringify(creation()).replace('"transactionIndex":2', '"transactionIndex":2.0')]) {
    fs.writeFileSync(f.input, content + '\n');
    assert.equal(audit.run({ inputs: [f.input], output: path.join(f.root, 'bad-' + content.length), retainedBefore: '1000000' }).status, 'INCOMPLETE'); }
});
test('source mutation is detected and input hashes retained', t => {
  const f = fixture(t, [creation()]); let changed = false;
  const r = audit.run({ inputs: [f.input], output: f.output, retainedBefore: '1000000', onRow: () => {
    if (!changed) { changed = true; fs.appendFileSync(f.input, JSON.stringify(creation({ instructionPath: [1] })) + '\n'); } } });
  assert.equal(r.status, 'INCOMPLETE'); assert.equal(r.code, 'INPUT_CHANGED'); assert.notEqual(r.inputs[0].hashBefore, r.inputs[0].hashAfter);
});
