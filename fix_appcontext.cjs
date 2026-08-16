const fs = require('fs');
let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

// The block that was accidentally added multiple times
const badBlock = `      const company = companies.find((c) => c.id === app.companyId);
      if (userId) {
        await createNotification({
          userId: userId,
          companyId: app.companyId,
          targetProfile: "driver",
          type: "RECRUITMENT_APPROVED",
          title: "Solicitação aprovada",
          message: \`Sua solicitação para a empresa \${company?.companyName || ""} foi aprovada!\`,
          dedupeKey: \`RECRUITMENT_APPROVED_\${applicationId}\`,
        });
      }`;

// Split by this block and re-insert it only in the right place
let parts = content.split(badBlock);
if (parts.length > 1) {
  content = parts.join(''); // remove all occurrences
  
  // Find where it SHOULD be: inside approveRecruitmentApplication
  const targetRegex = /(await updateDoc\(doc\(db, "recruitment_applications", applicationId\), {\s*status: "approved",\s*}\);)/;
  content = content.replace(targetRegex, "$1\n" + badBlock);
}

fs.writeFileSync('src/context/AppContext.tsx', content);
console.log("Fixed.");
