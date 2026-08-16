import fs from "fs";

let c = fs.readFileSync("src/pages/admin/Dashboard.tsx", "utf-8");

// 1. Add state
c = c.replace(
  "const [activeMobileSection, setActiveMobileSection] = useState<string | null>(null);",
  'const [activeMobileSection, setActiveMobileSection] = useState<string | null>(null);\n  const [activeJobsFilter, setActiveJobsFilter] = useState<"all" | "attention" | "late">("all");',
);

// 2. Add filtered list
const filteredListCode = `
  const filteredActiveJobsList = activeJobsList.filter(job => {
    if (activeJobsFilter === 'late') return job.daysLeft < 0;
    if (activeJobsFilter === 'attention') return job.daysLeft >= 0 && job.daysLeft <= 2;
    return true;
  });
`;

c = c.replace(
  "const pendingJobs = activeJobsList.filter(j => j.status === 'pending').length;",
  filteredListCode +
    "\n  const pendingJobs = activeJobsList.filter(j => j.status === 'pending').length;",
);

// 3. Add to mobile section
const mobileHeaderTarget = `           <div className="w-full flex items-center justify-between p-3 bg-white dark:bg-[#1A1F26] border-b border-gray-100 dark:border-[#2A2F3A]">
             <span className="font-bold text-[15px]">Trabalhos Ativos</span>
           </div>`;

const mobileHeaderReplacement = `           <div className="w-full flex items-center justify-between p-3 bg-white dark:bg-[#1A1F26] border-b border-gray-100 dark:border-[#2A2F3A]">
             <span className="font-bold text-[15px]">Trabalhos Ativos</span>
             <div className="relative inline-flex items-center">
                <Filter size={14} className="absolute left-2.5 text-gray-400" />
                <select 
                   className="pl-8 pr-7 py-1.5 text-[12px] font-medium bg-gray-50 dark:bg-[#09090b] border border-gray-200 dark:border-[#2A2F3A] rounded-lg text-gray-700 dark:text-[#a1a1aa] appearance-none focus:outline-none focus:ring-2 focus:ring-blue-500/20 shadow-sm dark:shadow-none"
                   value={activeJobsFilter}
                   onChange={(e) => setActiveJobsFilter(e.target.value as any)}
                >
                   <option value="all">Todos</option>
                   <option value="attention">Atenção</option>
                   <option value="late">Atrasados</option>
                </select>
                <ChevronDown size={14} className="absolute right-2 text-gray-500 pointer-events-none" />
             </div>
           </div>`;

c = c.replace(mobileHeaderTarget, mobileHeaderReplacement);

// 4. Add to desktop section
const desktopHeaderTarget = `           <div className="flex items-center justify-between px-1">
             <h2 className="text-base font-bold text-gray-900 dark:text-[#fafafa]">Trabalhos Ativos</h2>
             <button onClick={() => navigate('/admin/operations')} className="text-sm text-green-600 font-bold hover:text-green-700 dark:text-green-400">Ver todos</button>
           </div>`;

const desktopHeaderReplacement = `           <div className="flex items-center justify-between px-1">
             <h2 className="text-base font-bold text-gray-900 dark:text-[#fafafa]">Trabalhos Ativos</h2>
             <div className="flex items-center gap-3">
               <div className="relative inline-flex items-center">
                  <Filter size={14} className="absolute left-2.5 text-gray-400" />
                  <select 
                     className="pl-8 pr-7 py-1.5 text-[12px] font-medium bg-gray-50 dark:bg-[#09090b] border border-gray-200 dark:border-[#2A2F3A] rounded-lg text-gray-700 dark:text-[#a1a1aa] appearance-none focus:outline-none focus:ring-2 focus:ring-blue-500/20 shadow-sm dark:shadow-none"
                     value={activeJobsFilter}
                     onChange={(e) => setActiveJobsFilter(e.target.value as any)}
                  >
                     <option value="all">Todos</option>
                     <option value="attention">Atenção</option>
                     <option value="late">Atrasados</option>
                  </select>
                  <ChevronDown size={14} className="absolute right-2 text-gray-500 pointer-events-none" />
               </div>
               <button onClick={() => navigate('/admin/operations')} className="text-sm text-green-600 font-bold hover:text-green-700 dark:text-green-400">Ver todos</button>
             </div>
           </div>`;

c = c.replace(desktopHeaderTarget, desktopHeaderReplacement);

// 5. Replace references
c = c.replace(
  /{activeJobsList\.slice\(0, 5\)\.map\(job => {/g,
  "{filteredActiveJobsList.slice(0, 5).map(job => {",
);
c = c.replace(
  /{activeJobsList\.length === 0 \? \(/g,
  "{filteredActiveJobsList.length === 0 ? (",
);
c = c.replace(
  /activeJobsList\.map\(job => {/g,
  "filteredActiveJobsList.map(job => {",
);

fs.writeFileSync("src/pages/admin/Dashboard.tsx", c, "utf-8");
console.log("Done");
