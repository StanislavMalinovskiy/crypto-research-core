'use strict';
const p = require('./helius-probe.cjs');
async function runCli(args, deps = {}) {
  const invalid = code => ({ classification: 'EXPLORATORY', status: 'INCOMPLETE', code, d1Evidence: false, d1Passed: false, runAuthorized: false });
  if (!Array.isArray(args)) return invalid('ARGUMENTS_INVALID'); if (args.length === 0) return invalid('DISABLED');
  if (args.length === 2 && args[0] === '--replay' && args[1] === p.OUTPUT + '\\manifest.json') {
    try { return await p.replay(deps.store ?? p.fileStore()); } catch { return invalid('INTEGRITY_ERROR'); }
  }
  if (args.length === 2 && ['--replay', '--forecast'].includes(args[0]) && args[1] === p.OUTPUT3 + '\\manifest.json') {
    try { const result = await p.replay3(deps.store ?? p.fileStore(p.OUTPUT3), { utcNow: deps.utcNow }); if (args[0] === '--replay' || result.code) return result;
      return { ...p.forecast(result.summary, result.accounting), code: null, status: result.status, summaryHash: result.summaryHash }; } catch { return invalid('INTEGRITY_ERROR'); }
  }
  if (args.length === 7 && args[0] === '--enable-free' && args[1] === '--stage' && args[2] === 'H3' && args[3] === '--credits-remaining' && args[5] === '--output' && args[6] === p.OUTPUT3)
    return p.runH3({ enabled: true, stage: 'H3', creditsRemaining: args[4], output: args[6] }, deps);
  if (args.length !== 7 || args[0] !== '--enable-free' || args[1] !== '--stage' || args[2] !== 'H1' || args[3] !== '--credits-remaining' ||
    args[5] !== '--output' || args[6] !== p.OUTPUT) return invalid('ARGUMENTS_INVALID');
  return p.runProbe({ enabled: true, stage: 'H1', creditsRemaining: args[4], output: args[6] }, deps);
}
if (require.main === module) runCli(process.argv.slice(2)).then(result => {
  const { summary, ...receipt } = result; process.stdout.write(JSON.stringify(receipt) + '\n'); process.exitCode = result.code === null ? result.status === 'COMPLETE' ? 0 : 2 : ['DISABLED', 'HTTP_ERROR', 'RPC_ERROR'].includes(result.code) ? 2 : 1;
}).catch(() => { process.stdout.write(JSON.stringify({ classification: 'EXPLORATORY', code: 'INTERNAL_ERROR', d1Evidence: false, d1Passed: false, runAuthorized: false }) + '\n'); process.exitCode = 1; });
module.exports = { runCli };
