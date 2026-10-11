'use strict';

// Presentation of a pinned, already computed receipt. No provider or database access.
const fs = require('node:fs');
const crypto = require('node:crypto');
const MAX_BYTES = 16 * 1024 * 1024;
const POOLS = [
  '5iSVs31h7HMdGGmyUvzmHDsYwV7cydD6CZJTbqzCtLii',
  'EBNUexqpB5WjKXJsfm3ruFt7zb7ZbXHZGR7YdhYuvuyy',
  'FDtu6W24K2iyW9Ce9NsTyVSmSGjxj2hHrTkKc2xFTxSL'
];
const STATUSES = ['TRIGGER', 'NO_TRIGGER', 'INSUFFICIENT'];
const REASONS = {
  MISSING_CURRENT_BUCKET: 'нет текущей свечи',
  MISSING_PRIOR_BUCKET: 'нет предыдущей свечи',
  MISSING_PRICE: 'нет подтверждённой цены',
  UNPRICED_OPERATION: 'есть операция без подтверждённой цены',
  MISSING_TRANSACTION_INDEX: 'неизвестен порядок транзакций',
  LATE_OR_EXCLUDED_OPERATION: 'есть поздние или исключённые данные'
};
function requireFact(condition) {
  if (!condition) throw new Error('INVALID_RECEIPT');
}
function count(value) {
  requireFact(Number.isSafeInteger(value) && value >= 0);
  return value;
}
function render(receipt, receiptSha) {
  requireFact(receipt && /^[a-f0-9]{64}$/.test(receipt.sourceSha256));
  const chain = receipt.chainTime;
  requireFact(chain && chain.algorithmVersion === 'pumpswap-chain-time-candles-v1');
  requireFact(chain.interpretation?.readiness === 'DIAGNOSTIC_ONLY_NOT_ADMITTED'
    && chain.interpretation.coverage === 'INCOMPLETE'
    && chain.interpretation.unseenLoss === 'UNKNOWN'
    && chain.interpretation.gapCoverage === 'GAP_COVERAGE_NOT_BOUND');
  requireFact(Array.isArray(chain.candles) && Array.isArray(receipt.pairs));
  const request = chain.request;
  requireFact(request && ['fromInclusive', 'toExclusive', 'knowledgeCutoff'].every(
    key => typeof request[key] === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?Z$/.test(request[key])));
  const lines = [
    'PumpSwap — сохранённые свечи и диагностика',
    `Окно UTC: ${request.fromInclusive} — ${request.toExclusive}`,
    `Срез доступности: ${request.knowledgeCutoff}; анализ ретроспективный.`,
    `Весь сохранённый batch: ${count(chain.reconciliation?.transactionCount)} транзакций, ${count(chain.reconciliation?.operationCount)} операций.`,
    'Три настроенных пула (прочие пулы внутри транзакций не включены в строки ниже):'
  ];
  for (const address of POOLS) {
    const candles = chain.candles.filter(c => c.pool?.pool === address);
    const pairs = receipt.pairs.filter(p => p.request?.pool?.pool === address);
    let operations = 0;
    const statuses = Object.fromEntries(STATUSES.map(s => [s, 0]));
    const reasons = new Map();
    for (const candle of candles) operations = count(operations + count(candle.activity?.operationCount));
    for (const pair of pairs) {
      requireFact(STATUSES.includes(pair.status) && Array.isArray(pair.reasons));
      statuses[pair.status]++;
      if (pair.status === 'INSUFFICIENT') for (const reason of pair.reasons) {
        requireFact(typeof reason === 'string' && /^[A-Z][A-Z0-9_]{0,100}$/.test(reason));
        reasons.set(reason, (reasons.get(reason) || 0) + 1);
      }
    }
    lines.push(`${address}: свечей ${candles.length}, операций ${operations}; срабатываний ${statuses.TRIGGER}, нет сигнала ${statuses.NO_TRIGGER}, недостаточно данных ${statuses.INSUFFICIENT}.`);
    if (reasons.size) lines.push('  Причины: ' + [...reasons].sort(([a], [b]) => a.localeCompare(b, 'en')).map(
      ([reason, total]) => `${REASONS[reason] || 'другая явно указанная причина (' + reason + ')'}: ${total}`).join('; ') + '.');
  }
  lines.push('Причины могут пересекаться. Проверки окон — не число реальных торговых сигналов.',
    'Только диагностика (DIAGNOSTIC_ONLY_NOT_ADMITTED): полнота и потери неизвестны, разрывы не подтверждены как закрытые. Не оценка прибыльности.',
    `SHA-256 исходных данных: ${receipt.sourceSha256}`,
    `SHA-256 прочитанного отчёта: ${receiptSha}`);
  return lines.join('\n') + '\n';
}
function readReport(filename, expectedSha) {
  requireFact(typeof expectedSha === 'string' && /^[a-f0-9]{64}$/.test(expectedSha));
  const fd = fs.openSync(filename, 'r');
  let bytes;
  try {
    const size = fs.fstatSync(fd);
    requireFact(size.isFile() && size.size <= MAX_BYTES);
    // Bound actual reading too, even if a file grows after the size check.
    const buffer = Buffer.alloc(size.size + 1);
    let used = 0;
    while (used < buffer.length) {
      const n = fs.readSync(fd, buffer, used, buffer.length - used, null);
      if (!n) break;
      used += n;
    }
    requireFact(used === size.size);
    bytes = buffer.subarray(0, used);
  } finally { fs.closeSync(fd); }
  const sha = crypto.createHash('sha256').update(bytes).digest('hex');
  requireFact(sha === expectedSha);
  return render(JSON.parse(bytes.toString('utf8')), sha);
}
function main(args = process.argv.slice(2)) {
  try {
    requireFact(args.length === 2);
    process.stdout.write(readReport(args[0], args[1]));
    return 0;
  } catch {
    // Never print raw payloads, paths, parser excerpts or exception messages.
    process.stderr.write('Отчёт не прочитан: проверьте путь, SHA-256 и формат сохранённой квитанции.\n');
    return 1;
  }
}
if (require.main === module) process.exitCode = main();
module.exports = { readReport, render, POOLS, MAX_BYTES };
