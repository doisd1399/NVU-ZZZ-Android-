import fs from 'fs';
import path from 'path';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

// Update DriverRequest
content = content.replace(
  '  empresaId: string;\n  adminId: string;',
  '  empresaId: string;\n  simulatorId?: string;\n  adminId: string;'
);

// Update requestJoinCompany
content = content.replace(
  '        empresaId: companyId,\n        adminId: activeCompany?.userId || "",',
  '        empresaId: companyId,\n        simulatorId: activeCompany?.simulatorId || (activeCompany ? resolveSimulatorId(activeCompany) : ""),\n        adminId: activeCompany?.userId || "",'
);

fs.writeFileSync('src/context/AppContext.tsx', content);
