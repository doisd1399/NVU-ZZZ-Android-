import fs from "node:fs";
import path from "node:path";

const dist = path.resolve(process.cwd(), "dist");
const androidWebAssets = path.resolve(
  process.cwd(),
  "android/app/src/main/assets/public",
);

// Capacitor may overlay the new Vite output on top of an existing asset folder.
// Hashed chunks that disappeared from the latest manifest would then remain inside
// the APK. Remove the complete generated WebView tree before every copy so the
// embedded fallback is an exact, deterministic mirror of dist.
if (fs.existsSync(androidWebAssets)) {
  fs.rmSync(androidWebAssets, { recursive: true, force: true });
}

for (const name of ["server.cjs", "server.cjs.map"]) {
  const target = path.join(dist, name);
  if (fs.existsSync(target)) fs.rmSync(target, { force: true });
}
console.log("Capacitor assets preparados: fallback anterior limpo e artefatos Node removidos do dist.");
