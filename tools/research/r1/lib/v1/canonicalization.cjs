'use strict';
// Exact bytes, lexemes and ordering retain the e2-census-c14n-v2 meaning.
const crypto = require('node:crypto');
const { NumericToken } = require('./parsing.cjs');
const need = (ok, code = 'RESPONSE_INVALID') => { if (!ok) throw Error(code); };
const digest = bytes => 'sha256:' + crypto.createHash('sha256').update(bytes).digest('hex');
function encodedCeiling(bytes) { let capacity = 1n, chars = 0; const ceiling = 256n ** BigInt(bytes);
  while (capacity < ceiling) { capacity *= 58n; chars++; } return Math.max(chars, bytes); }
function stringifyExact(v) {
  if (v instanceof NumericToken) return v.text;
  if (Array.isArray(v)) return '[' + v.map(stringifyExact).join(',') + ']';
  if (v && typeof v === 'object') return '{' + Object.keys(v).map(k => JSON.stringify(k) + ':' + stringifyExact(v[k])).join(',') + '}';
  need(v === null || typeof v === 'string' || typeof v === 'boolean' || Number.isSafeInteger(v)); return JSON.stringify(v);
}
function canonical(v) { const atom = (tag, s) => tag + Buffer.byteLength(s) + ':' + s;
  if (v instanceof NumericToken) return atom('n', v.text);
  if (v === null) return 'z0:';
  if (typeof v === 'string') return atom('s', v);
  if (typeof v === 'boolean') return atom('b', v ? '1' : '0');
  if (Number.isSafeInteger(v)) return atom('n', String(v));
  if (Array.isArray(v)) return atom('a', v.map(canonical).join(''));
  need(v && typeof v === 'object'); return atom('o', Object.keys(v).sort().map(k => atom('k', k) + canonical(v[k])).join(''));
}
function comparePath(a, b) { for (let i = 0; i < Math.min(a.length, b.length); i++) if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1; return a.length - b.length; }
function compare(a, b) { return a.slot - b.slot || a.transactionIndex - b.transactionIndex || comparePath(a.instructionPath, b.instructionPath)
  || (a.signature < b.signature ? -1 : a.signature > b.signature ? 1 : 0); }
module.exports = { digest, encodedCeiling, stringifyExact, canonical, comparePath, compare };
