const fs = require('fs');
let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

const regex = /const rejectRecruitmentApplication = async \(applicationId: string\) => {[\s\S]*?\} catch \(e\) {/;
const match = content.match(regex);
if (match) {
  let block = match[0];
  // Remove all the injected code to start fresh
  const toRemove = `      const company = companies.find((c) => c.id === app?.companyId);
      let targetUserId = app?.userId;
      if (!targetUserId && app?.email) {
        const q = query(collection(db, "users"), where("email", "==", app.email.trim().toLowerCase()));
        const qs = await getDocs(q);
        if (!qs.empty) targetUserId = qs.docs[0].id;
      }
      if (targetUserId && app?.companyId) {
        await createNotification({
          userId: targetUserId,
          companyId: app.companyId,
          targetProfile: "driver",
          type: "RECRUITMENT_REJECTED",
          title: "Solicitação recusada",
          message: \`Sua solicitação para a empresa \${company?.companyName || ""} foi recusada.\`,
          dedupeKey: \`RECRUITMENT_REJECTED_\${applicationId}\`,
        });
      }`;
  // Split and reassemble without the toRemove block
  block = block.split(toRemove).join('');
  
  // Now add it exactly once after the recruitment_applications update
  const insertAfter = `      await updateDoc(doc(db, "recruitment_applications", applicationId), {\n        status: "rejected",\n      });`;
  
  block = block.replace(insertAfter, insertAfter + "\n" + toRemove);
  
  content = content.replace(match[0], block);
  fs.writeFileSync('src/context/AppContext.tsx', content);
  console.log("Fixed.");
} else {
  console.log("Not found.");
}
