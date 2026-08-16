import fs from 'node:fs';
import assert from 'node:assert/strict';

const file = 'android/app/src/main/java/com/nvu/operacional/GtoObserverService.java';
const src = fs.readFileSync(file, 'utf8');
const gradle = fs.readFileSync('android/app/build.gradle', 'utf8');

const checks = [
  ['explicit freight lifecycle fields', /freightListCycleSeen\s*=\s*false/],
  ['reopen lifecycle handler', /onFreightListVisibleAgain\(long now\)/],
  ['closed lifecycle handler', /markFreightListClosed\(long now\)/],
  ['new session retry handler', /restartWaitingFreightSelectionSession\(String reason\)/],
  ['new session generated on reopen', /GtoAutoTripSync\.newSessionId\(\)/],
  ['old snapshot discarded before retry', /GtoAutoTripSync\.discardSessionSnapshot\(this, previousSessionId\)/],
  ['old analysis cleared before retry', /clearTripAnalysis\(\);\n\n        String newSessionId/],
  ['projection permission blocks lifecycle transition', /if \(projectionPermissionInFlight\) return;/],
  ['projection restart clears only transient missing counters', /restarting MediaProjection is a technical capture transition/],
  ['version >=48', /versionCode\s+(?:4[8-9]|[5-9]\d|\d{3,})/],
  ['version 1.0.48', /versionName "1.0.48"/]
];

for (const [name, pattern] of checks) {
  assert.match(name.startsWith('version') ? gradle : src, pattern, `Missing R3.16 requirement: ${name}`);
  console.log(`PASS ${name}`);
}
console.log('GTO R3.16 freight reopen/projection lifecycle audit: PASS');
