import fs from 'fs';
let content = fs.readFileSync('src/pages/RegisterCompany.tsx', 'utf8');
content = content.replace(/\\n\\n/g, '\n\n');
fs.writeFileSync('src/pages/RegisterCompany.tsx', content);
