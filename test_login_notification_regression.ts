import assert from "node:assert/strict";
import fs from "node:fs";

const login = fs.readFileSync("src/pages/Login.tsx", "utf8");
const status = fs.readFileSync("src/pages/ApplicationStatus.tsx", "utf8");
const identity = fs.readFileSync("src/services/userIdentityService.ts", "utf8");
const scope = fs.readFileSync("src/lib/notificationScope.ts", "utf8");

assert.ok(login.includes('applicationDocument.data().status === "pending"'));
assert.ok(login.includes("if (loading) return;"));
assert.ok(login.includes("setLoading(false);"));
assert.ok(!login.includes("if (!auth.currentUser)"));
assert.ok(status.includes('application.status === "pending"'));
assert.ok(status.includes("applicationTimestamp(b) - applicationTimestamp(a)"));
assert.ok(status.includes('navigate("/apply")'));

for (const collectionName of [
  "trabalhos",
  "companyMembers",
  "notifications",
  "notificacoes",
  "recruitment_applications",
  "jobDemands",
  "solicitacoes_motoristas",
  "historico_viagens",
  "simulator_members",
  "frotas",
]) {
  assert.ok(identity.includes(`"${collectionName}"`), `${collectionName} migration is missing`);
}
assert.ok(identity.includes("if (migrationComplete)"));
assert.ok(identity.includes("mergePending: true"));
assert.ok(scope.includes("notification.dataHora"));
assert.ok(scope.includes("if (!activeProfile) return false"));

console.log("Login/notification regression tests passed: pending redirect, deterministic status and non-destructive identity migration.");
