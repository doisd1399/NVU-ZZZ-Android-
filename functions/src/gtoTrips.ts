import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { createHash } from "node:crypto";
import { finalValueCompatibilityIssue, parsePositiveNumber } from "./gtoMoney";


type CallableContext = functions.https.CallableContext;

type GtoTripRequest = {
  contractVersion?: unknown;
  sessionId?: unknown;
  driverId?: unknown;
  companyId?: unknown;
  jobId?: unknown;
  contractId?: unknown;
  contractMode?: unknown;
  vehicleId?: unknown;
  trailerId?: unknown;
  cargo?: unknown;
  companyRoute?: unknown;
  originCompany?: unknown;
  destinationCompany?: unknown;
  origin?: unknown;
  destination?: unknown;
  distanceKm?: unknown;
  offeredValue?: unknown;
  rawText?: unknown;
  selectedRow?: unknown;
  freightFingerprint?: unknown;
  finalValue?: unknown;
  completionStatus?: unknown;
  completedAtClient?: unknown;
};

const text = (value: unknown, maxLength = 180): string => {
  if (typeof value !== "string") return "";
  return value.trim().replace(/\s+/g, " ").slice(0, maxLength);
};

const normalizeAlias = (value: unknown): string =>
  text(value, 200)
    .toLocaleLowerCase("pt-BR")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

const isGtoAlias = (...values: unknown[]): boolean =>
  values.some((value) => {
    const normalized = normalizeAlias(value);
    return normalized === "gto" || normalized === "global-truck-online" || normalized === "global-truck";
  });

const safeClientTimestamp = (value: unknown): number | null => {
  const parsed = typeof value === "number" ? value : Number(String(value ?? "").trim());
  if (!Number.isFinite(parsed) || parsed <= 0) return null;
  const maxFuture = Date.now() + 24 * 60 * 60 * 1000;
  return parsed <= maxFuture ? Math.trunc(parsed) : null;
};

const assertDocumentId = (value: string, field: string): void => {
  if (!value || value.length > 220 || value.includes("/")) {
    throw new functions.https.HttpsError("invalid-argument", `${field} inválido.`);
  }
};

const assertBoundedText = (value: string, field: string, maxLength = 220): void => {
  if (value.length < 1 || value.length > maxLength || /[\u0000-\u001F]/.test(value)) {
    throw new functions.https.HttpsError("invalid-argument", `${field} inválido.`);
  }
};

const gtoPayloadFingerprint = (fields: Record<string, unknown>): string => {
  const ordered = [
    "sessionId", "driverId", "companyId", "jobId", "contractId", "vehicleId", "trailerId",
    "cargo", "companyRoute", "originCompany", "destinationCompany", "origin", "destination",
    "distanceKm", "offeredValue", "rawText", "selectedRow", "finalValue",
    "completionStatus", "completedAtClient",
  ].map((key) => [key, fields[key] ?? null]);
  return createHash("sha256").update(JSON.stringify(ordered)).digest("hex");
};

const freightFingerprintForRequest = (fields: {
  cargo: string;
  companyRoute: string;
  originCompany: string;
  destinationCompany: string;
  origin: string;
  destination: string;
  distanceKmRaw: string;
  offeredValueRaw: string;
  rawText: string;
  selectedRow: number;
}): string => {
  const ordered: Array<[string, string]> = [
    ["cargo", text(fields.cargo, 160)],
    ["companyRoute", text(fields.companyRoute, 220)],
    ["originCompany", text(fields.originCompany, 180)],
    ["destinationCompany", text(fields.destinationCompany, 180)],
    ["origin", text(fields.origin, 180)],
    ["destination", text(fields.destination, 180)],
    ["distanceKm", text(fields.distanceKmRaw, 80)],
    ["offeredValue", text(fields.offeredValueRaw, 80)],
    ["rawText", text(fields.rawText, 1200)],
    ["selectedRow", String(fields.selectedRow)],
  ];
  const canonical = ordered
    .map(([key, value]) => `${key}=${value.length}:${value}|`)
    .join("");
  return createHash("sha256").update(canonical, "utf8").digest("hex");
};

const statusIsTripRecordable = (value: unknown): boolean => {
  const normalized = text(value, 40).toLowerCase();
  return normalized === "active" || normalized === "delayed";
};

const statusIsValidTrip = (value: unknown): boolean => {
  const normalized = normalizeAlias(value);
  return [
    "concluida",
    "concluido",
    "completed",
    "finalizado",
    "finalizada",
    "entregue",
  ].includes(normalized);
};

const hasUsableMetricDate = (data: admin.firestore.DocumentData): boolean => {
  const raw =
    data.completedAt ??
    data.dataFechamento ??
    data.date ??
    data.dataLancamento ??
    data.createdAt;
  if (!raw) return false;
  if (raw instanceof admin.firestore.Timestamp) return raw.toMillis() > 0;
  if (raw instanceof Date) return raw.getTime() > 0;
  if (typeof raw?.toMillis === "function") return Number(raw.toMillis()) > 0;
  if (typeof raw?.toDate === "function") return raw.toDate().getTime() > 0;
  const parsed = new Date(raw);
  return !Number.isNaN(parsed.getTime()) && parsed.getTime() > 0;
};

const isValidTripDocument = (data: admin.firestore.DocumentData): boolean => {
  const status = text(data.status, 80);
  const normalizedStatus = normalizeAlias(status);
  const canceled =
    ["cancelado", "cancelada", "canceled", "cancelled", "excluido", "excluida"].includes(
      normalizedStatus,
    ) ||
    data.cancelado === true ||
    data.deleted === true ||
    data.softDeleted === true;

  if (canceled) return false;
  if (statusIsValidTrip(status)) return true;
  return !status && hasUsableMetricDate(data);
};

const safeTripDocumentId = (uid: string, sessionId: string): string => {
  const digest = createHash("sha256")
    .update(`${uid}|${sessionId}`)
    .digest("hex")
    .slice(0, 40);
  return `gto_${uid}_${digest}`.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 180);
};

const requireAuthenticatedUid = (context: CallableContext): string => {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Autenticação obrigatória para registrar a viagem GTO.",
    );
  }
  return uid;
};

const ensureNotSuspended = (user: admin.firestore.DocumentData, now: Date) => {
  const raw = user.operationalSuspendedUntil;
  const suspendedUntil = raw instanceof admin.firestore.Timestamp
    ? raw.toDate()
    : raw?.toDate?.() instanceof Date
      ? raw.toDate()
      : null;
  if (suspendedUntil && suspendedUntil.getTime() > now.getTime()) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "As atividades operacionais deste motorista estão suspensas.",
    );
  }
};

const resolveSimulator = async (
  database: admin.firestore.Firestore,
  company: admin.firestore.DocumentData,
): Promise<{ id: string; name: string; isGto: boolean }> => {
  const simulatorId = text(company.simulatorId ?? company.simuladorId, 180);
  let simulatorName = text(
    company.simulatorName ?? company.simuladorNome ?? company.simulator,
    180,
  );
  let catalog: admin.firestore.DocumentData | null = null;

  if (simulatorId) {
    const snapshot = await database.collection("simulators").doc(simulatorId).get();
    if (snapshot.exists) {
      catalog = snapshot.data() || {};
      simulatorName = text(
        catalog.name ?? catalog.simulatorName ?? catalog.nome ?? simulatorName,
        180,
      );
    }
  }

  return {
    id: simulatorId || "gto",
    name: simulatorName || "GTO",
    isGto: isGtoAlias(
      simulatorId,
      simulatorName,
      company.simulator,
      company.simuladorNome,
      catalog?.name,
      catalog?.simulatorName,
      catalog?.id,
    ),
  };
};

const tripMetricMillis = (data: admin.firestore.DocumentData): number => {
  const raw =
    data.completedAt ??
    data.dataFechamento ??
    data.date ??
    data.dataLancamento ??
    data.createdAt;
  if (!raw) return 0;
  if (raw instanceof admin.firestore.Timestamp) return raw.toMillis();
  if (raw instanceof Date) return raw.getTime();
  if (typeof raw?.toMillis === "function") return Number(raw.toMillis()) || 0;
  if (typeof raw?.toDate === "function") return raw.toDate().getTime();
  const parsed = new Date(raw).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
};

const deriveSimpleCompletedRoutes = (
  docs: Array<{ id: string; data(): Record<string, any> }>,
): Array<{ origin: string; destination: string }> => {
  return docs
    .map((document) => ({ id: document.id, data: document.data() || {} }))
    .filter((entry) => isValidTripDocument(entry.data))
    .sort((a, b) => {
      const byTime = tripMetricMillis(a.data) - tripMetricMillis(b.data);
      return byTime !== 0 ? byTime : a.id.localeCompare(b.id);
    })
    .map((entry) => {
      const data = entry.data;
      return {
        origin: text(data.origem ?? data.origin ?? data.gtoOriginCompany ?? data.gtoOrigin, 180),
        destination: text(data.destino ?? data.destination ?? data.gtoDestination, 180),
      };
    })
    .filter((route) => Boolean(route.origin) && Boolean(route.destination));
};

const syncJobProgress = async (
  database: admin.firestore.Firestore,
  jobId: string,
  totalDeliveries: number,
  contractMode = "",
): Promise<{ progress: number; jobStatus: string }> => {
  const jobRef = database.collection("trabalhos").doc(jobId);
  const tripsSnapshot = await database
    .collection("historico_viagens")
    .where("jobId", "==", jobId)
    .get();

  const validTripDocs = tripsSnapshot.docs.filter((document) =>
    isValidTripDocument(document.data() || {}),
  );
  const progress = validTripDocs.length;

  const freshJobSnapshot = await jobRef.get();
  const currentStatus = text(freshJobSnapshot.data()?.status, 60);
  let nextStatus = currentStatus;
  const progressUpdate: Record<string, unknown> = { progress };
  if (contractMode === "simple") {
    progressUpdate.completedRoutes = deriveSimpleCompletedRoutes(tripsSnapshot.docs);
    // Retire HF1 continuity metadata. A completed destination is never an origin source.
    progressUpdate.lastKnownGtoCity = admin.firestore.FieldValue.delete();
    progressUpdate.gtoRouteContinuityUpdatedAt = admin.firestore.FieldValue.delete();
  }

  if (
    totalDeliveries > 0 &&
    progress >= totalDeliveries &&
    ["active", "delayed", "pending"].includes(currentStatus)
  ) {
    nextStatus = "awaiting_completion";
    progressUpdate.status = nextStatus;
  } else if (
    currentStatus === "awaiting_completion" &&
    progress < totalDeliveries
  ) {
    nextStatus = "active";
    progressUpdate.status = nextStatus;
  } else if (currentStatus === "pending" && progress > 0) {
    nextStatus = "active";
    progressUpdate.status = nextStatus;
  }

  if (freshJobSnapshot.exists) {
    await jobRef.set(progressUpdate, { merge: true });
  }

  return { progress, jobStatus: nextStatus };
};

export const registerGtoTrip = functions.region("us-central1").https.onCall(
  async (rawData: GtoTripRequest, context) => {
    const db = admin.firestore();
    const uid = requireAuthenticatedUid(context);
    const clientContractVersionRaw = Number(rawData?.contractVersion);
    const clientContractVersion = Number.isFinite(clientContractVersionRaw) && clientContractVersionRaw > 0
      ? Math.trunc(clientContractVersionRaw)
      : 17;
    const sessionId = text(rawData?.sessionId, 160);
    const requestedDriverId = text(rawData?.driverId, 180);
    const companyId = text(rawData?.companyId, 180);
    const jobId = text(rawData?.jobId, 180);
    const contractId = text(rawData?.contractId, 180);
    const requestedContractMode = text(rawData?.contractMode, 20).toLowerCase();
    const requestedVehicleId = text(rawData?.vehicleId, 180);
    const requestedTrailerId = text(rawData?.trailerId, 180);
    const cargo = text(rawData?.cargo, 160);
    const companyRoute = text(rawData?.companyRoute, 220);
    const originCompany = text(rawData?.originCompany, 180);
    const destinationCompany = text(rawData?.destinationCompany, 180);
    const origin = text(rawData?.origin, 180);
    const destination = text(rawData?.destination, 180);
    const rawText = text(rawData?.rawText, 1200);
    const selectedRowRaw = Number(rawData?.selectedRow);
    const selectedRow = Number.isFinite(selectedRowRaw) ? Math.trunc(selectedRowRaw) : -1;
    const freightFingerprint = text(rawData?.freightFingerprint, 80);
    const distanceKmRaw = text(rawData?.distanceKm, 80);
    const offeredValueRaw = text(rawData?.offeredValue, 80);
    const distanceKm = parsePositiveNumber(rawData?.distanceKm);
    const offeredValue = parsePositiveNumber(rawData?.offeredValue);
    const finalValue = parsePositiveNumber(rawData?.finalValue);
    const completionStatus = text(rawData?.completionStatus, 80);
    const completedAtClient = safeClientTimestamp(rawData?.completedAtClient);

    if (!/^[A-Za-z0-9_-]{8,160}$/.test(sessionId)) {
      throw new functions.https.HttpsError("invalid-argument", "sessionId inválido.");
    }
    for (const [field, value] of [
      ["companyId", companyId], ["jobId", jobId], ["contractId", contractId],
    ] as const) {
      assertDocumentId(value, field);
    }
    if (clientContractVersion >= 18 && !requestedDriverId) {
      throw new functions.https.HttpsError("invalid-argument", "driverId é obrigatório no contrato FIX18.");
    }
    if (requestedDriverId) assertDocumentId(requestedDriverId, "driverId");
    if (requestedDriverId && requestedDriverId !== uid) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "A sessão GTO não pertence ao motorista autenticado.",
      );
    }
    assertBoundedText(cargo, "cargo");
    assertBoundedText(originCompany, "originCompany");
    // HF14: destinationCompany is optional metadata. Keep it in the fingerprint/payload
    // when present, but never reject a real trip solely because GTO did not expose/read it.
    if (destinationCompany) assertBoundedText(destinationCompany, "destinationCompany");
    assertBoundedText(origin, "origin");
    assertBoundedText(destination, "destination");
    if (selectedRow < 0) {
      throw new functions.https.HttpsError("invalid-argument", "selectedRow inválido.");
    }
    if (!/^[a-f0-9]{64}$/.test(freightFingerprint)) {
      throw new functions.https.HttpsError("invalid-argument", "freightFingerprint inválido.");
    }
    const expectedFreightFingerprint = freightFingerprintForRequest({
      cargo,
      companyRoute,
      originCompany,
      destinationCompany,
      origin,
      destination,
      distanceKmRaw,
      offeredValueRaw,
      rawText,
      selectedRow,
    });
    if (freightFingerprint !== expectedFreightFingerprint) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "A identidade do frete não corresponde aos dados enviados; registro bloqueado.",
      );
    }
    if (origin !== originCompany) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "A origem da viagem deve corresponder exatamente à empresa de origem detectada no GTO.",
      );
    }
    if (distanceKm <= 0 || distanceKm > 50_000 || finalValue <= 0 || finalValue > 1_000_000_000) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Distância ou valor final do frete GTO está fora da faixa válida.",
      );
    }
    if (offeredValue > 1_000_000_000) {
      throw new functions.https.HttpsError("invalid-argument", "Valor ofertado do frete GTO está fora da faixa válida.");
    }
    const moneyCompatibilityIssue = finalValueCompatibilityIssue(offeredValue, finalValue);
    if (moneyCompatibilityIssue) {
      throw new functions.https.HttpsError("failed-precondition", moneyCompatibilityIssue);
    }
    if (completionStatus !== "CONFIRMED_NORMAL") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Somente entregas GTO com recebimento normal podem ser registradas.",
      );
    }
    if (clientContractVersion >= 18 && completedAtClient === null) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "completedAtClient inválido para o contrato FIX18.",
      );
    }

    const payloadFingerprint = gtoPayloadFingerprint({
      sessionId,
      driverId: requestedDriverId || uid,
      companyId,
      jobId,
      contractId,
      vehicleId: requestedVehicleId,
      trailerId: requestedTrailerId,
      cargo,
      companyRoute,
      originCompany,
      destinationCompany,
      origin,
      destination,
      distanceKm,
      freightFingerprint,
      selectedRow,
      offeredValue: offeredValue || null,
      finalValue,
      completionStatus,
      completedAtClient,
    });

    const tripId = safeTripDocumentId(uid, sessionId);
    const tripRef = db.collection("historico_viagens").doc(tripId);
    const jobRef = db.collection("trabalhos").doc(jobId);

    const [
      userSnapshot,
      companySnapshot,
      jobSnapshot,
      contractSnapshot,
      existingTripSnapshot,
    ] = await Promise.all([
      db.collection("users").doc(uid).get(),
      db.collection("frotas").doc(companyId).get(),
      jobRef.get(),
      db.collection("contratos").doc(contractId).get(),
      tripRef.get(),
    ]);

    const contractForRetry = contractSnapshot.exists
      ? contractSnapshot.data() || {}
      : {};
    const retryTotalDeliveries = Math.max(
      0,
      Number(contractForRetry.totalDeliveries || 0),
    );

    // Idempotency must win over current operation state and resource cleanup.
    // If the server already created this exact session but the device missed
    // the response, a retry must still return success even if the first write
    // completed the operation or an administrator later archived its context.
    if (existingTripSnapshot.exists) {
      const existing = existingTripSnapshot.data() || {};
      const belongsToCaller =
        text(existing.driverId ?? existing.motoristaId, 180) === uid &&
        text(existing.gtoTripSessionId ?? existing.gtoSessionId, 160) === sessionId &&
        text(existing.companyId ?? existing.empresaId, 180) === companyId &&
        text(existing.jobId, 180) === jobId &&
        text(existing.contractId ?? existing.contratoId, 180) === contractId;
      const existingFingerprint = text(existing.gtoPayloadFingerprint, 80);

      if (!belongsToCaller || (existingFingerprint && existingFingerprint !== payloadFingerprint)) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "A sessão automática informada não corresponde à viagem existente.",
        );
      }

      if (jobSnapshot.exists) {
        const synced = await syncJobProgress(
          db,
          jobId,
          retryTotalDeliveries,
          text(contractForRetry.mode, 20).toLowerCase(),
        );
        return {
          success: true,
          contractVersion: 18,
          sessionId,
          tripId,
          created: false,
          duplicate: true,
          payloadFingerprint,
          progress: synced.progress,
          jobStatus: synced.jobStatus,
        };
      }

      return {
        success: true,
        contractVersion: 18,
        sessionId,
        tripId,
        created: false,
        duplicate: true,
        payloadFingerprint,
        progress: Number(existing.progress || 0),
        jobStatus: "",
      };
    }

    if (!userSnapshot.exists || !companySnapshot.exists || !jobSnapshot.exists || !contractSnapshot.exists) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O contexto operacional da viagem não está mais disponível.",
      );
    }

    const user = userSnapshot.data() || {};
    const company = companySnapshot.data() || {};
    const job = jobSnapshot.data() || {};
    const contract = contractSnapshot.data() || {};
    const now = new Date();
    const totalDeliveries = Math.max(0, Number(contract.totalDeliveries || 0));

    ensureNotSuspended(user, now);

    const jobDriverId = text(job.driverId ?? job.motoristaId ?? job.userId, 180);
    if (jobDriverId !== uid) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "O trabalho ativo não pertence ao motorista autenticado.",
      );
    }
    if (text(job.companyId, 180) !== companyId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O trabalho não pertence à empresa informada.",
      );
    }
    if (text(job.contractId, 180) !== contractId || text(contract.companyId, 180) !== companyId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O contrato não corresponde ao trabalho atual.",
      );
    }
    if (!statusIsTripRecordable(job.status)) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "A operação não está disponível para registrar novas viagens.",
      );
    }

    const serverContractMode = text(contract.mode, 20).toLowerCase();
    if (serverContractMode !== "simple" && serverContractMode !== "detailed") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O contrato não possui um modo operacional válido para registrar a viagem GTO.",
      );
    }
    if (requestedContractMode && requestedContractMode !== serverContractMode) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O modo do contrato mudou desde o início da sessão GTO.",
      );
    }
    if (serverContractMode === "detailed" && !origin) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "A origem é obrigatória em contratos detalhados.",
      );
    }

    // Business rule for automatic GTO trips: the source company displayed on the
    // selected freight card is the canonical Origem shown, stored and transmitted.
    // Previous destinations are never reused as origin continuity.
    const effectiveOrigin = originCompany;
    const effectiveOriginSource = "GTO_ORIGIN_COMPANY";

    const simulator = await resolveSimulator(db, company);
    if (!simulator.isGto) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O lançamento automático é permitido somente para empresas do simulador GTO.",
      );
    }

    const assignedJobVehicleId = text(job.vehicleId, 180);
    const assignedJobTrailerId = text(job.trailerId, 180) || text(contract.trailerId, 180);
    if (requestedVehicleId && assignedJobVehicleId && requestedVehicleId !== assignedJobVehicleId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O veículo da operação mudou desde o início da viagem GTO.",
      );
    }
    if (requestedTrailerId && assignedJobTrailerId && requestedTrailerId !== assignedJobTrailerId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O reboque da operação mudou desde o início da viagem GTO.",
      );
    }

    const vehicleId = assignedJobVehicleId || requestedVehicleId;
    const trailerId = assignedJobTrailerId || requestedTrailerId;
    const [vehicleSnapshot, trailerSnapshot] = await Promise.all([
      vehicleId ? db.collection("veiculos").doc(vehicleId).get() : Promise.resolve(null),
      trailerId ? db.collection("reboques").doc(trailerId).get() : Promise.resolve(null),
    ]);
    const vehicle = vehicleSnapshot?.exists ? vehicleSnapshot.data() || {} : {};
    const trailer = trailerSnapshot?.exists ? trailerSnapshot.data() || {} : {};

    if (vehicleId && text(vehicle.companyId, 180) && text(vehicle.companyId, 180) !== companyId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O veículo não pertence à empresa do trabalho atual.",
      );
    }
    if (trailerId && text(trailer.companyId, 180) && text(trailer.companyId, 180) !== companyId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O reboque não pertence à empresa do trabalho atual.",
      );
    }

    const serverNow = admin.firestore.FieldValue.serverTimestamp();
    const driverName = text(user.name ?? user.nome ?? context.auth?.token?.name, 180) || "Motorista";
    const companyName = text(company.companyName ?? company.name ?? company.nome, 180) || "Empresa";
    const contractName = text(contract.name ?? contract.nome, 180);
    const vehicleName = text(vehicle.name ?? vehicle.nome, 180);
    const trailerName = text(trailer.name ?? trailer.nome, 180);
    const transactionResult = await db.runTransaction(async (transaction) => {
      const [existingTrip, freshJob] = await Promise.all([
        transaction.get(tripRef),
        transaction.get(jobRef),
      ]);

      if (existingTrip.exists) {
        const existing = existingTrip.data() || {};
        const existingFingerprint = text(existing.gtoPayloadFingerprint, 80);
        const sameSession =
          text(existing.driverId ?? existing.motoristaId, 180) === uid &&
          text(existing.gtoTripSessionId ?? existing.gtoSessionId, 160) === sessionId &&
          text(existing.companyId ?? existing.empresaId, 180) === companyId &&
          text(existing.jobId, 180) === jobId &&
          text(existing.contractId ?? existing.contratoId, 180) === contractId;
        if (!sameSession || (existingFingerprint && existingFingerprint !== payloadFingerprint)) {
          throw new functions.https.HttpsError(
            "already-exists",
            "A chave desta sessão GTO já está vinculada a dados diferentes.",
          );
        }
        return { created: false };
      }

      const freshJobData = freshJob.exists ? freshJob.data() || {} : {};
      const freshDriverId = text(
        freshJobData.driverId ?? freshJobData.motoristaId ?? freshJobData.userId,
        180,
      );
      if (!freshJob.exists || freshDriverId !== uid || !statusIsTripRecordable(freshJobData.status)) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "A operação mudou antes da confirmação da viagem.",
        );
      }

      transaction.create(tripRef, {
        tripId,
        empresaId: companyId,
        companyId,
        empresaNome: companyName,
        simulatorId: simulator.id,
        simulatorName: simulator.name,
        simuladorNome: simulator.name,
        motoristaId: uid,
        driverId: uid,
        motoristaNome: driverName,
        contratoId: contractId,
        contractId,
        contratoNumero: contractName,
        contratoDescricao: "",
        jobId,
        veiculoId: vehicleId,
        veiculoNome: vehicleName,
        veiculoPlaca: text(vehicle.plate, 80),
        reboqueId: trailerId,
        reboqueNome: trailerName,
        carga: cargo,
        origem: effectiveOrigin,
        origemConhecida: Boolean(effectiveOrigin),
        origemFonte: effectiveOriginSource,
        destino: destination,
        distanciaPercorrida: distanceKm,
        valor: finalValue,
        valorPrevisto: offeredValue || null,
        status: "concluida",
        criadoPor: uid,
        dataLancamento: serverNow,
        completedAt: serverNow,
        createdAt: serverNow,
        updatedAt: serverNow,
        uploadedAt: serverNow,
        uploadedBy: uid,
        comprovanteUrl: "",
        comprovanteTituloOriginal: "",
        source: "gto_auto",
        registroAutomatico: true,
        automationSource: "gto-native-v1",
        gtoContractVersion: clientContractVersion,
        gtoPayloadFingerprint: payloadFingerprint,
        gtoTripSessionId: sessionId,
        gtoSessionId: sessionId,
        gtoCargo: cargo,
        gtoCompanyRoute: companyRoute,
        gtoOriginCompany: originCompany,
        gtoDestinationCompany: destinationCompany,
        gtoRequestOrigin: origin,
        gtoOrigin: effectiveOrigin,
        gtoOriginKnown: Boolean(effectiveOrigin),
        gtoOriginSource: effectiveOriginSource,
        gtoDestination: destination,
        gtoDistanceKm: distanceKm,
        gtoOfferedValue: offeredValue || null,
        gtoRawText: rawText,
        gtoSelectedRow: selectedRow,
        gtoFreightFingerprint: freightFingerprint,
        gtoFinalValue: finalValue,
        gtoMoneySchemaVersion: 2,
        gtoCompletionStatus: completionStatus,
        gtoCompletedAtClient: completedAtClient,
      });

      return { created: true };
    });

    const synced = await syncJobProgress(db, jobId, totalDeliveries, serverContractMode);

    return {
      success: true,
      contractVersion: 18,
      sessionId,
      tripId,
      created: transactionResult.created,
      duplicate: !transactionResult.created,
      payloadFingerprint,
      progress: synced.progress,
      jobStatus: synced.jobStatus,
    };
  },
);
