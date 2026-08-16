import fs from 'node:fs';

const service = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoObserverService.java', 'utf8');
const main = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/MainActivity.java', 'utf8');
const gradle = fs.readFileSync('android/app/build.gradle', 'utf8');
const fast = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoFastVisualDetector.java');
const coordinator = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoSelectionCoordinator.java');
const backend = fs.readFileSync('functions/src/gtoTrips.ts');
const crypto = await import('node:crypto');
const sha = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

const checks = [
  ['normal route guidance remains in the top journey guide', service.includes('Etapa 2 de 4 · Faça a rota normalmente. Ao chegar ao destino, a NVU identificará a conclusão e registrará a viagem automaticamente.')],
  ['duplicated lower auto-finish helper removed', !service.includes('TextView autoFinishHelper')],
  ['visual gate remains only an OCR wake-up mechanism', service.includes('GtoResultVisualGate is intentionally permissive and exists only to wake OCR')],
  ['fallback requires semantic OCR evidence', service.includes('hasPartialResultSemanticEvidence(normalized)')],
  ['visual candidate timestamp no longer drives driver-facing fallback', !service.includes('candidateAt > 0L && now - candidateAt <= AUTO_RESULT_FALLBACK_WINDOW_MS')],
  ['partial result evidence requires real result vocabulary', service.includes('return valueLabel || (completionWord && (receiveAction || bonusAction));')],
  ['manual fallback remains available when genuinely needed', service.includes('Confirmar conclusão da entrega')],
  ['automatic result parser remains authoritative', service.includes('ResultScreen resultScreen = parseResultScreen(lines, normalized);')],
  ['automatic Firebase sync path remains present', service.includes('GtoAutoTripSync')],
  ['MainActivity compile visibility fix retained', main.includes('public void onStart()')],
  ['Android version remains at or above the R3.2 baseline', /versionCode\s+(2[2-9]|[3-9][0-9]|[1-9][0-9]{2,})/.test(gradle)],
];

for (const [name, ok] of checks) console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}`);
console.log(`INFO freight detector sha256 ${sha(fast)}`);
console.log(`INFO selection coordinator sha256 ${sha(coordinator)}`);
console.log(`INFO backend gtoTrips sha256 ${sha(backend)}`);
const failed = checks.filter(([, ok]) => !ok);
console.log(`\n${checks.length - failed.length}/${checks.length} R3.2 checks passed.`);
if (failed.length) process.exit(1);
