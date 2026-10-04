'use strict';
const c = require('./e2-census.cjs');
async function runCli(args, deps = {}) {
  if (!Array.isArray(args)) return c.error('ARGUMENTS_INVALID'); if (!args.length) return c.error('DISABLED');
  if (args.length === 2 && args[0] === '--replay' && args[1] === c.OUTPUT + '\\manifest.json') return c.replay(deps.store ?? c.fileStore());
  if (args.length === 3 && args[0] === '--enable-public' && args[1] === '--retained-before')
    return c.run({ enabled: true, retainedBefore: args[2], output: c.OUTPUT }, deps);
  return c.error('ARGUMENTS_INVALID');
}
if (require.main === module) runCli(process.argv.slice(2)).then(result => {
  const { summary, ...receipt } = result, bytes = Buffer.from(JSON.stringify(receipt) + '\n');
  if (bytes.length > c.LIMITS.stdout) { process.stdout.write(JSON.stringify(c.error('METADATA_LIMIT')) + '\n'); process.exitCode = 1; return; }
  const preflightCapacityFailure = ['DISK_LIMIT', 'FREE_SPACE_LIMIT'].includes(result.code) && result.accounting?.attempts === 0;
  process.stdout.write(bytes); process.exitCode = preflightCapacityFailure ? 1 : result.code === null && result.status === 'SCAN_COMPLETE' ? 0
    : result.status === 'INCOMPLETE' && !['ARGUMENTS_INVALID', 'UNSAFE_PATH', 'ROOT_EXISTS', 'EXPIRY', 'INTEGRITY_ERROR', 'RUNTIME_INVALID', 'SOURCE_IDENTITY_INVALID'].includes(result.code) ? 2 : 1;
}).catch(() => { process.stdout.write(JSON.stringify(c.error('INTEGRITY_ERROR')) + '\n'); process.exitCode = 1; });
module.exports = { runCli };
