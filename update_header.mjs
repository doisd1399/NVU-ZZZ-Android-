import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/fleet/ContractsTab.tsx', 'utf8');

// We need to add state for folder dropdown:
// const [openFolderDropdownId, setOpenFolderDropdownId] = useState<string | null>(null);
content = content.replace(
  'const [openDropdownId, setOpenDropdownId] = useState<string | null>(null);',
  'const [openDropdownId, setOpenDropdownId] = useState<string | null>(null);\n  const [openFolderDropdownId, setOpenFolderDropdownId] = useState<string | null>(null);'
);

// We replace the Group Header block
const groupHeaderRegex = /\{\/\* Group Header \*\/\}.*?\{\/\* Group Grid \*\/\}/s;
const newGroupHeader = `{/* Group Header */}
                 <div 
                   className="flex items-center justify-between group/header cursor-pointer py-2 px-3 hover:bg-gray-50 dark:hover:bg-[#1A1F26] rounded-lg transition-colors border border-transparent hover:border-gray-200 dark:hover:border-[#2A2F3A] mb-2"
                   onClick={() => toggleSequence(group.seq.id)}
                 >
                   <div className="flex items-center gap-3">
                     <div className="w-8 h-8 rounded-lg bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center text-blue-600 dark:text-blue-500">
                       <FolderTree size={16} className={group.seq.id === "none" ? "text-gray-400" : ""} />
                     </div>
                     <h3 className="font-semibold text-[15px] text-gray-900 dark:text-[#fafafa]">
                       {group.seq.name}
                     </h3>
                     <span className="text-[12px] font-medium px-2 py-0.5 rounded-full bg-gray-100 dark:bg-[#2A2F3A] text-gray-600 dark:text-[#a1a1aa]">
                       {group.contracts.length}
                     </span>
                   </div>
                   
                   {group.seq.id !== "none" && (
                     <div className="relative" onClick={e => e.stopPropagation()}>
                       <button
                         onClick={() => setOpenFolderDropdownId(openFolderDropdownId === group.seq.id ? null : group.seq.id)}
                         className="p-1.5 text-gray-400 hover:text-gray-700 dark:text-[#d4d4d8] dark:hover:text-[#d4d4d8] rounded-lg hover:bg-gray-100 dark:bg-transparent dark:hover:bg-[#3f3f46] transition-colors"
                       >
                         <MoreVertical size={18} />
                       </button>
                       {openFolderDropdownId === group.seq.id && (
                         <>
                           <div className="fixed inset-0 z-40 bg-transparent" onClick={() => setOpenFolderDropdownId(null)}></div>
                           <div className="absolute right-0 top-8 w-48 bg-white dark:bg-[#1A1F26] border border-gray-200 dark:border-[#2A2F3A] rounded-xl shadow-lg z-50 py-1 origin-top-right animate-in fade-in zoom-in-95 duration-100">
                             <button
                               onClick={() => {
                                 setOpenFolderDropdownId(null);
                                 setEditSequenceId(group.seq.id);
                                 setSequenceName(group.seq.name);
                                 setSequenceDesc(group.seq.description || "");
                                 setSelectedContracts(group.contracts.map(c => c.id));
                                 setIsSequenceModalOpen(true);
                               }}
                               className="w-full text-left px-4 py-2 text-[13px] font-medium text-gray-700 dark:text-[#d4d4d8] hover:bg-gray-50 dark:hover:bg-[#3f3f46] flex items-center gap-2"
                             >
                               <Edit2 size={14} className="text-gray-400" />
                               Editar Pasta
                             </button>
                             <button
                               onClick={() => {
                                 setOpenFolderDropdownId(null);
                                 setSequenceToDelete(group.seq.id);
                               }}
                               className="w-full text-left px-4 py-2 text-[13px] font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10 flex items-center gap-2"
                             >
                               <Trash2 size={14} className="text-red-500" />
                               Excluir Pasta
                             </button>
                           </div>
                         </>
                       )}
                     </div>
                   )}
                 </div>
                 
                 {/* Group Grid */}`;

content = content.replace(groupHeaderRegex, newGroupHeader);
fs.writeFileSync('src/pages/admin/fleet/ContractsTab.tsx', content);
