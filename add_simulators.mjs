import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

// 1. Add Simulator interface
if (!content.includes('export interface Simulator {')) {
  content = content.replace(
    'export interface CompanyProfile {',
    `export interface Simulator {
  id: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyProfile {`
  );
}

// 2. Add to AppContextType
if (!content.includes('simulators: Simulator[];')) {
  content = content.replace(
    'companies: CompanyProfile[];',
    `simulators: Simulator[];\n  companies: CompanyProfile[];`
  );
}

// 3. Add to useState in AppProvider
if (!content.includes('const [simulators, setSimulators] = useState<Simulator[]>(')) {
  content = content.replace(
    'const [companies, setCompanies] = useState<CompanyProfile[]>([]);',
    `const [simulators, setSimulators] = useState<Simulator[]>([]);\n  const [companies, setCompanies] = useState<CompanyProfile[]>([]);`
  );
}

// 4. Add to context provider value
if (!content.includes('simulators,\n')) {
  content = content.replace(
    'companies,\n',
    `simulators,\n      companies,\n`
  );
}

fs.writeFileSync('src/context/AppContext.tsx', content);
console.log('Added state and interfaces.');
