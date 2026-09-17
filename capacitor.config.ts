import type { CapacitorConfig } from '@capacitor/cli';
import fs from 'node:fs';
import path from 'node:path';

type RemoteConfig = { enabled?: boolean; url?: string };

function loadRemoteServer(): CapacitorConfig['server'] {
  // The production APK must use the Web assets copied into the native bundle
  // unless a deployment explicitly opts into a remotely published build. The
  // previous unconditional server.url made every APK ignore the freshly built
  // Web UI and silently load a stale Netlify deployment instead.
  if (process.env.CAPACITOR_REMOTE_ENABLED !== 'true') {
    return undefined;
  }

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

const remoteServer = loadRemoteServer();

const config: CapacitorConfig = {
  appId: 'com.nvu.operacional',
  appName: 'nvu',
  webDir: 'dist',
  ...(remoteServer ? { server: remoteServer } : {}),
  plugins: {
    FirebaseAuthentication: { skipNativeAuth: false, providers: ['google.com'] },
    PushNotifications: { presentationOptions: ['badge', 'sound', 'alert'] },
  },
};

export default config;
