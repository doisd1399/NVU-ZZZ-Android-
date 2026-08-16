import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

content = content.replace(
  "      allCompanies: companies,",
  "      simulators,\n      allCompanies: companies,"
);

content = content.replace(
  "    globalEndDateStr,\n  ]);",
  "    globalEndDateStr,\n    simulators,\n  ]);"
);

fs.writeFileSync('src/context/AppContext.tsx', content);
console.log("Fixed simulators in AppContext");
