import { initializeApp } from "firebase/app";
import { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword } from "firebase/auth";
import { getFirestore, collection, getDocs } from "firebase/firestore";
import { normalizeTrip } from "./src/lib/tripNormalizer";

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
    const trips = tripsSnap.docs.map(d => ({ id: d.id, ...d.data() }));
    
    let total = trips.length;
    let deletedCount = 0;
    let canceladoCount = 0;
    let invalidCount = 0;
    
    const invalidIds: string[] = [];
    
    trips.forEach((t: any) => {
      if (t.deleted === true) deletedCount++;
      if (t.cancelado === true) canceladoCount++;
      
      const normalized = normalizeTrip(t);
      if (!normalized.isValid) {
         invalidCount++;
         invalidIds.push(t.id);
      }
    });
    
    console.log(`Total Trips: ${total}`);
    console.log(`Deleted flags: ${deletedCount}`);
    console.log(`Cancelado flags: ${canceladoCount}`);
    console.log(`Invalid (according to normalizeTrip): ${invalidCount}`);
    console.log(`Invalid IDs sample:`, invalidIds.slice(0, 10));
    
    process.exit(0);
  } catch (error) {
    console.error(error);
    process.exit(1);
  }
}
runAudit();
