import fs from 'node:fs';

const servicePath = 'android/app/src/main/java/com/nvu/operacional/GtoObserverService.java';
const permissionPath = 'android/app/src/main/java/com/nvu/operacional/GtoProjectionPermissionActivity.java';
const pluginPath = 'android/app/src/main/java/com/nvu/operacional/GtoObserverPlugin.java';
const manifestPath = 'android/app/src/main/AndroidManifest.xml';
const stylesPath = 'android/app/src/main/res/values/styles.xml';
const fastDetectorPath = 'android/app/src/main/java/com/nvu/operacional/GtoFastVisualDetector.java';
const resultGatePath = 'android/app/src/main/java/com/nvu/operacional/GtoResultVisualGate.java';
const selectionCoordinatorPath = 'android/app/src/main/java/com/nvu/operacional/GtoSelectionCoordinator.java';

const service = fs.readFileSync(servicePath, 'utf8');
const permission = fs.readFileSync(permissionPath, 'utf8');
const plugin = fs.readFileSync(pluginPath, 'utf8');
const manifest = fs.readFileSync(manifestPath, 'utf8');
const styles = fs.readFileSync(stylesPath, 'utf8');
const fastDetector = fs.readFileSync(fastDetectorPath, 'utf8');
const resultGate = fs.readFileSync(resultGatePath, 'utf8');
const selectionCoordinator = fs.readFileSync(selectionCoordinatorPath, 'utf8');

const checks = [];
function check(name, ok, detail='') {
  checks.push({name, ok, detail});
  if (!ok) console.error(`FAIL ${name}${detail ? `: ${detail}` : ''}`);
  else console.log(`OK   ${name}`);
}

function methodBody(source, signature, nextSignature) {
  const start = source.indexOf(signature);
  const end = nextSignature ? source.indexOf(nextSignature, start + signature.length) : -1;
  if (start < 0) return '';
  return source.slice(start, end > start ? end : source.length);
}

check('legacy isolated permission activity remains non-exported fallback', manifest.includes('android:name=".GtoProjectionPermissionActivity"') && manifest.includes('android:exported="false"'));
check('legacy permission task stays excluded from recents', manifest.includes('android:excludeFromRecents="true"') && manifest.includes('android:autoRemoveFromRecents="true"'));
check('isolated permission host stays hard landscape', manifest.includes('android:screenOrientation="landscape"') && permission.includes('SCREEN_ORIENTATION_LANDSCAPE'));
check('initial permission is not owned by Capacitor/NVU activity', !plugin.includes('@ActivityCallback') && !plugin.includes('startActivityForResult(call, captureIntent, "screenCaptureResult")') && permission.includes('startActivityForResult(captureIntent, REQUEST_CAPTURE)'));
check('permission host requests whole display on Android14+', permission.includes('MediaProjectionConfig.createConfigForDefaultDisplay()'));
check('web launch opens GTO before initial projection consent', !fs.readFileSync('src/services/gtoWorkLauncher.ts','utf8').includes('requestScreenCapture()') && fs.readFileSync('src/services/gtoWorkLauncher.ts','utf8').includes('openGto()') && service.includes('armProjectionPermissionAfterGtoOpen()') && service.includes('maybeLaunchInitialProjectionPermissionOverGto(now)'));
check('in-GTO permission recovery never opens MainActivity', methodBody(service, 'private void requestProjectionPermission()', 'private void scheduleBubbleRestoreAfterPermission()').includes('GtoProjectionPermissionActivity.class') && !methodBody(service, 'private void requestProjectionPermission()', 'private void scheduleBubbleRestoreAfterPermission()').includes('MainActivity.class'));
check('MainActivity has no MediaProjection lifecycle responsibility', !fs.readFileSync('android/app/src/main/java/com/nvu/operacional/MainActivity.java','utf8').includes('MediaProjectionManager') && !fs.readFileSync('android/app/src/main/java/com/nvu/operacional/MainActivity.java','utf8').includes('reopenGtoWhenProjectionReady'));
check('service uses projection permission grace', service.includes('PERMISSION_RETURN_GRACE_MS') && service.includes('projectionPermissionInFlight'));
check('leaving GTO pauses screen analysis immediately without a trip transition', service.includes('pauseScreenAnalysisOutsideGto(') && service.includes('tripStateWhenAnalysisPaused = getTripState()') && service.includes('Leitura pausada · estado da viagem preservado'));
check('returning to GTO resumes the same trip state', service.includes('resumeScreenAnalysisInSameState(absenceMs)') && service.includes('Returning to GTO is never a journey transition') && service.includes('Leitura retomada no GTO · estado preservado'));
check('known third-party foreground cannot be overridden by freight-like pixels', service.includes('boolean visualBridgeAllowed = visualGtoProofFresh') && service.includes('(packageUnknown || ownPermissionReturnBridge)') && service.includes('getPackageName().equals(foregroundPackage)'));
check('bubble taps are debounced', service.includes('BUBBLE_TAP_DEBOUNCE_MS') && service.includes('lastBubbleTapAt'));

const bubbleBlock = methodBody(service, 'private void showBubbleIfAllowed()', 'private void updateFreightTouchPulseSensor()');
check('interactive bubble never watches outside touches', !bubbleBlock.includes('FLAG_WATCH_OUTSIDE_TOUCH'));
check('independent 1px pulse sensor watches outside touches', service.includes('private void showFreightTouchPulseSensor()') && service.includes('FLAG_WATCH_OUTSIDE_TOUCH') && service.includes('new WindowManager.LayoutParams(\n            1,\n            1,'));
check('pulse sensor is an independent outside-touch observer', service.includes('FLAG_WATCH_OUTSIDE_TOUCH') && service.includes('queueFreightTouchMarker(event);') && service.includes('queueFreightTouchMarker(MotionEvent sourceEvent)'));
check('touch marker is serialized on capture handler', methodBody(service, 'private void queueFreightTouchMarker(MotionEvent sourceEvent)', 'private void toggleMenu()').includes('captureHandler.post'));
check('sensor removal does not clear selection engine', !methodBody(service, 'private void hideFreightTouchPulseSensor()', 'private void armResultTouchFallbackReady').includes('clearFastTouchPulse'));
check('frame correlation uses monotonic sequence coordinator', service.includes('selectionCoordinator.onFrameProcessed()') && service.includes('selectionCoordinator.isPostTouch(sequence)') && selectionCoordinator.includes('touchMarkerSequence'));
check('WAITING_FREIGHT preserves ordered selection frames', methodBody(service, 'private void onFreightFrameAvailable', 'private FreightSelectionTransaction buildSelectionTransaction').includes('GtoDeterministicFlowPolicy.useOrderedFreightFrames(getTripState())') && methodBody(service, 'private void onFreightFrameAvailable', 'private FreightSelectionTransaction buildSelectionTransaction').includes('reader.acquireNextImage()'));
check('non-waiting capture keeps latest-frame backpressure', methodBody(service, 'private void onImageAvailable', 'private FreightSelectionTransaction buildSelectionTransaction').includes('image = reader.acquireLatestImage()'));
check('selection snapshot is immutable before state transition', service.includes('FreightSelectionTransaction transaction = takePendingSelectionTransaction()') && service.indexOf('FreightSelectionTransaction transaction = takePendingSelectionTransaction()') < service.indexOf('setTripState(STATE_CONFIRMING_FREIGHT, "Frete identificado · validando dados")'));
check('transaction owns copied bitmap and geometry', service.includes('sourcePanel.copy(Bitmap.Config.ARGB_8888, false)') && service.includes('private static final class FreightSelectionTransaction'));
check('pulse sensor is hidden with overlays', methodBody(service, 'private void hideOverlays()', 'private int overlayType()').includes('hideFreightTouchPulseSensor()'));

check('projection uses default display capture', permission.includes('createConfigForDefaultDisplay()'));
check('projection handles captured-content resize', service.includes('onCapturedContentResize') && service.includes('resizeProjectionSurface'));
check('trip result OCR uses simple semantic gate instead of pixel/color signature', !service.includes('tripResultCandidate = resultVisualGate.looksLikeResultDialog')
  && service.includes('GtoSimpleScreenDetectionPolicy.isCompletedResult')
  && service.includes('Concluído + monetary value'));
check('trip result OCR keeps a slow fallback', service.includes('ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS')
  && service.includes('tripFallbackOcrDue'));
check('active trip treats a returned visual list as candidate and replaces only after new human Accept', service.includes('handleActiveTripFreightListEvidence')
  && service.includes('FREIGHT_LIST_CANDIDATE_AFTER_TRIP')
  && service.includes('replacementFreightTouchPending')
  && service.includes('mayReplaceCancelledTripOnNewAccept'));
check('no audio capture permission/code', !manifest.includes('RECORD_AUDIO') && !service.includes('AudioRecord') && !service.includes('MediaRecorder') && !service.includes('AudioPlaybackCapture'));
check('plugin capture request only defers consent to confirmed GTO', methodBody(plugin, 'public void requestScreenCapture', 'public void setContext').includes('requestProjectionPermissionIfRunning') && methodBody(plugin, 'public void requestScreenCapture', 'public void setContext').includes('projectionPermissionDeferredToGto') && !methodBody(plugin, 'public void requestScreenCapture', 'public void setContext').includes('startActivityForResult'));
check('no accessibility service remains', !manifest.includes('accessibilityservice') && !manifest.includes('BIND_ACCESSIBILITY_SERVICE'));

check('fast freight path orders all WAITING frames independent of OEM outside-touch delivery', service.includes('GtoDeterministicFlowPolicy.useOrderedFreightFrames(getTripState())') && service.includes('? reader.acquireNextImage()') && service.includes(': reader.acquireLatestImage()') && !methodBody(service, 'private void onFreightFrameAvailable', 'private FreightSelectionTransaction buildSelectionTransaction').includes('selectionCoordinator.isCriticalWindow() ?'));
check('fast freight detector is OCR-free', fastDetector.includes('detectPressedRow') && !fastDetector.includes('TextRecognizer') && !fastDetector.includes('Accessibility'));
check('touch-window detector uses relaxed post-touch scoring', fastDetector.includes('detectPressedRowAfterTouch') && fastDetector.includes('othersMean * 1.30f'));
check('touch-before-first-list never guesses a row from one frame', !fastDetector.includes('detectPressedRowFromSingleFrame') && service.includes('fastTouchBaseline = baselineRecord == null ? null : baselineRecord.frame') && service.includes('retrospectiveFastTouchCandidate()'));
check('temporarily dark pressed button is handled before list validity', fastDetector.indexOf('baseline.buttons.size() != current.buttons.size()') < fastDetector.indexOf('if (!current.hasFreightList()) return null') && service.includes('transientMissing'));
check('pre-touch baseline rejects pressed-frame contamination', service.includes('fastBaselineQuality') && service.includes('cleanest same-page baseline'));
check('ultra-fast taps have retrospective frame recovery', service.includes('retrospectiveFastTouchCandidate()') && service.includes('fastFrameHistory'));
check('pending selection is not cleared by pageChanged', !methodBody(service, 'private void onFreightFrameAvailable', 'private void armFastVisualSelection').includes('pageChanged && fastPendingSelectedRow'));
check('selected OCR prefers clean pre-touch snapshot', methodBody(service, 'private void armFastVisualSelection', 'private void finalizeFastVisualSelection').includes('Prefer the clean pre-touch page snapshot'));
check('freight page snapshot is populated in fast path', service.includes('cacheFastFreightPanel(image, current, now)'));
check('freight page text is pre-read before selection', service.includes('scheduleFreightPageOcr') && service.includes('freightPageGeneration') && service.includes('parseFreightOptions(lines, buttonCopy)'));
check('stale page OCR cannot overwrite current page', service.includes('generation != freightPageGeneration'));
check('different freight pages cannot reuse old snapshot', fastDetector.includes('< 0.024f') && service.includes('freightPageGeneration++'));
check('origin company has geometric OCR fallback', service.includes('inferOriginCompanyFromMlLine') && service.includes('knownGtoOriginCompanies'));
check('cancel button is trip-only', service.includes('if (STATE_TRIP_IN_PROGRESS.equals(state)) {\n                Button cancel = menuButton("Cancelar viagem")'));
check('in-progress trip survives temporary exit/process restart while completed unsynced trip is recoverable', service.includes('recoverActiveTrip') && service.includes('hasFreshDurableSession') && service.includes('ACTIVE_SESSION_STALE_MS') && service.includes('recoverCompletedTrip') && service.includes('retomando sincronização NVU'));
check('result refresh is fast', service.includes('STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) return 90L'));

const failed = checks.filter(c => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} checks passed.`);
if (failed.length) process.exit(1);
