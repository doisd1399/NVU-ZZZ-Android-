import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/ContractDetailsPage.tsx', 'utf8');

content = content.replace(/Contrato não encontrado/g, "Operação não encontrada");
content = content.replace(/Cancelar Contrato/g, "Cancelar Operação");
content = content.replace(/neste contrato/g, "nesta operação");
content = content.replace(/Este contrato será/g, "Esta operação será");

fs.writeFileSync('src/pages/admin/ContractDetailsPage.tsx', content);
