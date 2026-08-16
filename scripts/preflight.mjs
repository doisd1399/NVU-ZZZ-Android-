import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const root = process.cwd();
const mode = process.argv[2] ?? '--full';
const failures = [];
const warnings = [];

function fail(message) { failures.push(message); }
function warn(message) { warnings.push(message); }
function readJson(relativePath) {
  const absolutePath = path.join(root, relativePath);
  if (!fs.existsSync(absolutePath)) {
    fail(`Arquivo obrigatório ausente: ${relativePath}`);
    return null;
  }
  try {
    return JSON.parse(fs.readFileSync(absolutePath, 'utf8'));
  } catch (error) {
    fail(`JSON inválido em ${relativePath}: ${error.message}`);
    return null;
  }
}

const nodeMajor = Number(process.versions.node.split('.')[0]);
if (!Number.isFinite(nodeMajor) || nodeMajor < 22) {
  fail(`Node.js ${process.versions.node} detectado. Este projeto exige Node.js 22 ou superior.`);
}

const packageJson = readJson('package.json');
const packageLock = readJson('package-lock.json');
if (packageJson && packageLock) {
  if (packageJson.name !== packageLock.name) {
    fail('package.json e package-lock.json possuem nomes divergentes.');
  }
  const lockText = fs.readFileSync(path.join(root, 'package-lock.json'), 'utf8');
  if (/internal\.api\.openai\.org|applied-caas-gateway|artifactory\/api\/npm/i.test(lockText)) {
    fail('O package-lock.json ainda contém uma URL interna e não pode ser usado fora do ambiente de geração.');
  }
}

const npmrcPath = path.join(root, '.npmrc');
if (!fs.existsSync(npmrcPath)) {
  fail('Arquivo .npmrc ausente.');
} else {
  const npmrc = fs.readFileSync(npmrcPath, 'utf8');
  if (!/^registry=https:\/\/registry\.npmjs\.org\/?$/m.test(npmrc)) {
    fail('O .npmrc não aponta para https://registry.npmjs.org/.');
  }
}

const remoteConfig = readJson('capacitor.remote.json');
if (remoteConfig?.enabled) {
  try {
    const parsed = new URL(remoteConfig.url);
    if (parsed.protocol !== 'https:') fail('A URL remota do Capacitor precisa usar HTTPS.');
  } catch {
    fail('A URL em capacitor.remote.json é inválida.');
  }
}

if (mode === '--full' || mode === '--android') {
  const googleServices = readJson('android/app/google-services.json');
  const packageNames = googleServices?.client?.map(
    (client) => client?.client_info?.android_client_info?.package_name,
  ).filter(Boolean) ?? [];
  if (googleServices && !packageNames.includes('com.nvu.operacional')) {
    fail('O google-services.json não contém o pacote Android com.nvu.operacional.');
  }

  const requiredAndroidFiles = [
    'android/gradlew.bat',
    'android/settings.gradle',
    'android/app/build.gradle',
    'android/app/src/main/AndroidManifest.xml',
  ];
  for (const file of requiredAndroidFiles) {
    if (!fs.existsSync(path.join(root, file))) fail(`Arquivo Android obrigatório ausente: ${file}`);
  }
}

if (mode === '--full' && !fs.existsSync(path.join(root, 'node_modules'))) {
  warn('node_modules ainda não existe. Execute npm ci antes do build.');
}

for (const message of warnings) console.warn(`[AVISO] ${message}`);
if (failures.length) {
  for (const message of failures) console.error(`[ERRO] ${message}`);
  process.exit(1);
}
console.log('[OK] Pré-verificação do projeto concluída.');
