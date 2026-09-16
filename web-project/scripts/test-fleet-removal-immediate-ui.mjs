import fs from "node:fs";

const source = fs.readFileSync("src/context/AppContext.tsx", "utf8");
const driversTab = fs.readFileSync("src/pages/admin/fleet/DriversTab.tsx", "utf8");

const checks = [
  [
    "remoção otimista acontece somente após commit",
    /await removalBatch\.commit\(\);[\s\S]{0,900}setAllCompanyMembers\(/.test(source),
  ],
  [
    "projeção de membros remove o usuário e a empresa correta",
    /setAllCompanyMembers\([\s\S]{0,500}member\.companyId !== targetCompanyId \|\| member\.userId !== driverId/.test(source),
  ],
  [
    "projeção principal de usuários remove o funcionário",
    /setUsers\(\(currentUsers\) =>[\s\S]{0,180}user\.id !== driverId/.test(source),
  ],
  [
    "fallback de usuários ausentes também é limpo",
    /setFetchedMissingUsers\(\(currentUsers\) =>[\s\S]{0,220}user\.id !== driverId/.test(source),
  ],
  [
    "cards dependem da lista filtrada de membros ativos",
    /const allEmployees = React\.useMemo\([\s\S]{0,700}getDriverRoles\(u\)\.length > 0/.test(driversTab),
  ],
];

let failures = 0;
for (const [label, passed] of checks) {
  console.log(`${passed ? "PASS" : "FAIL"} ${label}`);
  if (!passed) failures += 1;
}
if (failures) process.exit(1);
console.log(`Fleet removal immediate UI regression: ${checks.length}/${checks.length} PASS`);
