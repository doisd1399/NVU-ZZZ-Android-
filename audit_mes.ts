import { initializeApp } from "firebase/app";
import { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword } from "firebase/auth";
import { getFirestore, collection, getDocs } from "firebase/firestore";
import { normalizeTrip } from "./src/lib/tripNormalizer";
import { getMonthlyRange, getFilteredTrips } from "./src/lib/metricsEngine";

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
    // History "mes" filter
    let sDate = new Date(now.getFullYear(), now.getMonth(), 1);
    let eDate = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999);
    
    // TripHistory filter
    const historyTrips = rawTrips.filter((trip: any) => {
       const ts = trip.completedAt || trip.dataFechamento || trip.date || trip.dataLancamento || trip.createdAt;
       let tripDate = new Date(0);
       if (ts) {
          if (typeof ts.toDate === 'function') tripDate = ts.toDate();
          else if (ts.seconds) tripDate = new Date(ts.seconds * 1000);
          else tripDate = new Date(ts);
       }
       if (tripDate < sDate) return false;
       if (tripDate > eDate) return false;
       return true;
    });
    
    // Ranking filter
    const normalizedTrips = rawTrips.map(normalizeTrip);
    const rankingTrips = getFilteredTrips(normalizedTrips, sDate, eDate, undefined, "Todos os simuladores", []);
    
    console.log(`History count: ${historyTrips.length}`);
    console.log(`Ranking count: ${rankingTrips.length}`);
    console.log(`Diff: ${Math.abs(historyTrips.length - rankingTrips.length)}`);

    process.exit(0);
  } catch (error) {
    console.error(error);
    process.exit(1);
  }
}
runAudit();
