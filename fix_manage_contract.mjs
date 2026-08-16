import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/ManageContract.tsx', 'utf8');

// Replace undefined with null in createContract and updateContract
content = content.replace(/sequenceId: selectedSequenceId \|\| undefined,/g, "sequenceId: selectedSequenceId || null,");
content = content.replace(/sequenceOrder: selectedSequenceId \? 999 : undefined,/g, "sequenceOrder: selectedSequenceId ? 999 : null,");
content = content.replace(/sequenceOrder: selectedSequenceId \? \(selectedSequenceId === existingContract\?\.sequenceId \? existingContract\.sequenceOrder : 999\) : undefined,/g, "sequenceOrder: selectedSequenceId ? (selectedSequenceId === existingContract?.sequenceId ? existingContract.sequenceOrder : 999) : null,");
content = content.replace(/trailerId: trailerId \|\| undefined,/g, "trailerId: trailerId || null,");

fs.writeFileSync('src/pages/admin/ManageContract.tsx', content);
