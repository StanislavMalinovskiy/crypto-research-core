'use strict';
const p = require('./e2-path-probe.cjs');
async function runCli(args, deps = {}) {
  if (!Array.isArray(args)) return p.error('ARGUMENTS_INVALID'); if (args.length === 0) return p.error('DISABLED');
  if (args.length === 2 && args[0] === '--replay' && args[1] === p.OUTPUT + '\\manifest.json') return p.replay(deps.store ?? p.fileStore());
  if (args.length === 3 && args[0] === '--enable-free' && args[1] === '--credits-remaining') return p.run({ enabled: true, creditsRemaining: args[2], output: p.OUTPUT }, deps);
  return p.error('ARGUMENTS_INVALID');
}
if (require.main === module) runCli(process.argv.slice(2)).then(r => {
  const { summary, ...receipt } = r; process.stdout.write(JSON.stringify(receipt) + '\n'); process.exitCode = r.code === null ? 0 : r.code === 'DISABLED' ? 2 : 1;
}).catch(() => { process.stdout.write(JSON.stringify(p.error('INTERNAL_ERROR')) + '\n'); process.exitCode = 1; });
module.exports = { runCli };
