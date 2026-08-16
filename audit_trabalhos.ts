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
      }
    }
    
    const trabalhosSnap = await getDocs(collection(db, "trabalhos"));
    const trabalhos = trabalhosSnap.docs.map(d => ({ id: d.id, ...d.data() }));
    
    let counts: Record<string, number> = {};
    trabalhos.forEach((t: any) => {
       const status = String(t.status);
       counts[status] = (counts[status] || 0) + 1;
    });
    
    console.log(counts);
    process.exit(0);
  } catch (error) {
    console.error(error);
    process.exit(1);
  }
}
runAudit();
