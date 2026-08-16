import fs from 'fs';

let content = fs.readFileSync('src/services/notificationService.ts', 'utf8');
content = content.replace(
  '  | "COMPANY_REJECTED"',
  '  | "COMPANY_REJECTED"\n  | "RECRUITMENT_APPROVED"\n  | "RECRUITMENT_REJECTED"'
);

fs.writeFileSync('src/services/notificationService.ts', content);
