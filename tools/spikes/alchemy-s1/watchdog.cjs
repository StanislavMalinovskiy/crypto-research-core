'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { fork } = require('node:child_process');
const { atomic, free } = require('./journal.cjs');
function supervise(child, options) {
  const watch = fork(__filename, [], { stdio: ['ignore', 'ignore', 'ignore', 'ipc'], windowsHide: true });
  watch.send({ pid: child.pid, ...options });
  child.once('exit', () => { if (watch.connected) watch.send({ finished: true }); });
  return watch;
}
if (require.main === module) {
  let timer, settings;
  process.on('message', message => {
    if (message.finished) { clearInterval(timer); process.exit(0); }
    if (settings) return; settings = message;
    timer = setInterval(() => {
      let reason;
      try {
        if (Date.now() >= settings.deadline) reason = 'WALL_LIMIT';
        else if (free(settings.dir) < settings.floor) reason = 'FREE_SPACE_LIMIT';
        if (!reason) { process.kill(settings.pid, 0); return; }
        // This tiny terminal record is covered by the collector's reserved headroom.
        let outcome = 'INCONCLUSIVE';
        const checkpoint = path.join(settings.dir, 'checkpoint.json');
        if (fs.existsSync(checkpoint) && fs.statSync(checkpoint).size < 1024 * 1024) {
          try { const state = JSON.parse(fs.readFileSync(checkpoint)); if (state.timeline?.outcome === 'FAIL' || state.summary?.outcome === 'FAIL') outcome = 'FAIL'; } catch {}
        }
        atomic(path.join(settings.dir, 'watchdog.json'), { reason, outcome, abrupt: true, at: new Date().toISOString() });
      } catch { reason = reason || 'WATCHDOG_ERROR'; }
      try { process.kill(settings.pid, 'SIGKILL'); } catch {} clearInterval(timer); process.exit(0);
    }, 100);
  });
  process.on('disconnect', () => {
    if (settings) { try { process.kill(settings.pid, 'SIGKILL'); } catch {} }
    process.exit(0);
  });
}
module.exports = { supervise };
