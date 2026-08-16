import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const activity = read('android/app/src/main/java/com/nvu/operacional/MainActivity.java');
const service = read('android/app/src/main/java/com/nvu/operacional/GtoObserverService.java');
const plugin = read('android/app/src/main/java/com/nvu/operacional/GtoObserverPlugin.java');

let passed = 0;
let failed = 0;
function check(name, ok) {
  if (ok) {
    passed++;
    console.log(`PASS ${name}`);
  } else {
    failed++;
    console.error(`FAIL ${name}`);
  }
}

const startProjectionStart = service.indexOf('private void startProjection(int resultCode, Intent resultData)');
const startProjectionEnd = service.indexOf('private void resizeProjectionSurface', startProjectionStart);
const startProjectionBody = service.slice(startProjectionStart, startProjectionEnd);
const createSurfaceStart = service.indexOf('private void createProjectionSurface(int width, int height)');
const createSurfaceEnd = service.indexOf('private void startProjection', createSurfaceStart);
const createSurfaceBody = service.slice(createSurfaceStart, createSurfaceEnd);
const pendingSurfaceStart = service.indexOf('private void maybeStartPendingProjectionSurface(long now)');
const pendingSurfaceEnd = service.indexOf('private void createProjectionSurface', pendingSurfaceStart);
const pendingSurfaceBody = service.slice(pendingSurfaceStart, pendingSurfaceEnd);
const prepareStart = service.indexOf('private void prepareCaptureForGtoLaunch()');
const prepareEnd = service.indexOf('private void resetCaptureStabilityBarrier', prepareStart);
const prepareBody = service.slice(prepareStart, prepareEnd);
const continuityStart = service.indexOf('private void ensureCaptureContinuityAfterGtoReturn()');
const continuityEnd = service.indexOf('private void refreshTransientVisualContextAfterGtoReturn', continuityStart);
const continuityBody = service.slice(continuityStart, continuityEnd);

check('permission host requests landscape before BridgeActivity UI',
  activity.indexOf('prepareProjectionHostOrientation(getIntent());') < activity.indexOf('super.onCreate(savedInstanceState);'));
check('singleTask permission recovery requests landscape before onNewIntent work',
  activity.indexOf('prepareProjectionHostOrientation(intent);') < activity.indexOf('super.onNewIntent(intent);'));
check('consent is gated by actual landscape geometry',
  activity.includes('isProjectionHostActuallyLandscape()') && activity.includes('metrics.widthPixels > metrics.heightPixels'));
check('portrait fallback was removed',
  !activity.includes('CONSENT_VISIBLE_APP_FALLBACK') && activity.includes('CONSENT_LANDSCAPE_TIMEOUT'));
check('no blind 15-second delay controls consent',
  !activity.includes('15000L') && !service.includes('15000L'));
check('initial consent waits for GTO landscape stability',
  service.includes('isLandscapeStableForProjectionConsent(now)') && service.includes('WAITING_GTO_LANDSCAPE_FOR_PERMISSION'));
check('main bubble remains attached while consent launches',
  service.includes('suspendInteractiveOverlaysKeepBubble();'));
check('MediaProjection token binding does not create VirtualDisplay',
  startProjectionBody.length > 0 && !startProjectionBody.includes('projection.createVirtualDisplay('));
check('exact VirtualDisplay creation is isolated in landscape surface phase',
  (createSurfaceBody.match(/createVirtualDisplay\(/g) || []).length === 1);
check('surface creation requires positive GTO foreground package',
  pendingSurfaceBody.includes('boolean packageMatchesGto = GTO_PACKAGE.equals(foregroundPackage);')
    && pendingSurfaceBody.includes('if (!packageMatchesGto'));
check('surface creation requires landscape',
  pendingSurfaceBody.includes('width <= height'));
check('surface creation requires multiple stable polls',
  pendingSurfaceBody.includes('PROJECTION_SURFACE_STABLE_POLLS')
    && pendingSurfaceBody.includes('PROJECTION_SURFACE_LANDSCAPE_SETTLE_MS'));
check('granted token returns to GTO before projectionActive',
  activity.includes('boolean sessionBound = prefs.getBoolean("projectionSessionBound", false);')
    && activity.includes('readyToReturn = active')
    && activity.includes('|| sessionBound'));
check('pending token cannot arm another consent during GTO launch',
  prepareBody.includes('if (projectionSurfacePending && mediaProjection != null)'));
check('pending token cannot be torn down as false continuity failure',
  continuityBody.includes('if (projectionSurfacePending && mediaProjection != null)')
    && continuityBody.includes('WAITING_GTO_GEOMETRY'));
check('permission request functions reject duplicate pending session',
  service.includes('if (projectionPermissionInFlight || projectionActive || projectionSurfacePending) return;'));
check('MediaProjection onStop before surface is diagnostic and reauths only then',
  service.includes('STOPPED_BEFORE_SURFACE') && service.includes('projectionReauthRequired", true'));
check('plugin treats bound/pending projection as accepted instead of timeout',
  plugin.includes('boolean sessionBound = Boolean.TRUE.equals(status.getBool("projectionSessionBound"));')
    && plugin.includes('active || sessionBound || surfacePending || terminal'));
check('native status exposes two-phase projection state',
  plugin.includes('status.put("projectionSessionBound"') && plugin.includes('status.put("projectionSurfacePending"'));

// Deterministic timeline model for the HF5 two-phase gate. It deliberately exercises
// portrait -> landscape consent, token bound -> GTO return, and one-shot surface creation.
class GateModel {
  constructor() {
    this.consentLandscapeSince = 0;
    this.surfaceSince = 0;
    this.surfacePolls = 0;
    this.surfaceW = 0;
    this.surfaceH = 0;
    this.tokenBound = false;
    this.surfaceCreated = 0;
  }
  mayAskConsent(now, isGto, w, h) {
    if (!isGto || w <= h) { this.consentLandscapeSince = 0; return false; }
    if (!this.consentLandscapeSince) { this.consentLandscapeSince = now; return false; }
    return now - this.consentLandscapeSince >= 420;
  }
  grant() { this.tokenBound = true; }
  mayCreateSurface(now, foregroundPackageIsGto, w, h) {
    if (!this.tokenBound || this.surfaceCreated || !foregroundPackageIsGto || w <= h) {
      this.surfaceSince = 0; this.surfacePolls = 0; this.surfaceW = 0; this.surfaceH = 0; return false;
    }
    if (this.surfaceW !== w || this.surfaceH !== h) {
      this.surfaceW = w; this.surfaceH = h; this.surfaceSince = now; this.surfacePolls = 1; return false;
    }
    this.surfacePolls++;
    if (this.surfacePolls >= 3 && now - this.surfaceSince >= 560) {
      this.surfaceCreated++;
      return true;
    }
    return false;
  }
}
const m = new GateModel();
check('scenario: portrait GTO cannot trigger consent', !m.mayAskConsent(1000, true, 1080, 2400));
check('scenario: first landscape sample still waits', !m.mayAskConsent(1100, true, 2400, 1080));
check('scenario: stable landscape triggers consent without fixed 15s sleep', m.mayAskConsent(1550, true, 2400, 1080));
m.grant();
check('scenario: token cannot be spent while NVU host still foreground', !m.mayCreateSurface(1700, false, 2400, 1080));
check('scenario: first GTO landscape sample cannot create surface', !m.mayCreateSurface(1800, true, 2400, 1080));
check('scenario: second sample cannot create surface early', !m.mayCreateSurface(2050, true, 2400, 1080));
check('scenario: stable third+ sample creates one surface', m.mayCreateSurface(2400, true, 2400, 1080));
check('scenario: surface cannot be created twice from same token', !m.mayCreateSurface(2800, true, 2400, 1080) && m.surfaceCreated === 1);

console.log(`\n${passed}/${passed + failed} HF5 projection-flow checks passed.`);
if (failed) process.exit(1);
