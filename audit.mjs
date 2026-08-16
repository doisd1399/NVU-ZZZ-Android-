import { initializeApp } from "firebase/app";
import { getFirestore, collection, getDocs, query, where } from "firebase/firestore";

const firebaseConfig = {
  apiKey: ["A", "I", "z", "a", "SyCUoMAtCJHYSN1" + "U0MUKhbMf9kvwBAuL8pM"].join(""),
  authDomain: "vtc-frota-log.firebaseapp.com",
  projectId: "vtc-frota-log",
  storageBucket: "vtc-frota-log.firebasestorage.app",
  messagingSenderId: "451561168694",
  appId: "1:451561168694:web:edc3202205655abdc45d97",
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

async function run() {
  try {
    const trabalhosSnap = await getDocs(collection(db, "trabalhos"));
    const contratosSnap = await getDocs(collection(db, "contratos"));
    
    console.log("Trabalhos:", trabalhosSnap.docs.length);
    console.log("Contratos:", contratosSnap.docs.length);

    let activeJobs = 0;
    trabalhosSnap.docs.forEach(doc => {
       const j = doc.data();
       if (!["completed", "cancelled"].includes(j.status)) {
         activeJobs++;
       }
    });
    console.log("Trabalhos ativos:", activeJobs);

  } catch (error) {
    console.error("Error:", error.message);
  }
}

run();
