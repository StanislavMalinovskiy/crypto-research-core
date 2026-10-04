'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const x = require('../e2-offline-mint-count.cjs');
const ZERO = '1'.repeat(32), SIG = '1'.repeat(64);
function row(extra = {}) { return { status: 'OBSERVED_DECLARED_CREATE_POOL', slot: 410195947, timestamp: 1775001600, signature: SIG,
  transactionIndex: 0, instructionPath: [2], cpiDepth: 0, pool: ZERO, globalConfig: ZERO, creator: ZERO, baseMint: ZERO,
  quoteMint: x.QUOTES[0], accountCount: 18, dataLength: 59, rawHash: 'sha256:' + 'a'.repeat(64), lineOrdinal: 0, layoutApplicability: 'UNVERIFIED', ...extra }; }
test('Accepted base-side SOL quote orients to the other mint rather than assuming base is the token', () => {
  assert.equal(x.orient(row({ baseMint: x.QUOTES[0], quoteMint: ZERO })).mint, ZERO, 'one base-side quote must identify other mint');
});
test('Accepted USDC and USDT quotes yield provisional studied mint without wSOL-only filtering', () => {
  for (const quoteMint of x.QUOTES.slice(1)) assert.equal(x.orient(row({ quoteMint })).reason, 'PROVISIONAL_STUDIED_MINT', 'accepted stablecoin quote must orient provisionally');
});
test('Contradictory immutable locator facts fail rather than earliest-wins deduplication', () => {
  const r = new x.Reducer(); r.add(row(), 0);
  assert.throws(() => r.add(row({ quoteMint: x.QUOTES[1] }), 0), /LOCATOR_CONFLICT/, 'same signature/path contradictory mint roles must fail');
});
const { canonical, digest, fingerprint } = require('../exploratory-probe.cjs');
const c = require('../e2-monthly-census.cjs'), tail = require('../e2-monthly-tail.cjs');
function b58(bytes) { const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = BigInt('0x' + bytes.toString('hex')), text = '', zero = 0;
  while (zero < bytes.length && bytes[zero] === 0) zero++; while (n) { text = alphabet[Number(n % 58n)] + text; n /= 58n; } return '1'.repeat(zero) + text; }
function key(n, size = 32) { const b = Buffer.alloc(size); b.writeUInt32BE(n, size - 4); return b58(b); }
function uniqueRow(root, n) { return row({ slot: root.start, timestamp: x.DATES[['april', 'may', 'june'].indexOf(root.month)], signature: key(n + 1, 64),
  pool: key(n + 1000001), baseMint: key(n + 1), dataLength: root.month === 'april' ? 59 : 60 }); }
function fixture(count = 1) {
  const roots = structuredClone(x.ROOTS), files = new Map();
  for (const [at, root] of roots.entries()) {
    const chunks = []; let text = '';
    for (let i = 0; i < count; i++) { text += JSON.stringify(uniqueRow(root, at * count + i)) + '\n'; if ((i + 1) % 1000 === 0) { chunks.push(Buffer.from(text)); text = ''; } }
    if (text) chunks.push(Buffer.from(text)); const cb = Buffer.concat(chunks); root.rows = count;
    const code = at === 1 || at === 2 ? 'HTTP_ERROR' : null, status = code ? 'INCOMPLETE' : 'SCAN_COMPLETE';
    const flags = { cohortAdmitted: false, d1Evidence: false, d1Passed: false, authoritativeCensusComplete: false };
    const s = { ...flags, creations: count, code, status }, sb = Buffer.from(JSON.stringify(s) + '\n'), configuration = at < 3 ? c.configuration(root.month) : tail.configuration(root.month);
    const m = { ...flags, configuration, code, status, summaryHash: fingerprint(configuration.canonicalVersion, s), accounting: { retainedKnown: true },
      lineage: { runtime: 'v24.19.0', source: { commit: 'a'.repeat(40), dirty: false } }, files: [{ name: 'summary.json', bytes: sb.length, hash: digest(sb) },
        { name: 'creations.jsonl', bytes: cb.length, hash: digest(cb) }, { name: '00001.raw', bytes: 64000000, hash: 'sha256:' + 'a'.repeat(64) }] };
    const mb = Buffer.from(JSON.stringify(m) + '\n'); root.pins = [mb, sb, cb].map(b => [b.length, digest(b).slice(7)]);
    for (const [i, b] of [mb, sb, cb].entries()) files.set(root.suffix + '/' + x.NAMES[i], b);
  }
  const reads = [];
  return { roots, files, reads, reader(root, name) { reads.push(root.suffix + '/' + name); const b = files.get(root.suffix + '/' + name); let at = 0;
    return { size: b.length, read() { const out = b.subarray(at, at + 65536); at += out.length; return out; }, close() {} }; } };
}
test('Orientation exclusions and canonical address lengths remain counted UNKNOWN not admission', () => {
  for (const [baseMint, quoteMint, reason] of [[x.QUOTES[0], x.QUOTES[1], 'TWO_SUPPORTED_QUOTES'], [ZERO, key(2), 'NO_SUPPORTED_QUOTE'],
    [ZERO, ZERO, 'SAME_MINT'], ['0', ZERO, 'UNSUPPORTED_REPRESENTATION']]) assert.equal(x.orient({ baseMint, quoteMint }).reason, reason);
  assert.throws(() => x.decode('1'.repeat(33), 32)); assert.equal(x.decode(SIG, 64).length, 64);
  const r = new x.Reducer(); r.add(row({ accountCount: 5 }), 0); r.add(row({ pool: key(2), signature: key(2, 64), cpiDepth: 2 }), 0);
  const out = r.result(); assert.equal(out.provisionalStudiedMints, 0); assert.equal(out.roots[0].unknownShape, 2);
  assert.equal(out.cohortAdmitted, false); assert.equal(out.confirmedCohortCounts, 'NOT_ESTABLISHED');
});
test('Mint cluster observed-first months and numeric path order are deterministic under permutations', () => {
  const rows = [row(), row({ slot: x.ROOTS[1].start, timestamp: x.DATES[1], pool: key(2), signature: key(2, 64) }),
    row({ slot: x.ROOTS[2].start, timestamp: x.DATES[2], pool: key(3), signature: key(3, 64) })];
  const results = []; for (const order of [[0, 1, 2], [2, 0, 1], [1, 2, 0]]) { const r = new x.Reducer(); for (const at of order) r.add(rows[at], at); results.push(r.result()); }
  assert.equal(canonical(results[0]), canonical(results[1])); assert.equal(canonical(results[1]), canonical(results[2])); const out = results[0];
  assert.deepEqual(out.monthlyObservedFirstMints, { april: 1, june: 0, may: 0 }); assert.equal(out.distinctObservedPools, 3);
  assert.ok(x.compare(row({ instructionPath: [2] }), row({ instructionPath: [10] })) < 0);
  assert.ok(x.compare(row({ instructionPath: [2] }), row({ instructionPath: [2, 0] })) < 0);
  const d = new x.Reducer(); d.add(row(), 0); d.add(row({ rawHash: 'sha256:' + 'b'.repeat(64), lineOrdinal: 10 }), 0);
  assert.equal(d.result().duplicateLocators, 1); assert.equal(d.result().uniqueLocators, 1);
  assert.throws(() => d.add(row({ signature: key(4, 64) }), 0), /POOL_CREATION_CONFLICT/);
});
test('Provenance and exact April May June-before28 boundaries cannot become historical PIT proof', () => {
  for (const [at, value] of [[0, x.DATES[1]], [1, x.DATES[2]], [2, x.DATES[3]]]) assert.throws(() => new x.Reducer().add(row({ slot: x.ROOTS[at].start, timestamp: value }), at), /PROVENANCE_INVALID/);
  for (const extra of [{ rawHash: 'bad' }, { lineOrdinal: -1 }, { slot: x.ROOTS[0].start - 1 }, { timestamp: x.DATES[0] - 1 }]) assert.throws(() => new x.Reducer().add(row(extra), 0), /PROVENANCE_INVALID/);
  assert.throws(() => new x.Reducer().add(row(), 0, new Map()), /PROVENANCE_INVALID/);
  const bytes = Buffer.from(JSON.stringify(row()).replace('410195947', '410195947.0')); assert.throws(() => x.parsedRow(bytes), /PROVENANCE_INVALID/);
  const r = new x.Reducer(); r.add(row({ instructionPath: [2, 10], cpiDepth: 1 }), 0); assert.equal(r.result().provisionalStudiedMints, 1);
  assert.equal(r.result().unknown.knownAt, 'UNKNOWN'); assert.equal(r.result().dispositions.april.recordedTiming, 'FAIL');
});
test('Fixed fifteen-input reduction verifies hashes metadata and all rows before qualified aggregate2', () => {
  const f = fixture(), r = x.inspect(f); assert.equal(r.code, null); assert.equal(x.exitCode(r), 2); assert.equal(f.reads.length, 15); assert.equal(r.inputRows, 5);
  assert.equal(r.provisionalStudiedMints, 5); assert.equal(r.inputs.length, 15); const out = x.serialize(r); assert.ok(Buffer.byteLength(out) < x.LIMITS.output);
  for (const forbidden of ['signature', 'baseMint', 'quoteMint', 'instructionPath', 'selected', 'rank']) assert.equal(Object.hasOwn(r, forbidden), false);
  for (const name of x.NAMES) { const bad = fixture(); const id = bad.roots[0].suffix + '/' + name; bad.files.set(id, Buffer.concat([bad.files.get(id), Buffer.from('x')]));
    assert.equal(x.exitCode(x.inspect(bad)), 1); }
  const bad = fixture(); bad.roots[0].pins[2][1] = '0'.repeat(64); assert.equal(x.inspect(bad).code, 'INTEGRITY_ERROR');
});
test('Disabled and invalid CLI have zero input IO and caps reject before reading', () => {
  const bad = { reader() { throw Error('must not read'); } };
  for (const args of [[], ['--root', 'anything'], ['--enable-offline', 'extra'], ['--replay']]) assert.equal(x.exitCode(x.runCli(args, bad)), 1);
  const f = fixture(); f.roots[0].pins[0][0] = 64000001; assert.equal(x.inspect(f).code, 'FILE_LIMIT'); assert.equal(f.reads.length, 0);
  const many = fixture(); for (const root of many.roots) for (const pin of root.pins) pin[0] = 20000000;
  assert.equal(x.inspect(many).code, 'INPUT_LIMIT'); assert.equal(many.reads.length, 0);
  const giant = fixture(); giant.roots[0].pins[2][0] = 200000 * 16384; assert.equal(x.exitCode(x.inspect(giant)), 1); assert.equal(giant.reads.length, 0);
  const count = new x.Reducer(); count.rows = 200000; assert.throws(() => count.add(row(), 0), /ROW_LIMIT/);
});
test('Maximum-line edge is independent from full-row envelope and is not silently truncated', () => {
  const serialized = JSON.stringify(row()), exact = Buffer.from(serialized + ' '.repeat(16383 - Buffer.byteLength(serialized)) + '\n');
  let count = 0; const lines = new x.Lines(b => { assert.equal(x.parsedRow(b).status, 'OBSERVED_DECLARED_CREATE_POOL'); count++; });
  for (let at = 0; at < exact.length; at += 31) lines.feed(exact.subarray(at, at + 31)); lines.finish(); assert.equal(count, 1);
  assert.throws(() => new x.Lines(() => {}).feed(Buffer.alloc(16385, 32)), /LINE_LIMIT/);
  const partial = new x.Lines(() => {}); partial.feed(Buffer.from('{}')); assert.throws(() => partial.finish(), /INTEGRITY_ERROR/);
});
test('Whole offline clock rollback deadline and output overflow stop without partial success', () => {
  const f = fixture(); let time = 0; f.now = () => time; const read = f.reader; f.reader = (...args) => { const r = read(...args), next = r.read;
    r.read = () => { time += 150001; return next(); }; return r; }; assert.equal(x.inspect(f).code, 'TIME_LIMIT');
  const backwards = fixture(); let calls = 0; backwards.now = () => ++calls === 1 ? 100 : 99; assert.equal(x.inspect(backwards).code, 'TIME_LIMIT');
  assert.throws(() => x.serialize({ text: 'x'.repeat(1000000) }), /OUTPUT_LIMIT/);
  const fs = require('node:fs'), original = fs.lstatSync; try { fs.lstatSync = () => ({ isSymbolicLink: () => true });
    assert.throws(() => x.reader(x.ROOTS[0], 'creations.jsonl'), /UNSAFE_PATH/); } finally { fs.lstatSync = original; }
});
test('Real pinned metadata and at most three first saved rows validate documentary shape without a real count', () => {
  const fs = require('node:fs'); let sampled = 0;
  for (const [at, root] of x.ROOTS.entries()) {
    const base = 'C:/crypto-research-evidence/r1-e2/' + root.suffix + '/', mb = fs.readFileSync(base + 'manifest.json'), sb = fs.readFileSync(base + 'summary.json');
    assert.equal(mb.length, root.pins[0][0]); assert.equal(digest(mb), 'sha256:' + root.pins[0][1]);
    assert.equal(sb.length, root.pins[1][0]); assert.equal(digest(sb), 'sha256:' + root.pins[1][1]); const provenance = x.metadata(root, at, mb, sb);
    if (at < 3) { const fd = fs.openSync(base + 'creations.jsonl', 'r'), parts = [], byte = Buffer.alloc(1);
      try { while (fs.readSync(fd, byte, 0, 1, null) === 1 && byte[0] !== 10) { assert.ok(parts.length < 16384); parts.push(byte[0]); } } finally { fs.closeSync(fd); }
      const saved = x.parsedRow(Buffer.from(parts)), r = new x.Reducer(); r.add(saved, at, provenance); sampled++;
      assert.equal(r.result().roots[at].documentaryShape, 1); assert.equal(r.result().cohortAdmitted, false); }
  }
  assert.equal(sampled, 3);
});
function stress() {
  const started = Date.now(), f = fixture(40000), declared = f.roots.reduce((sum, root) => sum + root.pins.reduce((n, p) => n + p[0], 0), 0);
  assert.ok(declared <= 200000000); for (const root of f.roots) for (const [n] of root.pins) assert.ok(n <= 64000000);
  const r = x.runCli(['--enable-offline'], f); assert.equal(r.code, null); assert.equal(r.inputRows, 200000); assert.equal(r.uniqueLocators, 200000);
  assert.equal(r.distinctObservedPools, 200000); assert.equal(r.provisionalStudiedMints, 200000); assert.equal(r.accounting.files, 15);
  const outputBytes = Buffer.byteLength(x.serialize(r)), elapsedMs = Date.now() - started, nativePeakRssBytes = process.resourceUsage().maxRSS * 1024;
  assert.ok(r.accounting.peakHeapBytes < 1024 * 1024 * 1024); assert.ok(elapsedMs <= 300000); assert.ok(outputBytes <= 1000000);
  return { rows: r.inputRows, uniqueLocators: r.uniqueLocators, pools: r.distinctObservedPools, provisionalMints: r.provisionalStudiedMints,
    files: 15, inputBytes: r.accounting.bytes, maxCreationFileBytes: Math.max(...f.roots.map(root => root.pins[2][0])), heapLimitMB: 1024,
    peakHeapBytes: r.accounting.peakHeapBytes, peakRssBytes: r.accounting.peakRssBytes, nativePeakRssBytes, elapsedMs, inspectElapsedMs: r.accounting.elapsedMs, outputBytes, nativeExit: 0 };
}
test('Full valid200000 unique-state rows fit15inputs200MB1024MBheap within5min and aggregate output', { timeout: 300000 }, () => {
  const { spawnSync } = require('node:child_process'); const child = spawnSync(process.execPath, ['--max-old-space-size=1024', '-e',
    "const r=require('./tools/research/r1/test/e2-offline-mint-count.test.cjs').stress();console.log(JSON.stringify(r));process.exit(0);"], { cwd: require('node:path').resolve(__dirname, '../../../..'), encoding: 'utf8', timeout: 300000, maxBuffer: 1000000 });
  assert.equal(child.status, 0, child.stderr); const metrics = JSON.parse(child.stdout.trim()); assert.equal(metrics.rows, 200000); assert.ok(metrics.inputBytes <= 200000000);
  process.stdout.write('STRESS_METRICS ' + JSON.stringify(metrics) + '\n');
});
module.exports = { fixture, stress };
