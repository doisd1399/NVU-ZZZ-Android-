sed -i '/const rejectRecruitmentApplication = async (applicationId: string) => {/,/    } catch (e) {/ {
  /status: "rejected",/!b
  n
  a\
      const company = companies.find((c) => c.id === app?.companyId);\
      let targetUserId = app?.userId;\
      if (!targetUserId && app?.email) {\
        const q = query(collection(db, "users"), where("email", "==", app.email.trim().toLowerCase()));\
        const qs = await getDocs(q);\
        if (!qs.empty) targetUserId = qs.docs[0].id;\
      }\
      if (targetUserId && app?.companyId) {\
        await createNotification({\
          userId: targetUserId,\
          companyId: app.companyId,\
          targetProfile: "driver",\
          type: "RECRUITMENT_REJECTED",\
          title: "Solicitação recusada",\
          message: `Sua solicitação para a empresa ${company?.companyName || ""} foi recusada.`,\
          dedupeKey: `RECRUITMENT_REJECTED_${applicationId}`,\
        });\
      }
}' src/context/AppContext.tsx
