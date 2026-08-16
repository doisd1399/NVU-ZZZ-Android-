import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/fleet/ContractsTab.tsx', 'utf8');

content = content.replace(/sequenceId: undefined, sequenceOrder: undefined/g, "sequenceId: null, sequenceOrder: null");

fs.writeFileSync('src/pages/admin/fleet/ContractsTab.tsx', content);
