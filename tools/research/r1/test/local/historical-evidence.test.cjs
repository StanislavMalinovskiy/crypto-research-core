'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const c = require('../../e2-monthly-wait-guard-v2.cjs');
const x = require('../../e2-offline-mint-count.cjs');
const census = require('../../e2-monthly-census.cjs');
const tail = require('../../e2-monthly-tail.cjs');
const { digest } = require('../../exploratory-probe.cjs');

const EVIDENCE_ROOT = path.resolve(process.env.R1_E2_EVIDENCE_ROOT ?? 'C:/crypto-research-evidence/r1-e2');
function readRequired(file) {
  try { return fs.readFileSync(file); }
  catch (error) {
    if (error.code === 'ENOENT') assert.fail('required local historical input is missing: ' + file);
    throw error;
  }
}
function openRequired(file) {
  try { return fs.openSync(file, 'r'); }
  catch (error) {
    if (error.code === 'ENOENT') assert.fail('required local historical input is missing: ' + file);
    throw error;
  }
}

test('Pinned historical April metadata retains its literal accounting and source pins', () => {
  const names = [], source = { read(name) {
    names.push(name);
    return readRequired(path.join(EVIDENCE_ROOT, 'exploratory-monthly-census-v1-april', name));
  } };
  const m = c.verifyMetadata('april', source);
  assert.deepEqual(names, ['manifest.json', 'summary.json']);
  assert.equal(m.accounting.attempts, 13075); assert.equal(m.accounting.retries, 94);
  assert.equal(m.accounting.received, 149613720); assert.equal(m.accounting.retained, 206425601);
  assert.equal(m.lineage.source.operationalGuard, undefined); assert.equal(m.publicationOperational.elapsedMs, 8483802);
  assert.throws(() => c.verifyMetadata('april', { read(name) {
    const bytes = readRequired(path.join(EVIDENCE_ROOT, 'exploratory-monthly-census-v1-april', name));
    return name === 'summary.json' ? Buffer.concat([bytes, Buffer.from(' ')]) : bytes;
  } }));
  for (const [name, hash] of Object.entries(c.PINS))
    assert.equal(digest(fs.readFileSync(path.join(__dirname, '..', '..', name))), hash);
});

test('Pinned offline historical metadata and first rows retain documentary shape without a real count', () => {
  let sampled = 0;
  for (const [at, root] of x.ROOTS.entries()) {
    const base = path.join(EVIDENCE_ROOT, root.suffix), mb = readRequired(path.join(base, 'manifest.json')),
      sb = readRequired(path.join(base, 'summary.json'));
    assert.equal(mb.length, root.pins[0][0]); assert.equal(digest(mb), 'sha256:' + root.pins[0][1]);
    assert.equal(sb.length, root.pins[1][0]); assert.equal(digest(sb), 'sha256:' + root.pins[1][1]);
    const provenance = x.metadata(root, at, mb, sb);
    if (at < 3) {
      const fd = openRequired(path.join(base, 'creations.jsonl')), parts = [], byte = Buffer.alloc(1);
      try {
        while (fs.readSync(fd, byte, 0, 1, null) === 1 && byte[0] !== 10) {
          assert.ok(parts.length < 16384, 'first saved row exceeds the bounded local sample'); parts.push(byte[0]);
        }
      } finally { fs.closeSync(fd); }
      const saved = x.parsedRow(Buffer.from(parts)), r = new x.Reducer(); r.add(saved, at, provenance); sampled++;
      assert.equal(r.result().roots[at].documentaryShape, 1); assert.equal(r.result().cohortAdmitted, false);
    }
  }
  assert.equal(sampled, 3);
});

test('Pinned census historical boundary and eight controls validate; corrupted control fails', () => {
  const controlRoot = path.join(EVIDENCE_ROOT, 'exploratory-census-v1');
  const expectedNames = ['manifest.json', ...Array.from({ length: 8 }, (_, i) => String(i + 1).padStart(4, '0') + '.raw')];
  const reads = [];
  const proof = census.verifyBoundary(name => {
    reads.push(name);
    return readRequired(path.join(controlRoot, name));
  });
  assert.deepEqual(proof, { status: 'VERIFIED', bounds: census.BOUNDS, manifestHash: census.SEAL.manifestHash, controlBytes: 880 });
  assert.deepEqual(reads, expectedNames);

  const corruptedReads = [];
  assert.throws(() => census.verifyBoundary(name => {
    corruptedReads.push(name);
    const bytes = readRequired(path.join(controlRoot, name));
    return name === '0008.raw' ? Buffer.concat([bytes, Buffer.from('!')]) : bytes;
  }));
  assert.deepEqual(corruptedReads, expectedNames);
});

test('Pinned tail metadata proves the historical prefix without reading raw pages or creations', () => {
  const reads = [];
  const proof = tail.proof('may', month => ({ read(name) {
    reads.push(month + '/' + name);
    assert.ok(['manifest.json', 'summary.json'].includes(name), 'local prefix proof is bounded to pinned metadata');
    return readRequired(path.join(EVIDENCE_ROOT, 'exploratory-monthly-census-v1-' + month, name));
  } }));
  assert.equal(proof.totals.attempts, 40050); assert.equal(proof.totals.retries, 615); assert.equal(proof.totals.received, 444036897);
  assert.equal(reads.length, 6); assert.equal(proof.prefixes[2].manifestHash, 'sha256:34742b956c883ca3af487d7ff4cc14e0b9edae68fbe58fe4f9c6c539bda2fa69');
  assert.throws(() => tail.proof('may', month => ({ read(name) {
    const bytes = readRequired(path.join(EVIDENCE_ROOT, 'exploratory-monthly-census-v1-' + month, name));
    return month === 'june' && name === 'summary.json' ? Buffer.concat([bytes, Buffer.from('x')]) : bytes;
  } })));
});
