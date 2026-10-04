'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const c = require('../lib/v1/canonicalization.cjs'), p = require('../lib/v1/parsing.cjs');
const parser = require('../e2-census-parser-v2.cjs');

test('v1 canonical encoding has literal UTF8 byte lengths typed atoms and sorted object keys', () => {
  assert.equal(c.canonical({ z: null, a: 'é' }), 'o16:k1:as2:ék1:zz0:');
  assert.equal(c.canonical({ a: 'é', z: null }), 'o16:k1:as2:ék1:zz0:');
  assert.equal(c.canonical([true, false, 2]), 'a12:b1:1b1:0n1:2');
  assert.notEqual(c.canonical(['a|b', 'c']), c.canonical(['a', 'b|c']));
  assert.notEqual(c.canonical([2, 10]), c.canonical([10, 2]));
  assert.notEqual(c.canonical(new p.NumericToken('2.0')), c.canonical(2));
  assert.throws(() => c.canonical(0.1), /RESPONSE_INVALID/);
});
test('v1 exact serialization retains object iteration order and numeric token class across consumers', () => {
  const value = p.parseExact(Buffer.from('{"z":2e0,"a":18446744073709551615}'));
  assert.equal(c.stringifyExact(value), '{"z":2e0,"a":18446744073709551615}');
  for (const name of ['parseExact', 'NumericToken', 'uint', 'decode', 'numericPath']) assert.equal(parser[name], p[name]);
  for (const name of ['canonical', 'stringifyExact', 'digest', 'comparePath', 'compare']) assert.equal(parser[name], c[name]);
});
test('v1 digest covers exact bytes with the unchanged literal SHA256 format', () => {
  assert.equal(c.digest(Buffer.from('abc')), 'sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  assert.notEqual(c.digest(Buffer.from('abc')), c.digest(Buffer.from('abc\n')));
});
test('v1 total ordering uses numeric slot index path prefix and signature tie break', () => {
  const row = (slot, transactionIndex, instructionPath, signature) => ({ slot, transactionIndex, instructionPath, signature });
  const expected = [row(1, 2, [2], 'a'), row(1, 2, [2], 'b'), row(1, 2, [2, 0], 'a'),
    row(1, 2, [10], 'a'), row(1, 10, [0], 'a'), row(2, 0, [0], 'a')];
  assert.deepEqual([...expected].reverse().sort(c.compare), expected);
  assert.equal(c.comparePath([2], [2, 0]), -1); assert.equal(c.comparePath([10], [2]), 1);
  assert.equal(c.compare(expected[0], { ...expected[0] }), 0);
});
test('v1 encoded ceiling retains the exact supported B5 layout limits', () => {
  assert.equal(c.encodedCeiling(60), 82);
  assert.equal(parser.LAYOUT.decodedBytes, 60); assert.equal(parser.LAYOUT.encodedCharacters, 82);
  assert.deepEqual(parser.boundedLimits(), parser.LIMITS);
});
