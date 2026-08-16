import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

content = content.replace(
  /const isStale =[\s\n]*activeCompanyId &&[\s\n]*!memberships\.some\(\(m\) => m\.companyId === activeCompanyId\) &&[\s\n]*memberships\.length > 0;/g,
  `const isStale =
      activeCompanyId &&
      !memberships.some((m) => m.companyId === activeCompanyId) &&
      memberships.length > 0 &&
      (!isSeniorAuthenticated || seniorCompanyId !== activeCompanyId);`
);

fs.writeFileSync('src/context/AppContext.tsx', content);
