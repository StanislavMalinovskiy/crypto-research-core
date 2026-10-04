'use strict';
const { createHash } = require('node:crypto');

// Pure boundary: { mapping: immutable V2 facts, raw: losslessly parsed Helius
// transaction, policy: cohort/quote/venue + pinned fee/tip policy, lineage }.
// The caller verifies raw bytes and documentary evidence before this boundary.
// Hashes here bind those supplied inputs; they do not attest historical truth.
const VERSION = 'e2-trade-reducer-v1';
const CHAIN = 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp';
const PUMP = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
const TOKEN = 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA';
const TOKEN2022 = 'TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb';
const SYSTEM = '11111111111111111111111111111111';
const WSOL = 'So11111111111111111111111111111111111111112';
const USDC = 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v';
const USDT = 'Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB';
const CU = 'ComputeBudget111111111111111111111111111111';
const PRECOMPILES = new Set(['Ed25519SigVerify111111111111111111111111111',
  'KeccakSecp256k11111111111111111111111111111', 'Secp256r1SigVerify1111111111111111111111111']);
const FEE_DOCUMENTS = ['https://solana.com/docs/core/fees/fee-structure',
  'https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/program-runtime/src/prioritization_fee.rs',
  'https://raw.githubusercontent.com/solana-labs/solana/v1.18.26/sdk/src/fee.rs'];
const LIMITS = Object.freeze({ mappingBytes: 2000000, rawBytes: 2000000, combinedBytes: 4000000,
  outputBytes: 2000000, rows: 10000, accounts: 128, path: 64, dataCharacters: 1024,
  policyBytes: 100000, lineageBytes: 100000, depth: 96 });
const U64 = 18446744073709551615n;
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const cmp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const hash = bytes => 'sha256:' + createHash('sha256').update(bytes).digest('hex');
const isHash = x => typeof x === 'string' && /^sha256:[a-f0-9]{64}$/.test(x);
const fail = (status, reason) => { throw { status, reason }; };
const need = (value, status = 'MISSING_LEG', reason = 'INPUT_INVALID') => { if (!value) fail(status, reason); };
function canonical(value, depth = 0) {
  need(depth <= LIMITS.depth, 'MISSING_LEG', 'INPUT_LIMIT');
  if (value === null || typeof value === 'boolean' || typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'number' && Number.isSafeInteger(value)) return JSON.stringify(value);
  if (Array.isArray(value)) return '[' + value.map(x => canonical(x, depth + 1)).join(',') + ']';
  need(value && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype);
  return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k], depth + 1)).join(',') + '}';
}
const fingerprint = value => hash(canonical(value));
function unsigned(value) {
  need(typeof value === 'string' && /^(0|[1-9][0-9]{0,19})$/.test(value), 'MISSING_LEG', 'RAW_AMOUNT_INVALID');
  const n = BigInt(value); need(n <= U64, 'MISSING_LEG', 'RAW_AMOUNT_INVALID'); return n;
}
function integer(value, max = Number.MAX_SAFE_INTEGER) {
  need((typeof value === 'string' && /^(0|[1-9][0-9]{0,15})$/.test(value)) || Number.isSafeInteger(value));
  const n = Number(value); need(Number.isSafeInteger(n) && n >= 0 && n <= max); return n;
}
function decode(value) {
  need(typeof value === 'string' && value.length > 0 && value.length <= LIMITS.dataCharacters);
  let n = 0n;
  for (const c of value) { const d = alphabet.indexOf(c); need(d >= 0); n = n * 58n + BigInt(d); }
  let hex = n.toString(16); if (hex.length % 2) hex = '0' + hex;
  const body = n ? Buffer.from(hex, 'hex') : Buffer.alloc(0);
  let zeros = 0; while (value[zeros] === '1') zeros++;
  need(zeros + body.length <= 768); return Buffer.concat([Buffer.alloc(zeros), body]);
}
function encode(bytes) {
  let n = BigInt('0x' + bytes.toString('hex')), out = '';
  while (n) { out = alphabet[Number(n % 58n)] + out; n /= 58n; }
  for (const byte of bytes) { if (byte) break; out = '1' + out; }
  return out;
}
const pathCmp = (a, b) => { for (let i = 0; i < Math.min(a.length, b.length); i++) if (a[i] !== b[i]) return a[i] - b[i]; return a.length - b.length; };
const pathKey = path => path.join('.');
const same = (a, b) => canonical(a) === canonical(b);
function mapperCanonical(value) {
  const atom = (tag, v) => { const text = String(v); return `${tag}${Buffer.byteLength(text)}:${text}`; };
  if (value === null) return 'z0:';
  if (typeof value === 'string') return atom('s', value);
  if (typeof value === 'boolean') return atom('b', value ? '1' : '0');
  if (typeof value === 'number' && Number.isSafeInteger(value)) return atom('n', value);
  if (Array.isArray(value)) return atom('a', value.map(mapperCanonical).join(''));
  return atom('o', Object.keys(value).sort().map(k => atom('k', k) + mapperCanonical(value[k])).join(''));
}
const sortedUnique = list => [...new Map(list.map(x => [canonical(x), x])).values()].sort((a, b) => cmp(canonical(a), canonical(b)));
function unique(list, key) {
  const map = new Map();
  for (const row of list) { const id = key(row); if (map.has(id)) need(same(map.get(id), row), 'AMBIGUOUS_ROUTE', 'IMMUTABLE_CONFLICT'); else map.set(id, row); }
  return [...map.values()];
}
function stable(t) {
  return t && ['Mint', 'Owner', 'Decimals', 'Amount'].every(k => t['pre' + k] !== null && t['pre' + k] !== undefined && t['post' + k] !== null && t['post' + k] !== undefined) &&
    t.preMint === t.postMint && t.preOwner === t.postOwner && t.preDecimals === t.postDecimals;
}
function net(t) { unsigned(t.preAmount); unsigned(t.postAmount); return BigInt(t.postAmount) - BigInt(t.preAmount); }
// Locate bounded raw instructions solely for corroboration of existing mapper
// paths/transfer records and account lifetime. This emits no new mapper facts.
function rawContext(raw) {
  const msg = raw.transaction?.message, meta = raw.meta;
  need(msg && meta && Array.isArray(msg.accountKeys) && Array.isArray(msg.instructions));
  need(msg.accountKeys.length <= LIMITS.accounts && msg.instructions.length <= LIMITS.rows, 'MISSING_LEG', 'INPUT_LIMIT');
  const loaded = meta.loadedAddresses ?? { writable: [], readonly: [] };
  need(Array.isArray(loaded.writable) && Array.isArray(loaded.readonly));
  const keys = [...msg.accountKeys.map(k => typeof k === 'string' ? k : k.pubkey), ...loaded.writable, ...loaded.readonly];
  need(keys.length <= LIMITS.accounts && keys.every(k => typeof k === 'string' && decode(k).length === 32));
  const key = value => { const index = integer(value, keys.length - 1); return keys[index]; };
  const records = [], byPath = new Map();
  const add = (i, path) => {
    need(Array.isArray(i.accounts) && i.accounts.length <= LIMITS.accounts);
    let data = null;
    try { data = decode(i.data); } catch { /* Preserve malformed ancillary evidence as unknown. */ }
    const record = { program: key(i.programIdIndex), accounts: i.accounts.map(key), data, path };
    records.push(record); byPath.set(pathKey(path), record);
  };
  msg.instructions.forEach((i, n) => add(i, [n]));
  let pathKnown = Array.isArray(meta.innerInstructions);
  const groups = new Set(); let count = msg.instructions.length;
  for (const g of meta.innerInstructions ?? []) {
    const outer = integer(g.index, msg.instructions.length - 1); need(!groups.has(outer), 'AMBIGUOUS_ROUTE', 'DUPLICATE_INNER_GROUP'); groups.add(outer);
    need(Array.isArray(g.instructions)); count += g.instructions.length; need(count <= LIMITS.rows, 'MISSING_LEG', 'INPUT_LIMIT');
    const stack = [[outer]], children = new Map();
    for (const i of g.instructions) {
      let height;
      try { height = integer(i.stackHeight, LIMITS.path); } catch { pathKnown = false; break; }
      if (height < 2 || height > stack.length + 1) { pathKnown = false; break; }
      stack.length = height - 1; const parent = stack.at(-1), id = pathKey(parent), child = children.get(id) ?? 0; children.set(id, child + 1);
      const path = [...parent, child]; stack.push(path); add(i, path);
    }
  }
  records.sort((a, b) => pathCmp(a.path, b.path));
  return { keys, staticKeys: msg.accountKeys.map(k => typeof k === 'string' ? k : k.pubkey), msg, meta, records, byPath, pathKnown };
}
function feeProof(raw, ctx, policy, lineage, trader) {
  const fees = { total: 'UNKNOWN', base: 'UNKNOWN', priority: 'UNKNOWN', payer: ctx.keys[0] ?? 'UNKNOWN',
    traderCost: 'UNKNOWN', splitStatus: 'UNKNOWN', attributionStatus: 'UNKNOWN', reason: 'FEE_REGIME_UNPROVED' };
  if (fees.payer === trader) fees.attributionStatus = 'ACTUAL_PAYER_IS_TRADER';
  try { fees.total = unsigned(raw.meta.fee).toString(); } catch { fees.reason = 'FEE_TOTAL_MISSING_OR_INVALID'; return fees; }
  if (fees.payer === trader) { fees.traderCost = fees.total; fees.attributionStatus = 'ACTUAL_PAYER_IS_TRADER'; }
  try {
    fees.reason = 'FEE_MESSAGE_FORMAT_UNPROVED';
    need(['legacy', '0'].includes(String(raw.version)));
    need(Array.isArray(raw.transaction.signatures));
    const signatures = integer(ctx.msg.header?.numRequiredSignatures, ctx.staticKeys.length);
    need(signatures > 0 && signatures === raw.transaction.signatures.length && new Set(raw.transaction.signatures).size === signatures);
    need(integer(ctx.msg.header.numReadonlySignedAccounts, signatures) < signatures);
    integer(ctx.msg.header.numReadonlyUnsignedAccounts, ctx.staticKeys.length - signatures);
    need(raw.transaction.signatures.every(s => decode(s).length === 64));
    need(!ctx.records.some(r => PRECOMPILES.has(r.program)));
    // Pinned documentation proves the formula only. A separate immutable
    // recorded historical bank-context receipt must bind this transaction.
    fees.reason = 'FEE_HISTORICAL_CONTEXT_UNPROVED';
    const regime = policy.feeRegime, evidence = lineage.feeRegimeEvidence, c = evidence?.context;
    need(regime?.version === 'solana-legacy-v0-explicit-cu-v1' && Array.isArray(regime.documents));
    for (const url of new Set(regime.documents.map(s => s.url))) {
      if (new Set(regime.documents.filter(s => s.url === url).map(s => s.contentHash)).size > 1) {
        fees.reason = 'FEE_POLICY_CONFLICT'; return fees;
      }
    }
    need(FEE_DOCUMENTS.every(url => regime.documents.some(s => s.url === url && isHash(s.contentHash))));
    need(c && evidence.contextHash === fingerprint(c) && c.chain === CHAIN && c.slot === String(raw.slot) && c.transactionRawHash === lineage.rawHash);
    need(c.transactionVersion === String(raw.version) && typeof c.runtimeVersion === 'string' && c.runtimeVersion.length > 0);
    need(typeof c.source === 'string' && c.source.length > 0 && isHash(c.sourceHash));
    need(c.lamportsPerSignature === '5000' && c.priorityRule === 'CEIL_PRICE_TIMES_REQUESTED_LIMIT' && c.limitRule === 'EXPLICIT_UNCLAMPED');
    const max = unsigned(c.maxComputeUnitLimit); need(max > 0n && max <= 1400000n);
    fees.reason = 'FEE_COMPUTE_BUDGET_UNPROVED';
    const compute = ctx.records.filter(r => r.program === CU);
    // Compute-budget instructions must be actual top-level instructions with
    // exact recognized layouts; unknown/deprecated/default behavior is unproved.
    need(compute.every(r => r.path.length === 1 && r.accounts.length === 0 && r.data !== null));
    need(new Set(compute.map(r => r.data[0])).size === compute.length);
    const prices = compute.filter(r => r.data[0] === 3), limits = compute.filter(r => r.data[0] === 2);
    need(prices.length === 1 && limits.length === 1 && prices[0].data.length === 9 && limits[0].data.length === 5);
    need(compute.every(r => [2, 3].includes(r.data[0]) || (r.data[0] === 1 && r.data.length === 5) || (r.data[0] === 4 && r.data.length === 5)));
    const price = prices[0].data.readBigUInt64LE(1), limit = BigInt(limits[0].data.readUInt32LE(1));
    need(limit > 0n && limit <= max);
    const priority = (price * limit + 999999n) / 1000000n, base = 5000n * BigInt(signatures);
    fees.reason = 'FEE_EQUATION_MISMATCH';
    need(priority <= U64 && base + priority <= U64 && base + priority === BigInt(fees.total));
    fees.base = base.toString(); fees.priority = priority.toString(); fees.splitStatus = 'PROVEN'; fees.reason = null;
    fees.proof = { regime: regime.version, contextHash: evidence.contextHash, documentPins: sortedUnique(regime.documents),
      requiredSignatures: signatures, priceMicroLamports: price.toString(), requestedLimit: limit.toString(), rounding: 'CEILING',
      instructionPaths: [limits[0].path, prices[0].path].sort(pathCmp) };
  } catch { return fees; }
  if (fees.payer === trader) { fees.traderCost = fees.total; fees.attributionStatus = 'ACTUAL_PAYER_IS_TRADER'; }
  else fees.reason = 'SPONSOR_ATTRIBUTION_UNPROVED';
  return fees;
}
function temporaryOwner(account, mint, trader, before, ctx) {
  const inits = ctx.records.filter(r => r.program === TOKEN && r.data && r.accounts[0] === account &&
    ((r.data[0] === 18 && r.data.length === 33 && r.accounts.length === 2) || (r.data[0] === 1 && r.data.length === 1 && r.accounts.length === 4)));
  need(inits.length === 1, 'MISSING_LEG', 'TEMPORARY_OWNER_UNPROVED');
  const init = inits[0], owner = init.data[0] === 18 ? encode(init.data.subarray(1)) : init.accounts[2];
  need(init.accounts[1] === mint && owner === trader && pathCmp(init.path, before) < 0, 'MISSING_LEG', 'TEMPORARY_OWNER_UNPROVED');
  const closes = ctx.records.filter(r => r.program === TOKEN && r.data && r.accounts[0] === account && r.data[0] === 9);
  need(closes.length === 1 && closes[0].data.length === 1 && closes[0].accounts.length === 3 &&
    closes[0].accounts[2] === trader && pathCmp(closes[0].path, before) > 0,
    'MISSING_LEG', 'TEMPORARY_LIFETIME_UNPROVED');
  return { init: init.path, close: closes[0].path };
}
function temporaryQuote(invocations, transfers, states, ctx, trader, quote, policy) {
  need(quote === WSOL, 'MISSING_LEG', 'TEMPORARY_QUOTE_UNSUPPORTED');
  let delta = 0n; const seen = new Set(), accounts = new Set(), feeAmounts = new Map(), vaultAmounts = new Map();
  const quoteLegs = invocations.filter(v => v.quoteMint === quote);
  need(quoteLegs.length > 0, 'MISSING_LEG', 'TEMPORARY_QUOTE_ROUTE_UNSUPPORTED');
  for (const v of quoteLegs) {
    const account = v.userQuote, vault = states.find(t => t.account === v.poolQuoteVault);
    need(stable(vault) && vault.preMint === quote && vault.preOwner === v.pool && vault.preDecimals === 9, 'MISSING_LEG', 'QUOTE_VAULT_UNPROVED');
    const scoped = transfers.filter(t => same(t.associatedInvocation, v.identity));
    need(scoped.length > 0, 'MISSING_LEG', 'TEMPORARY_TRANSFER_MISSING');
    let vaultFlow = 0n, userFlow = 0n, feeFlow = 0n;
    const lifetime = temporaryOwner(account, quote, trader, v.instructionAddress, ctx);
    for (const t of scoped) {
      const r = ctx.byPath.get(pathKey(t.instructionAddress));
      need(r && r.program === TOKEN && t.identity?.signature === v.identity.signature, 'MISSING_LEG', 'TRANSFER_RAW_LINK_MISSING');
      const checked = r.data?.[0] === 12;
      need(r.data && (r.data[0] === 3 || checked) && r.data.length === (checked ? 10 : 9) && r.accounts.length === (checked ? 4 : 3), 'MISSING_LEG', 'TRANSFER_LAYOUT_UNSUPPORTED');
      need(!t.reason || t.reason === 'MISSING_TOKEN_STATE', 'MISSING_LEG', 'TRANSFER_UNPROVED');
      const source = r.accounts[0], destination = r.accounts[checked ? 2 : 1], authority = r.accounts[checked ? 3 : 2];
      const amount = r.data.readBigUInt64LE(1);
      need(source === t.source && destination === t.destination && authority === t.declaredAuthority && amount.toString() === t.amount,
        'AMBIGUOUS_ROUTE', 'TRANSFER_RAW_CONFLICT');
      // Only transfers touching the quote vault/user or declared fee account
      // belong to this narrow quote reconciliation; base transfers stay intact.
      const relevant = [source, destination].some(a => a === account || a === v.poolQuoteVault);
      if (!relevant) continue;
      need(t.mint === null || t.mint === quote, 'MISSING_LEG', 'TRANSFER_MINT_UNPROVED');
      if (checked) need(r.accounts[1] === quote && r.data[9] === 9, 'MISSING_LEG', 'TRANSFER_MINT_UNPROVED');
      need(pathCmp(r.path, lifetime.init) > 0 && pathCmp(r.path, lifetime.close) < 0, 'MISSING_LEG', 'TEMPORARY_LIFETIME_UNPROVED');
      const id = pathKey(r.path); need(!seen.has(id), 'AMBIGUOUS_ROUTE', 'DUPLICATE_TRANSFER'); seen.add(id);
      if (source === account) { need(authority === trader, 'MISSING_LEG', 'TEMPORARY_AUTHORITY_UNPROVED'); userFlow -= amount; }
      else if (source === v.poolQuoteVault) need(authority === v.pool, 'MISSING_LEG', 'QUOTE_VAULT_AUTHORITY_UNPROVED');
      else fail('MISSING_LEG', 'QUOTE_TRANSFER_SOURCE_UNPROVED');
      if (destination === account) { userFlow += amount; accounts.add(account); }
      else if (destination !== v.poolQuoteVault) {
        const fees = policy.protocolFeeAccounts?.filter(f => f.pool === v.pool && f.account === destination && f.mint === quote && ['PROTOCOL', 'CREATOR'].includes(f.kind)) ?? [];
        const target = states.find(t => t.account === destination);
        // Declared IDL role10 is the protocol token recipient; role17 is the
        // creator quote vault. Check supplied identity against actual accounts,
        // not a name/flag assigned to an unrelated transfer destination.
        need(fees.length === 1 && stable(target) && target.preMint === quote && target.preDecimals === 9, 'MISSING_LEG', 'QUOTE_FEE_TRANSFER_UNPROVED');
        const swap = ctx.byPath.get(pathKey(v.instructionAddress)), expectedRole = fees[0].kind === 'PROTOCOL' ? 10 : 17;
        need(fees[0].roleIndex === expectedRole && swap.accounts[expectedRole] === destination, 'MISSING_LEG', 'QUOTE_FEE_IDENTITY_UNPROVED');
        need(target.preOwner === swap.accounts[fees[0].kind === 'PROTOCOL' ? 9 : 18], 'MISSING_LEG', 'QUOTE_FEE_OWNER_UNPROVED');
        feeFlow += amount;
        feeAmounts.set(destination, (feeAmounts.get(destination) ?? 0n) + amount);
      }
      if (destination === v.poolQuoteVault) vaultFlow += amount;
      if (source === v.poolQuoteVault) vaultFlow -= amount;
      accounts.add(account);
    }
    need(userFlow + vaultFlow + feeFlow === 0n,
      'MISSING_LEG', 'QUOTE_RECONCILIATION_FAILED');
    vaultAmounts.set(v.poolQuoteVault, (vaultAmounts.get(v.poolQuoteVault) ?? 0n) + vaultFlow);
    delta += userFlow;
  }
  for (const [account, amount] of vaultAmounts) need(net(states.find(t => t.account === account)) === amount, 'MISSING_LEG', 'QUOTE_RECONCILIATION_FAILED');
  // The temporary account must have no other SPL movements outside the supplied
  // swap legs. Funding/closing SOL effects are deliberately not quote volume.
  for (const r of ctx.records.filter(r => r.program === TOKEN && r.data && [3, 12].includes(r.data[0]))) {
    const dest = r.accounts[r.data[0] === 12 ? 2 : 1];
    if (accounts.has(r.accounts[0]) || accounts.has(dest)) need(seen.has(pathKey(r.path)), 'MISSING_LEG', 'UNSCOPED_TEMPORARY_TRANSFER');
  }
  for (const [account, amount] of feeAmounts) need(net(states.find(t => t.account === account)) === amount, 'MISSING_LEG', 'QUOTE_FEE_RECONCILIATION_FAILED');
  return { mint: quote, decimals: 9, rawDelta: delta.toString(), accounts: [...accounts].sort(), proof: 'SCOPED_TEMPORARY_SPL' };
}
function tips(ctx, policy, trader, raw) {
  const unknown = reason => ({ tipsObservable: false, rawAmount: 'UNKNOWN', reason });
  const snapshot = policy.tipIdentitySnapshot;
  if (!snapshot) return unknown('TIP_IDENTITY_HISTORY_UNPROVED');
  // Even a pinned address list cannot prove coverage/ownership/history alone.
  if (!isHash(snapshot.contentHash) || typeof snapshot.source !== 'string' || snapshot.chain !== CHAIN || !Array.isArray(snapshot.accounts) ||
      snapshot.fromSlot === undefined || snapshot.toSlotExclusive === undefined) return unknown('TIP_IDENTITY_HISTORY_UNPROVED');
  try {
    if (!(integer(snapshot.fromSlot) <= integer(raw.slot) && integer(raw.slot) < integer(snapshot.toSlotExclusive))) return unknown('TIP_IDENTITY_HISTORY_UNPROVED');
  } catch { return unknown('TIP_IDENTITY_HISTORY_UNPROVED'); }
  if (!snapshot.accounts.length || !snapshot.accounts.every(a => a && typeof a === 'object' &&
      typeof a.address === 'string' && typeof a.owner === 'string' && a.observedAtSlot === String(raw.slot) && isHash(a.sourceHash))) {
    return unknown('TIP_ACCOUNT_OWNERSHIP_UNPROVED');
  }
  // A complete native movement proof is not supplied by the V2 mapper. No zero
  // is inferred from a snapshot plus the absence of a guessed address match.
  return unknown('TIP_TRANSFER_COVERAGE_UNPROVED');
}
// Reconcile existing scoped SPL facts, not a new mapping algorithm. Every
// stable owned/vault/declared fee account must match its summed actual flows;
// intermediate legs share account totals and never add economic volume.
function reconcileScopedFlows(invocations, transfers, states, ctx, trader, quote, policy) {
  const stateByAccount = new Map(states.map(t => [t.account, t]));
  const flows = new Map(), required = new Set(states.filter(t => stable(t) && t.preOwner === trader).map(t => t.account));
  const add = (account, amount) => flows.set(account, (flows.get(account) ?? 0n) + amount);
  const token = (account, mint, owner, decimals) => {
    const t = stateByAccount.get(account);
    if (quote.proof === 'SCOPED_TEMPORARY_SPL' && quote.accounts.includes(account)) {
      need(mint === quote.mint && owner === trader && decimals === quote.decimals, 'MISSING_LEG', 'SCOPED_ACCOUNT_UNPROVED');
      return;
    }
    need(stable(t) && t.preMint === mint && t.preOwner === owner && t.preDecimals === decimals,
      'MISSING_LEG', 'SCOPED_ACCOUNT_UNPROVED'); required.add(account);
  };
  for (const v of invocations) {
    const baseState = stateByAccount.get(v.userBase), quoteVault = stateByAccount.get(v.poolQuoteVault);
    need(stable(baseState) && stable(quoteVault), 'MISSING_LEG', 'SCOPED_ACCOUNT_UNPROVED');
    const baseDecimals = baseState.preDecimals, quoteDecimals = quoteVault.preDecimals;
    token(v.userBase, v.baseMint, trader, baseDecimals); token(v.poolBaseVault, v.baseMint, v.pool, baseDecimals);
    token(v.userQuote, v.quoteMint, trader, quoteDecimals); token(v.poolQuoteVault, v.quoteMint, v.pool, quoteDecimals);
    const swap = ctx.byPath.get(pathKey(v.instructionAddress)), feeAccounts = new Set();
    const recipientClaims = policy.protocolFeeAccounts?.filter(f => f.pool === v.pool) ?? [];
    // Validate every supplied claim for this actual pool before selecting a
    // recipient; contradictory kinds/mints/accounts cannot vanish in a filter.
    for (const claim of recipientClaims) {
      need([10, 17].includes(claim.roleIndex) && claim.account === swap.accounts[claim.roleIndex] &&
        claim.kind === (claim.roleIndex === 10 ? 'PROTOCOL' : 'CREATOR') && claim.mint === v.quoteMint,
        'MISSING_LEG', 'QUOTE_FEE_IDENTITY_UNPROVED');
    }
    for (const [role, ownerRole, kind] of [[10, 9, 'PROTOCOL'], [17, 18, 'CREATOR']]) {
      const account = swap.accounts[role];
      // Zero fees also need observed stable account facts; absence is UNKNOWN.
      token(account, v.quoteMint, swap.accounts[ownerRole], quoteDecimals);
      feeAccounts.add(account);
      const claims = recipientClaims.filter(f => f.account === account);
      if (claims.length) need(claims.length === 1 && claims[0].roleIndex === role && claims[0].kind === kind && claims[0].mint === v.quoteMint,
        'MISSING_LEG', 'QUOTE_FEE_IDENTITY_UNPROVED');
    }
    let baseFlow = 0n, quoteFlow = 0n;
    for (const t of transfers.filter(t => same(t.associatedInvocation, v.identity))) {
      const r = ctx.byPath.get(pathKey(t.instructionAddress));
      need(r.program === TOKEN && r.data && [3, 12].includes(r.data[0]), 'MISSING_LEG', 'SCOPED_TRANSFER_UNPROVED');
      const amount = unsigned(t.amount), source = t.source, destination = t.destination;
      const sourceState = stateByAccount.get(source), destinationState = stateByAccount.get(destination);
      const basePair = (source === v.userBase && destination === v.poolBaseVault) || (source === v.poolBaseVault && destination === v.userBase);
      const quotePair = (source === v.userQuote && (destination === v.poolQuoteVault || feeAccounts.has(destination))) ||
        (source === v.poolQuoteVault && (destination === v.userQuote || feeAccounts.has(destination)));
      let mint, decimals, authority;
      if (basePair) { mint = v.baseMint; decimals = baseDecimals; authority = source === v.userBase ? trader : v.pool; baseFlow += source === v.userBase ? -amount : amount; }
      else if (quotePair) {
        mint = v.quoteMint; decimals = quoteDecimals; authority = source === v.userQuote ? trader : v.pool;
        if (source === v.userQuote) quoteFlow -= amount;
        if (destination === v.userQuote) quoteFlow += amount;
        if (feeAccounts.has(destination)) {
          const claims = recipientClaims.filter(f => f.account === destination);
          need(claims.length === 1, 'MISSING_LEG', 'QUOTE_FEE_TRANSFER_UNPROVED');
        }
      } else {
        // Exact internal owned-account redistribution is zero economic volume.
        need(stable(sourceState) && stable(destinationState) && sourceState.preOwner === trader && destinationState.preOwner === trader &&
          sourceState.preMint === destinationState.preMint && sourceState.preDecimals === destinationState.preDecimals,
          'MISSING_LEG', 'SCOPED_TRANSFER_ENDPOINT_UNPROVED');
        mint = sourceState.preMint; decimals = sourceState.preDecimals; authority = trader;
      }
      need(t.declaredAuthority === authority, 'MISSING_LEG', 'SCOPED_TRANSFER_AUTHORITY_UNPROVED');
      need((t.mint === mint && t.decimals === decimals) || (t.reason === 'MISSING_TOKEN_STATE' && quote.proof === 'SCOPED_TEMPORARY_SPL' &&
        (quote.accounts.includes(source) || quote.accounts.includes(destination)) && mint === quote.mint),
        'MISSING_LEG', 'SCOPED_TRANSFER_MINT_UNPROVED');
      if (r.data[0] === 12) need(r.accounts[1] === mint && r.data[9] === decimals, 'MISSING_LEG', 'SCOPED_TRANSFER_MINT_UNPROVED');
      add(source, -amount); add(destination, amount);
    }
    need(baseFlow !== 0n && quoteFlow !== 0n && (baseFlow > 0n) !== (quoteFlow > 0n), 'MISSING_LEG', 'SCOPED_LEG_RECONCILIATION_FAILED');
  }
  for (const account of required) need(net(stateByAccount.get(account)) === (flows.get(account) ?? 0n), 'MISSING_LEG', 'SCOPED_FLOW_RECONCILIATION_FAILED');
  if (quote.proof === 'SCOPED_TEMPORARY_SPL') {
    const temporaryFlow = quote.accounts.reduce((n, account) => n + (flows.get(account) ?? 0n), 0n);
    need(temporaryFlow.toString() === quote.rawDelta, 'MISSING_LEG', 'SCOPED_FLOW_RECONCILIATION_FAILED');
  }
}
function normalizedMapping(mapping) {
  const result = structuredClone(mapping);
  for (const k of ['invocations', 'tokenStates', 'ownedDeltas', 'transfers', 'diagnostics']) {
    if (Array.isArray(result[k])) {
      result[k] = result[k].map(r => {
        if (!r || typeof r !== 'object') return r;
        const row = { ...r };
        for (const name of ['accounts', 'invocationIdentities', 'vaultChecks']) if (Array.isArray(row[name])) row[name] = sortedUnique(row[name]);
        return row;
      });
      result[k] = sortedUnique(result[k]);
    }
  }
  return result;
}
function reduceTransaction(input) {
  let result = { version: VERSION, classification: 'EXPLORATORY', cohortAdmitted: false, d1Passed: false,
    status: 'MISSING_LEG', reason: 'INPUT_INVALID', actions: [], reconstructedCount: 0 };
  try {
    const { mapping: m, raw, policy, lineage } = input;
    const mBytes = Buffer.byteLength(canonical(m)), rawBytes = Buffer.byteLength(canonical(raw));
    need(mBytes <= LIMITS.mappingBytes && rawBytes <= LIMITS.rawBytes && mBytes + rawBytes <= LIMITS.combinedBytes, 'MISSING_LEG', 'INPUT_LIMIT');
    need(Buffer.byteLength(canonical(policy)) <= LIMITS.policyBytes && Buffer.byteLength(canonical(lineage)) <= LIMITS.lineageBytes, 'MISSING_LEG', 'INPUT_LIMIT');
    result.mapperStatus = m.status; result.mapperDiagnostics = structuredClone(m.diagnostics ?? []).sort((a, b) => cmp(a.code, b.code) || cmp(mapperCanonical(a.context), mapperCanonical(b.context)));
    result.lineage = structuredClone(lineage);
    result.inputHashes = { mapping: fingerprint(normalizedMapping(m)), raw: fingerprint(raw), policy: fingerprint(policy), lineage: fingerprint(lineage), configuration: fingerprint(LIMITS) };
    need(m.status !== 'MAPPING_INVALID', 'MISSING_LEG', m.code ?? 'MAPPER_INVALID');
    need(isHash(m.rawHash) && m.rawHash === lineage.rawHash, 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT');
    need(policy.chain === CHAIN && Array.isArray(policy.cohortMints) && Array.isArray(policy.quoteMints) && Array.isArray(policy.venues));
    const ctx = rawContext(raw), sig = raw.transaction?.signatures?.[0];
    need(typeof sig === 'string' && decode(sig).length === 64);
    need(m.header?.number === integer(raw.slot) && m.header?.timestamp === integer(raw.blockTime), 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT');
    if (m.sourceFacts) {
      need(m.sourceFacts.slot === integer(raw.slot) && m.sourceFacts.blockTime === integer(raw.blockTime) && same(m.sourceFacts.accountKeys, ctx.keys), 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT');
      need(same(m.sourceFacts.err, raw.meta.err) && m.sourceFacts.fee === (raw.meta.fee ?? null), 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT');
    }
    result.source = { signature: sig, slot: integer(raw.slot), blockTime: integer(raw.blockTime),
      transactionIndex: m.sourceFacts?.transactionIndex ?? null, knownAt: lineage.knownAt ?? 'UNKNOWN',
      source: lineage.source ?? 'UNKNOWN', rawHash: lineage.rawHash, rawCrossCheck: lineage.rawCrossCheck ?? 'NOT_CHECKED' };
    need(raw.meta.err === null, 'MISSING_LEG', raw.meta.err === undefined ? 'TRANSACTION_STATE_UNKNOWN' : 'FAILED_TRANSACTION');
    need(ctx.pathKnown && m.pathStatus !== 'PATH_UNKNOWN', 'MISSING_LEG', 'PATH_UNKNOWN');
    for (const name of ['invocations', 'tokenStates', 'ownedDeltas', 'transfers']) need(Array.isArray(m[name]) && m[name].length <= LIMITS.rows, 'MISSING_LEG', 'INPUT_LIMIT');
    const invocations = unique(m.invocations, v => canonical(v.identity)).sort((a, b) => pathCmp(a.instructionAddress, b.instructionAddress));
    need(invocations.length > 0, 'MISSING_LEG', 'NO_SWAP_INVOCATION');
    need(policy.venues.includes(PUMP), 'UNSUPPORTED_VENUE', 'UNDECLARED_VENUE');
    for (const v of invocations) {
      need(v.identity?.signature === sig, 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT');
      if (v.reason) fail(v.reason.startsWith('UNSUPPORTED') ? 'UNSUPPORTED_VENUE' : 'MISSING_LEG', v.reason);
      need(v.instructionAddress.length <= LIMITS.path && v.instructionAddress.every(Number.isSafeInteger));
      const actual = ctx.byPath.get(pathKey(v.instructionAddress));
      need(actual?.program === PUMP && actual.data, 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT');
      for (const [name, index] of Object.entries({ pool: 0, declaredTrader: 1, baseMint: 3, quoteMint: 4, userBase: 5, userQuote: 6, poolBaseVault: 7, poolQuoteVault: 8 })) {
        need(v[name] === actual.accounts[index], 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT');
      }
      need(v.vaultChecks.every(x => x.reason === null), 'MISSING_LEG', 'VAULT_STATE_UNPROVED');
    }
    const states = unique(m.tokenStates, t => canonical([t.signature, t.account])).filter(t => {
      need(t.signature === sig, 'AMBIGUOUS_ROUTE', 'SOURCE_CONFLICT'); return true;
    }).sort((a, b) => cmp(a.account, b.account));
    for (const t of states) { for (const side of ['pre', 'post']) if (t[side + 'Amount'] !== null) unsigned(t[side + 'Amount']); }
    const owners = new Set();
    for (const v of invocations) {
      const t = states.find(t => t.account === v.userBase);
      need(t, 'MISSING_LEG', 'BASE_OWNERSHIP_MISSING');
      need(t.preDecimals !== null && t.postDecimals !== null, 'DECIMALS_UNKNOWN', 'MINT_DECIMALS_UNPROVED');
      need(stable(t) && t.preMint === v.baseMint, 'AMBIGUOUS_ROUTE', 'BASE_OWNERSHIP_AMBIGUOUS');
      owners.add(t.preOwner);
      need(t.preOwner === v.declaredTrader, 'AMBIGUOUS_ROUTE', 'DECLARED_ACTUAL_TRADER_DISAGREEMENT');
    }
    need(owners.size === 1, 'AMBIGUOUS_ROUTE', 'MULTIPLE_TRADERS'); const trader = [...owners][0];
    const groups = new Map();
    for (const t of states.filter(t => t.preOwner === trader || t.postOwner === trader)) {
      need(t.preDecimals !== null && t.postDecimals !== null, 'DECIMALS_UNKNOWN', 'MINT_DECIMALS_UNPROVED');
      need(stable(t), 'AMBIGUOUS_ROUTE', 'OWNED_TOKEN_TRANSITION_AMBIGUOUS');
      need(Number.isInteger(t.preDecimals) && t.preDecimals >= 0 && t.preDecimals <= 255, 'DECIMALS_UNKNOWN', 'MINT_DECIMALS_UNPROVED');
      const g = groups.get(t.preMint) ?? { mint: t.preMint, decimals: t.preDecimals, delta: 0n, accounts: [] };
      need(g.decimals === t.preDecimals, 'DECIMALS_UNKNOWN', 'MINT_DECIMALS_CONFLICT'); g.delta += net(t); g.accounts.push(t.account); groups.set(t.preMint, g);
    }
    // Mapper owned deltas are already transaction aggregates, never per-leg
    // volume. Independently sum actual owned states once and check those facts.
    const owned = unique(m.ownedDeltas, d => canonical([d.signature, d.owner, d.mint, d.decimals]));
    for (const d of owned) {
      need(d.signature === sig && d.owner === trader, 'AMBIGUOUS_ROUTE', 'OWNED_DELTA_TRADER_CONFLICT');
      const g = groups.get(d.mint);
      if (d.reason || d.ownedDelta === null) {
        need(d.reason === 'MISSING_TOKEN_STATE' && d.mint === WSOL && !g, 'MISSING_LEG', d.reason ?? 'OWNED_DELTA_UNPROVED');
      } else need(g && d.decimals === g.decimals && d.ownedDelta === g.delta.toString() && same([...d.accounts].sort(), [...g.accounts].sort()), 'AMBIGUOUS_ROUTE', 'OWNED_DELTA_CONFLICT');
    }
    const nonzero = [...groups.values()].filter(g => g.delta !== 0n);
    const bases = nonzero.filter(g => policy.cohortMints.includes(g.mint));
    need(bases.length <= 1, 'AMBIGUOUS_ROUTE', 'MULTIPLE_COHORT_BASES'); need(bases.length === 1, 'MISSING_LEG', 'BASE_NET_MISSING');
    const base = bases[0], supported = policy.quoteMints.filter(mint => [WSOL, USDC, USDT].includes(mint));
    const quotes = nonzero.filter(g => supported.includes(g.mint));
    need(quotes.length <= 1, 'AMBIGUOUS_ROUTE', 'MULTIPLE_QUOTES');
    const transfers = unique(m.transfers, t => canonical(t.identity)).sort((a, b) => pathCmp(a.instructionAddress, b.instructionAddress));
    const rawTransfers = ctx.records.filter(r => [TOKEN, TOKEN2022, SYSTEM].includes(r.program));
    need(rawTransfers.length === transfers.length, 'MISSING_LEG', 'TRANSFER_RAW_MISSING');
    const suppliedPaths = new Set();
    for (const t of transfers) {
      need(same(t.identity?.instructionAddress, t.instructionAddress) && t.identity?.chain === CHAIN && !suppliedPaths.has(pathKey(t.instructionAddress)),
        'AMBIGUOUS_ROUTE', 'TRANSFER_PATH_CONFLICT');
      suppliedPaths.add(pathKey(t.instructionAddress));
      const ancestors = invocations.filter(v => v.instructionAddress.length < t.instructionAddress.length && v.instructionAddress.every((x, n) => x === t.instructionAddress[n]));
      need(ancestors.length <= 1 && same(t.associatedInvocation, ancestors[0]?.identity ?? null), 'AMBIGUOUS_ROUTE', 'TRANSFER_ASSOCIATION_CONFLICT');
      const r = ctx.byPath.get(pathKey(t.instructionAddress));
      need(t.identity?.signature === sig && r && [TOKEN, TOKEN2022, SYSTEM].includes(r.program), 'AMBIGUOUS_ROUTE', 'TRANSFER_RAW_CONFLICT');
      if (r.program === TOKEN && r.data && [3, 12].includes(r.data[0])) {
        const checked = r.data[0] === 12;
        need(r.data.length === (checked ? 10 : 9) && r.accounts.length === (checked ? 4 : 3), 'MISSING_LEG', 'TRANSFER_LAYOUT_UNSUPPORTED');
        need(t.source === r.accounts[0] && t.destination === r.accounts[checked ? 2 : 1] && t.declaredAuthority === r.accounts[checked ? 3 : 2] &&
          t.amount === r.data.readBigUInt64LE(1).toString(), 'AMBIGUOUS_ROUTE', 'TRANSFER_RAW_CONFLICT');
        if (checked) need(t.mint === r.accounts[1] && t.decimals === r.data[9], 'AMBIGUOUS_ROUTE', 'TRANSFER_RAW_CONFLICT');
      }
    }
    need(rawTransfers.every(r => suppliedPaths.has(pathKey(r.path))), 'MISSING_LEG', 'TRANSFER_RAW_MISSING');
    let quote;
    if (quotes.length) { const q = quotes[0]; quote = { mint: q.mint, decimals: q.decimals, rawDelta: q.delta.toString(), accounts: q.accounts.sort(), proof: 'STABLE_OWNED_SPL_NET' }; }
    else {
      const declared = [...new Set(invocations.flatMap(v => [v.baseMint, v.quoteMint]).filter(mint => supported.includes(mint)))];
      need(declared.length === 1, 'AMBIGUOUS_ROUTE', 'MULTIPLE_QUOTES');
      need(supported.includes(declared[0]), 'UNSUPPORTED_VENUE', 'UNSUPPORTED_QUOTE');
      quote = temporaryQuote(invocations, transfers, states, ctx, trader, declared[0], policy);
    }
    need(!nonzero.some(g => g.mint !== base.mint && g.mint !== quote.mint), 'AMBIGUOUS_ROUTE', 'OTHER_NONZERO_OWNED_MINT');
    need(BigInt(quote.rawDelta) !== 0n && (base.delta > 0n) !== (BigInt(quote.rawDelta) > 0n), 'AMBIGUOUS_ROUTE', 'NONOPPOSITE_QUOTE');
    // Corroborate complete token state facts with matching lossless raw rows.
    // This is a cross-check of immutable mapper output, not a new mapping route.
    for (const side of ['pre', 'post']) {
      const rows = raw.meta[side + 'TokenBalances']; need(Array.isArray(rows) && rows.length <= LIMITS.rows, 'MISSING_LEG', 'TOKEN_STATE_RAW_MISSING');
      const actual = unique(rows, t => String(t.accountIndex));
      for (const t of states) {
        const r = actual.find(x => ctx.keys[integer(x.accountIndex, ctx.keys.length - 1)] === t.account);
        for (const [name, value] of Object.entries({ Mint: r?.mint ?? null, Owner: r?.owner ?? null, Amount: r?.uiTokenAmount?.amount ?? null,
          Decimals: r?.uiTokenAmount?.decimals === undefined ? null : integer(r.uiTokenAmount.decimals, 255) })) {
          need(t[side + name] === value, 'AMBIGUOUS_ROUTE', 'TOKEN_STATE_RAW_CONFLICT');
        }
      }
      need(actual.length === states.filter(t => t[side + 'Mint'] !== null).length, 'AMBIGUOUS_ROUTE', 'TOKEN_STATE_RAW_CONFLICT');
    }
    for (const t of transfers) {
      const scoped = invocations.some(v => v.instructionAddress.length < t.instructionAddress.length && v.instructionAddress.every((x, n) => x === t.instructionAddress[n]));
      if (!scoped && t.amount !== null && BigInt(t.amount) !== 0n) {
        const touched = states.filter(s => s.preOwner === trader && [base.mint, quote.mint].includes(s.preMint)).map(s => s.account);
        need(!touched.includes(t.source) && !touched.includes(t.destination), 'AMBIGUOUS_ROUTE', 'UNSCOPED_OWNED_TRANSFER');
      }
      if (scoped) need(!t.reason || (quote.proof === 'SCOPED_TEMPORARY_SPL' && t.reason === 'MISSING_TOKEN_STATE' &&
        (quote.accounts.includes(t.source) || quote.accounts.includes(t.destination))), 'MISSING_LEG', 'SCOPED_TRANSFER_UNPROVED');
    }
    reconcileScopedFlows(invocations, transfers, states, ctx, trader, quote, policy);
    const fees = feeProof(raw, ctx, policy, lineage, trader);
    const feeStatus = fees.attributionStatus === 'UNKNOWN' || fees.reason === 'FEE_POLICY_CONFLICT' ? 'AMBIGUOUS_ROUTE' : fees.splitStatus !== 'PROVEN' ? 'MISSING_LEG' : 'RECONSTRUCTED';
    const action = { signature: sig, trader, side: base.delta > 0n ? 'BUY' : 'SELL',
      status: feeStatus, reason: feeStatus === 'RECONSTRUCTED' ? null : 'FEES_UNDETERMINED',
      base: { mint: base.mint, decimals: base.decimals, rawDelta: base.delta.toString(), accounts: base.accounts.sort() }, quote,
      ownedAccounts: states.filter(t => stable(t) && t.preOwner === trader).map(t => ({ account: t.account, owner: t.preOwner, mint: t.preMint,
        decimals: t.preDecimals, preAmount: t.preAmount, postAmount: t.postAmount, rawDelta: net(t).toString() })),
      intermediates: [...groups.values()].filter(g => g.delta === 0n && g.mint !== base.mint && g.mint !== quote.mint).map(g => ({ mint: g.mint, decimals: g.decimals, rawDelta: '0', accounts: g.accounts.sort() })).sort((a, b) => cmp(a.mint, b.mint)),
      legs: invocations.map(v => ({ venue: PUMP, pool: v.pool, identity: structuredClone(v.identity), variant: v.variant,
        baseMint: v.baseMint, quoteMint: v.quoteMint, userBase: v.userBase, userQuote: v.userQuote, declaredTrader: v.declaredTrader,
        poolBaseVault: v.poolBaseVault, poolQuoteVault: v.poolQuoteVault, layoutApplicability: v.layoutApplicability })),
      transfers: structuredClone(transfers), fees, tips: tips(ctx, policy, trader, raw), source: structuredClone(result.source) };
    result = { ...result, status: action.status, reason: action.reason, actions: [action], reconstructedCount: action.status === 'RECONSTRUCTED' ? 1 : 0 };
  } catch (error) {
    result.status = error?.status ?? 'MISSING_LEG'; result.reason = error?.reason ?? 'INPUT_INVALID';
    result.actions = []; result.reconstructedCount = 0;
  }
  try {
    result.reductionHash = fingerprint(result);
    if (Buffer.byteLength(canonical(result)) > LIMITS.outputBytes) throw null;
    return result;
  } catch {
    const limited = { version: VERSION, classification: 'EXPLORATORY', cohortAdmitted: false, d1Passed: false,
      status: 'MISSING_LEG', reason: 'OUTPUT_LIMIT', actions: [], reconstructedCount: 0 };
    if (result.inputHashes) limited.inputHashes = result.inputHashes;
    if (result.source) limited.source = result.source;
    limited.reductionHash = fingerprint(limited); return limited;
  }
}
module.exports = { reduceTransaction };
