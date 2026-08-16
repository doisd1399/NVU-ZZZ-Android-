import fs from 'fs';

for (const file of ['src/pages/SelectProfile.tsx', 'src/layouts/AdminLayout.tsx']) {
  let content = fs.readFileSync(file, 'utf8');
  content = content.replace('import {\nimport { resolveDriverPhoto } from "../lib/resolveDriverPhoto";', 'import { resolveDriverPhoto } from "../lib/resolveDriverPhoto";\nimport {');
  fs.writeFileSync(file, content);
}
