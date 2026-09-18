import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const url = 'https://stirring-pavlova-ca6808.netlify.app';
const dist = path.join(root, 'dist');
const assets = path.join(dist, 'assets');
const files = [
  'assets/index-BVfMD3P1.js',
  'assets/index-OiE9rkRc.css',
  'assets/vendor-BbvKNChJ.js',
  'assets/vendor-D6alEHDY.css',
  'assets/vendor-icons-DnfiRwyI.js',
];

fs.rmSync(dist, { recursive: true, force: true });
fs.mkdirSync(assets, { recursive: true });
for (const relative of files) {
  execFileSync('curl', ['--fail', '--silent', '--show-error', '--location', `${url}/${relative}`, '--output', path.join(dist, relative)]);
}
execFileSync('curl', ['--fail', '--silent', '--show-error', '--location', `${url}/`, '--output', path.join(dist, 'index.html')]);
const remoteManifest = JSON.parse(execFileSync('curl', ['--fail', '--silent', '--show-error', '--location', `${url}/nvu-build.json`], { encoding: 'utf8' }));
const localManifest = {
  ...remoteManifest,
  buildId: `bundled-reference-${remoteManifest.buildId}`,
  generatedAt: new Date().toISOString(),
  source: 'nvu-web-remote-reference-bundled',
  capacitorRuntime: 'local',
  otaEnabled: false,
  otaManifestUrl: undefined,
};
fs.writeFileSync(path.join(dist, 'nvu-build.json'), `${JSON.stringify(localManifest, (_, value) => value === undefined ? undefined : value, 2)}\n`);
console.log(JSON.stringify(localManifest, null, 2));
console.log(`Imported ${files.length + 2} files from ${url} into ${dist}`);
files.forEach((relative) => console.log(relative));
console.log('index.html');
console.log('nvu-build.json');
