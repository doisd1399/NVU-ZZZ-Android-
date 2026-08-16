package com.nvu.operacional;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.WindowMetrics;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GtoObserverService extends Service {
    public static final String GTO_PACKAGE = "com.stargamesapps.gto";
    public static final String PREFS_NAME = "nvu_gto_observer";

    public static final String ACTION_START = "com.nvu.operacional.gto.START";
    public static final String ACTION_STOP = "com.nvu.operacional.gto.STOP";
    public static final String ACTION_START_PROJECTION = "com.nvu.operacional.gto.START_PROJECTION";
    public static final String ACTION_PROJECTION_DENIED = "com.nvu.operacional.gto.PROJECTION_DENIED";
    public static final String EXTRA_RESULT_CODE = "projectionResultCode";
    public static final String EXTRA_RESULT_DATA = "projectionResultData";

    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_WAITING_FREIGHT = "WAITING_FREIGHT";
    public static final String STATE_CONFIRMING_FREIGHT = "CONFIRMING_FREIGHT";
    public static final String STATE_TRIP_IN_PROGRESS = "TRIP_IN_PROGRESS";
    public static final String STATE_RESULT_DETECTED = "RESULT_DETECTED";
    public static final String STATE_AWAITING_BONUS = "AWAITING_BONUS_VALIDATION";
    public static final String STATE_RESULT_CONFIRMED = "RESULT_CONFIRMED";
    public static final String STATE_REJECTED_BONUS = "REJECTED_BONUS";
    public static final String STATE_CANCELLED = "CANCELLED";

    private static final int NOTIFICATION_ID = 4607;
    private static final String CHANNEL_ID = "nvu_gto_observer";
    private static final int MAX_ANALYSIS_WIDTH = 1600;
    private static final int MAX_FREIGHT_ANALYSIS_WIDTH = 1440;
    private static final float FREIGHT_ROI_LEFT = 0.615f;
    private static final int FREIGHT_HISTORY_LIMIT = 5;
    private static final long STRUCTURE_INTERVAL_MS = 24L;
    private static final long SNAPSHOT_INTERVAL_MS = 50L;
    private static final long SELECTION_PROBE_TIMEOUT_MS = 620L;
    private static final int BUTTON_FRAME_HISTORY_LIMIT = 14;
    private static final long MANUAL_FINISH_MIN_DELAY_MS = 180L;
    private static final long MANUAL_FINISH_TIMEOUT_MS = 2200L;
    private static final int MANUAL_FINISH_MAX_ATTEMPTS = 3;
    private static final int AUTO_RESULT_FALLBACK_MISSES = 2;
    private static final long AUTO_RESULT_FALLBACK_WINDOW_MS = 2400L;
    private static final long FOREGROUND_POLL_INTERVAL_MS = 350L;
    // R3.11 low-end hardening: runtime diagnostics are useful, but persisting them on
    // every 350 ms poll/frame queues unnecessary SharedPreferences disk work on slower
    // devices. Detection remains real-time in memory; only diagnostic snapshots are
    // rate-limited.
    private static final long HEARTBEAT_PERSIST_INTERVAL_MS = 1200L;
    private static final long FOREGROUND_STATUS_PERSIST_INTERVAL_MS = 1000L;
    private static final long FREIGHT_RUNTIME_PERSIST_INTERVAL_MS = 300L;
    private static final long FREIGHT_PAGE_OCR_REFRESH_MS = 1800L;
    private static final long PRECISE_OCR_BUSY_RETRY_MS = 80L;
    private static final long PRECISE_OCR_BUSY_WAIT_TIMEOUT_MS = 3500L;
    private static final long GTO_EXIT_GRACE_MS = 1800L;
    private static final long PERMISSION_RETURN_GRACE_MS = 6500L;
    private static final long BUBBLE_TAP_DEBOUNCE_MS = 360L;
    private static final long FAST_SELECTION_CONFIRM_WINDOW_MS = 900L;
    private static final long FAST_SELECTION_FALSE_POSITIVE_TIMEOUT_MS = 950L;
    private static final long FAST_TOUCH_PULSE_WINDOW_MS = 520L;
    private static final int FAST_FRAME_HISTORY_LIMIT = 18;
    private static final long GTO_FRESH_SESSION_RESET_MS = 6000L;
    // A temporary app switch must never erase a valid freight/trip. Sessions are only
    // considered abandoned after a long inactivity window, which also prevents an old
    // unfinished trip from surviving indefinitely into a later game session.
    private static final long ACTIVE_SESSION_STALE_MS = 12L * 60L * 60L * 1000L;
    private static final long CRITICAL_TOUCH_WINDOW_MS = 700L;
    private static final long AUTO_SYNC_RETRY_INTERVAL_MS = 15_000L;
    private static final long BUBBLE_RETRY_INTERVAL_MS = 350L;
    // During a route we keep the expensive OCR asleep. A tiny raw-pixel probe runs a
    // few times per second and only wakes OCR when the fixed GTO completion dialog is
    // visually plausible. A slow fallback pass still exists so a theme/layout change
    // cannot permanently hide a real result screen.
    private static final long ACTIVE_TRIP_VISUAL_PROBE_MS = 180L;
    private static final long ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS = 3200L;
    private static final long ACTIVE_TRIP_RESULT_CANDIDATE_OCR_MS = 420L;
    private static final int ACTIVE_TRIP_FREIGHT_LIST_CONFIRM_FRAMES = 4;
    private static final long ACTIVE_TRIP_FREIGHT_LIST_CONFIRM_MS = 420L;
    // When the driver opens the GTO freight list before pressing the NVU floating
    // button, the observer must still bootstrap the operation from the live list.
    // Keep this gate shorter than the normal stale-session replacement gate so a
    // fast "Aceitar" press is not lost while the APK UI is being opened.
    private static final int UNARMED_FREIGHT_LIST_CONFIRM_FRAMES = 2;
    private static final long UNARMED_FREIGHT_LIST_CONFIRM_MS = 180L;
    // R3.6: an exact touch on Receber is a durable completion event. It has no timeout.
    // Once latched, loading/logo screens and elapsed time cannot invalidate the delivery.
    // Explicit ADS touches remain a separate action and never enter the normal receive path.
    private static final int RESULT_FREIGHT_LIST_CONFIRM_FRAMES = 3;
    private static final long RESULT_FREIGHT_LIST_CONFIRM_MS = 260L;

    private static volatile boolean running = false;
    private static volatile GtoObserverService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean ocrBusy = new AtomicBoolean(false);
    private final AtomicBoolean resultSnapshotRecoveryBusy = new AtomicBoolean(false);
    private long resultSnapshotRecoveryGeneration = 0L;
    private final List<FreightOption> freightOptions = new ArrayList<>();
    private final List<List<FreightOption>> freightHistory = new ArrayList<>();
    private int freightHistoryPage = -1;
    private long freightHistoryUpdatedAt = 0L;

    private SharedPreferences prefs;
    private WindowManager windowManager;
    private UsageStatsManager usageStatsManager;
    private NotificationManager notificationManager;

    private FrameLayout bubbleView;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout menuView;
    private WindowManager.LayoutParams menuParams;
    private TextView statusChipView;
    private WindowManager.LayoutParams statusChipParams;
    // R3.12: compact direct recovery action beside the NVU bubble when Android/OEM
    // stops MediaProjection and the active trip still requires screen reading.
    private TextView projectionReauthButtonView;
    private WindowManager.LayoutParams projectionReauthButtonParams;
    // Independent 1px touch-pulse sensor. It never toggles the NVU bubble. During
    // freight selection it marks the critical touch window; during the result dialog it
    // records that the driver acted so a slow GTO loading transition cannot strand the trip.
    private View freightTouchPulseView;
    private WindowManager.LayoutParams freightTouchPulseParams;
    private long menuOpenedAt = 0L;
    private long lastStateChangeAt = 0L;
    private long lastActiveTripVisualProbeAt = 0L;
    private long lastActiveTripFallbackOcrAt = 0L;
    private long lastResultCandidateOcrAt = 0L;
    private long activeTripFreightListSeenSince = 0L;
    private int activeTripFreightListFrames = 0;
    // R3.8 fail-safe for OEMs that refuse the 1px ACTION_OUTSIDE sensor. The normal
    // path remains fully automatic. This fallback is armed only after a real result
    // screen was OCR-confirmed, and it becomes manually confirmable only after the
    // result screen actually disappears while GTO remains continuously foreground.
    private boolean resultTouchFallbackRequired = false;
    private boolean resultTouchFallbackReady = false;
    private boolean resultTouchFallbackContinuityBroken = false;

    // R3.3: when a stale/in-progress session unexpectedly sees the GTO freight list,
    // pre-arm the selection path before the 4-frame cancellation confirmation finishes.
    // This closes the race where a fast Aceitar tap could happen while the service was
    // still in TRIP_IN_PROGRESS and therefore the normal freight detector was asleep.
    private boolean replacementFreightCandidateArmed = false;
    private long replacementFreightCandidateAt = 0L;
    private GtoFastVisualDetector.Frame replacementFreightBaseline;
    private Bitmap replacementFreightPanelFrame;
    private int replacementFreightPanelOffsetX = 0;
    private final List<Rect> replacementFreightButtons = new ArrayList<>();
    private int replacementFreightPressedRow = -1;
    private float replacementFreightPressedScore = 0f;
    private boolean replacementFreightTouchPending = false;
    private long replacementFreightTouchAt = 0L;

    private String foregroundPackage = "";
    private long lastUsageQueryAt = 0L;
    private long lastGtoForegroundEventAt = 0L;
    private long lastGtoBackgroundEventAt = 0L;
    private boolean gtoForeground = false;
    private long lastGtoForegroundEvidenceAt = 0L;
    private long nonGtoForegroundSince = 0L;
    private long suppressForegroundHideUntil = 0L;
    private boolean projectionPermissionInFlight = false;
    private String lastRuntimePermissionError = "";
    private long lastBubbleTapAt = 0L;
    private long lastBubbleAttemptAt = 0L;
    private long lastAutoSyncRetryAt = 0L;
    private long lastOcrAt = 0L;
    private String lastScreenState = "UNKNOWN";
    private long lastHeartbeatPersistAt = 0L;
    private long lastForegroundStatusPersistAt = 0L;
    private String lastPersistedForegroundPackage = "";
    private long lastPersistedGtoForegroundEventAt = 0L;
    private long lastPersistedGtoBackgroundEventAt = 0L;
    private long lastFreightRuntimePersistAt = 0L;
    private String lastPersistedFreightRuntimeState = "";
    private int lastPersistedFreightCount = -1;

    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection mediaProjection;
    // Every MediaProjection callback is bound to this generation. Re-authorizing capture
    // invalidates callbacks from the previous token so an old onStop() can never release
    // the newly-created ImageReader/VirtualDisplay.
    private long projectionGeneration = 0L;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private TextRecognizer textRecognizer;
    private TextRecognizer selectionTextRecognizer;
    private boolean projectionActive = false;
    private boolean destroying = false;
    private int captureWidth = 0;
    private int captureHeight = 0;
    private int captureDensityDpi = 0;
    private int pendingCapturedWidth = 0;
    private int pendingCapturedHeight = 0;
    private int outsideTouchCount = 0;
    private String projectionStatus = "INACTIVE";
    private float lastOutsideTouchX = -1f;
    private float lastOutsideTouchY = -1f;
    private float lastOutsideAltX = -1f;
    private float lastOutsideAltY = -1f;
    private long lastOutsideTouchAt = 0L;
    private long lastFreightListSeenAt = 0L;
    private long freightListMissingSince = 0L;
    private int freightListMissingFrames = 0;
    // R3.16: explicit freight-list lifecycle. A visually identical list reopened after
    // a failed selection is a new selection attempt and must receive a fresh trip session.
    private boolean freightListCycleSeen = false;
    private boolean freightListCycleClosed = false;
    private boolean freightListReopenPending = false;
    private long freightListCycleClosedAt = 0L;
    private FreightOption pendingFreightSelection;
    private long pendingFreightTouchAt = 0L;
    private String pendingSelectionSource = "";

    // Android 14/15/16 may redact ACTION_OUTSIDE coordinates for overlay windows (0,0).
    // To avoid associating the wrong freight, a short visual burst compares the GTO
    // "Aceitar" buttons before/after the touch and selects only when one row changes
    // distinctly while the other freight buttons are still visible.
    private long visualSelectionUntil = 0L;
    private long lastVisualAnalysisAt = 0L;
    private FreightOption visualFreightSelection;
    private float visualSelectionConfidence = 0f;
    private String visualSelectionSource = "";

    private Rect receiveRect;
    private Rect doubleValueRect;
    private String detectedResultValue = "";
    private long resultScreenLastSeenAt = 0L;
    private long resultActionTouchAt = 0L;
    private long resultExitSeenAt = 0L;
    private int gameplayFramesAfterResult = 0;
    private boolean manualFinishCapturePending = false;
    private long manualFinishRequestedAt = 0L;
    private int manualFinishAttempts = 0;
    private int automaticResultCandidateMisses = 0;
    private final Object freightFrameLock = new Object();
    private final List<Rect> realtimeAcceptRects = new ArrayList<>();
    private Bitmap latestFreightPanelFrame;
    private int latestFreightPanelOffsetX = 0;
    private long latestFreightPanelAt = 0L;
    private long lastStructureAt = 0L;
    private long lastSnapshotAt = 0L;
    private int preciseSelectedRow = -1;
    private long preciseSelectedTouchAt = 0L;
    private boolean preciseSelectionOcrBusy = false;
    // R3.11: async OCR callbacks are session-bound. A late ML Kit callback from an
    // abandoned/cancelled trip must never write freight/result data into the next trip.
    private long preciseSelectionOcrGeneration = 0L;
    private long analysisOcrGeneration = 0L;

    // FIX9: selection is resolved visually from a short frame buffer around the real
    // user touch. ACTION_OUTSIDE is used only as a timestamp trigger; its coordinates
    // are deliberately ignored because Android may redact them as (0,0).
    private final List<ButtonFrameSample> buttonFrameHistory = new ArrayList<>();
    private boolean selectionProbeActive = false;
    private long selectionProbeStartedAt = 0L;
    private ButtonFrameSample selectionProbeBaseline;
    private int selectionProbeBestRow = -1;
    private float selectionProbeBestScore = 0f;
    private float selectionProbeBestMargin = 0f;
    private int selectionProbeEvidenceFrames = 0;
    private int selectionProbeLastEvidenceRow = -1;
    private Bitmap frozenSelectionPanelFrame;
    private int frozenSelectionPanelOffsetX = 0;
    private final List<Rect> frozenSelectionButtons = new ArrayList<>();

    // FIX14: no AccessibilityService. Every freight-list frame is consumed in order
    // (acquireNextImage) by a lightweight visual-only detector. This preserves the
    // sub-frame pressed state that acquireLatestImage could discard on a very fast tap.
    private final GtoFastVisualDetector fastVisualDetector = new GtoFastVisualDetector();
    private final GtoResultVisualGate resultVisualGate = new GtoResultVisualGate();
    private final GtoSelectionCoordinator selectionCoordinator = new GtoSelectionCoordinator();
    private GtoFastVisualDetector.Frame fastPreviousFreightFrame;
    private long fastPreviousFreightSequence = 0L;
    private final List<SequencedFastFrame> fastFrameHistory = new ArrayList<>();
    private GtoFastVisualDetector.Frame fastLastSnapshotFrame;
    private long lastFastPanelSnapshotAt = 0L;
    private long freightPageGeneration = 0L;
    private long lastFreightPageOcrAt = 0L;
    private boolean fastTouchPulseActive = false;
    private long fastTouchPulseAt = 0L;
    private long fastTouchMarkerSequence = -1L;
    private boolean fastTouchMarkerQueued = false;
    private GtoFastVisualDetector.Frame fastTouchBaseline;
    private long fastTouchBaselineSequence = -1L;
    private int fastPendingSelectedRow = -1;
    private long fastPendingSelectedAt = 0L;
    private float fastPendingSelectedScore = 0f;
    private boolean fastPendingFromTouchPulse = false;
    private int fastMissingListFrames = 0;
    private FreightSelectionTransaction pendingSelectionTransaction;

    private final Runnable foregroundPoll = new Runnable() {
        @Override
        public void run() {
            if (!prefs.getBoolean("enabled", false)) return;

            boolean observerPermissionsReady = validateObserverRuntimePermissions();
            if (observerPermissionsReady) refreshForegroundPackage();
            long now = System.currentTimeMillis();
            persistServiceHeartbeatIfDue(now);

            // OEMs can detach an overlay without throwing through our original addView().
            // Treat a detached bubble as absent so the existing retry path can restore it.
            if (bubbleView != null && !bubbleView.isAttachedToWindow()) {
                hideProjectionReauthButton();
                bubbleView = null;
                bubbleParams = null;
                // R3.10: a detached OEM overlay is treated as an immediate recovery event.
                // Reset the throttle so this same foreground poll can recreate the bubble
                // instead of waiting a full retry interval on slower devices.
                lastBubbleAttemptAt = 0L;
                prefs.edit()
                    .putBoolean("overlayVisible", false)
                    .putString("lastEvent", "Botão flutuante foi desconectado pelo Android; restaurando")
                    .apply();
            }

            boolean rawGto = observerPermissionsReady && GTO_PACKAGE.equals(foregroundPackage);

            if (rawGto) {
                long absenceMs = nonGtoForegroundSince > 0L ? now - nonGtoForegroundSince : 0L;
                if (!gtoForeground && absenceMs >= GTO_FRESH_SESSION_RESET_MS) {
                    reconcileSessionAfterGtoReturn(absenceMs);
                }
                lastGtoForegroundEvidenceAt = now;
                nonGtoForegroundSince = 0L;
                if (!gtoForeground) {
                    gtoForeground = true;
                    prefs.edit().putBoolean("gtoForeground", true).apply();
                }
                if (!projectionPermissionInFlight
                    && bubbleView == null
                    && now - lastBubbleAttemptAt >= BUBBLE_RETRY_INTERVAL_MS) {
                    showBubbleIfAllowed();
                }
                updateFreightTouchPulseSensor();
                maybeNotifyProjectionReauthorization();
                updateProjectionReauthButton();
            } else {
                boolean transientSystemUi = projectionPermissionInFlight || now < suppressForegroundHideUntil;
                if (!transientSystemUi && gtoForeground) {
                    if (nonGtoForegroundSince == 0L) nonGtoForegroundSince = now;
                    long exitGrace = menuView != null ? Math.max(GTO_EXIT_GRACE_MS, 4200L) : GTO_EXIT_GRACE_MS;
                    if (now - nonGtoForegroundSince >= exitGrace) {
                        gtoForeground = false;
                        String unresolvedState = getTripState();
                        if ((STATE_RESULT_DETECTED.equals(unresolvedState) || STATE_AWAITING_BONUS.equals(unresolvedState))
                            && (resultTouchFallbackRequired || prefs.getBoolean("resultTouchFallbackRequired", false))
                            && !hasRecentNormalResultActionEvidence(now)) {
                            resultTouchFallbackContinuityBroken = true;
                        }
                        prefs.edit()
                            .putBoolean("gtoForeground", false)
                            .putBoolean("touchCaptureNeeded", false)
                            .putBoolean("resultTouchFallbackContinuityBroken", resultTouchFallbackContinuityBroken)
                            .apply();
                        hideOverlays();
                    }
                }
            }

            if (now - lastAutoSyncRetryAt >= AUTO_SYNC_RETRY_INTERVAL_MS && GtoAutoTripSync.hasPending(GtoObserverService.this)) {
                lastAutoSyncRetryAt = now;
                flushAutomaticTripQueue();
            }

            mainHandler.postDelayed(this, FOREGROUND_POLL_INTERVAL_MS);
        }
    };

    public static boolean isRunning() {
        return running;
    }

    /**
     * Terminal callback used by the isolated MediaProjection permission activity when
     * Android cannot even launch/deliver the consent result. This deliberately does not
     * start a new service: it clears the in-memory permission latch only when the already
     * running observer exists, while preferences retain the diagnostic for the next app
     * start. Without this path a failed permission activity could leave
     * projectionPermissionInFlight=true forever and suppress overlay recovery.
     */
    public static void reportProjectionPermissionTerminalFailure(
        Context context,
        String status,
        String error
    ) {
        if (context == null) return;
        String safeStatus = status == null || status.trim().isEmpty() ? "PERMISSION_FLOW_FAILED" : status.trim();
        String safeError = error == null ? "" : error.trim();
        SharedPreferences shared = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        shared.edit()
            .putBoolean("projectionPermissionInFlight", false)
            .putBoolean("projectionActive", false)
            .putString("projectionStatus", safeStatus)
            .putString("projectionError", safeError)
            .putBoolean("projectionReauthRequired", true)
            .putBoolean("projectionReauthNoticeShown", false)
            .putString("lastEvent", safeError.isEmpty()
                ? "Falha terminal no fluxo de autorização da leitura da tela"
                : "Falha terminal no fluxo de autorização: " + safeError)
            .apply();

        GtoObserverService live = instance;
        if (live != null && running) {
            live.mainHandler.post(() -> live.handleProjectionPermissionTerminalFailure(safeStatus, safeError));
        }
    }

    private void handleProjectionPermissionTerminalFailure(String status, String error) {
        projectionPermissionInFlight = false;
        suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
        projectionStatus = status == null || status.isEmpty() ? "PERMISSION_FLOW_FAILED" : status;
        prefs.edit()
            .putBoolean("projectionPermissionInFlight", false)
            .putBoolean("projectionActive", projectionActive)
            .putString("projectionStatus", projectionStatus)
            .putString("projectionError", error == null ? "" : error)
            .putBoolean("projectionReauthRequired", true)
            .putBoolean("projectionReauthNoticeShown", false)
            .apply();
        updateFreightTouchPulseSensor();
        try {
            startForegroundForTypes(projectionActive);
        } catch (Exception ex) {
            prefs.edit()
                .putString("startError", describeError(ex))
                .putString("lastEvent", "Falha ao restaurar o serviço após erro de autorização")
                .apply();
        }
        updateNotification();
        scheduleBubbleRestoreAfterPermission();
        if (gtoForeground) {
            updateProjectionReauthButton();
            if (projectionReauthButtonView == null) {
                showStatusChip("A autorização de leitura não foi concluída. Abra a bolinha NVU e tente novamente.", 4200L);
            }
        }
    }

    public static boolean requestProjectionPermissionIfRunning() {
        GtoObserverService live = instance;
        if (live == null || !running) return false;
        live.mainHandler.post(live::requestProjectionPermission);
        return true;
    }

    public static boolean markProjectionPermissionInFlightIfRunning() {
        GtoObserverService live = instance;
        if (live == null || !running) return false;
        live.mainHandler.post(() -> {
            live.projectionPermissionInFlight = true;
            live.suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
            live.projectionStatus = "REQUESTING_PERMISSION_APP";
            live.hideProjectionReauthButton();
            live.prefs.edit()
                .putBoolean("projectionPermissionInFlight", true)
                .putString("projectionStatus", live.projectionStatus)
                .apply();
        });
        return true;
    }

    public static boolean recoverIfEnabled(Context context) {
        if (context == null || running) return running;
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!preferences.getBoolean("enabled", false)) return false;
        if (!Settings.canDrawOverlays(appContext)) {
            preferences.edit().putString("startError", "Permissão de sobreposição não está ativa.").apply();
            return false;
        }
        if (!hasUsageStatsAccess(appContext)) {
            preferences.edit().putString("startError", "Acesso de uso necessário para detectar o GTO não está ativo.").apply();
            return false;
        }
        try {
            preferences.edit().remove("startError").apply();
            Intent intent = new Intent(appContext, GtoObserverService.class).setAction(ACTION_START);
            ContextCompat.startForegroundService(appContext, intent);
            return true;
        } catch (Exception ex) {
            preferences.edit().putString("startError", describeError(ex)).apply();
            return false;
        }
    }

    public static String describeError(Throwable error) {
        if (error == null) return "Erro desconhecido";
        String message = error.getMessage();
        String value = error.getClass().getSimpleName() + (message == null || message.trim().isEmpty() ? "" : ": " + message.trim());
        return value.length() > 220 ? value.substring(0, 220) : value;
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

    public static void reportPreciseTouch(float x, float y, long eventTime) {
        GtoObserverService service = instance;
        if (service == null || !running) return;
        service.mainHandler.post(() -> service.handlePreciseTouch(x, y, eventTime));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        instance = this;
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long serviceNow = System.currentTimeMillis();
        prefs.edit()
            .putBoolean("projectionActive", false)
            .putBoolean("overlayVisible", false)
            .putBoolean("projectionReauthButtonVisible", false)
            .putLong("serviceStartedAt", serviceNow)
            .putLong("serviceHeartbeatAt", serviceNow)
            .remove("startError")
            .apply();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        selectionTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        // MediaProjection itself cannot survive process death, but the immutable FIX18
        // operation/freight snapshot can. Preserve a real in-progress session and only
        // require the driver to re-authorize screen reading when capture is needed again.
        String restoredState = prefs.getString("tripState", STATE_IDLE);
        String restoredCompletion = prefs.getString("completionStatus", "");
        String restoredSyncStatus = prefs.getString("gtoTripSyncStatus", "");
        boolean recoverCompletedTrip = STATE_RESULT_CONFIRMED.equals(restoredState)
            && "CONFIRMED_NORMAL".equals(restoredCompletion)
            && !GtoAutoTripSync.STATUS_SYNCED.equals(restoredSyncStatus);
        boolean recoverActiveTrip = isRecoverableActiveState(restoredState)
            && hasFreshDurableSession(restoredState);
        String restoredResultAction = prefs.getString("resultAction", "");
        boolean recoverExactReceive = recoverActiveTrip
            && (STATE_RESULT_DETECTED.equals(restoredState) || STATE_AWAITING_BONUS.equals(restoredState))
            && ("RECEIVE".equals(restoredResultAction) || "RECEIVE_FALLBACK_CONFIRMED".equals(restoredResultAction))
            && prefs.getBoolean("resultReceiveLatched", false);

        if (recoverCompletedTrip) {
            prefs.edit()
                .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_PENDING)
                .remove("gtoTripSyncError")
                .putString("lastEvent", "Entrega concluída preservada · retomando sincronização NVU")
                .apply();
        } else if (recoverActiveTrip) {
            // A CONFIRMING_FREIGHT transaction keeps short-lived frame references only in
            // memory. After process death those references no longer exist, so resume at
            // WAITING_FREIGHT while keeping the immutable operation snapshot. This avoids
            // leaving the state machine permanently stuck in a confirmation that can no
            // longer finish. Trips with an already locked freight remain untouched.
            String recoverableState = STATE_CONFIRMING_FREIGHT.equals(restoredState)
                ? STATE_WAITING_FREIGHT
                : restoredState;
            prefs.edit()
                .putString("tripState", recoverableState)
                .putString("projectionStatus", "REAUTH_REQUIRED_AFTER_RESTART")
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putString("lastEvent", STATE_TRIP_IN_PROGRESS.equals(recoverableState)
                    ? "Viagem GTO preservada após reinício · reative a leitura da tela para finalizar"
                    : "Sessão GTO preservada após reinício · reative a leitura da tela")
                .apply();
        } else if (!STATE_IDLE.equals(restoredState)) {
            GtoAutoTripSync.discardSessionSnapshot(this, prefs.getString("gtoTripSessionId", ""));
            clearTripAnalysis();
            prefs.edit()
                .putString("tripState", STATE_IDLE)
                .putString("lastEvent", "Sessão GTO anterior encerrada com segurança")
                .apply();
        }
        createNotificationChannel();
        if (recoverCompletedTrip) {
            mainHandler.postDelayed(
                () -> GtoAutoTripSync.enqueueConfirmedTrip(this, prefs, automaticTripSyncListener()),
                900L
            );
        } else if (recoverExactReceive) {
            // The exact Receber action is durable. A process restart after the tap must
            // continue completion immediately instead of waiting for the GTO screen again.
            mainHandler.postDelayed(this::confirmNormalResultAutomatically, 250L);
        } else {
            mainHandler.postDelayed(this::flushAutomaticTripQueue, 1200L);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            prefs.edit()
                .putBoolean("enabled", false)
                .putBoolean("overlayVisible", false)
                .remove("startError")
                .apply();
            stopProjection();
            hideOverlays();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_PROJECTION_DENIED.equals(action)) {
            projectionPermissionInFlight = false;
            suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
            projectionActive = false;
            projectionStatus = "DENIED";
            prefs.edit()
                .putBoolean("projectionActive", false)
                .putString("projectionStatus", projectionStatus)
                .putBoolean("projectionPermissionInFlight", false)
                .putString("screenState", "CAPTURE_DENIED")
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putBoolean("touchCaptureNeeded", false)
                .putString("lastEvent", "Captura de tela não autorizada")
                .apply();
            if (STATE_WAITING_FREIGHT.equals(getTripState())) {
                showToast("A leitura da tela é necessária. Toque em Reativar ao lado da bolinha NVU.");
            }
            updateFreightTouchPulseSensor();
            try {
                startForegroundForTypes(false);
            } catch (Exception ex) {
                prefs.edit()
                    .putString("startError", describeError(ex))
                    .putString("lastEvent", "Falha ao manter serviço após recusa da captura")
                    .apply();
            }
            updateNotification();
            scheduleBubbleRestoreAfterPermission();
            return START_STICKY;
        }

        long startedAt = System.currentTimeMillis();
        prefs.edit()
            .putBoolean("enabled", true)
            .putLong("serviceHeartbeatAt", startedAt)
            .remove("startError")
            .apply();
        try {
            startForegroundForTypes(false);
        } catch (Exception ex) {
            running = false;
            prefs.edit()
                .putString("startError", describeError(ex))
                .putLong("serviceHeartbeatAt", 0L)
                .apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        scheduleForegroundPoll();

        if (ACTION_START_PROJECTION.equals(action)) {
            projectionPermissionInFlight = false;
            suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
            prefs.edit().putBoolean("projectionPermissionInFlight", false).apply();
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = readProjectionData(intent);
            if (resultData != null) {
                startProjection(resultCode, resultData);
            }
            scheduleBubbleRestoreAfterPermission();
        }

        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Intent readProjectionData(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void scheduleForegroundPoll() {
        mainHandler.removeCallbacks(foregroundPoll);
        lastUsageQueryAt = Math.max(0L, System.currentTimeMillis() - 60_000L);
        mainHandler.post(foregroundPoll);
    }

    private void refreshForegroundPackage() {
        if (usageStatsManager == null || !hasUsageStatsAccess()) return;

        long now = System.currentTimeMillis();
        long from = Math.max(0L, lastUsageQueryAt - 1200L);
        lastUsageQueryAt = now;

        UsageEvents events = usageStatsManager.queryEvents(from, now);
        UsageEvents.Event event = new UsageEvents.Event();
        long newestForeground = 0L;
        String latestPackage = null;
        boolean sawTransientForeground = false;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            long at = event.getTimeStamp();
            String packageName = event.getPackageName();
            String className = event.getClassName();

            boolean enteredForeground = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            boolean enteredBackground = type == UsageEvents.Event.MOVE_TO_BACKGROUND;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                enteredForeground = enteredForeground || type == UsageEvents.Event.ACTIVITY_RESUMED;
                enteredBackground = enteredBackground
                    || type == UsageEvents.Event.ACTIVITY_PAUSED
                    || type == UsageEvents.Event.ACTIVITY_STOPPED;
            }

            if (GTO_PACKAGE.equals(packageName)) {
                if (enteredForeground) lastGtoForegroundEventAt = Math.max(lastGtoForegroundEventAt, at);
                if (enteredBackground) lastGtoBackgroundEventAt = Math.max(lastGtoBackgroundEventAt, at);
            }

            if (enteredForeground && at >= newestForeground) {
                if (isTransientForegroundEvent(packageName, className)) {
                    sawTransientForeground = true;
                    continue;
                }
                newestForeground = at;
                latestPackage = packageName;
            }
        }

        if (latestPackage != null) {
            foregroundPackage = latestPackage;
        } else if (!sawTransientForeground
            && GTO_PACKAGE.equals(foregroundPackage)
            && lastGtoBackgroundEventAt > lastGtoForegroundEventAt) {
            // On some Android 7-9/OEM builds the next foreground package event can be
            // delayed, while GTO's MOVE_TO_BACKGROUND arrives immediately. Do not leave
            // the NVU bubble attached to another app in that case.
            foregroundPackage = "";
        }

        persistForegroundRuntimeIfDue(now);
    }

    private void persistServiceHeartbeatIfDue(long now) {
        if (now - lastHeartbeatPersistAt < HEARTBEAT_PERSIST_INTERVAL_MS) return;
        lastHeartbeatPersistAt = now;
        prefs.edit().putLong("serviceHeartbeatAt", now).apply();
    }

    private void persistForegroundRuntimeIfDue(long now) {
        String currentPackage = foregroundPackage == null ? "" : foregroundPackage;
        boolean changed = !currentPackage.equals(lastPersistedForegroundPackage)
            || lastGtoForegroundEventAt != lastPersistedGtoForegroundEventAt
            || lastGtoBackgroundEventAt != lastPersistedGtoBackgroundEventAt;
        if (!changed && now - lastForegroundStatusPersistAt < FOREGROUND_STATUS_PERSIST_INTERVAL_MS) return;
        lastForegroundStatusPersistAt = now;
        lastPersistedForegroundPackage = currentPackage;
        lastPersistedGtoForegroundEventAt = lastGtoForegroundEventAt;
        lastPersistedGtoBackgroundEventAt = lastGtoBackgroundEventAt;
        prefs.edit()
            .putString("foregroundPackage", currentPackage)
            .putLong("lastGtoForegroundEventAt", lastGtoForegroundEventAt)
            .putLong("lastGtoBackgroundEventAt", lastGtoBackgroundEventAt)
            .apply();
    }

    private void persistFreightRuntimeStatus(String state, int freightCount, long now, long sequence) {
        String safeState = state == null ? "UNKNOWN" : state;
        boolean changed = !safeState.equals(lastPersistedFreightRuntimeState)
            || freightCount != lastPersistedFreightCount;
        if (!changed && now - lastFreightRuntimePersistAt < FREIGHT_RUNTIME_PERSIST_INTERVAL_MS) return;
        lastFreightRuntimePersistAt = now;
        lastPersistedFreightRuntimeState = safeState;
        lastPersistedFreightCount = freightCount;
        SharedPreferences.Editor editor = prefs.edit()
            .putString("screenState", safeState)
            .putInt("freightCount", Math.max(0, freightCount));
        if ("FREIGHT_LIST".equals(safeState)) {
            editor.putLong("freightStructureAt", now)
                .putLong("freightFrameSequence", sequence);
        }
        editor.apply();
    }

    private boolean validateObserverRuntimePermissions() {
        boolean overlayOk = Settings.canDrawOverlays(this);
        boolean usageOk = hasUsageStatsAccess();
        if (overlayOk && usageOk) {
            if (!lastRuntimePermissionError.isEmpty() || (prefs != null && !prefs.getString("runtimePermissionError", "").isEmpty())) {
                lastRuntimePermissionError = "";
                prefs.edit()
                    .remove("runtimePermissionError")
                    .remove("runtimePermissionErrorCode")
                    .putString("lastEvent", "Permissões do observador GTO restauradas")
                    .apply();
                updateNotification();
            }
            return true;
        }

        String code = !overlayOk ? "OVERLAY_REVOKED" : "USAGE_ACCESS_REVOKED";
        String message = !overlayOk
            ? "Permissão do botão flutuante foi desativada pelo Android."
            : "Permissão de acesso de uso foi desativada pelo Android.";
        if (!message.equals(lastRuntimePermissionError)) {
            lastRuntimePermissionError = message;
            prefs.edit()
                .putString("runtimePermissionError", message)
                .putString("runtimePermissionErrorCode", code)
                .putString("lastEvent", message)
                .apply();
            updateNotification();
        }
        foregroundPackage = "";
        if (gtoForeground || bubbleView != null || menuView != null || statusChipView != null
            || projectionReauthButtonView != null || freightTouchPulseView != null) {
            gtoForeground = false;
            nonGtoForegroundSince = 0L;
            prefs.edit()
                .putBoolean("gtoForeground", false)
                .putBoolean("touchCaptureNeeded", false)
                .putString("foregroundPackage", "")
                .apply();
            hideOverlays();
        }
        return false;
    }

    private boolean captureIsNeededForCurrentState() {
        String state = getTripState();
        return STATE_WAITING_FREIGHT.equals(state)
            || STATE_CONFIRMING_FREIGHT.equals(state)
            || STATE_TRIP_IN_PROGRESS.equals(state)
            || STATE_RESULT_DETECTED.equals(state)
            || STATE_AWAITING_BONUS.equals(state);
    }

    private void maybeNotifyProjectionReauthorization() {
        if (projectionActive || projectionPermissionInFlight || !captureIsNeededForCurrentState()) return;
        if (!prefs.getBoolean("projectionReauthRequired", false)) return;
        updateProjectionReauthButton();
        if (prefs.getBoolean("projectionReauthNoticeShown", false)) return;
        prefs.edit()
            .putBoolean("projectionReauthNoticeShown", true)
            .putString("lastEvent", "Leitura da tela precisa ser autorizada novamente")
            .apply();
        if (projectionReauthButtonView == null) {
            showStatusChip("Leitura da tela foi encerrada pelo Android. Abra a bolinha e autorize novamente.", 5200L);
        }
        updateNotification();
    }

    private boolean isTransientForegroundEvent(String packageName, String className) {
        if (packageName == null) return true;
        if (getPackageName().equals(packageName)
            && className != null
            && className.endsWith("GtoProjectionPermissionActivity")) {
            return true;
        }

        // System UI/permission surfaces can temporarily emit a foreground UsageEvent
        // while GTO remains the actual task underneath. The old implementation ignored
        // them only during the projection dialog, which made the bubble disappear on
        // some OEMs after notification shade/game-system overlays. Never let these
        // transient system surfaces replace the last real application foreground owner.
        String p = packageName.toLowerCase(Locale.ROOT);
        if ("android".equals(p)
            || "com.android.systemui".equals(p)
            || p.contains("permissioncontroller")
            || p.contains("packageinstaller")) {
            return true;
        }
        return false;
    }

    private boolean hasUsageStatsAccess() {
        return hasUsageStatsAccess(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || notificationManager == null) return;
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "Observador GTO",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Mantém o botão NVU disponível durante o Global Truck Online.");
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this,
            4608,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, GtoObserverService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
            this,
            4609,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NVU · Observador GTO")
            .setContentText(notificationText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Parar", stopPending)
            .build();
    }

    private String notificationText() {
        String runtimeError = prefs == null ? "" : prefs.getString("runtimePermissionError", "");
        if (!runtimeError.isEmpty()) return "Ação necessária · revise as permissões do GTO";
        if (prefs != null && prefs.getBoolean("projectionReauthRequired", false) && captureIsNeededForCurrentState()) {
            return "Ação necessária · autorize novamente a leitura da tela";
        }
        String state = getTripState();
        if (STATE_WAITING_FREIGHT.equals(state)) return "Etapa 1/4 · escolha seu frete";
        if (STATE_CONFIRMING_FREIGHT.equals(state)) return "Etapa 1/4 · confirmando frete…";
        if (STATE_TRIP_IN_PROGRESS.equals(state)) return "Etapa 2/4 · viagem em andamento";
        if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) return "Etapa 3/4 · validando a entrega";
        if (STATE_REJECTED_BONUS.equals(state)) return "Última viagem recusada por bônus";
        if (STATE_RESULT_CONFIRMED.equals(state)) {
            String syncStatus = prefs.getString("gtoTripSyncStatus", "");
            if (GtoAutoTripSync.STATUS_SYNCED.equals(syncStatus)) return "Viagem registrada automaticamente";
            if (GtoAutoTripSync.STATUS_REJECTED.equals(syncStatus)) return "Viagem concluída · registro recusado";
            return "Etapa 4/4 · enviando viagem para a NVU";
        }
        return "Botão flutuante pronto para o GTO";
    }

    private void startForegroundForTypes(boolean includeProjection) {
        int serviceTypes = 0;
        if (includeProjection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            serviceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceTypes);
    }

    private void updateNotification() {
        if (notificationManager == null) return;
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
            if (prefs != null) prefs.edit().remove("notificationError").apply();
        } catch (Exception ex) {
            if (prefs != null) {
                prefs.edit()
                    .putString("notificationError", describeError(ex))
                    .putLong("notificationErrorAt", System.currentTimeMillis())
                    .putString("lastEvent", "Falha ao atualizar notificação do observador GTO")
                    .apply();
            }
        }
    }

    private void showBubbleIfAllowed() {
        if (bubbleView != null) return;
        lastBubbleAttemptAt = System.currentTimeMillis();
        if (windowManager == null) {
            recordOverlayFailure(new IllegalStateException("WindowManager indisponível"));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            recordOverlayFailure(new SecurityException("Permissão SYSTEM_ALERT_WINDOW não está ativa"));
            return;
        }
        prefs.edit().putLong("overlayLastAttemptAt", lastBubbleAttemptAt).apply();

        final int size = dp(56);
        bubbleView = new FrameLayout(this);
        bubbleView.setBackground(makeRoundedBackground(Color.rgb(59, 168, 176), dp(18)));
        bubbleView.setElevation(dp(6));

        TextView label = new TextView(this);
        label.setText("NVU");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13f);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        bubbleView.addView(label, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));

        bubbleParams = new WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics metrics = realDisplayMetrics();
        int savedX = prefs.getInt("bubbleX", Math.max(dp(8), metrics.widthPixels - size - dp(18)));
        int savedY = prefs.getInt("bubbleY", Math.max(dp(80), metrics.heightPixels / 3));
        bubbleParams.x = clamp(savedX, 0, Math.max(0, metrics.widthPixels - size));
        bubbleParams.y = clamp(savedY, 0, Math.max(0, metrics.heightPixels - size));

        final float[] downRawX = new float[1];
        final float[] downRawY = new float[1];
        final int[] startX = new int[1];
        final int[] startY = new int[1];
        final boolean[] dragging = new boolean[1];
        final int touchSlop = Math.max(dp(10), ViewConfiguration.get(this).getScaledTouchSlop());

        bubbleView.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX[0] = event.getRawX();
                    downRawY[0] = event.getRawY();
                    startX[0] = bubbleParams.x;
                    startY[0] = bubbleParams.y;
                    dragging[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX[0];
                    float dy = event.getRawY() - downRawY[0];
                    if (!dragging[0] && Math.hypot(dx, dy) >= touchSlop) {
                        dragging[0] = true;
                        // Dragging is an explicit user action, so collapsing the menu here
                        // is deterministic. Minor finger jitter never closes it anymore.
                        closeMenu();
                    }
                    if (!dragging[0]) return true;
                    DisplayMetrics screen = realDisplayMetrics();
                    bubbleParams.x = clamp(startX[0] + Math.round(dx), 0, Math.max(0, screen.widthPixels - size));
                    bubbleParams.y = clamp(startY[0] + Math.round(dy), 0, Math.max(0, screen.heightPixels - size));
                    try {
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        updateProjectionReauthButtonPosition();
                    } catch (Exception ex) {
                        recordOverlayFailure(ex);
                        hideProjectionReauthButton();
                        try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
                        bubbleView = null;
                        bubbleParams = null;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    // updateViewLayout can fail on an OEM while the finger is still down.
                    // In that case ACTION_MOVE already detached the broken overlay and
                    // cleared bubbleParams. Never dereference that stale LayoutParams on
                    // ACTION_UP; let the foreground self-heal recreate the bubble instead.
                    if (bubbleParams != null) {
                        prefs.edit().putInt("bubbleX", bubbleParams.x).putInt("bubbleY", bubbleParams.y).apply();
                    }
                    if (!dragging[0] && bubbleView != null && bubbleParams != null) {
                        toggleMenu();
                    } else if (bubbleView == null && gtoForeground && Settings.canDrawOverlays(this)) {
                        mainHandler.postDelayed(this::showBubbleIfAllowed, 220L);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging[0] = false;
                    return true;
                default:
                    return false;
            }
        });

        try {
            int previousFailures = prefs.getInt("overlayFailureCount", 0);
            windowManager.addView(bubbleView, bubbleParams);
            SharedPreferences.Editor editor = prefs.edit()
                .putBoolean("overlayVisible", true)
                .remove("overlayError")
                .remove("overlayErrorAt")
                .putInt("overlayFailureCount", 0);
            if (previousFailures > 0) {
                editor.putString("lastEvent", "Botão flutuante restaurado no GTO");
            }
            editor.apply();
            updateProjectionReauthButton();
        } catch (Exception ex) {
            bubbleView = null;
            bubbleParams = null;
            recordOverlayFailure(ex);
        }
    }

    private boolean shouldShowProjectionReauthButton() {
        return running
            && !destroying
            && prefs != null
            && prefs.getBoolean("enabled", false)
            && gtoForeground
            && !projectionActive
            && !projectionPermissionInFlight
            && captureIsNeededForCurrentState()
            && prefs.getBoolean("projectionReauthRequired", false)
            && Settings.canDrawOverlays(this);
    }

    private void updateProjectionReauthButton() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::updateProjectionReauthButton);
            return;
        }
        if (!shouldShowProjectionReauthButton()
            || bubbleView == null
            || bubbleParams == null
            || !bubbleView.isAttachedToWindow()
            || windowManager == null) {
            hideProjectionReauthButton();
            return;
        }
        if (projectionReauthButtonView != null && !projectionReauthButtonView.isAttachedToWindow()) {
            projectionReauthButtonView = null;
            projectionReauthButtonParams = null;
            if (prefs.getBoolean("projectionReauthButtonVisible", false)) {
                prefs.edit().putBoolean("projectionReauthButtonVisible", false).apply();
            }
        }
        if (projectionReauthButtonView == null) {
            TextView action = new TextView(this);
            action.setText("Reativar");
            action.setContentDescription("Reativar leitura da tela");
            action.setTextColor(Color.WHITE);
            action.setTextSize(10.5f);
            action.setGravity(Gravity.CENTER);
            action.setTypeface(action.getTypeface(), android.graphics.Typeface.BOLD);
            action.setPadding(dp(8), 0, dp(8), 0);
            action.setBackground(makeRoundedBackground(Color.rgb(208, 139, 35), dp(12)));
            action.setElevation(dp(7));
            action.setOnClickListener(v -> {
                if (projectionPermissionInFlight || projectionActive) return;
                closeMenu();
                hideProjectionReauthButton();
                requestProjectionPermission();
            });
            projectionReauthButtonParams = new WindowManager.LayoutParams(
                dp(82), dp(34), overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            );
            projectionReauthButtonParams.gravity = Gravity.TOP | Gravity.START;
            projectionReauthButtonView = action;
            updateProjectionReauthButtonPosition();
            try {
                windowManager.addView(projectionReauthButtonView, projectionReauthButtonParams);
                prefs.edit()
                    .putBoolean("projectionReauthButtonVisible", true)
                    .putString("lastEvent", "Atalho compacto para reativar leitura exibido")
                    .apply();
            } catch (Exception ex) {
                prefs.edit()
                    .putBoolean("projectionReauthButtonVisible", false)
                    .putString("projectionReauthButtonError", describeError(ex))
                    .putLong("projectionReauthButtonErrorAt", System.currentTimeMillis())
                    .apply();
                projectionReauthButtonView = null;
                projectionReauthButtonParams = null;
            }
            return;
        }
        updateProjectionReauthButtonPosition();
    }

    private void updateProjectionReauthButtonPosition() {
        if (projectionReauthButtonView == null || projectionReauthButtonParams == null
            || bubbleParams == null || windowManager == null) return;
        DisplayMetrics screen = realDisplayMetrics();
        int bubbleSize = dp(56);
        int buttonWidth = dp(82);
        int buttonHeight = dp(34);
        int gap = dp(8);
        int rightX = bubbleParams.x + bubbleSize + gap;
        int leftX = bubbleParams.x - buttonWidth - gap;
        if (rightX + buttonWidth <= screen.widthPixels - dp(8)) {
            projectionReauthButtonParams.x = Math.max(dp(8), rightX);
        } else {
            projectionReauthButtonParams.x = clamp(leftX, dp(8), Math.max(dp(8), screen.widthPixels - buttonWidth - dp(8)));
        }
        projectionReauthButtonParams.y = clamp(
            bubbleParams.y + Math.max(0, (bubbleSize - buttonHeight) / 2),
            dp(8), Math.max(dp(8), screen.heightPixels - buttonHeight - dp(8))
        );
        if (projectionReauthButtonView.isAttachedToWindow()) {
            try {
                windowManager.updateViewLayout(projectionReauthButtonView, projectionReauthButtonParams);
            } catch (Exception ex) {
                prefs.edit()
                    .putString("projectionReauthButtonError", describeError(ex))
                    .putLong("projectionReauthButtonErrorAt", System.currentTimeMillis())
                    .apply();
                hideProjectionReauthButton();
            }
        }
    }

    private void hideProjectionReauthButton() {
        boolean hadView = projectionReauthButtonView != null;
        if (hadView && windowManager != null) {
            try { windowManager.removeView(projectionReauthButtonView); } catch (Exception ignored) {}
        }
        projectionReauthButtonView = null;
        projectionReauthButtonParams = null;
        if (prefs != null && (hadView || prefs.getBoolean("projectionReauthButtonVisible", false))) {
            prefs.edit().putBoolean("projectionReauthButtonVisible", false).apply();
        }
    }

    private void recordOverlayFailure(Throwable error) {
        int failures = prefs.getInt("overlayFailureCount", 0) + 1;
        String detail = describeError(error);
        String previous = prefs.getString("overlayError", "");
        SharedPreferences.Editor editor = prefs.edit()
            .putBoolean("overlayVisible", false)
            .putString("overlayError", detail)
            .putLong("overlayErrorAt", System.currentTimeMillis())
            .putInt("overlayFailureCount", failures);
        if (failures == 1 || !detail.equals(previous)) {
            editor.putString("lastEvent", "Falha no botão flutuante: " + detail);
        }
        editor.apply();
    }

    private void updateFreightTouchPulseSensor() {
        String state = getTripState();
        boolean replacementSelectionArmed = replacementFreightCandidateArmed
            && isReplaceableActiveSessionState(state);
        boolean selectionArmed = STATE_WAITING_FREIGHT.equals(state) || replacementSelectionArmed;
        boolean resultActionArmed = (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state))
            && !replacementSelectionArmed;
        boolean shouldShow = gtoForeground
            && projectionActive
            && (selectionArmed || resultActionArmed)
            && windowManager != null
            && Settings.canDrawOverlays(this);
        if (shouldShow) showFreightTouchPulseSensor();
        else hideFreightTouchPulseSensor();
    }

    private void showFreightTouchPulseSensor() {
        if (freightTouchPulseView != null || windowManager == null || !Settings.canDrawOverlays(this)) return;

        View sensor = new View(this);
        sensor.setBackgroundColor(Color.TRANSPARENT);
        sensor.setAlpha(0.01f);
        sensor.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_OUTSIDE) return false;
            String state = getTripState();
            boolean replacementSelectionArmed = replacementFreightCandidateArmed
                && isReplaceableActiveSessionState(state);
            boolean selectionArmed = STATE_WAITING_FREIGHT.equals(state) || replacementSelectionArmed;
            boolean resultActionArmed = (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state))
                && !replacementSelectionArmed;
            if (!gtoForeground || menuView != null) return false;
            if (selectionArmed) {
                queueFreightTouchMarker();
            } else if (resultActionArmed) {
                // ACTION_OUTSIDE normally carries the real screen coordinates. Resolve
                // Receber x ADS immediately when the OEM preserves them; if coordinates
                // are redacted, keep a durable pending action and resolve it from the
                // subsequent GTO screen without any temporal expiry.
                if (!resolveResultActionOutsideTouch(event)) markResultActionTouch();
            }
            return false;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            1,
            1,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        try {
            windowManager.addView(sensor, params);
            freightTouchPulseView = sensor;
            freightTouchPulseParams = params;
            boolean resultState = STATE_RESULT_DETECTED.equals(getTripState()) || STATE_AWAITING_BONUS.equals(getTripState());
            if (resultState) {
                resultTouchFallbackRequired = false;
                resultTouchFallbackReady = false;
                resultTouchFallbackContinuityBroken = false;
            }
            prefs.edit()
                .putBoolean("touchPulseSensorVisible", true)
                .putBoolean("resultTouchFallbackRequired", resultState ? false : prefs.getBoolean("resultTouchFallbackRequired", false))
                .remove("touchPulseSensorError")
                .remove("touchPulseSensorErrorAt")
                .apply();
        } catch (Exception ex) {
            freightTouchPulseView = null;
            freightTouchPulseParams = null;
            boolean resultState = STATE_RESULT_DETECTED.equals(getTripState()) || STATE_AWAITING_BONUS.equals(getTripState());
            if (resultState) {
                resultTouchFallbackRequired = true;
                resultTouchFallbackReady = false;
                resultTouchFallbackContinuityBroken = false;
            }
            prefs.edit()
                .putBoolean("touchPulseSensorVisible", false)
                .putBoolean("resultTouchFallbackRequired", resultState)
                .putBoolean("resultTouchFallbackReady", false)
                .putBoolean("resultTouchFallbackContinuityBroken", false)
                .putString("touchPulseSensorError", describeError(ex))
                .putLong("touchPulseSensorErrorAt", System.currentTimeMillis())
                .putString("lastEvent", resultState
                    ? "Android não permitiu observar o toque em Receber; contingência segura ativada"
                    : "Sensor de seleção indisponível; usando confirmação visual reforçada")
                .apply();
            if (resultState) {
                showStatusChip("Seu Android não permitiu detectar o toque em Receber. Toque em Receber normalmente; se necessário, a bolinha NVU liberará uma confirmação segura após a tela fechar.", 5600L);
            }
        }
    }

    private void hideFreightTouchPulseSensor() {
        if (freightTouchPulseView != null && windowManager != null) {
            try { windowManager.removeView(freightTouchPulseView); } catch (Exception ignored) {}
        }
        freightTouchPulseView = null;
        freightTouchPulseParams = null;
        if (prefs != null) prefs.edit().putBoolean("touchPulseSensorVisible", false).apply();
        // UI lifecycle must never mutate the frame-selection engine. In older builds,
        // removing this 1px sensor could recycle the frozen freight snapshot before OCR.
    }

    private void armResultTouchFallbackReady(String reason) {
        String state = getTripState();
        if (!STATE_RESULT_DETECTED.equals(state) && !STATE_AWAITING_BONUS.equals(state)) return;
        boolean required = resultTouchFallbackRequired || prefs.getBoolean("resultTouchFallbackRequired", false);
        boolean broken = resultTouchFallbackContinuityBroken || prefs.getBoolean("resultTouchFallbackContinuityBroken", false);
        if (!required || broken || hasRecentNormalResultActionEvidence(System.currentTimeMillis())) return;
        if (resultTouchFallbackReady || prefs.getBoolean("resultTouchFallbackReady", false)) return;
        resultTouchFallbackReady = true;
        prefs.edit()
            .putBoolean("resultTouchFallbackRequired", true)
            .putBoolean("resultTouchFallbackReady", true)
            .putString("resultTouchFallbackReason", reason == null ? "RESULT_SCREEN_EXITED" : reason)
            .putString("lastEvent", "Contingência de recebimento pronta após a tela Concluído fechar")
            .apply();
        showStatusChip("A tela Concluído fechou, mas este Android não informou o toque. Abra a bolinha NVU e confirme o recebimento para preservar a entrega.", 5600L);
        if (menuView != null) populateMenuContents(menuView);
    }

    private void confirmResultTouchFallback() {
        String state = getTripState();
        if (!STATE_RESULT_DETECTED.equals(state) && !STATE_AWAITING_BONUS.equals(state)) return;
        boolean ready = resultTouchFallbackReady || prefs.getBoolean("resultTouchFallbackReady", false);
        boolean broken = resultTouchFallbackContinuityBroken || prefs.getBoolean("resultTouchFallbackContinuityBroken", false);
        if (!ready || broken) {
            showStatusChip("A confirmação de contingência ainda não é segura. Volte ao GTO e mantenha o fluxo da entrega visível.", 3600L);
            return;
        }
        long now = System.currentTimeMillis();
        boolean persisted = prefs.edit()
            .putLong("resultActionTouchAt", now)
            .putString("resultAction", "RECEIVE_FALLBACK_CONFIRMED")
            .putBoolean("resultReceiveLatched", true)
            .putString("resultActionSource", "oem-sensor-fallback")
            .putString("completionStatus", "RECEIVE_LATCHED")
            .putString("lastEvent", "Recebimento confirmado pela contingência segura do dispositivo")
            .putBoolean("touchCaptureNeeded", false)
            .putBoolean("resultTouchFallbackReady", false)
            .commit();
        if (!persisted) {
            showStatusChip("Não foi possível persistir a confirmação. A viagem continua preservada; tente novamente.", 4200L);
            return;
        }
        resultActionTouchAt = now;
        resultTouchFallbackReady = false;
        closeMenu();
        confirmNormalResultAutomatically();
    }

    private void discardUnresolvedResultAndStartNewFreight() {
        String state = getTripState();
        if (!STATE_RESULT_DETECTED.equals(state) && !STATE_AWAITING_BONUS.equals(state)) return;
        if (prefs.getBoolean("resultReceiveLatched", false)) {
            showStatusChip("Receber já foi confirmado. Esta entrega está preservada e não pode ser descartada.", 4200L);
            return;
        }
        if (replacementFreightCandidateArmed) {
            resultTouchFallbackRequired = false;
            resultTouchFallbackReady = false;
            prefs.edit()
                .putBoolean("resultTouchFallbackRequired", false)
                .putBoolean("resultTouchFallbackReady", false)
                .apply();
            closeMenu();
            promoteReplacementFreightCandidateToWaiting(false);
            return;
        }

        String cancelledSessionId = prefs.getString("gtoTripSessionId", "");
        String cancelledSummary = prefs.getString("selectedFreightSummary", "");
        GtoAutoTripSync.discardSessionSnapshot(this, cancelledSessionId);
        clearTripAnalysis();
        prefs.edit()
            .putString("completionStatus", "CANCELLED_IN_GAME")
            .putString("lastCancelledSessionId", cancelledSessionId)
            .putString("lastCancelledFreightSummary", cancelledSummary)
            .putLong("lastCancelledAt", System.currentTimeMillis())
            .putString("lastCancellationReason", "DRIVER_CONFIRMED_UNRESOLVED_RESULT_DISCARD")
            .putString("lastEvent", "Entrega não confirmada descartada; aguardando novo frete")
            .apply();
        setTripState(STATE_CANCELLED, "Entrega anterior descartada com segurança");
        beginTrip(false);
        announceDriverStage(
            "FREIGHT_RESTART",
            "Etapa 1/4 · Entrega anterior não confirmada descartada. Escolha o novo frete no GTO.",
            4200L,
            true
        );
        closeMenu();
    }

    private void markResultActionTouch() {
        String state = getTripState();
        if (!STATE_RESULT_DETECTED.equals(state) && !STATE_AWAITING_BONUS.equals(state)) return;
        long now = System.currentTimeMillis();
        // No expiry while the state machine is on the detected result. The driver may
        // remain on this screen for any amount of time before touching Receber.
        resultActionTouchAt = now;
        resultExitSeenAt = 0L;
        gameplayFramesAfterResult = 0;
        prefs.edit()
            .putLong("resultActionTouchAt", now)
            .putString("resultAction", "TOUCH_PENDING")
            .putBoolean("resultReceiveLatched", false)
            .putString("completionStatus", "VERIFYING_RESULT_ACTION")
            .putString("lastEvent", "Ação na tela de resultado detectada · acompanhando a transição do GTO")
            .apply();
    }

    private boolean resolveResultActionOutsideTouch(MotionEvent event) {
        if (event == null || captureWidth <= 0 || captureHeight <= 0) return false;
        if (receiveRect == null && doubleValueRect == null) return false;

        float rawX = event.getRawX();
        float rawY = event.getRawY();
        float localX = event.getX();
        float localY = event.getY();
        DisplayMetrics metrics = realDisplayMetrics();
        float scaleX = metrics.widthPixels > 0 ? captureWidth / (float) metrics.widthPixels : 1f;
        float scaleY = metrics.heightPixels > 0 ? captureHeight / (float) metrics.heightPixels : 1f;

        float[][] candidates = new float[][] {
            { rawX, rawY },
            { localX, localY },
            { rawX * scaleX, rawY * scaleY },
            { localX * scaleX, localY * scaleY }
        };

        for (float[] candidate : candidates) {
            int action = classifyResultButtonTouch(candidate[0], candidate[1]);
            if (action == 1) {
                latchExactReceiveAndSend(System.currentTimeMillis(), "outside-touch");
                return true;
            }
            if (action == 2) {
                latchExactAdsTouch(System.currentTimeMillis(), "outside-touch");
                return true;
            }
        }
        return false;
    }

    private int classifyResultButtonTouch(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0f || y < 0f
            || x > captureWidth || y > captureHeight) return 0;
        Rect receiveTarget = expandedResultTarget(receiveRect);
        Rect adsTarget = expandedResultTarget(doubleValueRect);
        boolean receive = receiveTarget != null && receiveTarget.contains(Math.round(x), Math.round(y));
        boolean ads = adsTarget != null && adsTarget.contains(Math.round(x), Math.round(y));
        // Ambiguous geometry is never promoted to an exact action. It falls back to the
        // persistent transition resolver instead of risking an ADS trip being registered.
        if (receive && ads) return 0;
        if (receive) return 1;
        if (ads) return 2;
        return 0;
    }

    private void latchExactReceiveAndSend(long now, String source) {
        String state = getTripState();
        if (!STATE_RESULT_DETECTED.equals(state) && !STATE_AWAITING_BONUS.equals(state)) return;
        resultActionTouchAt = now;
        resultExitSeenAt = 0L;
        gameplayFramesAfterResult = 0;
        boolean persisted = prefs.edit()
            .putLong("resultActionTouchAt", now)
            .putString("resultAction", "RECEIVE")
            .putBoolean("resultReceiveLatched", true)
            .putString("resultActionSource", source == null ? "exact-touch" : source)
            .putString("completionStatus", "RECEIVE_LATCHED")
            .putString("lastEvent", "Toque em Receber confirmado · finalizando e enviando a viagem")
            .putBoolean("touchCaptureNeeded", false)
            .commit();
        if (!persisted) {
            prefs.edit().putString("lastEvent", "Receber detectado, mas a confirmação local não pôde ser persistida").apply();
            return;
        }
        // R3.6: the exact Receber touch is the completion rule. No HUD return, loading
        // duration, logo screen, app switch or timeout is required before durable enqueue.
        confirmNormalResultAutomatically();
    }

    private void latchExactAdsTouch(long now, String source) {
        String state = getTripState();
        if (!STATE_RESULT_DETECTED.equals(state) && !STATE_AWAITING_BONUS.equals(state)) return;
        resultActionTouchAt = now;
        resultExitSeenAt = 0L;
        gameplayFramesAfterResult = 0;
        prefs.edit()
            .putLong("resultActionTouchAt", now)
            .putString("resultAction", "ADS")
            .putBoolean("resultReceiveLatched", false)
            .putString("resultActionSource", source == null ? "exact-touch" : source)
            .putString("completionStatus", "VERIFYING_AD_BONUS")
            .putBoolean("touchCaptureNeeded", false)
            .putString("lastEvent", "Toque em Dobrar valor/ADS detectado; registro normal bloqueado")
            .apply();
        setTripState(STATE_AWAITING_BONUS, "Opção de dobrar valor detectada com toque preciso");
    }

    private void queueFreightTouchMarker() {
        if (captureHandler == null || fastTouchMarkerQueued || selectionCoordinator.isCriticalWindow()) return;
        fastTouchMarkerQueued = true;
        captureHandler.post(() -> {
            fastTouchMarkerQueued = false;
            if (!gtoForeground) return;

            // If the old route is still marked TRIP_IN_PROGRESS but the real GTO freight
            // list is already visible, this touch is independent evidence that the driver
            // is choosing a new job. Promote to a clean WAITING_FREIGHT session before the
            // pressed frame arrives, while preserving the pre-touch page snapshot.
            if (isReplaceableActiveSessionState(getTripState()) && replacementFreightCandidateArmed) {
                String replacementState = getTripState();
                if ((STATE_RESULT_DETECTED.equals(replacementState) || STATE_AWAITING_BONUS.equals(replacementState))
                    && hasRecentNormalResultActionEvidence(System.currentTimeMillis())) {
                    // Do not throw away a completed delivery just because the driver tapped
                    // a new freight immediately after the jobs list returned. Finalize the
                    // previous action-backed Receber first; the next freight can be selected
                    // again after the durable ACK/pending status is known.
                    mainHandler.post(this::confirmNormalResultAutomatically);
                    return;
                }
                // One permissive visual candidate plus an arbitrary gameplay touch is not
                // enough to cancel a real route. Hold the touch marker until either a
                // second freight-list frame or a row-specific press signal confirms that
                // the GTO jobs list is genuinely present.
                if (activeTripFreightListFrames < 2 && replacementFreightPressedRow < 0) {
                    replacementFreightTouchPending = true;
                    replacementFreightTouchAt = System.currentTimeMillis();
                    prefs.edit().putString("lastEvent", "Novo frete tocado · aguardando segundo quadro da lista").apply();
                    return;
                }
                if (!promoteReplacementFreightCandidateToWaiting(true)) return;
            }

            if (!STATE_WAITING_FREIGHT.equals(getTripState())) return;
            armFastTouchPulseOnCaptureThread();
        });
    }

    private void toggleMenu() {
        long now = System.currentTimeMillis();
        if (now - lastBubbleTapAt < BUBBLE_TAP_DEBOUNCE_MS) return;
        lastBubbleTapAt = now;
        if (menuView != null) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    private void openMenu() {
        if (windowManager == null || bubbleParams == null || menuView != null) return;

        menuView = new LinearLayout(this);
        menuView.setOrientation(LinearLayout.VERTICAL);
        menuView.setPadding(dp(12), dp(12), dp(12), dp(12));
        menuView.setBackground(makeRoundedBackground(Color.rgb(28, 31, 36), dp(14)));
        menuView.setElevation(dp(8));
        menuOpenedAt = System.currentTimeMillis();
        populateMenuContents(menuView);

        menuParams = new WindowManager.LayoutParams(
            dp(256),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics screen = realDisplayMetrics();
        int proposedX = bubbleParams.x - dp(270);
        if (proposedX < dp(8)) proposedX = bubbleParams.x + dp(64);
        menuParams.x = clamp(proposedX, dp(8), Math.max(dp(8), screen.widthPixels - dp(264)));
        menuParams.y = clamp(bubbleParams.y, dp(8), Math.max(dp(8), screen.heightPixels - dp(220)));

        try {
            windowManager.addView(menuView, menuParams);
            prefs.edit()
                .remove("menuOverlayError")
                .remove("menuOverlayErrorAt")
                .apply();
        } catch (Exception ex) {
            String detail = describeError(ex);
            prefs.edit()
                .putString("menuOverlayError", detail)
                .putLong("menuOverlayErrorAt", System.currentTimeMillis())
                .putString("lastEvent", "Falha ao abrir painel flutuante: " + detail)
                .apply();
            menuView = null;
            menuParams = null;
            showStatusChip("Não foi possível abrir o painel NVU. Toque novamente.", 2200L);
        }
    }

    private void populateMenuContents(LinearLayout target) {
        if (target == null) return;
        target.removeAllViews();

        TextView title = new TextView(this);
        title.setText(menuTitle());
        title.setTextColor(Color.WHITE);
        title.setTextSize(13f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(2), dp(4), dp(9));
        target.addView(title, new LinearLayout.LayoutParams(dp(232), LinearLayout.LayoutParams.WRAP_CONTENT));

        String state = getTripState();
        TextView journeyGuide = new TextView(this);
        journeyGuide.setText(currentJourneyGuide(state));
        journeyGuide.setTextColor(Color.rgb(154, 164, 178));
        journeyGuide.setTextSize(10.5f);
        journeyGuide.setPadding(dp(6), 0, dp(6), dp(8));
        target.addView(journeyGuide);

        if (STATE_IDLE.equals(state) || STATE_CANCELLED.equals(state) || STATE_RESULT_CONFIRMED.equals(state) || STATE_REJECTED_BONUS.equals(state)) {
            if (STATE_RESULT_CONFIRMED.equals(state) || STATE_REJECTED_BONUS.equals(state)) {
                TextView completion = new TextView(this);
                String finalGain = prefs.getString("finalGain", prefs.getString("resultValue", ""));
                if (STATE_RESULT_CONFIRMED.equals(state)) {
                    String syncStatus = prefs.getString("gtoTripSyncStatus", "");
                    String syncError = prefs.getString("gtoTripSyncError", "");
                    if (GtoAutoTripSync.STATUS_SYNCED.equals(syncStatus)) {
                        completion.setText("Viagem registrada com sucesso" + (finalGain.isEmpty() ? "" : " · " + finalGain));
                    } else if (GtoAutoTripSync.STATUS_REJECTED.equals(syncStatus)) {
                        completion.setText("Entrega concluída · registro não aceito"
                            + (syncError.isEmpty() ? "" : "\n" + syncError));
                    } else if (GtoAutoTripSync.STATUS_PENDING.equals(syncStatus) && !syncError.isEmpty()) {
                        completion.setText("Entrega concluída" + (finalGain.isEmpty() ? "" : " · " + finalGain)
                            + "\nRegistro preservado no aparelho. " + syncError);
                    } else {
                        completion.setText("Entrega concluída" + (finalGain.isEmpty() ? "" : " · " + finalGain)
                            + "\nSincronizando automaticamente com a NVU…");
                    }
                } else {
                    completion.setText("Entrega não validada · anúncio/bônus detectado");
                }
                completion.setTextColor(Color.rgb(210, 216, 224));
                completion.setTextSize(11f);
                completion.setPadding(dp(6), 0, dp(6), dp(7));
                target.addView(completion);
            }
            boolean previousDeliveryPending = STATE_RESULT_CONFIRMED.equals(state)
                && !GtoAutoTripSync.STATUS_SYNCED.equals(prefs.getString("gtoTripSyncStatus", ""));
            boolean operationClosed = isOperationClosedForNewTrip();
            if (previousDeliveryPending) {
                TextView blocked = new TextView(this);
                blocked.setText("Aguardando confirmação da entrega anterior antes de iniciar outra viagem.");
                blocked.setTextColor(Color.rgb(245, 190, 86));
                blocked.setTextSize(10.5f);
                blocked.setPadding(dp(6), 0, dp(6), dp(6));
                target.addView(blocked);
            } else if (operationClosed) {
                TextView blocked = new TextView(this);
                blocked.setText("Operação concluída. Inicie uma nova operação para continuar.");
                blocked.setTextColor(Color.rgb(154, 164, 178));
                blocked.setTextSize(10.5f);
                blocked.setPadding(dp(6), 0, dp(6), dp(6));
                target.addView(blocked);
            } else {
                Button start = menuButton(STATE_IDLE.equals(state) ? "Iniciar viagem" : "Iniciar nova viagem");
                start.setOnClickListener(v -> {
                    closeMenu();
                    beginTrip();
                });
                target.addView(start);
            }
        } else {
            TextView status = new TextView(this);
            status.setText(statusLabel(state));
            status.setTextColor(Color.rgb(210, 216, 224));
            status.setTextSize(12f);
            status.setPadding(dp(6), dp(5), dp(6), dp(8));
            target.addView(status);

            if (STATE_WAITING_FREIGHT.equals(state)) {
                TextView helper = new TextView(this);
                int detected = prefs.getInt("freightCount", 0);
                if (!projectionActive) {
                    helper.setText("Autorize a leitura. A NVU abrirá a permissão do Android e retornará ao GTO automaticamente.");
                } else if (detected > 0) {
                    helper.setText("Lista reconhecida · " + detected + " frete" + (detected == 1 ? "" : "s")
                        + ". Selecione normalmente no GTO.");
                } else {
                    helper.setText("Abra a lista de fretes. A NVU identifica as opções automaticamente.");
                }
                helper.setTextColor(Color.rgb(154, 164, 178));
                helper.setTextSize(10.5f);
                helper.setPadding(dp(6), 0, dp(6), dp(6));
                target.addView(helper);

                if (!projectionActive) {
                    Button authorize = menuButton("Autorizar leitura da tela");
                    authorize.setOnClickListener(v -> {
                        closeMenu();
                        requestProjectionPermission();
                    });
                    target.addView(authorize);
                }
            }

            if (STATE_CONFIRMING_FREIGHT.equals(state)) {
                TextView helper = new TextView(this);
                helper.setText("Frete identificado. Conferindo os dados…");
                helper.setTextColor(Color.rgb(154, 164, 178));
                helper.setTextSize(10.5f);
                helper.setPadding(dp(6), 0, dp(6), dp(6));
                target.addView(helper);
                if (!projectionActive) {
                    Button authorize = menuButton("Reativar leitura da tela");
                    authorize.setOnClickListener(v -> {
                        closeMenu();
                        requestProjectionPermission();
                    });
                    target.addView(authorize);
                }
            }

            if (STATE_TRIP_IN_PROGRESS.equals(state)) {
                String selectedSummary = prefs.getString("selectedFreightSummary", "");
                if (!selectedSummary.isEmpty()) {
                    TextView selectedInfo = new TextView(this);
                    String destination = prefs.getString("selectedDestination", "");
                    String originCompany = prefs.getString("selectedOriginCompany", "");
                    String cargo = prefs.getString("selectedCargo", "");
                    String km = prefs.getString("selectedKm", "");
                    String value = prefs.getString("selectedValue", "");
                    StringBuilder details = new StringBuilder();
                    if (!cargo.isEmpty()) details.append("Carga: ").append(cargo).append('\n');
                    details.append("Empresa: ").append(originCompany.isEmpty() ? "—" : originCompany);
                    details.append("\nDestino: ").append(destination.isEmpty() ? "—" : destination);
                    if (!km.isEmpty()) details.append("\nDistância: ").append(km);
                    if (!value.isEmpty()) details.append("\nGanhos previstos: ").append(value);
                    selectedInfo.setText(details.toString());
                    selectedInfo.setTextColor(Color.rgb(154, 164, 178));
                    selectedInfo.setTextSize(10f);
                    selectedInfo.setPadding(dp(6), 0, dp(6), dp(4));
                    target.addView(selectedInfo);
                }

                if (!projectionActive) {
                    TextView captureHelper = new TextView(this);
                    captureHelper.setText("A viagem foi preservada. Reative a leitura; a NVU retorna ao GTO automaticamente após a autorização.");
                    captureHelper.setTextColor(Color.rgb(245, 190, 86));
                    captureHelper.setTextSize(10.5f);
                    captureHelper.setPadding(dp(6), 0, dp(6), dp(6));
                    target.addView(captureHelper);

                    Button authorize = menuButton("Reativar leitura da tela");
                    authorize.setOnClickListener(v -> {
                        closeMenu();
                        requestProjectionPermission();
                    });
                    target.addView(authorize);
                }

                if (prefs.getBoolean("resultConfirmationFallbackNeeded", false)) {
                    TextView fallbackHelper = new TextView(this);
                    fallbackHelper.setText("A conclusão não pôde ser confirmada automaticamente. Mantenha a tela “Concluído” aberta e confirme abaixo.");
                    fallbackHelper.setTextColor(Color.rgb(245, 190, 86));
                    fallbackHelper.setTextSize(10f);
                    fallbackHelper.setPadding(dp(6), dp(2), dp(6), dp(4));
                    target.addView(fallbackHelper);

                    Button finish = menuButton("Confirmar conclusão da entrega");
                    finish.setOnClickListener(v -> requestManualFinishCapture());
                    target.addView(finish);
                }
            }

            if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) {
                TextView helper = new TextView(this);
                String resultValue = prefs.getString("resultValue", "");
                boolean fallbackRequired = resultTouchFallbackRequired || prefs.getBoolean("resultTouchFallbackRequired", false);
                boolean fallbackReady = resultTouchFallbackReady || prefs.getBoolean("resultTouchFallbackReady", false);
                if (fallbackReady) {
                    helper.setText(resultValue.isEmpty()
                        ? "A tela Concluído fechou, mas este Android não informou o toque. Confirme abaixo somente se você tocou em Receber."
                        : "Resultado identificado · " + resultValue + "\nA tela Concluído fechou sem coordenadas de toque. Confirme abaixo somente se você tocou em Receber.");
                } else if (fallbackRequired) {
                    helper.setText(resultValue.isEmpty()
                        ? "Resultado identificado. Toque em Receber no GTO. Este Android usa uma contingência segura caso o toque não seja informado."
                        : "Resultado identificado · " + resultValue + "\nToque em Receber no GTO. A NVU acompanhará a saída da tela para a contingência segura.");
                } else {
                    helper.setText(resultValue.isEmpty()
                        ? "Resultado identificado. Toque em Receber no GTO para concluir."
                        : "Resultado identificado · " + resultValue + "\nToque em Receber no GTO para concluir.");
                }
                helper.setTextColor(fallbackReady ? Color.rgb(245, 190, 86) : Color.rgb(154, 164, 178));
                helper.setTextSize(10.5f);
                helper.setPadding(dp(6), 0, dp(6), dp(6));
                target.addView(helper);
                if (fallbackReady) {
                    Button confirmReceive = menuButton("Confirmar recebimento");
                    confirmReceive.setOnClickListener(v -> confirmResultTouchFallback());
                    target.addView(confirmReceive);

                    Button discardResult = menuButton("Descartar e iniciar novo frete");
                    discardResult.setOnClickListener(v -> discardUnresolvedResultAndStartNewFreight());
                    target.addView(discardResult);
                }
                if (!projectionActive) {
                    Button authorize = menuButton("Reativar leitura da tela");
                    authorize.setOnClickListener(v -> {
                        closeMenu();
                        requestProjectionPermission();
                    });
                    target.addView(authorize);
                }
            }

            if (STATE_TRIP_IN_PROGRESS.equals(state)) {
                Button cancel = menuButton("Cancelar viagem");
                cancel.setOnClickListener(v -> {
                    closeMenu();
                    cancelTrip();
                });
                target.addView(cancel);
            }
        }

        Button panel = menuButton("Painel operacional");
        panel.setOnClickListener(v -> {
            closeMenu();
            openOperationalPanel();
        });
        target.addView(panel);
    }

    private void refreshMenuContents() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::refreshMenuContents);
            return;
        }
        if (menuView == null) return;
        populateMenuContents(menuView);
        try {
            if (windowManager != null && menuParams != null) {
                windowManager.updateViewLayout(menuView, menuParams);
            }
        } catch (Exception ex) {
            prefs.edit()
                .putString("menuOverlayError", describeError(ex))
                .putLong("menuOverlayErrorAt", System.currentTimeMillis())
                .apply();
        }
    }

    private void showStatusChip(String text, long durationMs) {
        mainHandler.post(() -> {
            hideStatusChip();
            if (windowManager == null || bubbleParams == null || !Settings.canDrawOverlays(this)) return;

            TextView chip = new TextView(this);
            chip.setText(text);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(11f);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            chip.setBackground(makeRoundedBackground(Color.rgb(31, 36, 43), dp(12)));
            chip.setElevation(dp(7));

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            DisplayMetrics screen = realDisplayMetrics();
            int desiredX = bubbleParams.x - dp(250);
            if (desiredX < dp(8)) desiredX = bubbleParams.x + dp(64);
            params.x = clamp(desiredX, dp(8), Math.max(dp(8), screen.widthPixels - dp(260)));
            params.y = clamp(bubbleParams.y + dp(4), dp(8), Math.max(dp(8), screen.heightPixels - dp(64)));

            try {
                windowManager.addView(chip, params);
                statusChipView = chip;
                statusChipParams = params;
                mainHandler.postDelayed(this::hideStatusChip, Math.max(900L, durationMs));
            } catch (Exception ex) {
                prefs.edit()
                    .putString("statusOverlayError", describeError(ex))
                    .putLong("statusOverlayErrorAt", System.currentTimeMillis())
                    .apply();
                statusChipView = null;
                statusChipParams = null;
            }
        });
    }

    private void announceDriverStage(String code, String message, long durationMs, boolean force) {
        String sessionId = prefs.getString("gtoTripSessionId", "");
        String key = sessionId + "|" + (code == null ? "" : code);
        String previousKey = prefs.getString("driverStageShownKey", "");
        prefs.edit()
            .putString("driverStageCode", code == null ? "" : code)
            .putString("driverStageMessage", message == null ? "" : message)
            .putLong("driverStageAt", System.currentTimeMillis())
            .apply();
        if (force || !key.equals(previousKey)) {
            prefs.edit().putString("driverStageShownKey", key).apply();
            showStatusChip(message, durationMs);
        }
    }

    private String currentJourneyGuide(String state) {
        if (STATE_WAITING_FREIGHT.equals(state)) {
            return "Etapa 1 de 4 · Escolha o frete. A NVU identifica e valida os dados automaticamente.";
        }
        if (STATE_CONFIRMING_FREIGHT.equals(state)) {
            return "Etapa 1 de 4 · Frete selecionado. Aguarde a conferência automática dos dados.";
        }
        if (STATE_TRIP_IN_PROGRESS.equals(state)) {
            return "Etapa 2 de 4 · Faça a rota normalmente. Ao chegar ao destino, a NVU identificará a conclusão e registrará a viagem automaticamente.";
        }
        if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) {
            return "Etapa 3 de 4 · Entrega detectada. Toque em Receber; a NVU valida o recebimento automaticamente.";
        }
        if (STATE_RESULT_CONFIRMED.equals(state)) {
            String syncStatus = prefs.getString("gtoTripSyncStatus", "");
            if (GtoAutoTripSync.STATUS_SYNCED.equals(syncStatus)) {
                return "Concluído · Viagem registrada automaticamente na NVU.";
            }
            return "Etapa 4 de 4 · Recebimento confirmado. Enviando a viagem automaticamente para a NVU.";
        }
        if (STATE_REJECTED_BONUS.equals(state)) {
            return "Finalização interrompida · anúncio/bônus detectado; esta viagem não será registrada como normal.";
        }
        if (STATE_CANCELLED.equals(state)) {
            return "Viagem encerrada · a NVU limpará o frete anterior antes de uma nova seleção.";
        }
        return "Pronto · Inicie uma viagem e acompanhe cada etapa pelo botão NVU.";
    }

    private void hideStatusChip() {
        if (statusChipView != null && windowManager != null) {
            try {
                windowManager.removeView(statusChipView);
            } catch (Exception ignored) {}
        }
        statusChipView = null;
        statusChipParams = null;
    }

    private void requestManualFinishCapture() {
        if (!STATE_TRIP_IN_PROGRESS.equals(getTripState())) return;
        if (!projectionActive) {
            showStatusChip("Autorize a leitura da tela antes de finalizar.", 2400L);
            return;
        }

        closeMenu();
        manualFinishCapturePending = true;
        manualFinishRequestedAt = System.currentTimeMillis();
        manualFinishAttempts = 0;
        lastOcrAt = 0L;
        prefs.edit()
            .putString("completionStatus", "MANUAL_SCREENSHOT_REQUESTED")
            .putString("lastEvent", "Finalização solicitada; capturando a tela Concluído.")
            .apply();
        showStatusChip("Confirmando a conclusão da entrega…", 1500L);
    }

    private void failManualFinishCapture() {
        manualFinishCapturePending = false;
        manualFinishAttempts = 0;
        prefs.edit()
            .putString("completionStatus", "RESULT_SCREEN_NOT_FOUND")
            .putBoolean("resultConfirmationFallbackNeeded", true)
            .putString("lastEvent", "Tela Concluído não encontrada na captura solicitada.")
            .apply();
        showStatusChip("Não foi possível confirmar a conclusão. Mantenha a tela “Concluído” aberta e tente novamente.", 3200L);
    }

    private String menuTitle() {
        String company = prefs.getString("companyName", "");
        return company.isEmpty() ? "NVU · GTO" : "NVU · " + company;
    }

    private Button menuButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(makeRoundedBackground(Color.rgb(47, 52, 60), dp(10)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(42)
        );
        params.topMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private android.graphics.drawable.GradientDrawable makeRoundedBackground(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void reconcileSessionAfterGtoReturn(long absenceMs) {
        String state = getTripState();
        if (isRecoverableActiveState(state) && hasFreshDurableSession(state)) {
            prefs.edit()
                .putString("lastEvent", "Viagem GTO retomada após " + Math.max(1L, absenceMs / 1000L) + "s fora do simulador")
                .apply();
            if (STATE_TRIP_IN_PROGRESS.equals(state)) {
                announceDriverStage(
                    "CONTINUITY_CHECK",
                    "GTO retomado · verificando a continuidade da viagem. Se a lista de fretes reaparecer, a rota anterior será encerrada automaticamente.",
                    3600L,
                    false
                );
            }
            return;
        }
        resetForFreshGtoSession();
    }

    private boolean isRecoverableActiveState(String state) {
        return STATE_WAITING_FREIGHT.equals(state)
            || STATE_CONFIRMING_FREIGHT.equals(state)
            || STATE_TRIP_IN_PROGRESS.equals(state)
            || STATE_RESULT_DETECTED.equals(state)
            || STATE_AWAITING_BONUS.equals(state);
    }

    private boolean hasFreshDurableSession(String state) {
        String sessionId = prefs.getString("gtoTripSessionId", "");
        long startedAt = prefs.getLong("gtoTripSessionStartedAt", 0L);
        if (startedAt <= 0L) startedAt = prefs.getLong("tripStateChangedAt", 0L);
        long ageMs = startedAt <= 0L ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - startedAt);
        boolean requireFreight = STATE_TRIP_IN_PROGRESS.equals(state)
            || STATE_RESULT_DETECTED.equals(state)
            || STATE_AWAITING_BONUS.equals(state);
        return ageMs <= ACTIVE_SESSION_STALE_MS
            && GtoAutoTripSync.hasRecoverableSessionSnapshot(this, sessionId, requireFreight);
    }

    private boolean isOperationClosedForNewTrip() {
        String currentJobId = prefs.getString("jobId", "");
        String backendJobId = prefs.getString("gtoBackendJobId", "");
        if (!currentJobId.isEmpty()
            && currentJobId.equals(backendJobId)
            && prefs.getBoolean("gtoBackendJobClosed", false)) return true;

        String status = prefs.getString("jobStatus", prefs.getString("gtoJobStatus", ""))
            .trim().toLowerCase(Locale.ROOT);
        if ("awaiting_completion".equals(status) || "completed".equals(status)
            || "cancelled".equals(status) || "canceled".equals(status)) return true;

        int progress = Math.max(prefs.getInt("jobProgress", 0), prefs.getInt("gtoJobProgress", 0));
        int total = prefs.getInt("jobTotalDeliveries", 0);
        return total > 0 && progress >= total;
    }

    private void resetForFreshGtoSession() {
        String state = getTripState();
        if (STATE_IDLE.equals(state)) return;
        if (isRecoverableActiveState(state) && hasFreshDurableSession(state)) return;
        if (!preserveCompletedTripBeforeReset()) return;
        GtoAutoTripSync.discardSessionSnapshot(this, prefs.getString("gtoTripSessionId", ""));
        clearTripAnalysis();
        if (projectionActive) stopProjection();
        prefs.edit()
            .putString("tripState", STATE_IDLE)
            .putString("lastEvent", "Nova sessão GTO · pronta para iniciar viagem")
            .apply();
        if (menuView != null) mainHandler.post(this::refreshMenuContents);
    }

    private boolean preserveCompletedTripBeforeReset() {
        String state = getTripState();
        if (!STATE_RESULT_CONFIRMED.equals(state)) return true;
        String completion = prefs.getString("completionStatus", "");
        String syncStatus = prefs.getString("gtoTripSyncStatus", "");
        if (!"CONFIRMED_NORMAL".equals(completion) || GtoAutoTripSync.STATUS_SYNCED.equals(syncStatus)) return true;
        boolean queued = GtoAutoTripSync.enqueueConfirmedTrip(this, prefs, automaticTripSyncListener());
        if (!queued) {
            showStatusChip("Entrega concluída ainda não foi preservada na fila · nova viagem bloqueada.", 4200L);
            return false;
        }
        return true;
    }

    private void beginTrip() {
        beginTrip(true);
    }

    private void beginTrip(boolean announceStage) {
        String currentState = getTripState();
        if (isRecoverableActiveState(currentState) && hasFreshDurableSession(currentState)) {
            showStatusChip("Já existe uma viagem GTO em andamento.", 2800L);
            return;
        }
        if (STATE_RESULT_CONFIRMED.equals(currentState)
            && !GtoAutoTripSync.STATUS_SYNCED.equals(prefs.getString("gtoTripSyncStatus", ""))) {
            GtoAutoTripSync.enqueueConfirmedTrip(this, prefs, automaticTripSyncListener());
            showStatusChip("Aguarde a confirmação da entrega anterior antes de iniciar outra viagem.", 3600L);
            return;
        }
        if (isOperationClosedForNewTrip()) {
            prefs.edit().putString("lastEvent", "Nova viagem bloqueada: operação já concluída").apply();
            showStatusChip("Operação concluída. Inicie uma nova operação para continuar.", 3400L);
            return;
        }
        if (!preserveCompletedTripBeforeReset()) return;
        GtoAutoTripSync.discardSessionSnapshot(this, prefs.getString("gtoTripSessionId", ""));
        clearTripAnalysis();
        String sessionId = GtoAutoTripSync.newSessionId();
        long sessionStartedAt = System.currentTimeMillis();
        boolean sessionPersisted = prefs.edit()
            .putString("gtoTripSessionId", sessionId)
            .putLong("gtoTripSessionStartedAt", sessionStartedAt)
            .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_IN_PROGRESS)
            .putString("gtoTripIntegrityStatus", "CREATING_SNAPSHOT")
            .remove("gtoRegisteredTripId")
            .remove("gtoTripSyncError")
            .remove("gtoTripIntegrityError")
            .commit();
        if (!sessionPersisted || !GtoAutoTripSync.beginSessionSnapshot(this, prefs, sessionId)) {
            prefs.edit()
                .remove("gtoTripSessionId")
                .remove("gtoTripSessionStartedAt")
                .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_REJECTED)
                .putString("lastEvent", "Não foi possível iniciar: contexto da operação NVU incompleto ou não persistido")
                .apply();
            setTripState(STATE_IDLE, "Revise a operação NVU antes de iniciar a viagem GTO");
            showStatusChip("Não foi possível iniciar · revise motorista, empresa, contrato e operação.", 3600L);
            return;
        }
        setTripState(STATE_WAITING_FREIGHT, "Aguardando escolha do frete no GTO");

        if (announceStage) {
            announceDriverStage(
                "WAITING_FREIGHT",
                "Etapa 1/4 · Escolha um frete no GTO. A NVU identifica e confere os dados automaticamente.",
                3900L,
                false
            );
        }

        if (!projectionActive) {
            requestProjectionPermission();
            return;
        }

    }

    private void requestProjectionPermission() {
        if (projectionPermissionInFlight) return;

        projectionPermissionInFlight = true;
        suppressForegroundHideUntil = System.currentTimeMillis() + 12_000L;
        projectionStatus = "REQUESTING_PERMISSION";
        prefs.edit()
            .putString("projectionStatus", projectionStatus)
            .putString("screenState", "CAPTURE_PERMISSION_REQUIRED")
            .putString("lastEvent", "Aguardando autorização de leitura da tela")
            .putBoolean("projectionPermissionInFlight", true)
            .apply();

        // OEM compatibility hotfix: never create a transparent root task above GTO.
        // Some Android builds render that task as a gray screen or return to NVU while
        // permanently hiding the bubble. Instead, bring the existing NVU task forward,
        // request MediaProjection there, and automatically return to GTO after consent.
        hideOverlays();

        mainHandler.postDelayed(() -> {
            try {
                Intent permissionIntent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(MainActivity.EXTRA_REQUEST_GTO_PROJECTION, true)
                    .putExtra(MainActivity.EXTRA_RETURN_TO_GTO_AFTER_PROJECTION, true);
                startActivity(permissionIntent);
            } catch (Exception ex) {
                projectionPermissionInFlight = false;
                suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
                projectionStatus = "PERMISSION_ACTIVITY_FAILED";
                prefs.edit()
                    .putBoolean("projectionPermissionInFlight", false)
                    .putString("projectionStatus", projectionStatus)
                    .putString("lastEvent", "Falha ao abrir a NVU para autorizar leitura: " + ex.getClass().getSimpleName())
                    .apply();
                scheduleBubbleRestoreAfterPermission();
                showStatusChip("Não foi possível abrir a NVU para autorizar a leitura da tela.", 3000L);
            }
        }, 90L);
    }

    private void scheduleBubbleRestoreAfterPermission() {
        prefs.edit().putBoolean("projectionPermissionInFlight", false).apply();
        mainHandler.postDelayed(() -> restoreBubbleAfterPermission(false), 220L);
        mainHandler.postDelayed(() -> restoreBubbleAfterPermission(true), 700L);
        // Slower OEM launchers can take over a second to bring the GTO task back. Keep
        // bounded retries so the floating button cannot remain hidden after permission.
        mainHandler.postDelayed(() -> restoreBubbleAfterPermission(true), 1400L);
        mainHandler.postDelayed(() -> restoreBubbleAfterPermission(true), 2600L);
        mainHandler.postDelayed(() -> restoreBubbleAfterPermission(true), 4200L);
        mainHandler.postDelayed(() -> restoreBubbleAfterPermission(true), 6500L);
    }

    private void restoreBubbleAfterPermission(boolean refreshUsage) {
        if (!running || destroying || !prefs.getBoolean("enabled", false)) return;
        if (refreshUsage) refreshForegroundPackage();
        long now = System.currentTimeMillis();
        boolean recentGto = GTO_PACKAGE.equals(foregroundPackage)
            || gtoForeground
            || now - lastGtoForegroundEvidenceAt <= PERMISSION_RETURN_GRACE_MS;
        if (!recentGto) return;
        gtoForeground = true;
        prefs.edit().putBoolean("gtoForeground", true).apply();
        showBubbleIfAllowed();
        updateProjectionReauthButton();
    }

    private void cancelTrip() {
        GtoAutoTripSync.discardSessionSnapshot(this, prefs.getString("gtoTripSessionId", ""));
        clearTripAnalysis();
        setTripState(STATE_CANCELLED, "Viagem cancelada pelo motorista");
        showToast("Viagem cancelada.");
    }

    private void clearTripAnalysis() {
        deleteResultSnapshot();
        resultSnapshotRecoveryGeneration++;
        resultSnapshotRecoveryBusy.set(false);
        resultTouchFallbackRequired = false;
        resultTouchFallbackReady = false;
        resultTouchFallbackContinuityBroken = false;
        clearReplacementFreightCandidate();
        synchronized (freightOptions) {
            freightOptions.clear();
        }
        receiveRect = null;
        doubleValueRect = null;
        detectedResultValue = "";
        lastScreenState = "UNKNOWN";
        resultScreenLastSeenAt = 0L;
        resultActionTouchAt = 0L;
        resultExitSeenAt = 0L;
        gameplayFramesAfterResult = 0;
        manualFinishCapturePending = false;
        manualFinishRequestedAt = 0L;
        manualFinishAttempts = 0;
        automaticResultCandidateMisses = 0;
        lastActiveTripVisualProbeAt = 0L;
        lastActiveTripFallbackOcrAt = 0L;
        lastResultCandidateOcrAt = 0L;
        activeTripFreightListSeenSince = 0L;
        activeTripFreightListFrames = 0;
        outsideTouchCount = 0;
        lastOutsideTouchX = -1f;
        lastOutsideTouchY = -1f;
        lastOutsideAltX = -1f;
        lastOutsideAltY = -1f;
        lastOutsideTouchAt = 0L;
        lastFreightListSeenAt = 0L;
        freightListMissingSince = 0L;
        freightListMissingFrames = 0;
        freightListCycleSeen = false;
        freightListCycleClosed = false;
        freightListReopenPending = false;
        freightListCycleClosedAt = 0L;
        freightHistory.clear();
        freightHistoryPage = -1;
        freightHistoryUpdatedAt = 0L;
        pendingFreightSelection = null;
        pendingFreightTouchAt = 0L;
        pendingSelectionSource = "";
        visualSelectionUntil = 0L;
        lastVisualAnalysisAt = 0L;
        visualFreightSelection = null;
        visualSelectionConfidence = 0f;
        visualSelectionSource = "";
        preciseSelectedRow = -1;
        preciseSelectedTouchAt = 0L;
        clearSelectionProbe();
        preciseSelectionOcrGeneration++;
        analysisOcrGeneration++;
        preciseSelectionOcrBusy = false;
        fastPreviousFreightFrame = null;
        fastPreviousFreightSequence = 0L;
        synchronized (freightFrameLock) { fastFrameHistory.clear(); }
        fastLastSnapshotFrame = null;
        lastFastPanelSnapshotAt = 0L;
        // Keep this generation monotonic across sessions so a late OCR result from the
        // previous freight page can never become valid again after a reset.
        freightPageGeneration++;
        lastFreightPageOcrAt = 0L;
        lastFreightRuntimePersistAt = 0L;
        lastPersistedFreightRuntimeState = "";
        lastPersistedFreightCount = -1;
        fastTouchPulseActive = false;
        fastTouchPulseAt = 0L;
        fastTouchMarkerSequence = -1L;
        fastTouchMarkerQueued = false;
        fastTouchBaseline = null;
        fastTouchBaselineSequence = -1L;
        selectionCoordinator.reset();
        if (pendingSelectionTransaction != null) {
            pendingSelectionTransaction.close();
            pendingSelectionTransaction = null;
        }
        fastPendingSelectedRow = -1;
        fastPendingSelectedAt = 0L;
        fastPendingSelectedScore = 0f;
        fastPendingFromTouchPulse = false;
        fastMissingListFrames = 0;
        synchronized (freightFrameLock) {
            realtimeAcceptRects.clear();
            if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                latestFreightPanelFrame.recycle();
            }
            latestFreightPanelFrame = null;
            latestFreightPanelAt = 0L;
        }
        prefs.edit()
            .putBoolean("touchCaptureNeeded", false)
            .remove("selectedFreight")
            .remove("selectedFreightSummary")
            .remove("selectedOrigin")
            .remove("selectedDestination")
            .remove("selectedOriginCompany")
            .remove("selectedDestinationCompany")
            .remove("selectedCargo")
            .remove("selectedKm")
            .remove("selectedValue")
            .remove("pendingFreight")
            .remove("pendingSelectionSource")
            .remove("freightOptions")
            .remove("freightTextGeneration")
            .remove("freightTextAt")
            .putInt("freightCount", 0)
            .putInt("outsideTouchCount", 0)
            .remove("resultValue")
            .remove("resultAction")
            .remove("resultActionTouchAt")
            .remove("resultReceiveLatched")
            .remove("resultActionSource")
            .remove("finalGain")
            .remove("completionStatus")
            .remove("completionDetectedAt")
            .remove("gtoTripSessionId")
            .remove("gtoTripSessionStartedAt")
            .remove("gtoTripSyncStatus")
            .remove("gtoRegisteredTripId")
            .remove("gtoTripSyncError")
            .remove("gtoTripSyncLastErrorCode")
            .remove("gtoTripSyncLastAttemptAt")
            .remove("gtoTripQueueCleanupPending")
            .remove("gtoTripIntegrityStatus")
            .remove("gtoTripIntegrityError")
            .remove("driverStageCode")
            .remove("driverStageMessage")
            .remove("driverStageAt")
            .remove("driverStageShownKey")
            .remove("activeTripFreightListEvidenceFrames")
            .remove("activeTripFreightListEvidenceSince")
            .remove("replacementFreightCandidateArmed")
            .remove("resultVisualCandidateAt")
            .remove("resultConfirmationFallbackNeeded")
            .remove("resultTouchFallbackRequired")
            .remove("resultTouchFallbackReady")
            .remove("resultTouchFallbackContinuityBroken")
            .remove("resultTouchFallbackReason")
            .remove("resultSnapshotPath")
            .remove("resultSnapshotAt")
            .remove("resultSnapshotError")
            .remove("resultSnapshotErrorAt")
            .putString("screenState", "UNKNOWN")
            .apply();
    }

    private void openOperationalPanel() {
        Intent intent = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_NATIVE_ROUTE, "/driver/profile")
            .putExtra(MainActivity.EXTRA_NATIVE_PROFILE_TAB, "dashboard");
        startActivity(intent);
    }

    private void closeMenu() {
        if (menuView != null && windowManager != null) {
            try {
                windowManager.removeView(menuView);
            } catch (Exception ignored) {}
        }
        menuView = null;
        menuParams = null;
    }

    private void hideOverlays() {
        closeMenu();
        hideStatusChip();
        hideProjectionReauthButton();
        hideFreightTouchPulseSensor();
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
        }
        bubbleView = null;
        bubbleParams = null;
        if (prefs != null) prefs.edit().putBoolean("overlayVisible", false).apply();
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void startProjection(int resultCode, Intent resultData) {
        stopProjection();

        try {
            startForegroundForTypes(true);
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) throw new IllegalStateException("MediaProjectionManager indisponível");

            final long generation = ++projectionGeneration;
            final MediaProjection projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("MediaProjection não autorizado");
            mediaProjection = projection;

            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onCapturedContentResize(int width, int height) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return;
                    if (generation != projectionGeneration || mediaProjection != projection) return;
                    pendingCapturedWidth = width;
                    pendingCapturedHeight = height;
                    resizeProjectionSurface(width, height);
                }

                @Override
                public void onStop() {
                    mainHandler.post(() -> {
                        // stop() from the previous MediaProjection can arrive after a new
                        // permission token is already active. Never let that stale callback
                        // tear down the new ImageReader/VirtualDisplay.
                        if (generation != projectionGeneration || mediaProjection != projection) return;
                        projectionGeneration++;
                        projectionActive = false;
                        projectionStatus = "STOPPED";
                        prefs.edit()
                            .putBoolean("projectionActive", false)
                            .putString("projectionStatus", projectionStatus)
                            .putString("screenState", "CAPTURE_STOPPED")
                            .putBoolean("projectionReauthRequired", true)
                            .putBoolean("projectionReauthNoticeShown", false)
                            .putBoolean("touchCaptureNeeded", false)
                            .putString("lastEvent", "Leitura da tela foi encerrada pelo Android")
                            .apply();
                        releaseCaptureResources(false);
                        updateFreightTouchPulseSensor();
                        if (!destroying && running) {
                            try {
                                startForegroundForTypes(false);
                            } catch (Exception ex) {
                                prefs.edit()
                                    .putString("startError", describeError(ex))
                                    .putString("lastEvent", "Falha ao manter serviço após encerramento da captura")
                                    .apply();
                            }
                            updateNotification();
                            if (gtoForeground) {
                                maybeNotifyProjectionReauthorization();
                                updateProjectionReauthButton();
                            }
                        }
                    });
                }
            }, mainHandler);

            Rect projectionBounds;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
                WindowMetrics maximumMetrics = windowManager.getMaximumWindowMetrics();
                projectionBounds = maximumMetrics.getBounds();
            } else {
                DisplayMetrics metrics = realDisplayMetrics();
                projectionBounds = new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
            }
            captureWidth = Math.max(1, projectionBounds.width());
            captureHeight = Math.max(1, projectionBounds.height());
            captureDensityDpi = getResources().getConfiguration().densityDpi;
            int density = captureDensityDpi;

            captureThread = new HandlerThread("NVU-GTO-Capture");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());

            imageReader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                3
            );
            imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

            virtualDisplay = projection.createVirtualDisplay(
                "NVU-GTO-Observer",
                captureWidth,
                captureHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
            );

            projectionActive = true;
            // R3.16: restarting MediaProjection is a technical capture transition, not
            // a GTO navigation event. Forget only transient absence counters; preserve
            // the logical freight-list cycle and trip session.
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
            lastFreightListSeenAt = 0L;
            hideProjectionReauthButton();
            if (pendingCapturedWidth > 0 && pendingCapturedHeight > 0
                && (pendingCapturedWidth != captureWidth || pendingCapturedHeight != captureHeight)) {
                resizeProjectionSurface(pendingCapturedWidth, pendingCapturedHeight);
            }
            projectionStatus = "ACTIVE";
            prefs.edit()
                .putBoolean("projectionActive", true)
                .putString("projectionStatus", projectionStatus)
                .putInt("captureWidth", captureWidth)
                .putInt("captureHeight", captureHeight)
                .putInt("captureDensityDpi", captureDensityDpi)
                .putInt("captureAndroidApi", Build.VERSION.SDK_INT)
                .putString("screenState", "CAPTURE_READY")
                .remove("projectionError")
                .remove("projectionReauthRequired")
                .remove("projectionReauthNoticeShown")
                .putString("lastEvent", "Leitura local da tela autorizada")
                .apply();
            updateNotification();

        } catch (Exception ex) {
            projectionActive = false;
            projectionStatus = "START_FAILED";
            String detail = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
            projectionGeneration++;
            prefs.edit()
                .putBoolean("projectionActive", false)
                .putString("projectionStatus", projectionStatus)
                .putString("screenState", "CAPTURE_START_FAILED")
                .putString("projectionError", detail)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putBoolean("touchCaptureNeeded", false)
                .putString("lastEvent", "Falha ao iniciar leitura da tela: " + detail)
                .apply();
            releaseCaptureResources(true);
            updateFreightTouchPulseSensor();
            try {
                startForegroundForTypes(false);
            } catch (Exception foregroundEx) {
                prefs.edit()
                    .putString("startError", describeError(foregroundEx))
                    .putString("lastEvent", "Falha ao restaurar serviço depois de erro na leitura da tela")
                    .apply();
            }
            updateNotification();
            if (gtoForeground) updateProjectionReauthButton();
            showToast("Falha na leitura da tela. Toque em Reativar ao lado da bolinha NVU.");
        }
    }

    private void resizeProjectionSurface(int width, int height) {
        if (width <= 0 || height <= 0 || width == captureWidth && height == captureHeight) return;
        Handler handler = captureHandler;
        VirtualDisplay expectedDisplay = virtualDisplay;
        long expectedGeneration = projectionGeneration;
        if (handler == null || expectedDisplay == null) return;
        handler.post(() -> {
            // A resize callback from an older MediaProjection session can remain queued
            // while Android grants a replacement token. Never let that stale runnable
            // mutate the new session's global ImageReader/VirtualDisplay.
            if (!projectionActive
                || expectedGeneration != projectionGeneration
                || captureHandler != handler
                || virtualDisplay != expectedDisplay) return;
            ImageReader replacement = null;
            try {
                replacement = ImageReader.newInstance(
                    width,
                    height,
                    PixelFormat.RGBA_8888,
                    3
                );
                replacement.setOnImageAvailableListener(this::onImageAvailable, handler);
                if (!projectionActive
                    || expectedGeneration != projectionGeneration
                    || captureHandler != handler
                    || virtualDisplay != expectedDisplay) {
                    try { replacement.close(); } catch (Exception ignored) {}
                    return;
                }
                expectedDisplay.resize(width, height, Math.max(1, captureDensityDpi));
                expectedDisplay.setSurface(replacement.getSurface());
                if (!projectionActive
                    || expectedGeneration != projectionGeneration
                    || captureHandler != handler
                    || virtualDisplay != expectedDisplay) {
                    try { replacement.close(); } catch (Exception ignored) {}
                    return;
                }
                ImageReader previous = imageReader;
                imageReader = replacement;
                replacement = null;
                captureWidth = width;
                captureHeight = height;
                if (previous != null) {
                    try { previous.close(); } catch (Exception ignored) {}
                }
                prefs.edit()
                    .putInt("captureWidth", captureWidth)
                    .putInt("captureHeight", captureHeight)
                    .putString("lastEvent", "Captura ajustada ao GTO: " + width + "x" + height)
                    .apply();
            } catch (Exception ex) {
                if (replacement != null) {
                    try { replacement.close(); } catch (Exception ignored) {}
                }
                prefs.edit()
                    .putString("projectionError", "Resize: " + describeError(ex))
                    .putLong("projectionErrorAt", System.currentTimeMillis())
                    .putString("lastEvent", "Falha ao ajustar captura ao tamanho atual do GTO")
                    .apply();
            }
        });
    }

    private void onImageAvailable(ImageReader reader) {
        // Freight selection is the only stage where dropping an intermediate frame can
        // lose information. Consume those frames in order with a tiny OCR-free detector.
        // All other states keep acquireLatestImage() so background work never builds lag.
        if (STATE_WAITING_FREIGHT.equals(getTripState())) {
            onFreightFrameAvailable(reader);
            return;
        }

        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;

            String state = getTripState();
            if (!gtoForeground) return;

            long now = System.currentTimeMillis();
            boolean tripResultCandidate = false;
            boolean activeSessionVisualState = STATE_TRIP_IN_PROGRESS.equals(state)
                || STATE_RESULT_DETECTED.equals(state)
                || STATE_AWAITING_BONUS.equals(state);

            // IMPORTANT: freight-list detection is intentionally independent of the
            // current trip state. The GTO can already be showing the jobs list before
            // the driver opens NVU or before the floating button is pressed. The old
            // flow discarded those frames because IDLE was not an analyzed state.
            //
            // The detector is OCR-free and cheap. Once a real freight list is visible,
            // handleActiveTripFreightListEvidence() bootstraps WAITING_FREIGHT and
            // preserves the exact page snapshot for row-level selection.
            if (isReplaceableActiveSessionState(state)
                && now - lastActiveTripVisualProbeAt >= ACTIVE_TRIP_VISUAL_PROBE_MS) {
                lastActiveTripVisualProbeAt = now;
                GtoFastVisualDetector.Frame activeFrame = fastVisualDetector.analyze(
                    image, captureWidth, captureHeight, now
                );
                if (handleActiveTripFreightListEvidence(image, activeFrame, now)) return;

                if (STATE_TRIP_IN_PROGRESS.equals(state)) {
                    // Result OCR is woken by a tiny pixel sampler instead of running for the
                    // whole route. parseResultScreen() remains the authority afterwards.
                    tripResultCandidate = resultVisualGate.looksLikeResultDialog(
                        image, captureWidth, captureHeight
                    );
                    if (tripResultCandidate) {
                        prefs.edit()
                            .putString("screenState", "RESULT_CANDIDATE")
                            .putLong("resultVisualCandidateAt", now)
                            .apply();
                    }
                }
            }

            long interval = analysisIntervalForState(state);
            boolean structureDue = STATE_WAITING_FREIGHT.equals(state)
                && (selectionProbeActive || now - lastStructureAt >= STRUCTURE_INTERVAL_MS);
            boolean visualDue = STATE_WAITING_FREIGHT.equals(state)
                && now <= visualSelectionUntil
                && now - lastVisualAnalysisAt >= 30L;
            boolean manualFinishReady = !STATE_TRIP_IN_PROGRESS.equals(state)
                || !manualFinishCapturePending
                || now - manualFinishRequestedAt >= MANUAL_FINISH_MIN_DELAY_MS;
            boolean tripFallbackOcrDue = STATE_TRIP_IN_PROGRESS.equals(state)
                && !manualFinishCapturePending
                && now - lastActiveTripFallbackOcrAt >= ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS;
            boolean tripCandidateOcrDue = STATE_TRIP_IN_PROGRESS.equals(state)
                && !manualFinishCapturePending
                && tripResultCandidate
                && now - lastResultCandidateOcrAt >= ACTIVE_TRIP_RESULT_CANDIDATE_OCR_MS;
            boolean ocrDue = manualFinishReady && (
                (STATE_TRIP_IN_PROGRESS.equals(state)
                    ? (manualFinishCapturePending
                        ? now - lastOcrAt >= interval
                        : tripCandidateOcrDue || tripFallbackOcrDue)
                    : now - lastOcrAt >= interval)
            );
            // During the sub-second selection probe we prioritize frame capture over OCR.
            // The selected row is OCRed from the frozen pre-touch snapshot afterwards.
            boolean ocrSlot = !selectionProbeActive && ocrDue && ocrBusy.compareAndSet(false, true);

            if (!structureDue && !visualDue && !ocrSlot) return;

            Bitmap source = imageToBitmap(image, captureWidth, captureHeight);
            if (source == null) {
                if (ocrSlot) ocrBusy.set(false);
                return;
            }

            if (structureDue) {
                lastStructureAt = now;
                updateRealtimeFreightStructure(source, now);
            }

            if (visualDue) {
                lastVisualAnalysisAt = now;
                analyzeVisualSelectionFrame(source);
            }

            if (!ocrSlot) {
                source.recycle();
                return;
            }

            lastOcrAt = now;
            if (STATE_TRIP_IN_PROGRESS.equals(state) && !manualFinishCapturePending) {
                if (tripResultCandidate) lastResultCandidateOcrAt = now;
                if (tripFallbackOcrDue) lastActiveTripFallbackOcrAt = now;
            }

            // Freight text is small and always lives on the right side of the GTO UI.
            // OCRing the whole 2712px display and shrinking it to ~1280px was the main
            // source of fake words and wrong row/value associations. While choosing a
            // freight we crop the native-resolution jobs panel first, preserving the
            // original glyph detail while reducing the amount of pixels ML Kit must read.
            Bitmap analysisBitmap;
            int analysisOffsetX = 0;
            int analysisOffsetY = 0;
            int maxWidth = MAX_ANALYSIS_WIDTH;
            if (STATE_WAITING_FREIGHT.equals(state)) {
                // R3.4: use the detected Aceitar column when available. If the fast
                // detector has not locked a column yet, deliberately use a wider right
                // half instead of the old fixed 61.5% crop so different GTO UI scales
                // still have a reliable OCR fallback.
                analysisOffsetX = freightOcrLeftForCurrentLayout(source.getWidth());
                int roiWidth = source.getWidth() - analysisOffsetX;
                analysisBitmap = Bitmap.createBitmap(source, analysisOffsetX, 0, roiWidth, source.getHeight());
                maxWidth = MAX_FREIGHT_ANALYSIS_WIDTH;
            } else if (STATE_TRIP_IN_PROGRESS.equals(state)) {
                // The completion dialog is central and comparatively small. Reading the
                // central area at native resolution makes result detection faster without
                // sacrificing the exact "Valor a receber" amount.
                int left = clamp(Math.round(source.getWidth() * 0.245f), 0, source.getWidth() - 2);
                int top = clamp(Math.round(source.getHeight() * 0.12f), 0, source.getHeight() - 2);
                int right = clamp(Math.round(source.getWidth() * 0.755f), left + 1, source.getWidth());
                int bottom = clamp(Math.round(source.getHeight() * 0.84f), top + 1, source.getHeight());
                analysisOffsetX = left;
                analysisOffsetY = top;
                analysisBitmap = Bitmap.createBitmap(source, left, top, right - left, bottom - top);
            } else {
                // After the result screen we need the whole display to distinguish normal
                // gameplay from an advertisement/reward flow.
                analysisBitmap = Bitmap.createBitmap(source);
            }

            float scale = 1f;
            if (analysisBitmap.getWidth() > maxWidth) {
                scale = maxWidth / (float) analysisBitmap.getWidth();
                int scaledHeight = Math.max(1, Math.round(analysisBitmap.getHeight() * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(analysisBitmap, maxWidth, scaledHeight, true);
                analysisBitmap.recycle();
                analysisBitmap = scaled;
            }

            final Bitmap bitmapForOcr = analysisBitmap;
            final Bitmap fullFrameForGeometry = source;
            final float analysisScale = scale;
            final int offsetX = analysisOffsetX;
            final int offsetY = analysisOffsetY;
            final long scheduledOcrGeneration = analysisOcrGeneration;
            final String scheduledOcrSessionId = prefs.getString("gtoTripSessionId", "");
            InputImage input = InputImage.fromBitmap(bitmapForOcr, 0);
            textRecognizer.process(input)
                .addOnSuccessListener(text -> {
                    if (!isCurrentAnalysisOcr(scheduledOcrGeneration, scheduledOcrSessionId)) return;
                    handleOcrResult(text, analysisScale, offsetX, offsetY, fullFrameForGeometry);
                })
                .addOnFailureListener(error -> {
                    if (!isCurrentAnalysisOcr(scheduledOcrGeneration, scheduledOcrSessionId)) return;
                    prefs.edit()
                        .putString("lastEvent", "OCR local falhou: " + error.getClass().getSimpleName())
                        .apply();
                })
                .addOnCompleteListener(task -> {
                    if (!bitmapForOcr.isRecycled()) bitmapForOcr.recycle();
                    if (fullFrameForGeometry != bitmapForOcr && !fullFrameForGeometry.isRecycled()) fullFrameForGeometry.recycle();
                    ocrBusy.set(false);
                });
        } catch (Exception ex) {
            ocrBusy.set(false);
            prefs.edit().putString("lastEvent", "Falha ao processar quadro: " + ex.getClass().getSimpleName()).apply();
        } finally {
            if (image != null) image.close();
        }
    }

    private boolean isCurrentAnalysisOcr(long generation, String sessionId) {
        if (generation != analysisOcrGeneration) return false;
        String currentSessionId = prefs == null ? "" : prefs.getString("gtoTripSessionId", "");
        if (!(sessionId == null ? "" : sessionId).equals(currentSessionId)) return false;
        String state = getTripState();
        return STATE_TRIP_IN_PROGRESS.equals(state)
            || STATE_RESULT_DETECTED.equals(state)
            || STATE_AWAITING_BONUS.equals(state);
    }

    private boolean isCurrentPreciseSelectionOcr(long generation, String sessionId) {
        if (generation != preciseSelectionOcrGeneration) return false;
        String currentSessionId = prefs == null ? "" : prefs.getString("gtoTripSessionId", "");
        return (sessionId == null ? "" : sessionId).equals(currentSessionId);
    }

    private void recordFastFreightFrame(GtoFastVisualDetector.Frame frame, long sequence) {
        if (frame == null || !frame.hasFreightList()) return;
        synchronized (freightFrameLock) {
            fastFrameHistory.add(new SequencedFastFrame(sequence, frame));
            while (fastFrameHistory.size() > FAST_FRAME_HISTORY_LIMIT) fastFrameHistory.remove(0);
        }
    }

    private SequencedFastFrame fastBaselineBeforeSequence(long markerSequence) {
        synchronized (freightFrameLock) {
            SequencedFastFrame newest = null;
            for (int i = fastFrameHistory.size() - 1; i >= 0; i--) {
                SequencedFastFrame record = fastFrameHistory.get(i);
                if (record == null || record.frame == null || !record.frame.hasFreightList()) continue;
                if (record.sequence <= markerSequence) {
                    newest = record;
                    break;
                }
            }
            if (newest == null) return null;

            // A pressed frame can be delivered just before the main-thread ACTION_OUTSIDE
            // callback posts its marker. Choose the cleanest same-page baseline from the
            // recent history, not blindly the newest frame. Normal Aceitar buttons have
            // the strongest/most uniform orange fill; the pressed row is the outlier.
            SequencedFastFrame best = newest;
            float bestQuality = fastBaselineQuality(newest.frame);
            int considered = 0;
            for (int i = fastFrameHistory.size() - 1; i >= 0 && considered < 6; i--) {
                SequencedFastFrame record = fastFrameHistory.get(i);
                if (record == null || record.frame == null || !record.frame.hasFreightList()) continue;
                if (record.sequence > markerSequence) continue;
                if (!fastVisualDetector.samePage(newest.frame, record.frame)) continue;
                considered++;
                float quality = fastBaselineQuality(record.frame);
                if (quality > bestQuality + 0.004f
                    || Math.abs(quality - bestQuality) <= 0.004f && record.sequence > best.sequence) {
                    best = record;
                    bestQuality = quality;
                }
            }
            return best;
        }
    }

    private float fastBaselineQuality(GtoFastVisualDetector.Frame frame) {
        if (frame == null || frame.orangeRatios == null || frame.orangeRatios.length == 0) return 0f;
        float sum = 0f;
        float min = 1f;
        for (float value : frame.orangeRatios) {
            sum += value;
            min = Math.min(min, value);
        }
        float mean = sum / frame.orangeRatios.length;
        return mean * 0.65f + min * 0.35f;
    }

    private void armFastTouchPulseOnCaptureThread() {
        if (!STATE_WAITING_FREIGHT.equals(getTripState())) return;

        long markerSequence = selectionCoordinator.markTouch();
        fastTouchPulseActive = true;
        fastTouchPulseAt = System.currentTimeMillis();
        fastTouchMarkerSequence = markerSequence;
        fastPendingSelectedRow = -1;
        fastPendingSelectedAt = 0L;
        fastPendingSelectedScore = 0f;
        fastPendingFromTouchPulse = false;
        fastMissingListFrames = 0;

        SequencedFastFrame baselineRecord = fastBaselineBeforeSequence(markerSequence);
        if (baselineRecord == null && fastPreviousFreightFrame != null && fastPreviousFreightFrame.hasFreightList()) {
            baselineRecord = new SequencedFastFrame(fastPreviousFreightSequence, fastPreviousFreightFrame);
        }
        fastTouchBaseline = baselineRecord == null ? null : baselineRecord.frame;
        fastTouchBaselineSequence = baselineRecord == null ? -1L : baselineRecord.sequence;

        synchronized (freightFrameLock) {
            recycleFrozenSelectionPanel();
            frozenSelectionButtons.clear();
            if (fastTouchBaseline != null) {
                boolean snapshotMatches = fastLastSnapshotFrame != null
                    && fastVisualDetector.samePage(fastLastSnapshotFrame, fastTouchBaseline);
                if (snapshotMatches && latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                    frozenSelectionPanelFrame = latestFreightPanelFrame.copy(Bitmap.Config.ARGB_8888, false);
                    frozenSelectionPanelOffsetX = latestFreightPanelOffsetX;
                }
                for (Rect rect : fastTouchBaseline.buttons) frozenSelectionButtons.add(new Rect(rect));
            }
        }

        prefs.edit()
            .putLong("freightTouchPulseAt", fastTouchPulseAt)
            .putLong("freightTouchSequence", markerSequence)
            .putString("pendingSelectionSource", "touch-marker")
            .putString("lastEvent", "Toque detectado · correlacionando a sequência de quadros")
            .apply();
    }

    private void clearFastTouchPulse(boolean preserveFrozenSnapshot) {
        fastTouchPulseActive = false;
        fastTouchPulseAt = 0L;
        fastTouchMarkerSequence = -1L;
        fastTouchBaseline = null;
        fastTouchBaselineSequence = -1L;
        selectionCoordinator.finishCriticalWindow();
        if (!preserveFrozenSnapshot && !preciseSelectionOcrBusy && pendingSelectionTransaction == null) {
            synchronized (freightFrameLock) {
                frozenSelectionButtons.clear();
                recycleFrozenSelectionPanel();
            }
        }
    }

    private GtoFastVisualDetector.PressCandidate retrospectiveFastTouchCandidate() {
        if (!fastTouchPulseActive || fastTouchBaseline == null || fastTouchMarkerSequence < 0L) return null;
        synchronized (freightFrameLock) {
            GtoFastVisualDetector.PressCandidate best = null;
            for (SequencedFastFrame record : fastFrameHistory) {
                if (record == null || record.frame == null || !record.frame.hasFreightList()) continue;
                if (record.sequence <= fastTouchMarkerSequence) continue;
                GtoFastVisualDetector.PressCandidate candidate =
                    fastVisualDetector.detectPressedRowAfterTouch(fastTouchBaseline, record.frame, captureHeight);
                if (candidate == null) continue;
                float candidateStrength = candidate.score + Math.max(0f, candidate.margin) * 0.75f;
                float bestStrength = best == null ? -1f : best.score + Math.max(0f, best.margin) * 0.75f;
                if (candidateStrength > bestStrength) best = candidate;
            }
            return best;
        }
    }

    private void cacheFastFreightPanel(Image image, GtoFastVisualDetector.Frame current, long now) {
        if (image == null || current == null || !current.hasFreightList()) return;
        boolean noSnapshot = latestFreightPanelFrame == null || latestFreightPanelFrame.isRecycled() || fastLastSnapshotFrame == null;
        boolean pageChanged = !noSnapshot && !fastVisualDetector.samePage(fastLastSnapshotFrame, current);
        boolean refresh = now - lastFastPanelSnapshotAt >= 900L;
        if (!noSnapshot && !pageChanged && !refresh) return;

        // A button press does not alter panelSignature (the button strip is excluded),
        // so pageChanged here means a real GTO page change rather than a tap animation.
        Bitmap full = imageToBitmap(image, captureWidth, captureHeight);
        if (full == null) return;
        int left = freightPanelLeftForButtons(full.getWidth(), current.buttons);
        Bitmap panel = Bitmap.createBitmap(full, left, 0, full.getWidth() - left, full.getHeight());
        full.recycle();

        synchronized (freightFrameLock) {
            if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) latestFreightPanelFrame.recycle();
            latestFreightPanelFrame = panel;
            latestFreightPanelOffsetX = left;
            latestFreightPanelAt = now;
            realtimeAcceptRects.clear();
            for (Rect rect : current.buttons) realtimeAcceptRects.add(new Rect(rect));
        }
        fastLastSnapshotFrame = current;
        lastFastPanelSnapshotAt = now;
        if (noSnapshot || pageChanged) {
            freightPageGeneration++;
            if (pageChanged) {
                // Critical correctness rule: text from page N must never be used as the
                // fallback for a fast tap on page N+1 while the new OCR is still running.
                synchronized (freightOptions) { freightOptions.clear(); }
                prefs.edit()
                    .remove("freightOptions")
                    .remove("freightTextGeneration")
                    .remove("freightTextAt")
                    .putString("lastEvent", "Nova página de fretes detectada · cache textual anterior descartado")
                    .apply();
            }
        }
        if (freightPageGeneration <= 0L) freightPageGeneration = 1L;
        boolean textRefreshDue = noSnapshot || pageChanged
            || now - lastFreightPageOcrAt >= FREIGHT_PAGE_OCR_REFRESH_MS;
        if (textRefreshDue) {
            scheduleFreightPageOcr(freightPageGeneration, panel, left, current.buttons, now);
        }
    }

    private void scheduleFreightPageOcr(
        long generation,
        Bitmap panelSnapshot,
        int panelOffsetX,
        List<Rect> buttons,
        long now
    ) {
        if (panelSnapshot == null || panelSnapshot.isRecycled() || textRecognizer == null) return;
        if (now - lastFreightPageOcrAt < 220L || !ocrBusy.compareAndSet(false, true)) return;
        lastFreightPageOcrAt = now;

        Bitmap ocrCopy = panelSnapshot.copy(Bitmap.Config.ARGB_8888, false);
        if (ocrCopy == null) {
            ocrBusy.set(false);
            return;
        }
        List<Rect> buttonCopy = new ArrayList<>();
        for (Rect rect : buttons) buttonCopy.add(new Rect(rect));
        buttonCopy.sort(Comparator.comparingInt(Rect::centerY));

        textRecognizer.process(InputImage.fromBitmap(ocrCopy, 0))
            .addOnSuccessListener(text -> {
                if (!STATE_WAITING_FREIGHT.equals(getTripState()) || generation != freightPageGeneration) return;
                List<OcrLine> lines = new ArrayList<>();
                for (Text.TextBlock block : text.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        Rect box = line.getBoundingBox();
                        if (box == null || line.getText() == null) continue;
                        String value = line.getText().trim();
                        if (value.isEmpty()) continue;
                        Rect mapped = new Rect(
                            panelOffsetX + box.left,
                            box.top,
                            panelOffsetX + box.right,
                            box.bottom
                        );
                        lines.add(new OcrLine(value, mapped, line.getConfidence()));
                    }
                }
                List<FreightOption> parsed = parseFreightOptions(lines, buttonCopy);
                if (parsed.isEmpty()) return;
                for (FreightOption option : parsed) {
                    option.consensusFrames = Math.max(1, option.consensusFrames);
                }
                synchronized (freightOptions) {
                    freightOptions.clear();
                    for (FreightOption option : parsed) freightOptions.add(copyFreightOption(option));
                }
                prefs.edit()
                    .putString("freightOptions", freightOptionsToJson(parsed))
                    .putLong("freightTextGeneration", generation)
                    .putLong("freightTextAt", System.currentTimeMillis())
                    .apply();
            })
            .addOnFailureListener(error -> prefs.edit()
                .putString("lastFreightTextError", error.getClass().getSimpleName())
                .apply())
            .addOnCompleteListener(task -> {
                if (!ocrCopy.isRecycled()) ocrCopy.recycle();
                ocrBusy.set(false);
            });
    }

    private void onFreightFrameAvailable(ImageReader reader) {
        Image image = null;
        try {
            // Stay on the newest GTO frame during normal list browsing. Once the touch
            // marker reaches this same capture thread, preserve the short post-touch
            // sequence in order so the pressed-button frame cannot be skipped.
            image = selectionCoordinator.isCriticalWindow()
                ? reader.acquireNextImage()
                : reader.acquireLatestImage();
            if (image == null) return;
            if (!gtoForeground || !STATE_WAITING_FREIGHT.equals(getTripState())) return;

            long sequence = selectionCoordinator.onFrameProcessed();
            long now = System.currentTimeMillis();
            GtoFastVisualDetector.Frame current = fastVisualDetector.analyze(image, captureWidth, captureHeight, now);
            boolean hasList = current != null && current.hasFreightList();

            if (hasList) {
                lastFreightListSeenAt = now;
                fastMissingListFrames = 0;
                lastScreenState = "FREIGHT_LIST";
                persistFreightRuntimeStatus("FREIGHT_LIST", current.buttons.size(), now, sequence);

                recordFastFreightFrame(current, sequence);
                cacheFastFreightPanel(image, current, now);

                // A touch can arrive before the very first structural pass. Because the
                // touch marker is serialized on this Handler, any pre-touch callbacks
                // already queued have run first. If no baseline exists even now, capture
                // this first visible list frame and continue without discarding the tap.
                if (fastTouchPulseActive && fastTouchBaseline == null) {
                    fastTouchBaseline = current;
                    fastTouchBaselineSequence = sequence;
                    synchronized (freightFrameLock) {
                        if ((frozenSelectionPanelFrame == null || frozenSelectionPanelFrame.isRecycled())
                            && latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                            frozenSelectionPanelFrame = latestFreightPanelFrame.copy(Bitmap.Config.ARGB_8888, false);
                            frozenSelectionPanelOffsetX = latestFreightPanelOffsetX;
                        }
                        frozenSelectionButtons.clear();
                        for (Rect rect : current.buttons) frozenSelectionButtons.add(new Rect(rect));
                    }
                }

                GtoFastVisualDetector.PressCandidate candidate = null;
                boolean postTouchFrame = fastTouchPulseActive && selectionCoordinator.isPostTouch(sequence);

                if (postTouchFrame && fastTouchBaseline != null) {
                    // Page arrows also emit ACTION_OUTSIDE. A real page change modifies
                    // the cargo panel, while pressing Aceitar changes one button first.
                    float pageDistance = fastVisualDetector.pageDistance(fastTouchBaseline, current);
                    if (pageDistance >= 0.028f) {
                        // The touch was a page arrow/navigation. Release the critical
                        // window immediately so an instant Aceitar tap on the new page
                        // can create its own marker instead of being swallowed.
                        clearFastTouchPulse(false);
                    } else {
                        candidate = fastVisualDetector.detectPressedRowAfterTouch(
                            fastTouchBaseline, current, captureHeight
                        );
                        if (candidate == null && fastTouchBaselineSequence >= fastTouchMarkerSequence) {
                            candidate = fastVisualDetector.detectPressedRowFromSingleFrame(current);
                        }
                    }
                }

                // Passive fallback remains available for OEMs that suppress ACTION_OUTSIDE,
                // but it is deliberately stricter and still requires the list to close.
                if (candidate == null && !fastTouchPulseActive
                    && fastPreviousFreightFrame != null && fastPreviousFreightFrame.hasFreightList()) {
                    candidate = fastVisualDetector.detectPressedRow(fastPreviousFreightFrame, current, captureHeight);
                    if (candidate == null) {
                        candidate = fastVisualDetector.detectTemporarilyMissingPressedRow(
                            fastPreviousFreightFrame, current, captureHeight
                        );
                    }
                }

                if (candidate != null && !fastTouchPulseActive) {
                    // Without the ACTION_OUTSIDE timestamp we have less independent
                    // evidence. Require a stronger isolated visual change so an OEM frame
                    // animation cannot silently select the neighboring row.
                    boolean strongPassive = candidate.score >= 0.052f && candidate.margin >= 0.017f;
                    boolean missingButtonSignal = candidate.score >= 0.10f && candidate.margin >= 0.08f;
                    if (!strongPassive && !missingButtonSignal) candidate = null;
                }

                if (candidate != null && fastPendingSelectedRow < 0) {
                    fastPendingFromTouchPulse = fastTouchPulseActive;
                    GtoFastVisualDetector.Frame baseline = fastTouchPulseActive && fastTouchBaseline != null
                        ? fastTouchBaseline
                        : fastPreviousFreightFrame;
                    armFastVisualSelection(candidate, image, baseline, current, now);
                }

                if (fastTouchPulseActive && fastPendingSelectedRow < 0
                    && now - fastTouchPulseAt > CRITICAL_TOUCH_WINDOW_MS) {
                    clearFastTouchPulse(false);
                }

                if (fastPendingSelectedRow >= 0
                    && now - fastPendingSelectedAt > FAST_SELECTION_FALSE_POSITIVE_TIMEOUT_MS) {
                    clearFastPendingSelection();
                }

                fastPreviousFreightFrame = current;
                fastPreviousFreightSequence = sequence;
                return;
            }

            boolean previousWasList = fastPreviousFreightFrame != null && fastPreviousFreightFrame.hasFreightList();
            if (previousWasList || now - lastFreightListSeenAt <= 300L) {
                fastMissingListFrames++;

                if (fastPendingSelectedRow < 0 && fastTouchPulseActive && fastTouchBaseline != null
                    && selectionCoordinator.isPostTouch(sequence)) {
                    // The pressed Aceitar can temporarily vanish from the orange mask.
                    // In that exact frame current.hasFreightList() is false because one
                    // row is missing, so evaluate it here before treating the list as gone.
                    GtoFastVisualDetector.PressCandidate transientMissing =
                        fastVisualDetector.detectPressedRowAfterTouch(fastTouchBaseline, current, captureHeight);
                    if (transientMissing != null) {
                        fastPendingFromTouchPulse = true;
                        armFastVisualSelection(
                            transientMissing,
                            image,
                            fastTouchBaseline,
                            current,
                            now
                        );
                    }
                }

                if (fastPendingSelectedRow < 0 && fastTouchPulseActive) {
                    GtoFastVisualDetector.PressCandidate retrospective = retrospectiveFastTouchCandidate();
                    if (retrospective != null) {
                        fastPendingFromTouchPulse = true;
                        armFastVisualSelection(
                            retrospective,
                            image,
                            fastTouchBaseline,
                            fastPreviousFreightFrame,
                            now
                        );
                    }
                }

                int missingRequired = fastPendingFromTouchPulse ? 1 : 2;
                if (fastPendingSelectedRow >= 0
                    && now - fastPendingSelectedAt <= FAST_SELECTION_CONFIRM_WINDOW_MS
                    && fastMissingListFrames >= missingRequired) {
                    finalizeFastVisualSelection();
                    fastPreviousFreightFrame = current;
                    fastPreviousFreightSequence = sequence;
                    return;
                }
            } else {
                fastMissingListFrames = 0;
            }

            if (now - lastFreightListSeenAt > 380L) {
                persistFreightRuntimeStatus("OTHER", 0, now, sequence);
            }

            if (fastTouchPulseActive && fastPendingSelectedRow < 0
                && now - fastTouchPulseAt > CRITICAL_TOUCH_WINDOW_MS) {
                clearFastTouchPulse(false);
            }
            if (fastPendingSelectedRow >= 0
                && now - fastPendingSelectedAt > FAST_SELECTION_CONFIRM_WINDOW_MS) {
                clearFastPendingSelection();
            }
            fastPreviousFreightFrame = current;
            fastPreviousFreightSequence = sequence;
        } catch (IllegalStateException queueError) {
            // If the OEM momentarily outruns the 3-image queue, abort only the critical
            // window and immediately resume acquireLatestImage on the next callback.
            clearFastPendingSelection();
            prefs.edit().putString("lastEvent", "Fila de captura sincronizada novamente").apply();
        } catch (Exception ex) {
            clearFastPendingSelection();
            prefs.edit().putString("lastEvent", "Falha no detector visual: " + ex.getClass().getSimpleName()).apply();
        } finally {
            if (image != null) image.close();
        }
    }

    private FreightSelectionTransaction buildSelectionTransaction(int rowIndex, String source) {
        synchronized (freightFrameLock) {
            Bitmap sourcePanel = frozenSelectionPanelFrame != null && !frozenSelectionPanelFrame.isRecycled()
                ? frozenSelectionPanelFrame
                : latestFreightPanelFrame;
            List<Rect> sourceButtons = !frozenSelectionButtons.isEmpty() ? frozenSelectionButtons : realtimeAcceptRects;
            if (sourcePanel == null || sourcePanel.isRecycled() || sourceButtons.isEmpty()) return null;

            List<Rect> buttons = new ArrayList<>();
            for (Rect rect : sourceButtons) buttons.add(new Rect(rect));
            buttons.sort(Comparator.comparingInt(Rect::centerY));
            if (rowIndex < 0 || rowIndex >= buttons.size()) return null;

            Bitmap panelCopy = sourcePanel.copy(Bitmap.Config.ARGB_8888, false);
            if (panelCopy == null) return null;
            int offset = frozenSelectionPanelFrame != null && !frozenSelectionPanelFrame.isRecycled()
                ? frozenSelectionPanelOffsetX
                : latestFreightPanelOffsetX;
            return new FreightSelectionTransaction(
                rowIndex,
                panelCopy,
                offset,
                buttons,
                source == null ? "frame-lock" : source,
                selectionCoordinator.touchMarkerSequence(),
                prefs.getString("gtoTripSessionId", ""),
                preciseSelectionOcrGeneration
            );
        }
    }

    private void replacePendingSelectionTransaction(FreightSelectionTransaction transaction) {
        if (pendingSelectionTransaction != null && pendingSelectionTransaction != transaction) {
            pendingSelectionTransaction.close();
        }
        pendingSelectionTransaction = transaction;
    }

    private FreightSelectionTransaction takePendingSelectionTransaction() {
        FreightSelectionTransaction transaction = pendingSelectionTransaction;
        pendingSelectionTransaction = null;
        return transaction;
    }

    private void armFastVisualSelection(
        GtoFastVisualDetector.PressCandidate candidate,
        Image image,
        GtoFastVisualDetector.Frame baseline,
        GtoFastVisualDetector.Frame current,
        long now
    ) {
        if (candidate == null || baseline == null || candidate.row < 0 || candidate.row >= baseline.buttons.size()) return;

        // Prefer the clean pre-touch page snapshot. The pressed/transition frame is only
        // a last-resort OCR source; freight text should never depend on a closing screen.
        synchronized (freightFrameLock) {
            if (frozenSelectionPanelFrame == null || frozenSelectionPanelFrame.isRecycled()) {
                boolean snapshotMatches = fastLastSnapshotFrame != null
                    && fastVisualDetector.samePage(fastLastSnapshotFrame, baseline);
                if (snapshotMatches && latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                    frozenSelectionPanelFrame = latestFreightPanelFrame.copy(Bitmap.Config.ARGB_8888, false);
                    frozenSelectionPanelOffsetX = latestFreightPanelOffsetX;
                }
            }
            if (frozenSelectionButtons.isEmpty()) {
                for (Rect rect : baseline.buttons) frozenSelectionButtons.add(new Rect(rect));
            }
        }

        if (frozenSelectionPanelFrame == null || frozenSelectionPanelFrame.isRecycled()) {
            Bitmap full = imageToBitmap(image, captureWidth, captureHeight);
            if (full != null) {
                int left = freightPanelLeftForButtons(full.getWidth(), baseline.buttons);
                Bitmap panel = Bitmap.createBitmap(full, left, 0, full.getWidth() - left, full.getHeight());
                full.recycle();
                synchronized (freightFrameLock) {
                    if (frozenSelectionPanelFrame != null && !frozenSelectionPanelFrame.isRecycled()) {
                        frozenSelectionPanelFrame.recycle();
                    }
                    frozenSelectionPanelFrame = panel;
                    frozenSelectionPanelOffsetX = left;
                }
            }
        }

        fastPendingSelectedRow = candidate.row;
        fastPendingSelectedAt = now;
        fastPendingSelectedScore = candidate.score;
        preciseSelectedRow = candidate.row;
        preciseSelectedTouchAt = now;
        String source = fastPendingFromTouchPulse ? "touch-marker+frame-lock" : "frame-lock";
        FreightSelectionTransaction transaction = buildSelectionTransaction(candidate.row, source);
        if (transaction != null) replacePendingSelectionTransaction(transaction);
        prefs.edit()
            .putString("pendingSelectionSource",
                (fastPendingFromTouchPulse ? "touch-pulse-row-" : "frame-lock-row-") + (candidate.row + 1))
            .putString("lastEvent", "Frete tocado na linha " + (candidate.row + 1) + " · aguardando fechamento da lista")
            .apply();
    }

    private void finalizeFastVisualSelection() {
        int row = fastPendingSelectedRow;
        if (row < 0) return;
        boolean fromPulse = fastPendingFromTouchPulse;

        FreightSelectionTransaction transaction = takePendingSelectionTransaction();
        if (transaction == null) {
            transaction = buildSelectionTransaction(
                row,
                fromPulse ? "touch-marker+frame-lock" : "frame-lock"
            );
        }
        if (transaction == null) {
            clearFastPendingSelection();
            restoreWaitingAfterSelectionFailure(row, "O frete foi tocado, mas a página não pôde ser congelada com segurança.");
            return;
        }

        fastPendingSelectedRow = -1;
        fastPendingSelectedAt = 0L;
        fastPendingSelectedScore = 0f;
        fastMissingListFrames = 0;
        fastPendingFromTouchPulse = false;
        clearFastTouchPulse(false);

        prefs.edit()
            .putString("selectionSource", transaction.source)
            .putLong("selectionTouchSequence", transaction.touchSequence)
            .apply();

        // The immutable transaction owns its bitmap and button geometry before the UI
        // changes state. Removing the 1px sensor or refreshing the overlay cannot recycle it.
        setTripState(STATE_CONFIRMING_FREIGHT, "Frete identificado. Validando os dados…");
        runPreciseSelectedRowOcr(transaction);
    }

    private void clearFastPendingSelection() {
        fastPendingSelectedRow = -1;
        fastPendingSelectedAt = 0L;
        fastPendingSelectedScore = 0f;
        fastPendingFromTouchPulse = false;
        fastMissingListFrames = 0;
        if (pendingSelectionTransaction != null) {
            pendingSelectionTransaction.close();
            pendingSelectionTransaction = null;
        }
        clearFastTouchPulse(false);
    }

    private boolean shouldAnalyzeState(String state) {
        return STATE_TRIP_IN_PROGRESS.equals(state)
            || STATE_RESULT_DETECTED.equals(state)
            || STATE_AWAITING_BONUS.equals(state);
    }

    private long analysisIntervalForState(String state) {
        if (STATE_WAITING_FREIGHT.equals(state)) return 95L;
        if (STATE_CONFIRMING_FREIGHT.equals(state)) return 80L;
        if (STATE_TRIP_IN_PROGRESS.equals(state)) {
            return manualFinishCapturePending ? 120L : ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS;
        }
        if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) return 90L;
        return 1000L;
    }

    private boolean handleActiveTripFreightListEvidence(
        Image image,
        GtoFastVisualDetector.Frame frame,
        long now
    ) {
        String activeState = getTripState();
        if (!isReplaceableActiveSessionState(activeState)) return false;
        boolean unresolvedResult = STATE_RESULT_DETECTED.equals(activeState) || STATE_AWAITING_BONUS.equals(activeState);
        boolean freightList = frame != null && frame.hasFreightList();

        if (!freightList) {
            // If a real touch was already observed and the row-specific pressed state was
            // captured just before the list closed, that pair is stronger than waiting
            // for four static frames. Promote now and let the normal confirmation path
            // consume the preserved pre-touch snapshot.
            if (replacementFreightPressedRow >= 0
                && ((replacementFreightTouchPending
                    && now - replacementFreightTouchAt <= CRITICAL_TOUCH_WINDOW_MS + 260L)
                    || now - replacementFreightCandidateAt <= CRITICAL_TOUCH_WINDOW_MS + 420L)) {
                // The row transition itself is sufficient evidence that the list was
                // acted on. Keep the clean pre-press snapshot and bootstrap the session
                // before the screen disappears completely.
                return promoteReplacementFreightCandidateToWaiting(true);
            }

            activeTripFreightListSeenSince = 0L;
            activeTripFreightListFrames = 0;

            // A tap can close the jobs list before the main-thread ACTION_OUTSIDE marker
            // reaches the capture thread. Keep the pre-armed snapshot briefly so that the
            // marker can still promote the stale route and correlate the selected row.
            if (replacementFreightCandidateArmed
                && now - replacementFreightCandidateAt <= CRITICAL_TOUCH_WINDOW_MS + 260L) {
                return false;
            }
            clearReplacementFreightCandidate();
            return false;
        }

        armOrRefreshReplacementFreightCandidate(image, frame, now);

        if (activeTripFreightListSeenSince == 0L) activeTripFreightListSeenSince = now;
        activeTripFreightListFrames++;
        prefs.edit()
            .putString("screenState", "FREIGHT_LIST_DURING_TRIP")
            .putInt("activeTripFreightListEvidenceFrames", activeTripFreightListFrames)
            .putLong("activeTripFreightListEvidenceSince", activeTripFreightListSeenSince)
            .putBoolean("replacementFreightCandidateArmed", replacementFreightCandidateArmed)
            .apply();

        if (replacementFreightTouchPending
            && now - replacementFreightTouchAt <= CRITICAL_TOUCH_WINDOW_MS + 260L
            && (activeTripFreightListFrames >= 2 || replacementFreightPressedRow >= 0)) {
            return promoteReplacementFreightCandidateToWaiting(true);
        }

        int confirmFrames;
        long confirmMs;
        if (STATE_IDLE.equals(activeState) || STATE_CANCELLED.equals(activeState)) {
            confirmFrames = UNARMED_FREIGHT_LIST_CONFIRM_FRAMES;
            confirmMs = UNARMED_FREIGHT_LIST_CONFIRM_MS;
        } else {
            confirmFrames = unresolvedResult ? RESULT_FREIGHT_LIST_CONFIRM_FRAMES : ACTIVE_TRIP_FREIGHT_LIST_CONFIRM_FRAMES;
            confirmMs = unresolvedResult ? RESULT_FREIGHT_LIST_CONFIRM_MS : ACTIVE_TRIP_FREIGHT_LIST_CONFIRM_MS;
        }
        if (activeTripFreightListFrames < confirmFrames
            || now - activeTripFreightListSeenSince < confirmMs) {
            return false;
        }

        if (unresolvedResult && hasRecentNormalResultActionEvidence(now)) {
            // A real result action was observed, no ADS evidence appeared, and GTO has
            // already reached its jobs list. The loading/logo screen duration is irrelevant.
            mainHandler.post(this::confirmNormalResultAutomatically);
            return true;
        }

        if (unresolvedResult
            && (resultTouchFallbackRequired || prefs.getBoolean("resultTouchFallbackRequired", false))
            && !(resultTouchFallbackContinuityBroken || prefs.getBoolean("resultTouchFallbackContinuityBroken", false))) {
            // This OEM refused the independent outside-touch sensor. A stable jobs list
            // proves the result dialog was dismissed, but cannot distinguish Receber from
            // an unobserved alternate action with enough integrity to auto-register. Hold
            // the previous delivery and expose an explicit, non-silent choice instead of
            // either losing it or inventing a successful Receive.
            armResultTouchFallbackReady("FREIGHT_LIST_AFTER_RESULT");
            return true;
        }

        // Stable list evidence alone is enough to bootstrap WAITING_FREIGHT. If the
        // detector already observed a row-specific button transition while NVU was still
        // opening (or before the floating button was pressed), preserve that row as a
        // pending exact selection instead of waiting for a second user action.
        boolean rowAlreadyPressed = replacementFreightPressedRow >= 0;
        boolean touchEvidence = replacementFreightTouchPending || rowAlreadyPressed;
        return promoteReplacementFreightCandidateToWaiting(touchEvidence);
    }

    private boolean isReplaceableActiveSessionState(String state) {
        // IDLE/CANCELLED are included deliberately: a stable GTO freight list is
        // sufficient evidence to bootstrap a fresh NVU trip session. This does not
        // bypass operation-context validation; beginTrip(false) still refuses to
        // create a session when driver/company/contract context is incomplete.
        return STATE_IDLE.equals(state)
            || STATE_CANCELLED.equals(state)
            || STATE_TRIP_IN_PROGRESS.equals(state)
            || STATE_RESULT_DETECTED.equals(state)
            || STATE_AWAITING_BONUS.equals(state);
    }

    private boolean hasRecentNormalResultActionEvidence(long now) {
        // Kept under the historical method name to minimize regression surface. R3.6
        // deliberately has NO time window: a result action remains valid until the
        // state machine resolves it as RECEIVE, ADS, or the unfinished session is
        // explicitly replaced by a new freight list.
        long persistedTouchAt = prefs == null ? 0L : prefs.getLong("resultActionTouchAt", 0L);
        long touchAt = Math.max(resultActionTouchAt, persistedTouchAt);
        if (touchAt <= 0L) return false;
        String action = prefs.getString("resultAction", "");
        boolean receiveLatched = prefs.getBoolean("resultReceiveLatched", false);
        boolean normalAction = "RECEIVE".equals(action)
            || "RECEIVE_FALLBACK_CONFIRMED".equals(action)
            || "TOUCH_PENDING".equals(action)
            || (receiveLatched && !"ADS".equals(action));
        return normalAction
            && !"REJECTED_BONUS".equals(prefs.getString("completionStatus", ""));
    }

    private void armOrRefreshReplacementFreightCandidate(
        Image image,
        GtoFastVisualDetector.Frame frame,
        long now
    ) {
        if (frame == null || !frame.hasFreightList()) return;

        if (!replacementFreightCandidateArmed) {
            replacementFreightCandidateArmed = true;
            replacementFreightCandidateAt = now;
            replacementFreightBaseline = frame;
            replacementFreightPressedRow = -1;
            replacementFreightPressedScore = 0f;
            replacementFreightTouchPending = false;
            replacementFreightTouchAt = 0L;
            captureReplacementFreightPanel(image, frame);
            mainHandler.post(this::updateFreightTouchPulseSensor);
            return;
        }

        // Keep the grace window anchored to the most recent visible list frame. This
        // matters when the driver browses for several seconds and then taps quickly.
        replacementFreightCandidateAt = now;

        if (replacementFreightBaseline != null
            && !fastVisualDetector.samePage(replacementFreightBaseline, frame)) {
            // The driver changed freight pages while the old route was stale. Use the
            // newest clean page as baseline instead of carrying geometry from page N.
            replacementFreightCandidateAt = now;
            replacementFreightBaseline = frame;
            replacementFreightPressedRow = -1;
            replacementFreightPressedScore = 0f;
            replacementFreightTouchPending = false;
            replacementFreightTouchAt = 0L;
            captureReplacementFreightPanel(image, frame);
            return;
        }

        if (replacementFreightBaseline != null && replacementFreightPressedRow < 0) {
            GtoFastVisualDetector.PressCandidate pressed =
                fastVisualDetector.detectPressedRow(replacementFreightBaseline, frame, captureHeight);
            if (pressed == null) {
                pressed = fastVisualDetector.detectTemporarilyMissingPressedRow(
                    replacementFreightBaseline, frame, captureHeight
                );
            }
            if (pressed != null) {
                replacementFreightPressedRow = pressed.row;
                replacementFreightPressedScore = pressed.score;
            }
        }
    }

    private void captureReplacementFreightPanel(Image image, GtoFastVisualDetector.Frame frame) {
        if (image == null || frame == null || !frame.hasFreightList()) return;
        Bitmap full = imageToBitmap(image, captureWidth, captureHeight);
        if (full == null) return;
        int left = freightPanelLeftForButtons(full.getWidth(), frame.buttons);
        Bitmap panel = Bitmap.createBitmap(full, left, 0, full.getWidth() - left, full.getHeight());
        full.recycle();

        if (replacementFreightPanelFrame != null && !replacementFreightPanelFrame.isRecycled()) {
            replacementFreightPanelFrame.recycle();
        }
        replacementFreightPanelFrame = panel;
        replacementFreightPanelOffsetX = left;
        replacementFreightButtons.clear();
        for (Rect rect : frame.buttons) replacementFreightButtons.add(new Rect(rect));
    }

    private boolean promoteReplacementFreightCandidateToWaiting(boolean fromTouch) {
        String replacedState = getTripState();
        if (!isReplaceableActiveSessionState(replacedState) || !replacementFreightCandidateArmed) return false;

        // Detach the candidate resources before clearTripAnalysis(), which intentionally
        // destroys every old-session visual buffer. These detached objects belong to the
        // new freight page and are restored after beginTrip(false).
        GtoFastVisualDetector.Frame savedBaseline = replacementFreightBaseline;
        Bitmap savedPanel = replacementFreightPanelFrame;
        int savedOffset = replacementFreightPanelOffsetX;
        List<Rect> savedButtons = new ArrayList<>();
        for (Rect rect : replacementFreightButtons) savedButtons.add(new Rect(rect));
        int savedPressedRow = replacementFreightPressedRow;
        float savedPressedScore = replacementFreightPressedScore;
        long savedAt = replacementFreightCandidateAt > 0L
            ? replacementFreightCandidateAt
            : System.currentTimeMillis();

        replacementFreightPanelFrame = null;
        replacementFreightBaseline = null;
        replacementFreightButtons.clear();
        replacementFreightCandidateArmed = false;
        replacementFreightCandidateAt = 0L;
        replacementFreightPressedRow = -1;
        replacementFreightPressedScore = 0f;
        replacementFreightTouchPending = false;
        replacementFreightTouchAt = 0L;

        String cancelledSessionId = prefs.getString("gtoTripSessionId", "");
        String cancelledSummary = prefs.getString("selectedFreightSummary", "");
        long cancelledAt = System.currentTimeMillis();
        boolean hadActiveSession = !STATE_IDLE.equals(replacedState) && !STATE_CANCELLED.equals(replacedState);
        GtoAutoTripSync.discardSessionSnapshot(this, cancelledSessionId);
        clearTripAnalysis();

        if (hadActiveSession) {
            prefs.edit()
                .putString("completionStatus", "CANCELLED_IN_GAME")
                .putString("lastCancelledSessionId", cancelledSessionId)
                .putString("lastCancelledFreightSummary", cancelledSummary)
                .putLong("lastCancelledAt", cancelledAt)
                .putString("lastCancellationReason", (STATE_RESULT_DETECTED.equals(replacedState) || STATE_AWAITING_BONUS.equals(replacedState))
                    ? "UNRESOLVED_RESULT_FREIGHT_LIST_RETURNED"
                    : (fromTouch ? "FREIGHT_LIST_TOUCH_DURING_STALE_ROUTE" : "FREIGHT_LIST_RETURNED"))
                .putString("lastEvent", (STATE_RESULT_DETECTED.equals(replacedState) || STATE_AWAITING_BONUS.equals(replacedState))
                    ? "Entrega anterior não pôde ser confirmada · nova lista detectada"
                    : "Viagem anterior encerrada no GTO · preparando novo frete")
                .apply();
        } else {
            prefs.edit()
                .putString("lastEvent", "Lista de fretes detectada automaticamente · preparando a seleção")
                .apply();
        }

        beginTrip(false);

        if (!STATE_WAITING_FREIGHT.equals(getTripState())) {
            if (savedPanel != null && !savedPanel.isRecycled()) savedPanel.recycle();
            return false;
        }

        if (savedBaseline != null && savedPanel != null && !savedPanel.isRecycled() && !savedButtons.isEmpty()) {
            synchronized (freightFrameLock) {
                if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                    latestFreightPanelFrame.recycle();
                }
                latestFreightPanelFrame = savedPanel;
                latestFreightPanelOffsetX = savedOffset;
                latestFreightPanelAt = savedAt;
                realtimeAcceptRects.clear();
                for (Rect rect : savedButtons) realtimeAcceptRects.add(new Rect(rect));

                recycleFrozenSelectionPanel();
                frozenSelectionPanelFrame = savedPanel.copy(Bitmap.Config.ARGB_8888, false);
                frozenSelectionPanelOffsetX = savedOffset;
                frozenSelectionButtons.clear();
                for (Rect rect : savedButtons) frozenSelectionButtons.add(new Rect(rect));
            }
            fastLastSnapshotFrame = savedBaseline;
            lastFastPanelSnapshotAt = savedAt;
            lastFreightListSeenAt = savedAt;
            long baselineSequence = selectionCoordinator.onFrameProcessed();
            fastPreviousFreightFrame = savedBaseline;
            fastPreviousFreightSequence = baselineSequence;
            recordFastFreightFrame(savedBaseline, baselineSequence);
            freightPageGeneration++;
            scheduleFreightPageOcr(
                freightPageGeneration, savedPanel, savedOffset, savedButtons, System.currentTimeMillis()
            );
            prefs.edit()
                .putString("screenState", "FREIGHT_LIST")
                .putInt("freightCount", savedButtons.size())
                .putLong("freightStructureAt", savedAt)
                .apply();
        } else if (savedPanel != null && !savedPanel.isRecycled()) {
            savedPanel.recycle();
        }

        if (fromTouch) {
            armFastTouchPulseOnCaptureThread();
            if (savedPressedRow >= 0 && savedPressedRow < savedButtons.size()) {
                fastPendingSelectedRow = savedPressedRow;
                fastPendingSelectedAt = System.currentTimeMillis();
                fastPendingSelectedScore = savedPressedScore;
                fastPendingFromTouchPulse = true;
                FreightSelectionTransaction transaction = buildSelectionTransaction(
                    savedPressedRow, "replacement-touch+frame-lock"
                );
                if (transaction != null) replacePendingSelectionTransaction(transaction);
                prefs.edit()
                    .putString("pendingSelectionSource", "replacement-touch-row-" + (savedPressedRow + 1))
                    .putString("lastEvent", "Novo frete tocado durante retomada · linha " + (savedPressedRow + 1))
                    .apply();
            }
        } else {
            announceDriverStage(
                "FREIGHT_RESTART",
                (STATE_RESULT_DETECTED.equals(replacedState) || STATE_AWAITING_BONUS.equals(replacedState))
                    ? "Etapa 1/4 · A entrega anterior não pôde ser confirmada e a lista de fretes voltou. Escolha o novo frete; a NVU já está pronta."
                    : (hadActiveSession
                        ? "Etapa 1/4 · Viagem anterior encerrada no GTO. Escolha o novo frete; a NVU já está pronta."
                        : "Etapa 1/4 · Lista de fretes detectada automaticamente. A NVU já está pronta para identificar o frete selecionado."),
                4200L,
                true
            );
        }
        mainHandler.post(this::updateFreightTouchPulseSensor);
        return true;
    }

    private void clearReplacementFreightCandidate() {
        replacementFreightCandidateArmed = false;
        replacementFreightCandidateAt = 0L;
        replacementFreightBaseline = null;
        replacementFreightPressedRow = -1;
        replacementFreightPressedScore = 0f;
        replacementFreightTouchPending = false;
        replacementFreightTouchAt = 0L;
        replacementFreightButtons.clear();
        if (replacementFreightPanelFrame != null && !replacementFreightPanelFrame.isRecycled()) {
            replacementFreightPanelFrame.recycle();
        }
        replacementFreightPanelFrame = null;
        replacementFreightPanelOffsetX = 0;
        prefs.edit().remove("replacementFreightCandidateArmed").apply();
        mainHandler.post(this::updateFreightTouchPulseSensor);
    }

    /**
     * R3.16: Treat the freight list as a UI lifecycle, not only as an image signature.
     * If a previous selection failed and the list was actually closed, the next visible
     * list starts a new selection/trip session even when the pixels are identical.
     */
    private void onFreightListVisibleAgain(long now) {
        if (projectionPermissionInFlight) return;
        if (!freightListCycleSeen) {
            freightListCycleSeen = true;
            freightListCycleClosed = false;
            freightListReopenPending = false;
            return;
        }
        if (!freightListCycleClosed || !freightListReopenPending) return;

        // Reopening after a failed confirmation is a new NVU trip attempt. Do not let
        // the previous session, OCR generation, selection cache or locked freight leak
        // into the new list. The current projection remains active.
        if (restartWaitingFreightSelectionSession("FREIGHT_LIST_REOPENED_AFTER_SELECTION_FAILURE")) {
            freightListCycleSeen = true;
            freightListCycleClosed = false;
            freightListReopenPending = false;
            freightListCycleClosedAt = 0L;
            lastFreightListSeenAt = 0L;
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
            prefs.edit()
                .putString("lastEvent", "Nova lista de fretes reaberta · nova tentativa de viagem iniciada")
                .putLong("freightListReopenedAt", now)
                .apply();
        }
    }

    private void markFreightListClosed(long now) {
        if (projectionPermissionInFlight || !freightListCycleSeen) return;
        if (!freightListCycleClosed) {
            freightListCycleClosed = true;
            freightListCycleClosedAt = now;
        }

        // A list can disappear while CONFIRMING_FREIGHT because the GTO closes it
        // immediately after the driver's tap. Do not lose that lifecycle edge. We only
        // arm a *new session* while the logical state is waiting; successful confirmation
        // clears the lifecycle flags below. This prevents a failed selection from being
        // silently attached to the next identical list.
        boolean waitingForRetry = STATE_WAITING_FREIGHT.equals(getTripState());
        if (waitingForRetry) {
            freightListReopenPending = true;
            prefs.edit()
                .putBoolean("freightListReopenPending", true)
                .putLong("freightListClosedAt", now)
                .apply();
        }
    }

    private void armFreightListReopenAfterSelectionFailure(long now, String reason) {
        if (projectionPermissionInFlight || !freightListCycleSeen) return;

        // If the list disappeared before the OCR/confirmation callback returned, the
        // callback still needs to convert the already-recorded close edge into a retry
        // edge after restoring WAITING_FREIGHT. This is the race that previously made
        // the second identical list look like the first attempt.
        if (lastFreightListSeenAt <= 0L || now - lastFreightListSeenAt > 420L) {
            if (!freightListCycleClosed) {
                freightListCycleClosed = true;
                freightListCycleClosedAt = now;
            }
            freightListReopenPending = true;
            prefs.edit()
                .putBoolean("freightListReopenPending", true)
                .putLong("freightListClosedAt", freightListCycleClosedAt > 0L ? freightListCycleClosedAt : now)
                .putString("freightRetryReason", reason == null ? "SELECTION_FAILED" : reason)
                .putLong("freightRetryAt", now)
                .apply();
        }
    }

    private boolean restartWaitingFreightSelectionSession(String reason) {
        if (projectionPermissionInFlight) return false;
        if (!STATE_WAITING_FREIGHT.equals(getTripState())) return false;
        if (isOperationClosedForNewTrip()) return false;

        String previousSessionId = prefs.getString("gtoTripSessionId", "");
        GtoAutoTripSync.discardSessionSnapshot(this, previousSessionId);
        clearTripAnalysis();

        String newSessionId = GtoAutoTripSync.newSessionId();
        long startedAt = System.currentTimeMillis();
        boolean persisted = prefs.edit()
            .putString("gtoTripSessionId", newSessionId)
            .putLong("gtoTripSessionStartedAt", startedAt)
            .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_IN_PROGRESS)
            .putString("gtoTripIntegrityStatus", "CREATING_SNAPSHOT")
            .remove("gtoRegisteredTripId")
            .remove("gtoTripSyncError")
            .remove("gtoTripIntegrityError")
            .remove("selectedFreight")
            .remove("selectedFreightSummary")
            .remove("freightOptions")
            .remove("freightTextGeneration")
            .remove("freightTextAt")
            .putString("lastEvent", "Nova tentativa de seleção de frete · " + reason)
            .putString("freightRetryReason", reason)
            .putLong("freightRetryAt", startedAt)
            .commit();

        if (!persisted || !GtoAutoTripSync.beginSessionSnapshot(this, prefs, newSessionId)) {
            prefs.edit()
                .remove("gtoTripSessionId")
                .remove("gtoTripSessionStartedAt")
                .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_REJECTED)
                .putString("lastEvent", "Não foi possível criar a nova tentativa de viagem")
                .apply();
            setTripState(STATE_IDLE, "Nova tentativa não pôde ser criada");
            return false;
        }

        setTripState(STATE_WAITING_FREIGHT, "Nova lista detectada · aguardando escolha do frete");
        return true;
    }

    private void updateRealtimeFreightStructure(Bitmap frame, long now) {
        if (frame == null || frame.isRecycled() || !STATE_WAITING_FREIGHT.equals(getTripState())) return;
        List<Rect> buttons = detectAcceptButtonRects(frame);
        boolean freightList = buttons.size() >= 1 && buttons.size() <= 5;

        if (!freightList) {
            if (selectionProbeActive) {
                finishSelectionProbeIfPossible(now, true);
            }
            if (now - lastFreightListSeenAt > 420L) {
                markFreightListClosed(now);
                prefs.edit().putBoolean("touchCaptureNeeded", false).apply();
            }
            return;
        }

        buttons.sort(Comparator.comparingInt(Rect::centerY));
        onFreightListVisibleAgain(now);
        recordButtonFrame(frame, buttons, now);
        if (selectionProbeActive) evaluateSelectionProbe(frame, buttons, now);
        synchronized (freightFrameLock) {
            realtimeAcceptRects.clear();
            for (Rect rect : buttons) realtimeAcceptRects.add(new Rect(rect));
        }

        lastFreightListSeenAt = now;
        lastScreenState = "FREIGHT_LIST";
        prefs.edit()
            .putString("screenState", "FREIGHT_LIST")
            .putInt("freightCount", buttons.size())
            .putLong("freightStructureAt", now)
            .putBoolean("touchCaptureNeeded", true)
            .apply();

        if (now - lastSnapshotAt >= SNAPSHOT_INTERVAL_MS) {
            lastSnapshotAt = now;
            int left = freightPanelLeftForButtons(frame.getWidth(), buttons);
            Bitmap panel = Bitmap.createBitmap(frame, left, 0, frame.getWidth() - left, frame.getHeight());
            synchronized (freightFrameLock) {
                if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                    latestFreightPanelFrame.recycle();
                }
                latestFreightPanelFrame = panel;
                latestFreightPanelOffsetX = left;
                latestFreightPanelAt = now;
            }
        }
    }

    private void armSelectionProbe(long touchAt) {
        if (!STATE_WAITING_FREIGHT.equals(getTripState())) return;
        synchronized (freightFrameLock) {
            ButtonFrameSample baseline = null;
            for (int i = buttonFrameHistory.size() - 1; i >= 0; i--) {
                ButtonFrameSample candidate = buttonFrameHistory.get(i);
                if (candidate == null || candidate.buttons.isEmpty()) continue;
                if (touchAt - candidate.at > 320L) break;
                if (candidate.at <= touchAt + 40L) {
                    baseline = candidate.copy();
                    break;
                }
            }
            if (baseline == null && !buttonFrameHistory.isEmpty()) {
                baseline = buttonFrameHistory.get(buttonFrameHistory.size() - 1).copy();
            }
            if (baseline == null || baseline.buttons.isEmpty()) return;

            selectionProbeBaseline = baseline;
            selectionProbeStartedAt = touchAt;
            selectionProbeActive = true;
            selectionProbeBestRow = -1;
            selectionProbeBestScore = 0f;
            selectionProbeBestMargin = 0f;
            selectionProbeEvidenceFrames = 0;
            selectionProbeLastEvidenceRow = -1;

            recycleFrozenSelectionPanel();
            if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                frozenSelectionPanelFrame = latestFreightPanelFrame.copy(Bitmap.Config.ARGB_8888, false);
                frozenSelectionPanelOffsetX = latestFreightPanelOffsetX;
            }
            frozenSelectionButtons.clear();
            for (Rect rect : baseline.buttons) frozenSelectionButtons.add(new Rect(rect));
        }
        prefs.edit()
            .putString("selectionSource", "visual-buffer")
            .putString("lastEvent", "Toque detectado na lista; confirmando visualmente o frete selecionado.")
            .apply();
    }

    private void recordButtonFrame(Bitmap frame, List<Rect> buttons, long now) {
        if (frame == null || frame.isRecycled() || buttons == null || buttons.isEmpty()) return;
        ButtonFrameSample sample = new ButtonFrameSample();
        sample.at = now;
        for (Rect rect : buttons) {
            Rect copy = new Rect(rect);
            sample.buttons.add(copy);
            sample.signatures.add(buttonSignature(frame, copy, 1f));
            sample.orangeRatios.add(orangeRatio(frame, copy, 1f));
        }

        ButtonFrameSample previous = null;
        synchronized (freightFrameLock) {
            if (!buttonFrameHistory.isEmpty()) {
                previous = buttonFrameHistory.get(buttonFrameHistory.size() - 1).copy();
            }
            buttonFrameHistory.add(sample);
            while (buttonFrameHistory.size() > BUTTON_FRAME_HISTORY_LIMIT) buttonFrameHistory.remove(0);
        }

        // Primary FIX9 path: do not wait for Android to reveal touch coordinates.
        // The GTO Accept buttons are visually static until one is pressed. A change in
        // exactly one button between adjacent frames identifies the selected row.
        if (!selectionProbeActive && !preciseSelectionOcrBusy && previous != null) {
            detectDirectButtonPress(previous, sample);
        }
    }

    private void detectDirectButtonPress(ButtonFrameSample previous, ButtonFrameSample current) {
        if (!STATE_WAITING_FREIGHT.equals(getTripState())) return;
        if (previous == null || current == null) return;
        if (previous.buttons.size() < 1 || previous.buttons.size() != current.buttons.size()) return;
        if (current.at - previous.at > 140L) return;

        int count = current.buttons.size();
        float best = 0f;
        float second = 0f;
        int bestRow = -1;
        int stableOthers = 0;
        float[] diffs = new float[count];

        for (int i = 0; i < count; i++) {
            if (Math.abs(previous.buttons.get(i).centerY() - current.buttons.get(i).centerY()) > Math.max(dp(8), captureHeight / 80)) {
                return; // page/layout transition, not a button press
            }
            float signature = signatureDistance(previous.signatures.get(i), current.signatures.get(i));
            float orangeDrop = Math.max(0f, previous.orangeRatios.get(i) - current.orangeRatios.get(i));
            float diff = Math.max(signature, orangeDrop * 0.95f);
            diffs[i] = diff;
            if (diff > best) {
                second = best;
                best = diff;
                bestRow = i;
            } else if (diff > second) {
                second = diff;
            }
        }
        if (bestRow < 0) return;
        for (int i = 0; i < count; i++) {
            if (i == bestRow) continue;
            if (diffs[i] <= 0.040f) stableOthers++;
        }
        float margin = best - second;
        if (best < 0.050f || margin < 0.018f || stableOthers < Math.max(1, count - 2)) return;

        synchronized (freightFrameLock) {
            selectionProbeBaseline = previous.copy();
            selectionProbeStartedAt = current.at;
            selectionProbeActive = true;
            selectionProbeBestRow = bestRow;
            selectionProbeBestScore = best;
            selectionProbeBestMargin = margin;
            selectionProbeEvidenceFrames = 1;
            selectionProbeLastEvidenceRow = bestRow;

            recycleFrozenSelectionPanel();
            if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                frozenSelectionPanelFrame = latestFreightPanelFrame.copy(Bitmap.Config.ARGB_8888, false);
                frozenSelectionPanelOffsetX = latestFreightPanelOffsetX;
            }
            frozenSelectionButtons.clear();
            for (Rect rect : previous.buttons) frozenSelectionButtons.add(new Rect(rect));
        }
        commitVisualSelectedRow(bestRow, best, margin);
    }


    private void evaluateSelectionProbe(Bitmap frame, List<Rect> buttons, long now) {
        if (!selectionProbeActive || selectionProbeBaseline == null) return;
        if (now < selectionProbeStartedAt - 25L) return;
        if (now - selectionProbeStartedAt > SELECTION_PROBE_TIMEOUT_MS) {
            finishSelectionProbeIfPossible(now, false);
            return;
        }
        if (buttons.size() != selectionProbeBaseline.buttons.size() || buttons.isEmpty()) return;

        float best = 0f;
        float second = 0f;
        int bestRow = -1;
        int stableOthers = 0;
        float[] diffs = new float[buttons.size()];
        for (int i = 0; i < buttons.size(); i++) {
            int[] currentSig = buttonSignature(frame, buttons.get(i), 1f);
            float currentOrange = orangeRatio(frame, buttons.get(i), 1f);
            float signature = signatureDistance(selectionProbeBaseline.signatures.get(i), currentSig);
            float orangeDrop = Math.max(0f, selectionProbeBaseline.orangeRatios.get(i) - currentOrange);
            float diff = Math.max(signature, orangeDrop * 0.92f);
            diffs[i] = diff;
            if (diff > best) {
                second = best;
                best = diff;
                bestRow = i;
            } else if (diff > second) {
                second = diff;
            }
        }
        if (bestRow < 0) return;
        for (int i = 0; i < diffs.length; i++) {
            if (i == bestRow) continue;
            if (diffs[i] <= 0.045f) stableOthers++;
        }

        float margin = best - second;
        boolean highEvidence = best >= 0.085f && margin >= 0.028f && stableOthers >= Math.max(1, buttons.size() - 2);
        boolean mediumEvidence = best >= 0.050f && margin >= 0.018f && stableOthers >= Math.max(1, buttons.size() - 2);

        if (highEvidence || mediumEvidence) {
            if (bestRow == selectionProbeLastEvidenceRow) selectionProbeEvidenceFrames++;
            else {
                selectionProbeLastEvidenceRow = bestRow;
                selectionProbeEvidenceFrames = 1;
            }
            if (best > selectionProbeBestScore || (bestRow == selectionProbeBestRow && margin > selectionProbeBestMargin)) {
                selectionProbeBestRow = bestRow;
                selectionProbeBestScore = best;
                selectionProbeBestMargin = margin;
            }
            if (highEvidence || selectionProbeEvidenceFrames >= 2) {
                commitVisualSelectedRow(bestRow, best, margin);
            }
        }
    }

    private boolean finishSelectionProbeIfPossible(long now, boolean listDisappeared) {
        if (!selectionProbeActive) return false;
        boolean enough = selectionProbeBestRow >= 0
            && selectionProbeBestScore >= 0.055f
            && selectionProbeBestMargin >= 0.018f;
        if (enough) {
            int row = selectionProbeBestRow;
            float score = selectionProbeBestScore;
            float margin = selectionProbeBestMargin;
            commitVisualSelectedRow(row, score, margin);
            return STATE_TRIP_IN_PROGRESS.equals(getTripState()) || preciseSelectionOcrBusy;
        }
        if (now - selectionProbeStartedAt >= SELECTION_PROBE_TIMEOUT_MS || listDisappeared) {
            // On a page arrow all Accept buttons remain visually unchanged. Do not guess.
            clearSelectionProbe();
            prefs.edit().putString("lastEvent", "Ação na lista detectada sem alteração exclusiva em um botão Aceitar; nenhum frete foi presumido.").apply();
        }
        return false;
    }

    private void commitVisualSelectedRow(int row, float score, float margin) {
        if (!selectionProbeActive || row < 0) return;
        preciseSelectedRow = row;
        preciseSelectedTouchAt = System.currentTimeMillis();
        selectionProbeActive = false;
        prefs.edit()
            .putInt("preciseSelectedRow", row)
            .putString("selectionSource", "visual-buffer")
            .putString("lastEvent", "Frete selecionado · confirmando dados da linha " + (row + 1))
            .putBoolean("touchCaptureNeeded", false)
            .apply();
        // Important: leave WAITING immediately. Otherwise a stale MediaProjection frame
        // containing the old freight list can auto-close the NVU menu after the driver
        // has already accepted the job.
        setTripState(STATE_CONFIRMING_FREIGHT, "Frete selecionado. Confirmando informações…");
        runPreciseSelectedRowOcr(row);
    }

    private void clearSelectionProbe() {
        selectionProbeActive = false;
        selectionProbeStartedAt = 0L;
        selectionProbeBaseline = null;
        selectionProbeBestRow = -1;
        selectionProbeBestScore = 0f;
        selectionProbeBestMargin = 0f;
        selectionProbeEvidenceFrames = 0;
        selectionProbeLastEvidenceRow = -1;
        synchronized (freightFrameLock) {
            buttonFrameHistory.clear();
            frozenSelectionButtons.clear();
            recycleFrozenSelectionPanel();
        }
    }

    private void recycleFrozenSelectionPanel() {
        if (frozenSelectionPanelFrame != null && !frozenSelectionPanelFrame.isRecycled()) {
            frozenSelectionPanelFrame.recycle();
        }
        frozenSelectionPanelFrame = null;
        frozenSelectionPanelOffsetX = 0;
    }

    private void handlePreciseTouch(float x, float y, long eventTime) {
        if (!gtoForeground) return;
        String state = getTripState();

        if (STATE_WAITING_FREIGHT.equals(state)) {
            List<Rect> buttons = new ArrayList<>();
            synchronized (freightFrameLock) {
                for (Rect rect : realtimeAcceptRects) buttons.add(new Rect(rect));
            }
            if (buttons.isEmpty() || System.currentTimeMillis() - lastFreightListSeenAt > 900L) return;
            buttons.sort(Comparator.comparingInt(Rect::centerY));

            int hit = -1;
            for (int i = 0; i < buttons.size(); i++) {
                Rect target = new Rect(buttons.get(i));
                target.inset(-dp(10), -dp(10));
                if (target.contains(Math.round(x), Math.round(y))) {
                    hit = i;
                    break;
                }
            }
            if (hit < 0) return; // Page arrows/other game controls pass through untouched.

            preciseSelectedRow = hit;
            preciseSelectedTouchAt = System.currentTimeMillis();
            prefs.edit()
                .putFloat("preciseTouchX", x)
                .putFloat("preciseTouchY", y)
                .putInt("preciseSelectedRow", hit)
                .putString("selectionSource", "precise-touch")
                .putString("lastEvent", "Frete selecionado · confirmando dados da linha " + (hit + 1))
                .putBoolean("touchCaptureNeeded", false)
                .apply();
            setTripState(STATE_CONFIRMING_FREIGHT, "Frete selecionado. Confirmando informações…");
            runPreciseSelectedRowOcr(hit);
            return;
        }

        if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) {
            long now = System.currentTimeMillis();
            Rect receiveTarget = expandedResultTarget(receiveRect);
            Rect doubleTarget = expandedResultTarget(doubleValueRect);
            if (receiveTarget != null && receiveTarget.contains(Math.round(x), Math.round(y))) {
                latchExactReceiveAndSend(now, "precise-touch");
                return;
            }
            if (doubleTarget != null && doubleTarget.contains(Math.round(x), Math.round(y))) {
                latchExactAdsTouch(now, "precise-touch");
            }
        }
    }

    private Rect expandedResultTarget(Rect rect) {
        if (rect == null) return null;
        Rect target = new Rect(rect);
        int horizontal = Math.max(dp(48), captureWidth / 28);
        int vertical = Math.max(dp(28), captureHeight / 28);
        target.inset(-horizontal, -vertical);
        target.intersect(0, 0, captureWidth, captureHeight);
        return target;
    }

    private FreightOption stableFreightForRow(int rowIndex) {
        long textGeneration = prefs == null ? -1L : prefs.getLong("freightTextGeneration", -1L);
        if (freightPageGeneration <= 0L || textGeneration != freightPageGeneration) return null;
        synchronized (freightOptions) {
            for (FreightOption option : freightOptions) {
                if (option.rowIndex == rowIndex) return copyFreightOption(option);
            }
        }
        return null;
    }

    private void restoreWaitingAfterSelectionFailure(int rowIndex, String reason) {
        long now = System.currentTimeMillis();
        prefs.edit()
            .putString("lastEvent", reason)
            .putBoolean("touchCaptureNeeded", false)
            .apply();

        // The list normally closes before the precise OCR callback returns. Record the
        // close edge while the state is still CONFIRMING_FREIGHT, then arm the reopen
        // edge *after* restoring WAITING_FREIGHT. This makes the next visually identical
        // list a new selection session instead of a continuation of the failed one.
        if (!projectionPermissionInFlight && freightListCycleSeen
            && (lastFreightListSeenAt <= 0L || now - lastFreightListSeenAt > 420L)) {
            if (!freightListCycleClosed) {
                freightListCycleClosed = true;
                freightListCycleClosedAt = now;
            }
        }

        // Do not close the overlay. The user can see that the job was not confirmed
        // instead of the bubble silently disappearing.
        setTripState(STATE_WAITING_FREIGHT, "Não foi possível confirmar todos os dados. Abra a lista e selecione novamente.");
        armFreightListReopenAfterSelectionFailure(now, reason);
    }

    private void runPreciseSelectedRowOcr(int rowIndex) {
        FreightSelectionTransaction transaction = buildSelectionTransaction(rowIndex, "legacy-row-selection");
        if (transaction == null) {
            FreightOption stable = stableFreightForRow(rowIndex);
            if (stable != null && isExactFreightDataValid(stable)) commitPreciseFreight(stable);
            else restoreWaitingAfterSelectionFailure(rowIndex, "Frete selecionado, mas a página não estava disponível para leitura.");
            return;
        }
        runPreciseSelectedRowOcr(transaction);
    }

    private void runPreciseSelectedRowOcr(FreightSelectionTransaction transaction) {
        if (transaction == null) return;
        String currentSessionId = prefs == null ? "" : prefs.getString("gtoTripSessionId", "");
        if (transaction.generation != preciseSelectionOcrGeneration
            || !transaction.sessionId.equals(currentSessionId)) {
            transaction.close();
            return;
        }
        if (selectionTextRecognizer == null) {
            transaction.close();
            restoreWaitingAfterSelectionFailure(transaction.rowIndex, "OCR local indisponível para confirmar o frete.");
            return;
        }
        if (preciseSelectionOcrBusy) {
            long waitedMs = System.currentTimeMillis() - transaction.createdAt;
            if (waitedMs >= PRECISE_OCR_BUSY_WAIT_TIMEOUT_MS) {
                int row = transaction.rowIndex;
                transaction.close();
                FreightOption stable = stableFreightForRow(row);
                if (stable != null && isExactFreightDataValid(stable)) {
                    commitPreciseFreight(stable);
                } else {
                    restoreWaitingAfterSelectionFailure(
                        row,
                        "A confirmação do frete ficou ocupada por tempo demais. Selecione novamente; nenhum dado incorreto foi registrado."
                    );
                }
                return;
            }
            mainHandler.postDelayed(
                () -> runPreciseSelectedRowOcr(transaction),
                PRECISE_OCR_BUSY_RETRY_MS
            );
            return;
        }
        String currentState = getTripState();
        if (!STATE_CONFIRMING_FREIGHT.equals(currentState) && !STATE_WAITING_FREIGHT.equals(currentState)) {
            transaction.close();
            return;
        }

        final int rowIndex = transaction.rowIndex;
        Bitmap panelCopy = transaction.panelFrame.copy(Bitmap.Config.ARGB_8888, false);
        int panelOffset = transaction.panelOffsetX;
        List<Rect> buttons = new ArrayList<>();
        for (Rect rect : transaction.buttons) buttons.add(new Rect(rect));
        if (panelCopy == null) {
            transaction.close();
            FreightOption stable = stableFreightForRow(rowIndex);
            if (stable != null && isExactFreightDataValid(stable)) commitPreciseFreight(stable);
            else restoreWaitingAfterSelectionFailure(rowIndex, "Frete selecionado, mas a imagem congelada não pôde ser copiada.");
            return;
        }
        buttons.sort(Comparator.comparingInt(Rect::centerY));
        if (rowIndex < 0 || rowIndex >= buttons.size()) {
            panelCopy.recycle();
            transaction.close();
            FreightOption stable = stableFreightForRow(rowIndex);
            if (stable != null && isExactFreightDataValid(stable)) commitPreciseFreight(stable);
            else restoreWaitingAfterSelectionFailure(rowIndex, "Frete selecionado, mas a linha não pôde ser reconstruída com segurança.");
            return;
        }

        Rect button = buttons.get(rowIndex);
        int spacing = Math.round(captureHeight * 0.175f);
        if (buttons.size() >= 2) {
            List<Integer> gaps = new ArrayList<>();
            for (int i = 1; i < buttons.size(); i++) gaps.add(buttons.get(i).centerY() - buttons.get(i - 1).centerY());
            Collections.sort(gaps);
            spacing = gaps.get(gaps.size() / 2);
        }
        int top = rowIndex == 0
            ? Math.max(0, button.centerY() - spacing / 2)
            : (buttons.get(rowIndex - 1).centerY() + button.centerY()) / 2;
        int bottom = rowIndex == buttons.size() - 1
            ? Math.min(captureHeight, button.centerY() + spacing / 2)
            : (button.centerY() + buttons.get(rowIndex + 1).centerY()) / 2;

        int textLeftScreen = clamp(
            button.left - Math.round(captureWidth * 0.245f),
            panelOffset,
            captureWidth - 2
        );
        int textRightScreen = clamp(
            button.left + Math.round(captureWidth * 0.018f),
            textLeftScreen + 2,
            captureWidth
        );
        int localLeft = clamp(textLeftScreen - panelOffset, 0, panelCopy.getWidth() - 2);
        int localRight = clamp(textRightScreen - panelOffset, localLeft + 2, panelCopy.getWidth());
        int cropTop = clamp(top, 0, panelCopy.getHeight() - 2);
        int cropBottom = clamp(bottom, cropTop + 2, panelCopy.getHeight());

        Bitmap rowCrop = Bitmap.createBitmap(panelCopy, localLeft, cropTop, localRight - localLeft, cropBottom - cropTop);
        panelCopy.recycle();
        transaction.close();
        float upscale = Math.min(2.25f, 1650f / Math.max(1f, rowCrop.getWidth()));
        Bitmap ocrBitmap = rowCrop;
        if (upscale > 1.08f) {
            ocrBitmap = Bitmap.createScaledBitmap(
                rowCrop,
                Math.max(1, Math.round(rowCrop.getWidth() * upscale)),
                Math.max(1, Math.round(rowCrop.getHeight() * upscale)),
                true
            );
            rowCrop.recycle();
        } else {
            upscale = 1f;
        }

        preciseSelectionOcrBusy = true;
        final Bitmap bitmapForOcr = ocrBitmap;
        final float scale = upscale;
        final int screenLeft = textLeftScreen;
        final int screenTop = cropTop;
        final Rect exactButton = new Rect(button);
        final int exactRow = rowIndex;
        final long scheduledSelectionGeneration = transaction.generation;
        final String scheduledSelectionSessionId = transaction.sessionId;

        selectionTextRecognizer.process(InputImage.fromBitmap(bitmapForOcr, 0))
            .addOnSuccessListener(text -> {
                if (!isCurrentPreciseSelectionOcr(scheduledSelectionGeneration, scheduledSelectionSessionId)) return;
                List<OcrLine> lines = new ArrayList<>();
                String geometricOriginFallback = "";
                for (Text.TextBlock block : text.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        Rect box = line.getBoundingBox();
                        if (box == null || line.getText() == null) continue;
                        String value = line.getText().trim();
                        if (value.isEmpty()) continue;
                        Rect mapped = new Rect(
                            screenLeft + Math.round(box.left / scale),
                            screenTop + Math.round(box.top / scale),
                            screenLeft + Math.round(box.right / scale),
                            screenTop + Math.round(box.bottom / scale)
                        );
                        lines.add(new OcrLine(value, mapped, line.getConfidence()));
                        if (geometricOriginFallback.isEmpty()) {
                            float relY = (mapped.centerY() - top) / (float) Math.max(1, bottom - top);
                            if (relY >= 0.22f && relY <= 0.66f
                                && extractKmDigits(value).isEmpty()
                                && extractMoneyDigits(value).isEmpty()
                                && !normalize(value).contains("aceitar")) {
                                String inferred = inferOriginCompanyFromMlLine(line);
                                if (looksLikeEntityName(inferred)) geometricOriginFallback = inferred;
                            }
                        }
                    }
                }
                List<FreightOption> parsed = parseFreightOptions(lines, Collections.singletonList(exactButton));
                FreightOption selected = parsed.isEmpty() ? null : parsed.get(0);
                if (selected == null) {
                    // The row itself was already identified visually. If the dedicated
                    // OCR misses a glyph in this one frame, reuse only the stabilized
                    // snapshot of THE SAME ROW — never another card.
                    selected = stableFreightForRow(exactRow);
                } else {
                    selected.rowIndex = exactRow;
                    selected.acceptRect = new Rect(exactButton);
                    selected.acceptCenterY = exactButton.centerY();
                    selected.rowTop = top;
                    selected.rowBottom = bottom;
                    refinePreciseRowFields(selected, lines, top, bottom);
                    FreightOption stableSamePage = stableFreightForRow(exactRow);
                    if (stableSamePage != null && hasCriticalFreightConflict(selected, stableSamePage)) {
                        prefs.edit()
                            .putString("lastFreightConflict", freightConflictSummary(selected, stableSamePage))
                            .putLong("lastFreightConflictAt", System.currentTimeMillis())
                            .apply();
                        restoreWaitingAfterSelectionFailure(exactRow,
                            "O frete foi identificado, mas duas leituras da mesma linha divergiram. Selecione novamente para evitar registrar dados errados.");
                        return;
                    }
                    if (selected.originCompany.isEmpty() && looksLikeEntityName(geometricOriginFallback)) {
                        selected.originCompany = geometricOriginFallback;
                        selected.companyRoute = selected.originCompany
                            + (selected.destinationCompany.isEmpty() ? "" : " > " + selected.destinationCompany);
                    }
                    selected = mergePreciseWithStable(selected, exactRow);
                }

                if (selected != null && isExactFreightDataValid(selected)) {
                    commitPreciseFreight(selected);
                } else {
                    FreightOption stable = stableFreightForRow(exactRow);
                    if (stable != null && isExactFreightDataValid(stable)) {
                        commitPreciseFreight(stable);
                    } else {
                        restoreWaitingAfterSelectionFailure(exactRow,
                            "Frete da linha " + (exactRow + 1) + " detectado, mas os dados não ficaram legíveis o suficiente para confirmar com segurança.");
                    }
                }
            })
            .addOnFailureListener(error -> {
                if (!isCurrentPreciseSelectionOcr(scheduledSelectionGeneration, scheduledSelectionSessionId)) return;
                FreightOption stable = stableFreightForRow(exactRow);
                if (stable != null && isExactFreightDataValid(stable)) {
                    commitPreciseFreight(stable);
                } else {
                    restoreWaitingAfterSelectionFailure(exactRow,
                        "Falha ao confirmar os dados do frete: " + error.getClass().getSimpleName());
                }
            })
            .addOnCompleteListener(task -> {
                if (!bitmapForOcr.isRecycled()) bitmapForOcr.recycle();
                if (isCurrentPreciseSelectionOcr(scheduledSelectionGeneration, scheduledSelectionSessionId)) {
                    preciseSelectionOcrBusy = false;
                    clearSelectionProbe();
                }
            });
    }

    private String inferOriginCompanyFromMlLine(Text.Line line) {
        if (line == null || line.getText() == null) return "";
        String raw = line.getText().trim();
        if (raw.isEmpty()) return "";

        int separator = routeSeparatorIndex(raw);
        if (separator > 0) {
            String direct = cleanOcrLabel(raw.substring(0, separator));
            if (looksLikeEntityName(direct)) return direct;
        }

        // Prefer origin companies learned from earlier high-confidence cards. This is
        // deterministic and avoids turning the whole route into an origin when ML Kit
        // drops the tiny '>' glyph.
        java.util.Set<String> known = prefs.getStringSet(
            "knownGtoOriginCompanies", java.util.Collections.emptySet()
        );
        String normalizedRaw = normalize(raw);
        String bestKnown = "";
        for (String company : known) {
            if (company == null || company.trim().isEmpty()) continue;
            String n = normalize(company);
            if (normalizedRaw.startsWith(n) && company.length() > bestKnown.length()) bestKnown = company;
        }
        if (!bestKnown.isEmpty()) return bestKnown;

        List<Text.Element> elements = new ArrayList<>();
        for (Text.Element element : line.getElements()) {
            if (element == null || element.getText() == null || element.getBoundingBox() == null) continue;
            String value = cleanOcrLabel(element.getText());
            if (!value.isEmpty() && !value.equals(">") && !value.equals("›") && !value.equals("»")) {
                elements.add(element);
            }
        }
        if (elements.size() < 2) return "";
        elements.sort((a, b) -> Integer.compare(a.getBoundingBox().left, b.getBoundingBox().left));

        if (elements.size() == 2) {
            String first = cleanOcrLabel(elements.get(0).getText());
            return looksLikeEntityName(first) ? first : "";
        }

        int largestGap = Integer.MIN_VALUE;
        int secondGap = Integer.MIN_VALUE;
        int split = -1;
        float averageHeight = 0f;
        for (Text.Element element : elements) averageHeight += element.getBoundingBox().height();
        averageHeight /= Math.max(1, elements.size());
        for (int i = 0; i < elements.size() - 1; i++) {
            int gap = elements.get(i + 1).getBoundingBox().left - elements.get(i).getBoundingBox().right;
            if (gap > largestGap) {
                secondGap = largestGap;
                largestGap = gap;
                split = i + 1;
            } else if (gap > secondGap) {
                secondGap = gap;
            }
        }
        if (secondGap == Integer.MIN_VALUE) secondGap = 0;
        boolean clearVisualSeparator = largestGap >= Math.max(6, Math.round(averageHeight * 0.34f))
            && largestGap >= secondGap + Math.max(2, Math.round(averageHeight * 0.10f));
        if (!clearVisualSeparator || split <= 0) return "";

        StringBuilder origin = new StringBuilder();
        for (int i = 0; i < split; i++) {
            String token = cleanOcrLabel(elements.get(i).getText());
            if (token.isEmpty()) continue;
            if (origin.length() > 0) origin.append(' ');
            origin.append(token);
        }
        String result = cleanOcrLabel(origin.toString());
        return looksLikeEntityName(result) ? result : "";
    }

    private void refinePreciseRowFields(FreightOption option, List<OcrLine> lines, int top, int bottom) {
        if (option == null || lines == null || lines.isEmpty()) return;
        List<OcrLine> plain = new ArrayList<>();
        for (OcrLine line : lines) {
            if (line.confidence > 0f && line.confidence < 0.34f) continue;
            String n = normalize(line.text);
            if (n.contains("aceitar")) continue;
            if (!extractKmDigits(line.text).isEmpty() || !extractMoneyDigits(line.text).isEmpty()) continue;
            String cleaned = cleanOcrLabel(line.text);
            if (!cleaned.isEmpty()) plain.add(new OcrLine(cleaned, line.rect));
        }
        plain.sort((a, b) -> Integer.compare(a.rect.centerY(), b.rect.centerY()));
        if (plain.isEmpty()) return;
        float height = Math.max(1f, bottom - top);

        OcrLine cargoLine = null;
        OcrLine destinationLine = null;
        for (OcrLine line : plain) {
            float rel = (line.rect.centerY() - top) / height;
            if (cargoLine == null && rel <= 0.43f && looksLikeEntityName(line.text)) cargoLine = line;
            if (rel >= 0.56f && looksLikePlaceName(line.text)) destinationLine = line;
        }
        if (cargoLine == null && looksLikeEntityName(plain.get(0).text)) cargoLine = plain.get(0);
        if (destinationLine == null && looksLikePlaceName(plain.get(plain.size() - 1).text)) {
            destinationLine = plain.get(plain.size() - 1);
        }
        if (cargoLine != null) option.cargo = cargoLine.text;
        if (destinationLine != null && (cargoLine == null || destinationLine != cargoLine)) {
            option.destination = destinationLine.text;
        }

        for (OcrLine line : plain) {
            if (line == cargoLine || line == destinationLine) continue;
            int sep = routeSeparatorIndex(line.text);
            if (sep <= 0) continue;
            String origin = cleanOcrLabel(line.text.substring(0, sep));
            if (looksLikeEntityName(origin)) {
                option.originCompany = origin;
                int length = routeSeparatorLength(line.text, sep);
                String destinationCompany = cleanOcrLabel(
                    line.text.substring(Math.min(line.text.length(), sep + length))
                );
                if (looksLikeEntityName(destinationCompany)) option.destinationCompany = destinationCompany;
                break;
            }
        }
        option.companyRoute = option.originCompany
            + (option.destinationCompany.isEmpty() ? "" : " > " + option.destinationCompany);
    }

    private FreightOption mergePreciseWithStable(FreightOption exact, int rowIndex) {
        FreightOption stable = null;
        synchronized (freightOptions) {
            for (FreightOption option : freightOptions) {
                if (option.rowIndex == rowIndex) {
                    stable = copyFreightOption(option);
                    break;
                }
            }
        }
        if (stable == null) return exact;
        if (exact.cargo.isEmpty() && looksLikeEntityName(stable.cargo)) exact.cargo = stable.cargo;
        if (exact.originCompany.isEmpty() && looksLikeEntityName(stable.originCompany)) exact.originCompany = stable.originCompany;
        if (exact.destination.isEmpty() && looksLikePlaceName(stable.destination)) exact.destination = stable.destination;
        if (exact.km.isEmpty()) exact.km = canonicalKm(stable.km);
        if (exact.offeredValue.isEmpty()) exact.offeredValue = canonicalMoney(stable.offeredValue);
        if (exact.destinationCompany.isEmpty()) exact.destinationCompany = stable.destinationCompany;
        exact.companyRoute = exact.originCompany + (exact.destinationCompany.isEmpty() ? "" : " > " + exact.destinationCompany);
        return exact;
    }

    private boolean hasCriticalFreightConflict(FreightOption exact, FreightOption stable) {
        if (exact == null || stable == null) return false;
        String exactKm = digitsOnly(exact.km);
        String stableKm = digitsOnly(stable.km);
        if (!exactKm.isEmpty() && !stableKm.isEmpty() && !exactKm.equals(stableKm)) return true;
        String exactValue = digitsOnly(exact.offeredValue);
        String stableValue = digitsOnly(stable.offeredValue);
        return !exactValue.isEmpty() && !stableValue.isEmpty() && !exactValue.equals(stableValue);
    }

    private String freightConflictSummary(FreightOption exact, FreightOption stable) {
        return "exact[km=" + digitsOnly(exact == null ? "" : exact.km)
            + ",value=" + digitsOnly(exact == null ? "" : exact.offeredValue)
            + "] stable[km=" + digitsOnly(stable == null ? "" : stable.km)
            + ",value=" + digitsOnly(stable == null ? "" : stable.offeredValue) + "]";
    }

    private boolean isExactFreightDataValid(FreightOption option) {
        if (option == null) return false;
        if (!looksLikeEntityName(option.cargo) || textQuality(option.cargo) < 0.68f) return false;
        if (!looksLikeEntityName(option.originCompany) || textQuality(option.originCompany) < 0.68f) return false;
        if (!looksLikePlaceName(option.destination) || textQuality(option.destination) < 0.68f) return false;
        String kmDigits = digitsOnly(option.km);
        String valueDigits = digitsOnly(option.offeredValue);
        try {
            int km = Integer.parseInt(kmDigits);
            long value = Long.parseLong(valueDigits);
            return km >= 10 && km <= 10000 && value >= 100 && value <= 100000000L;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void commitPreciseFreight(FreightOption selected) {
        if (selected == null) return;
        String state = getTripState();
        if (!STATE_WAITING_FREIGHT.equals(state) && !STATE_CONFIRMING_FREIGHT.equals(state)) return;
        selected.km = canonicalKm(selected.km);
        selected.offeredValue = canonicalMoney(selected.offeredValue);
        String json = freightOptionToJson(selected).toString();
        java.util.Set<String> knownOrigins = new java.util.HashSet<>(prefs.getStringSet("knownGtoOriginCompanies", java.util.Collections.emptySet()));
        if (!selected.originCompany.isEmpty()) knownOrigins.add(selected.originCompany);
        prefs.edit()
            .putStringSet("knownGtoOriginCompanies", knownOrigins)
            .putString("selectedFreight", json)
            .putString("selectedFreightSummary", selected.summary())
            .putString("selectedDestination", selected.destination)
            .putString("selectedOriginCompany", selected.originCompany)
            .putString("selectedDestinationCompany", selected.destinationCompany)
            .putString("selectedCargo", selected.cargo)
            .putString("selectedKm", selected.km)
            .putString("selectedValue", selected.offeredValue)
            .putString("selectionSource", prefs.getString("selectionSource", "frame-lock") + "+row-ocr")
            .putBoolean("touchCaptureNeeded", false)
            .remove("pendingFreight")
            .remove("pendingSelectionSource")
            .apply();
        if (!GtoAutoTripSync.lockSelectedFreight(this, prefs)) {
            setTripState(STATE_WAITING_FREIGHT, "Frete detectado, mas a integridade dos dados não pôde ser bloqueada");
            showStatusChip("Frete não confirmado · integridade dos dados falhou.", 3200L);
            return;
        }
        pendingFreightSelection = null;
        visualFreightSelection = null;
        visualSelectionUntil = 0L;
        // The current list lifecycle is consumed by a successful selection. A later
        // list appearing during the active route belongs to the replacement-trip
        // detector, not to this waiting/reopen retry cycle.
        freightListReopenPending = false;
        freightListCycleClosed = false;
        prefs.edit().putBoolean("freightListReopenPending", false).apply();
        setTripState(STATE_TRIP_IN_PROGRESS,
            "Frete confirmado com sucesso");
        announceDriverStage(
            "TRIP_IN_PROGRESS",
            "Etapa 2/4 · Frete confirmado. Faça a rota normalmente. Ao chegar ao destino, a NVU registrará a viagem automaticamente.",
            4200L,
            false
        );
    }

    private Bitmap imageToBitmap(Image image, int width, int height) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return null;

        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        int paddedWidth = width + Math.max(0, rowPadding / Math.max(1, pixelStride));

        Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == width) return padded;

        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private void handleOcrResult(Text text, float analysisScale, int analysisOffsetX, int analysisOffsetY, Bitmap fullFrame) {
        List<OcrLine> lines = new ArrayList<>();
        StringBuilder allText = new StringBuilder();

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box == null) continue;
                Rect screenBox = new Rect(
                    analysisOffsetX + Math.round(box.left / analysisScale),
                    analysisOffsetY + Math.round(box.top / analysisScale),
                    analysisOffsetX + Math.round(box.right / analysisScale),
                    analysisOffsetY + Math.round(box.bottom / analysisScale)
                );
                String value = line.getText() == null ? "" : line.getText().trim();
                if (value.isEmpty()) continue;
                lines.add(new OcrLine(value, screenBox, line.getConfidence()));
                if (allText.length() > 0) allText.append('\n');
                allText.append(value);
            }
        }

        String normalized = normalize(allText.toString());
        prefs.edit().putString("lastOcrText", truncate(allText.toString(), 1800)).apply();

        if (containsBonusVideo(normalized)) {
            lastScreenState = "BONUS_VIDEO";
            prefs.edit().putString("screenState", lastScreenState).apply();
            if (STATE_RESULT_DETECTED.equals(getTripState()) || STATE_AWAITING_BONUS.equals(getTripState())) {
                persistArrivalCityFromSelectedFreight();
                prefs.edit()
                    .putString("completionStatus", "REJECTED_BONUS")
                    .putLong("completionDetectedAt", System.currentTimeMillis())
                    .apply();
                setTripState(STATE_REJECTED_BONUS, "Bônus de vídeo detectado; viagem bloqueada");
                announceDriverStage(
                    "REJECTED_BONUS",
                    "Viagem não registrada · bônus/ADS detectado. Inicie uma nova viagem normal para registrar na NVU.",
                    4300L,
                    true
                );
            }
            return;
        }

        ResultScreen resultScreen = parseResultScreen(lines, normalized);
        if (resultScreen != null) {
            String stateAtResultCallback = getTripState();
            if (STATE_RESULT_CONFIRMED.equals(stateAtResultCallback)
                || STATE_REJECTED_BONUS.equals(stateAtResultCallback)) {
                return;
            }
            manualFinishCapturePending = false;
            manualFinishAttempts = 0;
            lastScreenState = "RESULT";
            receiveRect = resultScreen.receiveRect;
            doubleValueRect = resultScreen.doubleValueRect;
            detectedResultValue = resultScreen.value;
            resultScreenLastSeenAt = System.currentTimeMillis();
            resultExitSeenAt = 0L;
            if (detectedResultValue == null || detectedResultValue.trim().isEmpty()) {
                persistResultSnapshot(fullFrame);
            } else {
                deleteResultSnapshot();
            }
            gameplayFramesAfterResult = 0;
            boolean receiveAlreadyLatched = prefs.getBoolean("resultReceiveLatched", false)
                && "RECEIVE".equals(prefs.getString("resultAction", ""));
            SharedPreferences.Editor resultEditor = prefs.edit()
                .putString("screenState", lastScreenState)
                .putString("resultValue", detectedResultValue)
                .putBoolean("resultConfirmationFallbackNeeded", false);
            if (receiveAlreadyLatched) {
                // A late OCR callback must never downgrade an already-observed Receber
                // back to RESULT_SCREEN. Persist the recovered value synchronously before
                // resuming the durable completion path.
                boolean resultPersisted = resultEditor
                    .putString("completionStatus", "RECEIVE_LATCHED")
                    .putBoolean("touchCaptureNeeded", false)
                    .commit();
                if (!resultPersisted) {
                    prefs.edit()
                        .putString("gtoTripIntegrityError", "Falha ao persistir o resultado recuperado após Receber.")
                        .putString("lastEvent", "Resultado recuperado, mas a persistência local falhou")
                        .apply();
                    return;
                }
                automaticResultCandidateMisses = 0;
                mainHandler.post(this::confirmNormalResultAutomatically);
                return;
            }
            boolean resultPersisted = resultEditor
                .putString("completionStatus", "RESULT_SCREEN")
                .putBoolean("touchCaptureNeeded", true)
                .commit();
            if (!resultPersisted) {
                prefs.edit()
                    .putString("gtoTripIntegrityError", "Falha ao persistir a tela de resultado antes de armar Receber.")
                    .putString("lastEvent", "Tela Concluído detectada, mas a persistência local falhou")
                    .apply();
                showStatusChip("Entrega detectada, mas o armazenamento local falhou. Não feche a tela Concluído e tente novamente.", 4800L);
                return;
            }
            automaticResultCandidateMisses = 0;

            if (STATE_TRIP_IN_PROGRESS.equals(getTripState())) {
                setTripState(STATE_RESULT_DETECTED, "Entrega concluída detectada: " + detectedResultValue);
                mainHandler.post(this::updateFreightTouchPulseSensor);
                announceDriverStage(
                    "RESULT_DETECTED",
                    detectedResultValue.isEmpty()
                        ? "Etapa 3/4 · Entrega detectada. Toque em Receber no GTO; não use ADS."
                        : "Etapa 3/4 · Entrega detectada · " + detectedResultValue + ". Toque em Receber; não use ADS.",
                    4200L,
                    false
                );
            }
            return;
        }

        if (STATE_TRIP_IN_PROGRESS.equals(getTripState()) && !manualFinishCapturePending) {
            // GtoResultVisualGate is intentionally permissive and exists only to wake OCR.
            // A gameplay scene can occasionally resemble the dark/gold completion dialog,
            // so a visual candidate by itself must never produce a driver-facing failure.
            // Only repeated OCR with semantic evidence from the real result dialog may
            // expose the manual confirmation fallback.
            if (hasPartialResultSemanticEvidence(normalized)) {
                automaticResultCandidateMisses++;
                if (automaticResultCandidateMisses >= AUTO_RESULT_FALLBACK_MISSES
                    && !prefs.getBoolean("resultConfirmationFallbackNeeded", false)) {
                    prefs.edit()
                        .putBoolean("resultConfirmationFallbackNeeded", true)
                        .putString("lastEvent", "Tela de conclusão parcialmente reconhecida; confirmação manual disponibilizada como contingência.")
                        .apply();
                    announceDriverStage(
                        "RESULT_CONFIRMATION_FALLBACK",
                        "A tela de conclusão foi identificada, mas faltou confirmar algum dado. Mantenha a tela Concluído aberta e toque em Confirmar conclusão da entrega.",
                        4600L,
                        true
                    );
                }
            } else {
                automaticResultCandidateMisses = 0;
            }
        }

        if (STATE_TRIP_IN_PROGRESS.equals(getTripState()) && manualFinishCapturePending) {
            manualFinishAttempts++;
            long elapsed = System.currentTimeMillis() - manualFinishRequestedAt;
            if (manualFinishAttempts >= MANUAL_FINISH_MAX_ATTEMPTS || elapsed >= MANUAL_FINISH_TIMEOUT_MS) {
                failManualFinishCapture();
            }
            return;
        }

        String stateAfterResult = getTripState();
        if ((STATE_RESULT_DETECTED.equals(stateAfterResult) || STATE_AWAITING_BONUS.equals(stateAfterResult))
            && System.currentTimeMillis() - resultScreenLastSeenAt >= 90L) {
            long now = System.currentTimeMillis();
            if (containsPostResultAdEvidence(normalized)) {
                persistArrivalCityFromSelectedFreight();
                prefs.edit()
                    .putString("completionStatus", "REJECTED_BONUS")
                    .putLong("completionDetectedAt", now)
                    .apply();
                setTripState(STATE_REJECTED_BONUS, "Fluxo de anúncio/bônus detectado após a entrega; viagem bloqueada");
                announceDriverStage(
                    "REJECTED_BONUS",
                    "Viagem não registrada · anúncio/bônus detectado. O resultado normal foi bloqueado.",
                    4200L,
                    true
                );
                return;
            }

            if (resultExitSeenAt == 0L) resultExitSeenAt = now;
            if (looksLikeGameplay(normalized)) {
                gameplayFramesAfterResult++;
                boolean actionBackedReturn = hasRecentNormalResultActionEvidence(now);
                if (!actionBackedReturn
                    && gameplayFramesAfterResult >= 2
                    && (resultTouchFallbackRequired || prefs.getBoolean("resultTouchFallbackRequired", false))
                    && !(resultTouchFallbackContinuityBroken || prefs.getBoolean("resultTouchFallbackContinuityBroken", false))) {
                    armResultTouchFallbackReady("GAMEPLAY_AFTER_RESULT");
                }
                // R3.6: normal completion requires an observed action on the result
                // screen. There is no elapsed-time limit after that action. A mere HUD
                // return without a result-screen action can no longer complete a trip.
                if (gameplayFramesAfterResult >= 2
                    && now - resultExitSeenAt >= 120L
                    && actionBackedReturn) {
                    confirmNormalResultAutomatically();
                    return;
                }
            } else {
                gameplayFramesAfterResult = 0;
                if (now - resultExitSeenAt >= 1800L && STATE_RESULT_DETECTED.equals(stateAfterResult)) {
                    // This is now an unresolved transition, not an implicit ADS verdict.
                    // It remains recoverable by a later action-backed gameplay/list return.
                    setTripState(STATE_AWAITING_BONUS, "Validando retorno do GTO após a conclusão");
                }
            }
        }

        boolean waitingForFreight = STATE_WAITING_FREIGHT.equals(getTripState());
        List<Rect> visualButtons = waitingForFreight ? detectAcceptButtonRects(fullFrame) : Collections.emptyList();
        int freightPage = waitingForFreight ? parseFreightPageNumber(lines) : -1;
        List<FreightOption> parsedOptions = waitingForFreight
            ? parseFreightOptions(lines, visualButtons)
            : Collections.emptyList();
        if (!parsedOptions.isEmpty()) {
            lastScreenState = "FREIGHT_LIST";
            lastFreightListSeenAt = System.currentTimeMillis();
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
            if (pendingFreightSelection != null && System.currentTimeMillis() - pendingFreightTouchAt > 2200L) {
                pendingFreightSelection = null;
                pendingFreightTouchAt = 0L;
                pendingSelectionSource = "";
                prefs.edit().remove("pendingFreight").remove("pendingSelectionSource").apply();
            }

            // Merge several consecutive native-resolution reads of the SAME page.
            // Numeric values must agree by row and text fields use majority voting,
            // which prevents one bad OCR frame from becoming the selected trip.
            List<FreightOption> stableOptions = stabilizeFreightOptions(freightPage, parsedOptions);
            learnCompanyCities(stableOptions);
            for (FreightOption option : stableOptions) {
                if (option.origin.isEmpty()) option.origin = resolveKnownOrigin(option.originCompany);
            }

            // Do not overwrite the pre-touch baseline while we are inside the
            // selection burst. That baseline is what lets us identify the exact row.
            if (System.currentTimeMillis() > visualSelectionUntil) {
                updateButtonBaselines(stableOptions, fullFrame, 1f);
            } else {
                carryButtonBaselines(stableOptions);
            }

            synchronized (freightOptions) {
                freightOptions.clear();
                freightOptions.addAll(stableOptions);
            }
            prefs.edit()
                .putString("screenState", lastScreenState)
                .putString("freightOptions", freightOptionsToJson(stableOptions))
                .putInt("freightCount", stableOptions.size())
                .putInt("freightPage", freightPage)
                .putLong("freightStableAt", freightHistoryUpdatedAt)
                .apply();
            return;
        }

        // Geometry is authoritative for list presence/count. OCR is allowed to lag or
        // miss a text field without making the app think the freight screen disappeared.
        if (waitingForFreight && visualButtons != null && !visualButtons.isEmpty()) {
            lastScreenState = "FREIGHT_LIST";
            lastFreightListSeenAt = System.currentTimeMillis();
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
            prefs.edit()
                .putString("screenState", lastScreenState)
                .putInt("freightCount", visualButtons.size())
                .putBoolean("touchCaptureNeeded", true)
                .apply();
            return;
        }

        String previousScreenState = lastScreenState;
        lastScreenState = "OTHER";
        prefs.edit().putString("screenState", lastScreenState).apply();

        if (STATE_WAITING_FREIGHT.equals(getTripState())
            && ("FREIGHT_LIST".equals(previousScreenState) || System.currentTimeMillis() - lastFreightListSeenAt <= 1800L)) {
            long now = System.currentTimeMillis();
            if (freightListMissingSince == 0L) freightListMissingSince = now;
            freightListMissingFrames++;
            // A page change can produce one transient OCR frame without cards. Requiring
            // two consecutive missing-list reads prevents navigation between pages from
            // being mistaken for accepting a freight.
            if (freightListMissingFrames >= 2 && now - freightListMissingSince >= 140L) {
                if (confirmFreightAfterListExit()) return;
                markFreightListClosed(now);
            }
        } else {
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
        }
    }

    private List<FreightOption> parseFreightOptions(List<OcrLine> lines, List<Rect> visualButtons) {
        if (captureWidth <= 0 || captureHeight <= 0) return Collections.emptyList();

        // The orange "Aceitar" buttons are a much stronger row anchor than OCR text.
        // Prefer image geometry whenever 2-5 buttons are visible; fall back to OCR only
        // when the visual detector is temporarily unavailable. This prevents adjacent
        // cards from being merged (the main cause of 4 visible jobs becoming 3 rows).
        List<Integer> rowCenters = new ArrayList<>();
        List<Rect> sortedButtons = new ArrayList<>();
        if (visualButtons != null) sortedButtons.addAll(visualButtons);
        sortedButtons.sort(Comparator.comparingInt(Rect::centerY));
        if (sortedButtons.size() >= 1 && sortedButtons.size() <= 6) {
            for (Rect rect : sortedButtons) rowCenters.add(rect.centerY());
        } else {
            List<Integer> anchors = new ArrayList<>();
            int minRightX = Math.round(captureWidth * 0.52f);
            for (OcrLine line : lines) {
                if (line.rect.centerX() < minRightX) continue;
                String n = normalize(line.text);
                boolean accept = n.contains("aceitar");
                boolean km = !extractKmDigits(line.text).isEmpty();
                boolean money = !extractMoneyDigits(line.text).isEmpty();
                if (accept || km || money) anchors.add(line.rect.centerY());
            }

            if (anchors.isEmpty()) return Collections.emptyList();
            Collections.sort(anchors);

            int clusterTolerance = Math.max(dp(22), Math.round(captureHeight * 0.044f));
            List<List<Integer>> clusters = new ArrayList<>();
            for (Integer y : anchors) {
                if (clusters.isEmpty()) {
                    List<Integer> first = new ArrayList<>();
                    first.add(y);
                    clusters.add(first);
                    continue;
                }
                List<Integer> last = clusters.get(clusters.size() - 1);
                int mean = 0;
                for (Integer value : last) mean += value;
                mean /= Math.max(1, last.size());
                if (Math.abs(y - mean) <= clusterTolerance) {
                    last.add(y);
                } else {
                    List<Integer> next = new ArrayList<>();
                    next.add(y);
                    clusters.add(next);
                }
            }

            for (List<Integer> cluster : clusters) {
                int sum = 0;
                for (Integer value : cluster) sum += value;
                rowCenters.add(sum / Math.max(1, cluster.size()));
            }

            List<Integer> compactCenters = new ArrayList<>();
            int minRowGap = Math.max(dp(42), Math.round(captureHeight * 0.072f));
            for (Integer center : rowCenters) {
                if (compactCenters.isEmpty()) {
                    compactCenters.add(center);
                    continue;
                }
                int last = compactCenters.get(compactCenters.size() - 1);
                if (center - last < minRowGap) {
                    compactCenters.set(compactCenters.size() - 1, (last + center) / 2);
                } else {
                    compactCenters.add(center);
                }
            }
            rowCenters = compactCenters;
        }

        if (rowCenters.isEmpty()) return Collections.emptyList();

        int defaultSpacing = Math.max(dp(90), Math.round(captureHeight * 0.175f));
        if (rowCenters.size() >= 2) {
            List<Integer> gaps = new ArrayList<>();
            for (int i = 1; i < rowCenters.size(); i++) {
                gaps.add(Math.abs(rowCenters.get(i) - rowCenters.get(i - 1)));
            }
            Collections.sort(gaps);
            defaultSpacing = gaps.get(gaps.size() / 2);
        }

        List<FreightOption> options = new ArrayList<>();
        for (int i = 0; i < rowCenters.size(); i++) {
            int centerY = rowCenters.get(i);
            int top = i == 0
                ? Math.max(0, centerY - defaultSpacing / 2)
                : (rowCenters.get(i - 1) + centerY) / 2;
            int bottom = i == rowCenters.size() - 1
                ? Math.min(captureHeight, centerY + defaultSpacing / 2)
                : (centerY + rowCenters.get(i + 1)) / 2;

            List<OcrLine> cardLines = new ArrayList<>();
            int contentLeftX = Math.round(captureWidth * 0.52f);
            if (!sortedButtons.isEmpty()) {
                int buttonLeft = sortedButtons.get(Math.min(i, sortedButtons.size() - 1)).left;
                contentLeftX = Math.max(Math.round(captureWidth * 0.42f), buttonLeft - Math.round(captureWidth * 0.265f));
            }
            for (OcrLine line : lines) {
                int cy = line.rect.centerY();
                if (cy < top || cy > bottom) continue;
                if (line.rect.centerX() < contentLeftX) continue;
                cardLines.add(line);
            }
            cardLines.sort((a, b) -> {
                int dy = Integer.compare(a.rect.top, b.rect.top);
                return dy != 0 ? dy : Integer.compare(a.rect.left, b.rect.left);
            });

            FreightOption option = new FreightOption();
            option.rowIndex = i;
            option.acceptCenterY = centerY;
            option.rowTop = top;
            option.rowBottom = bottom;
            option.rawText = joinCardText(cardLines);

            // Prefer the visually detected orange button. Its geometry is independent
            // from OCR and gives us an exact row target even when Android hides touch
            // coordinates or ML Kit misses the stylized word "Aceitar".
            if (sortedButtons.size() == rowCenters.size() && i < sortedButtons.size()) {
                option.acceptRect = new Rect(sortedButtons.get(i));
            } else {
                for (OcrLine line : cardLines) {
                    if (normalize(line.text).contains("aceitar")) {
                        option.acceptRect = new Rect(line.rect);
                        break;
                    }
                }
            }
            if (option.acceptRect == null) option.acceptRect = buttonRegionFor(option);

            // Numeric fields are read geometrically from the right side of the same card.
            OcrLine bestKm = null;
            OcrLine bestMoney = null;
            int bestKmDistance = Integer.MAX_VALUE;
            int bestMoneyDistance = Integer.MAX_VALUE;
            for (OcrLine line : cardLines) {
                String kmDigits = extractKmDigits(line.text);
                if (!kmDigits.isEmpty()) {
                    int d = Math.abs(line.rect.centerY() - centerY);
                    if (d < bestKmDistance) {
                        bestKmDistance = d;
                        bestKm = line;
                        option.km = kmDigits + "Km";
                    }
                }

                String moneyDigits = extractMoneyDigits(line.text);
                if (!moneyDigits.isEmpty()) {
                    int d = Math.abs(line.rect.centerY() - centerY);
                    if (d < bestMoneyDistance) {
                        bestMoneyDistance = d;
                        bestMoney = line;
                        option.offeredValue = "R$ " + moneyDigits;
                    }
                }
            }

            // Text fields: cargo is the first plain line; destination city is the
            // bottom-most plain line. Any wrapped text between '>' and the last line
            // belongs to the destination company name (e.g. "Fazenda" + "Areia Dourada").
            List<OcrLine> plain = new ArrayList<>();
            for (OcrLine line : cardLines) {
                String n = normalize(line.text);
                if (n.contains("aceitar")) continue;
                if (bestKm == line || bestMoney == line) continue;
                if (!extractKmDigits(line.text).isEmpty()) continue;
                if (!extractMoneyDigits(line.text).isEmpty()) continue;
                String cleaned = line.text.trim();
                if (!cleaned.isEmpty()) plain.add(line);
            }

            if (!plain.isEmpty()) option.cargo = cleanOcrLabel(plain.get(0).text);

            int routeIndex = -1;
            int separatorIndex = -1;
            for (int t = 0; t < plain.size(); t++) {
                separatorIndex = routeSeparatorIndex(plain.get(t).text);
                if (separatorIndex >= 0) {
                    routeIndex = t;
                    break;
                }
            }

            if (routeIndex >= 0) {
                int destinationIndex = plain.size() - 1;
                while (destinationIndex > routeIndex
                    && !looksLikePlaceName(cleanOcrLabel(plain.get(destinationIndex).text))) {
                    destinationIndex--;
                }
                if (destinationIndex > routeIndex) {
                    option.destination = cleanOcrLabel(plain.get(destinationIndex).text);
                }

                String routeHead = plain.get(routeIndex).text.trim();
                int sep = routeSeparatorIndex(routeHead);
                if (sep >= 0) {
                    option.originCompany = cleanOcrLabel(routeHead.substring(0, sep));
                    String after = routeHead.substring(Math.min(routeHead.length(), sep + routeSeparatorLength(routeHead, sep))).trim();
                    StringBuilder destinationCompany = new StringBuilder(cleanOcrLabel(after));
                    for (int t = routeIndex + 1; t < destinationIndex; t++) {
                        String continuation = cleanOcrLabel(plain.get(t).text);
                        if (!continuation.isEmpty()) {
                            if (destinationCompany.length() > 0) destinationCompany.append(' ');
                            destinationCompany.append(continuation);
                        }
                    }
                    option.destinationCompany = destinationCompany.toString().replaceAll("\\s+", " ").trim();
                    option.companyRoute = option.originCompany + (option.destinationCompany.isEmpty() ? "" : " > " + option.destinationCompany);
                }
            } else if (plain.size() >= 3) {
                // Rare fallback when OCR drops the separator glyph. The second line is
                // still the company route in the fixed GTO card layout. We keep only a
                // plausible left-hand company token and never invent a city/name.
                String destinationCandidate = cleanOcrLabel(plain.get(plain.size() - 1).text);
                if (looksLikePlaceName(destinationCandidate)) option.destination = destinationCandidate;
            }

            option.dataConfidence = freightFieldPresenceConfidence(option);
            if (!option.km.isEmpty() && !option.offeredValue.isEmpty()) {
                options.add(option);
            }
        }

        return options;
    }


    private int freightOcrLeftForCurrentLayout(int width) {
        if (width <= 2) return 0;
        List<Rect> buttons = fastLastSnapshotFrame == null ? Collections.emptyList() : fastLastSnapshotFrame.buttons;
        if (buttons != null && !buttons.isEmpty()) {
            return freightPanelLeftForButtons(width, buttons);
        }
        int rememberedButtonLeft = prefs == null ? 0 : prefs.getInt("freightButtonBandLeft", 0);
        if (rememberedButtonLeft >= Math.round(width * 0.60f) && rememberedButtonLeft < width) {
            return clamp(rememberedButtonLeft - Math.round(width * 0.300f), Math.round(width * 0.40f), width - 2);
        }
        return clamp(Math.round(width * 0.48f), 0, width - 2);
    }

    private int freightPanelLeftForButtons(int width, List<Rect> buttons) {
        int buttonLeft = Math.round(width * 0.910f);
        if (buttons != null && !buttons.isEmpty()) {
            buttonLeft = buttons.get(0).left;
            for (Rect rect : buttons) buttonLeft = Math.min(buttonLeft, rect.left);
        }
        return clamp(
            buttonLeft - Math.round(width * 0.300f),
            Math.round(width * 0.40f),
            Math.max(0, width - 2)
        );
    }

    private List<Rect> detectAcceptButtonRects(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || captureWidth <= 0 || captureHeight <= 0) {
            return Collections.emptyList();
        }

        final float[][] bands = new float[][] {
            {0.910f, 0.998f},
            {0.885f, 0.985f},
            {0.855f, 0.965f},
            {0.825f, 0.945f},
            {0.790f, 0.925f},
            {0.750f, 0.900f},
            {0.710f, 0.875f},
            {0.670f, 0.850f}
        };
        List<Rect> best = Collections.emptyList();
        float bestScore = -1f;
        for (float[] band : bands) {
            int left = clamp(Math.round(captureWidth * band[0]), 0, captureWidth - 2);
            int right = clamp(Math.round(captureWidth * band[1]), left + 1, captureWidth);
            List<Rect> candidate = detectAcceptButtonRectsInBand(bitmap, left, right);
            if (!plausibleAcceptStack(candidate)) continue;
            float score = candidate.size() * 2.0f + bitmapButtonBandScore(bitmap, candidate) + band[0] * 0.05f;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (!best.isEmpty()) {
            List<Rect> refined = new ArrayList<>();
            for (Rect rect : best) refined.add(refineBitmapButtonHorizontalBounds(bitmap, rect));
            best = refined;
            prefs.edit()
                .putInt("freightButtonBandLeft", best.get(0).left)
                .putInt("freightButtonBandRight", best.get(0).right)
                .putInt("freightDetectedButtonCount", best.size())
                .apply();
        }
        return best;
    }

    private Rect refineBitmapButtonHorizontalBounds(Bitmap bitmap, Rect coarse) {
        if (bitmap == null || bitmap.isRecycled() || coarse == null || coarse.width() <= 2 || coarse.height() <= 2) {
            return coarse == null ? new Rect() : new Rect(coarse);
        }
        int stepX = Math.max(1, captureWidth / 1600);
        int stepY = Math.max(1, captureHeight / 900);
        int first = -1;
        int last = -1;
        int gap = 0;
        int allowedGap = Math.max(2, Math.round(captureWidth * 0.0035f));
        for (int x = coarse.left; x < coarse.right; x += stepX) {
            int orange = 0;
            int total = 0;
            for (int y = coarse.top; y < coarse.bottom; y += stepY) {
                total++;
                if (isGtoOrange(bitmap.getPixel(x, y))) orange++;
            }
            boolean active = total > 0 && orange / (float) total >= 0.16f;
            if (active) {
                if (first < 0) first = x;
                last = x;
                gap = 0;
            } else if (first >= 0) {
                gap += stepX;
                if (gap > allowedGap) break;
            }
        }
        if (first < 0 || last < first || last - first < Math.max(8, Math.round(captureWidth * 0.018f))) {
            return new Rect(coarse);
        }
        int pad = Math.max(2, Math.round(captureWidth * 0.003f));
        return new Rect(
            clamp(first - pad, 0, captureWidth - 2),
            coarse.top,
            clamp(last + pad, first + 1, captureWidth),
            coarse.bottom
        );
    }

    private List<Rect> detectAcceptButtonRectsInBand(Bitmap bitmap, int left, int right) {
        int maxY = clamp(Math.round(captureHeight * 0.900f), 1, captureHeight);
        int stepY = Math.max(1, captureHeight / 720);
        int stepX = Math.max(2, captureWidth / 900);
        int allowedGap = Math.max(4, Math.round(captureHeight * 0.010f));
        int minHeight = Math.max(10, Math.round(captureHeight * 0.012f));

        List<Rect> result = new ArrayList<>();
        int runStart = -1;
        int lastActive = -1;
        for (int y = 0; y < maxY; y += stepY) {
            int orange = 0;
            int total = 0;
            for (int x = left; x < right; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                total++;
                if (isGtoOrange(pixel)) orange++;
            }
            float ratio = total == 0 ? 0f : orange / (float) total;
            boolean active = ratio >= 0.085f;
            if (active) {
                if (runStart < 0) runStart = y;
                lastActive = y;
            } else if (runStart >= 0 && lastActive >= 0 && y - lastActive > allowedGap) {
                addBitmapButtonRun(result, left, right, runStart, lastActive, minHeight);
                runStart = -1;
                lastActive = -1;
            }
        }
        if (runStart >= 0) addBitmapButtonRun(result, left, right, runStart, lastActive, minHeight);

        result.sort(Comparator.comparingInt(Rect::centerY));
        List<Rect> merged = new ArrayList<>();
        int mergeGap = Math.max(dp(7), Math.round(captureHeight * 0.016f));
        for (Rect rect : result) {
            if (merged.isEmpty()) {
                merged.add(new Rect(rect));
                continue;
            }
            Rect last = merged.get(merged.size() - 1);
            if (rect.top - last.bottom <= mergeGap) last.union(rect);
            else merged.add(new Rect(rect));
        }
        if (merged.size() > 6) {
            List<Integer> heights = new ArrayList<>();
            for (Rect rect : merged) heights.add(rect.height());
            Collections.sort(heights);
            final int median = heights.get(heights.size() / 2);
            merged.sort((a, b) -> Integer.compare(Math.abs(a.height() - median), Math.abs(b.height() - median)));
            merged = new ArrayList<>(merged.subList(0, 6));
            merged.sort(Comparator.comparingInt(Rect::centerY));
        }
        return merged;
    }

    private void addBitmapButtonRun(List<Rect> result, int left, int right, int runStart, int lastActive, int minHeight) {
        if (lastActive < runStart) return;
        int runHeight = lastActive - runStart;
        int maxHeight = Math.max(minHeight + 1, Math.round(captureHeight * 0.155f));
        if (runHeight >= minHeight && runHeight <= maxHeight) {
            int pad = Math.max(dp(2), Math.round(captureHeight * 0.004f));
            result.add(new Rect(left, Math.max(0, runStart - pad), right, Math.min(captureHeight, lastActive + pad)));
        }
    }

    private boolean plausibleAcceptStack(List<Rect> buttons) {
        if (buttons == null || buttons.isEmpty() || buttons.size() > 6) return false;
        int minHeight = Math.max(10, Math.round(captureHeight * 0.014f));
        int maxHeight = Math.max(minHeight + 1, Math.round(captureHeight * 0.160f));
        for (Rect rect : buttons) if (rect.height() < minHeight || rect.height() > maxHeight) return false;
        if (buttons.size() == 1) return buttons.get(0).centerY() <= Math.round(captureHeight * 0.32f);
        int minGap = Math.round(captureHeight * 0.060f);
        int maxGap = Math.round(captureHeight * 0.320f);
        for (int i = 1; i < buttons.size(); i++) {
            int gap = buttons.get(i).centerY() - buttons.get(i - 1).centerY();
            if (gap < minGap || gap > maxGap) return false;
        }
        return true;
    }

    private float bitmapButtonBandScore(Bitmap bitmap, List<Rect> buttons) {
        if (buttons == null || buttons.isEmpty()) return -1f;
        float score = 0f;
        for (Rect rect : buttons) score += orangeRatio(bitmap, rect, 1f);
        return score / buttons.size();
    }

    private boolean isGtoOrange(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int chroma = max - min;
        boolean referenceOrange = r >= 130 && g >= 65 && g <= 235 && b <= 175
            && r >= g + 12 && g >= b + 8;
        boolean scaledOrange = r >= 92 && g >= 45 && chroma >= 34
            && r >= g * 1.08f && g >= b * 1.06f;
        boolean darkOrange = r >= 82 && g >= 42 && b <= 105
            && r >= g * 1.16f && g >= b * 1.05f;
        return referenceOrange || scaledOrange || darkOrange;
    }

    private int parseFreightPageNumber(List<OcrLine> lines) {
        if (lines == null || captureWidth <= 0 || captureHeight <= 0) return -1;
        int buttonLeft = prefs == null ? 0 : prefs.getInt("freightButtonBandLeft", 0);
        int pageLeft = buttonLeft > 0 ? Math.max(Math.round(captureWidth * 0.42f), buttonLeft - Math.round(captureWidth * 0.22f)) : Math.round(captureWidth * 0.52f);
        int pageRight = buttonLeft > 0 ? Math.min(captureWidth, buttonLeft + Math.round(captureWidth * 0.02f)) : Math.round(captureWidth * 0.93f);
        for (OcrLine line : lines) {
            if (line.rect.centerY() < captureHeight * 0.86f) continue;
            if (line.rect.centerX() < pageLeft || line.rect.centerX() > pageRight) continue;
            String value = line.text == null ? "" : line.text.trim();
            if (value.matches("[1-9]")) return Integer.parseInt(value);
        }
        return -1;
    }

    private List<FreightOption> stabilizeFreightOptions(int page, List<FreightOption> parsed) {
        if (parsed == null || parsed.isEmpty()) return Collections.emptyList();

        boolean reset = freightHistory.isEmpty() || freightHistoryPage != page;
        if (!reset) {
            List<FreightOption> previous = freightHistory.get(freightHistory.size() - 1);
            if (previous.size() != parsed.size()) {
                reset = true;
            } else if (previous.size() >= 2 && freightNumericOverlap(previous, parsed) < 0.26f) {
                reset = true;
            }
        }
        if (reset) {
            freightHistory.clear();
            freightHistoryPage = page;
        }

        List<FreightOption> frameCopy = new ArrayList<>();
        for (FreightOption option : parsed) frameCopy.add(copyFreightOption(option));
        freightHistory.add(frameCopy);
        while (freightHistory.size() > FREIGHT_HISTORY_LIMIT) freightHistory.remove(0);
        freightHistoryUpdatedAt = System.currentTimeMillis();

        List<FreightOption> stable = new ArrayList<>();
        for (int row = 0; row < parsed.size(); row++) {
            FreightOption base = copyFreightOption(parsed.get(row));
            List<FreightOption> candidates = new ArrayList<>();
            for (List<FreightOption> frame : freightHistory) {
                if (frame.size() != parsed.size() || row >= frame.size()) continue;
                FreightOption candidate = frame.get(row);
                if (Math.abs(candidate.acceptCenterY - base.acceptCenterY) <= Math.max(dp(34), captureHeight / 18)) {
                    candidates.add(candidate);
                }
            }

            VoteResult cargo = voteText(candidates, "cargo");
            VoteResult company = voteText(candidates, "originCompany");
            VoteResult destinationCompany = voteText(candidates, "destinationCompany");
            VoteResult destination = voteText(candidates, "destination");
            VoteResult km = voteText(candidates, "km");
            VoteResult value = voteText(candidates, "offeredValue");

            if (!cargo.value.isEmpty()) base.cargo = cargo.value;
            if (!company.value.isEmpty()) base.originCompany = company.value;
            if (!destinationCompany.value.isEmpty()) base.destinationCompany = destinationCompany.value;
            if (!destination.value.isEmpty()) base.destination = destination.value;
            if (!km.value.isEmpty()) base.km = canonicalKm(km.value);
            if (!value.value.isEmpty()) base.offeredValue = canonicalMoney(value.value);
            base.companyRoute = base.originCompany + (base.destinationCompany.isEmpty() ? "" : " > " + base.destinationCompany);

            float evidence = 0f;
            evidence += voteEvidence(cargo, base.cargo);
            evidence += voteEvidence(company, base.originCompany);
            evidence += voteEvidence(destination, base.destination);
            evidence += voteEvidence(km, base.km);
            evidence += voteEvidence(value, base.offeredValue);
            base.dataConfidence = evidence / 5f;
            base.consensusFrames = candidates.size();
            stable.add(base);
        }
        return stable;
    }

    private float freightNumericOverlap(List<FreightOption> a, List<FreightOption> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) return 0f;
        int matches = 0;
        int compared = 0;
        for (int i = 0; i < a.size(); i++) {
            String ak = digitsOnly(a.get(i).km);
            String bk = digitsOnly(b.get(i).km);
            String av = digitsOnly(a.get(i).offeredValue);
            String bv = digitsOnly(b.get(i).offeredValue);
            if (!ak.isEmpty() && !bk.isEmpty()) {
                compared++;
                if (ak.equals(bk)) matches++;
            }
            if (!av.isEmpty() && !bv.isEmpty()) {
                compared++;
                if (av.equals(bv)) matches++;
            }
        }
        return compared == 0 ? 0f : matches / (float) compared;
    }

    private FreightOption copyFreightOption(FreightOption src) {
        FreightOption dst = new FreightOption();
        dst.rowIndex = src.rowIndex;
        dst.acceptRect = src.acceptRect == null ? null : new Rect(src.acceptRect);
        dst.acceptCenterY = src.acceptCenterY;
        dst.rowTop = src.rowTop;
        dst.rowBottom = src.rowBottom;
        dst.buttonOrangeBaseline = src.buttonOrangeBaseline;
        dst.lastButtonOrangeRatio = src.lastButtonOrangeRatio;
        dst.buttonVisualSignature = src.buttonVisualSignature == null ? new int[0] : src.buttonVisualSignature.clone();
        dst.cargo = src.cargo;
        dst.companyRoute = src.companyRoute;
        dst.originCompany = src.originCompany;
        dst.destinationCompany = src.destinationCompany;
        dst.origin = src.origin;
        dst.destination = src.destination;
        dst.km = src.km;
        dst.offeredValue = src.offeredValue;
        dst.rawText = src.rawText;
        dst.dataConfidence = src.dataConfidence;
        dst.consensusFrames = src.consensusFrames;
        return dst;
    }

    private VoteResult voteText(List<FreightOption> options, String field) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> originals = new HashMap<>();
        Map<String, Float> quality = new HashMap<>();
        int total = 0;
        for (FreightOption option : options) {
            String value;
            switch (field) {
                case "cargo": value = option.cargo; break;
                case "originCompany": value = option.originCompany; break;
                case "destinationCompany": value = option.destinationCompany; break;
                case "destination": value = option.destination; break;
                case "km": value = canonicalKm(option.km); break;
                case "offeredValue": value = canonicalMoney(option.offeredValue); break;
                default: value = "";
            }
            value = value == null ? "" : value.trim();
            if (value.isEmpty()) continue;
            String key = (field.equals("km") || field.equals("offeredValue")) ? digitsOnly(value) : normalize(value);
            if (key.isEmpty()) continue;
            total++;
            counts.put(key, counts.getOrDefault(key, 0) + 1);
            float q = textQuality(value);
            if (!originals.containsKey(key) || q > quality.getOrDefault(key, -1f)) {
                originals.put(key, value);
                quality.put(key, q);
            }
        }

        String bestKey = "";
        int bestCount = 0;
        float bestQuality = -1f;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            float q = quality.getOrDefault(entry.getKey(), 0f);
            if (entry.getValue() > bestCount || (entry.getValue() == bestCount && q > bestQuality)) {
                bestKey = entry.getKey();
                bestCount = entry.getValue();
                bestQuality = q;
            }
        }
        return new VoteResult(originals.getOrDefault(bestKey, ""), bestCount, total);
    }

    private float voteEvidence(VoteResult vote, String currentValue) {
        if (currentValue == null || currentValue.trim().isEmpty()) return 0f;
        if (vote.count >= 3) return 1f;
        if (vote.count == 2) return 0.92f;
        if (vote.count == 1 && (currentValue.contains("Km") || currentValue.startsWith("R$ "))) return 0.78f;
        if (vote.count == 1 && textQuality(currentValue) >= 0.72f) return 0.64f;
        return 0.45f;
    }

    private String cleanOcrLabel(String value) {
        if (value == null) return "";
        String cleaned = value
            .replace('¦', ' ')
            .replace('|', ' ')
            .replaceAll("[\\p{Cntrl}]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        cleaned = cleaned.replaceAll("^[^\\p{L}\\p{N}]+", "");
        cleaned = cleaned.replaceAll("[^\\p{L}\\p{N}À-ÿ'&().-]+$", "");
        return cleaned.trim();
    }

    private int routeSeparatorIndex(String text) {
        if (text == null) return -1;
        String[] separators = new String[] {">", "›", "»", "→", "->", " - "};
        int best = -1;
        for (String separator : separators) {
            int idx = text.indexOf(separator);
            if (idx > 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private int routeSeparatorLength(String text, int index) {
        if (text == null || index < 0 || index >= text.length()) return 1;
        if (text.startsWith("->", index)) return 2;
        if (text.startsWith(" - ", index)) return 3;
        return 1;
    }

    private boolean looksLikePlaceName(String value) {
        if (value == null) return false;
        String v = cleanOcrLabel(value);
        if (v.length() < 3 || v.length() > 38) return false;
        if (v.matches(".*[0-9$].*")) return false;
        String n = normalize(v);
        if (n.contains("aceitar") || n.contains("km") || n.contains("voltar")) return false;
        int letters = 0;
        int useful = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isLetter(c)) letters++;
            if (!Character.isWhitespace(c)) useful++;
        }
        return useful > 0 && letters / (float) useful >= 0.72f;
    }

    private boolean looksLikeEntityName(String value) {
        if (!looksLikePlaceName(value)) return false;
        String n = normalize(value);
        return !n.equals("receber") && !n.contains("valor a receber") && !n.contains("dobrar valor");
    }

    private float textQuality(String value) {
        if (value == null || value.trim().isEmpty()) return 0f;
        String v = value.trim();
        int letters = 0;
        int digits = 0;
        int noise = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else if (!Character.isWhitespace(c) && "-.'&()/R$".indexOf(c) < 0) noise++;
        }
        float useful = Math.max(1f, letters + digits);
        return Math.max(0f, Math.min(1f, (letters + digits) / (float) Math.max(1, v.length()) - noise / useful * 0.22f));
    }

    private float freightFieldPresenceConfidence(FreightOption option) {
        float score = 0f;
        if (looksLikeEntityName(option.cargo)) score += 1f;
        if (looksLikeEntityName(option.originCompany)) score += 1f;
        if (looksLikePlaceName(option.destination)) score += 1f;
        if (!digitsOnly(option.km).isEmpty()) score += 1f;
        if (!digitsOnly(option.offeredValue).isEmpty()) score += 1f;
        return score / 5f;
    }

    private String canonicalKm(String value) {
        String digits = digitsOnly(value);
        return digits.isEmpty() ? "" : digits + "Km";
    }

    private String canonicalMoney(String value) {
        String digits = digitsOnly(value);
        return digits.isEmpty() ? "" : "R$ " + digits;
    }

    private boolean isFreightDataReliable(FreightOption option) {
        if (option == null) return false;
        if (!looksLikeEntityName(option.cargo)) return false;
        if (!looksLikeEntityName(option.originCompany)) return false;
        if (!looksLikePlaceName(option.destination)) return false;
        String kmDigits = digitsOnly(option.km);
        String valueDigits = digitsOnly(option.offeredValue);
        if (kmDigits.isEmpty() || valueDigits.isEmpty()) return false;
        try {
            int km = Integer.parseInt(kmDigits);
            long value = Long.parseLong(valueDigits);
            if (km < 10 || km > 10000 || value < 100 || value > 100000000L) return false;
        } catch (Exception ignored) {
            return false;
        }
        return option.consensusFrames >= 2 && option.dataConfidence >= 0.78f;
    }

    private boolean hasPartialResultSemanticEvidence(String normalizedAll) {
        if (normalizedAll == null || normalizedAll.isEmpty()) return false;
        boolean valueLabel = normalizedAll.contains("valor a receber");
        boolean completionWord = normalizedAll.contains("concluido");
        boolean receiveAction = normalizedAll.contains("receber");
        boolean bonusAction = normalizedAll.contains("dobrar valor") || normalizedAll.contains("ads");

        // Strong partial evidence means OCR actually read vocabulary belonging to the
        // fixed GTO result dialog. Ordinary gameplay pixels are not enough.
        return valueLabel || (completionWord && (receiveAction || bonusAction));
    }

    private ResultScreen parseResultScreen(List<OcrLine> lines, String normalizedAll) {
        boolean valueLabel = normalizedAll.contains("valor a receber");
        boolean completionWord = normalizedAll.contains("concluido");
        boolean resultButtons = normalizedAll.contains("dobrar valor") || normalizedAll.contains("ads");
        if (!valueLabel || (!completionWord && !resultButtons)) return null;

        ResultScreen result = new ResultScreen();
        String all = normalizedAll.replace('\n', ' ');
        Matcher valueMatcher = Pattern.compile("valor a receber[^0-9]*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE).matcher(all);
        if (valueMatcher.find()) result.value = "R$ " + valueMatcher.group(1).trim();

        for (OcrLine line : lines) {
            String n = normalize(line.text);
            if (n.equals("receber") || (n.contains("receber") && !n.contains("valor a receber"))) {
                result.receiveRect = new Rect(line.rect);
            }
            if (n.contains("dobrar valor") || n.contains("ads")) {
                if (result.doubleValueRect == null) result.doubleValueRect = new Rect(line.rect);
                else result.doubleValueRect.union(line.rect);
            }
            if (result.value.isEmpty()) {
                Matcher money = Pattern.compile("R\\$\\s*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE).matcher(line.text);
                if (money.find()) result.value = "R$ " + money.group(1).trim();
            }
        }
        return result;
    }

    private void handleOutsideTouch(float rawX, float rawY, float alternateX, float alternateY) {
        outsideTouchCount++;
        lastOutsideTouchX = rawX;
        lastOutsideTouchY = rawY;
        lastOutsideAltX = alternateX;
        lastOutsideAltY = alternateY;
        lastOutsideTouchAt = System.currentTimeMillis();
        prefs.edit()
            .putInt("outsideTouchCount", outsideTouchCount)
            .putFloat("lastOutsideTouchX", rawX)
            .putFloat("lastOutsideTouchY", rawY)
            .putFloat("lastOutsideAltX", alternateX)
            .putFloat("lastOutsideAltY", alternateY)
            .putLong("lastOutsideTouchAt", lastOutsideTouchAt)
            .putString("lastEvent", "Toque externo detectado em " + Math.round(rawX) + "," + Math.round(rawY))
            .apply();

        String state = getTripState();

        // ACTION_OUTSIDE chega apenas como o primeiro DOWN. Em alguns aparelhos as
        // coordenadas podem estar no espaço da janela de overlay e o OCR pode mudar
        // para OTHER quase ao mesmo tempo em que o GTO fecha a lista. Por isso,
        // guardamos um candidato enquanto a lista foi vista recentemente e só
        // confirmamos a viagem quando a lista realmente desaparece.
        if (STATE_WAITING_FREIGHT.equals(state) && System.currentTimeMillis() - lastFreightListSeenAt <= 900L) {
            // Android frequently redacts ACTION_OUTSIDE coordinates. FIX9 never maps
            // the freight from x/y. The event only arms a short visual probe around
            // the five fixed GTO Accept buttons. Page arrows and other touches do not
            // change one Accept button, so they naturally produce no selection.
            armSelectionProbe(lastOutsideTouchAt);
            return;
        }

        // The observer may still be bootstrapping WAITING_FREIGHT while the driver taps
        // the already-open GTO list. Preserve that touch and correlate it with the exact
        // row transition from the preserved freight page.
        if (replacementFreightCandidateArmed
            && System.currentTimeMillis() - replacementFreightCandidateAt <= 900L) {
            replacementFreightTouchPending = true;
            replacementFreightTouchAt = lastOutsideTouchAt;
            prefs.edit()
                .putString("pendingSelectionSource", "bootstrap-touch")
                .putString("lastEvent", "Toque na lista detectado durante inicialização automática")
                .apply();
            return;
        }

        if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) {
            // Some Android builds redact ACTION_OUTSIDE coordinates as (0,0). At the
            // result screen we therefore treat the outside touch only as evidence that
            // the driver acted, then let OCR decide whether the next screen is normal
            // gameplay (Receber) or an advertisement/bonus flow (Dobrar valor).
            {
                resultActionTouchAt = lastOutsideTouchAt;
                resultExitSeenAt = 0L;
                gameplayFramesAfterResult = 0;
                prefs.edit()
                    .putLong("resultActionTouchAt", resultActionTouchAt)
                    .putBoolean("resultReceiveLatched", false)
                    .putString("completionStatus", "VERIFYING_RESULT_ACTION")
                    .putString("lastEvent", "Ação na tela de resultado detectada; verificando Receber x anúncio/bônus.")
                    .apply();
                prefs.edit().putString("resultAction", "TOUCH_PENDING").apply();
            }
        }
    }

    private FreightOption findFreightAt(float rawX, float rawY) {
        if (captureWidth <= 0 || captureHeight <= 0) return null;

        FreightOption nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        int horizontal = Math.max(dp(84), captureWidth / 18);
        int vertical = Math.max(dp(30), captureHeight / 28);

        synchronized (freightOptions) {
            for (FreightOption option : freightOptions) {
                if (option.acceptRect == null) continue;
                Rect touchTarget = new Rect(option.acceptRect);
                touchTarget.inset(-horizontal, -vertical);
                if (!touchTarget.contains(Math.round(rawX), Math.round(rawY))) continue;

                float distance = Math.abs(rawY - option.acceptCenterY);
                if (distance < nearestDistance) {
                    nearest = option;
                    nearestDistance = distance;
                }
            }

            // Fallback for OEMs that expose usable outside-touch coordinates. Anchor
            // the right-side threshold to the detected Aceitar column instead of a fixed
            // 72% screen coordinate.
            int detectedButtonLeft = prefs == null ? 0 : prefs.getInt("freightButtonBandLeft", 0);
            float rightSideThreshold = detectedButtonLeft > 0
                ? Math.max(captureWidth * 0.42f, detectedButtonLeft - captureWidth * 0.08f)
                : captureWidth * 0.52f;
            if (nearest == null && rawX >= rightSideThreshold) {
                float maxYDistance = Math.max(dp(54), captureHeight / 10f);
                for (FreightOption option : freightOptions) {
                    float distance = Math.abs(rawY - option.acceptCenterY);
                    if (distance <= maxYDistance && distance < nearestDistance) {
                        nearest = option;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private boolean confirmFreightAfterListExit() {
        long now = System.currentTimeMillis();
        if (selectionProbeActive) {
            if (finishSelectionProbeIfPossible(now, true)) return true;
            // The list may disappear before the pressed frame is delivered. Wait for
            // the probe timeout rather than guessing from list disappearance.
            if (now - selectionProbeStartedAt < SELECTION_PROBE_TIMEOUT_MS) return false;
        }
        FreightOption selected = null;
        String source = "";

        if (visualFreightSelection != null
            && now - lastOutsideTouchAt <= 1800L
            && visualSelectionConfidence >= 0.050f) {
            selected = visualFreightSelection;
            source = visualSelectionSource.isEmpty() ? "visual" : visualSelectionSource;
        }

        if (selected == null
            && pendingFreightSelection != null
            && now - pendingFreightTouchAt <= 2200L) {
            selected = pendingFreightSelection;
            source = pendingSelectionSource.isEmpty() ? "pending" : pendingSelectionSource;
        }

        if (selected == null) {
            prefs.edit()
                .putString("lastEvent", "A lista fechou, mas o frete não teve confirmação visual suficiente; nenhuma linha foi adivinhada.")
                .apply();
            return false;
        }

        // Accuracy gate: the system is allowed to miss a selection, but it is never
        // allowed to invent a company/city/value. Only start the trip when all four
        // required data fields plus the cargo are plausible and cross-checked.
        if (!isFreightDataReliable(selected)) {
            prefs.edit()
                .putString("lastEvent", "Frete localizado, porém os dados não atingiram confiança suficiente ("
                    + Math.round(selected.dataConfidence * 100f) + "%).")
                .apply();
            return false;
        }

        if (selected.origin.isEmpty()) selected.origin = resolveKnownOrigin(selected.originCompany);
        String json = freightOptionToJson(selected).toString();
        String summary = selected.summary();
        prefs.edit()
            .putString("selectedFreight", json)
            .putString("selectedFreightSummary", summary)
            .putString("selectedOrigin", selected.origin)
            .putString("selectedDestination", selected.destination)
            .putString("selectedOriginCompany", selected.originCompany)
            .putString("selectedDestinationCompany", selected.destinationCompany)
            .putString("selectedCargo", selected.cargo)
            .putString("selectedKm", selected.km)
            .putString("selectedValue", selected.offeredValue)
            .putString("selectionSource", source)
            .remove("pendingFreight")
            .remove("pendingSelectionSource")
            .apply();

        if (!GtoAutoTripSync.lockSelectedFreight(this, prefs)) {
            setTripState(STATE_WAITING_FREIGHT, "Frete detectado, mas a integridade dos dados não pôde ser bloqueada");
            showStatusChip("Frete não confirmado · integridade dos dados falhou.", 3200L);
            return false;
        }
        pendingFreightSelection = null;
        pendingFreightTouchAt = 0L;
        pendingSelectionSource = "";
        visualSelectionUntil = 0L;
        setTripState(STATE_TRIP_IN_PROGRESS, "Frete selecionado automaticamente: " + summary + " [" + source + "]");
        announceDriverStage(
            "TRIP_IN_PROGRESS",
            "Etapa 2/4 · Frete confirmado. Faça a rota normalmente. Ao chegar ao destino, a NVU registrará a viagem automaticamente.",
            4200L,
            false
        );
        return true;
    }

    private boolean isReliableOutsideCoordinate(float x, float y) {
        if (captureWidth <= 0 || captureHeight <= 0) return false;
        if (Math.abs(x) <= 1f && Math.abs(y) <= 1f) return false;
        return x >= 0f && y >= 0f && x <= captureWidth && y <= captureHeight;
    }

    private Rect buttonRegionFor(FreightOption option) {
        if (option != null && option.acceptRect != null
            && option.acceptRect.width() >= captureWidth * 0.035f
            && option.acceptRect.height() >= captureHeight * 0.018f) {
            Rect visual = new Rect(option.acceptRect);
            visual.inset(-dp(2), -dp(2));
            visual.intersect(0, 0, captureWidth, captureHeight);
            return visual;
        }
        int left = prefs == null
            ? Math.round(captureWidth * 0.910f)
            : prefs.getInt("freightButtonBandLeft", Math.round(captureWidth * 0.910f));
        int right = prefs == null
            ? Math.min(captureWidth, Math.round(captureWidth * 0.994f))
            : prefs.getInt("freightButtonBandRight", Math.min(captureWidth, Math.round(captureWidth * 0.994f)));
        left = clamp(left, 0, Math.max(0, captureWidth - 2));
        right = clamp(right, left + 1, captureWidth);
        int rowHeight = Math.max(dp(50), option.rowBottom - option.rowTop);
        int half = Math.max(dp(22), Math.round(rowHeight * 0.28f));
        int top = Math.max(0, option.acceptCenterY - half);
        int bottom = Math.min(captureHeight, option.acceptCenterY + half);
        return new Rect(left, top, right, bottom);
    }

    private void updateButtonBaselines(List<FreightOption> options, Bitmap bitmap, float scale) {
        if (bitmap == null || bitmap.isRecycled()) return;
        for (FreightOption option : options) {
            Rect region = buttonRegionFor(option);
            option.buttonOrangeBaseline = orangeRatio(bitmap, region, scale);
            option.buttonVisualSignature = buttonSignature(bitmap, region, scale);
        }
    }

    private void carryButtonBaselines(List<FreightOption> parsedOptions) {
        synchronized (freightOptions) {
            for (FreightOption parsed : parsedOptions) {
                FreightOption best = null;
                int bestDistance = Integer.MAX_VALUE;
                for (FreightOption old : freightOptions) {
                    int distance = Math.abs(old.acceptCenterY - parsed.acceptCenterY);
                    if (distance < bestDistance) {
                        best = old;
                        bestDistance = distance;
                    }
                }
                if (best != null && bestDistance <= Math.max(dp(40), captureHeight / 12)) {
                    parsed.buttonOrangeBaseline = best.buttonOrangeBaseline;
                    parsed.buttonVisualSignature = best.buttonVisualSignature;
                }
            }
        }
    }

    private void analyzeVisualSelectionFrame(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        List<FreightOption> snapshot = new ArrayList<>();
        synchronized (freightOptions) {
            snapshot.addAll(freightOptions);
        }
        if (snapshot.size() < 2) return;

        float bestDiff = 0f;
        float secondDiff = 0f;
        FreightOption best = null;
        int visibleOthersForBest = 0;

        for (FreightOption option : snapshot) {
            if (option.buttonOrangeBaseline < 0.18f) continue;
            Rect region = buttonRegionFor(option);
            float current = orangeRatio(bitmap, region, 1f);
            int[] currentSignature = buttonSignature(bitmap, region, 1f);
            float colorDrop = Math.max(0f, option.buttonOrangeBaseline - current);
            float signatureDiff = signatureDistance(option.buttonVisualSignature, currentSignature);
            float diff = Math.max(colorDrop, signatureDiff);
            option.lastButtonOrangeRatio = current;
            if (diff > bestDiff) {
                secondDiff = bestDiff;
                bestDiff = diff;
                best = option;
            } else if (diff > secondDiff) {
                secondDiff = diff;
            }
        }

        if (best == null) return;
        for (FreightOption option : snapshot) {
            if (option == best || option.buttonOrangeBaseline < 0.18f) continue;
            if (option.lastButtonOrangeRatio >= Math.max(0.16f, option.buttonOrangeBaseline * 0.52f)) {
                visibleOthersForBest++;
            }
        }

        float margin = bestDiff - secondDiff;
        if (bestDiff >= 0.072f && margin >= 0.018f && visibleOthersForBest >= 1) {
            float confidence = Math.min(1f, bestDiff + margin);
            if (visualFreightSelection == null || confidence > visualSelectionConfidence) {
                visualFreightSelection = best;
                visualSelectionConfidence = confidence;
                visualSelectionSource = "visual-row-" + (best.rowIndex + 1)
                    + "/d=" + Math.round(bestDiff * 100f)
                    + "/m=" + Math.round(margin * 100f);
                pendingSelectionSource = visualSelectionSource;
                prefs.edit()
                    .putString("pendingFreight", freightOptionToJson(best).toString())
                    .putString("pendingSelectionSource", visualSelectionSource)
                    .putString("lastEvent", "Botão Aceitar confirmado visualmente: " + best.summary())
                    .apply();
            }
        }
    }

    private int[] buttonSignature(Bitmap bitmap, Rect screenRect, float scale) {
        if (bitmap == null || bitmap.isRecycled() || screenRect == null) return new int[0];
        int left = clamp(Math.round(screenRect.left * scale), 0, Math.max(0, bitmap.getWidth() - 1));
        int right = clamp(Math.round(screenRect.right * scale), left + 1, bitmap.getWidth());
        int top = clamp(Math.round(screenRect.top * scale), 0, Math.max(0, bitmap.getHeight() - 1));
        int bottom = clamp(Math.round(screenRect.bottom * scale), top + 1, bitmap.getHeight());
        final int cols = 6;
        final int rows = 4;
        int[] signature = new int[cols * rows * 3];
        int index = 0;
        for (int gy = 0; gy < rows; gy++) {
            int y = top + Math.round((gy + 0.5f) * (bottom - top) / rows);
            y = clamp(y, top, bottom - 1);
            for (int gx = 0; gx < cols; gx++) {
                int x = left + Math.round((gx + 0.5f) * (right - left) / cols);
                x = clamp(x, left, right - 1);
                int r = 0, g = 0, b = 0, samples = 0;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int sx = clamp(x + ox, left, right - 1);
                        int sy = clamp(y + oy, top, bottom - 1);
                        int pixel = bitmap.getPixel(sx, sy);
                        r += Color.red(pixel);
                        g += Color.green(pixel);
                        b += Color.blue(pixel);
                        samples++;
                    }
                }
                signature[index++] = r / Math.max(1, samples);
                signature[index++] = g / Math.max(1, samples);
                signature[index++] = b / Math.max(1, samples);
            }
        }
        return signature;
    }

    private float signatureDistance(int[] baseline, int[] current) {
        if (baseline == null || current == null || baseline.length == 0 || baseline.length != current.length) return 0f;
        long sum = 0L;
        for (int i = 0; i < baseline.length; i++) sum += Math.abs(baseline[i] - current[i]);
        return sum / (255f * baseline.length);
    }

    private float orangeRatio(Bitmap bitmap, Rect screenRect, float scale) {
        if (bitmap == null || bitmap.isRecycled() || screenRect == null) return 0f;
        int left = clamp(Math.round(screenRect.left * scale), 0, Math.max(0, bitmap.getWidth() - 1));
        int right = clamp(Math.round(screenRect.right * scale), left + 1, bitmap.getWidth());
        int top = clamp(Math.round(screenRect.top * scale), 0, Math.max(0, bitmap.getHeight() - 1));
        int bottom = clamp(Math.round(screenRect.bottom * scale), top + 1, bitmap.getHeight());
        int step = Math.max(2, Math.round(5f * Math.max(0.35f, scale)));
        int orange = 0;
        int total = 0;
        for (int y = top; y < bottom; y += step) {
            for (int x = left; x < right; x += step) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                total++;
                if (r >= 135 && g >= 75 && g <= 205 && b <= 135 && r >= g + 18 && g >= b + 15) {
                    orange++;
                }
            }
        }
        return total == 0 ? 0f : orange / (float) total;
    }

    private String extractKmDigits(String value) {
        if (value == null) return "";
        String tolerant = value.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1');
        Matcher matcher = Pattern.compile("([0-9][0-9.,]*)\\s*[kK]\\s*[mM]").matcher(tolerant);
        if (!matcher.find()) return "";
        return digitsOnly(matcher.group(1));
    }

    private String extractMoneyDigits(String value) {
        if (value == null) return "";
        String tolerant = value.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1');
        Matcher matcher = Pattern.compile("[rR]\\s*[$sS]\\s*([0-9][0-9.,]*)").matcher(tolerant);
        if (!matcher.find()) return "";
        return digitsOnly(matcher.group(1));
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private void learnCompanyCities(List<FreightOption> options) {
        JSONObject map = readCompanyCityMap();
        boolean changed = false;
        for (FreightOption option : options) {
            if (option.destinationCompany.isEmpty() || option.destination.isEmpty()) continue;
            String key = normalize(option.destinationCompany);
            if (key.isEmpty()) continue;
            try {
                if (!option.destination.equals(map.optString(key, ""))) {
                    map.put(key, option.destination);
                    changed = true;
                }
            } catch (Exception ignored) {}
        }
        if (changed) prefs.edit().putString("companyCityMap", map.toString()).apply();
    }

    private JSONObject readCompanyCityMap() {
        try {
            return new JSONObject(prefs.getString("companyCityMap", "{}"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private String resolveKnownOrigin(String originCompany) {
        String currentCity = prefs.getString("currentGtoCity", "").trim();
        if (!currentCity.isEmpty()) return currentCity;
        if (originCompany == null || originCompany.trim().isEmpty()) return "";
        JSONObject map = readCompanyCityMap();
        return map.optString(normalize(originCompany), "").trim();
    }

    private void persistArrivalCityFromSelectedFreight() {
        String destination = prefs.getString("selectedDestination", "").trim();
        if (!destination.isEmpty()) prefs.edit().putString("currentGtoCity", destination).apply();
    }

    private FreightMatch findFreightFlexible(float rawX, float rawY, float alternateX, float alternateY) {
        if (captureWidth <= 0 || captureHeight <= 0) return null;

        FreightOption direct = findFreightAt(rawX, rawY);
        if (direct != null) return new FreightMatch(direct, "raw-xy");

        if (Math.abs(alternateX - rawX) > 1f || Math.abs(alternateY - rawY) > 1f) {
            FreightOption alternate = findFreightAt(alternateX, alternateY);
            if (alternate != null) return new FreightMatch(alternate, "alt-xy");
        }

        DisplayMetrics screen = realDisplayMetrics();
        float scaleY = screen.heightPixels > 0 ? captureHeight / (float) screen.heightPixels : 1f;
        float[] yCandidates = new float[] {
            rawY,
            alternateY,
            rawY * scaleY,
            alternateY * scaleY,
            captureHeight - rawY,
            captureHeight - alternateY,
            captureHeight - (rawY * scaleY),
            captureHeight - (alternateY * scaleY)
        };
        String[] names = new String[] {
            "raw-y", "alt-y", "raw-y-scaled", "alt-y-scaled",
            "raw-y-flip", "alt-y-flip", "raw-y-scaled-flip", "alt-y-scaled-flip"
        };

        FreightOption best = null;
        String bestSource = "";
        float bestDistance = Float.MAX_VALUE;
        float maxDistance = Math.max(dp(50), captureHeight / 11f);

        synchronized (freightOptions) {
            for (int c = 0; c < yCandidates.length; c++) {
                float y = yCandidates[c];
                if (y < 0 || y > captureHeight) continue;
                for (FreightOption option : freightOptions) {
                    float distance = Math.abs(y - option.acceptCenterY);
                    if (distance <= maxDistance && distance < bestDistance) {
                        best = option;
                        bestDistance = distance;
                        bestSource = names[c];
                    }
                }
            }
        }

        return best == null ? null : new FreightMatch(best, bestSource + "/d=" + Math.round(bestDistance));
    }

    private boolean isTapNearRect(float rawX, float rawY, Rect rect, boolean expandButton) {
        if (rect == null || captureWidth <= 0 || captureHeight <= 0) return false;
        Rect expanded = new Rect(rect);
        if (expandButton) {
            int horizontal = Math.max(dp(60), captureWidth / 18);
            int vertical = Math.max(dp(22), captureHeight / 30);
            expanded.inset(-horizontal, -vertical);
        }
        return expanded.contains(Math.round(rawX), Math.round(rawY));
    }

    private boolean containsPostResultAdEvidence(String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;
        return containsBonusVideo(normalized)
            || normalized.contains("anuncio")
            || normalized.contains("advertisement")
            || normalized.contains("rewarded")
            || normalized.contains("recompensa")
            || normalized.contains("assistir video")
            || normalized.contains("watch video")
            || normalized.contains("skip ad")
            || normalized.contains("pular anuncio");
    }

    private boolean looksLikeGameplay(String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;
        boolean hud = normalized.contains("km/h")
            || normalized.contains("km h")
            || normalized.contains("fps")
            || normalized.contains("desligado");
        boolean resultWords = normalized.contains("valor a receber") || normalized.contains("concluido");
        return hud && !resultWords && !containsPostResultAdEvidence(normalized);
    }

    private void persistResultSnapshot(Bitmap fullFrame) {
        if (fullFrame == null || fullFrame.isRecycled()) return;
        String sessionId = prefs.getString("gtoTripSessionId", "");
        if (sessionId == null || sessionId.trim().isEmpty()) return;
        Bitmap snapshot = null;
        FileOutputStream output = null;
        try {
            int width = fullFrame.getWidth();
            int height = fullFrame.getHeight();
            int left = clamp(Math.round(width * 0.20f), 0, width - 2);
            int top = clamp(Math.round(height * 0.12f), 0, height - 2);
            int right = clamp(Math.round(width * 0.80f), left + 1, width);
            int bottom = clamp(Math.round(height * 0.78f), top + 1, height);
            snapshot = Bitmap.createBitmap(fullFrame, left, top, right - left, bottom - top);

            File dir = new File(getNoBackupFilesDir(), "gto_result_runtime");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("snapshot-dir");
            String safeSession = sessionId.replaceAll("[^A-Za-z0-9._-]", "_");
            File target = new File(dir, safeSession + ".png");
            File temp = new File(dir, safeSession + ".tmp");
            output = new FileOutputStream(temp, false);
            if (!snapshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("snapshot-compress");
            }
            output.flush();
            output.close();
            output = null;
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("snapshot-replace");
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("snapshot-rename");
            }
            boolean snapshotPathPersisted = prefs.edit()
                .putString("resultSnapshotPath", target.getAbsolutePath())
                .putLong("resultSnapshotAt", System.currentTimeMillis())
                .remove("resultSnapshotError")
                .commit();
            if (!snapshotPathPersisted) {
                throw new IllegalStateException("snapshot-path-persist");
            }
        } catch (Exception ex) {
            prefs.edit()
                .putString("resultSnapshotError", describeError(ex))
                .putLong("resultSnapshotErrorAt", System.currentTimeMillis())
                .putString("lastEvent", "Não foi possível preservar a imagem local do resultado para recuperação")
                .apply();
        } finally {
            if (output != null) {
                try { output.close(); } catch (Exception ignored) {}
            }
            if (snapshot != null && !snapshot.isRecycled()) snapshot.recycle();
        }
    }

    private File resultSnapshotFileForCurrentSession() {
        if (prefs == null) return null;
        String sessionId = prefs.getString("gtoTripSessionId", "");
        if (sessionId == null || sessionId.trim().isEmpty()) return null;
        String safeSession = sessionId.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(new File(getNoBackupFilesDir(), "gto_result_runtime"), safeSession + ".png");
    }

    private void deleteResultSnapshot() {
        if (prefs == null) return;
        String path = prefs.getString("resultSnapshotPath", "");
        String deleteError = "";
        try {
            File file = path != null && !path.isEmpty() ? new File(path) : resultSnapshotFileForCurrentSession();
            if (file != null) {
                if (file.exists() && !file.delete()) {
                    deleteError = "Não foi possível remover a captura local do resultado.";
                }
                File parent = file.getParentFile();
                if (parent != null) {
                    File temp = new File(parent, file.getName().replace(".png", ".tmp"));
                    if (temp.exists() && !temp.delete() && deleteError.isEmpty()) {
                        deleteError = "Não foi possível remover o arquivo temporário da captura do resultado.";
                    }
                }
            }
        } catch (Exception ex) {
            deleteError = "Falha ao limpar captura local do resultado: " + describeError(ex);
        }
        android.content.SharedPreferences.Editor editor = prefs.edit()
            .remove("resultSnapshotPath")
            .remove("resultSnapshotAt");
        if (deleteError.isEmpty()) {
            editor.remove("resultSnapshotError").remove("resultSnapshotErrorAt");
        } else {
            editor
                .putString("resultSnapshotError", deleteError)
                .putLong("resultSnapshotErrorAt", System.currentTimeMillis())
                .putString("lastEvent", deleteError);
        }
        editor.apply();
    }

    private boolean recoverResultValueFromSnapshotAsync() {
        if (prefs == null || textRecognizer == null) return false;
        String path = prefs.getString("resultSnapshotPath", "");
        File file = path == null || path.trim().isEmpty()
            ? resultSnapshotFileForCurrentSession()
            : new File(path);
        if (file == null) return false;
        if (!file.exists() || !file.isFile()) return false;
        if (!resultSnapshotRecoveryBusy.compareAndSet(false, true)) return true;
        final long recoveryGeneration = ++resultSnapshotRecoveryGeneration;
        final String recoverySessionId = prefs.getString("gtoTripSessionId", "");

        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                if (recoveryGeneration == resultSnapshotRecoveryGeneration) resultSnapshotRecoveryBusy.set(false);
                prefs.edit().putString("resultSnapshotError", "BitmapFactory retornou vazio").apply();
                return false;
            }
            final Bitmap recoveryBitmap = bitmap;
            InputImage input = InputImage.fromBitmap(recoveryBitmap, 0);
            textRecognizer.process(input)
                .addOnSuccessListener(text -> {
                    if (recoveryGeneration != resultSnapshotRecoveryGeneration
                        || !recoverySessionId.equals(prefs.getString("gtoTripSessionId", ""))) return;
                    String recovered = extractResultValueFromRawOcr(text == null ? "" : text.getText());
                    if (!recovered.isEmpty()) {
                        detectedResultValue = recovered;
                        boolean stored = prefs.edit()
                            .putString("resultValue", recovered)
                            .remove("gtoTripIntegrityError")
                            .putString("lastEvent", "Valor final recuperado da captura local preservada")
                            .commit();
                        if (stored) {
                            deleteResultSnapshot();
                            mainHandler.post(this::confirmNormalResultAutomatically);
                        }
                    } else {
                        prefs.edit()
                            .putString("resultSnapshotError", "OCR de recuperação não encontrou Valor a receber")
                            .putString("gtoTripIntegrityError", "Receber foi confirmado, mas o valor final ainda não pôde ser lido da captura preservada.")
                            .putString("lastEvent", "Captura de resultado preservada, mas valor final ainda não foi reconhecido")
                            .apply();
                    }
                })
                .addOnFailureListener(error -> {
                    if (recoveryGeneration != resultSnapshotRecoveryGeneration
                        || !recoverySessionId.equals(prefs.getString("gtoTripSessionId", ""))) return;
                    prefs.edit()
                        .putString("resultSnapshotError", describeError(error))
                        .putString("gtoTripIntegrityError", "Falha ao reler o valor final da captura preservada.")
                        .putString("lastEvent", "Falha no OCR de recuperação do resultado")
                        .apply();
                })
                .addOnCompleteListener(task -> {
                    if (!recoveryBitmap.isRecycled()) recoveryBitmap.recycle();
                    if (recoveryGeneration == resultSnapshotRecoveryGeneration) {
                        resultSnapshotRecoveryBusy.set(false);
                    }
                });
            return true;
        } catch (Exception ex) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (recoveryGeneration == resultSnapshotRecoveryGeneration) resultSnapshotRecoveryBusy.set(false);
            prefs.edit()
                .putString("resultSnapshotError", describeError(ex))
                .putString("gtoTripIntegrityError", "Falha ao abrir a captura local preservada do resultado.")
                .apply();
            return false;
        }
    }

    private String extractResultValueFromRawOcr(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        Matcher labelled = Pattern.compile(
            "valor\\s*a\\s*receber[^0-9]{0,48}(?:R\\$\\s*)?([0-9][0-9.,\\s]{1,18})",
            Pattern.CASE_INSENSITIVE
        ).matcher(normalize(raw));
        if (labelled.find()) {
            String digits = labelled.group(1).replaceAll("\\s+", "").trim();
            if (!digits.isEmpty()) return "R$ " + digits;
        }
        Matcher money = Pattern.compile("R\\$\\s*([0-9][0-9.,]{1,18})", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (money.find()) return "R$ " + money.group(1).trim();
        return "";
    }

    private void confirmNormalResultAutomatically() {
        String currentState = getTripState();
        if (STATE_RESULT_CONFIRMED.equals(currentState)) return;
        if (!STATE_RESULT_DETECTED.equals(currentState) && !STATE_AWAITING_BONUS.equals(currentState)) return;
        if (detectedResultValue == null || detectedResultValue.trim().isEmpty()) {
            detectedResultValue = prefs.getString("resultValue", "");
        }
        if (detectedResultValue == null || detectedResultValue.trim().isEmpty()) {
            detectedResultValue = recoverResultValueFromPersistedOcr();
            if (!detectedResultValue.isEmpty()) {
                prefs.edit().putString("resultValue", detectedResultValue).apply();
            }
        }
        if (detectedResultValue == null || detectedResultValue.trim().isEmpty()) {
            if (recoverResultValueFromSnapshotAsync()) {
                prefs.edit()
                    .putString("completionStatus", "RECEIVE_LATCHED_RECOVERING_VALUE")
                    .putString("lastEvent", "Receber confirmado; relendo valor da captura local preservada")
                    .apply();
                showStatusChip("Receber confirmado · recuperando o valor final da captura preservada…", 4200L);
                return;
            }
            // Exact Receber is still latched and never expires. Do not transition to a
            // completed state until the monetary value is durable; otherwise a rare
            // storage/OCR failure could create an incomplete payload.
            prefs.edit()
                .putString("completionStatus", "RECEIVE_LATCHED_WAITING_VALUE")
                .putString("gtoTripIntegrityError", "Receber confirmado, mas o valor final ainda não pôde ser recuperado.")
                .putString("lastEvent", "Receber confirmado; aguardando recuperar o valor detectado")
                .apply();
            showStatusChip("Receber confirmado · recuperando o valor final da entrega…", 3600L);
            return;
        }
        persistArrivalCityFromSelectedFreight();
        long now = System.currentTimeMillis();
        boolean completionPersisted = prefs.edit()
            .putString("finalGain", detectedResultValue)
            .putString("completionStatus", "CONFIRMED_NORMAL")
            .putLong("completionDetectedAt", now)
            .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_PENDING)
            .putString("gtoTripIntegrityStatus", "COMPLETION_PERSISTED")
            .remove("gtoTripIntegrityError")
            .commit();
        if (!completionPersisted) {
            // Never advance the state machine when synchronous durable persistence fails.
            // The RECEIVE latch remains intact so a later retry can safely resume.
            prefs.edit()
                .putString("gtoTripSyncError", "Falha ao persistir a conclusão local; dados do frete permanecem bloqueados.")
                .putString("lastEvent", "Falha ao persistir conclusão; Receber permanece bloqueado para retry")
                .apply();
            showStatusChip("Entrega concluída · falha de persistência local. A viagem continua preservada.", 4600L);
            return;
        }
        deleteResultSnapshot();
        setTripState(STATE_RESULT_CONFIRMED, "Entrega finalizada e recebimento normal confirmado: " + detectedResultValue);
        announceDriverStage(
            "SYNCING",
            "Etapa 4/4 · Recebimento confirmado. Enviando a viagem automaticamente para a NVU…",
            4200L,
            false
        );
        GtoAutoTripSync.enqueueConfirmedTrip(this, prefs, automaticTripSyncListener());
    }

    private String recoverResultValueFromPersistedOcr() {
        String raw = prefs == null ? "" : prefs.getString("lastOcrText", "");
        return extractResultValueFromRawOcr(raw);
    }

    private GtoAutoTripSync.Listener automaticTripSyncListener() {
        return new GtoAutoTripSync.Listener() {
            @Override
            public void onSynced(String sessionId, String tripId) {
                mainHandler.post(() -> {
                    String currentSession = prefs.getString("gtoTripSessionId", "");
                    if (!sessionId.equals(currentSession)) return;
                    if (isOperationClosedForNewTrip()) {
                        announceDriverStage(
                            "SYNCED",
                            "Concluído · viagem registrada automaticamente e operação finalizada!",
                            4400L,
                            true
                        );
                    } else {
                        announceDriverStage(
                            "SYNCED",
                            "Concluído · viagem registrada automaticamente na NVU!",
                            3900L,
                            true
                        );
                    }
                    updateNotification();
                    if (menuView != null) refreshMenuContents();
                });
            }

            @Override
            public void onPending(String sessionId, String message) {
                mainHandler.post(() -> {
                    String currentSession = prefs.getString("gtoTripSessionId", "");
                    if (!sessionId.isEmpty() && !sessionId.equals(currentSession)) return;
                    String detail = message == null ? "" : message.trim();
                    String stageMessage = detail.isEmpty()
                        ? "Entrega preservada no aparelho · aguardando nova tentativa automática de envio."
                        : "Entrega preservada no aparelho · " + detail;
                    prefs.edit()
                        .putString("driverStageCode", "SYNC_PENDING")
                        .putString("driverStageMessage", stageMessage)
                        .putLong("driverStageAt", System.currentTimeMillis())
                        .apply();
                    updateNotification();
                    if (menuView != null) refreshMenuContents();
                });
            }
        };
    }

    private void flushAutomaticTripQueue() {
        GtoAutoTripSync.flushPending(this, prefs, automaticTripSyncListener());
    }

    private boolean containsBonusVideo(String normalized) {
        boolean bonus = normalized.contains("bonus") || normalized.contains("bonificacao") || normalized.contains("recompensa");
        boolean video = normalized.contains("video") || normalized.contains("anuncio") || normalized.contains("assistiu");
        return bonus && video;
    }

    private void setTripState(String state, String event) {
        lastStateChangeAt = System.currentTimeMillis();
        prefs.edit()
            .putString("tripState", state)
            .putString("lastEvent", event)
            .putLong("tripStateChangedAt", lastStateChangeAt)
            .apply();
        updateNotification();
        mainHandler.post(this::updateFreightTouchPulseSensor);
        mainHandler.post(this::updateProjectionReauthButton);
        if (menuView != null) mainHandler.post(this::refreshMenuContents);
    }

    private String getTripState() {
        return prefs.getString("tripState", STATE_IDLE);
    }

    private String waitingDiagnostics() {
        int count = prefs.getInt("freightCount", 0);
        int touches = prefs.getInt("outsideTouchCount", outsideTouchCount);
        DisplayMetrics screen = realDisplayMetrics();
        String screenState = prefs.getString("screenState", lastScreenState);
        String savedProjection = prefs.getString("projectionStatus", projectionStatus);
        String projectionError = prefs.getString("projectionError", "");
        String line = "Leitura: " + screenState + " · fretes: " + count
            + "\nCaptura: " + savedProjection + " · " + captureWidth + "x" + captureHeight
            + " / tela " + screen.widthPixels + "x" + screen.heightPixels
            + "\nToques externos: " + touches
            + " · último " + Math.round(prefs.getFloat("lastOutsideTouchX", -1f)) + "," + Math.round(prefs.getFloat("lastOutsideTouchY", -1f));
        String pending = prefs.getString("pendingSelectionSource", "");
        if (!pending.isEmpty()) line += "\nCandidato: " + truncate(pending, 58);
        if (visualFreightSelection != null) {
            line += "\nVisual: linha " + (visualFreightSelection.rowIndex + 1)
                + " · conf " + Math.round(visualSelectionConfidence * 100f) + "%";
        }
        if (!projectionError.isEmpty()) line += "\nErro: " + truncate(projectionError, 74);
        return line;
    }

    private String statusLabel(String state) {
        if (STATE_WAITING_FREIGHT.equals(state)) return "Escolha seu frete";
        if (STATE_CONFIRMING_FREIGHT.equals(state)) return "Confirmando frete";
        if (STATE_TRIP_IN_PROGRESS.equals(state)) return "Viagem em andamento";
        if (STATE_RESULT_DETECTED.equals(state)) return "Entrega concluída";
        if (STATE_AWAITING_BONUS.equals(state)) return "Validando o recebimento";
        return state;
    }

    private String freightOptionsToJson(List<FreightOption> options) {
        JSONArray array = new JSONArray();
        for (FreightOption option : options) array.put(freightOptionToJson(option));
        return array.toString();
    }

    private JSONObject freightOptionToJson(FreightOption option) {
        JSONObject json = new JSONObject();
        try {
            json.put("row", option.rowIndex);
            json.put("cargo", option.cargo);
            json.put("companyRoute", option.companyRoute);
            json.put("originCompany", option.originCompany);
            json.put("destinationCompany", option.destinationCompany);
            json.put("origin", option.origin);
            json.put("destination", option.destination);
            json.put("km", option.km);
            json.put("offeredValue", option.offeredValue);
            json.put("rawText", option.rawText);
            json.put("confidence", Math.round(option.dataConfidence * 100f));
            json.put("consensusFrames", option.consensusFrames);
        } catch (Exception ignored) {}
        return json;
    }

    private String joinCardText(List<OcrLine> lines) {
        StringBuilder result = new StringBuilder();
        for (OcrLine line : lines) {
            if (result.length() > 0) result.append(" | ");
            result.append(line.text);
        }
        return result.toString();
    }

    private String normalize(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
        return normalized;
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private DisplayMetrics realDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        return metrics;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    private void stopProjection() {
        boolean wasActive = projectionActive || mediaProjection != null || virtualDisplay != null;
        // Invalidate callbacks before calling MediaProjection.stop(). Android is allowed
        // to deliver onStop asynchronously, including after a replacement session starts.
        projectionGeneration++;
        projectionActive = false;
        pendingCapturedWidth = 0;
        pendingCapturedHeight = 0;
        if (wasActive) projectionStatus = "STOPPED";
        prefs.edit()
            .putBoolean("projectionActive", false)
            .putString("projectionStatus", projectionStatus)
            .putBoolean("touchCaptureNeeded", false)
            .apply();
        hideFreightTouchPulseSensor();
        releaseCaptureResources(true);
    }

    private void releaseCaptureResources(boolean stopMediaProjection) {
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (stopMediaProjection && mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
        }
        mediaProjection = null;
        if (captureThread != null) {
            try { captureThread.quitSafely(); } catch (Exception ignored) {}
            captureThread = null;
            captureHandler = null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (prefs != null) {
            prefs.edit()
                .putLong("observerTaskRemovedAt", System.currentTimeMillis())
                .putString("lastEvent", "Interface NVU removida; observador GTO e dados da viagem permanecem preservados")
                .apply();
        }
        if (GtoAutoTripSync.hasPending(this)) {
            mainHandler.post(this::flushAutomaticTripQueue);
        }
        // The service is stopWithTask=false + START_STICKY. Do not launch activities or
        // a new MediaProjection here; Android may legitimately keep/restart the service.
        // If the process itself dies, onCreate restores the durable trip snapshot and
        // requests a fresh projection authorization because projection tokens are not
        // process-persistent.
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        destroying = true;
        running = false;
        mainHandler.removeCallbacks(foregroundPoll);
        stopProjection();
        hideOverlays();
        if (textRecognizer != null) {
            try { textRecognizer.close(); } catch (Exception ignored) {}
        }
        if (selectionTextRecognizer != null) {
            try { selectionTextRecognizer.close(); } catch (Exception ignored) {}
        }
        synchronized (freightFrameLock) {
            if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                latestFreightPanelFrame.recycle();
            }
            latestFreightPanelFrame = null;
            recycleFrozenSelectionPanel();
            frozenSelectionButtons.clear();
        }
        if (pendingSelectionTransaction != null) {
            pendingSelectionTransaction.close();
            pendingSelectionTransaction = null;
        }
        selectionCoordinator.reset();
        prefs.edit()
            .putBoolean("projectionActive", false)
            .putBoolean("gtoForeground", false)
            .putBoolean("touchCaptureNeeded", false)
            .putBoolean("projectionPermissionInFlight", false)
            .putBoolean("overlayVisible", false)
            .putLong("serviceHeartbeatAt", 0L)
            .apply();
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class OcrLine {
        final String text;
        final Rect rect;
        final float confidence;

        OcrLine(String text, Rect rect) {
            this(text, rect, 0f);
        }

        OcrLine(String text, Rect rect, float confidence) {
            this.text = text;
            this.rect = rect;
            this.confidence = confidence;
        }
    }

    private static final class SequencedFastFrame {
        final long sequence;
        final GtoFastVisualDetector.Frame frame;

        SequencedFastFrame(long sequence, GtoFastVisualDetector.Frame frame) {
            this.sequence = sequence;
            this.frame = frame;
        }
    }

    private static final class FreightSelectionTransaction {
        final int rowIndex;
        final Bitmap panelFrame;
        final int panelOffsetX;
        final List<Rect> buttons;
        final String source;
        final long touchSequence;
        final String sessionId;
        final long generation;
        final long createdAt;
        private boolean closed = false;

        FreightSelectionTransaction(
            int rowIndex,
            Bitmap panelFrame,
            int panelOffsetX,
            List<Rect> buttons,
            String source,
            long touchSequence,
            String sessionId,
            long generation
        ) {
            this.rowIndex = rowIndex;
            this.panelFrame = panelFrame;
            this.panelOffsetX = panelOffsetX;
            this.buttons = buttons;
            this.source = source;
            this.touchSequence = touchSequence;
            this.sessionId = sessionId == null ? "" : sessionId;
            this.generation = generation;
            this.createdAt = System.currentTimeMillis();
        }

        void close() {
            if (closed) return;
            closed = true;
            if (panelFrame != null && !panelFrame.isRecycled()) panelFrame.recycle();
        }
    }

    private static class ButtonFrameSample {
        long at;
        final List<Rect> buttons = new ArrayList<>();
        final List<int[]> signatures = new ArrayList<>();
        final List<Float> orangeRatios = new ArrayList<>();

        ButtonFrameSample copy() {
            ButtonFrameSample copy = new ButtonFrameSample();
            copy.at = at;
            for (Rect rect : buttons) copy.buttons.add(new Rect(rect));
            for (int[] signature : signatures) copy.signatures.add(signature == null ? new int[0] : signature.clone());
            copy.orangeRatios.addAll(orangeRatios);
            return copy;
        }
    }

    private static class FreightMatch {
        final FreightOption option;
        final String source;

        FreightMatch(FreightOption option, String source) {
            this.option = option;
            this.source = source;
        }
    }

    private static class FreightOption {
        int rowIndex;
        Rect acceptRect;
        int acceptCenterY;
        int rowTop;
        int rowBottom;
        float buttonOrangeBaseline = 0f;
        float lastButtonOrangeRatio = 0f;
        int[] buttonVisualSignature = new int[0];
        String cargo = "";
        String companyRoute = "";
        String originCompany = "";
        String destinationCompany = "";
        String origin = "";
        String destination = "";
        String km = "";
        String offeredValue = "";
        String rawText = "";
        float dataConfidence = 0f;
        int consensusFrames = 0;

        String summary() {
            List<String> parts = new ArrayList<>();
            if (!origin.isEmpty() && !destination.isEmpty()) parts.add(origin + " → " + destination);
            else if (!destination.isEmpty()) parts.add(destination);
            if (!km.isEmpty()) parts.add(km);
            if (!offeredValue.isEmpty()) parts.add(offeredValue);
            return parts.isEmpty() ? "linha " + (rowIndex + 1) : android.text.TextUtils.join(" · ", parts);
        }
    }

    private static class VoteResult {
        final String value;
        final int count;
        final int total;

        VoteResult(String value, int count, int total) {
            this.value = value == null ? "" : value;
            this.count = count;
            this.total = total;
        }
    }

    private static class ResultScreen {
        String value = "";
        Rect receiveRect;
        Rect doubleValueRect;
    }
}
