import fs from 'fs';

let content = fs.readFileSync('src/pages/driver/JoinCompany.tsx', 'utf8');

// 1. Add simulators to destructuring and import resolveSimulatorId
content = content.replace(
  '  const {',
  '  const {\n    simulators,'
);
content = content.replace(
  'import { Button } from "../../components/ui/Button";',
  'import { Button } from "../../components/ui/Button";\nimport { resolveSimulatorId } from "../../lib/resolveSimulator";'
);

// 2. Add state
content = content.replace(
  '  const [searchTerm, setSearchTerm] = useState("");',
  '  const [searchTerm, setSearchTerm] = useState("");\n  const [selectedSimulatorId, setSelectedSimulatorId] = useState("");'
);

// 3. Filter active simulators
content = content.replace(
  '  const [isRequestingId, setIsRequestingId] = useState<string | null>(null);',
  '  const [isRequestingId, setIsRequestingId] = useState<string | null>(null);\n\n  const activeSimulators = simulators.filter((s:any) => s.active !== false).sort((a:any, b:any) => a.name.localeCompare(b.name));'
);

// 4. Update filtering logic
content = content.replace(
  `  const filteredCompanies = companiesWithCount.filter((c) =>
    c.companyName.toLowerCase().includes(searchTerm.toLowerCase()),
  );`,
  `  const filteredCompanies = companiesWithCount.filter((c) => {
    if (!selectedSimulatorId) return false;
    const matchesSimulator = (c.simulatorId || resolveSimulatorId(c)) === selectedSimulatorId;
    const matchesSearch = c.companyName.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesSimulator && matchesSearch;
  });`
);

// 5. Add select HTML
const searchInputHtml = `{/* SEARCH INPUT */}
      <div className="relative mb-8 group max-w-2xl mx-auto px-4 sm:px-0">`;

const replaceHtml = `{/* SIMULATOR SELECT */}
      <div className="max-w-2xl mx-auto px-4 sm:px-0 mb-4">
        <label className="block text-[13px] font-medium text-slate-700 dark:text-[#d4d4d8] mb-1.5 ml-0.5">
          Simulador
        </label>
        <select
          value={selectedSimulatorId}
          onChange={(e) => setSelectedSimulatorId(e.target.value)}
          className="w-full bg-white dark:bg-[#09090b] border border-slate-200 dark:border-[#2A2F3A] rounded-xl px-4 h-14 text-[15px] outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 dark:text-[#fafafa] transition-all shadow-sm appearance-none cursor-pointer"
        >
          <option value="" disabled>Selecione um simulador</option>
          {activeSimulators.map((sim:any) => (
            <option key={sim.id} value={sim.id}>
              {sim.name}
            </option>
          ))}
        </select>
      </div>

      {/* SEARCH INPUT */}
      <div className="relative mb-8 group max-w-2xl mx-auto px-4 sm:px-0">`;

content = content.replace(searchInputHtml, replaceHtml);

const inputElement = `<input
          type="text"
          placeholder="Buscar empresa ou frota..."
          autoFocus
          className="w-full pl-12 sm:pl-14 pr-4 h-14 bg-white dark:bg-[#1A1F26] rounded-2xl border border-gray-200 dark:border-[#2A2F3A] focus:outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 text-[15px] font-medium text-gray-900 dark:text-[#fafafa] placeholder:text-gray-400 dark:placeholder:text-[#71717a] shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)] transition-all"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />`;

const newInputElement = `<input
          type="text"
          disabled={!selectedSimulatorId}
          placeholder={selectedSimulatorId ? "Buscar empresa ou frota..." : "Selecione um simulador antes"}
          autoFocus
          className="w-full pl-12 sm:pl-14 pr-4 h-14 bg-white dark:bg-[#1A1F26] rounded-2xl border border-gray-200 dark:border-[#2A2F3A] focus:outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 text-[15px] font-medium text-gray-900 dark:text-[#fafafa] placeholder:text-gray-400 dark:placeholder:text-[#71717a] shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />`;

content = content.replace(inputElement, newInputElement);

fs.writeFileSync('src/pages/driver/JoinCompany.tsx', content);
