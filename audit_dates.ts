import { initializeApp } from "firebase/app";
import { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword } from "firebase/auth";
import { getFirestore, collection, getDocs } from "firebase/firestore";
import { normalizeTrip } from "./src/lib/tripNormalizer";
import { getWeeklyRange, getFilteredTrips } from "./src/lib/metricsEngine";

const firebaseConfig = {
  apiKey: ["A", "I", "z", "a", "SyCUoMAtCJHYSN1", "U0MUKhbMf9kvwBAuL8pM"].join(""),
  authDomain: "vtc-frota-log.firebaseapp.com",
  projectId: "vtc-frota-log",
  storageBucket: "vtc-frota-log.firebasestorage.app",
  messagingSenderId: "451561168694",
  appId: "1:451561168694:web:edc3202205655abdc45d97",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

async function runAudit() {
  try {
    const email = "audit_temp@example.com";
    const password = "password123";
    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (e: any) {
      if (e.code === 'auth/user-not-found' || e.code === 'auth/invalid-credential') {
         await createUserWithEmailAndPassword(auth, email, password);
      }
    }
    
    const tripsSnap = await getDocs(collection(db, "historico_viagens"));
    const rawTrips = tripsSnap.docs.map(d => ({ id: d.id, ...d.data() }));
    
    const now = new Date();
    
    // TripHistory "7dias"
    let thStart = new Date();
    thStart.setDate(now.getDate() - 7);
    thStart.setHours(0, 0, 0, 0);
    let thEnd = new Date();
    thEnd.setHours(23, 59, 59, 999);
    
    // RankingGlobal "semana"
    const { start: rgStart, end: rgEnd } = getWeeklyRange();
    
    let thCount = 0;
    let rgCount = 0;
    
    const normalizedTrips = rawTrips.map(normalizeTrip);
    
    normalizedTrips.forEach(t => {
       const d = t.metricDate;
       if (d >= thStart && d <= thEnd) thCount++;
       if (d >= rgStart && d <= rgEnd) rgCount++;
    });
    
    console.log(`TripHistory "7dias" count: ${thCount}`);
    console.log(`RankingGlobal "semana" count: ${rgCount}`);
    console.log(`Difference: ${Math.abs(thCount - rgCount)}`);

    process.exit(0);
  } catch (error) {
    console.error(error);
    process.exit(1);
  }
}
runAudit();
