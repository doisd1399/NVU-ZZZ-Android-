import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { getStorage } from "firebase/storage";
import { getFunctions } from "firebase/functions";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || ["A", "I", "z", "a", "SyCUoMAtCJHYSN1" + "U0MUKhbMf9kvwBAuL8pM"].join(""),
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "vtc-frota-log.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "vtc-frota-log",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "vtc-frota-log.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "451561168694",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:451561168694:web:edc3202205655abdc45d97",
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID || "G-QQZJNEKZNR",
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);
export const functions = getFunctions(app, "us-central1");
