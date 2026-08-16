import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

// Add simulatorId to CompanyProfile
content = content.replace(
  '  companyName: string;\n  fleetName?: string;\n  simulatorName: string;',
  '  companyName: string;\n  fleetName?: string;\n  simulatorName: string;\n  simulatorId?: string;'
);

// Import resolveSimulatorId
if (!content.includes('import { resolveSimulatorId }')) {
  content = content.replace(
    'import { auth, db } from "../lib/firebase";',
    'import { auth, db } from "../lib/firebase";\nimport { resolveSimulatorId } from "../lib/resolveSimulator";'
  );
}

fs.writeFileSync('src/context/AppContext.tsx', content);

let contentJoin = fs.readFileSync('src/pages/driver/JoinCompany.tsx', 'utf8');
contentJoin = contentJoin.replace(
  'const matchesSimulator = (c.simulatorId || resolveSimulatorId(c)) === selectedSimulatorId;',
  'const matchesSimulator = ((c as any).simulatorId || resolveSimulatorId(c as any)) === selectedSimulatorId;'
);

fs.writeFileSync('src/pages/driver/JoinCompany.tsx', contentJoin);
