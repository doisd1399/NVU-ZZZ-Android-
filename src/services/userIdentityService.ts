import type { User as FirebaseAuthUser } from "firebase/auth";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  where,
  writeBatch,
  type DocumentReference,
} from "firebase/firestore";
import { legacyNotificationCompatibility } from "../config/legacyNotifications";
import { db } from "../lib/firebase";
import {
  approvedApplicationField,
  resolveCanonicalProfileName,
  selectNewestApprovedApplication,
  type IdentityApplication,
} from "../lib/canonicalIdentity";
import { resolvePersistedUserProfilePhoto } from "../lib/profilePhotoRecovery";

interface UserDocumentRecord {
  id: string;
  data: Record<string, any>;
}

interface ReferenceMigrationSpec {
  collectionName: string;
  fields: string[];
}

const REFERENCE_MIGRATIONS: ReferenceMigrationSpec[] = [
  { collectionName: "trabalhos", fields: ["motoristaId", "driverId"] },
  { collectionName: "companyMembers", fields: ["userId"] },
  { collectionName: "notifications", fields: ["userId"] },
  ...(legacyNotificationCompatibility.resolveLegacy
    ? [{ collectionName: "notificacoes", fields: ["userId"] }]
    : []),
  {
    collectionName: "recruitment_applications",
    fields: ["userId", "approvedUserId"],
  },
  {
    collectionName: "jobDemands",
    fields: ["userId", "driverId", "requestedBy", "requestedById"],
  },
  {
    collectionName: "solicitacoes_motoristas",
    fields: ["motoristaId", "adminId"],
  },
  {
    collectionName: "historico_viagens",
    fields: ["motoristaId", "driverId", "userId"],
  },
  { collectionName: "frotas", fields: ["ownerId", "userId"] },
];

function hasUsefulValue(value: unknown) {
  if (value == null) return false;
  if (typeof value === "string") return value.trim().length > 0;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === "object") return Object.keys(value as object).length > 0;
  return true;
}

function scoreUserDocument(record: UserDocumentRecord, canonicalUid: string) {
  const data = record.data;
  let score = record.id === canonicalUid ? 100 : 0;
  if (data.status === "active") score += 30;
  if (data.role === "admin" || data.roles?.includes?.("admin")) score += 25;
  if (hasUsefulValue(data.companyId)) score += 15;
  if (hasUsefulValue(data.profilePhotoURL)) score += 12;
  if (hasUsefulValue(data.name) && data.name !== "Motorista") score += 10;
  if (hasUsefulValue(data.memberships)) score += 8;
  if (hasUsefulValue(data.whatsapp)) score += 3;
  return score;
}

function mergeUsefulDocuments(records: UserDocumentRecord[], canonicalUid: string) {
  const sorted = [...records].sort(
    (a, b) => scoreUserDocument(a, canonicalUid) - scoreUserDocument(b, canonicalUid),
  );
  const merged: Record<string, any> = {};

  for (const record of sorted) {
    for (const [key, value] of Object.entries(record.data)) {
      if (hasUsefulValue(value)) merged[key] = value;
    }
  }

  const roles = new Set<string>();
  const memberships: Record<string, any> = {};
  for (const record of records) {
    const role = record.data.role;
    if (role === "admin" || role === "driver" || role === "senior") {
      roles.add(role);
    }
    if (Array.isArray(record.data.roles)) {
      record.data.roles.forEach((item: unknown) => {
        if (item === "admin" || item === "driver" || item === "senior") {
          roles.add(item);
        }
      });
    }
    if (record.data.memberships && typeof record.data.memberships === "object") {
      Object.assign(memberships, record.data.memberships);
    }
  }

  return { merged, roles, memberships };
}

async function commitUpdatesInChunks(
  updates: Array<{ ref: DocumentReference; data: Record<string, unknown> }>,
) {
  const CHUNK_SIZE = 350;
  for (let index = 0; index < updates.length; index += CHUNK_SIZE) {
    const batch = writeBatch(db);
    for (const update of updates.slice(index, index + CHUNK_SIZE)) {
      batch.update(update.ref, update.data);
    }
    await batch.commit();
  }
}

async function migrateSimulatorMembers(oldUserId: string, canonicalUserId: string) {
  const snapshot = await getDocs(
    query(collection(db, "simulator_members"), where("userId", "==", oldUserId)),
  );

  const operations: Array<{
    oldRef: DocumentReference;
    newRef: DocumentReference;
    data: Record<string, unknown>;
  }> = [];

  snapshot.docs.forEach((memberDocument) => {
    const data = memberDocument.data();
    const companyId = typeof data.companyId === "string" ? data.companyId : "";
    if (!companyId) return;
    operations.push({
      oldRef: memberDocument.ref,
      newRef: doc(db, "simulator_members", `${canonicalUserId}_${companyId}`),
      data: { ...data, userId: canonicalUserId },
    });
  });

  for (let index = 0; index < operations.length; index += 180) {
    const batch = writeBatch(db);
    for (const operation of operations.slice(index, index + 180)) {
      batch.set(operation.newRef, operation.data, { merge: true });
      batch.delete(operation.oldRef);
    }
    await batch.commit();
  }
}

async function migrateUserReferences(oldUserId: string, canonicalUserId: string) {
  const updateMap = new Map<
    string,
    { ref: DocumentReference; data: Record<string, unknown> }
  >();
  const failures: Array<{ collectionName: string; field: string; error: unknown }> = [];

  for (const migration of REFERENCE_MIGRATIONS) {
    for (const field of migration.fields) {
      try {
        const snapshot = await getDocs(
          query(
            collection(db, migration.collectionName),
            where(field, "==", oldUserId),
          ),
        );

        snapshot.docs.forEach((referenceDocument) => {
          const existing = updateMap.get(referenceDocument.ref.path);
          updateMap.set(referenceDocument.ref.path, {
            ref: referenceDocument.ref,
            data: {
              ...(existing?.data ?? {}),
              [field]: canonicalUserId,
            },
          });
        });
      } catch (error) {
        failures.push({
          collectionName: migration.collectionName,
          field,
          error,
        });
      }
    }
  }

  try {
    await migrateSimulatorMembers(oldUserId, canonicalUserId);
  } catch (error) {
    failures.push({
      collectionName: "simulator_members",
      field: "userId/documentId",
      error,
    });
  }

  try {
    await commitUpdatesInChunks(Array.from(updateMap.values()));
  } catch (error) {
    failures.push({
      collectionName: "batch",
      field: "commit",
      error,
    });
  }

  failures.forEach((failure) => {
    console.warn(
      `[NVU Login] Migração pendente em ${failure.collectionName}.${failure.field}`,
      failure.error,
    );
  });

  return failures.length === 0;
}

async function loadApprovedIdentityApplication(
  userId: string,
  normalizedEmail: string,
): Promise<IdentityApplication | null> {
  const applications = new Map<string, IdentityApplication>();
  const identityQueries = [
    query(
      collection(db, "recruitment_applications"),
      where("userId", "==", userId),
    ),
    query(
      collection(db, "recruitment_applications"),
      where("email", "==", normalizedEmail),
    ),
  ];

  const snapshots = await Promise.allSettled(
    identityQueries.map((identityQuery) => getDocs(identityQuery)),
  );

  snapshots.forEach((result) => {
    if (result.status !== "fulfilled") return;
    result.value.docs.forEach((applicationDocument) => {
      applications.set(applicationDocument.id, {
        id: applicationDocument.id,
        ...(applicationDocument.data() as IdentityApplication),
      });
    });
  });

  return selectNewestApprovedApplication(Array.from(applications.values()));
}

export async function unifyUserDocument(user: FirebaseAuthUser) {
  if (!user.uid || !user.email) {
    throw new Error("Usuário autenticado sem UID ou e-mail válido.");
  }

  const normalizedEmail = user.email.trim().toLowerCase();
  const emailCandidates = Array.from(
    new Set([normalizedEmail, user.email.trim()].filter(Boolean)),
  );

  const recordsById = new Map<string, UserDocumentRecord>();
  for (const emailCandidate of emailCandidates) {
    const emailSnapshot = await getDocs(
      query(collection(db, "users"), where("email", "==", emailCandidate)),
    );
    emailSnapshot.docs.forEach((userDocument) => {
      recordsById.set(userDocument.id, {
        id: userDocument.id,
        data: userDocument.data(),
      });
    });
  }

  const canonicalSnapshot = await getDoc(doc(db, "users", user.uid));
  if (canonicalSnapshot.exists()) {
    recordsById.set(user.uid, {
      id: user.uid,
      data: canonicalSnapshot.data(),
    });
  }

  const records = Array.from(recordsById.values());
  const canonicalRecord = recordsById.get(user.uid)?.data ?? null;
  const approvedIdentityApplication = await loadApprovedIdentityApplication(
    user.uid,
    normalizedEmail,
  );
  const { merged, roles, memberships } = mergeUsefulDocuments(records, user.uid);
  const preservedProfilePhotoURL = resolvePersistedUserProfilePhoto(
    {
      profilePhotoURL: approvedApplicationField(
        approvedIdentityApplication,
        "applicationPhotoURL",
      ),
    },
    merged,
    ...records.map((record) => record.data),
  );
  const preservedAuthPhotoURL =
    (typeof user.photoURL === "string" && user.photoURL.trim()) ||
    (typeof merged.authPhotoURL === "string" && merged.authPhotoURL.trim()) ||
    null;

  if (roles.size === 0) roles.add("driver");
  const finalRole = roles.has("senior")
    ? "senior"
    : roles.has("admin")
      ? "admin"
      : "driver";
  if (finalRole === "admin") {
    roles.add("admin");
    roles.add("driver");
  }

  if (Object.keys(memberships).length === 0 && merged.companyId) {
    memberships[merged.companyId] = {
      role: finalRole,
      roles: Array.from(roles),
      status: merged.status || (finalRole === "admin" ? "active" : "pending"),
    };
  }

  const hasActiveRecord = records.some((record) => record.data.status === "active");
  const finalStatus =
    finalRole === "admin" || finalRole === "senior" || hasActiveRecord
      ? "active"
      : merged.status || "pending";

  const finalUserData = {
    ...merged,
    id: user.uid,
    email: normalizedEmail,
    name: resolveCanonicalProfileName({
      approvedApplication: approvedIdentityApplication,
      canonicalRecord,
      mergedRecord: merged,
      googleDisplayName: user.displayName,
      email: normalizedEmail,
    }),
    whatsapp:
      approvedApplicationField(approvedIdentityApplication, "whatsapp") ||
      merged.whatsapp ||
      "",
    authPhotoURL: preservedAuthPhotoURL,
    ...(preservedProfilePhotoURL && {
      profilePhotoURL: preservedProfilePhotoURL,
    }),
    role: finalRole,
    roles: Array.from(roles),
    status: finalStatus,
    memberships,
    canonicalUserId: user.uid,
    updatedAt: new Date().toISOString(),
  };

  // O documento canônico é salvo antes de qualquer limpeza. Assim uma falha de
  // migração nunca apaga o único registro utilizável do usuário.
  await setDoc(doc(db, "users", user.uid), finalUserData, { merge: true });

  const duplicateIds = records
    .map((record) => record.id)
    .filter((id) => id !== user.uid);

  for (const oldUserId of duplicateIds) {
    const migrationComplete = await migrateUserReferences(oldUserId, user.uid);
    if (migrationComplete) {
      await deleteDoc(doc(db, "users", oldUserId));
    } else {
      await setDoc(
        doc(db, "users", oldUserId),
        {
          mergedInto: user.uid,
          canonicalUserId: user.uid,
          mergePending: true,
          updatedAt: new Date().toISOString(),
        },
        { merge: true },
      );
    }
  }

  return finalUserData;
}
