import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

content = content.replace(
  /if \(currentMember\) \{\n        if \(!activeRole \|\| !currentMember\.roles\.includes\(activeRole\)\) \{\n          setActiveRole\(currentMember\.roles\[0\] as Role\);\n        \}\n      \}/g,
  `if (currentMember) {
        if (!activeRole || !currentMember.roles.includes(activeRole)) {
          setActiveRole(currentMember.roles[0] as Role);
        }
      } else if (isSeniorAuthenticated && seniorCompanyId === activeCompanyId) {
        if (activeRole !== "admin") {
          setActiveRole("admin");
        }
      }`
);

fs.writeFileSync('src/context/AppContext.tsx', content);
