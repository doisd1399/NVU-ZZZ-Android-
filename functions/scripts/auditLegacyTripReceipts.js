/*
 * NVU — auditoria NÃO DESTRUTIVA dos comprovantes legados.
 *
 * Escopo aceito, e somente este:
 *   empresas/{companyId}/receipts/{userId}/{file}
 *
 * Este script NÃO contém chamada de exclusão de objetos e NÃO acessa
 * historico_viagens. Ele apenas lê metadados do Cloud Storage para produzir
 * um relatório dos arquivos que já atingiram a retenção de 45 dias.
 *
 * Uso recomendado (ambiente autenticado / Google Cloud Shell):
 *   node functions/scripts/auditLegacyTripReceipts.js
 *
 * Opcional:
 *   --bucket=vtc-frota-log.firebasestorage.app
 *   --out=legacy-trip-receipts-audit.json
 *   --verbose
 */
const fs = require("node:fs");
const path = require("node:path");
const admin = require("firebase-admin");

const RETENTION_DAYS = 45;
const RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000;
const LEGACY_PREFIX = "empresas/";
const LEGACY_RECEIPT_PATH = /^empresas\/([^/]+)\/receipts\/([^/]+)\/(.+)$/;
const DEFAULT_BUCKET = "vtc-frota-log.firebasestorage.app";
const DEFAULT_OUT = "legacy-trip-receipts-audit.json";

function readArg(name) {
  const prefix = `--${name}=`;
  const value = process.argv.find((arg) => arg.startsWith(prefix));
  return value ? value.slice(prefix.length).trim() : "";
}

function safeInteger(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? Math.trunc(parsed) : 0;
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(unit === 0 ? 0 : 2)} ${units[unit]}`;
}

function isoOrNull(ms) {
  return Number.isFinite(ms) && ms > 0 ? new Date(ms).toISOString() : null;
}

const bucketName = readArg("bucket") || process.env.FIREBASE_STORAGE_BUCKET || DEFAULT_BUCKET;
const outputPath = path.resolve(process.cwd(), readArg("out") || DEFAULT_OUT);
const verbose = process.argv.includes("--verbose");

if (!admin.apps.length) {
  admin.initializeApp({ storageBucket: bucketName });
}

async function main() {
  const bucket = admin.storage().bucket(bucketName);
  const nowMs = Date.now();
  const cutoffMs = nowMs - RETENTION_MS;

  let pageToken;
  let scannedUnderEmpresas = 0;
  let legacyReceipts = 0;
  let eligible = 0;
  let notYetEligible = 0;
  let invalidMetadata = 0;
  let totalLegacyBytes = 0;
  let eligibleBytes = 0;
  let oldestEligibleMs = Infinity;
  let newestEligibleMs = -Infinity;

  const eligibleObjects = [];
  const byCompany = new Map();

  do {
    const [files, nextQuery] = await bucket.getFiles({
      prefix: LEGACY_PREFIX,
      autoPaginate: false,
      maxResults: 1000,
      pageToken,
    });

    for (const file of files) {
      scannedUnderEmpresas += 1;
      const match = file.name.match(LEGACY_RECEIPT_PATH);
      if (!match) continue;

      legacyReceipts += 1;
      const companyId = match[1];
      const userId = match[2];
      const size = safeInteger(file.metadata.size);
      totalLegacyBytes += size;

      const createdMs = new Date(file.metadata.timeCreated || "").getTime();
      if (!Number.isFinite(createdMs) || createdMs <= 0) {
        invalidMetadata += 1;
        continue;
      }

      if (createdMs > cutoffMs) {
        notYetEligible += 1;
        continue;
      }

      eligible += 1;
      eligibleBytes += size;
      oldestEligibleMs = Math.min(oldestEligibleMs, createdMs);
      newestEligibleMs = Math.max(newestEligibleMs, createdMs);

      const companyStats = byCompany.get(companyId) || { count: 0, bytes: 0 };
      companyStats.count += 1;
      companyStats.bytes += size;
      byCompany.set(companyId, companyStats);

      const object = {
        path: file.name,
        companyId,
        userId,
        sizeBytes: size,
        timeCreated: new Date(createdMs).toISOString(),
        generation: String(file.metadata.generation || ""),
      };
      eligibleObjects.push(object);

      if (verbose) {
        console.log(`ELEGÍVEL: ${file.name} | ${formatBytes(size)} | ${object.timeCreated}`);
      }
    }

    pageToken = nextQuery?.pageToken || undefined;
  } while (pageToken);

  const companies = Array.from(byCompany.entries())
    .map(([companyId, stats]) => ({
      companyId,
      eligibleCount: stats.count,
      eligibleBytes: stats.bytes,
      eligibleSize: formatBytes(stats.bytes),
    }))
    .sort((a, b) => b.eligibleBytes - a.eligibleBytes);

  const report = {
    audit: "NVU legacy trip receipts",
    mode: "read-only",
    deletionEnabled: false,
    bucket: bucketName,
    legacyPattern: "empresas/{companyId}/receipts/{userId}/{file}",
    retentionDays: RETENTION_DAYS,
    generatedAt: new Date(nowMs).toISOString(),
    cutoffTimeCreated: new Date(cutoffMs).toISOString(),
    summary: {
      scannedUnderEmpresas,
      legacyReceipts,
      eligible,
      notYetEligible,
      invalidMetadata,
      totalLegacyBytes,
      totalLegacySize: formatBytes(totalLegacyBytes),
      eligibleBytes,
      eligibleSize: formatBytes(eligibleBytes),
      oldestEligible: oldestEligibleMs === Infinity ? null : isoOrNull(oldestEligibleMs),
      newestEligible: newestEligibleMs === -Infinity ? null : isoOrNull(newestEligibleMs),
    },
    companies,
    eligibleObjects,
  };

  fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  console.log("\n=== NVU | AUDITORIA DE COMPROVANTES LEGADOS ===");
  console.log("Modo: SOMENTE LEITURA — nenhuma exclusão é executada");
  console.log(`Bucket: ${bucketName}`);
  console.log(`Retenção analisada: ${RETENTION_DAYS} dias`);
  console.log(`Objetos examinados sob empresas/: ${scannedUnderEmpresas}`);
  console.log(`Comprovantes legados encontrados: ${legacyReceipts}`);
  console.log(`Elegíveis (>= ${RETENTION_DAYS} dias): ${eligible}`);
  console.log(`Ainda dentro do prazo: ${notYetEligible}`);
  console.log(`Metadados inválidos (ignorados): ${invalidMetadata}`);
  console.log(`Espaço total dos comprovantes legados: ${formatBytes(totalLegacyBytes)}`);
  console.log(`Espaço potencialmente liberável: ${formatBytes(eligibleBytes)}`);
  console.log(`Relatório: ${outputPath}`);
  console.log("Nenhum arquivo foi apagado.\n");
}

main().catch((error) => {
  console.error("Falha na auditoria não destrutiva:", error);
  process.exitCode = 1;
});
