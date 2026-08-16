import fs from 'fs';
let content = fs.readFileSync('firestore.rules', 'utf8');

const simulatorsRule = `
    match /simulators/{id} {
      allow read: if true;
      allow write: if isAuthenticated();
    }
`;

content = content.replace('    // Default fallback', simulatorsRule + '    // Default fallback');

fs.writeFileSync('firestore.rules', content);
