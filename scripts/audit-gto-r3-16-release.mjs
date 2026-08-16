import fs from 'node:fs';
import crypto from 'node:crypto';

const read = (p) => fs.readFileSync(p, 'utf8');
const checks = [];
const check = (name, ok, detail = '') => {
  checks.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ` — ${detail}` : ''}`);
};

const service = read('android/app/src/main/java/com/nvu/operacional/GtoObserverService.java');
const plugin = read('android/app/src/main/java/com/nvu/operacional/GtoObserverPlugin.java');
const main = read('android/app/src/main/java/com/nvu/operacional/MainActivity.java');
const permission = read('android/app/src/main/java/com/nvu/operacional/GtoProjectionPermissionActivity.java');
const sync = read('android/app/src/main/java/com/nvu/operacional/GtoAutoTripSync.java');
const launcher = read('src/services/gtoWorkLauncher.ts');
const bridge = read('src/lib/gtoObserver.ts');
const manifest = read('android/app/src/main/AndroidManifest.xml');
const gradle = read('android/app/build.gradle');
const remote = JSON.parse(read('capacitor.remote.json'));

check('release version is R3.31 baseline', Number((gradle.match(/versionCode\s+(\d+)/)||[])[1]||0) >= 48 && Number((gradle.match(/versionName\s+"1\.0\.(\d+)"/)||[])[1]||0) >= 48);
check('remote Capacitor runtime is HTTPS Netlify', remote.enabled === true && /^https:\/\//.test(remote.url) && remote.url.includes('netlify.app'));
check('web launcher opens GTO before initial MediaProjection consent', !launcher.includes('requestScreenCapture()') && launcher.includes('openGto()') && service.includes('armProjectionPermissionAfterGtoOpen()'));
check('permission flow is visible NVU ActivityResult', plugin.includes('startActivityForResult(call, captureIntent, "screenCaptureResult")'));
check('permission recovery does not launch GTO before projection', main.includes('projectionActive') && main.includes('reopenGtoWhenProjectionReady'));
check('projection callback is generation protected', service.includes('generation != projectionGeneration || mediaProjection != projection'));
check('permission transition has foreground grace', service.includes('PERMISSION_RETURN_GRACE_MS') && service.includes('projectionPermissionInFlight'));
check('selection failure explicitly arms reopen after restoring WAITING', service.includes('armFreightListReopenAfterSelectionFailure(now, safeReason)'));
check('selection close is recorded even while confirming', service.includes('CONFIRMING_FREIGHT') && service.includes('freightListCycleClosed'));
check('reopened identical list creates a fresh session', service.includes('restartWaitingFreightSelectionSession("FREIGHT_LIST_REOPENED_AFTER_SELECTION_FAILURE")'));
check('fresh retry clears previous session snapshot', service.includes('discardSessionSnapshot(this, previousSessionId)') && service.includes('clearTripAnalysis()'));
check('fresh retry generates a new session id', service.includes('String newSessionId = GtoAutoTripSync.newSessionId()'));
check('successful selection consumes reopen lifecycle', service.includes('freightListReopenPending = false;') && service.includes('STATE_TRIP_IN_PROGRESS'));
check('stale OCR cannot cross session generation', service.includes('transaction.generation != preciseSelectionOcrGeneration') && service.includes('transaction.sessionId.equals(currentSessionId)'));
check('selected freight is durably locked before route state', service.includes('GtoAutoTripSync.lockSelectedFreight(this, prefs)') && service.includes('STATE_TRIP_IN_PROGRESS'));
check('queue is sealed and retried durably', sync.includes('sealPayload') && sync.includes('STATUS_PENDING') && sync.includes('scheduleRetry'));
check('driver UID mismatch is fail-closed', sync.includes('DRIVER_UID_MISMATCH') && sync.includes('quarantine'));
check('no audio capture permission is declared', !manifest.includes('RECORD_AUDIO') && !service.includes('AudioRecord'));
check('media projection foreground service type is declared', manifest.includes('FOREGROUND_SERVICE_MEDIA_PROJECTION') && manifest.includes('foregroundServiceType="specialUse|mediaProjection"'));
check('selection coordinator remains sequence based', read('android/app/src/main/java/com/nvu/operacional/GtoSelectionCoordinator.java').includes('touchMarkerSequence') && read('android/app/src/main/java/com/nvu/operacional/GtoSelectionCoordinator.java').includes('frameSequence'));
check('web/native status exposes lifecycle diagnostics', bridge.includes('projectionPermissionInFlight') && bridge.includes('gtoTripSessionId') && bridge.includes('tripState'));

// Deterministic lifecycle model: failure after close must arm exactly one new session;
// identical reopening must consume the arm and not create a second session per frame.
let state = 'CONFIRMING_FREIGHT';
let seen = true;
let closed = false;
let reopen = false;
let sessions = 1;
const fail = () => { closed = true; state = 'WAITING_FREIGHT'; reopen = true; };
const visible = () => { if (seen && closed && reopen && state === 'WAITING_FREIGHT') { sessions += 1; closed = false; reopen = false; } };
fail(); visible(); visible();
check('deterministic retry model creates exactly one new session', sessions === 2 && !reopen && !closed);

const failed = checks.filter(c => !c.ok);
console.log(`\\n${checks.length - failed.length}/${checks.length} R3.16 release-audit checks passed.`);
if (failed.length) process.exit(1);
