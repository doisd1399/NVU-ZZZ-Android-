import fs from "node:fs";
import path from "node:path";

const file = path.resolve(process.cwd(), "capacitor.remote.json");
if (!fs.existsSync(file)) {
  throw new Error("capacitor.remote.json não encontrado.");
}

const config = JSON.parse(fs.readFileSync(file, "utf8"));
const url = String(config?.url || "").trim();

if (config?.enabled !== true || !url) {
  throw new Error(
    "Build Android bloqueado: o runtime remoto do Netlify não está habilitado em capacitor.remote.json.",
  );
}

const parsed = new URL(url);
if (parsed.protocol !== "https:") {
  throw new Error("A URL remota do Capacitor precisa usar HTTPS.");
}

console.log(`✓ Capacitor Android configurado para runtime remoto: ${parsed.origin}`);
