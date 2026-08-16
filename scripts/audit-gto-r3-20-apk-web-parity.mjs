import fs from 'node:fs';

const files = {
  service: 'android/app/src/main/java/com/nvu/operacional/GtoObserverService.java',
  sync: 'android/app/src/main/java/com/nvu/operacional/GtoAutoTripSync.java',
  plugin: 'android/app/src/main/java/com/nvu/operacional/GtoObserverPlugin.java',
  webObserver: 'src/lib/gtoObserver.ts',
  webCanonical: 'src/services/gtoCanonicalState.ts',
  webHook: 'src/hooks/useGtoCanonicalState.ts',
  backendState: 'functions/src/gtoState.ts',
  backendTrip: 'functions/src/gtoTrips.ts',
  index: 'functions/src/index.ts',
  rules: 'firestore.rules',
  package: 'package.json',
};
const text = Object.fromEntries(Object.entries(files).map(([k,p]) => [k,fs.readFileSync(p,'utf8')]));
const checks=[];
const check=(name,ok,detail='')=>{checks.push({name,ok,detail});console.log(`${ok?'PASS':'FAIL'} ${name}${detail?' — '+detail:''}`)};

const states = ['IDLE','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS','RESULT_DETECTED','AWAITING_BONUS_VALIDATION','RESULT_CONFIRMED','REJECTED_BONUS','CANCELLED'];
const stateGraph = {
  IDLE:['WAITING_FREIGHT','CANCELLED'], WAITING_FREIGHT:['CONFIRMING_FREIGHT','CANCELLED','IDLE'],
  CONFIRMING_FREIGHT:['TRIP_IN_PROGRESS','WAITING_FREIGHT','CANCELLED','IDLE'],
  TRIP_IN_PROGRESS:['RESULT_DETECTED','REJECTED_BONUS','WAITING_FREIGHT','CANCELLED'],
  RESULT_DETECTED:['AWAITING_BONUS_VALIDATION','RESULT_CONFIRMED','REJECTED_BONUS','WAITING_FREIGHT','CANCELLED'],
  AWAITING_BONUS_VALIDATION:['RESULT_CONFIRMED','REJECTED_BONUS','RESULT_DETECTED','WAITING_FREIGHT','CANCELLED'],
  RESULT_CONFIRMED:['IDLE','WAITING_FREIGHT','CANCELLED'], REJECTED_BONUS:['IDLE','WAITING_FREIGHT','CANCELLED'], CANCELLED:['IDLE','WAITING_FREIGHT']
};
for (const s of states) check(`state ${s} is declared in Web/native canonical set`, text.service.includes(`STATE_${s.replaceAll('AWAITING_BONUS_VALIDATION','AWAITING_BONUS')}`) || text.webCanonical.includes(`"${s}"`) && text.backendState.includes(`"${s}"`));
check('Web exposes canonical state reader', text.webCanonical.includes('subscribeToGtoCanonicalSession') && text.webHook.includes('useGtoCanonicalState'));
check('server canonical state callable is exported', text.index.includes('syncGtoTripState') && text.backendState.includes('https.onCall'));
check('server validates authenticated driver ownership', text.backendState.includes('driverId !== uid'));
check('server has one canonical transition graph', Object.keys(stateGraph).every(s => text.backendState.includes(`${s}: new Set`)));
check('server rejects impossible transitions', text.backendState.includes('Transição remota inválida'));
check('new WAITING session retires prior active session', text.backendState.includes('REPLACED_BY_NEW_FREIGHT_SESSION') && text.backendState.includes('activeSessionId'));
check('active pointer avoids composite Firestore query/index dependency', text.backendState.includes('gto_active_gto_sessions') && text.webCanonical.includes('gto_active_gto_sessions'));
check('Firestore exposes read-only canonical state to owner', text.rules.includes('match /gto_active_gto_sessions/{driverId}') && text.rules.includes('allow write: if false'));
check('APK publishes every accepted state transition', text.service.includes('GtoAutoTripSync.syncCanonicalState(this, prefs, previous, state, event)'));
check('APK retries canonical state publication after transient failure', text.service.includes('retryCanonicalStateSync') && text.service.includes('gtoCanonicalStatePending'));
check('CONFIRMING_FREIGHT is isolated from reopened-list replacement', text.service.includes('GtoDeterministicFlowPolicy.mayObserveFreightListOutsideWaiting(state)') && !/mayObserveFreightListOutsideWaiting[\s\S]{0,500}\"CONFIRMING_FREIGHT\"/.test(fs.readFileSync('android/app/src/main/java/com/nvu/operacional/GtoDeterministicFlowPolicy.java','utf8')));
check('explicit replacement still retires old durable freight snapshot only through guarded promotion', text.service.includes('isExplicitFreightReplacementActive') && text.service.includes('promoteReplacementFreightCandidateToWaiting') && text.service.includes('GtoAutoTripSync.discardSessionSnapshot(this, cancelledSessionId)'));
check('selection is still fail-closed', text.service.includes('coordinateEvidenceAgreesWithRow') && !text.service.includes('findFreightAt(') && !text.service.includes('findFreightFlexible('));
check('snapshot identity remains server-verifiable', text.sync.includes('freightFingerprint') && text.backendTrip.includes('expectedFreightFingerprint'));
check('no stale root-level GTO Java implementation', !fs.readdirSync('.').some(n => /^Gto.*\.java$/.test(n)));

// Pure state-machine simulations required by the user.
const transition = (from,to) => from === to || (stateGraph[from]||[]).includes(to);
const scenarios = [
  ['normal','IDLE','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS','RESULT_DETECTED','AWAITING_BONUS_VALIDATION','RESULT_CONFIRMED'],
  ['cancel-reselect','IDLE','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS'],
  ['select-no-start-reselect','IDLE','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS','WAITING_FREIGHT'],
  ['restart-in-progress','IDLE','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS','TRIP_IN_PROGRESS'],
  ['old-inconsistent-recovered','TRIP_IN_PROGRESS','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS'],
  ['abandoned-never-send','IDLE','WAITING_FREIGHT','CONFIRMING_FREIGHT','TRIP_IN_PROGRESS','CANCELLED'],
];
for (const [name,...path] of scenarios) {
  let ok=true;
  for(let i=1;i<path.length;i++) if(!transition(path[i-1],path[i])) ok=false;
  check(`scenario ${name} has no invalid transition`, ok, path.join(' -> '));
}

// Cross-layer golden contract.
for (const field of ['sessionId','driverId','companyId','jobId','selectedRow']) {
  check(`state contract field ${field} exists across APK/backend`, text.sync.includes(field) && text.backendState.includes(field));
}
check('state source is explicitly canonical Firestore session, not Web localStorage', text.webCanonical.includes('gto_active_gto_sessions') && !text.webCanonical.includes('localStorage'));

const failed=checks.filter(c=>!c.ok);
console.log(`\n${checks.length-failed.length}/${checks.length} APK×Web parity checks passed.`);
if(failed.length){console.error('\nFailures:');failed.forEach(f=>console.error('- '+f.name+(f.detail?' — '+f.detail:'')));process.exit(1);}
