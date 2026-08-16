import fs from 'node:fs';
import path from 'node:path';

const destination = path.resolve(process.cwd(), 'capacitor.remote.json');
const argument = process.argv[2]?.trim();

if (argument === '--local') {
  fs.writeFileSync(
    destination,
    `${JSON.stringify({ enabled: false, url: '' }, null, 2)}\n`,
    'utf8',
  );
  console.log('Modo local ativado: o APK usará os arquivos da pasta dist.');
  process.exit(0);
}

if (!argument) {
  console.error('Informe a URL HTTPS do Netlify.');
  console.error('Exemplo: npm run cap:configure-netlify -- https://seu-site.netlify.app');
  process.exit(1);
}

let parsed;
try {
  parsed = new URL(argument);
} catch {
  console.error('URL inválida.');
  process.exit(1);
}

if (parsed.protocol !== 'https:') {
  console.error('A URL precisa começar com https://');
  process.exit(1);
}

const normalizedUrl = parsed.toString().replace(/\/$/, '');
fs.writeFileSync(
  destination,
  `${JSON.stringify({ enabled: true, url: normalizedUrl }, null, 2)}\n`,
  'utf8',
);

console.log(`Modo Netlify remoto configurado: ${normalizedUrl}`);
console.log('Próximo comando: npm run cap:sync:android');
