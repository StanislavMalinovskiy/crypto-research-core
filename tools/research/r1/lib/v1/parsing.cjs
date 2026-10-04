'use strict';
// Versioned pure primitives extracted from the approved B5 parser.
const need = (ok, code = 'RESPONSE_INVALID') => { if (!ok) throw Error(code); };
const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function boundedLimits(overrides = {}, defaults) { need(overrides && typeof overrides === 'object');
  const result = { ...defaults }; for (const [k, v] of Object.entries(overrides)) {
    need(Object.hasOwn(defaults, k) && Number.isSafeInteger(v) && v > 0 && v <= defaults[k], 'LIMIT_INVALID'); result[k] = v;
  } return Object.freeze(result); }
class NumericToken { constructor(text) { this.text = text; Object.freeze(this); } }
// A small bounded JSON reader preserves lexemes and rejects duplicate decoded keys.
function parseExact(bytes, { maxBytes = 64000000, maxDepth = 64, maxNodes = 1000000, check = () => {} } = {}) {
  need(Buffer.isBuffer(bytes) && bytes.length <= maxBytes, 'INPUT_LIMIT');
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes); let at = 0, nodes = 0;
  const ws = () => { while (/^[\x20\x09\x0a\x0d]$/.test(text[at] ?? '')) at++; };
  function str() { const start = at++; need(text[start] === '"'); let closed = false;
    while (at < text.length) { const c = text[at++]; if (c === '\\') at++; else if (c === '"') { closed = true; break; } }
    need(closed); return JSON.parse(text.slice(start, at)); }
  function value(depth) { need(depth <= maxDepth && ++nodes <= maxNodes, 'INPUT_LIMIT'); if (!(nodes % 1024)) check(); ws(); const c = text[at];
    if (c === '"') return str();
    if (c === '{') { at++; const o = Object.create(null), keys = new Set(); ws(); if (text[at] === '}') { at++; return o; }
      while (true) { ws(); const k = str(); need(!keys.has(k)); keys.add(k); ws(); need(text[at++] === ':'); o[k] = value(depth + 1); ws();
        const end = text[at++]; if (end === '}') return o; need(end === ','); } }
    if (c === '[') { at++; const a = []; ws(); if (text[at] === ']') { at++; return a; }
      while (true) { a.push(value(depth + 1)); ws(); const end = text[at++]; if (end === ']') return a; need(end === ','); } }
    for (const [token, v] of [['true', true], ['false', false], ['null', null]]) if (text.startsWith(token, at)) { at += token.length; return v; }
    const match = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/.exec(text.slice(at)); need(match); at += match[0].length;
    return new NumericToken(match[0]);
  }
  const out = value(0); ws(); need(at === text.length); check(); return out;
}
function uint(v, max = Number.MAX_SAFE_INTEGER) { const text = v instanceof NumericToken ? v.text : typeof v === 'number' ? String(v) : '';
  need(/^(0|[1-9][0-9]*)$/.test(text) && BigInt(text) <= BigInt(max)); return Number(text); }
function decode(text, maxChars, maxBytes, code = 'RESPONSE_INVALID') {
  // Both encoded work and decoded allocation are bounded before the BigInt loop.
  need(typeof text === 'string' && text.length > 0 && text.length <= maxChars, code);
  let n = 0n, zeros = 0; while (text[zeros] === '1') zeros++;
  need(zeros <= maxBytes, code);
  for (const ch of text) { const digit = ALPHABET.indexOf(ch); need(digit >= 0); n = n * 58n + BigInt(digit); }
  const hex = n.toString(16), bodyBytes = n ? Math.ceil(hex.length / 2) : 0;
  need(zeros + bodyBytes <= maxBytes, code);
  return Buffer.concat([Buffer.alloc(zeros), n ? Buffer.from(hex.padStart(bodyBytes * 2, '0'), 'hex') : Buffer.alloc(0)]);
}
const address = v => { need(decode(v, 44, 32).length === 32); return v; };
const signature = v => { need(decode(v, 88, 64).length === 64); return v; };
function numericPath(v, limit = 64) { need(Array.isArray(v) && v.length > 0 && v.length <= limit); return v.map(n => uint(n)); }
module.exports = { NumericToken, parseExact, uint, decode, address, signature, numericPath, boundedLimits };
