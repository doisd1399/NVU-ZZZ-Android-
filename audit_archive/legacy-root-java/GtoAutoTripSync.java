package com.nvu.operacional;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX18 durable/integrity layer for completed GTO trips.
 *
 * Real-time freight detection stays in GtoObserverService. This class only owns
 * the immutable trip snapshot, durable completed-delivery queue and Firebase
 * acknowledgement contract. A detected freight is locked to the session before
 * the trip starts, so later UI/context refreshes cannot silently change the trip
 * that is eventually registered.
 */
final class GtoAutoTripSync {
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    static final String STATUS_SYNCING = "SYNCING";
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SYNCED = "SYNCED";
    static final String STATUS_REJECTED = "REJECTED";

    static final int CONTRACT_VERSION = 18;

    private static final String QUEUE_PREFS = "nvu_gto_auto_trip_queue_v1"; // retained for FIX17 queue compatibility
    private static final String RETRY_PREFS = "nvu_gto_auto_trip_retry_v1";
    private static final String SNAPSHOT_PREFS = "nvu_gto_trip_snapshot_v2";
    private static final String QUARANTINE_PREFS = "nvu_gto_auto_trip_quarantine_v2";
    private static final String QUEUE_PREFIX = "trip_";
    private static final String SNAPSHOT_PREFIX = "snapshot_";
    private static final String QUARANTINE_PREFIX = "quarantine_";
    private static final String RETRY_AT_PREFIX = "retry_at_";
    private static final String RETRY_COUNT_PREFIX = "retry_count_";
    private static final long BASE_RETRY_MS = 15_000L;
    private static final long MAX_RETRY_MS = 5 * 60_000L;
    private static final long CALL_WATCHDOG_MS = 25_000L;
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final String[] HASH_FIELDS = new String[] {
        "contractVersion", "sessionId", "driverId", "companyId", "jobId", "contractId",
        "vehicleId", "trailerId", "driverName", "companyName", "contractName", "vehicleName",
        "trailerName", "cargo", "originCompany", "destinationCompany", "destination",
        "distanceKm", "offeredValue", "finalValue", "completionStatus", "completedAtClient"
    };

    interface Listener {
        void onSynced(String sessionId, String tripId);
        void onPending(String sessionId, String message);
    }

    private static final class QueueRecord {
        final JSONObject payload;
        final boolean legacy;

        QueueRecord(JSONObject payload, boolean legacy) {
            this.payload = payload;
            this.legacy = legacy;
        }
    }

    private GtoAutoTripSync() {}

    static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    static boolean hasRecoverableSessionSnapshot(Context context, String sessionId, boolean requireFreightLocked) {
        String cleanSession = clean(sessionId);
        if (context == null || cleanSession.isEmpty()) return false;
        SharedPreferences snapshots = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE);
        JSONObject snapshot = readJson(snapshots.getString(SNAPSHOT_PREFIX + cleanSession, ""));
        if (snapshot == null) return false;
        if (!cleanSession.equals(clean(snapshot.optString("sessionId", "")))) return false;
        if (validateContextSnapshot(snapshot) != null) return false;
        return !requireFreightLocked || snapshot.optBoolean("freightLocked", false);
    }

    /** Locks NVU operation context to this trip session before freight detection starts. */
    static boolean beginSessionSnapshot(Context context, SharedPreferences prefs, String sessionId) {
        String cleanSession = clean(sessionId);
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("contractVersion", CONTRACT_VERSION);
            snapshot.put("sessionId", cleanSession);
            snapshot.put("createdAt", System.currentTimeMillis());
            copyContextFromPrefs(snapshot, prefs);
            snapshot.put("freightLocked", false);
        } catch (JSONException error) {
            markIntegrityError(prefs, "Falha ao criar snapshot da operação GTO.");
            return false;
        }

        String issue = validateContextSnapshot(snapshot);
        if (issue != null) {
            markIntegrityError(prefs, issue);
            return false;
        }

        boolean committed = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SNAPSHOT_PREFIX + cleanSession, snapshot.toString())
            .commit();
        if (!committed) {
            markIntegrityError(prefs, "Não foi possível persistir a operação GTO no aparelho.");
            return false;
        }
        prefs.edit()
            .putString("gtoTripIntegrityStatus", "CONTEXT_LOCKED")
            .remove("gtoTripIntegrityError")
            .apply();
        return true;
    }

    /** Locks the selected freight into the immutable per-session snapshot. */
    static boolean lockSelectedFreight(Context context, SharedPreferences prefs) {
        String sessionId = clean(prefs.getString("gtoTripSessionId", ""));
        if (sessionId.isEmpty()) {
            markIntegrityError(prefs, "Sessão GTO ausente ao confirmar o frete.");
            return false;
        }

        SharedPreferences snapshots = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE);
        JSONObject snapshot = readJson(snapshots.getString(SNAPSHOT_PREFIX + sessionId, ""));
        if (snapshot == null) {
            // FIX17 migration/recovery: reconstruct the context snapshot once.
            if (!beginSessionSnapshot(context, prefs, sessionId)) return false;
            snapshot = readJson(snapshots.getString(SNAPSHOT_PREFIX + sessionId, ""));
            if (snapshot == null) return false;
        }

        JSONObject candidate = new JSONObject();
        try {
            candidate.put("cargo", clean(prefs.getString("selectedCargo", "")));
            candidate.put("originCompany", clean(prefs.getString("selectedOriginCompany", "")));
            candidate.put("destinationCompany", clean(prefs.getString("selectedDestinationCompany", "")));
            candidate.put("destination", clean(prefs.getString("selectedDestination", "")));
            candidate.put("distanceKm", clean(prefs.getString("selectedKm", "")));
            candidate.put("offeredValue", clean(prefs.getString("selectedValue", "")));
        } catch (JSONException error) {
            markIntegrityError(prefs, "Falha ao preparar os dados do frete selecionado.");
            return false;
        }

        String freightIssue = validateFreight(candidate);
        if (freightIssue != null) {
            markIntegrityError(prefs, freightIssue);
            return false;
        }

        if (snapshot.optBoolean("freightLocked", false)) {
            if (!sameFreight(snapshot, candidate)) {
                markIntegrityError(prefs, "O frete desta sessão já estava bloqueado com dados diferentes.");
                return false;
            }
            return true;
        }

        try {
            for (String field : new String[] {"cargo", "originCompany", "destinationCompany", "destination", "distanceKm", "offeredValue"}) {
                snapshot.put(field, candidate.optString(field, ""));
            }
            snapshot.put("selectionSource", clean(prefs.getString("selectionSource", "")));
            snapshot.put("freightLocked", true);
            snapshot.put("freightLockedAt", System.currentTimeMillis());
        } catch (JSONException error) {
            markIntegrityError(prefs, "Falha ao bloquear os dados do frete selecionado.");
            return false;
        }

        boolean committed = snapshots.edit()
            .putString(SNAPSHOT_PREFIX + sessionId, snapshot.toString())
            .commit();
        if (!committed) {
            markIntegrityError(prefs, "Não foi possível persistir o frete selecionado no aparelho.");
            return false;
        }
        prefs.edit()
            .putString("gtoTripIntegrityStatus", "FREIGHT_LOCKED")
            .remove("gtoTripIntegrityError")
            .apply();
        return true;
    }

    static boolean hasPending(Context context) {
        SharedPreferences queue = context.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences retry = context.getSharedPreferences(RETRY_PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        for (String key : queue.getAll().keySet()) {
            if (!key.startsWith(QUEUE_PREFIX)) continue;
            String sessionId = key.substring(QUEUE_PREFIX.length());
            long retryAt = retry.getLong(RETRY_AT_PREFIX + sessionId, 0L);
            if (retryAt <= now) return true;
        }
        return false;
    }

    /** Persists a sealed completed payload before the network call is attempted. */
    static boolean enqueueConfirmedTrip(Context context, SharedPreferences mainPrefs, Listener listener) {
        String sessionId = clean(mainPrefs.getString("gtoTripSessionId", ""));
        if (sessionId.isEmpty()) {
            markPending(mainPrefs, "Sessão GTO ausente; a viagem não foi enviada ainda.");
            if (listener != null) listener.onPending("", "Sessão GTO ausente");
            return false;
        }

        JSONObject payload = buildPayload(context, mainPrefs, sessionId);
        String validationIssue = payload == null ? "Dados da viagem incompletos" : validateCompletedPayload(payload);
        if (payload == null || validationIssue != null) {
            String issue = validationIssue == null ? "Dados da viagem incompletos" : validationIssue;
            markPending(mainPrefs, issue + "; registro preservado para correção/retry.");
            if (listener != null) listener.onPending(sessionId, issue);
            return false;
        }

        SharedPreferences queue = context.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences retry = context.getSharedPreferences(RETRY_PREFS, Context.MODE_PRIVATE);
        String sealed = sealPayload(payload);
        if (sealed.isEmpty() || !queue.edit().putString(QUEUE_PREFIX + sessionId, sealed).commit()) {
            markPending(mainPrefs, "Falha ao persistir a entrega concluída; o registro não foi descartado.");
            if (listener != null) listener.onPending(sessionId, "Falha de persistência local");
            return false;
        }

        retry.edit()
            .remove(RETRY_AT_PREFIX + sessionId)
            .remove(RETRY_COUNT_PREFIX + sessionId)
            .commit();
        mainPrefs.edit()
            .putString("gtoTripSyncStatus", STATUS_PENDING)
            .putString("gtoTripIntegrityStatus", "PAYLOAD_SEALED")
            .remove("gtoRegisteredTripId")
            .remove("gtoTripSyncError")
            .remove("gtoTripIntegrityError")
            .commit();

        flushPending(context, mainPrefs, listener);
        return true;
    }

    static void flushPending(Context context, SharedPreferences mainPrefs, Listener listener) {
        com.google.firebase.auth.FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            mainPrefs.edit()
                .putLong("gtoTripSyncLastAttemptAt", System.currentTimeMillis())
                .putString("gtoTripSyncLastErrorCode", "NO_NATIVE_AUTH")
                .apply();
            String message = "Aguardando autenticação NVU para sincronizar a viagem.";
            markPending(mainPrefs, message);
            if (listener != null) listener.onPending(mainPrefs.getString("gtoTripSessionId", ""), message);
            return;
        }
        String currentUid = clean(currentUser.getUid());

        SharedPreferences queue = context.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences retry = context.getSharedPreferences(RETRY_PREFS, Context.MODE_PRIVATE);
        Map<String, ?> all = queue.getAll();
        if (all.isEmpty()) return;
        long now = System.currentTimeMillis();

        List<String> keys = new ArrayList<>();
        for (String key : all.keySet()) {
            if (key.startsWith(QUEUE_PREFIX)) keys.add(key);
        }

        for (String key : keys) {
            String raw = queue.getString(key, "");
            String sessionId = key.substring(QUEUE_PREFIX.length());
            if (raw == null || raw.trim().isEmpty()) {
                quarantine(context, queue, key, sessionId, raw == null ? "" : raw, "Entrada de fila vazia");
                notifyIntegrityProblem(mainPrefs, listener, sessionId, "Fila local inválida preservada para diagnóstico.");
                continue;
            }

            long retryAt = retry.getLong(RETRY_AT_PREFIX + sessionId, 0L);
            if (retryAt > now) continue;
            if (!IN_FLIGHT.add(sessionId)) continue;

            QueueRecord record = readQueueRecord(raw);
            if (record == null) {
                quarantine(context, queue, key, sessionId, raw, "JSON/checksum da fila inválido");
                IN_FLIGHT.remove(sessionId);
                notifyIntegrityProblem(mainPrefs, listener, sessionId, "Integridade da fila local falhou; cópia preservada para diagnóstico.");
                continue;
            }
            JSONObject payload = record.payload;
            String validationIssue = validateCompletedPayload(payload);
            if (validationIssue != null) {
                quarantine(context, queue, key, sessionId, raw, validationIssue);
                IN_FLIGHT.remove(sessionId);
                notifyIntegrityProblem(mainPrefs, listener, sessionId, validationIssue);
                continue;
            }

            if (record.legacy) {
                // Upgrade a still-pending FIX17 raw payload in place without losing it.
                String upgraded = sealPayload(payload);
                if (!upgraded.isEmpty()) queue.edit().putString(key, upgraded).commit();
            }

            String payloadDriverId = clean(payload.optString("driverId", ""));
            if (!payloadDriverId.equals(currentUid)) {
                // Keep another driver's durable queue intact on shared devices, but never
                // leave the current trip looking like an endless network sync. This also
                // surfaces stale profile/native-auth mismatches that previously failed
                // silently on only some devices.
                IN_FLIGHT.remove(sessionId);
                if (sessionId.equals(mainPrefs.getString("gtoTripSessionId", ""))) {
                    mainPrefs.edit()
                        .putLong("gtoTripSyncLastAttemptAt", System.currentTimeMillis())
                        .putString("gtoTripSyncLastErrorCode", "DRIVER_UID_MISMATCH")
                        .apply();
                    String message = "A sessão autenticada não corresponde ao motorista desta entrega. Reabra a NVU com o perfil correto; a viagem permanece preservada.";
                    markPending(mainPrefs, message);
                    if (listener != null) listener.onPending(sessionId, message);
                }
                continue;
            }

            if (sessionId.equals(mainPrefs.getString("gtoTripSessionId", ""))) {
                mainPrefs.edit()
                    .putLong("gtoTripSyncLastAttemptAt", System.currentTimeMillis())
                    .remove("gtoTripSyncLastErrorCode")
                    .apply();
            }

            if (sessionId.equals(mainPrefs.getString("gtoTripSessionId", ""))) {
                mainPrefs.edit()
                    .putString("gtoTripSyncStatus", STATUS_SYNCING)
                    .remove("gtoTripSyncError")
                    .apply();
            }

            MAIN_HANDLER.postDelayed(() -> {
                if (!IN_FLIGHT.remove(sessionId)) return;
                if (!queue.contains(key)) return;
                scheduleRetry(retry, sessionId, null);
                String message = "O servidor não confirmou a viagem dentro do prazo. Registro preservado; nova tentativa automática.";
                if (sessionId.equals(mainPrefs.getString("gtoTripSessionId", ""))) {
                    mainPrefs.edit()
                        .putString("gtoTripSyncLastErrorCode", "CALL_TIMEOUT")
                        .putLong("gtoTripSyncLastAttemptAt", System.currentTimeMillis())
                        .apply();
                    markPending(mainPrefs, message);
                }
                if (listener != null) listener.onPending(sessionId, message);
            }, CALL_WATCHDOG_MS);

            FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("registerGtoTrip")
                .call(toMap(payload))
                .addOnSuccessListener(result -> {
                    Object data = result.getData();
                    Map<?, ?> response = data instanceof Map<?, ?> ? (Map<?, ?>) data : null;
                    String tripId = response == null ? "" : clean(String.valueOf(response.get("tripId") == null ? "" : response.get("tripId")));
                    String responseSession = response == null ? "" : clean(String.valueOf(response.get("sessionId") == null ? "" : response.get("sessionId")));
                    int responseContract = response == null ? 0 : safeInt(response.get("contractVersion"));
                    boolean success = response != null && Boolean.TRUE.equals(response.get("success"));

                    // Do not acknowledge/remove a local delivery unless the FIX18 backend
                    // proves it accepted this exact session under the current contract.
                    if (!success || tripId.isEmpty() || !sessionId.equals(responseSession) || responseContract < CONTRACT_VERSION) {
                        IN_FLIGHT.remove(sessionId);
                        scheduleRetry(retry, sessionId, null);
                        String message = "Resposta do backend incompatível com o contrato FIX18. Registro local preservado.";
                        if (sessionId.equals(mainPrefs.getString("gtoTripSessionId", ""))) markPending(mainPrefs, message);
                        if (listener != null) listener.onPending(sessionId, message);
                        return;
                    }

                    IN_FLIGHT.remove(sessionId);
                    boolean currentSession = sessionId.equals(mainPrefs.getString("gtoTripSessionId", ""));
                    int responseProgress = response == null ? 0 : safeInt(response.get("progress"));
                    String responseJobStatus = response == null ? "" : clean(String.valueOf(
                        response.get("jobStatus") == null ? "" : response.get("jobStatus")
                    ));
                    String payloadJobId = clean(payload.optString("jobId", ""));
                    boolean backendJobClosed = isClosedJobStatus(responseJobStatus);

                    // R3.11 durability rule: for the active trip, persist the exact backend
                    // ACK before deleting the only durable queue/snapshot copies. If local
                    // ACK persistence fails (disk pressure/OEM I/O failure), keep both
                    // copies and retry the idempotent callable instead of risking a trip
                    // that exists on the server but looks permanently pending locally.
                    if (currentSession) {
                        boolean ackPersisted = mainPrefs.edit()
                            .putString("gtoTripSyncStatus", STATUS_SYNCED)
                            .putString("gtoTripIntegrityStatus", "ACKNOWLEDGED")
                            .remove("gtoTripSyncLastErrorCode")
                            .putString("gtoRegisteredTripId", tripId)
                            .putInt("gtoJobProgress", responseProgress)
                            .putString("gtoJobStatus", responseJobStatus)
                            .putString("gtoBackendJobId", payloadJobId)
                            .putBoolean("gtoBackendJobClosed", backendJobClosed)
                            .putLong("gtoBackendJobStatusAt", System.currentTimeMillis())
                            .remove("gtoTripSyncError")
                            .remove("gtoTripIntegrityError")
                            .remove("gtoTripQueueCleanupPending")
                            .putString("lastEvent", backendJobClosed
                                ? "Viagem registrada · operação concluída no NVU"
                                : "Viagem registrada automaticamente no NVU")
                            .commit();
                        if (!ackPersisted) {
                            scheduleRetry(retry, sessionId, null);
                            String message = "Backend confirmou, mas o ACK local não pôde ser persistido; fila e snapshot foram preservados para retry idempotente.";
                            markPending(mainPrefs, message);
                            if (listener != null) listener.onPending(sessionId, message);
                            return;
                        }
                    }

                    if (!queue.edit().remove(key).commit()) {
                        scheduleRetry(retry, sessionId, null);
                        String message = "Backend e ACK local confirmados, mas a limpeza da fila ficou pendente; retry idempotente preservado.";
                        if (currentSession) {
                            mainPrefs.edit()
                                .putBoolean("gtoTripQueueCleanupPending", true)
                                .putString("gtoTripSyncLastErrorCode", "LOCAL_QUEUE_CLEANUP")
                                .apply();
                            // The trip is already durably ACKed and may safely release the
                            // driver for the next trip. The old queue entry is idempotent.
                            if (listener != null) listener.onSynced(sessionId, tripId);
                        } else if (listener != null) {
                            listener.onPending(sessionId, message);
                        }
                        return;
                    }
                    retry.edit()
                        .remove(RETRY_AT_PREFIX + sessionId)
                        .remove(RETRY_COUNT_PREFIX + sessionId)
                        .commit();
                    removeSnapshot(context, sessionId);
                    if (currentSession) {
                        mainPrefs.edit()
                            .remove("gtoTripQueueCleanupPending")
                            .remove("gtoTripSyncLastErrorCode")
                            .apply();
                    }
                    if (listener != null) listener.onSynced(sessionId, tripId);
                })
                .addOnFailureListener(error -> {
                    IN_FLIGHT.remove(sessionId);
                    FirebaseFunctionsException.Code code = null;
                    if (error instanceof FirebaseFunctionsException) {
                        code = ((FirebaseFunctionsException) error).getCode();
                    }
                    scheduleRetry(retry, sessionId, code);

                    String message = clean(error.getMessage());
                    if (message.isEmpty()) message = "Sem conexão com o servidor";
                    String codeLabel = code == null ? "" : " [" + code.name() + "]";
                    String retryMessage = message + codeLabel + ". Registro preservado; nova tentativa automática.";
                    if (sessionId.equals(mainPrefs.getString("gtoTripSessionId", ""))) {
                        mainPrefs.edit()
                            .putString("gtoTripSyncLastErrorCode", code == null ? "CALL_FAILED" : code.name())
                            .putLong("gtoTripSyncLastAttemptAt", System.currentTimeMillis())
                            .apply();
                        markPending(mainPrefs, retryMessage);
                    }
                    if (listener != null) listener.onPending(sessionId, retryMessage);
                });
        }
    }

    private static JSONObject buildPayload(Context context, SharedPreferences prefs, String sessionId) {
        SharedPreferences snapshots = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE);
        JSONObject snapshot = readJson(snapshots.getString(SNAPSHOT_PREFIX + sessionId, ""));

        // FIX17 recovery: if the process completed a trip before FIX18 snapshotting,
        // reconstruct once from the still-preserved main preferences.
        if (snapshot == null) {
            if (!beginSessionSnapshot(context, prefs, sessionId)) return null;
            if (!lockSelectedFreight(context, prefs)) return null;
            snapshot = readJson(snapshots.getString(SNAPSHOT_PREFIX + sessionId, ""));
        }
        if (snapshot == null || !snapshot.optBoolean("freightLocked", false)) return null;

        String completionStatus = clean(prefs.getString("completionStatus", ""));
        String finalGain = clean(prefs.getString("finalGain", prefs.getString("resultValue", "")));
        long completedAt = prefs.getLong("completionDetectedAt", 0L);

        try {
            JSONObject payload = new JSONObject();
            payload.put("contractVersion", CONTRACT_VERSION);
            for (String field : new String[] {
                "sessionId", "driverId", "companyId", "jobId", "contractId", "vehicleId", "trailerId",
                "driverName", "companyName", "contractName", "vehicleName", "trailerName", "cargo",
                "originCompany", "destinationCompany", "destination", "distanceKm", "offeredValue"
            }) {
                payload.put(field, clean(snapshot.optString(field, "")));
            }
            payload.put("finalValue", finalGain);
            payload.put("completionStatus", completionStatus);
            payload.put("completedAtClient", completedAt > 0L ? completedAt : System.currentTimeMillis());

            // Compatibility aliases retained only in transport. Canonical FIX18 validation
            // and fingerprinting use the fields above.
            payload.put("origin", payload.optString("originCompany", ""));
            payload.put("origem", payload.optString("originCompany", ""));
            payload.put("destino", payload.optString("destination", ""));
            payload.put("km", payload.optString("distanceKm", ""));
            payload.put("ganhos", payload.optString("offeredValue", ""));
            payload.put("finalGain", finalGain);
            payload.put("earnings", finalGain);
            payload.put("valor", finalGain);
            return payload;
        } catch (JSONException error) {
            return null;
        }
    }

    private static void copyContextFromPrefs(JSONObject target, SharedPreferences prefs) throws JSONException {
        for (String field : new String[] {
            "driverId", "companyId", "jobId", "contractId", "vehicleId", "trailerId",
            "driverName", "companyName", "contractName", "vehicleName", "trailerName"
        }) {
            target.put(field, clean(prefs.getString(field, "")));
        }
    }

    private static String validateContextSnapshot(JSONObject snapshot) {
        if (!isSessionId(snapshot.optString("sessionId", ""))) return "Sessão GTO inválida.";
        for (String field : new String[] {"driverId", "companyId", "jobId", "contractId"}) {
            String value = clean(snapshot.optString(field, ""));
            if (value.isEmpty()) return "Contexto da operação incompleto: " + field + ".";
            if (value.length() > 220 || value.contains("/")) return "Identificador inválido no contexto GTO: " + field + ".";
        }
        return null;
    }

    private static String validateFreight(JSONObject payload) {
        for (String field : new String[] {"cargo", "originCompany", "destination"}) {
            String value = clean(payload.optString(field, ""));
            if (value.length() < 2 || value.length() > 220) return "Dado de frete inválido: " + field + ".";
        }
        Double km = parsePositiveNumber(payload.optString("distanceKm", ""));
        if (km == null || km <= 0 || km > 50_000d) return "Distância do frete inválida.";
        String offered = clean(payload.optString("offeredValue", ""));
        if (!offered.isEmpty()) {
            Double offeredNumber = parsePositiveNumber(offered);
            if (offeredNumber == null || offeredNumber <= 0 || offeredNumber > 1_000_000_000d) return "Valor ofertado do frete inválido.";
        }
        return null;
    }

    private static String validateCompletedPayload(JSONObject payload) {
        if (safeInt(payload.opt("contractVersion")) < CONTRACT_VERSION) {
            // Legacy FIX17 queue entries are upgraded in-memory before validation.
            try { payload.put("contractVersion", CONTRACT_VERSION); } catch (JSONException ignored) {}
        }
        String contextIssue = validateContextSnapshot(payload);
        if (contextIssue != null) return contextIssue;
        String freightIssue = validateFreight(payload);
        if (freightIssue != null) return freightIssue;
        if (!"CONFIRMED_NORMAL".equals(clean(payload.optString("completionStatus", "")))) {
            return "Conclusão GTO não confirmada como normal.";
        }
        Double finalValue = parsePositiveNumber(payload.optString("finalValue", ""));
        if (finalValue == null || finalValue <= 0 || finalValue > 1_000_000_000d) return "Valor final da entrega inválido.";
        long completedAt = payload.optLong("completedAtClient", 0L);
        long now = System.currentTimeMillis();
        if (completedAt <= 0L || completedAt > now + 24L * 60L * 60L * 1000L) return "Timestamp de conclusão inválido.";
        return null;
    }

    private static boolean sameFreight(JSONObject locked, JSONObject candidate) {
        for (String field : new String[] {"cargo", "originCompany", "destinationCompany", "destination", "distanceKm", "offeredValue"}) {
            if (!clean(locked.optString(field, "")).equals(clean(candidate.optString(field, "")))) return false;
        }
        return true;
    }

    private static String sealPayload(JSONObject payload) {
        try {
            JSONObject envelope = new JSONObject();
            envelope.put("format", "NVU_GTO_QUEUE_V2");
            envelope.put("contractVersion", CONTRACT_VERSION);
            envelope.put("persistedAt", System.currentTimeMillis());
            envelope.put("checksum", checksum(payload));
            envelope.put("payload", payload);
            return envelope.toString();
        } catch (JSONException error) {
            return "";
        }
    }

    private static QueueRecord readQueueRecord(String raw) {
        JSONObject object = readJson(raw);
        if (object == null) return null;
        JSONObject nested = object.optJSONObject("payload");
        if (nested == null) {
            // Raw FIX17 payload.
            return new QueueRecord(object, true);
        }
        String expected = clean(object.optString("checksum", ""));
        if (expected.isEmpty() || !expected.equals(checksum(nested))) return null;
        return new QueueRecord(nested, false);
    }

    private static String checksum(JSONObject payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = canonicalHashInput(payload).getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception error) {
            return "";
        }
    }

    private static String canonicalHashInput(JSONObject payload) {
        StringBuilder out = new StringBuilder();
        for (String field : HASH_FIELDS) {
            String value;
            if ("completedAtClient".equals(field) || "contractVersion".equals(field)) {
                value = String.valueOf(payload.optLong(field, 0L));
            } else {
                value = clean(payload.optString(field, ""));
            }
            out.append(field.length()).append(':').append(field)
                .append('=').append(value.length()).append(':').append(value).append('|');
        }
        return out.toString();
    }

    private static void quarantine(Context context, SharedPreferences queue, String queueKey, String sessionId, String raw, String reason) {
        SharedPreferences quarantine = context.getSharedPreferences(QUARANTINE_PREFS, Context.MODE_PRIVATE);
        JSONObject record = new JSONObject();
        try {
            record.put("sessionId", sessionId);
            record.put("reason", reason);
            record.put("raw", raw == null ? "" : raw);
            record.put("quarantinedAt", System.currentTimeMillis());
        } catch (JSONException ignored) {}
        boolean saved = quarantine.edit()
            .putString(QUARANTINE_PREFIX + sessionId + "_" + System.currentTimeMillis(), record.toString())
            .commit();
        if (saved) queue.edit().remove(queueKey).commit();
    }

    private static void notifyIntegrityProblem(SharedPreferences prefs, Listener listener, String sessionId, String message) {
        if (sessionId.equals(prefs.getString("gtoTripSessionId", ""))) {
            markPending(prefs, message);
            markIntegrityError(prefs, message);
        }
        if (listener != null) listener.onPending(sessionId, message);
    }

    static void discardSessionSnapshot(Context context, String sessionId) {
        removeSnapshot(context, clean(sessionId));
    }

    private static void removeSnapshot(Context context, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            .edit().remove(SNAPSHOT_PREFIX + sessionId).commit();
    }

    private static void scheduleRetry(SharedPreferences retry, String sessionId, FirebaseFunctionsException.Code code) {
        int attempt = retry.getInt(RETRY_COUNT_PREFIX + sessionId, 0) + 1;
        boolean validationFailure = code == FirebaseFunctionsException.Code.INVALID_ARGUMENT
            || code == FirebaseFunctionsException.Code.FAILED_PRECONDITION
            || code == FirebaseFunctionsException.Code.PERMISSION_DENIED
            || code == FirebaseFunctionsException.Code.UNAUTHENTICATED;
        long delayMs = validationFailure
            ? MAX_RETRY_MS
            : Math.min(MAX_RETRY_MS, BASE_RETRY_MS * (1L << Math.min(attempt - 1, 4)));
        retry.edit()
            .putInt(RETRY_COUNT_PREFIX + sessionId, attempt)
            .putLong(RETRY_AT_PREFIX + sessionId, System.currentTimeMillis() + delayMs)
            .commit();
    }

    private static boolean isClosedJobStatus(String value) {
        String normalized = clean(value).toLowerCase(java.util.Locale.ROOT);
        return "awaiting_completion".equals(normalized)
            || "completed".equals(normalized)
            || "cancelled".equals(normalized)
            || "canceled".equals(normalized);
    }

    private static void markPending(SharedPreferences prefs, String message) {
        prefs.edit()
            .putString("gtoTripSyncStatus", STATUS_PENDING)
            .putString("gtoTripSyncError", message)
            .apply();
    }

    private static void markIntegrityError(SharedPreferences prefs, String message) {
        prefs.edit()
            .putString("gtoTripIntegrityStatus", "ERROR")
            .putString("gtoTripIntegrityError", message)
            .apply();
    }

    private static Map<String, Object> toMap(JSONObject object) {
        Map<String, Object> map = new HashMap<>();
        for (java.util.Iterator<String> iterator = object.keys(); iterator.hasNext();) {
            String key = iterator.next();
            Object value = object.opt(key);
            if (value == JSONObject.NULL) value = null;
            map.put(key, value);
        }
        return map;
    }

    private static JSONObject readJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (JSONException error) {
            return null;
        }
    }

    private static int safeInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(clean(value == null ? "" : String.valueOf(value))); }
        catch (Exception ignored) { return 0; }
    }

    private static boolean isSessionId(String value) {
        String sessionId = clean(value);
        return sessionId.length() >= 8 && sessionId.length() <= 160 && sessionId.matches("[A-Za-z0-9_-]+");
    }

    private static Double parsePositiveNumber(String value) {
        String token = clean(value).replaceAll("\\s+", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d[\\d.,]*").matcher(token);
        if (!matcher.find()) return null;
        token = matcher.group();
        int comma = token.lastIndexOf(',');
        int dot = token.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) token = token.replace(".", "").replace(',', '.');
            else token = token.replace(",", "");
        } else if (comma >= 0) {
            int decimals = token.length() - comma - 1;
            token = decimals > 0 && decimals <= 2 ? token.replace(".", "").replace(',', '.') : token.replace(",", "");
        } else if (dot >= 0 && token.length() - dot - 1 == 3) {
            token = token.replace(".", "");
        }
        try {
            double parsed = Double.parseDouble(token);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
