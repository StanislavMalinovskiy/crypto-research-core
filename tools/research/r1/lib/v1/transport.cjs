'use strict';
// Synchronous bounded byte transport from the B5 audit; no network or retry policy.
const need = (ok, code = 'INPUT_INVALID') => { if (!ok) throw Error(code); };
function scanBytes(read, limit, check, consume) {
  const buffer = Buffer.alloc(65536); let total = 0;
  while (true) { check(); const n = read(buffer); if (!n) break;
    total += n; need(total <= limit, 'INPUT_LIMIT'); consume(buffer.subarray(0, n)); }
  check(); return total;
}
module.exports = { scanBytes };
