sed -i '1010i\
      console.log(`[DIAGNOSTIC] Ao excluir viagem:`);\
      console.log(`- confirmar documento removido: ${deletingTrip.id}`);\
      console.log(`- job associado (se houver): ${deletingTrip.jobId || "N/A"}`);\
' src/pages/driver/TripHistory.tsx
