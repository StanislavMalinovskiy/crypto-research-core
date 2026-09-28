'use strict';
const crypto = require('node:crypto');
const PROGRAMS = ['6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P',
  'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', '675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8'];
const fail = code => { const e = new Error(code); e.safeCode = code; throw e; };
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const exact = value => { const s = String(value); if (!/^\d+$/.test(s) || (typeof value === 'number' && !Number.isSafeInteger(value))) fail('UNSUPPORTED_INTEGER'); return BigInt(s).toString(); };
class Budget {
  constructor(limits, state = {}) {
    this.limits = limits; this.state = { received: 0, disk: 0, rpc: 0, stopped: null, grpcBytes: 0, rpcBytes: 0, ...state };
    this.controller = new AbortController(); this.persist = () => {};
    if (this.state.stopped) this.controller.abort();
  }
  stop(reason) { if (!this.state.stopped) { this.state.stopped = reason; this.controller.abort(); this.persist(); } return false; }
  reserve(bytes, disk, now, free) {
    if (this.state.stopped) return false;
    if (now >= this.limits.deadline) return this.stop('WALL_LIMIT');
    if (this.state.received + bytes > this.limits.received) return this.stop('RECEIVED_LIMIT');
    if (this.state.disk + disk > this.limits.disk) return this.stop('DISK_LIMIT');
    if (free - disk < this.limits.floor) return this.stop('FREE_SPACE_LIMIT');
    this.state.received += bytes; this.state.disk += disk; this.persist(); return true;
  }
  rpc() {
    if (this.state.stopped) return false;
    if (this.state.rpc >= this.limits.rpc) return this.stop('RPC_LIMIT');
    this.state.rpc++; this.persist(); return true;
  }
  settle(reserved, received, source) {
    this.state.received -= reserved - received;
    this.state[source === 'grpc' ? 'grpcBytes' : 'rpcBytes'] += received; this.persist();
  }
  check() { if (this.state.stopped) fail(this.state.stopped); }
}
class Queue {
  constructor(limit = 16 * 1024 * 1024) { this.limit = limit; this.bytes = 0; this.items = []; }
  push(value) { if (this.bytes + value.length > this.limit) return false; this.items.push(value); this.bytes += value.length; return true; }
  shift() { const value = this.items.shift(); if (value) this.bytes -= value.length; return value; }
}
class Timeline {
  constructor(now) { this.at = now; this.mode = 'REPLAY'; this.liveMs = 0; this.outcome = 'INCONCLUSIVE'; }
  tick(now) { if (this.mode === 'LIVE') this.liveMs += Math.max(0, now - this.at); this.at = now; }
  transition(mode, now) { this.tick(now); this.mode = mode; }
  fail(reason) { this.outcome = 'FAIL'; this.reason = reason; }
  stop(reason) { if (this.outcome !== 'FAIL') this.reason = reason; }
}
function config(env = {}, properties = '') {
  const names = ['ALCHEMY_API_KEY', 'ALCHEMY_SOLANA_RPC_ENDPOINT', 'ALCHEMY_SOLANA_GRPC_ENDPOINT', 'ALCHEMY_SOLANA_GRPC_PORT'];
  const values = {};
  for (const line of properties.split(/\r?\n/)) {
    const match = /^\s*([^#!\s=:]+)\s*[=:]\s*(.*?)\s*$/.exec(line);
    if (!match || !names.includes(match[1])) continue;
    if (Object.hasOwn(values, match[1])) fail('CONFIG_INVALID'); values[match[1]] = match[2];
  }
  for (const name of names) {
    if (Object.hasOwn(env, name)) values[name] = env[name];
    if (typeof values[name] !== 'string' || !values[name].trim() || /[\r\n\0]/.test(values[name])) fail('CONFIG_INVALID');
  }
  const key = values[names[0]]; let url;
  if (!/^[A-Za-z0-9_-]+$/.test(key)) fail('CONFIG_INVALID');
  let endpoint = values[names[1]];
  if (endpoint.includes('${')) {
    if (!/^https:\/\/(?:[a-z0-9-]+\.)+alchemy\.com(?::443)?\/v2\/\$\{ALCHEMY_API_KEY\}$/.test(endpoint)) fail('CONFIG_INVALID');
    endpoint = endpoint.replace('${ALCHEMY_API_KEY}', key);
  }
  try { url = new URL(endpoint); } catch { fail('CONFIG_INVALID'); }
  const host = values[names[2]].replace(/^https:\/\//, '').replace(/\/$/, '');
  const allowed = name => /^(?:[a-z0-9-]+\.)+alchemy\.com$/.test(name);
  if (!allowed(url.hostname) || url.protocol !== 'https:' || url.username || url.password || url.hash || url.search || (url.port && url.port !== '443')) fail('CONFIG_INVALID');
  if (!allowed(host) || values[names[3]] !== '443' || !/^[A-Za-z0-9_-]+$/.test(key)) fail('CONFIG_INVALID');
  if (url.pathname !== `/v2/${key}`) fail('CONFIG_INVALID');
  return { key, rpcEndpoint: url.toString(), grpcEndpoint: `${host}:443` };
}
function diagnostic(error) { return error && /^[A-Z][A-Z0-9_]{0,63}$/.test(error.safeCode || '') ? error.safeCode : 'INTERNAL_ERROR'; }
async function launch(options) {
  if (!options.enabled) return { reason: 'LIVE_DISABLED' };
  let settings; try { settings = config(options.env, options.properties); } catch { return { reason: 'CONFIG_INVALID' }; }
  return options.connect(settings);
}
function filterBlock(block) {
  if (!block || !Array.isArray(block.transactions)) fail('BLOCK_UNAVAILABLE');
  const output = [];
  block.transactions.forEach((entry, index) => {
    if (entry.version !== undefined && entry.version !== 'legacy' && entry.version !== 0 && entry.version !== 1) fail('UNSUPPORTED');
    const transaction = entry.transaction, meta = entry.meta;
    if (!transaction || !Array.isArray(transaction.signatures) || !transaction.message || !Array.isArray(transaction.message.accountKeys) || !meta || !Object.hasOwn(meta, 'err')) fail('UNSUPPORTED');
    const message = transaction.message, hasConfig = Object.hasOwn(message, 'transactionConfig');
    if (entry.version === 1) {
      if (!hasConfig || !message.transactionConfig || typeof message.transactionConfig !== 'object' || Array.isArray(message.transactionConfig)) fail('UNSUPPORTED');
      if ((message.addressTableLookups !== undefined && (!Array.isArray(message.addressTableLookups) || message.addressTableLookups.length))
        || (meta.loadedAddresses !== undefined && (!meta.loadedAddresses || typeof meta.loadedAddresses !== 'object' || Array.isArray(meta.loadedAddresses)
          || ['writable', 'readonly'].some(k => meta.loadedAddresses[k] !== undefined && (!Array.isArray(meta.loadedAddresses[k]) || meta.loadedAddresses[k].length))))) fail('UNSUPPORTED');
    } else if (hasConfig) fail('UNSUPPORTED');
    if (meta.err !== null) return;
    if (entry.version === 0 && !meta.loadedAddresses) fail('UNSUPPORTED');
    const keys = [...transaction.message.accountKeys, ...(meta.loadedAddresses?.writable || []), ...(meta.loadedAddresses?.readonly || [])];
    if (!keys.every(k => typeof k === 'string')) fail('UNSUPPORTED');
    const instructions = transaction.message.instructions;
    if (!Array.isArray(instructions)) fail('UNSUPPORTED');
    if (instructions.length === 1 && transaction.signatures.length < 3 && keys[instructions[0].programIdIndex] === 'Vote111111111111111111111111111111111111111') return;
    if (!keys.some(k => PROGRAMS.includes(k))) return;
    if (typeof transaction.signatures[0] !== 'string') fail('UNSUPPORTED');
    output.push({ signature: transaction.signatures[0], index: String(index) });
  }); return output;
}
async function reconcile({ from, to, journal, call, sampled = false }) {
  from = exact(from); to = exact(to); const initial = from;
  if (BigInt(to) < BigInt(from)) return { complete: false, outcome: 'INCONCLUSIVE', reason: 'UPPER_BOUND_NOT_ADVANCED' };
  journal.state.unresolved = [{ from, to }]; journal.checkpoint();
  try {
    for (let low = BigInt(from); low <= BigInt(to); low += 1000n) {
      const high = low + 999n < BigInt(to) ? low + 999n : BigInt(to);
      if (high > BigInt(Number.MAX_SAFE_INTEGER)) fail('UNSUPPORTED_INTEGER');
      const slots = await call('getBlocks', [Number(low), Number(high), { commitment: 'finalized' }]);
      if (!Array.isArray(slots) || slots.length > 1000) fail('BLOCK_UNAVAILABLE');
      let previous = low - 1n;
      for (const value of slots) {
        const slot = BigInt(exact(value)); if (slot < low || slot > high || slot <= previous) fail('UNSUPPORTED'); previous = slot;
        const block = await call('getBlock', [Number(slot), { commitment: 'finalized', encoding: 'json', transactionDetails: 'full', rewards: false, maxSupportedTransactionVersion: 1 }]);
        const expected = filterBlock(block).sort((a, b) => a.signature.localeCompare(b.signature));
        const actual = journal.signatures(String(slot)).map(({ signature, index }) => ({ signature, index })).sort((a, b) => a.signature.localeCompare(b.signature));
        if (JSON.stringify(expected) !== JSON.stringify(actual)) {
          journal.event({ type: 'mismatch', slot: String(slot), expected, actual });
          return { complete: false, outcome: 'FAIL', reason: 'UNEXPLAINED_LOSS' };
        }
      }
      for (let slot = low; slot <= high; slot++) {
        if (!slots.some(s => BigInt(s) === slot) && journal.signatures(String(slot)).length) {
          journal.event({ type: 'unexpected_skipped_slot', slot: String(slot) }); return { complete: false, outcome: 'FAIL', reason: 'UNEXPLAINED_LOSS' };
        }
      }
      if (!sampled) journal.verifyRange(String(low), String(high), []);
      journal.event({ type: 'verified_range', from: String(low), to: String(high), produced: slots.length, sampled });
      journal.state.unresolved = high === BigInt(to) ? [] : [{ from: String(high + 1n), to }]; journal.checkpoint();
    }
    return { complete: true, from: initial, to };
  } catch (e) { return { complete: false, outcome: 'INCONCLUSIVE', reason: diagnostic(e) }; }
}
module.exports = { Budget, Queue, Timeline, config, diagnostic, launch, filterBlock, reconcile, PROGRAMS, fail, hash, exact };
Object.defineProperties(module.exports, {
  Journal: { get: () => require('./journal.cjs').Journal },
  rpc: { get: () => require('./transport.cjs').rpc }, stream: { get: () => require('./transport.cjs').stream },
  supervise: { get: () => require('./watchdog.cjs').supervise }
});
