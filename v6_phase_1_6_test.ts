import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
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
  writeBatch
} from 'firebase/firestore';

function getDaysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate();
}

function calculateScores(activeDrivers: any[], runDays: number) {
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
    d.finalIndex = Math.min(100, Math.round((constScore + tripsScore + earnScore) / 3)) || 0;
  });

  return { globalMaxConstancy, maxTrips, maxEarnings };
}

async function recalculateRanks(db: any, simulatorId: string, periodKey: string) {
  const driversRef = collection(db, `leaderboard/${simulatorId}/periods/${periodKey}/drivers`);
  const snap = await getDocs(driversRef);
  let drivers: any[] = [];
  snap.forEach((d: any) => drivers.push({ id: d.id, ...d.data() }));

  const activeDrivers = drivers.filter((d: any) => d.isEligible === true);

  const [yearStr, monthStr] = periodKey.split('-');
  const runDays = getDaysInMonth(parseInt(yearStr), parseInt(monthStr));

  calculateScores(activeDrivers, runDays);

  activeDrivers.sort((a, b) => {
    if (b.earnings !== a.earnings) return b.earnings - a.earnings;
    return b.trips - a.trips;
  });

  for (let i = 0; i < activeDrivers.length; i++) {
    if (i > 0 && activeDrivers[i].earnings === activeDrivers[i-1].earnings && activeDrivers[i].trips === activeDrivers[i-1].trips) {
      activeDrivers[i].globalRank = activeDrivers[i-1].globalRank;
    } else {
      activeDrivers[i].globalRank = i + 1;
    }
  }

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
    for (let i = 0; i < companyGroups[cid].length; i++) {
      if (i > 0 && companyGroups[cid][i].earnings === companyGroups[cid][i-1].earnings && companyGroups[cid][i].trips === companyGroups[cid][i-1].trips) {
        companyGroups[cid][i].internalRank = companyGroups[cid][i-1].internalRank;
      } else {
        companyGroups[cid][i].internalRank = i + 1;
      }
    }
  }

  const newGenerationId = Date.now().toString();
  
  const chunks: any[][] = [];
  for (let i = 0; i < drivers.length; i += 400) {
    chunks.push(drivers.slice(i, i + 400));
  }

  for (const chunk of chunks) {
    const batch = writeBatch(db);
    chunk.forEach(d => {
      const ref = doc(db, `leaderboard/${simulatorId}/periods/${periodKey}/drivers/${d.id}`);
      if (d.isEligible) {
        batch.update(ref, {
          globalRank: d.globalRank,
          internalRank: d.internalRank,
          finalIndex: d.finalIndex,
          generationId: newGenerationId
        });
      } else {
        batch.update(ref, {
          globalRank: null,
          internalRank: null,
          finalIndex: 0,
          generationId: newGenerationId
        });
      }
    });
    await batch.commit();
  }

  const batchFinal = writeBatch(db);
  const periodRef = doc(db, `leaderboard/${simulatorId}/periods/${periodKey}`);
  batchFinal.set(periodRef, { status: 'ready', generationId: newGenerationId }, { merge: true });
  await batchFinal.commit();
}

async function processTrip(db: any, event: any) {
  const tripRevRef = doc(db, `trip_revisions/${event.simulatorId}_${event.tripId}`);
  let oldPeriod = '';
  
  await runTransaction(db, async (t: any) => {
    const revSnap = await t.get(tripRevRef);
    let oldData: any = null;
    if (revSnap.exists()) {
      oldData = revSnap.data();
      if (event.updateTime <= oldData.updateTime) {
        return; // Ignore old/equal revision
      }
    }

    let periodsToUpdate = new Set<string>();
    if (oldData) {
      oldPeriod = oldData.metricDate.substring(0, 7);
      periodsToUpdate.add(oldPeriod);
    }
    periodsToUpdate.add(event.metricDate.substring(0, 7));

    const driverRefs = new Map<string, any>();
    const driverSnaps = new Map<string, any>();
    for (const p of periodsToUpdate) {
      const ref = doc(db, `leaderboard/${event.simulatorId}/periods/${p}/drivers/${event.userId}`);
      driverRefs.set(p, ref);
      driverSnaps.set(p, await t.get(ref));
    }

    const cmSnap = await t.get(doc(db, `companyMembers/${event.userId}`));
    const cmData = cmSnap.exists() ? cmSnap.data() : null;
    const isEligible = cmData ? (cmData.status === 'active' && cmData.role === 'driver') : true; 
    const companyId = cmData ? cmData.companyId : event.companyId; 

    if (oldData && oldData.status === 'aprovada') {
      const oldP = oldData.metricDate.substring(0, 7);
      const oldD = oldData.metricDate.substring(0, 10);
      let dData = (driverSnaps.get(oldP) && typeof driverSnaps.get(oldP).exists === "function" && driverSnaps.get(oldP).exists()) ? driverSnaps.get(oldP).data() : {};
      dData.earnings = (dData.earnings || 0) - oldData.earnings;
      dData.trips = (dData.trips || 0) - 1;
      let dailyMap = dData.dailyTripsMap || {};
      dailyMap[oldD] = Math.max(0, (dailyMap[oldD] || 0) - 1);
      dData.dailyTripsMap = dailyMap;
      dData.constancy = Object.values(dailyMap).filter((v: any) => v > 0).length;
      driverSnaps.set(oldP, { ...dData });
    }

    if (event.status === 'aprovada') {
      const newP = event.metricDate.substring(0, 7);
      const newD = event.metricDate.substring(0, 10);
      let dData = (driverSnaps.get(newP) && typeof driverSnaps.get(newP).exists === "function" && driverSnaps.get(newP).exists()) ? driverSnaps.get(newP).data() : { earnings: 0, trips: 0, dailyTripsMap: {}, constancy: 0 };
      dData.earnings = (dData.earnings || 0) + event.earnings;
      dData.trips = (dData.trips || 0) + 1;
      let dailyMap = dData.dailyTripsMap || {};
      dailyMap[newD] = (dailyMap[newD] || 0) + 1;
      dData.dailyTripsMap = dailyMap;
      dData.constancy = Object.values(dailyMap).filter((v: any) => v > 0).length;
      driverSnaps.set(newP, { ...dData });
    }

    for (const [p, dData] of driverSnaps.entries()) {
      t.set(driverRefs.get(p), {
        ...dData,
        isEligible: isEligible,
        companyId: companyId
      }, { merge: true });
      t.set(doc(db, `leaderboard/${event.simulatorId}/periods/${p}`), { status: 'building' }, { merge: true });
    }

    t.set(tripRevRef, {
      earnings: event.earnings,
      metricDate: event.metricDate,
      status: event.status,
      updateTime: event.updateTime
    });
  });

  const p1 = event.metricDate.substring(0, 7);
  await recalculateRanks(db, event.simulatorId, p1);
  if (oldPeriod && oldPeriod !== p1) {
    await recalculateRanks(db, event.simulatorId, oldPeriod);
  }
}

async function runTests() {
  const testEnv = await initializeTestEnvironment({
    projectId: 'demo-leaderboard-v6',
    firestore: { host: '127.0.0.1', port: 8085, rules: readFileSync('firestore.rules.v6', 'utf8') }
  });

  const adminDb = testEnv.authenticatedContext('admin').firestore();

  // Test: Piso anti-inflação e globalMaxConstancy (simular mes com 31 dias)
  console.log("== Piso Anti-inflação & Índice Final ==");
  const activeDrivers: any[] = [
    { constancy: 2, trips: 10, earnings: 1000 },
    { constancy: 10, trips: 50, earnings: 5000 }
  ];
  const scores = calculateScores(activeDrivers, 31);
  console.log(`MaxConstancy (piso=${Math.ceil(31*0.5)}):`, scores.globalMaxConstancy);
  console.log(`MaxTrips (piso=${31*1}):`, scores.maxTrips);
  console.log(`MaxEarnings (piso=${31*100}):`, scores.maxEarnings);
  console.log("Scores Driver 1 (Denominador não-zero):", activeDrivers[0].finalIndex);
  console.log("Scores Driver 2:", activeDrivers[1].finalIndex);

  // Setup company members for source of truth tests
  await setDoc(doc(adminDb, 'companyMembers/dr1'), { status: 'active', role: 'driver', companyId: 'compA' });
  await setDoc(doc(adminDb, 'companyMembers/dr2'), { status: 'active', role: 'driver', companyId: 'compA' });
  await setDoc(doc(adminDb, 'companyMembers/dr3'), { status: 'active', role: 'driver', companyId: 'compA' });
  await setDoc(doc(adminDb, 'companyMembers/dr4'), { status: 'active', role: 'driver', companyId: 'compB' });
  await setDoc(doc(adminDb, 'companyMembers/drInact'), { status: 'inactive', role: 'driver', companyId: 'compA' });

  // Test: Empate completo 1, 1, 3
  console.log("\n== Empate Completo 1,1,3 ==");
  await processTrip(adminDb, { tripId: 't1', userId: 'dr1', simulatorId: 'ETS2', earnings: 5000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z', updateTime: 1 });
  await processTrip(adminDb, { tripId: 't2', userId: 'dr2', simulatorId: 'ETS2', earnings: 5000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z', updateTime: 1 });
  await processTrip(adminDb, { tripId: 't3', userId: 'dr3', simulatorId: 'ETS2', earnings: 1000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z', updateTime: 1 });
  
  let d1 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/dr1'));
  let d2 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/dr2'));
  let d3 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/dr3'));
  console.log(`dr1 rank: ${d1.data()?.globalRank} | dr2 rank: ${d2.data()?.globalRank} | dr3 rank: ${d3.data()?.globalRank}`);

  // Test: Motorista inativo
  console.log("\n== Motorista Inativo ==");
  await processTrip(adminDb, { tripId: 't4', userId: 'drInact', simulatorId: 'ETS2', earnings: 10000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z', updateTime: 1 });
  let dinact = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/drInact'));
  console.log("Inativo isEligible:", dinact.data()?.isEligible);
  console.log("Inativo globalRank (deve ser null):", dinact.data()?.globalRank);

  // Test: Evento antigo entregue depois (revisão)
  console.log("\n== Evento antigo ignorado (Revisão Monotônica) ==");
  await processTrip(adminDb, { tripId: 't1', userId: 'dr1', simulatorId: 'ETS2', earnings: 2000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z', updateTime: 2 });
  let d1_v2 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/dr1'));
  console.log("Earnings atualizados para UpdateTime 2:", d1_v2.data()?.earnings);

  await processTrip(adminDb, { tripId: 't1', userId: 'dr1', simulatorId: 'ETS2', earnings: 100000, status: 'aprovada', metricDate: '2026-07-01T12:00:00Z', updateTime: 1 });
  let d1_v3 = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/dr1'));
  console.log("Earnings após UpdateTime 1 (deve ignorar e manter igual):", d1_v3.data()?.earnings);

  // Test: Mudança de MetricDate entre períodos
  console.log("\n== Mudança de MetricDate ==");
  await processTrip(adminDb, { tripId: 't1', userId: 'dr1', simulatorId: 'ETS2', earnings: 2000, status: 'aprovada', metricDate: '2026-06-01T12:00:00Z', updateTime: 3 });
  let d1_jul = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-07/drivers/dr1'));
  let d1_jun = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-06/drivers/dr1'));
  console.log("Trips em Julho (movido para fora):", d1_jul.data()?.trips);
  console.log("Trips em Junho (entrou):", d1_jun.data()?.trips);

  // Test: Motorista ativo sem viagens (isEligible=true no período por outro motivo ou backfill)
  console.log("\n== Motorista Ativo Sem Viagens ==");
  await setDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-06/drivers/dr4'), { isEligible: true, trips: 0, earnings: 0, constancy: 0, companyId: 'compB' });
  await recalculateRanks(adminDb, 'ETS2', '2026-06');
  let d4_jun = await getDoc(doc(adminDb, 'leaderboard/ETS2/periods/2026-06/drivers/dr4'));
  console.log("Rank do driver sem viagens:", d4_jun.data()?.globalRank, "Index:", d4_jun.data()?.finalIndex);

  // Test: Rules Projection e Bloqueio
  console.log("\n== Rules: Projection simulator_members ==");
  await setDoc(doc(adminDb, 'simulator_members/ETS2/users/auth_yes'), { active: true });
  
  const authYes = testEnv.authenticatedContext('auth_yes').firestore();
  const authNo = testEnv.authenticatedContext('auth_no').firestore();

  await assertSucceeds(getDoc(doc(authYes, 'leaderboard/ETS2/periods/2026-07')));
  console.log("-> Membro ETS2 com acesso liberado");
  await assertFails(getDoc(doc(authNo, 'leaderboard/ETS2/periods/2026-07')));
  console.log("-> Outro simulador ou sem membership bloqueado corretamente");

  // Test: Reconstrução de 1.200 motoristas em lotes
  console.log("\n== Reconstrução com 1.200 motoristas ==");
  const batch1 = writeBatch(adminDb);
  const batch2 = writeBatch(adminDb);
  const batch3 = writeBatch(adminDb);

  for (let i = 0; i < 400; i++) batch1.set(doc(adminDb, `leaderboard/ETS2/periods/2026-01/drivers/d${i}`), { isEligible: true, trips: 1, earnings: 10, constancy: 1, companyId: 'c1' });
  for (let i = 400; i < 800; i++) batch2.set(doc(adminDb, `leaderboard/ETS2/periods/2026-01/drivers/d${i}`), { isEligible: true, trips: 1, earnings: 10, constancy: 1, companyId: 'c1' });
  for (let i = 800; i < 1200; i++) batch3.set(doc(adminDb, `leaderboard/ETS2/periods/2026-01/drivers/d${i}`), { isEligible: true, trips: 1, earnings: 10, constancy: 1, companyId: 'c1' });
  await batch1.commit();
  await batch2.commit();
  await batch3.commit();

  await recalculateRanks(adminDb, 'ETS2', '2026-01');
  const checkBig = await getDocs(collection(adminDb, 'leaderboard/ETS2/periods/2026-01/drivers'));
  let withRank = 0;
  checkBig.forEach(d => { if(d.data().globalRank) withRank++; });
  console.log(`Motoristas rankeados em 2026-01: ${withRank}/1200`);

  await testEnv.cleanup();
}

runTests().catch(console.error);
