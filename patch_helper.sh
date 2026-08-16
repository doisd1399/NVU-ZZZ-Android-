sed -i '179i\
  useEffect(() => {\
    if (myJob) {\
      console.log("[DIAGNOSTIC] Ao carregar a operação:");\
      console.log(`- operationId: ${myJob.id}`);\
      console.log(`- contractId: ${myJob.contractId}`);\
      console.log(`- quantidade de viagens encontrada (historicoTrips): ${currentOperationTrips.length}`);\
      console.log(`- quantidade usada no cálculo do card (myJob.progress): ${myJob.progress}`);\
      const contractTotal = contract ? contract.totalDeliveries : "N/A";\
      console.log(`- entregas concluídas: ${myJob.progress}`);\
      console.log(`- total de entregas: ${contractTotal}`);\
      console.log(`- data da última atualização (job): ${myJob.updatedAt}`);\
    }\
  }, [myJob, currentOperationTrips.length, contract]);\
' src/pages/driver/Dashboard.tsx
