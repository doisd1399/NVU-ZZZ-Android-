/*
 * One-time helper for legacy receipts stored at:
 * empresas/{companyId}/receipts/{userId}/{file}
 *
 * Default: DRY RUN. To actually delete, run with --apply.
 * Uses object creation time only and never reads/deletes historico_viagens.
 */
const admin = require("firebase-admin");

if (!admin.apps.length) admin.initializeApp();

const RETENTION_DAYS = 45;
const CUTOFF_MS = Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000;
const APPLY = process.argv.includes("--apply");
const LEGACY_RECEIPT_PATH = /^empresas\/[^/]+\/receipts\/[^/]+\/.+/;

async function main() {
  const bucket = admin.storage().bucket();
  let pageToken;
  let scanned = 0;
  let eligible = 0;
  let deleted = 0;

  do {
    const [files, nextQuery] = await bucket.getFiles({
      prefix: "empresas/",
      autoPaginate: false,
      maxResults: 1000,
      pageToken,
    });

    for (const file of files) {
      scanned += 1;
      if (!LEGACY_RECEIPT_PATH.test(file.name)) continue;

      const createdMs = new Date(file.metadata.timeCreated || 0).getTime();
      if (!Number.isFinite(createdMs) || createdMs <= 0 || createdMs > CUTOFF_MS) continue;

      eligible += 1;
      console.log(`${APPLY ? "DELETE" : "DRY-RUN"}: ${file.name} (${file.metadata.timeCreated})`);

      if (APPLY) {
        await file.delete({ ignoreNotFound: true });
        deleted += 1;
      }
    }

    pageToken = nextQuery?.pageToken || undefined;
  } while (pageToken);

  console.log(JSON.stringify({ mode: APPLY ? "apply" : "dry-run", scanned, eligible, deleted }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
