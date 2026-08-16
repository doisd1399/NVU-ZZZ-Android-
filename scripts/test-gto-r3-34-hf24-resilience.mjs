import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const read = p => fs.readFileSync(p, "utf8");
const service = read("android/app/src/main/java/com/nvu/operacional/GtoObserverService.java");
const gradle = read("android/app/build.gradle");
const workflow = read(".github/workflows/build-android-release.yml");
const lifecycle = read("scripts/audit-gto-r2-lifecycle.mjs");
const checks = [];
const check = (name, ok) => { checks.push({name, ok: !!ok}); console.log(`${ok ? "PASS" : "FAIL"} ${name}`); };

const currentCode = Number((gradle.match(/versionCode\s+(\d+)/)||[])[1]||0);
const currentPatch = Number((gradle.match(/versionName\s+"1\.0\.(\d+)"/)||[])[1]||0);
check("HF24 baseline identity is preserved or advanced", currentCode >= 76 && currentPatch >= 76);
check("release workflow remains aligned to current Android identity", workflow.includes(`EXPECTED_VERSION_CODE: "${currentCode}"`) && workflow.includes(`EXPECTED_VERSION_NAME: "1.0.${currentPatch}"`));
check("clean checkout lifecycle audit no longer requires generated Capacitor assets", !lifecycle.includes("readdirSync('android/app/src/main/assets/public/assets')"));
check("certificate parser accepts modern apksigner V3 output", workflow.includes("certificate SHA-256 digest:[[:space:]]"));
check("drag outside GTO exposes remove target", service.includes("GtoBubbleDismissPolicy.shouldShowRemoveTarget(gtoForeground, true)") && service.includes("Remover e parar NVU"));
check("dropping on remove target stops observer", service.includes("isBubbleDroppedOnRemoveTarget()") && service.includes("stopObserverFromFloatingBubble()") && service.includes("ACTION_STOP"));
check("remove target is disabled inside GTO", service.includes("if (gtoForeground || windowManager == null || bubbleRemoveTargetView != null) return;"));
check("capture loss can auto-request fresh authorization", service.includes("ensureProjectionAuthorizationIfNeeded(now)") && service.includes("AUTO_REAUTH_AFTER_CAPTURE_LOSS"));
check("dead surface escalates after repeated recovery", service.includes("PROJECTION_SURFACE_REAUTH_ESCALATION_ATTEMPTS") && service.includes("escalateProjectionToFreshAuthorization"));
check("explicit permission denial suppresses prompt loop", service.includes('putBoolean("projectionReauthAutoAllowed", false)'));
check("selected-row OCR remains primary", service.includes("immutable pre-touch row is a first-class evidence source") && service.includes("can never overwrite a valid precise read"));
check("single frozen same-row evidence may fill a missing field", service.includes("GtoFrozenFreightFallbackPolicy.canUse") && service.includes("FROZEN_TOUCH_BASELINE"));
check("destination company remains optional", service.includes("destinationCompany remains optional metadata and never creates REVIEW_REQUIRED"));

const temp = fs.mkdtempSync(path.join(os.tmpdir(), "nvu-hf24-"));
const sources = [
  "android/app/src/main/java/com/nvu/operacional/GtoMoneyValue.java",
  "android/app/src/main/java/com/nvu/operacional/GtoFreightReviewPolicy.java",
  "android/app/src/main/java/com/nvu/operacional/GtoFrozenFreightFallbackPolicy.java",
  "android/app/src/main/java/com/nvu/operacional/GtoProjectionRecoveryPolicy.java",
  "android/app/src/main/java/com/nvu/operacional/GtoBubbleDismissPolicy.java",
  "scripts/java-tests/com/nvu/operacional/GtoHf24ResilienceProbe.java",
];
let r = spawnSync("javac", ["-encoding", "UTF-8", "-d", temp, ...sources], {encoding:"utf8"});
if (r.stdout) process.stdout.write(r.stdout); if (r.stderr) process.stderr.write(r.stderr);
check("HF24 pure resilience probe compiles", r.status === 0);
if (r.status === 0) {
  r = spawnSync("java", ["-cp", temp, "com.nvu.operacional.GtoHf24ResilienceProbe"], {encoding:"utf8"});
  if (r.stdout) process.stdout.write(r.stdout); if (r.stderr) process.stderr.write(r.stderr);
  check("five freight fields + projection recovery + remove gesture policies pass", r.status === 0);
}
fs.rmSync(temp, {recursive:true, force:true});

// Re-run the existing 5-freight end-to-end model as a production gate.
r = spawnSync("node", ["scripts/test-gto-r3-34-hf23-production-certification.mjs"], {encoding:"utf8"});
if (r.stdout) process.stdout.write(r.stdout); if (r.stderr) process.stderr.write(r.stderr);
check("existing five-freight full flow remains green", r.status === 0);

const failed = checks.filter(x => !x.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} HF24 resilience checks passed.`);
if (failed.length) process.exit(1);
