import fs from 'node:fs';
import path from 'node:path';

const source = [
  'src/lib/gtoObserver.ts',
  'src/services/gtoCanonicalState.ts',
  'src/hooks/useGtoCanonicalState.ts',
  'src/components/GtoObserverSetup.tsx',
  'src/pages/driver/Dashboard.tsx',
];
const assets = 'android/app/src/main/assets/public/assets';
const latestSource = Math.max(...source.map(p=>fs.statSync(p).mtimeMs));
const assetFiles = fs.readdirSync(assets).filter(f=>f.endsWith('.js'));
const latestAsset = Math.max(...assetFiles.map(f=>fs.statSync(path.join(assets,f)).mtimeMs));
console.log(`source_latest=${new Date(latestSource).toISOString()}`);
console.log(`asset_latest=${new Date(latestAsset).toISOString()}`);
if (latestSource > latestAsset) {
  console.error('FAIL packaged Capacitor Web assets are older than current source Web code; run npm run build && npx cap sync android before release.');
  process.exit(1);
}
console.log('PASS packaged Web assets are at least as new as source code');
