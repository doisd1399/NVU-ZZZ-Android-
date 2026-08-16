import assert from "node:assert/strict";
import {
  inferNotificationTargetProfile,
  isNotificationVisibleForContext,
  resolveActiveNotificationProfile,
} from "./src/lib/notificationScope";

assert.equal(resolveActiveNotificationProfile("driver"), "driver");
assert.equal(resolveActiveNotificationProfile("admin"), "corporate");
assert.equal(resolveActiveNotificationProfile("owner"), "corporate");

const driverNotification = {
  userId: "u1",
  companyId: "c1",
  targetProfile: "driver" as const,
  type: "NEW_OPERATION",
};
const corporateNotification = {
  userId: "u1",
  companyId: "c1",
  targetProfile: "corporate" as const,
  type: "WORK_REQUEST",
};

// Motorista puro.
assert.equal(
  isNotificationVisibleForContext(driverNotification, {
    userId: "u1",
    activeRole: "driver",
    activeCompanyId: "c1",
  }),
  true,
);
assert.equal(
  isNotificationVisibleForContext(corporateNotification, {
    userId: "u1",
    activeRole: "driver",
    activeCompanyId: "c1",
  }),
  false,
);

// Dono + motorista: a troca de perfil muda a lista.
assert.equal(
  isNotificationVisibleForContext(corporateNotification, {
    userId: "u1",
    activeRole: "admin",
    activeCompanyId: "c1",
  }),
  true,
);
assert.equal(
  isNotificationVisibleForContext(driverNotification, {
    userId: "u1",
    activeRole: "admin",
    activeCompanyId: "c1",
  }),
  false,
);

// Admin + motorista e isolamento por empresa.
assert.equal(
  isNotificationVisibleForContext(corporateNotification, {
    userId: "u1",
    activeRole: "admin",
    activeCompanyId: "c2",
  }),
  false,
);

// Compatibilidade com documentos antigos.
assert.equal(
  inferNotificationTargetProfile({ userId: "u1", tipo: "solicitacao" }),
  "corporate",
);
assert.equal(
  inferNotificationTargetProfile({ userId: "u1", tipo: "approved" }),
  "driver",
);

console.log("notification scope tests: OK");
