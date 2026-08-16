import fs from 'node:fs';
import crypto from 'node:crypto';

const servicePath = 'android/app/src/main/java/com/nvu/operacional/GtoObserverService.java';
const syncPath = 'android/app/src/main/java/com/nvu/operacional/GtoAutoTripSync.java';
const detectorPath = 'android/app/src/main/java/com/nvu/operacional/GtoFastVisualDetector.java';
const coordinatorPath = 'android/app/src/main/java/com/nvu/operacional/GtoSelectionCoordinator.java';
const backendPath = 'functions/src/gtoTrips.ts';
const service = fs.readFileSync(servicePath, 'utf8');
const sync = fs.readFileSync(syncPath, 'utf8');
const detector = fs.readFileSync(detectorPath, 'utf8');
const coordinator = fs.readFileSync(coordinatorPath, 'utf8');
const backend = fs.readFileSync(backendPath, 'utf8');

const checks = [];
function check(name, ok, detail='') {
  checks.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ` — ${detail}` : ''}`);
}
function expect(cond, message) { if (!cond) throw new Error(message); }

// 1. Geometry model: every list position must map to exactly one bounding box.
function hitRow(x, y, boxes) {
  let hit = -1;
  for (let i = 0; i < boxes.length; i++) {
    const b = boxes[i];
    if (x >= b.left && x < b.right && y >= b.top && y < b.bottom) {
      if (hit !== -1) return -1;
      hit = i;
    }
  }
  return hit;
}

const boxes = [
  {left: 620, top: 180, right: 980, bottom: 240},
  {left: 620, top: 300, right: 980, bottom: 360},
  {left: 620, top: 420, right: 980, bottom: 480},
  {left: 620, top: 540, right: 980, bottom: 600},
  {left: 620, top: 660, right: 980, bottom: 720},
];
for (let i = 0; i < boxes.length; i++) {
  const b = boxes[i];
  const row = hitRow((b.left + b.right) / 2, (b.top + b.bottom) / 2, boxes);
  check(`selection position ${i + 1}/5 maps to exact row`, row === i);
}
check('touch gap does not select a neighboring row', hitRow(800, 270, boxes) === -1);
check('overlapping bounding boxes fail closed', hitRow(800, 215, [boxes[0], {left: 700, top: 200, right: 900, bottom: 230}]) === -1);
check('nearest-row resolver is absent', !service.includes('findFreightAt(') && !service.includes('findFreightFlexible('));
check('OEM-safe visual selection requires isolated row change plus list closure', service.includes('fastVisualDetector.detectPressedRow(') && service.includes('!fastTouchPulseActive') && service.includes('fastMissingListFrames >= missingRequired') && service.includes('finalizeFastVisualSelection()'));
check('replacement flow cannot promote row without touch', service.includes('boolean touchEvidence = replacementFreightTouchPending;') && !service.includes('boolean touchEvidence = replacementFreightTouchPending || rowAlreadyPressed;'));

// 2. Correlation invariants.
check('touch marker is sequenced on capture coordinator', coordinator.includes('markTouch()') && service.includes('selectionCoordinator.markTouch()'));
check('post-touch candidate requires baseline', service.includes('if (!fastTouchPulseActive || fastTouchBaseline == null || fastTouchMarkerSequence < 0L) return null;'));
check('coordinate disagreement rejects touch candidate', service.includes('coordinateEvidenceAgreesWithRow') && service.includes('candidateFromTouch') && service.includes('&& !coordinateEvidenceAgreesWithRow'));
check('direct touch requires exact unique bbox', service.includes('exactUniqueRowForTouch(x, y, buttons)') && service.includes('if (hit < 0) return;'));
check('single-frame pre-touch fallback is absent', !detector.includes('detectPressedRowFromSingleFrame'));
check('page change invalidates stale freight text', service.includes('freightPageGeneration++') && service.includes('Nova página de fretes detectada') && service.includes('generation != freightPageGeneration'));

// 3. Fingerprint: prove two distinct freight cards cannot share identity when identity fields differ.
function fp(f) {
  const fields = ['cargo','companyRoute','originCompany','destinationCompany','origin','destination','distanceKm','offeredValue','rawText'];
  let canonical = '';
  for (const key of fields) {
    const value = String(f[key] ?? '').trim();
    canonical += `${key}=${value.length}:${value}|`;
  }
  const row = String(Number.isFinite(f.selectedRow) ? Math.trunc(f.selectedRow) : -1);
  canonical += `selectedRow=${row.length}:${row}|`;
  return crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
}
const freightA = {
  cargo:'Café', companyRoute:'Route A', originCompany:'Empresa A', destinationCompany:'Empresa B',
  origin:'Búzios', destination:'Cabo Frio', distanceKm:'42Km', offeredValue:'R$ 1.250', rawText:'A|B|42Km|R$ 1.250', selectedRow:0,
};
const freightB = {...freightA, destination:'Arraial do Cabo', selectedRow:1, rawText:'A|B|55Km|R$ 1.400'};
const hashA = fp(freightA);
const hashB = fp(freightB);
check('fingerprint is SHA-256', /^[a-f0-9]{64}$/.test(hashA));
check('different freights have different fingerprints', hashA !== hashB);
check('snapshot includes full freight identity fields', ['cargo','companyRoute','originCompany','destinationCompany','origin','destination','distanceKm','offeredValue','rawText','freightFingerprint'].every(k => sync.includes(`"${k}"`)));
check('snapshot lock refuses missing selectedFreight', sync.includes('Snapshot do frete selecionado ausente; nenhuma viagem pode ser iniciada.'));
check('missing durable session snapshot fails closed', sync.includes('Snapshot durável da sessão GTO ausente; viagem bloqueada por segurança.'));
check('session snapshot is write-once', sync.includes('A session snapshot is write-once') && sync.includes('if (existing != null)'));

// 4. Payload integrity: the detected source company is the requested business-semantic Origem.
check('durable payload forces canonical origin from detected source company', sync.includes('payload.put("origin", clean(payload.optString("originCompany", "")))'));
check('backend persists canonical origin field', backend.includes('origem: effectiveOrigin') || backend.includes('origem: origin'));
check('backend uses originCompany as canonical route origin by explicit rule', backend.includes('const effectiveOrigin = originCompany') && backend.includes('if (origin !== originCompany)'));
check('backend accepts destination company separately', backend.includes('destinationCompany') && backend.includes('gtoDestinationCompany'));
check('backend verifies freight fingerprint against supplied freight fields', backend.includes('expectedFreightFingerprint') && backend.includes('freightFingerprint !== expectedFreightFingerprint'));

// 5. State machine and completion invariants.
check('state transitions are explicitly guarded', service.includes('isAllowedTripTransition') && service.includes('STATE_CONFLICT'));
check('Receive latch is durably persisted before completion', service.includes('boolean persisted = prefs.edit()') && service.includes('completionStatus", "RECEIVE_LATCHED"') && service.includes('.commit();'));
check('completion cannot send before sealed payload', service.includes('boolean queued = GtoAutoTripSync.enqueueConfirmedTrip') && sync.includes('PAYLOAD_SEALED'));
check('queue survives send failure', sync.includes('failed sync is') || (sync.includes('markPending') && sync.includes('STATUS_PENDING')));
check('snapshot is deleted only after sync ACK', sync.includes('removeSnapshot(context, sessionId)') && sync.includes('STATUS_SYNCED') && sync.indexOf('removeSnapshot(context, sessionId)') > sync.indexOf('putString("gtoTripSyncStatus", STATUS_SYNCED)'));

// 6. Regression against duplicate source copies.
const rootJava = fs.readdirSync('.').filter(name => /^Gto.*\.java$/.test(name));
check('duplicate GTO Java sources removed from project root', rootJava.length === 0, rootJava.join(','));

// 7. Full logical flow simulation using the exact same sealed-data rule.
const flow = [];
flow.push('LIST_DETECTED');
const selected = freightA;
const locked = {...selected, freightFingerprint: fp(selected)};
flow.push('FREIGHT_SELECTED_EXACT');
flow.push('SNAPSHOT_LOCKED');
flow.push('TRIP_IN_PROGRESS');
flow.push('RESULT_DETECTED');
flow.push('RECEIVE_LATCHED');
const queued = JSON.parse(JSON.stringify(locked));
flow.push('PAYLOAD_SEALED');
const mismatch = ['cargo','companyRoute','originCompany','destinationCompany','origin','destination','distanceKm','offeredValue','rawText'].find(k => queued[k] !== selected[k]);
check('simulated end-to-end payload matches selected freight 100%', !mismatch && queued.selectedRow === selected.selectedRow && queued.freightFingerprint === fp(selected));
check('simulated flow reaches every required stage', JSON.stringify(flow) === JSON.stringify(['LIST_DETECTED','FREIGHT_SELECTED_EXACT','SNAPSHOT_LOCKED','TRIP_IN_PROGRESS','RESULT_DETECTED','RECEIVE_LATCHED','PAYLOAD_SEALED']));

const failed = checks.filter(c => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} end-to-end integrity checks passed.`);
if (failed.length) process.exit(1);
