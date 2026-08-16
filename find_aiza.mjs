import fs from 'fs';
import path from 'path';

function findFiles(dir, pattern) {
  let results = [];
  const list = fs.readdirSync(dir);
  for (const file of list) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      results = results.concat(findFiles(filePath, pattern));
    } else {
      if (filePath.endsWith('.js') || filePath.endsWith('.ts') || filePath.endsWith('.map') || filePath.endsWith('.d.ts') || filePath.endsWith('.cjs')) {
        const content = fs.readFileSync(filePath, 'utf8');
        if (content.includes(pattern)) {
          results.push(filePath);
        }
      }
    }
  }
  return results;
}

const res = findFiles('node_modules/@firebase', 'AIza');
console.log(res.join('\n'));
