import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

const subCode = `
  // --- Simulators Subscription ---
  useEffect(() => {
    import("firebase/firestore").then(({ collection, onSnapshot, getDocs, addDoc }) => {
      const unsub = onSnapshot(
        collection(db, "simulators"),
        async (snap) => {
          if (snap.empty) {
            // Seed default simulators
            const defaults = ["GTO", "WTDS", "WBDS", "TOE 3", "ETS 2", "ATS", "PBS"];
            const now = new Date().toISOString();
            for (const name of defaults) {
              await addDoc(collection(db, "simulators"), {
                name,
                active: true,
                createdAt: now,
                updatedAt: now,
              });
            }
          } else {
            const data = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
            setSimulators(data);
          }
        },
        (error) => {
          console.error("Error fetching simulators:", error);
        }
      );
      return () => unsub();
    });
  }, []);
`;

if (!content.includes('// --- Simulators Subscription ---')) {
  content = content.replace(
    '// --- Global Public Companies Subscription ---',
    subCode + '\n  // --- Global Public Companies Subscription ---'
  );
  fs.writeFileSync('src/context/AppContext.tsx', content);
  console.log('Added simulators subscription.');
} else {
  console.log('Already added.');
}
