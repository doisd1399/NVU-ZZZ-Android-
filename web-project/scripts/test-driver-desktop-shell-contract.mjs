import fs from "node:fs";

const layout = fs.readFileSync("src/layouts/DriverLayout.tsx", "utf8");
const menu = fs.readFileSync("src/components/GlobalMenu.tsx", "utf8");
const profile = fs.readFileSync("src/pages/driver/Profile.tsx", "utf8");
const css = fs.readFileSync("src/index.css", "utf8");

const checks = [
  ["menu do motorista acessível no desktop", /nvu-native-menu-button inline-flex/.test(layout)],
  ["conteúdo desktop do motorista centralizado", /lg:max-w-\[1440px\] lg:mx-auto/.test(layout)],
  ["gutter desktop preservado no conteúdo", /lg:px-8 lg:py-6/.test(layout)],
  ["menu global marcado como flutuante desktop", /nvu-desktop-floating-menu/.test(menu)],
  ["menu desktop centralizado abaixo do header", /left: 50% !important;[\s\S]{0,350}translateX\(-50%\)/.test(css)],
  ["perfil desktop usa largura de leitura", /lg:max-w-\[1280px\] lg:mx-auto/.test(profile)],
  ["hero do perfil compacto em desktop", /lg:h-\[248px\]/.test(profile)],
];

let failures = 0;
for (const [label, passed] of checks) {
  console.log(`${passed ? "PASS" : "FAIL"} ${label}`);
  if (!passed) failures += 1;
}
if (failures) process.exit(1);
console.log(`Driver desktop shell contract: ${checks.length}/${checks.length} PASS`);
