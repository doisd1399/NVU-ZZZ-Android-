const fs = require('fs');
let code = fs.readFileSync('src/pages/admin/fleet/RecruitmentTab.tsx', 'utf8');
code = code.replace(/setSelectedHistoryApp\(null\)/g, 'handleSetSelectedHistoryApp(null)');
fs.writeFileSync('src/pages/admin/fleet/RecruitmentTab.tsx', code);
