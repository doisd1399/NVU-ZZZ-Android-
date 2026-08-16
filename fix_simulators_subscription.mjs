import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

const oldSub = `  // --- Simulators Subscription ---
  const currentUserIdForSimulators = currentUser?.id;
  useEffect(() => {
    if (!currentUserIdForSimulators) {
      setSimulators([]);
      return;
    }
    const unsub = onSnapshot(
      collection(db, "simulators"),
      async (snap) => {
        if (snap.empty) {
          // Seed default simulators
          const defaults = ["GTO", "WTDS", "WBDS", "TOE 3", "ETS 2", "ATS", "PBS"];
          const now = new Date().toISOString();
          for (const name of defaults) {
            try {
              await addDoc(collection(db, "simulators"), {
                name,
                active: true,
                createdAt: now,
                updatedAt: now,
              });
            } catch (e) {
              console.error("Failed to seed simulator:", e);
            }
          }
        } else {
          const data = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
          // @ts-ignore
          setSimulators(data);
        }
      },
      (error) => {
        console.error("Error fetching simulators:", error);
      }
    );
    return () => unsub();
  }, [currentUserIdForSimulators]);`;

const newSub = `  // --- Simulators Subscription ---
  useEffect(() => {
    const unsub = onSnapshot(
      collection(db, "simulators"),
      async (snap) => {
        if (snap.empty) {
          // Seed default simulators
          const defaults = ["GTO", "WTDS", "WBDS", "TOE 3", "ETS 2", "ATS", "PBS"];
          const now = new Date().toISOString();
          for (const name of defaults) {
            try {
              await addDoc(collection(db, "simulators"), {
                name,
                active: true,
                createdAt: now,
                updatedAt: now,
              });
            } catch (e) {
              console.error("Failed to seed simulator (possibly unauthenticated):", e);
            }
          }
        } else {
          const data = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
          // @ts-ignore
          setSimulators(data);
        }
      },
      (error) => {
        console.error("Error fetching simulators:", error);
      }
    );
    return () => unsub();
  }, []);`;

content = content.replace(oldSub, newSub);

fs.writeFileSync('src/context/AppContext.tsx', content);
