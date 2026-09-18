import { auth, db, storage, functions } from "../lib/firebase";
import { Capacitor } from "@capacitor/core";
import { App } from "@capacitor/app";

export const REMOTE_APP_URL = String(import.meta.env.VITE_NVU_NETLIFY_URL || "").trim();

export interface DiagnosticResult {
  platform: string;
  isNativePlatform: boolean;
  remoteAppUrl: string;
  nativeVersion?: string;
  nativeBuild?: number;
  buildManifest?: any;
  manifestError?: string;
  firebase: {
    initialized: boolean;
    auth: string;
    firestore: string;
    storage: string;
    functions: string;
  };
}

export async function runDiagnostics(): Promise<DiagnosticResult> {
  const result: DiagnosticResult = {
    platform: Capacitor.getPlatform(),
    isNativePlatform: Capacitor.isNativePlatform(),
    remoteAppUrl: REMOTE_APP_URL || "Runtime local empacotado no APK",
    firebase: {
      initialized: false,
      auth: 'uninitialized',
      firestore: 'uninitialized',
      storage: 'uninitialized',
      functions: 'uninitialized',
    }
  };

  // Build manifest.version is the Web/package version, not the Android
  // version shown by the installed APK. Read the native
  // package identity from the App plugin so diagnostics never report an old
  // Web version as the Android release.
  try {
    const nativeInfo = await App.getInfo();
    result.nativeVersion = nativeInfo.version;
    result.nativeBuild = Number(nativeInfo.build);
  } catch {
    // Browser builds do not expose native package metadata.
  }

  try {
    // 1. Fetch Build Manifest
    // In a browser, this fetches from the current domain.
    // Always inspect the manifest served by the active WebView origin. For the
    // production APK this is the locally bundled Web asset, not the Netlify URL.
    const manifestUrl = `${window.location.origin}/nvu-build.json?t=${Date.now()}`;
    const manifestRes = await fetch(manifestUrl);
    
    if (manifestRes.ok) {
      result.buildManifest = await manifestRes.json();
    } else {
      result.manifestError = `HTTP ${manifestRes.status}: ${manifestRes.statusText}`;
    }
  } catch (error: any) {
    result.manifestError = error.message;
  }

  // 2. Firebase Check
  try {
    if (auth && db && storage && functions) {
      result.firebase.initialized = true;
      result.firebase.auth = auth.app ? 'ready' : 'error';
      result.firebase.firestore = db.app ? 'ready' : 'error';
      result.firebase.storage = storage.app ? 'ready' : 'error';
      result.firebase.functions = functions.app ? 'ready' : 'error';
    }
  } catch (err: any) {
    result.firebase.initialized = false;
  }

  return result;
}
