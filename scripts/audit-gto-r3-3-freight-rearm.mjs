import fs from 'node:fs';
import crypto from 'node:crypto';

const service = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoObserverService.java', 'utf8');
const fast = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoFastVisualDetector.java');
const coordinator = fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoSelectionCoordinator.java');
const gradle = fs.readFileSync('android/app/build.gradle', 'utf8');
const backend = fs.readFileSync('functions/src/gtoTrips.ts');
const sha = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

const WORKING_FAST = '069c51986dd6bdf58e2b8d12d1fdcb9862f35c3b24f27c30613241a6fe8ecbfd';
const WORKING_COORD = 'd84fe0848f5a054225cf939786156c07a291a4eb74a362ca9f72878d920b0ddd';

const checks = [
  ['working freight detector preserves OCR-free selection contract', fast.toString('utf8').includes('PressCandidate') && fast.toString('utf8').includes('detectButtons') && !fast.toString('utf8').includes('TextRecognizer')],
  ['working selection coordinator remains byte-identical', sha(coordinator) === WORKING_COORD],
  ['replacement freight candidate is armed only inside the guarded list-observation path', service.includes('replacementFreightCandidateArmed') && service.includes('armOrRefreshReplacementFreightCandidate(image, frame, now)') && service.includes('isExplicitFreightReplacementActive(now)')],
  ['touch pulse sensor observes a replacement candidate without bypassing state policy', service.includes('replacementSelectionArmed = replacementFreightCandidateArmed') && service.includes('isReplaceableActiveSessionState(state)') && service.includes('GtoDeterministicFlowPolicy.mayObserveFreightListOutsideWaiting(state)')],
  ['touch marker promotion is guarded by the explicit active-trip replacement arm', /STATE_TRIP_IN_PROGRESS\.equals\(replacedState\)[\s\S]*!isExplicitFreightReplacementActive/.test(service) && service.includes('promoteReplacementFreightCandidateToWaiting(')],
  ['single permissive frame cannot cancel a real route on arbitrary touch', service.includes('GtoFreightBootstrapPolicy.shouldAwaitSecondListFrame(') && service.includes('replacementFreightTouchPending = true') && service.includes('if (STATE_TRIP_IN_PROGRESS.equals(activeState) && !explicitReplacement)')],
  ['pending fast touch still requires list evidence and guarded promotion', service.includes('replacementFreightTouchPending') && service.includes('activeTripFreightListFrames >= 2') && service.includes('promoteReplacementFreightCandidateToWaiting(')],
  ['pre-touch freight page is frozen before state replacement', service.includes('captureReplacementFreightPanel(image, frame)') && service.includes('replacementFreightPanelFrame')],
  ['candidate survives short list-close race until touch marker arrives', service.includes('CRITICAL_TOUCH_WINDOW_MS + 260L')],
  ['page navigation refreshes replacement baseline instead of mixing pages', service.includes('!fastVisualDetector.samePage(replacementFreightBaseline, frame)')],
  ['pressed row can be recovered before stale route promotion', service.includes('replacementFreightPressedRow = pressed.row')],
  ['old unfinished session is explicitly discarded', service.includes('GtoAutoTripSync.discardSessionSnapshot(this, cancelledSessionId)')],
  ['new session is created before selected freight is committed', service.includes('beginTrip(false);') && service.includes('STATE_WAITING_FREIGHT.equals(getTripState())')],
  ['pre-touch panel and button geometry are restored into new session', service.includes('latestFreightPanelFrame = savedPanel') && service.includes('realtimeAcceptRects.add(new Rect(rect))')],
  ['selection coordinator receives restored baseline sequence', service.includes('long baselineSequence = selectionCoordinator.onFrameProcessed()') && service.includes('recordFastFreightFrame(savedBaseline, baselineSequence)')],
  ['new page OCR is scheduled after replacement session is armed', service.includes('scheduleFreightPageOcr(') && service.includes('freightPageGeneration, savedPanel, savedOffset, savedButtons')],
  ['active route ignores list-like pixels unless replacement is explicitly armed', service.includes('mayProbeFreightListForCurrentState') && service.includes('if (STATE_TRIP_IN_PROGRESS.equals(activeState) && !explicitReplacement)') && service.includes('putString("screenState", "TRIP")') && service.includes('boolean selectedNewRow = replacementFreightTouchPending || replacementFreightPressedRow >= 0')],
  ['completed delivery remains outside replacement cancellation path', (() => { const a=service.indexOf('private boolean isReplaceableActiveSessionState'); const b=service.indexOf('private boolean hasRecentNormalResultActionEvidence', a); return a>=0 && b>a && !service.slice(a,b).includes('STATE_RESULT_CONFIRMED'); })()],
  ['automatic result flow remains present', service.includes('confirmNormalResultAutomatically()') && service.includes('GtoAutoTripSync.enqueueConfirmedTrip')],
  ['R3.2 semantic-only fallback remains present', service.includes('hasPartialResultSemanticEvidence(normalized)')],
  ['Android version remains at or above R3.3 baseline', (() => {
    const code = Number((gradle.match(/versionCode\s+(\d+)/) || [])[1] || 0);
    const name = (gradle.match(/versionName\s+"([^"]+)"/) || [])[1] || '';
    return code >= 23 && /^1\.0\.(?:2[3-9]|[3-9]\d|\d{3,})$/.test(name);
  })()],
];

for (const [name, ok] of checks) console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}`);
console.log(`INFO freight detector sha256 ${sha(fast)}`);
console.log(`INFO selection coordinator sha256 ${sha(coordinator)}`);
console.log(`INFO backend gtoTrips sha256 ${sha(backend)}`);
const failed = checks.filter(([, ok]) => !ok);
console.log(`\n${checks.length - failed.length}/${checks.length} R3.3 freight re-arm checks passed.`);
if (failed.length) process.exit(1);
