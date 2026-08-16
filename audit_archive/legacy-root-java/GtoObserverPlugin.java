package com.nvu.operacional;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.io.File;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "GtoObserver")
public class GtoObserverPlugin extends Plugin {

    @PluginMethod
    public void getStatus(PluginCall call) {
        call.resolve(buildStatus());
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        try {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getContext().getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve(buildStatus());
        } catch (Exception ex) {
            call.reject("Não foi possível abrir a permissão de sobreposição.", ex);
        }
    }

    @PluginMethod
    public void openUsageAccessSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve(buildStatus());
        } catch (Exception ex) {
            call.reject("Não foi possível abrir a permissão de acesso de uso.", ex);
        }
    }

    @PluginMethod
    public void openPreciseTouchSettings(PluginCall call) {
        // FIX9 no longer uses AccessibilityService. Kept for remote-web backwards compatibility.
        call.resolve(buildStatus());
    }

    @PluginMethod
    public void startObserver(PluginCall call) {
        JSObject status = buildStatus();
        if (!Boolean.TRUE.equals(status.getBool("overlayPermission"))) {
            status.put("started", false);
            status.put("missingPermission", "overlay");
            call.resolve(status);
            return;
        }
        if (!Boolean.TRUE.equals(status.getBool("usageAccess"))) {
            status.put("started", false);
            status.put("missingPermission", "usage");
            call.resolve(status);
            return;
        }
        try {
            getContext().getSharedPreferences(GtoObserverService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove("startError")
                .apply();
            Intent intent = new Intent(getContext(), GtoObserverService.class)
                .setAction(GtoObserverService.ACTION_START);
            ContextCompat.startForegroundService(getContext(), intent);
            // Older/slower devices can take well over 320 ms to create a foreground
            // service after a cold start. Poll for up to ~2.2 s instead of reporting a
            // false failure while the service is actually starting.
            resolveObserverStart(call, 0);
        } catch (Exception ex) {
            String detail = GtoObserverService.describeError(ex);
            getContext().getSharedPreferences(GtoObserverService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("startError", detail)
                .apply();
            JSObject failed = buildStatus();
            failed.put("started", false);
            failed.put("startError", detail);
            call.resolve(failed);
        }
    }


    @PluginMethod
    public void recoverObserver(PluginCall call) {
        boolean requested = GtoObserverService.recoverIfEnabled(getContext());
        if (!requested && !GtoObserverService.isRunning()) {
            call.resolve(buildStatus());
            return;
        }
        resolveObserverRecovery(call, 0);
    }

    @PluginMethod
    public void stopObserver(PluginCall call) {
        try {
            Intent intent = new Intent(getContext(), GtoObserverService.class)
                .setAction(GtoObserverService.ACTION_STOP);
            getContext().startService(intent);
            JSObject status = buildStatus();
            status.put("stopping", true);
            call.resolve(status);
        } catch (Exception ex) {
            call.reject("Não foi possível parar o observador GTO.", ex);
        }
    }

    @PluginMethod
    public void requestScreenCapture(PluginCall call) {
        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            GtoObserverService.PREFS_NAME,
            Context.MODE_PRIVATE
        );
        JSObject current = buildStatus();
        if (Boolean.TRUE.equals(current.getBool("projectionActive"))) {
            call.resolve(current);
            return;
        }
        if (!GtoObserverService.isRunning()) {
            current.put("projectionStatus", "OBSERVER_NOT_RUNNING");
            current.put("projectionError", "O Observador GTO precisa estar ativo antes da leitura da tela.");
            call.resolve(current);
            return;
        }

        try {
            MediaProjectionManager manager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                String detail = "MediaProjectionManager indisponível";
                GtoObserverService.reportProjectionPermissionTerminalFailure(
                    context, "MANAGER_UNAVAILABLE_APP", detail
                );
                call.resolve(buildStatus());
                return;
            }

            Intent captureIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Initial work launch asks for whole-display capture while NVU itself is
                // visible. The GTO is opened only after this permission has been granted.
                captureIntent = manager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay()
                );
            } else {
                captureIntent = manager.createScreenCaptureIntent();
            }

            prefs.edit()
                .putBoolean("projectionPermissionInFlight", true)
                .putString("projectionStatus", "REQUESTING_PERMISSION_APP")
                .remove("projectionError")
                .putString("lastEvent", "Aguardando autorização de leitura antes de abrir o GTO")
                .apply();
            GtoObserverService.markProjectionPermissionInFlightIfRunning();
            startActivityForResult(call, captureIntent, "screenCaptureResult");
        } catch (Exception ex) {
            String detail = GtoObserverService.describeError(ex);
            GtoObserverService.reportProjectionPermissionTerminalFailure(
                context, "CONSENT_LAUNCH_FAILED_APP", detail
            );
            call.resolve(buildStatus());
        }
    }

    @ActivityCallback
    private void screenCaptureResult(PluginCall call, ActivityResult result) {
        if (call == null) return;

        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            GtoObserverService.PREFS_NAME,
            Context.MODE_PRIVATE
        );
        Intent data = result.getData();
        boolean granted = result.getResultCode() == Activity.RESULT_OK && data != null;

        prefs.edit()
            .putBoolean("projectionPermissionInFlight", false)
            .putString("projectionStatus", granted ? "CONSENT_GRANTED_APP" : "DENIED")
            .putString("lastEvent", granted
                ? "Autorização de leitura concedida antes de abrir o GTO"
                : "Autorização de leitura recusada")
            .apply();

        Intent serviceIntent = new Intent(context, GtoObserverService.class);
        if (granted) {
            serviceIntent.setAction(GtoObserverService.ACTION_START_PROJECTION);
            serviceIntent.putExtra(GtoObserverService.EXTRA_RESULT_CODE, result.getResultCode());
            serviceIntent.putExtra(GtoObserverService.EXTRA_RESULT_DATA, data);
        } else {
            serviceIntent.setAction(GtoObserverService.ACTION_PROJECTION_DENIED);
        }

        try {
            if (GtoObserverService.isRunning()) {
                context.startService(serviceIntent);
            } else {
                ContextCompat.startForegroundService(context, serviceIntent);
            }
        } catch (Exception ex) {
            GtoObserverService.reportProjectionPermissionTerminalFailure(
                context, "SERVICE_DISPATCH_FAILED_APP", GtoObserverService.describeError(ex)
            );
            call.resolve(buildStatus());
            return;
        }

        if (!granted) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> call.resolve(buildStatus()), 120L);
            return;
        }
        resolveProjectionStart(call, 0);
    }

    private void resolveProjectionStart(PluginCall call, int attempt) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            JSObject status = buildStatus();
            boolean active = Boolean.TRUE.equals(status.getBool("projectionActive"));
            String projectionStatus = status.optString("projectionStatus", "");
            boolean terminal = "START_FAILED".equals(projectionStatus)
                || "DENIED".equals(projectionStatus)
                || projectionStatus.startsWith("PERMISSION_")
                || projectionStatus.startsWith("SERVICE_DISPATCH_FAILED");
            if (active || terminal || attempt >= 55) {
                if (!active && attempt >= 55 && status.optString("projectionError", "").isEmpty()) {
                    status.put("projectionStatus", "START_TIMEOUT");
                    status.put("projectionError", "O Android não confirmou a leitura da tela a tempo.");
                }
                call.resolve(status);
                return;
            }
            resolveProjectionStart(call, attempt + 1);
        }, attempt == 0 ? 120L : 140L);
    }

    @PluginMethod
    public void setContext(PluginCall call) {
        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            GtoObserverService.PREFS_NAME,
            Context.MODE_PRIVATE
        );
        String previousJobId = prefs.getString("jobId", "");
        String nextJobId = safe(call.getString("jobId"));
        Integer jobProgress = call.getInt("jobProgress");
        Integer jobTotalDeliveries = call.getInt("jobTotalDeliveries");

        android.content.SharedPreferences.Editor editor = prefs.edit()
            .putString("driverId", safe(call.getString("driverId")))
            .putString("driverName", safe(call.getString("driverName")))
            .putString("companyId", safe(call.getString("companyId")))
            .putString("companyName", safe(call.getString("companyName")))
            .putString("jobId", nextJobId)
            .putString("jobStatus", safe(call.getString("jobStatus")))
            .putInt("jobProgress", jobProgress == null ? 0 : Math.max(0, jobProgress))
            .putInt("jobTotalDeliveries", jobTotalDeliveries == null ? 0 : Math.max(0, jobTotalDeliveries))
            .putString("contractId", safe(call.getString("contractId")))
            .putString("contractName", safe(call.getString("contractName")))
            .putString("vehicleId", safe(call.getString("vehicleId")))
            .putString("vehicleName", safe(call.getString("vehicleName")))
            .putString("trailerId", safe(call.getString("trailerId")))
            .putString("trailerName", safe(call.getString("trailerName")));

        // A backend completion lock belongs to one job only. Switching operation must
        // never leak the previous job's closed state into the new one.
        if (!nextJobId.equals(previousJobId)) {
            editor
                .remove("gtoJobProgress")
                .remove("gtoJobStatus")
                .remove("gtoBackendJobId")
                .remove("gtoBackendJobClosed")
                .remove("gtoBackendJobStatusAt");
        }
        editor.apply();
        call.resolve(buildStatus());
    }

    @PluginMethod
    public void logoutCleanup(PluginCall call) {
        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            GtoObserverService.PREFS_NAME,
            Context.MODE_PRIVATE
        );
        String sessionId = prefs.getString("gtoTripSessionId", "");
        String state = prefs.getString("tripState", GtoObserverService.STATE_IDLE);
        String completion = prefs.getString("completionStatus", "");
        String syncStatus = prefs.getString("gtoTripSyncStatus", "");
        String resultSnapshotPath = prefs.getString("resultSnapshotPath", "");
        String logoutCleanupError = "";

        // Result frames are strictly local runtime artifacts. They must never survive a
        // logout/account switch, even if Android kills the process immediately afterwards.
        if (!resultSnapshotPath.isEmpty()) {
            try {
                File snapshot = new File(resultSnapshotPath);
                if (snapshot.exists() && !snapshot.delete()) {
                    logoutCleanupError = "Não foi possível remover o quadro local temporário do resultado.";
                }
            } catch (Exception ex) {
                logoutCleanupError = "Falha ao remover quadro local temporário: " + GtoObserverService.describeError(ex);
            }
        }

        // Completed deliveries remain in the durable queue across logout. An unfinished
        // trip must not be allowed to continue under the next account on a shared device.
        if (GtoObserverService.STATE_RESULT_CONFIRMED.equals(state)
            && "CONFIRMED_NORMAL".equals(completion)
            && !GtoAutoTripSync.STATUS_SYNCED.equals(syncStatus)) {
            GtoAutoTripSync.enqueueConfirmedTrip(context, prefs, null);
        } else {
            GtoAutoTripSync.discardSessionSnapshot(context, sessionId);
        }

        boolean cleanupCommit = prefs.edit()
            .putBoolean("enabled", false)
            .putBoolean("gtoForeground", false)
            .putBoolean("overlayVisible", false)
            .putString("tripState", GtoObserverService.STATE_IDLE)
            .putString("lastEvent", "Sessão GTO encerrada no logout")
            .remove("driverId")
            .remove("driverName")
            .remove("companyId")
            .remove("companyName")
            .remove("jobId")
            .remove("jobStatus")
            .remove("jobProgress")
            .remove("jobTotalDeliveries")
            .remove("contractId")
            .remove("contractName")
            .remove("vehicleId")
            .remove("vehicleName")
            .remove("trailerId")
            .remove("trailerName")
            .remove("selectedFreight")
            .remove("selectedFreightSummary")
            .remove("selectedDestination")
            .remove("selectedOriginCompany")
            .remove("selectedDestinationCompany")
            .remove("selectedCargo")
            .remove("selectedKm")
            .remove("selectedValue")
            .remove("resultValue")
            .remove("finalGain")
            .remove("completionStatus")
            .remove("completionDetectedAt")
            .remove("gtoTripSessionId")
            .remove("gtoTripSessionStartedAt")
            .remove("gtoTripSyncStatus")
            .remove("gtoRegisteredTripId")
            .remove("gtoTripSyncError")
            .remove("gtoTripIntegrityStatus")
            .remove("gtoTripIntegrityError")
            .remove("gtoJobProgress")
            .remove("gtoJobStatus")
            .remove("gtoBackendJobId")
            .remove("gtoBackendJobClosed")
            .remove("gtoBackendJobStatusAt")
            .remove("driverStageCode")
            .remove("driverStageMessage")
            .remove("driverStageAt")
            .remove("driverStageShownKey")
            .remove("resultAction")
            .remove("resultActionTouchAt")
            .remove("resultReceiveLatched")
            .remove("resultActionSource")
            .remove("resultTouchFallbackRequired")
            .remove("resultTouchFallbackReady")
            .remove("resultTouchFallbackContinuityBroken")
            .remove("resultTouchFallbackReason")
            .remove("resultSnapshotPath")
            .remove("resultSnapshotAt")
            .remove("resultSnapshotError")
            .remove("resultSnapshotErrorAt")
            .remove("projectionPermissionInFlight")
            .remove("logoutCleanupError")
            .commit();

        boolean cleanupPersisted = cleanupCommit
            && !prefs.getBoolean("enabled", true)
            && prefs.getString("driverId", "").isEmpty()
            && prefs.getString("gtoTripSessionId", "").isEmpty();
        if (!cleanupPersisted) {
            logoutCleanupError = "O Android não confirmou a limpeza persistente da sessão GTO.";
        }

        // Do not start a stopped foreground service just to stop it again during logout.
        // If it is alive, terminate it immediately; if it is not, enabled=false above is
        // sufficient to prevent MainActivity from recovering it for the next account.
        if (GtoObserverService.isRunning()) {
            try {
                Intent intent = new Intent(context, GtoObserverService.class)
                    .setAction(GtoObserverService.ACTION_STOP);
                context.startService(intent);
            } catch (Exception ex) {
                try {
                    context.stopService(new Intent(context, GtoObserverService.class));
                } catch (Exception fallbackEx) {
                    logoutCleanupError = "Falha ao encerrar serviço GTO no logout: "
                        + GtoObserverService.describeError(fallbackEx);
                }
            }
        }

        if (!logoutCleanupError.isEmpty()) {
            prefs.edit().putString("logoutCleanupError", logoutCleanupError).commit();
        }

        JSObject status = buildStatus();
        status.put("logoutCleaned", cleanupPersisted && logoutCleanupError.isEmpty());
        call.resolve(status);
    }

    @PluginMethod
    public void openGto(PluginCall call) {
        try {
            Intent launchIntent = getContext().getPackageManager()
                .getLaunchIntentForPackage(GtoObserverService.GTO_PACKAGE);
            if (launchIntent == null) {
                JSObject result = new JSObject();
                result.put("opened", false);
                result.put("installed", false);
                call.resolve(result);
                return;
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            getContext().startActivity(launchIntent);
            JSObject result = new JSObject();
            result.put("opened", true);
            result.put("installed", true);
            call.resolve(result);
        } catch (Exception ex) {
            call.reject("Não foi possível abrir o Global Truck Online.", ex);
        }
    }

    private void resolveObserverStart(PluginCall call, int attempt) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            JSObject confirmed = buildStatus();
            boolean started = GtoObserverService.isRunning()
                && confirmed.optLong("serviceHeartbeatAt", 0L) > 0L
                && System.currentTimeMillis() - confirmed.optLong("serviceHeartbeatAt", 0L) < 5000L;
            if (started || attempt >= 24) {
                confirmed.put("started", started);
                if (!started && confirmed.optString("startError", "").isEmpty()) {
                    confirmed.put("startError", "O Android não confirmou a inicialização do serviço GTO.");
                }
                call.resolve(confirmed);
                return;
            }
            resolveObserverStart(call, attempt + 1);
        }, attempt == 0 ? 180L : 200L);
    }

    private void resolveObserverRecovery(PluginCall call, int attempt) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            JSObject status = buildStatus();
            if (Boolean.TRUE.equals(status.getBool("observerHealthy")) || attempt >= 20) {
                call.resolve(status);
                return;
            }
            resolveObserverRecovery(call, attempt + 1);
        }, attempt == 0 ? 160L : 200L);
    }

    private JSObject buildStatus() {
        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            GtoObserverService.PREFS_NAME,
            Context.MODE_PRIVATE
        );

        JSObject status = new JSObject();
        status.put("overlayPermission", Settings.canDrawOverlays(context));
        status.put("usageAccess", hasUsageStatsAccess(context));
        status.put("preciseTouchPermission", true);
        status.put("preciseTouchActive", false);
        status.put("running", GtoObserverService.isRunning());
        status.put("enabled", prefs.getBoolean("enabled", false));
        long heartbeatAt = prefs.getLong("serviceHeartbeatAt", 0L);
        boolean healthy = GtoObserverService.isRunning()
            && heartbeatAt > 0L
            && System.currentTimeMillis() - heartbeatAt < 5000L;
        status.put("observerHealthy", healthy);
        status.put("serviceStartedAt", prefs.getLong("serviceStartedAt", 0L));
        status.put("serviceHeartbeatAt", heartbeatAt);
        status.put("overlayVisible", prefs.getBoolean("overlayVisible", false));
        status.put("overlayError", prefs.getString("overlayError", ""));
        status.put("overlayErrorAt", prefs.getLong("overlayErrorAt", 0L));
        status.put("overlayFailureCount", prefs.getInt("overlayFailureCount", 0));
        status.put("menuOverlayError", prefs.getString("menuOverlayError", ""));
        status.put("menuOverlayErrorAt", prefs.getLong("menuOverlayErrorAt", 0L));
        status.put("statusOverlayError", prefs.getString("statusOverlayError", ""));
        status.put("statusOverlayErrorAt", prefs.getLong("statusOverlayErrorAt", 0L));
        status.put("notificationError", prefs.getString("notificationError", ""));
        status.put("notificationErrorAt", prefs.getLong("notificationErrorAt", 0L));
        status.put("touchPulseSensorVisible", prefs.getBoolean("touchPulseSensorVisible", false));
        status.put("touchPulseSensorError", prefs.getString("touchPulseSensorError", ""));
        status.put("touchPulseSensorErrorAt", prefs.getLong("touchPulseSensorErrorAt", 0L));
        status.put("captureWidth", prefs.getInt("captureWidth", 0));
        status.put("captureHeight", prefs.getInt("captureHeight", 0));
        status.put("captureDensityDpi", prefs.getInt("captureDensityDpi", 0));
        status.put("captureAndroidApi", prefs.getInt("captureAndroidApi", Build.VERSION.SDK_INT));
        status.put("freightButtonBandLeft", prefs.getInt("freightButtonBandLeft", 0));
        status.put("freightButtonBandRight", prefs.getInt("freightButtonBandRight", 0));
        status.put("freightDetectedButtonCount", prefs.getInt("freightDetectedButtonCount", 0));
        status.put("lastFreightConflict", prefs.getString("lastFreightConflict", ""));
        status.put("lastFreightConflictAt", prefs.getLong("lastFreightConflictAt", 0L));
        status.put("foregroundPackage", prefs.getString("foregroundPackage", ""));
        status.put("startError", prefs.getString("startError", ""));
        status.put("projectionActive", prefs.getBoolean("projectionActive", false));
        status.put("projectionStatus", prefs.getString("projectionStatus", "INACTIVE"));
        status.put("projectionError", prefs.getString("projectionError", ""));
        status.put("projectionReauthRequired", prefs.getBoolean("projectionReauthRequired", false));
        status.put("projectionPermissionInFlight", prefs.getBoolean("projectionPermissionInFlight", false));
        status.put("resultTouchFallbackRequired", prefs.getBoolean("resultTouchFallbackRequired", false));
        status.put("resultTouchFallbackReady", prefs.getBoolean("resultTouchFallbackReady", false));
        status.put("resultTouchFallbackContinuityBroken", prefs.getBoolean("resultTouchFallbackContinuityBroken", false));
        status.put("resultSnapshotError", prefs.getString("resultSnapshotError", ""));
        status.put("resultSnapshotErrorAt", prefs.getLong("resultSnapshotErrorAt", 0L));
        status.put("logoutCleanupError", prefs.getString("logoutCleanupError", ""));
        status.put("runtimePermissionError", prefs.getString("runtimePermissionError", ""));
        status.put("runtimePermissionErrorCode", prefs.getString("runtimePermissionErrorCode", ""));
        status.put("gtoForeground", prefs.getBoolean("gtoForeground", false));
        status.put("tripState", prefs.getString("tripState", GtoObserverService.STATE_IDLE));
        status.put("screenState", prefs.getString("screenState", "UNKNOWN"));
        status.put("lastEvent", prefs.getString("lastEvent", ""));
        status.put("driverStageCode", prefs.getString("driverStageCode", ""));
        status.put("driverStageMessage", prefs.getString("driverStageMessage", ""));
        status.put("driverStageAt", prefs.getLong("driverStageAt", 0L));
        status.put("lastCancellationReason", prefs.getString("lastCancellationReason", ""));
        status.put("lastCancelledAt", prefs.getLong("lastCancelledAt", 0L));
        status.put("selectedFreight", prefs.getString("selectedFreight", ""));
        status.put("selectedCargo", prefs.getString("selectedCargo", ""));
        status.put("selectedCompany", prefs.getString("selectedOriginCompany", ""));
        status.put("selectedDestination", prefs.getString("selectedDestination", ""));
        status.put("selectedKm", prefs.getString("selectedKm", ""));
        status.put("selectedValue", prefs.getString("selectedValue", ""));
        status.put("resultValue", prefs.getString("resultValue", ""));
        status.put("finalGain", prefs.getString("finalGain", ""));
        status.put("completionStatus", prefs.getString("completionStatus", ""));
        status.put("completionDetectedAt", prefs.getLong("completionDetectedAt", 0L));
        status.put("gtoTripSessionId", prefs.getString("gtoTripSessionId", ""));
        status.put("gtoTripSyncStatus", prefs.getString("gtoTripSyncStatus", ""));
        status.put("gtoRegisteredTripId", prefs.getString("gtoRegisteredTripId", ""));
        status.put("gtoTripSyncError", prefs.getString("gtoTripSyncError", ""));
        status.put("gtoTripSyncLastAttemptAt", prefs.getLong("gtoTripSyncLastAttemptAt", 0L));
        status.put("gtoTripSyncLastErrorCode", prefs.getString("gtoTripSyncLastErrorCode", ""));
        status.put("gtoTripIntegrityStatus", prefs.getString("gtoTripIntegrityStatus", ""));
        status.put("gtoTripIntegrityError", prefs.getString("gtoTripIntegrityError", ""));
        status.put("jobStatus", prefs.getString("jobStatus", ""));
        status.put("jobProgress", prefs.getInt("jobProgress", 0));
        status.put("jobTotalDeliveries", prefs.getInt("jobTotalDeliveries", 0));
        status.put("gtoJobStatus", prefs.getString("gtoJobStatus", ""));
        status.put("gtoJobProgress", prefs.getInt("gtoJobProgress", 0));
        status.put("gtoBackendJobClosed", prefs.getBoolean("gtoBackendJobClosed", false));
        status.put("gtoContractVersion", GtoAutoTripSync.CONTRACT_VERSION);
        status.put("gtoPackage", GtoObserverService.GTO_PACKAGE);
        return status;
    }

    private static boolean hasUsageStatsAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;

        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName()
            );
        } else {
            mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName()
            );
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }


    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
