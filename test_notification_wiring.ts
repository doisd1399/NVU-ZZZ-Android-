import assert from "node:assert/strict";
import fs from "node:fs";

const appContext = fs.readFileSync("src/context/AppContext.tsx", "utf8");
const listener = fs.readFileSync("src/components/NotificationToastListener.tsx", "utf8");
const seniorPanel = fs.readFileSync("src/pages/admin/SeniorPanel.tsx", "utf8");
const service = fs.readFileSync("src/services/notificationService.ts", "utf8");
const app = fs.readFileSync("src/App.tsx", "utf8");
const firebaseConfig = JSON.parse(fs.readFileSync("firebase.json", "utf8"));
const rules = fs.readFileSync("firestore.rules", "utf8");

for (const eventType of [
  "NEW_OPERATION",
  "RH_APPLICATION",
  "WORK_REQUEST",
  "OPERATION_COMPLETED",
]) {
  assert.ok(appContext.includes(`type: "${eventType}"`), `${eventType} is not wired`);
}
assert.ok(listener.includes("closeButton: true"), "Dismissible popup is not wired");
assert.ok(listener.includes("notificationsHydrated"), "Initial hydration gate is missing");
assert.ok(listener.includes("shouldDisplayNotificationPopup"), "Public-route popup suppression is missing");
assert.ok(listener.includes("markNotificationPopupShown"), "Persistent popup marker is missing");
assert.ok(app.includes("<NotificationToastListener />"), "Global listener is not mounted inside the router");
assert.ok(appContext.includes("popupShownAt: serverTimestamp()"));
assert.ok(appContext.includes("setNotificationsHydrated(true)"));
assert.ok(seniorPanel.includes("createNotification({"));
assert.ok(!seniorPanel.includes('batch.set(notifRef'));
assert.ok(service.includes('doc(db, "notifications"'), "Modern notification collection missing");
assert.ok(service.includes('doc(db, "notificacoes"'), "Legacy fallback collection missing");
assert.equal(firebaseConfig.firestore.rules, "firestore.rules");
assert.ok(rules.includes("match /notifications/{id}"));
assert.ok(rules.includes("match /notificacoes/{id}"));

console.log("Notification wiring tests passed: producers, hydration gate, route suppression and popup persistence.");
