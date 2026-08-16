import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

if (!content.includes('isSeniorAuthenticated')) {
  // 1. Add to AppContextType
  content = content.replace(
    /interface AppContextType \{/,
    `interface AppContextType {\n  isSeniorAuthenticated: boolean;\n  setIsSeniorAuthenticated: (val: boolean) => void;\n  seniorCompanyId: string | null;\n  setSeniorCompanyId: (val: string | null) => void;`
  );

  // 2. Add to Provider state
  content = content.replace(
    /const \[activeRole, setActiveRole\] = useState<Role \| null>\(\(\) => \{/,
    `const [isSeniorAuthenticated, setIsSeniorAuthenticated] = useState<boolean>(() => sessionStorage.getItem("isSeniorAuthenticated") === "true");\n  const [seniorCompanyId, setSeniorCompanyId] = useState<string | null>(() => sessionStorage.getItem("seniorCompanyId"));\n  const [activeRole, setActiveRole] = useState<Role | null>(() => {`
  );

  // 3. Save to storage on change
  content = content.replace(
    /useEffect\(\(\) => \{\n    if \(activeCompanyId\) \{/,
    `useEffect(() => {\n    if (isSeniorAuthenticated) {\n      sessionStorage.setItem("isSeniorAuthenticated", "true");\n    } else {\n      sessionStorage.removeItem("isSeniorAuthenticated");\n    }\n    if (seniorCompanyId) {\n      sessionStorage.setItem("seniorCompanyId", seniorCompanyId);\n    } else {\n      sessionStorage.removeItem("seniorCompanyId");\n    }\n  }, [isSeniorAuthenticated, seniorCompanyId]);\n\n  useEffect(() => {\n    if (activeCompanyId) {`
  );

  // 4. Update the return payload
  content = content.replace(
    /return \{\n      currentUser,/,
    `return {\n      isSeniorAuthenticated,\n      setIsSeniorAuthenticated,\n      seniorCompanyId,\n      setSeniorCompanyId,\n      currentUser,`
  );
  
  // 5. Update companies filter
  content = content.replace(
    /companies: companies.filter\(c => memberships\?\.some\(m => m\.companyId === c\.id\)\),/g,
    `companies: isSeniorAuthenticated ? companies : companies.filter(c => memberships?.some(m => m.companyId === c.id)),`
  );

  fs.writeFileSync('src/context/AppContext.tsx', content);
}
