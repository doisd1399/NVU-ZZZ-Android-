import type { CapacitorConfig } from '@capacitor/cli';
import fs from 'node:fs';
import path from 'node:path';

type RemoteConfig = { enabled?: boolean; url?: string };

function loadRemoteServer(): CapacitorConfig['server'] {
  const file = path.resolve(process.cwd(), 'capacitor.remote.json');
  if (!fs.existsSync(file)) {
    throw new Error('capacitor.remote.json é obrigatório no build de produção do APK.');
  }
  const cfg = JSON.parse(fs.readFileSync(file, 'utf8')) as RemoteConfig;
  const url = cfg.url?.trim();
  if (!cfg.enabled || !url) {
    throw new Error('Runtime remoto do Netlify está desabilitado. O APK de produção deve usar o deploy web.');
  }
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:') throw new Error('A URL remota do Capacitor precisa usar HTTPS.');
  return { url: parsed.toString().replace(/\/$/, ''), cleartext: false, androidScheme: 'https' };
}

const config: CapacitorConfig = {
  appId: 'com.nvu.operacional',
  appName: 'nvu',
  webDir: 'dist',
  server: loadRemoteServer(),
  plugins: {
    FirebaseAuthentication: { skipNativeAuth: false, providers: ['google.com'] },
    PushNotifications: { presentationOptions: ['badge', 'sound', 'alert'] },
  },
};

export default config;
