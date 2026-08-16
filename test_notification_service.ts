import assert from "node:assert/strict";
import { persistNotificationWithFallback } from "./src/services/notificationService";

let modernCalls = 0;
let legacyCalls = 0;
const modernResult = await persistNotificationWithFallback({
  writeModern: async () => {
    modernCalls += 1;
    return "modern";
  },
  writeLegacy: async () => {
    legacyCalls += 1;
    return "legacy";
  },
});
assert.equal(modernResult, "modern");
assert.equal(modernCalls, 1);
assert.equal(legacyCalls, 0);

modernCalls = 0;
legacyCalls = 0;
let modernErrorObserved = false;
const legacyResult = await persistNotificationWithFallback({
  writeModern: async () => {
    modernCalls += 1;
    throw new Error("permission-denied");
  },
  writeLegacy: async () => {
    legacyCalls += 1;
    return "legacy";
  },
  onModernError: () => {
    modernErrorObserved = true;
  },
});
assert.equal(legacyResult, "legacy");
assert.equal(modernCalls, 1);
assert.equal(legacyCalls, 1);
assert.equal(modernErrorObserved, true);

console.log("Notification persistence fallback tests passed.");
