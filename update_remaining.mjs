import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/fleet/ContractsTab.tsx', 'utf8');

content = content.replace(/Detalhes sobre a sequência/g, "Detalhes sobre a pasta");
content = content.replace(/Operações na sequência/g, "Operações na pasta");

fs.writeFileSync('src/pages/admin/fleet/ContractsTab.tsx', content);
