'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const p = require('../lib/v1/parsing.cjs');
const c = require('../lib/v1/canonicalization.cjs');

test('v1 exact reader preserves large integer decimal exponent and signed zero lexemes', () => {
  const text = '{"u64":18446744073709551615,"decimal":1.2300,"exp":2E+03,"zero":-0}';
  const value = p.parseExact(Buffer.from(text));
  assert.equal(Object.getPrototypeOf(value), null);
  assert.ok(value.u64 instanceof p.NumericToken); assert.ok(Object.isFrozen(value.u64));
  assert.equal(c.stringifyExact(value), text);
});
test('v1 reader rejects decoded duplicate keys malformed JSON and invalid UTF8', () => {
  for (const text of ['{"a":1,"\\u0061":2}', '{"x":{"b":1,"b":2}}', '[01]', '[1,]',
    '{"a":true} trailing', '"unterminated', '[NaN]', '[+1]', '[1.]', '[1e]']) {
    assert.throws(() => p.parseExact(Buffer.from(text)), /RESPONSE_INVALID/);
  }
  assert.throws(() => p.parseExact(Buffer.from([0xff])), TypeError);
});
test('v1 reader applies byte depth node and injected check bounds', () => {
  assert.throws(() => p.parseExact(Buffer.from('null'), { maxBytes: 3 }), /INPUT_LIMIT/);
  assert.throws(() => p.parseExact(Buffer.from('[0]'), { maxDepth: 0 }), /INPUT_LIMIT/);
  assert.throws(() => p.parseExact(Buffer.from('[0]'), { maxNodes: 1 }), /INPUT_LIMIT/);
  let checks = 0;
  p.parseExact(Buffer.from('[' + Array(1024).fill('0').join(',') + ']'), { check: () => checks++ });
  assert.equal(checks, 2);
  assert.throws(() => p.parseExact(Buffer.from('0'), { check: () => { throw Error('TIME_LIMIT'); } }), /TIME_LIMIT/);
});
test('v1 unsigned integers preserve safe bounds and reject alternate numeric spellings', () => {
  for (const text of ['0', '2', '9007199254740991']) assert.equal(p.uint(p.parseExact(Buffer.from(text))), Number(text));
  for (const text of ['-0', '-1', '2.0', '2e0', '9007199254740992', '"2"']) {
    assert.throws(() => p.uint(p.parseExact(Buffer.from(text))), /RESPONSE_INVALID/);
  }
  assert.equal(p.uint(new p.NumericToken('10'), 10), 10);
  assert.throws(() => p.uint(new p.NumericToken('11'), 10), /RESPONSE_INVALID/);
  assert.deepEqual(p.numericPath([new p.NumericToken('2'), 10]), [2, 10]);
  for (const value of [[], [1, 2], ['2']]) assert.throws(() => p.numericPath(value, 1), /RESPONSE_INVALID/);
});
test('v1 base58 bounds leading zeros alphabet and exact address signature lengths', () => {
  assert.deepEqual(p.decode('12', 2, 2), Buffer.from([0, 1]));
  assert.deepEqual(p.decode('5Q', 2, 1), Buffer.from([255]));
  assert.equal(p.address('1'.repeat(32)), '1'.repeat(32));
  assert.equal(p.signature('1'.repeat(64)), '1'.repeat(64));
  for (const args of [['', 82, 60], ['1'.repeat(83), 82, 60], ['11', 2, 1], ['5R', 2, 1]]) {
    assert.throws(() => p.decode(...args, 'INSTRUCTION_DATA_LIMIT'), /INSTRUCTION_DATA_LIMIT/);
  }
  for (const ch of ['0', 'O', 'I', 'l', ' ']) assert.throws(() => p.decode(ch, 1, 1), /RESPONSE_INVALID/);
  assert.throws(() => p.address('1'.repeat(31)), /RESPONSE_INVALID/);
  assert.throws(() => p.signature('1'.repeat(65)), /RESPONSE_INVALID/);
});
test('v1 limit validation only narrows declared positive safe bounds', () => {
  const defaults = { bytes: 10, rows: 2 };
  assert.deepEqual(p.boundedLimits({ bytes: 5 }, defaults), { bytes: 5, rows: 2 });
  assert.ok(Object.isFrozen(p.boundedLimits({}, defaults)));
  for (const overrides of [{ bytes: 11 }, { bytes: 0 }, { bytes: 1.5 }, { unknown: 1 }]) {
    assert.throws(() => p.boundedLimits(overrides, defaults), /LIMIT_INVALID/);
  }
  assert.deepEqual(defaults, { bytes: 10, rows: 2 });
});
