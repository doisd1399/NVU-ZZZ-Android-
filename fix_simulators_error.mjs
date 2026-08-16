import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

const oldError = `      (error) => {
        console.error("Error fetching simulators:", error);
      }`;

const newError = `      (error) => {
        console.error("Error fetching simulators:", error);
        // Fallback to hardcoded simulators if permissions fail (e.g. rules not deployed yet)
        const defaults = ["GTO", "WTDS", "WBDS", "TOE 3", "ETS 2", "ATS", "PBS"];
        setSimulators(
          defaults.map((name) => ({
            id: name.toLowerCase().replace(/[^a-z0-9]/g, ""),
            name,
            active: true
          })) as any
        );
      }`;

content = content.replace(oldError, newError);

fs.writeFileSync('src/context/AppContext.tsx', content);
