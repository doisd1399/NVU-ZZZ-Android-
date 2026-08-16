import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/ManageContract.tsx', 'utf8');

content = content.replace(/Adicionar à sequência/g, "Adicionar à pasta");
content = content.replace(/Sem sequência/g, "Sem pasta");

fs.writeFileSync('src/pages/admin/ManageContract.tsx', content);
