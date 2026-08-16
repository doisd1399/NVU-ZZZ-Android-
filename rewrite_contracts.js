const fs = require('fs');

let content = fs.readFileSync('src/pages/admin/fleet/ContractsTab.tsx', 'utf8');

// Replace "Contratos" strings
content = content.replace(/Contratos/g, 'Operações');
content = content.replace(/contrato/g, 'operação');
content = content.replace(/Contrato/g, 'Operação');

// Wait, doing this globally is dangerous because of route paths like "/admin/contract/" and variable names.
// It's better to just rewrite the file from scratch or use more specific replacements.
