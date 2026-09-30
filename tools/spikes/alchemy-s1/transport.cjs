'use strict';
const http = require('node:http');
const https = require('node:https');
const path = require('node:path');
const grpc = require('@grpc/grpc-js');
const loader = require('@grpc/proto-loader');
const { PROGRAMS, Queue, fail, diagnostic } = require('./core.cjs');
const definitions = loader.loadSync(path.join(__dirname, 'proto/geyser.proto'), { keepCase: true, longs: String, bytes: Buffer, defaults: false, oneofs: true });
const service = grpc.loadPackageDefinition(definitions).geyser.Geyser.service;
const subscription = service.Subscribe;
const FRAME = 8 * 1024 * 1024;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function decode(raw) { try { return subscription.responseDeserialize(raw); } catch { fail('SCHEMA_INVALID'); } }
function encode(update) { return subscription.responseSerialize(update); }
function local(url) { return url.hostname === '127.0.0.1' || url.hostname === '[::1]'; }
async function rpc(options) {
  const { endpoint, method, params, budget } = options;
  budget.check(); if (budget.rpcBusy) fail('RPC_CONCURRENCY'); budget.rpcBusy = true;
  const max = options.maxBody || 16 * 1024 * 1024, reservation = max + 65536;
  const now = options.now || Date.now, wait = options.sleep || sleep;
  let target;
  try { target = new URL(endpoint); } catch { budget.rpcBusy = false; fail('CONFIG_INVALID'); }
  if (target.protocol !== 'https:' && !(options.allowLocal && target.protocol === 'http:' && local(target))) { budget.rpcBusy = false; fail('CONFIG_INVALID'); }
  try {
    const maxAttempts = options.maxAttempts || 3;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      await wait(Math.max(0, (budget.lastRpc || 0) + 500 - now())); budget.check();
      if (!budget.rpc(options.cuPerAttempt || 0)) budget.check(); budget.lastRpc = now();
      if (!budget.reserve(reservation, 0, now(), Infinity)) budget.check();
      let received = 0;
      try {
        const result = await new Promise((resolve, reject) => {
          let done = false, request, timer; const chunks = [];
          const finish = (code, result) => {
            if (done) return; done = true; clearTimeout(timer); budget.controller.signal.removeEventListener('abort', abort);
            if (request && code) request.destroy();
            if (code) { const e = new Error(code); e.safeCode = code; reject(e); } else resolve(result);
          };
          const abort = () => finish(budget.state.stopped || 'CANCELLED');
          budget.controller.signal.addEventListener('abort', abort, { once: true });
          request = (target.protocol === 'https:' ? https : http).request(target, {
            method: 'POST', headers: { 'content-type': 'application/json' }, agent: false,
          }, response => {
            if (response.statusCode >= 300 && response.statusCode < 400) { finish('RPC_REDIRECT'); return; }
            if ([401, 403].includes(response.statusCode)) { finish('AUTH_FAILED'); return; }
            response.on('data', chunk => {
              received += chunk.length;
              try {
                if (options.onRaw) options.onRaw(chunk, { source: 'rpc', method, attempt: attempt + 1, request: budget.state.rpc,
                  receivedAt: new Date(now()).toISOString(), bodyOffset: received - chunk.length, status: response.statusCode });
              } catch (e) { finish(diagnostic(e)); return; }
              if (received <= max) chunks.push(chunk);
              else { finish('RPC_BODY_LIMIT'); return; }
            });
            response.on('error', () => finish('RPC_UNAVAILABLE'));
            response.on('end', () => {
              if (response.statusCode !== 200) { finish(response.statusCode === 429 ? 'RPC_QUOTA' : 'RPC_UNAVAILABLE'); return; }
              let value; try { value = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { finish('RPC_INVALID'); return; }
              if (value.error || !Object.hasOwn(value, 'result')) { finish('RPC_RESPONSE_ERROR'); return; }
              finish(null, value.result);
            });
          });
          request.on('error', () => finish('RPC_UNAVAILABLE'));
          timer = setTimeout(() => finish('RPC_TIMEOUT'), 15000);
          request.end(JSON.stringify({ jsonrpc: '2.0', id: budget.state.rpc, method, params }));
        }); return result;
      } catch (e) {
        const code = diagnostic(e);
        if (budget.state.stopped || !['RPC_UNAVAILABLE', 'RPC_TIMEOUT', 'RPC_QUOTA'].includes(code) || attempt + 1 === maxAttempts) throw e;
        await wait(1000 * 2 ** attempt);
      } finally { budget.settle(reservation, received, 'rpc'); }
    }
  } finally { budget.rpcBusy = false; }
}
async function stream(options) {
  const { endpoint, budget, onRaw } = options; budget.check();
  if (options.maxReceivedBytes !== undefined && (!Number.isSafeInteger(options.maxReceivedBytes) || options.maxReceivedBytes < FRAME)) fail('ARGUMENT_INVALID');
  if (budget.streamBusy) fail('STREAM_CONCURRENCY');
  if ((budget.state.streamStarts || 0) >= (options.maxStreamStarts || 20)) { budget.stop('STREAM_LIMIT'); budget.check(); }
  if (options.allowLocal && !/^127\.0\.0\.1:\d+$/.test(endpoint)) fail('CONFIG_INVALID');
  if (!options.allowLocal && !/^(?:[a-z0-9-]+\.)+alchemy\.com:443$/.test(endpoint)) fail('CONFIG_INVALID');
  const now = options.now || Date.now;
  if (!budget.reserve(FRAME, 0, now(), Infinity, 'grpc')) budget.check();
  let reserved = FRAME, sessionReceived = 0, call, last = now(), ended = false, reason = 'STREAM_ENDED', tick, expiry;
  const queue = new Queue(16 * 1024 * 1024);
  budget.streamBusy = true; budget.state.streamStarts = (budget.state.streamStarts || 0) + 1; budget.persist();
  const client = new grpc.Client(endpoint, options.allowLocal ? grpc.credentials.createInsecure() : grpc.credentials.createSsl(), {
    'grpc.max_receive_message_length': FRAME, 'grpc.enable_retries': 0, 'grpc.initial_reconnect_backoff_ms': 1000,
    'grpc.max_reconnect_backoff_ms': 4000, 'grpc.keepalive_time_ms': 10000, 'grpc.keepalive_timeout_ms': 10000,
  });
  let resolveDone; const done = new Promise(resolve => { resolveDone = resolve; });
  const finish = code => {
    if (ended) return; ended = true; reason = code; clearInterval(tick); clearTimeout(expiry); budget.controller.signal.removeEventListener('abort', abort);
    if (call) call.cancel(); client.close(); budget.streamBusy = false;
    if (reserved) { budget.settle(reserved, 0, 'grpc'); reserved = 0; }
    resolveDone({ reason, lastReceived: last });
  };
  const abort = () => finish(budget.state.stopped || 'CANCELLED');
  budget.controller.signal.addEventListener('abort', abort, { once: true });
  try { await new Promise((resolve, reject) => client.waitForReady(new Date(now() + 15000), err => err ? reject(err) : resolve())); }
  catch { finish('STREAM_CONNECT'); return { done, cancel: finish }; }
  if (ended) return { done, cancel: finish };
  const metadata = new grpc.Metadata(); metadata.set('x-token', options.key);
  call = client.makeBidiStreamRequest(subscription.path, subscription.requestSerialize, raw => {
    // Process one raw frame synchronously before gRPC can request the next one.
    // No decoded protobuf or wrapper reserialization substitutes for these bytes.
    if (ended) return {};
    last = now(); const held = reserved; reserved = 0;
    budget.settle(held, raw.length, 'grpc');
    sessionReceived += raw.length;
    try {
      if (!queue.push(raw)) fail('QUEUE_LIMIT');
      const result = onRaw(queue.shift(), { source: 'grpc', receivedAt: new Date(last).toISOString() });
      if (result && typeof result.then === 'function') fail('ASYNC_RAW_HANDLER');
      if (options.stopOnFirstFinalizedSlot) {
        const update = decode(raw);
        if (update.slot?.status === 2 || update.slot?.status === 'SLOT_FINALIZED') { finish('FIRST_FINALIZED_SLOT'); return {}; }
      }
      if (options.maxReceivedBytes !== undefined && options.maxReceivedBytes - sessionReceived < FRAME) { finish('SESSION_RECEIVED_LIMIT'); return {}; }
      if (now() - last > 250) fail('PROCESSING_LIMIT');
      if (!ended && budget.reserve(FRAME, 0, now(), Infinity, 'grpc')) reserved = FRAME;
      else finish(budget.state.stopped || 'CANCELLED');
    } catch (e) { finish(diagnostic(e)); }
    return {};
  }, metadata);
  call.on('data', () => {}); call.on('error', e => finish([7, 16].includes(e.code) ? 'AUTH_FAILED' : 'STREAM_ERROR'));
  call.on('end', () => finish('STREAM_ENDED'));
  call.write(options.subscription || { transactions: { watched: { vote: false, failed: false, account_include: PROGRAMS } },
    slots: { finalized: { filter_by_commitment: true } }, blocks_meta: { finalized: {} }, commitment: 2, from_slot: options.from });
  tick = setInterval(() => { if (now() - last >= 30000) finish('STREAM_STALLED'); }, 250);
  if (options.timeoutMs) { expiry = setTimeout(() => finish('STREAM_TIMEOUT'), options.timeoutMs); expiry.unref?.(); }
  return { done, cancel: code => finish(code || 'DISCONNECT'), ping: () => { if (!ended) call.write({ ping: { id: 1 } }); } };
}
module.exports = { rpc, stream, decode, encode, definitions, sleep };
