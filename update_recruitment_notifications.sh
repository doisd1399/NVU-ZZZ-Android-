sed -i '/status: "approved",/!b;n;a\
      const company = companies.find((c) => c.id === app.companyId);\
      if (userId) {\
        await createNotification({\
          userId: userId,\
          companyId: app.companyId,\
          targetProfile: "driver",\
          type: "RECRUITMENT_APPROVED",\
          title: "Solicitação aprovada",\
          message: `Sua solicitação para a empresa ${company?.companyName || ""} foi aprovada!`,\
          dedupeKey: `RECRUITMENT_APPROVED_${applicationId}`,\
        });\
      }' src/context/AppContext.tsx
