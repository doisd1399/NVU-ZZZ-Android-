import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { createHash } from "node:crypto";

admin.initializeApp();

const db = admin.firestore();

function readEnabledFlag(value: unknown, defaultValue = true) {
  if (typeof value !== "string" || !value.trim()) return defaultValue;
  const normalized = value.trim().toLowerCase();
  if (["false", "0", "off", "no"].includes(normalized)) return false;
  if (["true", "1", "on", "yes"].includes(normalized)) return true;
  return defaultValue;
}

// Compatibilidade ativada por padrão. A Function só deve ser removida depois
// que nenhum cliente ou regra ainda gravar na coleção `notificacoes`.
const LEGACY_NOTIFICATION_PUSH_ENABLED = readEnabledFlag(
  process.env.ENABLE_LEGACY_NOTIFICATION_PUSH,
);

type NotificationRecord = {
  userId?: unknown;
  title?: unknown;
  message?: unknown;
  titulo?: unknown;
  mensagem?: unknown;
  type?: unknown;
  tipo?: unknown;
  companyId?: unknown;
  targetProfile?: unknown;
  dedupeKey?: unknown;
  metadata?: unknown;
};

function asNonEmptyString(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized ? normalized : null;
}

function safeDocumentId(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 1400);
}

function pushDispatchId(
  userId: string,
  notificationId: string,
  dedupeKey: string | null,
) {
  // createNotification usa exatamente userId + dedupeKey como ID. Repetir a
  // regra aqui mantém compatibilidade com registros já processados e também
  // une documentos moderno/legado que tenham IDs diferentes.
  return dedupeKey
    ? safeDocumentId(`${userId}_${dedupeKey}`)
    : notificationId;
}

function androidNotificationKey(dispatchId: string) {
  return `nvu_${createHash("sha256").update(dispatchId).digest("hex").slice(0, 40)}`;
}

function stringifyPushData(data: NotificationRecord, notificationId: string) {
  const payload: Record<string, string> = {
    notificationId,
  };

  const type = asNonEmptyString(data.type) || asNonEmptyString(data.tipo);
  const companyId = asNonEmptyString(data.companyId);
  const targetProfile = asNonEmptyString(data.targetProfile);
  const dedupeKey = asNonEmptyString(data.dedupeKey);

  if (type) payload.type = type;
  if (companyId) payload.companyId = companyId;
  if (targetProfile) payload.targetProfile = targetProfile;
  if (dedupeKey) payload.dedupeKey = dedupeKey;

  if (data.metadata && typeof data.metadata === "object") {
    Object.entries(data.metadata as Record<string, unknown>).forEach(([key, value]) => {
      if (value == null) return;
      if (["string", "number", "boolean"].includes(typeof value)) {
        payload[key] = String(value);
      }
    });
  }

  return payload;
}

async function deleteInvalidTokens(tokens: string[], response: admin.messaging.BatchResponse) {
  const invalidTokens = new Set<string>();

  response.responses.forEach((result, index) => {
    if (result.success) return;
    const code = result.error?.code;
    if (
      code === "messaging/invalid-registration-token" ||
      code === "messaging/registration-token-not-registered"
    ) {
      invalidTokens.add(tokens[index]);
    }
  });

  for (const token of invalidTokens) {
    const snapshot = await db.collection("userDevices").where("token", "==", token).get();
    const batch = db.batch();
    snapshot.docs.forEach((document) => batch.delete(document.ref));
    if (!snapshot.empty) await batch.commit();
  }
}

async function sendNotificationDocumentPush(
  collectionName: "notifications" | "notificacoes",
  notificationId: string,
  data: NotificationRecord,
) {
  const userId = asNonEmptyString(data.userId);
  const title = asNonEmptyString(data.title) || asNonEmptyString(data.titulo);
  const body = asNonEmptyString(data.message) || asNonEmptyString(data.mensagem);
  const dedupeKey = asNonEmptyString(data.dedupeKey);

  if (!userId || !title || !body) {
    console.warn("[NVU PUSH] Notificação sem userId, título ou mensagem.", {
      collectionName,
      notificationId,
    });
    return;
  }

  // A mesma notificação pode existir no esquema moderno ou legado. A chave sem
  // o nome da coleção impede que os dois gatilhos enviem o mesmo evento.
  const dispatchId = pushDispatchId(userId, notificationId, dedupeKey);
  const dispatchRef = db.collection("pushDispatches").doc(dispatchId);
  const claimed = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(dispatchRef);
    if (snapshot.exists) return false;
    transaction.create(dispatchRef, {
      notificationId,
      dedupeKey,
      sourceCollection: collectionName,
      userId,
      status: "processing",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return true;
  });

  if (!claimed) {
    console.log(`[NVU PUSH] Evento ${notificationId} já processado ou em processamento.`);
    return;
  }

  try {
    const devices = await db.collection("userDevices").where("userId", "==", userId).get();
    const tokens = Array.from(
      new Set(
        devices.docs
          .map((document) => asNonEmptyString(document.data().token))
          .filter((token): token is string => Boolean(token)),
      ),
    );

    if (tokens.length === 0) {
      await dispatchRef.set(
        {
          status: "completed_no_tokens",
          completedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
      return;
    }

    const androidKey = androidNotificationKey(dispatchId);
    const response = await admin.messaging().sendEachForMulticast({
      notification: { title, body },
      data: stringifyPushData(data, notificationId),
      tokens,
      android: {
        collapseKey: androidKey,
        notification: {
          channelId: "nvu_notifications",
          tag: androidKey,
        },
      },
    });

    await deleteInvalidTokens(tokens, response);
    await dispatchRef.set(
      {
        status: "completed",
        successCount: response.successCount,
        failureCount: response.failureCount,
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
  } catch (error) {
    // Libera a chave para que uma repetição automática do gatilho possa tentar novamente.
    await dispatchRef.delete().catch(() => undefined);
    console.error("[NVU PUSH] Falha ao processar notificação:", error);
    throw error;
  }
}

export const pushOnNotificationCreated = functions.firestore
  .document("notifications/{notificationId}")
  .onCreate(async (snapshot, context) => {
    await sendNotificationDocumentPush(
      "notifications",
      context.params.notificationId,
      snapshot.data() as NotificationRecord,
    );
  });

export const pushOnLegacyNotificationCreated = functions.firestore
  .document("notificacoes/{notificationId}")
  .onCreate(async (snapshot, context) => {
    if (!LEGACY_NOTIFICATION_PUSH_ENABLED) {
      console.log(
        `[NVU PUSH] Evento legado ${context.params.notificationId} ignorado por configuração.`,
      );
      return;
    }
    await sendNotificationDocumentPush(
      "notificacoes",
      context.params.notificationId,
      snapshot.data() as NotificationRecord,
    );
  });

/**
 * Registra um token FCM de forma canônica. Antes de salvar, remove qualquer
 * associação anterior do mesmo token, evitando que um aparelho continue
 * vinculado a contas antigas.
 */
export const registerPushDevice = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Autenticação obrigatória.");
  }

  const token = asNonEmptyString(data?.token);
  if (!token) {
    throw new functions.https.HttpsError("invalid-argument", "Token FCM obrigatório.");
  }

  const uid = context.auth.uid;
  const existing = await db.collection("userDevices").where("token", "==", token).get();
  const batch = db.batch();
  existing.docs.forEach((document) => batch.delete(document.ref));

  const deviceRef = db.collection("userDevices").doc(`${uid}_${token}`);
  batch.set(
    deviceRef,
    {
      userId: uid,
      token,
      platform: asNonEmptyString(data?.platform) || "unknown",
      companyId: asNonEmptyString(data?.companyId),
      activeProfile: asNonEmptyString(data?.activeProfile),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  await batch.commit();

  return { success: true };
});

export const sendPushNotification = functions.https.onCall(async (_data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "O usuário precisa estar autenticado para enviar notificações.",
    );
  }

  // Compatibilidade com versões antigas do cliente: a função continua
  // existente, mas não dispara FCM. Toda notificação deve nascer em
  // notifications/notificacoes e passar pelo gatilho idempotente acima.
  console.warn(
    "[NVU PUSH] sendPushNotification é legado; envio direto ignorado para evitar duplicação.",
  );
  return {
    success: true,
    skipped: true,
    reason: "notification_documents_are_canonical",
  };
});

/**
 * Repara de forma idempotente o vínculo de uma inscrição já aprovada.
 */
export const repairApprovedMembership = functions.https.onCall(async (_data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "O usuário precisa estar autenticado para reparar o acesso.",
    );
  }

  const applicationId = String(
    (_data as { applicationId?: unknown } | undefined)?.applicationId || "",
  ).trim();
  if (!applicationId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "O applicationId é obrigatório para reparar o acesso.",
    );
  }

  const uid = context.auth.uid;
  const email = String(context.auth.token.email || "").trim().toLowerCase();
  const applicationRef = db.collection("recruitment_applications").doc(applicationId);
  const applicationSnapshot = await applicationRef.get();

  if (!applicationSnapshot.exists) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "A inscrição aprovada não está mais disponível.",
    );
  }

  const application = applicationSnapshot.data() || {};
  const applicationUserId = String(application.userId || "").trim();
  const applicationEmail = String(application.email || "").trim().toLowerCase();
  const companyId = String(application.companyId || "").trim();
  const identityMatches =
    applicationUserId === uid || Boolean(email && applicationEmail && applicationEmail === email);

  if (!identityMatches) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "A inscrição não pertence à conta autenticada.",
    );
  }

  const userRef = db.collection("users").doc(uid);
  const userBeforeRepair = await userRef.get();
  const currentApplicationId = String(
    userBeforeRepair.data()?.currentRecruitmentApplicationId || "",
  ).trim();
  if (currentApplicationId && currentApplicationId !== applicationId) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Esta aprovação pertence a uma inscrição anterior e não pode liberar acesso.",
    );
  }

  if (!companyId) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "A inscrição aprovada não possui empresa vinculada.",
    );
  }

  if (
    application.status !== "approved" ||
    application.accessRevokedAt ||
    application.accessRevokedReason === "removed_from_fleet"
  ) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "A inscrição foi revogada e precisa ser reenviada.",
    );
  }

  const now = admin.firestore.FieldValue.serverTimestamp();
  const membershipQuery = await db
    .collection("companyMembers")
    .where("userId", "==", uid)
    .where("companyId", "==", companyId)
    .get();
  const membershipRefs = membershipQuery.docs.map((document) => document.ref);

  await db.runTransaction(async (transaction) => {
    const [freshApplicationSnapshot, ...membershipSnapshots] = await Promise.all([
      transaction.get(applicationRef),
      ...membershipRefs.map((ref) => transaction.get(ref)),
    ]);
    const userSnapshot = await transaction.get(userRef);
    const existingUser = userSnapshot.exists ? userSnapshot.data() || {} : {};
    const existingRoles = Array.isArray(existingUser.roles) ? existingUser.roles : [];
    const roles = Array.from(new Set([...existingRoles, "driver"]));

    if (!freshApplicationSnapshot.exists) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "A inscrição aprovada não está mais disponível.",
      );
    }

    const currentApplication = freshApplicationSnapshot.data() || {};
    const currentApplicationEmail = String(currentApplication.email || "").trim().toLowerCase();
    const currentApplicationUserId = String(currentApplication.userId || "").trim();
    const currentIdentityMatches =
      currentApplicationUserId === uid ||
      Boolean(email && currentApplicationEmail && currentApplicationEmail === email);

    if (!currentIdentityMatches) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "A inscrição não pertence à conta autenticada.",
      );
    }

    const transactionCurrentApplicationId = String(
      existingUser.currentRecruitmentApplicationId || "",
    ).trim();
    if (
      transactionCurrentApplicationId &&
      transactionCurrentApplicationId !== applicationId
    ) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Esta aprovação pertence a uma inscrição anterior e não pode liberar acesso.",
      );
    }

    if (
      currentApplication.status !== "approved" ||
      currentApplication.accessRevokedAt ||
      currentApplication.accessRevokedReason === "removed_from_fleet"
    ) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "A inscrição foi revogada e precisa ser reenviada.",
      );
    }

    transaction.set(
      userRef,
      {
        id: uid,
        email:
          email ||
          String(currentApplication.email || existingUser.email || "").trim().toLowerCase(),
        name:
          currentApplication.fullName ||
          existingUser.name ||
          context.auth?.token.name ||
          "Usuário",
        whatsapp: currentApplication.whatsapp || existingUser.whatsapp || "",
        profilePhotoURL:
          currentApplication.applicationPhotoURL || existingUser.profilePhotoURL || "",
        companyId,
        status: "active",
        currentRecruitmentApplicationId: applicationId,
        currentRecruitmentCompanyId: companyId,
        currentRecruitmentStatus: "approved",
        role: existingUser.role === "admin" ? "admin" : "driver",
        roles,
        updatedAt: now,
        ...(!userSnapshot.exists ? { createdAt: now } : {}),
      },
      { merge: true },
    );

    if (membershipRefs.length === 0) {
      transaction.set(db.collection("companyMembers").doc(), {
        userId: uid,
        companyId,
        roles: ["driver"],
        status: "active",
        permissions: [],
        joinedAt: now,
        updatedAt: now,
      });
    } else {
      membershipRefs.forEach((membershipRef, index) => {
        const membershipSnapshot = membershipSnapshots[index];
        const membership = membershipSnapshot.exists ? membershipSnapshot.data() || {} : {};
        const currentRoles = Array.isArray(membership.roles) ? membership.roles : [];
        transaction.set(
          membershipRef,
          {
            userId: uid,
            companyId,
            roles: Array.from(new Set([...currentRoles, "driver"])),
            status: "active",
            permissions: Array.isArray(membership.permissions) ? membership.permissions : [],
            updatedAt: now,
          },
          { merge: true },
        );
      });
    }

    transaction.set(
      applicationRef,
      {
        userId: uid,
        email:
          email || String(currentApplication.email || "").trim().toLowerCase(),
        status: "approved",
        accessRepairedAt: now,
        updatedAt: now,
      },
      { merge: true },
    );
  });

  return { success: true, userId: uid, companyId, applicationId };
});

export {
  generateNvuNewsBackfill,
  generateNvuNewsScheduled,
  generateNvuNewsMonthlyScheduled,
  updateNvuNewsClassificationsOnRankingAggregateWrite,
  updateNvuNewsClassificationsOnClosedTripWrite,
  updateNvuNewsClassificationsOnCompanyWrite,
  updateNvuNewsClassificationsOnSimulatorWrite,
} from "./nvuNewsBackfill";
export {
  publishCompanyApprovalNews,
  publishCompanyApprovalNewsOnCompanyCreate,
  publishCompanyApprovalNewsOnOwnerProfileWrite,
  publishCompanyApprovalNewsOnSimulatorWrite,
  syncCompanyApprovalNews,
} from "./companyApprovalNews";
export {
  ensureRankingAggregates,
  updateRankingAggregatesOnTripWrite,
  updateRankingAggregatesOnCompanyWrite,
  updateRankingAggregatesOnSimulatorWrite,
} from "./rankingAggregates";
export {
  auditUserStorageImagesOnUpdate,
  auditCompanyStorageImagesOnUpdate,
  auditUserStorageImagesOnDelete,
  auditCompanyStorageImagesOnDelete,
} from "./storageCleanupAudit";
export { authenticateSeniorAccess } from "./seniorAccess";


export { registerGtoTrip } from "./gtoTrips";

export { syncGtoTripState } from "./gtoState";
