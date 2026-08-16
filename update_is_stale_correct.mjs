import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

content = content.replace(
  /const isStale =\s*!activeCompanyId \|\| !validCompanyIds\.includes\(activeCompanyId\);/g,
  `const isStale = !activeCompanyId || (!validCompanyIds.includes(activeCompanyId) && !(isSeniorAuthenticated && seniorCompanyId === activeCompanyId));`
);

fs.writeFileSync('src/context/AppContext.tsx', content);
