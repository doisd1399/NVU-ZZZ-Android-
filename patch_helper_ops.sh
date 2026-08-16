sed -i '249i\
  useEffect(() => {\
    if (activeJobsList.length > 0) {\
      console.log(`[DIAGNOSTIC] Company Profile - Trabalhos Ativos:`);\
      activeJobsList.slice(0, 3).forEach(job => {\
        const tripsForJob = historicoTrips.filter((t: any) => t.jobId === job.id);\
        console.log(`  - Job ${job.id}: card usa progress=${job.progress}, bd/trips reais=${tripsForJob.length}`);\
      });\
    }\
  }, [activeJobsList, historicoTrips.length]);\
' src/pages/admin/fleet/OperationsTab.tsx
