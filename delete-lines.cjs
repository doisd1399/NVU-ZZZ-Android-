const fs = require('fs');

const data = fs.readFileSync('src/pages/admin/fleet/OperationsTab.tsx', 'utf8').split('\n');
const start = 70; // 0-indexed would mean line 71 is data[70]
const end = 537; // line 538 is data[537]

data.splice(start, end - start + 1);

// Inject import
data.splice(69, 0, 'import { DesempenhoOperacionalCard } from "../../../components/DesempenhoOperacionalCard";');

fs.writeFileSync('src/pages/admin/fleet/OperationsTab.tsx', data.join('\n'));
console.log('done');
