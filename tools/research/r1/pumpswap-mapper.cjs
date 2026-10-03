'use strict';
const { parse, canonical, digest, fingerprint, integer } = require('./exploratory-probe.cjs');
const PUMP = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', TOKEN = 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA';
const SYSTEM = '11111111111111111111111111111111', ASSOCIATED = 'ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL';
const TOKEN2022 = 'TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb';
const FLAGS = { classification: 'OFFLINE_DEVELOPMENT', d1Evidence: false, d1Passed: false, runAuthorized: false };
const CONFIG = { version: 'pumpswap-offline-mapper-v1', factsVersion: 'pumpswap-offline-facts-v1', reportVersion: 'pumpswap-offline-report-v1',
  limits: { input: 2000000, output: 2000000, transactions: 1000, instructions: 10000, tokenBalances: 10000, balances: 10000,
    path: 64, accounts: 128, dataCharacters: 1024, supportedCharacters: 512, decodedBytes: 384, diagnostics: 1000 },
  range: { from: 1775001600, toExclusive: 1788134400 }, chain: 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',
  programs: { pump: PUMP, token: TOKEN, system: SYSTEM, associated: ASSOCIATED, token2022: TOKEN2022 },
  roles: { pool: 0, declaredTrader: 1, baseMint: 3, quoteMint: 4, userBase: 5, userQuote: 6, poolBaseVault: 7, poolQuoteVault: 8,
    baseTokenProgram: 11, quoteTokenProgram: 12, system: 13, associated: 14, self: 16 },
  variants: { '66063d1201daebea': { name: 'buy', bytes: 25, accounts: 23 },
    c62e1552b4d9e870: { name: 'buy_exact_quote_in', bytes: 25, accounts: 23 },
    '33e685a4017f83ad': { name: 'sell', bytes: 24, accounts: 21 } },
  transfers: { transfer: { tag: 3, bytes: 9, accounts: 3 }, transferChecked: { tag: 12, bytes: 10, accounts: 4 } },
  sources: { pumpIdl: { commit: '82dacacf15ca93dc0444ab38714f2226210a0a3d', bytes: 130535,
    sha256: '5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6',
    url: 'https://raw.githubusercontent.com/pump-fun/pump-public-docs/82dacacf15ca93dc0444ab38714f2226210a0a3d/idl/pump_amm.json',
    name: 'pump_amm', version: '0.1.0', spec: '0.1.0' },
    classicSpl: { commit: '109c4c91f914c248f77501605ab7e9bd14d67120', bytes: 60588,
      sha256: '0822f6fa79004e4401c927601fa92ffe95ca146369f1191e3027e080969298ae',
      url: 'https://raw.githubusercontent.com/solana-program/token/109c4c91f914c248f77501605ab7e9bd14d67120/interface/src/instruction.rs' } },
  ownedDeltaScope: 'TRANSACTION_USER_MINT_DECIMALS_NOT_INVOCATION_EXECUTION', nativeBalances: 'IGNORED_NO_TRANSACTION_LINK' };
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz', U64 = 18446744073709551615n;
const fail = code => { throw code; }, requireValue = (value, code = 'INPUT_INVALID') => { if (!value) fail(code); };
function decode(value, maxChars = 512, maxBytes = 384) {
  requireValue(typeof value === 'string' && value.length > 0 && value.length <= maxChars);
  let n = 0n; for (const c of value) { const digit = alphabet.indexOf(c); requireValue(digit >= 0); n = n * 58n + BigInt(digit); }
  let hex = n.toString(16); if (hex.length % 2) hex = '0' + hex;
  const body = n ? Buffer.from(hex, 'hex') : Buffer.alloc(0); let zeros = 0; while (value[zeros] === '1') zeros++;
  requireValue(zeros + body.length <= maxBytes); return Buffer.concat([Buffer.alloc(zeros), body]);
}
function address(value) { requireValue(decode(value, 44, 32).length === 32); return value; }
function signature(value) { requireValue(decode(value, 88, 64).length === 64); return value; }
function obj(value) { requireValue(value && typeof value === 'object' && !Array.isArray(value)); }
function safeInt(value) { try { return integer(value); } catch { fail('INPUT_INVALID'); } }
function amount(value) { if (value === null || value === undefined) return null;
  requireValue(typeof value === 'string' && /^(0|[1-9][0-9]{0,19})$/.test(value) && BigInt(value) <= U64); return value; }
function pathCompare(a, b) { for (let i = 0; i < Math.min(a.length, b.length); i++) if (a[i] !== b[i]) return a[i] - b[i]; return a.length - b.length; }
const cmp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const identity = (sig, path) => sig === null ? null : { chain: CONFIG.chain, signature: sig, instructionAddress: path };
function transactionReason(err) {
  if (err === null) return null;
  if (err === 'AccountInUse') return 'FAILED_TRANSACTION';
  const tuple = err && typeof err === 'object' && !Array.isArray(err) && Object.keys(err).length === 1 && err.InstructionError;
  const custom = Array.isArray(tuple) && tuple.length === 2 && tuple[1];
  return tuple && Number.isInteger(tuple[0]) && tuple[0] >= 0 && tuple[0] <= 255 && custom && typeof custom === 'object' && !Array.isArray(custom) &&
    Object.keys(custom).length === 1 && Number.isInteger(custom.Custom) && custom.Custom >= 0 && custom.Custom <= 4294967295 ? 'FAILED_TRANSACTION' : 'UNSUPPORTED_DATA';
}
function mapBlock(input, expectedRawHash) {
  try {
    requireValue(Buffer.isBuffer(input) || input instanceof Uint8Array); requireValue(input.byteLength <= CONFIG.limits.input, 'INPUT_LIMIT');
    const bytes = Buffer.from(input), rawHash = digest(bytes); requireValue(expectedRawHash === rawHash, 'HASH_MISMATCH');
    const lines = new TextDecoder('utf-8', { fatal: true }).decode(bytes).trim().split(/\r?\n/); requireValue(lines.length === 1 && lines[0].length > 0);
    const b = parse(Buffer.from(lines[0])); obj(b); obj(b.header);
    const header = { number: safeInt(b.header.number), parentNumber: safeInt(b.header.parentNumber), hash: address(b.header.hash),
      parentHash: address(b.header.parentHash), timestamp: safeInt(b.header.timestamp) };
    requireValue(header.parentNumber < header.number && header.timestamp >= CONFIG.range.from && header.timestamp < CONFIG.range.toExclusive);
    for (const name of ['transactions', 'instructions', 'tokenBalances', 'balances']) {
      if (b[name] === undefined) b[name] = []; requireValue(Array.isArray(b[name])); requireValue(b[name].length <= CONFIG.limits[name], 'INPUT_LIMIT');
      for (const row of b[name]) obj(row);
    }
    const diagnostics = new Map(), txBySignature = new Map(), links = new Map(), instructions = new Map(), tokens = new Map(), transactionReasons = new Map();
    const typedTransactions = JSON.parse(lines[0]).transactions ?? []; // Error integer types only; balance quantity lexemes remain untouched.
    const diag = (code, context = null) => { const d = { code, context }, key = canonical(d); diagnostics.set(key, d);
      requireValue(diagnostics.size <= CONFIG.limits.diagnostics, 'OUTPUT_LIMIT'); };
    const unique = (map, key, row) => { if (map.has(key)) requireValue(canonical(map.get(key)) === canonical(row), 'IMMUTABLE_CONFLICT'); else map.set(key, row); };
    for (const [ordinal, tx] of b.transactions.entries()) {
      const index = safeInt(tx.transactionIndex); requireValue(Array.isArray(tx.signatures) && tx.signatures.length <= 128);
      for (const sig of tx.signatures) signature(sig);
      const sig = tx.signatures[0] ?? null, content = { ...tx }; delete content.transactionIndex;
      if (sig !== null) unique(txBySignature, sig, content);
      else diag('AMBIGUOUS_SIGNATURE');
      if (sig !== null) { const reason = transactionReason(typedTransactions[ordinal].err);
        if (transactionReasons.has(sig)) requireValue(transactionReasons.get(sig) === reason, 'IMMUTABLE_CONFLICT'); transactionReasons.set(sig, reason);
        if (reason) diag(reason, reason === 'UNSUPPORTED_DATA' ? { signature: sig, transactionError: 'UNKNOWN' } : { signature: sig }); }
      if (!links.has(index)) links.set(index, sig); else if (links.get(index) !== sig) { links.set(index, null); diag('AMBIGUOUS_SIGNATURE'); }
    }
    const link = index => links.get(safeInt(index)) ?? null;
    for (const i of b.instructions) {
      address(i.programId); requireValue(Array.isArray(i.instructionAddress) && i.instructionAddress.length >= 1 && i.instructionAddress.length <= 64);
      const path = i.instructionAddress.map(safeInt); requireValue(Array.isArray(i.accounts) && i.accounts.length <= 128);
      i.accounts.forEach(address); requireValue(typeof i.data === 'string' && i.data.length <= 1024);
      requireValue(i.isCommitted === true || i.isCommitted === false || i.isCommitted === null || i.isCommitted === undefined);
      const sig = link(i.transactionIndex), row = { ...i, instructionAddress: path, signature: sig }; delete row.transactionIndex;
      const key = canonical([sig, path]);
      if (sig !== null) unique(instructions, key, row);
      else { diag('AMBIGUOUS_SIGNATURE', { instructionAddress: path }); unique(instructions, canonical([null, safeInt(i.transactionIndex), path]), row); }
    }
    for (const t of b.tokenBalances) {
      const sig = link(t.transactionIndex), row = { signature: sig, account: address(t.account) };
      for (const side of ['pre', 'post']) {
        for (const name of ['Mint', 'Owner']) row[side + name] = t[side + name] === null || t[side + name] === undefined ? null : address(t[side + name]);
        row[side + 'Amount'] = amount(t[side + 'Amount']);
        row[side + 'Decimals'] = t[side + 'Decimals'] === null || t[side + 'Decimals'] === undefined ? null : safeInt(t[side + 'Decimals']);
        requireValue(row[side + 'Decimals'] === null || row[side + 'Decimals'] <= 255);
      }
      unique(tokens, canonical([sig, sig === null ? safeInt(t.transactionIndex) : null, row.account]), row);
      if (sig === null) diag('AMBIGUOUS_SIGNATURE', { account: row.account });
    }
    const ordered = [...instructions.values()].sort((a, z) => cmp(a.signature ?? '', z.signature ?? '') || pathCompare(a.instructionAddress, z.instructionAddress) || cmp(canonical(a), canonical(z)));
    const tokenStates = [...tokens.values()].sort((a, z) => cmp(a.signature ?? '', z.signature ?? '') || cmp(a.account, z.account) || cmp(canonical(a), canonical(z)));
    const state = (sig, account) => tokens.get(canonical([sig, null, account]));
    const stable = t => t && ['Mint', 'Owner', 'Decimals', 'Amount'].every(k => t['pre' + k] !== null && t['post' + k] !== null) &&
      t.preMint === t.postMint && t.preOwner === t.postOwner && t.preDecimals === t.postDecimals;
    const failure = i => i.signature === null ? 'AMBIGUOUS_SIGNATURE' : transactionReasons.get(i.signature) ?? (
      i.error !== null && i.error !== undefined ? 'FAILED_INSTRUCTION' : i.isCommitted !== true ? 'UNCOMMITTED_INSTRUCTION' : null);
    const invocations = [];
    for (const i of ordered.filter(x => x.programId === PUMP)) {
      const id = identity(i.signature, i.instructionAddress), invocation = { identity: id, instructionAddress: i.instructionAddress,
        variant: null, layoutApplicability: 'DECLARED_UNVERIFIED', reason: failure(i), declaredTrader: null, pool: null,
        baseMint: null, quoteMint: null, userBase: null, userQuote: null, poolBaseVault: null, poolQuoteVault: null, vaultChecks: [] };
      let data; try { data = decode(i.data); } catch { invocation.reason ??= 'UNSUPPORTED_DATA'; }
      const variant = data && CONFIG.variants[data.subarray(0, 8).toString('hex')];
      invocation.variant = variant?.name ?? null;
      if (!invocation.reason && !variant) invocation.reason = 'UNSUPPORTED_VARIANT';
      if (!invocation.reason && (data.length !== variant.bytes || i.accounts.length !== variant.accounts || (data.length === 25 && ![0, 1].includes(data[24])))) invocation.reason = 'UNSUPPORTED_LAYOUT';
      if (!invocation.reason && (i.accounts[11] !== TOKEN || i.accounts[12] !== TOKEN)) invocation.reason = 'UNSUPPORTED_TOKEN_PROGRAM';
      if (!invocation.reason && (i.accounts[13] !== SYSTEM || i.accounts[14] !== ASSOCIATED || i.accounts[16] !== PUMP ||
        i.accounts[3] === i.accounts[4] || new Set([i.accounts[5], i.accounts[6], i.accounts[7], i.accounts[8]]).size !== 4)) invocation.reason = 'UNSUPPORTED_LAYOUT';
      if (!invocation.reason) {
        for (const name of ['pool', 'declaredTrader', 'baseMint', 'quoteMint', 'userBase', 'userQuote', 'poolBaseVault', 'poolQuoteVault']) invocation[name] = i.accounts[CONFIG.roles[name]];
        for (const side of ['base', 'quote']) {
          const account = invocation[side === 'base' ? 'poolBaseVault' : 'poolQuoteVault'], t = state(i.signature, account);
          const reason = !t ? 'MISSING_VAULT_STATE' : !stable(t) || t.preOwner !== invocation.pool || t.preMint !== invocation[side + 'Mint'] ? 'VAULT_STATE_MISMATCH' : null;
          invocation.vaultChecks.push({ account, mint: invocation[side + 'Mint'], owner: invocation.pool, reason }); if (reason) diag(reason, id);
        }
      }
      diag('DECLARED_LAYOUT_UNVERIFIED', id); if (invocation.reason) diag(invocation.reason, id ?? { instructionAddress: i.instructionAddress }); invocations.push(invocation);
    }
    const owned = new Map();
    for (const invocation of invocations.filter(x => x.reason === null)) for (const side of ['base', 'quote']) {
      const sig = invocation.identity.signature, user = invocation.declaredTrader, mint = invocation[side + 'Mint'];
      const relevant = tokenStates.filter(t => t.signature === sig), userAccount = invocation[side === 'base' ? 'userBase' : 'userQuote'];
      const declared = state(sig, userAccount), candidates = relevant.filter(t => (t.preOwner === user || t.postOwner === user) && (t.preMint === mint || t.postMint === mint));
      let reason = !declared ? 'MISSING_TOKEN_STATE' : !stable(declared) || declared.preOwner !== user || declared.preMint !== mint ? 'AMBIGUOUS_TOKEN_TRANSITION' : null;
      const decimals = stable(declared) ? declared.preDecimals : candidates.find(stable)?.preDecimals ?? declared?.preDecimals ?? declared?.postDecimals ?? null;
      if (relevant.some(t => t.preOwner === null || t.postOwner === null ||
        ((t.preOwner === user || t.postOwner === user) && (t.preMint === null || t.postMint === null))) ||
        candidates.some(t => !stable(t) || t.preDecimals !== decimals)) reason = 'AMBIGUOUS_TOKEN_TRANSITION';
      let delta = 0n; if (!reason) for (const t of candidates) delta += BigInt(t.postAmount) - BigInt(t.preAmount);
      const key = canonical([sig, user, mint, decimals]), group = { signature: sig, owner: user, mint, decimals,
        scope: CONFIG.ownedDeltaScope, ownedDelta: reason ? null : delta.toString(), reason, accounts: candidates.map(t => t.account).sort(),
        invocationIdentities: [invocation.identity] };
      if (owned.has(key)) { const prior = owned.get(key); prior.invocationIdentities.push(invocation.identity); if (reason) { prior.reason = reason; prior.ownedDelta = null; } }
      else owned.set(key, group); if (reason) diag(reason, { signature: sig, owner: user, mint });
    }
    const transfers = [];
    for (const i of ordered.filter(x => [TOKEN, TOKEN2022, SYSTEM].includes(x.programId))) {
      const id = identity(i.signature, i.instructionAddress), record = { identity: id, instructionAddress: i.instructionAddress, variant: null,
        source: null, destination: null, declaredAuthority: null, amount: null, mint: null, decimals: null, associatedInvocation: null, reason: failure(i) };
      let data; try { data = decode(i.data); } catch { record.reason ??= 'UNSUPPORTED_DATA'; }
      const checked = data?.[0] === 12, supported = data && (data[0] === 3 || checked);
      if (!record.reason && (i.programId !== TOKEN || !supported || data.length !== (checked ? 10 : 9) || i.accounts.length !== (checked ? 4 : 3))) record.reason = 'UNSUPPORTED_TRANSFER';
      if (!record.reason) {
        record.variant = checked ? 'transferChecked' : 'transfer'; record.source = i.accounts[0]; record.destination = i.accounts[checked ? 2 : 1];
        record.declaredAuthority = i.accounts[checked ? 3 : 2]; record.amount = data.readBigUInt64LE(1).toString();
        if (checked) { record.mint = i.accounts[1]; record.decimals = data[9]; }
        else { const a = state(i.signature, record.source), z = state(i.signature, record.destination);
          if (stable(a) && stable(z) && a.preMint === z.preMint && a.preDecimals === z.preDecimals) { record.mint = a.preMint; record.decimals = a.preDecimals; }
          else record.reason = !a || !z ? 'MISSING_TOKEN_STATE' : 'AMBIGUOUS_TOKEN_TRANSITION'; }
        const ancestors = invocations.filter(v => v.identity?.signature === i.signature && v.instructionAddress.length < i.instructionAddress.length &&
          v.instructionAddress.every((x, n) => x === i.instructionAddress[n]));
        if (ancestors.length === 1) record.associatedInvocation = ancestors[0].identity;
        else if (ancestors.length > 1) { diag('AMBIGUOUS_TRANSFER_ASSOCIATION', id); record.reason ??= 'AMBIGUOUS_TRANSFER_ASSOCIATION'; }
      }
      if (record.reason) diag(record.reason, id ?? { instructionAddress: i.instructionAddress }); transfers.push(record);
    }
    const ownedDeltas = [...owned.values()].sort((a, z) => cmp(a.signature, z.signature) || cmp(a.mint, z.mint) || cmp(a.owner, z.owner) || cmp(a.decimals, z.decimals));
    for (const group of ownedDeltas) group.invocationIdentities.sort((a, z) => pathCompare(a.instructionAddress, z.instructionAddress));
    const diagnosticList = [...diagnostics.values()].sort((a, z) => cmp(a.code, z.code) || cmp(canonical(a.context), canonical(z.context)));
    const facts = { header, invocations, tokenStates, ownedDeltas, transfers, diagnostics: diagnosticList, nativeBalances: CONFIG.nativeBalances };
    const factsHash = fingerprint(CONFIG.factsVersion, { configHash: fingerprint(CONFIG.version, CONFIG), ...facts });
    const report = { ...FLAGS, version: CONFIG.reportVersion, configuration: structuredClone(CONFIG), rawHash, factsHash,
      status: diagnosticList.some(d => d.code !== 'DECLARED_LAYOUT_UNVERIFIED') ? 'OFFLINE_MAPPING_PARTIAL' : 'OFFLINE_MAPPING_COMPLETE', ...facts };
    report.reportHash = fingerprint(CONFIG.reportVersion, report); requireValue(Buffer.byteLength(JSON.stringify(report)) <= CONFIG.limits.output, 'OUTPUT_LIMIT'); return report;
  } catch (code) { return { ...FLAGS, status: 'MAPPING_INVALID', code: ['HASH_MISMATCH', 'INPUT_LIMIT', 'OUTPUT_LIMIT', 'IMMUTABLE_CONFLICT'].includes(code) ? code : 'INPUT_INVALID' }; }
}
module.exports = { mapBlock };
