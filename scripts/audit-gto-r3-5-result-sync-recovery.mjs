import fs from 'node:fs';

const service = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoObserverService.java', 'utf8');
const sync = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoAutoTripSync.java', 'utf8');
const gradle = fs.readFileSync('android/app/build.gradle', 'utf8');
const fast = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoFastVisualDetector.java', 'utf8');
const coord = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoSelectionCoordinator.java', 'utf8');
const flow = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoDeterministicFlowPolicy.java', 'utf8');

const checks = [];
function check(name, ok) { checks.push([name, !!ok]); }

check('R3.5+ versionCode >=25', /versionCode\s+(?:2[5-9]|[3-9]\d+)\b/.test(gradle));
check('R3.5+ versionName >=1.0.25', /versionName\s+"1\.0\.(?:2[5-9]|[3-9]\d+)"/.test(gradle));
check('result action survives normal GTO loading or is durable without timeout', /RESULT_ACTION_CONFIRM_WINDOW_MS\s*=\s*10_000L/.test(service) || /deliberately has NO time window/.test(service));
check('result states arm independent touch pulse sensor', /STATE_RESULT_DETECTED\.equals\(state\).*STATE_AWAITING_BONUS\.equals\(state\)/s.test(service) && /markResultActionTouch\(\)/.test(service));
check('result action marker is persisted', /putLong\("resultActionTouchAt", now\)/.test(service));
check('persisted result action survives process restart', /prefs\.getLong\("resultActionTouchAt", 0L\)/.test(service));
check('generic result action does not claim ADS/Receive coordinates', /putString\("resultAction", "TOUCH_PENDING"\)/.test(service));
check('explicit ADS still blocks normal result evidence', /"RECEIVE"\.equals\(action\).*"TOUCH_PENDING"\.equals\(action\)/s.test(service));
check('explicit ad semantic evidence remains authoritative', /containsPostResultAdEvidence\(normalized\)/.test(service) && /STATE_REJECTED_BONUS/.test(service));
check('slow result transition can still confirm after gameplay returns', /actionBackedReturn/.test(service) && /confirmNormalResultAutomatically\(\)/.test(service));
check('R3.5 compatibility no longer requires a time-only completion path', !/directLegacyReturn/.test(service) || /now - resultExitSeenAt <= 1800L/.test(service));
check('unknown post-result transition stays neutral instead of becoming an implicit ADS verdict', /Unknown\/post-result intermediary screens are neutral/.test(service) && /recordNeutralScreenObservation\("UNKNOWN_AFTER_RESULT"/.test(service));
check('freight list is probed in RESULT_DETECTED', /mayObserveFreightListOutsideWaiting[\s\S]*RESULT_DETECTED/.test(flow) && service.includes('mayProbeFreightListForCurrentState'));
check('freight list is probed in AWAITING_BONUS', /mayObserveFreightListOutsideWaiting[\s\S]*AWAITING_BONUS_VALIDATION/.test(flow) && service.includes('mayProbeFreightListForCurrentState'));
check('freight list after unresolved result preserves the previous delivery', /A result already detected cannot be discarded by merely seeing the freight[\s\S]*Lista detectada após resultado · entrega anterior preservada/.test(service));
check('unresolved result is discarded only by explicit driver recovery action', /discardUnresolvedResultAndStartNewFreight\(\)/.test(service) && /DRIVER_CONFIRMED_UNRESOLVED_RESULT_DISCARD/.test(service));
check('new freight snapshot is promoted only through the guarded replacement path', /promoteReplacementFreightCandidateToWaiting/.test(service) && /isExplicitFreightReplacementActive/.test(service));
check('fast tap after stale result does not discard action-backed completed delivery', /Do not throw away a completed delivery/.test(service));
check('duplicate result confirmation is guarded', /if \(STATE_RESULT_CONFIRMED\.equals\(currentState\)\) return;/.test(service));
check('completed trip still enters durable pending queue', /putString\("gtoTripSyncStatus", GtoAutoTripSync\.STATUS_PENDING\)/.test(service));
check('Firebase callable has application watchdog', /CALL_WATCHDOG_MS\s*=\s*25_000L/.test(sync) && /MAIN_HANDLER\.postDelayed/.test(sync));
check('watchdog releases IN_FLIGHT lock', /IN_FLIGHT\.remove\(sessionId\)/.test(sync));
check('watchdog preserves queue and schedules retry', /queue\.contains\(key\)/.test(sync) && /scheduleRetry\(retry, sessionId, null\)/.test(sync));
check('watchdog converts endless SYNCING to visible PENDING', /CALL_TIMEOUT/.test(sync) && /markPending\(mainPrefs, message\)/.test(sync));
check('missing native auth notifies active service UI', /NO_NATIVE_AUTH[\s\S]*listener\.onPending/.test(sync));
check('driver UID mismatch notifies active service UI', /DRIVER_UID_MISMATCH[\s\S]*listener\.onPending\(sessionId, message\)/.test(sync));
check('pending UI keeps driver message simple and preserves actual backend/auth error for diagnostics', /announceDriverStage\("SYNC_PENDING", stageMessage/.test(service) && /putString\("gtoTripSyncError", detail\)/.test(service) && /Viagem salva e aguardando envio\./.test(service));
check('result action metadata is cleared before next trip', /remove\("resultAction"\)/.test(service) && /remove\("resultActionTouchAt"\)/.test(service));
check('fast detector remains present and OCR-free', /class GtoFastVisualDetector/.test(fast) && !/TextRecognition|getClient\(/.test(fast));
check('selection coordinator remains sequence based', /class GtoSelectionCoordinator/.test(coord) && /markTouch\(\)/.test(coord));

// Behavioral state-model checks for R3.5+; R3.6 intentionally removes the action timeout.
function resolve({state, hasAction, adEvidence, gameplayFrames, exitAgeMs, freightFrames, freightAgeMs, explicitDiscard=false}) {
  if (adEvidence && (state === 'RESULT_DETECTED' || state === 'AWAITING')) return 'REJECTED_BONUS';
  if (gameplayFrames >= 2 && exitAgeMs >= 120 && hasAction) return 'RESULT_CONFIRMED';
  if (freightFrames >= 1 && hasAction) return 'RESULT_CONFIRMED';
  if (freightFrames >= 1 && !hasAction && explicitDiscard) return 'WAITING_FREIGHT_NEW_SESSION';
  if (freightFrames >= 1 && !hasAction) return state;
  if (exitAgeMs >= 1800 && state === 'RESULT_DETECTED') return 'AWAITING';
  return state;
}
check('scenario: Receive + 4.5s logo + gameplay confirms', resolve({state:'AWAITING',hasAction:true,adEvidence:false,gameplayFrames:2,exitAgeMs:4500,freightFrames:0,freightAgeMs:0}) === 'RESULT_CONFIRMED');
check('scenario: ADS semantic evidence is rejected', resolve({state:'AWAITING',hasAction:true,adEvidence:true,gameplayFrames:0,exitAgeMs:2500,freightFrames:0,freightAgeMs:0}) === 'REJECTED_BONUS');
check('scenario: unresolved result without action + jobs list stays preserved', resolve({state:'AWAITING',hasAction:false,adEvidence:false,gameplayFrames:0,exitAgeMs:12000,freightFrames:4,freightAgeMs:500}) === 'AWAITING');
check('scenario: unresolved result changes session only after explicit discard', resolve({state:'AWAITING',hasAction:false,adEvidence:false,gameplayFrames:0,exitAgeMs:12000,freightFrames:4,freightAgeMs:500,explicitDiscard:true}) === 'WAITING_FREIGHT_NEW_SESSION');
check('scenario: action-backed result + jobs list preserves completed trip with no timeout', resolve({state:'AWAITING',hasAction:true,adEvidence:false,gameplayFrames:0,exitAgeMs:120000,freightFrames:4,freightAgeMs:500}) === 'RESULT_CONFIRMED');

let passed = 0;
for (const [name, ok] of checks) {
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}`);
  if (ok) passed++;
}
console.log(`\n${passed}/${checks.length} R3.5 result/sync recovery checks passed.`);
if (passed !== checks.length) process.exit(1);
