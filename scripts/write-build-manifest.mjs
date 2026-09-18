import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = process.cwd();
const dist = resolve(root, "dist");
const packageJson = JSON.parse(
  await readFile(resolve(root, "package.json"), "utf8"),
);

// Netlify exposes DEPLOY_ID for every deploy. Local builds still receive a
// unique id so an Android WebView can detect a newly published web bundle.
const explicitId = String(process.env.NVU_BUILD_ID || "").trim();
const deployId = String(process.env.DEPLOY_ID || "").trim();
const commitRef = String(process.env.COMMIT_REF || "").trim();
const buildId =
  explicitId ||
  [commitRef || "local", deployId || new Date().toISOString()].join("-");
const runtimeRevision = String(
  process.env.NVU_RUNTIME_REVISION ||
    "R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR",
).trim();
const nativeChannel = String(
  process.env.NVU_NATIVE_CHANNEL || "production-358",
).trim();
const otaEnabled = process.env.CAPACITOR_REMOTE_ENABLED === "true";

await mkdir(dist, { recursive: true });
await writeFile(
  resolve(dist, "nvu-build.json"),
  `${JSON.stringify(
    {
      buildId,
      version: String(packageJson.version || "unknown"),
      generatedAt: new Date().toISOString(),
      source: "nvu-web",
      runtimeRevision,
      nativeChannel,
      otaEnabled,
    },
    null,
    2,
  )}\n`,
  "utf8",
);
