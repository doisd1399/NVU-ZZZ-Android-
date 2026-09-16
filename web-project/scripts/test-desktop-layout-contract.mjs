import fs from "node:fs";

const read = (file) => fs.readFileSync(file, "utf8");
const fleet = read("src/pages/admin/Fleet.tsx");
const layout = read("src/layouts/AdminLayout.tsx");
const drivers = read("src/pages/admin/fleet/DriversTab.tsx");
const css = read("src/index.css");

const checks = [
  ["menu do shell não fica restrito ao mobile", /nvu-admin-menu-button inline-flex/ .test(layout)],
  ["menu desktop mantém estado acessível", /aria-expanded=\{isMobileMenuOpen\}/.test(layout)],
  ["container desktop usa largura controlada", /lg:max-w-\[1280px\]/.test(fleet)],
  ["shell administrativo centraliza o conteúdo", /lg:max-w-\[1440px\] lg:mx-auto/.test(layout)],
  ["menu desktop usa largura compacta", /width: min\(360px/.test(css)],
  ["botões do menu desktop são compactos", /min-height: 2\.25rem/.test(css)],
  ["banner desktop fica mais compacto", /lg:h-\[248px\]/.test(fleet)],
  ["cards usam três colunas em desktop amplo", /xl:grid-cols-3/.test(drivers)],
  ["cards usam quatro colunas em telas muito largas", /2xl:grid-cols-4/.test(drivers)],
];

let failures = 0;
for (const [label, passed] of checks) {
  console.log(`${passed ? "PASS" : "FAIL"} ${label}`);
  if (!passed) failures += 1;
}
if (failures) process.exit(1);
console.log(`Desktop layout contract: ${checks.length}/${checks.length} PASS`);
