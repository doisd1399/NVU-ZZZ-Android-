import fs from "fs";

const file = "src/pages/admin/Operations.tsx";
let txt = fs.readFileSync(file, "utf8");

const sIdx = txt.indexOf("             {filteredJobs.map(job => {");
const eIdxStr =
  "                 </div>\n               )\n             })}\n           </div>\n         )}\n      </div>";
const eIdx = txt.indexOf(eIdxStr);

if (sIdx > -1 && eIdx > -1) {
  const replacement = `             {filteredJobs.map(job => {
               const isSelected = selectedJobId === job.id;
               const progressPct = Math.round((job.progress / Math.max(1, job.contract!.totalDeliveries)) * 100) || 0;
               const deliveriesLeft = job.contract!.totalDeliveries - job.progress;
               const isOverdue = job.daysLeft < 0;
               const effectiveTrailerId = job.trailerId || job.contract?.trailerId;
               const currentTrailer = trailers.find(t => t.id === effectiveTrailerId);
               
               return (
                 <div key={job.id} className={cn("flex flex-col border rounded-xl transition-all overflow-hidden bg-white", isSelected ? "border-blue-500 shadow-md ring-1 ring-blue-500/20" : "border-gray-200 shadow-sm hover:border-gray-300")}>
                   <button onClick={() => setSelectedJobId(isSelected ? null : job.id)} className="w-full text-left p-3 sm:p-4">
                     <div className="flex justify-between items-center gap-3">
                       <div className="flex-1 min-w-0">
                         <h3 className="font-bold text-gray-900 text-[13px] sm:text-[14px] truncate mb-1">{job.driver!.name}</h3>
                         <div className="flex items-center gap-1.5 text-[11px] text-gray-500">
                           <span className="truncate max-w-[100px] sm:max-w-[180px] font-medium text-gray-700">{job.contract!.name}</span>
                           <span className="text-gray-300 shrink-0">•</span>
                           <span className="truncate font-medium flex items-center gap-1"><Truck size={10} className="shrink-0"/>{currentTrailer?.name || 'S/ Reboque'}</span>
                         </div>
                       </div>
                       <div className="w-24 sm:w-28 shrink-0 flex flex-col justify-center">
                         <div className="flex justify-between items-center mb-1.5 text-[10px] sm:text-[11px] font-bold">
                           <span className="text-gray-900">{job.progress}/{job.contract!.totalDeliveries}</span>
                           <span className="text-gray-500">{deliveriesLeft} faltam</span>
                         </div>
                         <div className="w-full bg-gray-100 rounded-full h-1.5 overflow-hidden">
                           <div className={cn("h-full rounded-full transition-all duration-500", job.statusDetails.color)} style={{ width: \`\${Math.max(3, progressPct)}%\` }}></div>
                         </div>
                       </div>
                       <div className="shrink-0 text-gray-400 pl-1">
                         {isSelected ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                       </div>
                     </div>
                   </button>
                   {isSelected && (
                     <div className="border-t border-gray-100 bg-gray-50/50 p-3 sm:p-4 flex flex-col justify-between flex-1">
                        <div className="grid grid-cols-2 gap-3 text-[12px] mb-4">
                           <div className="min-w-0">
                              <span className="block text-[10px] font-bold text-gray-500 uppercase tracking-widest mb-1">Prazo</span>
                              <span className={cn("font-medium flex items-center gap-1.5", isOverdue && job.status === 'active' ? "text-red-600 font-bold" : "text-gray-900")}>
                                 <Clock size={12} className={cn(isOverdue && job.status === 'active' ? "text-red-500" : "text-gray-400")} /> 
                                 {format(new Date(job.deadlineDate), "dd/MM/yyyy")}
                              </span>
                           </div>
                           <div className="min-w-0">
                              <span className="block text-[10px] font-bold text-gray-500 uppercase tracking-widest mb-1">Veículo</span>
                              <span className="font-medium text-gray-900 truncate block">{job.vehicle?.name || 'Nenhum'}</span>
                           </div>
                        </div>
                        
                        <div className="flex flex-col sm:flex-row items-center gap-2 mt-auto pt-2 border-t border-gray-100/50">
                           <Button 
                             onClick={(e) => { e.stopPropagation(); setViewingDriverId(job.driver!.id); }}
                             className="w-full sm:flex-1 h-8 text-[11px] font-semibold text-gray-700 bg-white hover:bg-gray-50 border border-gray-200 shadow-none border-gray-300" 
                           >
                             <User size={12} className="mr-1.5" /> Perfil
                           </Button>
                          
                          {job.status !== 'completed' && job.status !== 'cancelled' && (
                            <Button 
                              className="w-full sm:flex-1 h-8 px-4 text-[11px] font-semibold text-red-600 bg-red-50 hover:bg-red-100 border-none shadow-none"
                              onClick={(e) => {
                                 e.stopPropagation();
                                 if (window.confirm('Tem certeza que deseja cancelar esta operação?')) {
                                    cancelJob(job.id);
                                    setSelectedJobId(null);
                                 }
                              }}
                            >
                              Cancelar
                            </Button>
                          )}
                          
                          {(job.status === 'completed' || job.status === 'cancelled') && (
                            <Button 
                              className="w-full sm:flex-[2] h-8 px-4 text-[11px] font-semibold text-red-600 bg-red-50 hover:bg-red-100 border-none shadow-none flex items-center gap-1.5 text-center justify-center"
                              onClick={(e) => {
                                 e.stopPropagation();
                                 if (window.confirm('Tem certeza que deseja excluir o histórico deste trabalho?')) {
                                    deleteJob(job.id);
                                    setSelectedJobId(null);
                                 }
                              }}
                            >
                              <Trash2 size={13} />
                              Excluir Histórico
                            </Button>
                          )}
                        </div>
                     </div>
                   )}
                 </div>
               )
             })}
           </div>
         )}
      </div>`;

  txt =
    txt.substring(0, sIdx) + replacement + txt.substring(eIdx + eIdxStr.length);
  fs.writeFileSync(file, txt, "utf8");
  console.log("SUCCESS");
} else {
  console.log("FAILED", sIdx, eIdx);
}
