'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const { scanBytes } = require('../lib/v1/transport.cjs');
function fake(chunks) {
  let reads = 0;
  return { read(buffer) { assert.equal(buffer.length, 65536); const chunk = chunks[reads++];
    if (!chunk) return 0; chunk.copy(buffer); return chunk.length; }, reads: () => reads };
}
test('v1 fake byte transport consumes ordered chunks at the exact total bound with fixed allocation', () => {
  const transport = fake([Buffer.alloc(65536, 1), Buffer.from('tail')]), seen = []; let checks = 0;
  const count = scanBytes(transport.read, 65540, () => checks++, b => seen.push(Buffer.from(b)));
  assert.equal(count, 65540); assert.equal(transport.reads(), 3); assert.equal(checks, 4);
  assert.deepEqual(seen, [Buffer.alloc(65536, 1), Buffer.from('tail')]);
});
test('v1 byte overflow stops before publishing the offending chunk with no retry', () => {
  const transport = fake([Buffer.from('abc'), Buffer.from('de')]), seen = [];
  assert.throws(() => scanBytes(transport.read, 4, () => {}, b => seen.push(Buffer.from(b))), /INPUT_LIMIT/);
  assert.equal(transport.reads(), 2); assert.deepEqual(seen, [Buffer.from('abc')]);
});
test('v1 check transport and consumer failures propagate without another read', () => {
  const transport = fake([Buffer.from('abc')]);
  assert.throws(() => scanBytes(transport.read, 3, () => { throw Error('TIME_LIMIT'); }, () => {}), /TIME_LIMIT/);
  assert.equal(transport.reads(), 0);
  let reads = 0;
  assert.throws(() => scanBytes(() => { reads++; throw Error('READ_FAILED'); }, 3, () => {}, () => {}), /READ_FAILED/);
  assert.equal(reads, 1);
  assert.throws(() => scanBytes(transport.read, 3, () => {}, () => { throw Error('CONSUME_FAILED'); }), /CONSUME_FAILED/);
  assert.equal(transport.reads(), 1);
});
test('v1 empty transport and terminal budget check preserve the B5 loop semantics', () => {
  const transport = fake([]); let checks = 0, consumed = 0;
  assert.equal(scanBytes(transport.read, 1, () => checks++, () => consumed++), 0);
  assert.equal(checks, 2); assert.equal(consumed, 0); assert.equal(transport.reads(), 1);
  const terminal = fake([]); let terminalChecks = 0;
  assert.throws(() => scanBytes(terminal.read, 1, () => { if (++terminalChecks === 2) throw Error('TIME_LIMIT'); }, () => {}), /TIME_LIMIT/);
  assert.equal(terminal.reads(), 1);
});
