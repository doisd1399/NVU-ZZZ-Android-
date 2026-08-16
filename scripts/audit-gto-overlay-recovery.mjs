import fs from 'node:fs';
import path from 'node:path';

const checks = [];
const check = (name, ok) => {
  checks.push([name, Boolean(ok)]);
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}`);
};
const read = (p) => fs.readFileSync(p, 'utf8');

const remote = JSON.parse(read('capacitor.remote.json'));
const assetConfig = JSON.parse(read('android/app/src/main/assets/capacitor.config.json'));
const service = read('android/app/src/main/java/com/nvu/operacional/GtoObserverService.java');
const plugin = read('android/app/src/main/java/com/nvu/operacional/GtoObserverPlugin.java');
const main = read('android/app/src/main/java/com/nvu/operacional/MainActivity.java');
const bridge = read('src/lib/gtoObserver.ts');
const setup = read('src/components/GtoObserverSetup.tsx');
const build = read('android/app/build.gradle');
const bundleName = fs.readdirSync('android/app/src/main/assets/public/assets').find((x) => x.startsWith('RecordTrip-') && x.endsWith('.js'));
const bundle = bundleName ? read(path.join('android/app/src/main/assets/public/assets', bundleName)) : '';

check('Capacitor source delivery defaults to local assets', remote.enabled === false);
check('packaged Android config has no remote server.url', !assetConfig.server);
check('Android sync script forces local mode', read('package.json').includes('"cap:sync:android": "npm run cap:local'));
check('service publishes heartbeat', service.includes('serviceHeartbeatAt'));
check('visible NVU activity restores previously enabled observer', main.includes('GtoObserverService.recoverIfEnabled(this)'));
check('frontend also requests safe recovery', setup.includes('GtoObserver.recoverObserver()'));
check('plugin confirms service start instead of blind success', plugin.includes('confirmed.optLong("serviceHeartbeatAt"') && plugin.includes('confirmed.put("started", started)'));
check('overlay add failure is persisted', service.includes('recordOverlayFailure(ex)') && service.includes('overlayError'));
check('overlay creation is retried with throttle', service.includes('BUBBLE_RETRY_INTERVAL_MS'));
check('overlay success clears diagnostic failure', service.includes('Botão flutuante restaurado no GTO'));
check('legacy Global Truck alias recognized in source', bridge.includes('value.includes("global truck")'));
check('bundled Android frontend also recognizes legacy alias', bundle.includes('N.includes("global truck")'));
check('bundled Android frontend has observer recovery', bundle.includes('ke.recoverObserver()'));
check('bundled Android wording matches automatic registration flow', bundle.includes('registro automático das viagens concluídas') || bundle.includes('Frete, rota, conclusão e envio acompanhados automaticamente pelo botão flutuante.'));
check('private signing key is not shipped', !fs.existsSync('android/app/meu-app.keystore.jks'));
check('root node_modules excluded from delivery', !fs.existsSync('node_modules'));

const failed = checks.filter(([, ok]) => !ok);
const report = [
  'NVU GTO FIX18-R1 — OVERLAY/RECOVERY AUDIT',
  '==========================================',
  `Result: ${checks.length - failed.length}/${checks.length} checks passed`,
  '',
  ...checks.map(([name, ok]) => `${ok ? 'PASS' : 'FAIL'} | ${name}`),
  '',
  'Notes:',
  '- The native freight-selection and completion algorithms were intentionally left unchanged.',
  '- Android packaged assets are local; Netlify remote mode remains an explicit development option only.',
  '- The existing packaged RecordTrip bundle was patched because npm registry access was unavailable in this execution environment.',
  '- A normal npm run cap:sync:android on the development machine will rebuild these assets from the corrected source.',
  '- Release signing keys are intentionally excluded from shared source archives.',
  '',
].join('\n');
fs.writeFileSync('GTO_FIX18_R1_OVERLAY_RECOVERY_AUDIT.txt', report);
console.log(`\n${checks.length - failed.length}/${checks.length} recovery checks passed.`);
if (failed.length) process.exit(1);
