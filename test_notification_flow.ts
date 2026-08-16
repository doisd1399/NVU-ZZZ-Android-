import assert from "node:assert/strict";
import {
  inferNotificationTargetProfile,
  isNotificationVisibleForContext,
  normalizeNotificationForUi,
  notificationPopupWasShown,
  notificationTimestampMs,
  resolveActiveNotificationProfile,
  shouldDisplayNotificationPopup,
} from "./src/lib/notificationScope";

const driverNotification = normalizeNotificationForUi("new-operation", {
  userId: "driver-1",
  companyId: "company-a",
  targetProfile: "driver",
  type: "NEW_OPERATION",
  title: "Nova operação",
  message: "Você recebeu uma operação.",
  read: false,
  createdAtIso: "2026-07-19T12:00:00.000Z",
});

assert.equal(driverNotification.titulo, "Nova operação");
assert.equal(driverNotification.mensagem, "Você recebeu uma operação.");
assert.equal(driverNotification.lida, false);
assert.equal(inferNotificationTargetProfile(driverNotification), "driver");
assert.equal(notificationTimestampMs(driverNotification), Date.parse("2026-07-19T12:00:00.000Z"));
assert.equal(
  isNotificationVisibleForContext(driverNotification, {
    userId: "driver-1",
    activeRole: "driver",
    activeCompanyId: "company-a",
  }),
  true,
);
assert.equal(
  isNotificationVisibleForContext(driverNotification, {
    userId: "driver-1",
    activeRole: null,
    activeCompanyId: "company-a",
  }),
  false,
);
assert.equal(
  isNotificationVisibleForContext(driverNotification, {
    userId: "driver-1",
    activeRole: "driver",
    activeCompanyId: null,
  }),
  false,
);

const corporateNotification = normalizeNotificationForUi("work-request", {
  userId: "owner-1",
  companyId: "company-a",
  targetProfile: "corporate",
  type: "WORK_REQUEST",
  title: "Nova solicitação",
  message: "Um motorista solicitou trabalho.",
  read: false,
});

assert.equal(
  isNotificationVisibleForContext(corporateNotification, {
    userId: "owner-1",
    activeRole: "admin",
    activeCompanyId: "company-a",
  }),
  true,
);
assert.equal(
  isNotificationVisibleForContext(corporateNotification, {
    userId: "owner-1",
    activeRole: "driver",
    activeCompanyId: "company-a",
  }),
  false,
);
assert.equal(
  isNotificationVisibleForContext(corporateNotification, {
    userId: "owner-1",
    activeRole: "admin",
    activeCompanyId: "company-b",
  }),
  false,
);

const legacyCorporate = normalizeNotificationForUi("legacy", {
  userId: "owner-1",
  titulo: "Nova solicitação",
  mensagem: "Mensagem legada",
  tipo: "admin",
  lida: false,
  dataHora: "2026-07-19T11:00:00.000Z",
});
assert.equal(legacyCorporate.targetProfile, "corporate");
assert.equal(legacyCorporate.createdAtIso, "2026-07-19T11:00:00.000Z");
assert.equal(notificationTimestampMs(legacyCorporate), Date.parse("2026-07-19T11:00:00.000Z"));

const companyRejected = normalizeNotificationForUi("company-rejected", {
  userId: "owner-2",
  type: "COMPANY_REJECTED",
  title: "Solicitação recusada",
  message: "Recusada",
  read: false,
});
assert.equal(companyRejected.targetProfile, "corporate");
assert.equal(resolveActiveNotificationProfile(undefined), null);
assert.equal(resolveActiveNotificationProfile("admin"), "corporate");

const alreadyShown = normalizeNotificationForUi("shown", {
  userId: "driver-1",
  targetProfile: "driver",
  type: "SYSTEM",
  title: "Aviso",
  message: "Aviso",
  popupShownAtIso: "2026-07-19T12:01:00.000Z",
});
assert.equal(notificationPopupWasShown(alreadyShown), true);

const popupNow = Date.parse("2026-07-19T12:00:30.000Z");
assert.equal(
  shouldDisplayNotificationPopup(driverNotification, {
    pathname: "/driver/profile",
    now: popupNow,
  }),
  true,
);
assert.equal(
  shouldDisplayNotificationPopup(driverNotification, {
    pathname: "/select-profile",
    now: popupNow,
  }),
  false,
);
assert.equal(
  shouldDisplayNotificationPopup(
    { ...driverNotification, createdAtIso: "2026-07-19T11:00:00.000Z" },
    { pathname: "/driver/profile", now: popupNow },
  ),
  false,
);
assert.equal(
  shouldDisplayNotificationPopup(alreadyShown, {
    pathname: "/driver/profile",
    now: popupNow,
  }),
  false,
);

console.log(
  "Notification flow tests passed: context scope, legacy timestamp, profile mapping and popup persistence.",
);
