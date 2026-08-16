import fs from "fs";
let c = fs.readFileSync("src/pages/driver/Profile.tsx", "utf-8");

const target = `                 {pastJobs.slice().reverse().map(job => {
                    const contract = contracts.find(c => c.id === job.contractId);
                    const jobTrailerId = job.trailerId || contract?.trailerId;
                    const vehicle = vehicles.find(v => v.id === job.vehicleId);
                    const trailer = jobTrailerId ? trailers.find(t => t.id === jobTrailerId) : null;
                    
                    return (
                      <div key={job.id} className="bg-white dark:bg-[#1A1F26] border border-gray-100 dark:border-[#2A2F3A] rounded-2xl p-4 flex items-center justify-between shadow-sm dark:shadow-none">
                         <div className="flex items-center gap-4 min-w-0">
                            <div className="w-10 h-10 rounded-full bg-gray-50 dark:bg-[#1A1F26] border border-gray-100 dark:border-[#2A2F3A] flex items-center justify-center shrink-0">
                               <CheckCircle size={18} className="text-gray-400" />
                            </div>
                            <div className="min-w-0">
                               <h4 className="font-semibold text-gray-900 dark:text-[#fafafa] text-[14px] tracking-tight truncate">{contract?.name || 'Contrato'}</h4>
                               <div className="flex flex-col gap-0.5 mt-0.5">
                                  <span className="text-[12px] text-gray-500 dark:text-[#a1a1aa] flex items-center gap-1.5 truncate">
                                     {job.progress} entregas • {vehicle?.name || 'N/A'}
                                  </span>
                                  {trailer && (
                                    <span className="text-[11px] text-gray-400 flex items-center gap-1.5 truncate">
                                       {trailer.name}
                                    </span>
                                  )}
                               </div>
                            </div>
                         </div>
                      </div>
                    );
                 })}`;

const replacement = `                 {pastJobs.slice().reverse().map(job => {
                     const contract = contracts.find(c => c.id === job.contractId);
                     const jobTrailerId = job.trailerId || contract?.trailerId;
                     const vehicle = vehicles.find(v => v.id === job.vehicleId);
                     const trailer = jobTrailerId ? trailers.find(t => t.id === jobTrailerId) : null;
                     
                     const completedAt = job.completedAt ? new Date(job.completedAt) : new Date();
                     const deadline = new Date(job.deadlineDate);
                     const onTime = completedAt <= deadline;
                     const isExpanded = expandedJobId === job.id;

                     return (
                       <div key={job.id} className={cn("bg-white dark:bg-[#1A1F26] border rounded-2xl transition-all shadow-sm dark:shadow-none overflow-hidden", isExpanded ? "border-gray-300 dark:border-gray-600 ring-1 ring-gray-200 dark:ring-gray-700 block" : "border-gray-100 dark:border-[#2A2F3A] hover:border-gray-200 dark:hover:border-gray-700")}>
                          <button 
                            className="w-full flex items-center justify-between p-4 focus:outline-none" 
                            onClick={() => setExpandedJobId(isExpanded ? null : job.id)}
                          >
                            <div className="flex items-center gap-4 min-w-0 pr-4">
                               <div className={cn("w-10 h-10 rounded-full flex items-center justify-center shrink-0 border", onTime ? "bg-green-50 dark:bg-green-900/20 border-green-100 dark:border-green-800/30 text-green-600 dark:text-green-400" : "bg-orange-50 dark:bg-orange-900/20 border-orange-100 dark:border-orange-800/30 text-orange-600 dark:text-orange-400")}>
                                  {onTime ? <CheckCircle size={18} /> : <AlertCircle size={18} />}
                               </div>
                               <div className="min-w-0 text-left">
                                  <h4 className="font-semibold text-gray-900 dark:text-[#fafafa] text-[14px] tracking-tight truncate">{contract?.name || 'Contrato'}</h4>
                                  <div className="flex items-center gap-1.5 mt-0.5">
                                     <span className={cn("text-[11px] font-semibold tracking-tight uppercase", onTime ? "text-green-600" : "text-orange-600")}>
                                        {onTime ? 'No prazo' : 'Atrasado'}
                                     </span>
                                     <span className="text-gray-300 dark:text-gray-600 text-[10px]">•</span>
                                     <span className="text-[12px] text-gray-500 dark:text-[#a1a1aa] truncate flex-1">
                                        {job.progress} entregas
                                     </span>
                                  </div>
                               </div>
                            </div>
                            <div className="text-gray-400 dark:text-gray-500 shrink-0">
                               {isExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                            </div>
                          </button>
                          
                          {isExpanded && (
                            <div className="px-4 pb-4 animate-in fade-in slide-in-from-top-2 duration-200">
                               <div className="pt-3 border-t border-gray-100 dark:border-[#2A2F3A] flex flex-col gap-3">
                                  
                                  <div className="grid grid-cols-2 gap-3">
                                    <div className="bg-gray-50 dark:bg-[#09090b] rounded-xl p-3 border border-gray-100 dark:border-[#2A2F3A]">
                                      <span className="text-[11px] text-gray-500 dark:text-[#a1a1aa] font-medium tracking-tight mb-1 block">Veículo</span>
                                      <div className="flex items-center gap-1.5 min-w-0">
                                        <Car size={13} className="text-gray-400 shrink-0"/>
                                        <span className="text-[13px] font-semibold text-gray-900 dark:text-[#fafafa] truncate">{vehicle?.name || 'N/A'}</span>
                                      </div>
                                    </div>
                                    
                                    <div className="bg-gray-50 dark:bg-[#09090b] rounded-xl p-3 border border-gray-100 dark:border-[#2A2F3A]">
                                      <span className="text-[11px] text-gray-500 dark:text-[#a1a1aa] font-medium tracking-tight mb-1 block">Reboque</span>
                                      <div className="flex items-center gap-1.5 min-w-0">
                                        <Truck size={13} className="text-gray-400 shrink-0"/>
                                        <span className="text-[13px] font-semibold text-gray-900 dark:text-[#fafafa] truncate">{trailer?.name || 'N/A'}</span>
                                      </div>
                                    </div>
                                  </div>

                                  <div className="flex flex-col gap-2 mt-1">
                                     <div className="flex justify-between items-center text-[12px]">
                                        <span className="text-gray-500 dark:text-[#a1a1aa] flex items-center gap-1.5"><CheckCircle size={14} className="text-gray-400"/> Conclusão</span>
                                        <span className="font-medium text-gray-900 dark:text-[#fafafa]">{completedAt.toLocaleDateString('pt-BR')}</span>
                                     </div>
                                     <div className="flex justify-between items-center text-[12px]">
                                        <span className="text-gray-500 dark:text-[#a1a1aa] flex items-center gap-1.5"><Clock size={14} className="text-gray-400"/> Prazo Base</span>
                                        <span className="font-medium text-gray-900 dark:text-[#fafafa]">{deadline.toLocaleDateString('pt-BR')}</span>
                                     </div>
                                  </div>
                                  
                               </div>
                            </div>
                          )}
                       </div>
                     );
                  })}`;

if (c.includes(target)) {
  c = c.replace(target, replacement);
  fs.writeFileSync("src/pages/driver/Profile.tsx", c, "utf-8");
  console.log("Success");
} else {
  // Let's try simpler replacement inside the map
  const alternateReplaceBaseTarget = `                    return (
                      <div key={job.id} className="bg-white dark:bg-[#1A1F26] border border-gray-100 dark:border-[#2A2F3A] rounded-2xl p-4 flex items-center justify-between shadow-sm dark:shadow-none">
                         <div className="flex items-center gap-4 min-w-0">
                            <div className="w-10 h-10 rounded-full bg-gray-50 dark:bg-[#1A1F26] border border-gray-100 dark:border-[#2A2F3A] flex items-center justify-center shrink-0">
                               <CheckCircle size={18} className="text-gray-400" />
                            </div>
                            <div className="min-w-0">
                               <h4 className="font-semibold text-gray-900 dark:text-[#fafafa] text-[14px] tracking-tight truncate">{contract?.name || 'Contrato'}</h4>
                               <div className="flex flex-col gap-0.5 mt-0.5">
                                  <span className="text-[12px] text-gray-500 dark:text-[#a1a1aa] flex items-center gap-1.5 truncate">
                                     {job.progress} entregas • {vehicle?.name || 'N/A'}
                                  </span>
                                  {trailer && (
                                    <span className="text-[11px] text-gray-400 flex items-center gap-1.5 truncate">
                                       {trailer.name}
                                    </span>
                                  )}
                               </div>
                            </div>
                         </div>
                      </div>
                    );`;

  const altReplacement = `                    const completedAt = job.completedAt ? new Date(job.completedAt) : new Date();
                    const deadline = new Date(job.deadlineDate);
                    const onTime = completedAt <= deadline;
                    const isExpanded = expandedJobId === job.id;

                    return (
                      <div key={job.id} className={cn("bg-white dark:bg-[#1A1F26] border rounded-2xl transition-all shadow-sm dark:shadow-none overflow-hidden", isExpanded ? "border-gray-300 dark:border-gray-600 ring-1 ring-gray-200 dark:ring-gray-700 block" : "border-gray-100 dark:border-[#2A2F3A] hover:border-gray-200 dark:hover:border-gray-700")}>
                         <button 
                           className="w-full flex items-center justify-between p-4 focus:outline-none" 
                           onClick={() => setExpandedJobId(isExpanded ? null : job.id)}
                         >
                           <div className="flex items-center gap-4 min-w-0 pr-4">
                              <div className={cn("w-10 h-10 rounded-full flex items-center justify-center shrink-0 border", onTime ? "bg-green-50 dark:bg-green-900/20 border-green-100 dark:border-green-800/30 text-green-600 dark:text-green-400" : "bg-orange-50 dark:bg-orange-900/20 border-orange-100 dark:border-orange-800/30 text-orange-600 dark:text-orange-400")}>
                                 {onTime ? <CheckCircle size={18} /> : <AlertCircle size={18} />}
                              </div>
                              <div className="min-w-0 text-left">
                                 <h4 className="font-semibold text-gray-900 dark:text-[#fafafa] text-[14px] tracking-tight truncate">{contract?.name || 'Contrato'}</h4>
                                 <div className="flex items-center gap-1.5 mt-0.5">
                                    <span className={cn("text-[11px] font-semibold tracking-tight uppercase", onTime ? "text-green-600" : "text-orange-600")}>
                                       {onTime ? 'No prazo' : 'Atrasado'}
                                    </span>
                                    <span className="text-gray-300 dark:text-gray-600 text-[10px]">•</span>
                                    <span className="text-[12px] text-gray-500 dark:text-[#a1a1aa] truncate flex-1">
                                       {job.progress} entregas
                                    </span>
                                 </div>
                              </div>
                           </div>
                           <div className="text-gray-400 dark:text-gray-500 shrink-0">
                              {isExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                           </div>
                         </button>
                         
                         {isExpanded && (
                           <div className="px-4 pb-4 animate-in fade-in slide-in-from-top-2 duration-200">
                              <div className="pt-3 border-t border-gray-100 dark:border-[#2A2F3A] flex flex-col gap-3">
                                 
                                 <div className="grid grid-cols-2 gap-3">
                                   <div className="bg-gray-50 dark:bg-[#09090b] rounded-xl p-3 border border-gray-100 dark:border-[#2A2F3A]">
                                     <span className="text-[11px] text-gray-500 dark:text-[#a1a1aa] font-medium tracking-tight mb-1 block">Veículo</span>
                                     <div className="flex items-center gap-1.5 min-w-0">
                                       <Car size={13} className="text-gray-400 shrink-0"/>
                                       <span className="text-[13px] font-semibold text-gray-900 dark:text-[#fafafa] truncate">{vehicle?.name || 'N/A'}</span>
                                     </div>
                                   </div>
                                   
                                   <div className="bg-gray-50 dark:bg-[#09090b] rounded-xl p-3 border border-gray-100 dark:border-[#2A2F3A]">
                                     <span className="text-[11px] text-gray-500 dark:text-[#a1a1aa] font-medium tracking-tight mb-1 block">Reboque</span>
                                     <div className="flex items-center gap-1.5 min-w-0">
                                       <Truck size={13} className="text-gray-400 shrink-0"/>
                                       <span className="text-[13px] font-semibold text-gray-900 dark:text-[#fafafa] truncate">{trailer?.name || 'N/A'}</span>
                                     </div>
                                   </div>
                                 </div>

                                 <div className="flex flex-col gap-2 mt-1">
                                    <div className="flex justify-between items-center text-[12px]">
                                       <span className="text-gray-500 dark:text-[#a1a1aa] flex items-center gap-1.5"><CheckCircle size={14} className="text-gray-400"/> Conclusão</span>
                                       <span className="font-medium text-gray-900 dark:text-[#fafafa]">{completedAt.toLocaleDateString('pt-BR')}</span>
                                    </div>
                                    <div className="flex justify-between items-center text-[12px]">
                                       <span className="text-gray-500 dark:text-[#a1a1aa] flex items-center gap-1.5"><Clock size={14} className="text-gray-400"/> Prazo Base</span>
                                       <span className="font-medium text-gray-900 dark:text-[#fafafa]">{deadline.toLocaleDateString('pt-BR')}</span>
                                    </div>
                                 </div>
                                 
                              </div>
                           </div>
                         )}
                      </div>
                    );`;

  if (c.includes(alternateReplaceBaseTarget)) {
    c = c.replace(alternateReplaceBaseTarget, altReplacement);
    fs.writeFileSync("src/pages/driver/Profile.tsx", c, "utf-8");
    console.log("Success with alt target");
  } else {
    console.log("Both target styles not found.");
  }
}
