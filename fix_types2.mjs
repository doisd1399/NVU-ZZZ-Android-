import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

content = content.replace(
  '  simulatorName: string;',
  '  simulatorName: string;\n  simulatorId?: string;'
);

// We need to typecast in requestJoinCompany if needed
content = content.replace(
  'activeCompany?.simulatorId',
  '(activeCompany as any)?.simulatorId'
);
content = content.replace(
  'resolveSimulatorId(activeCompany)',
  'resolveSimulatorId(activeCompany as any)'
);

fs.writeFileSync('src/context/AppContext.tsx', content);
