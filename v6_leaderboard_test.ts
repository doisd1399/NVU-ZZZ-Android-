import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
  RulesTestEnvironment
} from '@firebase/rules-unit-testing';
import { readFileSync } from 'fs';
import {
  setDoc,
  getDoc,
  updateDoc,
  doc,
  collection,
  getDocs,
  runTransaction,
  writeBatch,
  query,
  orderBy,
  where
} from 'firebase/firestore';

function getDaysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate();
}

async function recalculateRanks(db: any, simulatorId: string, periodKey: string) {
  const driversRef = collection(db, `leaderboard/${simulatorId}/periods/${periodKey}/drivers`);
  const snap = await getDocs(driversRef);
  let drivers: any[] = [];
  snap.forEach((d: any) => drivers.push({ id: d.id, ...d.data() }));

  const activeDrivers = drivers.filter((d: any) => d.status === 'active');

  const [yearStr, monthStr] = periodKey.split('-');
  const runDays = getDaysInMonth(parseInt(yearStr), parseInt(monthStr));

  let globalMaxConstancy = Math.max(1, Math.ceil(runDays * 0.5));
  let maxTrips = Math.max(1, runDays * 1);
  let maxEarnings = Math.max(1, runDays * 100);

  for (const d of activeDrivers) {
    if (d.constancy > globalMaxConstancy) globalMaxConstancy = d.constancy;
    if (d.trips > maxTrips) maxTrips = d.trips;
    if (d.earnings > maxEarnings) maxEarnings = d.earnings;
  }

  activeDrivers.forEach(d => {
    const constScore = Math.min(100, Math.round((d.constancy / globalMaxConstancy) * 100)) || 0;
    const tripsScore = Math.min(100, Math.round((d.trips / maxTrips) * 100)) || 0;
    const earnScore = Math.min(100, Math.round((d.earnings / maxEarnings) * 100)) || 0;
    d.finalIndex = Math.min(100, Math.round((constScore + tripsScore + earnScore) / 3));
  });

  activeDrivers.sort((a, b) => {
    if (b.earnings !== a.earnings) return b.earnings - a.earnings;
    return b.trips - a.trips;
  });

  activeDrivers.forEach((d, i) => d.globalRank = i + 1);

  const companyGroups: Record<string, any[]> = {};
  activeDrivers.forEach(d => {
    const cid = d.companyId;
    if (!companyGroups[cid]) companyGroups[cid] = [];
    companyGroups[cid].push(d);
  });

  for (const cid in companyGroups) {
    companyGroups[cid].sort((a, b) => {
      if (b.earnings !== a.earnings) return b.earnings - a.earnings;
      return b.trips - a.trips;
    });
    companyGroups[cid].forEach((d, i) => d.internalRank = i + 1);
  }

  const newGenerationId = Date.now().toString();
  const batch = writeBatch(db);
  
  activeDrivers.forEach(d => {
    const ref = doc(db, `leaderboard/${simulatorId}/periods/${periodKey}/drivers/${d.id}`);
    batch.update(ref, {
      globalRank: d.globalRank,
      internalRank: d.internalRank,
      finalIndex: d.finalIndex,
      generationId: newGenerationId
    });
  });

  const periodRef = doc(db, `leaderboard/${simulatorId}/periods/${periodKey}`);
  batch.set(periodRef, { status: 'ready', generationId: newGenerationId }, { merge: true });

  await batch.commit();
}

async function processTrip(db: any, event: any) {
  const periodKey = event.metricDate.substring(0, 7);
  const dailyKey = event.metricDate.substring(0, 10);
  const isApproved = event.status === 'aprovada';

  await runTransaction(db, async (t: any) => {
    const ledgerRef = doc(db, `leaderboard/${event.simulatorId}/periods/${periodKey}/drivers/${event.userId}/ledger/${event.tripId}`);
    const driverRef = doc(db, `leaderboard/${event.simulatorId}/periods/${periodKey}/drivers/${event.userId}`);
    
    const [ledgerSnap, driverSnap] = await Promise.all([
      t.get(ledgerRef),
      t.get(driverRef)
    ]);

    let oldEarnings = 0, oldTrips = 0, oldDailyTrips = 0, oldMetricDate = '';
    if (ledgerSnap.exists()) {
      const data = ledgerSnap.data();
      oldEarnings = data.earnings || 0;
      oldTrips = data.trips || 0;
      oldDailyTrips = data.dailyTrips || 0;
      oldMetricDate = data.metricDate || '';
    }

    const newTripsVal = isApproved ? 1 : 0;
    const newEarningsVal = isApproved ? event.earnings : 0;
    const deltaTrips = newTripsVal - oldTrips;
    const deltaEarnings = newEarningsVal - oldEarnings;
    
    let currentDriverData = driverSnap.exists() ? driverSnap.data() : {
      earnings: 0, trips: 0, constancy: 0, dailyTripsMap: {}, userId: event.userId, companyId: event.companyId, status: 'active'
    };

    let dailyMap = currentDriverData.dailyTripsMap || {};
    
    if (oldMetricDate && oldMetricDate.substring(0, 10) !== dailyKey) {
      const oldDailyKey = oldMetricDate.substring(0, 10);
      dailyMap[oldDailyKey] = Math.max(0, (dailyMap[oldDailyKey] || 0) - oldDailyTrips);
      dailyMap[dailyKey] = Math.max(0, (dailyMap[dailyKey] || 0) + newTripsVal);
    } else {
      const deltaDailyTrips = newTripsVal - oldDailyTrips;
      dailyMap[dailyKey] = Math.max(0, (dailyMap[dailyKey] || 0) + deltaDailyTrips);
    }

    let newConstancy = Object.values(dailyMap).filter((v: any) => v > 0).length;
    let targetCompany = currentDriverData.companyId;
    if (isApproved && event.companyId) targetCompany = event.companyId;

    t.set(driverRef, {
      ...currentDriverData,
      earnings: currentDriverData.earnings + deltaEarnings,
      trips: currentDriverData.trips + deltaTrips,
      constancy: newConstancy,
      dailyTripsMap: dailyMap,
      companyId: targetCompany
    }, { merge: true });

    t.set(ledgerRef, {
      earnings: newEarningsVal, trips: newTripsVal, dailyTrips: newTripsVal, companyId: event.companyId, metricDate: event.metricDate
    }, { merge: true });

    const periodRef = doc(db, `leaderboard/${event.simulatorId}/periods/${periodKey}`);
    t.set(periodRef, { status: 'building' }, { merge: true });
  });

  await recalculateRanks(db, event.simulatorId, periodKey);
}

async function runTests() {
  const projectId = `demo-leaderboard`;
  const testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { host: '127.0.0.1', port: 8080, rules: readFileSync('firestore.rules.v6', 'utf8') }
  });

  const adminDb = testEnv.authenticatedContext('admin').firestore();

  // Pre-seed some data to test queries
  const pId = Date.now();

  console.log("\n== Teste 1: Viagem Aprovada ==");
  await processTrip(adminDb, { tripId: 'trip1', userId: 'driverA', simulatorId: 'ETS2', companyId: 'DC Transportes', earnings: 15000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z' });
  let snap = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverA'));
  console.log("Driver A:", snap.data());

  console.log("\n== Teste 2: Evento Duplicado (Idempotência) ==");
  await processTrip(adminDb, { tripId: 'trip1', userId: 'driverA', simulatorId: 'ETS2', companyId: 'DC Transportes', earnings: 15000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z' });
  snap = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverA'));
  console.log("Driver A (trips deve manter-se igual):", snap.data()?.trips);

  console.log("\n== Teste 3: Duas viagens no mesmo dia ==");
  await processTrip(adminDb, { tripId: 'trip2', userId: 'driverA', simulatorId: 'ETS2', companyId: 'DC Transportes', earnings: 20000, status: 'aprovada', metricDate: '2026-07-01T15:00:00Z' });
  snap = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverA'));
  console.log("Driver A (constancy deve ser 1):", snap.data()?.constancy);

  console.log("\n== Teste 4: Rejeição da Viagem 2 ==");
  await processTrip(adminDb, { tripId: 'trip2', userId: 'driverA', simulatorId: 'ETS2', companyId: 'DC Transportes', earnings: 20000, status: 'rejeitada', metricDate: '2026-07-01T15:00:00Z' });
  snap = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverA'));
  console.log("Driver A (earnings deve voltar para 15000):", snap.data()?.earnings);

  console.log("\n== Teste 5: Edição de Valor e Data ==");
  await processTrip(adminDb, { tripId: 'trip1', userId: 'driverA', simulatorId: 'ETS2', companyId: 'DC Transportes', earnings: 30000, status: 'aprovada', metricDate: '2026-07-05T12:00:00Z' });
  snap = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverA'));
  console.log("Driver A (earnings: 30000, dailyTripsMap movido para dia 5):", snap.data()?.dailyTripsMap);

  console.log("\n== Teste 6: Queries e Indices (Competidor B) ==");
  await processTrip(adminDb, { tripId: 'tripB1', userId: 'driverB', simulatorId: 'ETS2', companyId: 'DC Transportes', earnings: 10000, status: 'aprovada', metricDate: '2026-07-05T12:00:00Z' });
  let d1 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverA'));
  let d2 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverB'));
  console.log(`Driver A Rank: ${d1.data()?.globalRank} | Driver B Rank: ${d2.data()?.globalRank}`);
  
  // Testando order by indices query
  const qSnap = await getDocs(query(collection(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers'), where('status', '==', 'active'), orderBy('globalRank', 'asc')));
  let count = 0;
  qSnap.forEach(d => {
    count++;
    console.log(`Rank ${d.data().globalRank}: ${d.id}`);
  });
  console.log("Query Results Count:", count);

  console.log("\n== Teste 7: Motorista inativo == ");
  await updateDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverB'), { status: 'inactive' });
  await recalculateRanks(adminDb, 'ETS2', '2026-07');
  d1 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverA'));
  d2 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/driverB'));
  console.log(`Driver A Rank: ${d1.data()?.globalRank} | Driver B Rank: ${d2.data()?.globalRank} (deve ser undefined)`);

  console.log("\n== Teste 8: Firestore Rules ==");
  await setDoc(doc(adminDb, 'users/user1'), { simulators: ['ETS2'] });
  await setDoc(doc(adminDb, 'users/user2'), { simulators: ['ATS'] });
  
  const authUser1 = testEnv.authenticatedContext('user1').firestore();
  const authUser2 = testEnv.authenticatedContext('user2').firestore();

  await assertSucceeds(getDoc(doc(authUser1, 'leaderboard/ETS2/periods/2026-07')));
  console.log("-> User 1 leu ETS2 com sucesso");

  await assertFails(getDoc(doc(authUser2, 'leaderboard/ETS2/periods/2026-07')));
  console.log("-> User 2 foi bloqueado em ETS2");
  
  const pDoc = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07'));
  console.log("\n== Teste 9: Atomic Swap Generation ID ==");
  console.log("Period status:", pDoc.data()?.status, "Generation ID:", pDoc.data()?.generationId);
  console.log("Driver A Generation ID:", d1.data()?.generationId);

  await testEnv.cleanup();
}

runTests().catch(console.error);
