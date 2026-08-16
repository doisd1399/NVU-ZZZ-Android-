import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/SeniorPanel.tsx', 'utf8');

// Use isSeniorAuthenticated from AppContext
content = content.replace(
  'const [password, setPassword] = useState("");\n  const [isAuthenticated, setIsAuthenticated] = useState(false);',
  'const { isSeniorAuthenticated, setIsSeniorAuthenticated, setActiveCompanyId, setActiveRole, setSeniorCompanyId } = useAppStore();\n  const [password, setPassword] = useState("");'
);

content = content.replace(
  /if \(password === "9173"\) \{\n      setIsAuthenticated\(true\);\n    \}/g,
  `if (password === "9173") {\n      setIsSeniorAuthenticated(true);\n    }`
);

content = content.replace(
  /if \(!isAuthenticated\) \{/g,
  `if (!isSeniorAuthenticated) {`
);

// We need to change `viewCompanyProfile` to navigate to fleet
content = content.replace(
  /const viewCompanyProfile = \(companyId: string\) => \{\n    setSelectedCompanyId\(companyId\);\n    setActiveTab\("profile"\);\n  \};/g,
  `const viewCompanyProfile = (companyId: string) => {\n    setSeniorCompanyId(companyId);\n    setActiveCompanyId(companyId);\n    setActiveRole("admin");\n    window.location.href = "/admin/fleet";\n  };`
);

fs.writeFileSync('src/pages/admin/SeniorPanel.tsx', content);
