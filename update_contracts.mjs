import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/fleet/ContractsTab.tsx', 'utf8');

// Replacements for text
content = content.replace(/Sem sequência/g, "Sem pasta");
content = content.replace(/Nova Sequência/g, "Nova Pasta");
content = content.replace(/Nova sequência/g, "Nova Pasta");
content = content.replace(/Nome da sequência/g, "Nome da pasta");
content = content.replace(/Editar Sequência/g, "Editar Pasta");
content = content.replace(/Excluir Sequência/g, "Excluir Pasta");
content = content.replace(/Mover para Sequência/g, "Mover para Pasta");
content = content.replace(/Mover para sequência/g, "Mover para pasta");
content = content.replace(/Remover da sequência/g, "Remover da pasta");
content = content.replace(/Mover para/g, "Mover para"); // no change
content = content.replace(/Sequência excluída/g, "Pasta excluída");
content = content.replace(/da sequência/g, "da pasta");
content = content.replace(/nesta sequência/g, "nesta pasta");
content = content.replace(/desta sequência/g, "desta pasta");
content = content.replace(/uma sequência/g, "uma pasta");
content = content.replace(/Sem sequência/gi, "Sem pasta");
content = content.replace(/Nova operação/gi, "Nova Operação");
content = content.replace(/Excluir operação/gi, "Excluir Operação");
content = content.replace(/Editar operação/gi, "Editar Operação");
content = content.replace(/Ver detalhes da operação/gi, "Ver detalhes da operação");

fs.writeFileSync('src/pages/admin/fleet/ContractsTab.tsx', content);
