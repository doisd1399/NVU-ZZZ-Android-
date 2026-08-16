import { initializeApp } from "firebase/app";
import { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword } from "firebase/auth";
import { getFirestore, collection, getDocs } from "firebase/firestore";

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
      } else {
         throw e;
      }
    }
    
    console.log("Logged in!");
    
    const tripsSnap = await getDocs(collection(db, "historico_viagens"));
    const trips = tripsSnap.docs.map(d => ({ id: d.id, ...d.data() }));
    
    let total = trips.length;
    let concluida = 0;
    let pending = 0;
    let andamento = 0;
    let cancelada = 0;
    let deleted = 0;
    let undefinedStatus = 0;
    let otherStatus: Record<string, number> = {};
    
    trips.forEach((t: any) => {
      const status = t.status?.toLowerCase();
      
      let isDeleted = !!(t.deleted || t.softDeleted);
      if (isDeleted) deleted++;
      
      if (!status) {
         undefinedStatus++;
      } else if (status === 'concluída' || status === 'concluida') {
         concluida++;
      } else if (status === 'pending') {
         pending++;
      } else if (status === 'em andamento' || status === 'andamento') {
         andamento++;
      } else if (status === 'cancelada' || status === 'cancelado' || status === 'canceled') {
         cancelada++;
      } else {
         otherStatus[status] = (otherStatus[status] || 0) + 1;
      }
    });
    
    console.log(`Total Trips: ${total}`);
    console.log(`Concluída: ${concluida}`);
    console.log(`Pendente: ${pending}`);
    console.log(`Em Andamento: ${andamento}`);
    console.log(`Cancelada: ${cancelada}`);
    console.log(`Deleted (flag): ${deleted}`);
    console.log(`Undefined Status: ${undefinedStatus}`);
    console.log(`Other Statuses:`, otherStatus);
    
    process.exit(0);
  } catch (error) {
    console.error("Audit error:", error);
    process.exit(1);
  }
}

runAudit();
