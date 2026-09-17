import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const servicePath = path.join(root, "android/app/src/main/java/com/nvu/operacional/GtoObserverService.java");
const syncPath = path.join(root, "android/app/src/main/java/com/nvu/operacional/GtoAutoTripSync.java");
const service = fs.readFileSync(servicePath, "utf8");
const sync = fs.readFileSync(syncPath, "utf8");

const fail = (message) => {
  console.error(`FAIL: ${message}`);
  process.exit(1);
};
const requireText = (source, text, label) => {
  if (!source.includes(text)) fail(`${label}: trecho ausente: ${text}`);
};

const signatureStart = service.indexOf("private String menuRenderSignature() {");
const signatureEnd = service.indexOf("\n    private void refreshMenuContents()", signatureStart);
if (signatureStart < 0 || signatureEnd < 0) fail("menuRenderSignature não foi localizado");
const signature = service.slice(signatureStart, signatureEnd);

for (const field of [
  'prefs.getInt("gtoJobProgress", 0)',
  'prefs.getString("gtoJobStatus", "")',
  'prefs.getBoolean("gtoBackendJobClosed", false)',
  'prefs.getString("contractName", "")',
  'prefs.getString("jobId", "")',
  'prefs.getInt("jobTotalDeliveries", 0)',
  'prefs.getString("vehicleName", "")',
  'prefs.getString("trailerName", "")',
]) requireText(signature, field, "assinatura do card Operação");

requireText(service, "if (nextSignature.equals(lastMenuRenderSignature)) return;", "guard de renderização");
requireText(service, "populateMenuContents(menuContentView);", "re-renderização do menu");

const ackBlockStart = sync.indexOf('putInt("gtoJobProgress", responseProgress)');
if (ackBlockStart < 0) fail("ACK não persiste gtoJobProgress");
requireText(sync.slice(Math.max(0, ackBlockStart - 900), ackBlockStart + 1200), 'putString("gtoJobStatus", responseJobStatus)', "ACK da operação");
requireText(service, "if (menuView != null) refreshMenuContents();", "callback de sincronização atualiza o menu");

console.log("PASS: o ACK atualiza o progresso da operação e esses campos invalidam o card Operação imediatamente.");
console.log("PASS: o menu é re-renderizado quando a assinatura operacional muda.");
