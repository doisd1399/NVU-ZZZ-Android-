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
import android.graphics.Insets;
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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    // R3.21: do not release visual/OCR analysis merely because the VirtualDisplay was
    // created. GTO must be freshly foreground and the final capture geometry must pass
    // the dedicated three-frame stability gate first.
    private static final long CAPTURE_GEOMETRY_POLL_INTERVAL_MS = 16L;
    // R3.11 low-end hardening: runtime diagnostics are useful, but persisting them on
    // every 350 ms poll/frame queues unnecessary SharedPreferences disk work on slower
    // devices. Detection remains real-time in memory; only diagnostic snapshots are
    // rate-limited.
    private static final long HEARTBEAT_PERSIST_INTERVAL_MS = 1200L;
    private static final long FOREGROUND_STATUS_PERSIST_INTERVAL_MS = 1000L;
    private static final long FREIGHT_RUNTIME_PERSIST_INTERVAL_MS = 300L;
    // Visual GTO proof is intentionally short-lived. It exists only to bridge OEMs
    // that delay/omit UsageEvents after MediaProjection returns to the game. A valid
    // freight-list capture refreshes it continuously while the list is visible.
    private static final long VISUAL_GTO_EVIDENCE_FRESH_MS = 2400L;
    private static final long FREIGHT_PAGE_OCR_REFRESH_MS = 1800L;
    private static final long PRECISE_OCR_BUSY_RETRY_MS = 80L;
    private static final long PRECISE_OCR_BUSY_WAIT_TIMEOUT_MS = 8000L;
    private static final long FREIGHT_CONFIRMATION_WATCHDOG_MS = 7000L;
    private static final long PERMISSION_RETURN_GRACE_MS = 6500L;
    // The system consent host must return a result. If an OEM destroys that Activity or
    // drops the callback, never leave the observer indefinitely stuck in the in-flight
    // state. A late RESULT_OK is still accepted if it arrives after this watchdog.
    private static final long PROJECTION_PERMISSION_RESULT_WATCHDOG_MS = 45_000L;
    // Initial MediaProjection consent must appear only after the real GTO task is
    // foreground. This prevents creating a portrait capture surface from the NVU UI.
    // HF5: permission is event-gated by real landscape geometry, not a blind long timer.
    // A short minimum avoids opening consent during the first GTO transition; the
    // landscape-stability check below is authoritative and may wait longer automatically.
    private static final long INITIAL_PROJECTION_AFTER_GTO_DELAY_MS = 900L;
    private static final long PROJECTION_PERMISSION_LANDSCAPE_SETTLE_MS = 420L;
    private static final long PROJECTION_SURFACE_LANDSCAPE_SETTLE_MS = 560L;
    private static final int PROJECTION_SURFACE_STABLE_POLLS = 3;
    private static final long CAPTURE_RESIZE_CALLBACK_GRACE_MS = 900L;
    private static final long PROJECTION_FIRST_FRAME_WATCHDOG_MS = 2800L;
    private static final long PROJECTION_STALE_FRAME_WATCHDOG_MS = 3200L;
    private static final long PROJECTION_STALE_ANALYSIS_WATCHDOG_MS = 4200L;
    // Recovery must remain available for the entire authorized MediaProjection session.
    // A short cooldown prevents a tight rebind loop without creating a terminal attempt cap.
    private static final long PROJECTION_SURFACE_REBIND_COOLDOWN_MS = 1500L;
    private static final long PROJECTION_AUTO_REAUTH_COOLDOWN_MS = 12_000L;
    private static final int PROJECTION_SURFACE_REAUTH_ESCALATION_ATTEMPTS = 3;
    private static final long EXPLICIT_FREIGHT_REPLACEMENT_TIMEOUT_MS = 30_000L;
    private static final long DRIVER_ERROR_NOTICE_THROTTLE_MS = 4500L;
    private static final long BUBBLE_TAP_DEBOUNCE_MS = 180L;
    private static final long OUTSIDE_SAME_GESTURE_GUARD_MS = 140L;
    private static final long DRIVER_STAGE_MIN_VISIBLE_MS = 1400L;
    private static final long EXTERNAL_APP_MENU_MINIMIZE_MS = 1050L;
    private static final long FAST_SELECTION_CONFIRM_WINDOW_MS = 900L;
    private static final long FAST_SELECTION_FALSE_POSITIVE_TIMEOUT_MS = 950L;
    private static final long FAST_TOUCH_PULSE_WINDOW_MS = 520L;
    private static final int FAST_FRAME_HISTORY_LIMIT = 18;
    // A temporary app switch must never erase a valid freight/trip. Sessions are only
    // considered abandoned after a long inactivity window, which also prevents an old
    // unfinished trip from surviving indefinitely into a later game session.
    // HF23: 12h is a diagnostic threshold only. Time alone never discards a durable trip.
    private static final long ACTIVE_SESSION_STALE_MS = 12L * 60L * 60L * 1000L;
    private static final long CRITICAL_TOUCH_WINDOW_MS = 700L;
    private static final long AUTO_SYNC_RETRY_INTERVAL_MS = 15_000L;
    private static final long BUBBLE_RETRY_INTERVAL_MS = 350L;
    // A system/permission/game-assistant surface can sit briefly over GTO without the
    // driver actually leaving the simulator. Keep the main bubble attached while frame
    // interpretation is paused, so OEM overlay transitions cannot make it disappear.
    private static final long TRANSIENT_OVERLAY_DIAGNOSTIC_GRACE_MS = 2600L;
    // Some OEM game overlays never emit the matching background UsageEvent. Bound that
    // stale flag so screen analysis cannot remain paused forever after the overlay is gone.
    // A still-visible overlay simply fails normal visual gates; no freight is fabricated.
    private static final long TRANSIENT_OVERLAY_STALE_RECOVERY_MS = 8000L;
    // HF22: route OCR remains deliberately sparse and semantic. The observer keeps the
    // latest frame only and performs a bounded fallback pass for "Concluído + valor";
    // selection/list identity never depends on route OCR. We intentionally avoid reviving
    // color/pixel result gates because the NVU overlay itself is part of MediaProjection.
    private static final long ACTIVE_TRIP_VISUAL_PROBE_MS = 180L;
    private static final long ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS = 1800L;
    private static final int ACTIVE_TRIP_FREIGHT_LIST_CONFIRM_FRAMES = 2;
    private static final long ACTIVE_TRIP_FREIGHT_LIST_CONFIRM_MS = 180L;
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
    private boolean focusedFreightConflictRetryBusy = false;
    private final AtomicBoolean resultSnapshotRecoveryBusy = new AtomicBoolean(false);
    private long resultSnapshotRecoveryGeneration = 0L;
    private long resultEvidenceSequence = 0L;
    private final List<FreightOption> freightOptions = new ArrayList<>();
    private final List<List<FreightOption>> freightHistory = new ArrayList<>();
    private int freightHistoryPage = -1;
    private long freightHistoryUpdatedAt = 0L;

    private SharedPreferences prefs;
    private WindowManager windowManager;
    private UsageStatsManager usageStatsManager;
    private NotificationManager notificationManager;

    private FrameLayout bubbleView;
    private View captureHealthDotView;
    private TextView bubbleRemoveTargetView;
    private WindowManager.LayoutParams bubbleRemoveTargetParams;
    private boolean bubbleRemoveTargetHighlighted = false;
    private Boolean lastCaptureHealthIndicatorState = null;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout menuView;
    private ScrollView menuScrollView;
    private LinearLayout menuContentView;
    private EditText activeReviewInput;
    private String activeReviewInputField = "";
    private String activeReviewInputDraft = "";
    private WindowManager.LayoutParams menuParams;
    private boolean operationSummaryExpanded = false;
    private TextView statusChipView;
    private WindowManager.LayoutParams statusChipParams;
    private Runnable statusChipHideRunnable;
    private boolean statusChipIsDriverStage = false;
    private String statusChipDriverStageKey = "";
    private int statusChipDriverStagePriority = GtoDriverMessagePriorityPolicy.INFO;
    private long statusChipShownAt = 0L;
    private Runnable pendingDriverStageReplacementRunnable;
    // HF6: recovery remains inside the NVU bubble menu. No second standalone
        // Independent 1px touch-pulse sensor. It never toggles the NVU bubble. During
    // freight selection it marks the critical touch window; during the result dialog it
    // records that the driver acted so a slow GTO loading transition cannot strand the trip.
    private View freightTouchPulseView;
    private WindowManager.LayoutParams freightTouchPulseParams;
    private long menuOpenedAt = 0L;
    // HF20: the bubble is the visual anchor for the open card. If the pair needs an
    // automatic nudge to fit, remember the driver's pre-open position and restore it when
    // the card is minimized. Automatic layout must never overwrite the saved drag position.
    private int bubbleXBeforeMenuOpen = Integer.MIN_VALUE;
    private int bubbleYBeforeMenuOpen = Integer.MIN_VALUE;
    private boolean bubbleAutoDockedForMenu = false;
    // HF17: prevents a tap on the separate bubble window from reopening the card when
    // the card has already received ACTION_OUTSIDE for the same physical gesture.
    private long lastMenuOutsideTouchAt = 0L;
    private String lastMenuRenderSignature = "";
    private long lastStateChangeAt = 0L;
    private long lastActiveTripVisualProbeAt = 0L;
    private long lastActiveTripFallbackOcrAt = 0L;
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
    // Active trips never enter replacement mode from pixels alone. The driver must arm
    // the explicit "Trocar frete atual" action while a real GTO freight list is visible.
    private boolean freightReplacementExplicitlyArmed = false;
    private long freightReplacementExplicitlyArmedAt = 0L;
    private boolean activeTripFreightListVisible = false;
    private long activeTripFreightListLastSeenAt = 0L;
    private GtoFastVisualDetector.Frame activeTripFreightListBaseline;
    private int activeTripFreightListStableCount = 0;

    private String foregroundPackage = "";
    private String trustedGtoCitiesCacheJson = "";
    private List<String> trustedGtoCitiesCache = Collections.emptyList();
    private boolean transientForegroundSurfaceActive = false;
    private String transientForegroundPackage = "";
    private long transientForegroundSurfaceAt = 0L;
    private long lastUsageQueryAt = 0L;
    private long lastGtoForegroundEventAt = 0L;
    private long lastGtoBackgroundEventAt = 0L;
    private boolean gtoForeground = false;
    // Orthogonal to tripState: leaving GTO pauses frame interpretation without changing
    // the current journey state. Returning to GTO simply unpauses the same state.
    private boolean screenAnalysisPausedOutsideGto = true;
    private String tripStateWhenAnalysisPaused = STATE_IDLE;
    private long screenAnalysisPausedAt = 0L;
    private FreightOption deferredPreciseFreightCommit;
    private int deferredSelectionFailureRow = -1;
    private String deferredSelectionFailureReason = "";
    private boolean deferredNormalResultConfirmation = false;
    private long lastDriverErrorNoticeAt = 0L;
    private long lastDriverStageRetryAt = 0L;
    private long lastGtoForegroundEvidenceAt = 0L;
    private long lastVisualGtoForegroundEvidenceAt = 0L;
    private long nonGtoForegroundSince = 0L;
    private long suppressForegroundHideUntil = 0L;
    private boolean projectionPermissionInFlight = false;
    private boolean projectionPermissionAfterGtoOpenPending = false;
    private long projectionPermissionAfterGtoOpenArmedAt = 0L;
    private long projectionPermissionLandscapeStableSince = 0L;
    private int projectionPermissionLandscapeWidth = 0;
    private int projectionPermissionLandscapeHeight = 0;
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
    // HF5 separates "Android consent token bound" from "VirtualDisplay active". The
    // one-use token is bound while the NVU permission host is foreground, then the first
    // and only VirtualDisplay is created after the GTO/landscape geometry is stable.
    private boolean projectionSurfacePending = false;
    private long projectionSessionBoundAt = 0L;
    private long projectionSurfaceStableSince = 0L;
    private int projectionSurfaceStablePolls = 0;
    private int projectionSurfaceStableWidth = 0;
    private int projectionSurfaceStableHeight = 0;
    // Every MediaProjection callback is bound to this generation. Re-authorizing capture
    // invalidates callbacks from the previous token so an old onStop() can never release
    // the newly-created ImageReader/VirtualDisplay.
    private long projectionGeneration = 0L;
    private int captureResizeRetryCount = 0;
    private int projectionSurfaceRebindAttempts = 0;
    private long lastProjectionSurfaceRecoveryAt = 0L;
    private long lastProjectionAutoReauthAt = 0L;
    private long lastProjectionFrameAt = 0L;
    private long lastProjectionAnalyzedFrameAt = 0L;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private TextRecognizer textRecognizer;
    private TextRecognizer selectionTextRecognizer;
    private boolean projectionActive = false;
    private boolean destroying = false;
    private final GtoCaptureStabilityGate captureStabilityGate = new GtoCaptureStabilityGate();
    private int captureWidth = 0;
    private int captureHeight = 0;
    private int captureDensityDpi = 0;
    private int pendingCapturedWidth = 0;
    private int pendingCapturedHeight = 0;
    private long lastCaptureGeometryPollAt = 0L;
    private boolean lastCaptureGeometryMatched = false;
    private int outsideTouchCount = 0;
    private String projectionStatus = "INACTIVE";
    private long projectionStartedAt = 0L;
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
    // HF18: an exact touch is only a candidate until the same-row visual change or
    // a stable freight-list exit confirms that the GTO actually accepted it.
    private boolean preciseTouchAcceptancePending = false;
    private boolean preciseSelectionOcrBusy = false;
    private long freightConfirmationWatchdogGeneration = 0L;
    // R3.11: async OCR callbacks are session-bound. A late ML Kit callback from an
    // abandoned/cancelled trip must never write freight/result data into the next trip.
    private long preciseSelectionOcrGeneration = 0L;
    private long analysisOcrGeneration = 0L;

    // FIX9: selection is resolved visually from a short frame buffer around the real
    // user touch. ACTION_OUTSIDE is used only as a timestamp trigger; its coordinates
    // are deliberately ignored because Android may redact them as (0,0).
    private final List<ButtonFrameSample> buttonFrameHistory = new ArrayList<>();
    private boolean selectionProbeActive = false;
    // HF26: visual differences may corroborate a real touch but can never create one.
    private boolean selectionProbeHumanActionObserved = false;
    private String selectionProbeHumanActionSource = "";
    private long lastVisualOnlyPressIgnoredAt = 0L;
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
    // HF26: visual geometry is only a candidate. A page becomes a real freight list
    // after OCR ties at least one monetary value to an Aceitar-row geometry.
    private long freightSemanticCertifiedGeneration = -1L;
    private long freightSemanticCertifiedAt = 0L;
    private int freightSemanticAnchorRows = 0;
    private int freightEvidenceRetryCount = 0;
    private long lastFreightPageOcrAt = 0L;
    private boolean fastTouchPulseActive = false;
    private long fastTouchPulseAt = 0L;
    private long fastTouchMarkerSequence = -1L;
    // Coordinates captured from ACTION_OUTSIDE are advisory only. When Android/OEM
    // provides a usable coordinate we use it as an independent identity check against
    // the exact pre-touch Aceitar bounding box; we never choose a row from distance alone.
    private float fastTouchRawX = -1f;
    private float fastTouchRawY = -1f;
    private float fastTouchLocalX = -1f;
    private float fastTouchLocalY = -1f;
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
            reconcileProjectionPermissionLifecycle(now);
            // A granted token may be waiting for the GTO to return in landscape. This
            // check runs independently of UsageStats so an explicit NVU->GTO launch can
            // still bootstrap the surface on OEMs whose foreground event arrives late.
            maybeStartPendingProjectionSurface(now);
            maybeRecoverProjectionFrameDelivery(now);

            // OEMs can detach an overlay without throwing through our original addView().
            // Treat a detached bubble as absent so the existing retry path can restore it.
            if (bubbleView != null && !bubbleView.isAttachedToWindow()) {
                bubbleView = null;
                captureHealthDotView = null;
                lastCaptureHealthIndicatorState = null;
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

            boolean visualGtoProofFresh = lastVisualGtoForegroundEvidenceAt > 0L
                && now >= lastVisualGtoForegroundEvidenceAt
                && now - lastVisualGtoForegroundEvidenceAt <= VISUAL_GTO_EVIDENCE_FRESH_MS;
            boolean packageMatchesGto = GTO_PACKAGE.equals(foregroundPackage);
            boolean packageUnknown = foregroundPackage == null || foregroundPackage.isEmpty();
            boolean ownPermissionReturnBridge = now < suppressForegroundHideUntil
                && getPackageName().equals(foregroundPackage);
            boolean visualBridgeAllowed = visualGtoProofFresh
                && (packageUnknown || ownPermissionReturnBridge);
            if (transientForegroundSurfaceActive
                && gtoForeground
                && transientForegroundSurfaceAt > 0L
                && now - transientForegroundSurfaceAt >= TRANSIENT_OVERLAY_STALE_RECOVERY_MS
                && (packageMatchesGto || visualBridgeAllowed)) {
                // Fail-open only to ANALYSIS, never to data acceptance: every screen/freight
                // still has to pass its ordinary visual + OCR consensus gates. This repairs
                // OEMs that omit the transient-surface background event and would otherwise
                // leave the observer paused indefinitely.
                transientForegroundSurfaceActive = false;
                transientForegroundPackage = "";
                transientForegroundSurfaceAt = now;
                prefs.edit()
                    .putString("lastEvent", "Interface temporária sem evento de retorno · retomando leitura protegida")
                    .apply();
            }

            boolean rawGto = observerPermissionsReady
                && !transientForegroundSurfaceActive
                && (packageMatchesGto || visualBridgeAllowed);

            if (rawGto) {
                // A remove target is only meaningful outside GTO while the user is dragging.
                // If foreground changes mid-gesture, remove the helper immediately without
                // touching the primary bubble or the observer state.
                hideBubbleRemoveTarget();
                long absenceMs = nonGtoForegroundSince > 0L ? now - nonGtoForegroundSince : 0L;
                lastGtoForegroundEvidenceAt = now;
                nonGtoForegroundSince = 0L;
                if (!gtoForeground) {
                    gtoForeground = true;
                    prefs.edit().putBoolean("gtoForeground", true).apply();
                }
                resumeScreenAnalysisInSameState(absenceMs);

                // HF3 ordering invariant: establish/repair the TYPE_APPLICATION_OVERLAY
                // token while GTO is unquestionably foreground BEFORE launching the
                // MediaProjection consent surface. Permission UI is transient and must
                // never be allowed to suppress the main recovery control.
                if (bubbleView == null
                    && now - lastBubbleAttemptAt >= BUBBLE_RETRY_INTERVAL_MS) {
                    showBubbleIfAllowed();
                } else if (bubbleView != null && !bubbleView.isAttachedToWindow()) {
                    // Defensive stale-token recovery: an OEM can keep a Java reference after
                    // detaching the underlying overlay window. Drop it and retry immediately.
                    bubbleView = null;
                    captureHealthDotView = null;
                    lastCaptureHealthIndicatorState = null;
                    bubbleParams = null;
                    lastBubbleAttemptAt = 0L;
                    showBubbleIfAllowed();
                }

                // Only after the bubble had a chance to attach may the recorder consent
                // be opened. maybeLaunchInitialProjectionPermissionOverGto() enforces
                // the attached-window precondition too, so slow OEM WindowManager paths
                // simply retry on the next foreground poll instead of racing the bubble.
                maybeLaunchInitialProjectionPermissionOverGto(now);
                retryPendingDriverStageIfNeeded(now);
                updateFreightTouchPulseSensor();
                ensureProjectionAuthorizationIfNeeded(now);
            } else if (transientForegroundSurfaceActive && gtoForeground) {
                // Do not destroy/recreate the main floating bubble for notification shade,
                // permission controller or OEM game-assistant surfaces. OCR/touch sensing is
                // paused until GTO is visible again, but the bubble stays attached and can
                // recover immediately when the transient surface closes.
                if (nonGtoForegroundSince == 0L) nonGtoForegroundSince = now;
                pauseScreenAnalysisOutsideGto("Interface temporária sobre o GTO");
                suspendPassiveDetectionOverlaysKeepBubbleAndMenu();
                if (now - transientForegroundSurfaceAt >= TRANSIENT_OVERLAY_DIAGNOSTIC_GRACE_MS) {
                    prefs.edit()
                        .putString("lastEvent", "Interface temporária ainda cobre o GTO · leitura pausada, viagem preservada")
                        .apply();
                }
            } else {
                if (nonGtoForegroundSince == 0L) nonGtoForegroundSince = now;
                pauseScreenAnalysisOutsideGto("GTO fora do primeiro plano");
                if (gtoForeground) {
                    gtoForeground = false;
                    prefs.edit().putBoolean("gtoForeground", false).apply();
                }
                // Keep the primary NVU bubble attached for the whole automatic session.
                // Transient SystemUI surfaces are handled in the branch above and never
                // minimize the card. A positively identified third-party app may minimize
                // the card only after sustained background evidence; the bubble/state stay
                // alive and the card can be reopened immediately on return.
                if (shouldMinimizeMenuForConfirmedExternalApp(now)) {
                    closeMenu();
                    hideStatusChip();
                    prefs.edit()
                        .putLong("menuMinimizedForExternalAppAt", now)
                        .putString("lastEvent", "Card NVU minimizado após saída confirmada do GTO")
                        .apply();
                }
                suspendPassiveDetectionOverlaysKeepBubbleAndMenu();
            }

            updateCaptureHealthIndicator(now);

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
     * Called immediately before either the Capacitor plugin or MainActivity brings the
     * existing GTO task forward. A projection that was ready for a previous surface is
     * never trusted for the new launch until fresh foreground evidence and stable frames
     * are observed again.
     */
    /**
     * Arms the canonical journey state before the GTO task is brought to foreground.
     *
     * R3.27 root-cause fix: the Web launcher used to open GTO while the native observer
     * was still IDLE. On OEMs with delayed UsageStats this created a circular gate: live
     * freight pixels were only allowed to prove GTO foreground in WAITING_FREIGHT, while
     * reaching WAITING_FREIGHT depended on those same pixels being analyzed. Preparing
     * the session here makes WAITING_FREIGHT authoritative before the first GTO frame.
     * Existing active journeys are preserved exactly as-is.
     */
    public static boolean prepareWorkLaunchIfRunning() {
        GtoObserverService current = instance;
        if (current == null || !running || current.destroying) return false;

        if (Looper.myLooper() == current.mainHandler.getLooper()) {
            return current.prepareJourneyForGtoLaunch();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean prepared = new AtomicBoolean(false);
        current.mainHandler.post(() -> {
            try {
                prepared.set(current.prepareJourneyForGtoLaunch());
            } finally {
                latch.countDown();
            }
        });
        try {
            return latch.await(1400L, TimeUnit.MILLISECONDS) && prepared.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void sanitizeLegacyUntrustedPendingSelectionOnStartup() {
        if (prefs == null) return;
        String state = prefs.getString("tripState", STATE_IDLE);
        String status = prefs.getString("selectionIdentityStatus", "");
        String source = prefs.getString("selectionIdentitySource", prefs.getString("selectionSource", ""));
        boolean hasReview = prefs.getBoolean("pendingFreightReview", false)
            || "REVIEW_REQUIRED".equals(prefs.getString("selectionConfirmationStatus", ""));
        boolean pendingUntrusted = STATE_CONFIRMING_FREIGHT.equals(state)
            && ("CONFIRMED".equals(status) || hasReview)
            && !GtoSelectionEvidencePolicy.isHumanBackedSource(source);
        boolean activeLegacyUntrusted = STATE_TRIP_IN_PROGRESS.equals(state)
            && "CONFIRMED".equals(prefs.getString("selectionConfirmationStatus", ""))
            && !GtoSelectionEvidencePolicy.isHumanBackedSource(source);
        if (!pendingUntrusted && !activeLegacyUntrusted) return;

        if (activeLegacyUntrusted) {
            String unsafeSession = prefs.getString("gtoTripSessionId", "");
            GtoAutoTripSync.discardSessionSnapshot(this, unsafeSession);
            clearTripAnalysis();
            prefs.edit()
                .putString("tripState", STATE_IDLE)
                .putString("lastEvent", "Frete legado sem prova humana descartado · inicie o trabalho novamente")
                .putString("gtoTripIntegrityStatus", "LEGACY_UNTRUSTED_SELECTION_DROPPED")
                .apply();
            recordObserverIncident("LEGACY_UNTRUSTED_ACTIVE_TRIP_DROPPED", "source=" + source);
            return;
        }

        prefs.edit()
            .putString("tripState", STATE_WAITING_FREIGHT)
            .putString("lastEvent", "Seleção antiga sem prova humana descartada · aguardando lista certificada")
            .remove("selectionIdentityStatus")
            .remove("selectionIdentitySource")
            .remove("selectionIdentityAt")
            .remove("selectionConfirmationStatus")
            .remove("pendingFreightReview")
            .remove("reviewRequiredField")
            .remove("selectedFreightRow")
            .remove("preciseSelectedRow")
            .remove("selectedFreight")
            .remove("selectedFreightSummary")
            .remove("reviewCargo")
            .remove("reviewOriginCompany")
            .remove("reviewDestinationCompany")
            .remove("reviewDestination")
            .remove("reviewKm")
            .remove("reviewValue")
            .remove("reviewCargoSource")
            .remove("reviewOriginCompanySource")
            .remove("reviewDestinationCompanySource")
            .remove("reviewDestinationSource")
            .remove("reviewKmSource")
            .remove("reviewValueSource")
            .apply();
        recordObserverIncident("LEGACY_UNTRUSTED_SELECTION_DROPPED", "source=" + source);
    }

    private boolean prepareJourneyForGtoLaunch() {
        String state = getTripState();
        if (STATE_CONFIRMING_FREIGHT.equals(state) && !hasConfirmedSelectionIdentity()) {
            sanitizeLegacyUntrustedPendingSelectionOnStartup();
            state = getTripState();
        }
        if (GtoDeterministicFlowPolicy.shouldPrepareWaitingBeforeGtoOpen(state)) {
            // Do not ask for MediaProjection while NVU is still portrait/foreground.
            // The consent is armed below and displayed only after GTO is confirmed.
            beginTrip(false, false);
            state = getTripState();
        }

        boolean prepared = GtoDeterministicFlowPolicy.isPreparedForGtoOpen(state);
        if (prepared) {
            prefs.edit()
                .putBoolean("gtoWorkLaunchPrepared", true)
                .putString("gtoWorkLaunchPreparedState", state)
                .putLong("gtoWorkLaunchPreparedAt", System.currentTimeMillis())
                .putString("lastEvent", STATE_WAITING_FREIGHT.equals(state)
                    ? "GTO preparado · aguardando lista de fretes"
                    : "GTO preparado · estado da viagem preservado: " + state)
                .apply();
        }
        return prepared;
    }

    /**
     * Rebuilds only volatile overlay views and arms a fresh bubble for the next confirmed
     * GTO foreground. Durable journey/session/freight/result state is deliberately kept.
     * This gives every automatic-mode launch an explicit OEM-safe bubble recovery path.
     */
    public static boolean prepareFloatingButtonForNextGtoLaunch() {
        GtoObserverService current = instance;
        if (current == null || !running || current.destroying) return false;
        if (Looper.myLooper() == current.mainHandler.getLooper()) {
            return current.armFreshFloatingButtonForNextGtoForeground();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean prepared = new AtomicBoolean(false);
        current.mainHandler.post(() -> {
            try {
                prepared.set(current.armFreshFloatingButtonForNextGtoForeground());
            } finally {
                latch.countDown();
            }
        });
        try {
            return latch.await(1400L, TimeUnit.MILLISECONDS) && prepared.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Idempotent companion used by openGto(). The Web launcher has already called
     * prepareFloatingButton(), so openGto must not immediately tear down the freshly
     * armed/attached overlay a second time. Direct native callers still get the same
     * safety fallback when nothing is currently prepared.
     */
    public static boolean ensureFloatingButtonPreparedForNextGtoLaunch() {
        GtoObserverService current = instance;
        if (current == null || !running || current.destroying) return false;
        if (Looper.myLooper() == current.mainHandler.getLooper()) {
            return current.ensureFloatingButtonPreparedOnMainThread();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean prepared = new AtomicBoolean(false);
        current.mainHandler.post(() -> {
            try {
                prepared.set(current.ensureFloatingButtonPreparedOnMainThread());
            } finally {
                latch.countDown();
            }
        });
        try {
            return latch.await(1400L, TimeUnit.MILLISECONDS) && prepared.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean ensureFloatingButtonPreparedOnMainThread() {
        boolean attached = bubbleView != null && bubbleView.isAttachedToWindow();
        boolean armed = prefs != null && prefs.getBoolean("floatingButtonActivationArmed", false);
        if (attached || armed) return true;
        return armFreshFloatingButtonForNextGtoForeground();
    }

    private boolean armFreshFloatingButtonForNextGtoForeground() {
        if (destroying || prefs == null || windowManager == null) return false;
        if (!Settings.canDrawOverlays(this)) {
            prefs.edit()
                .putBoolean("floatingButtonActivationArmed", false)
                .putString("overlayError", "Permissão SYSTEM_ALERT_WINDOW não está ativa")
                .putLong("overlayErrorAt", System.currentTimeMillis())
                .apply();
            return false;
        }
        // A stale View token is the common reason an OEM leaves the logical service alive
        // while the visible bubble is gone. Remove all volatile overlay views and force a
        // fresh WindowManager token on the next real GTO foreground poll.
        hideOverlays();
        lastBubbleAttemptAt = 0L;
        prefs.edit()
            .putBoolean("floatingButtonActivationArmed", true)
            .putLong("floatingButtonArmedAt", System.currentTimeMillis())
            .putBoolean("overlayVisible", false)
            .remove("overlayError")
            .remove("overlayErrorAt")
            .putString("lastEvent", "Botão flutuante armado · aguardando GTO em primeiro plano")
            .apply();
        return true;
    }

    public static void markGtoLaunchRequestedIfRunning() {
        GtoObserverService current = instance;
        if (current == null || !running || current.destroying) return;
        current.mainHandler.post(current::prepareCaptureForGtoLaunch);
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
            .putBoolean("projectionSessionBound", false)
            .putBoolean("projectionSurfacePending", false)
            .putBoolean("projectionGrantValidated", false)
            .remove("projectionSessionBoundAt")
            .putString("projectionStatus", safeStatus)
            .putString("projectionError", safeError)
            .putBoolean("projectionReauthRequired", true)
            .putBoolean("projectionReauthAutoAllowed", true)
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
        projectionSurfacePending = false;
        projectionSessionBoundAt = 0L;
        resetPendingProjectionSurfaceStability();
        suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
        projectionStatus = status == null || status.isEmpty() ? "PERMISSION_FLOW_FAILED" : status;
        prefs.edit()
            .putBoolean("projectionPermissionInFlight", false)
            .putBoolean("projectionActive", projectionActive)
            .putBoolean("projectionSessionBound", false)
            .putBoolean("projectionSurfacePending", false)
            .putBoolean("projectionGrantValidated", false)
            .remove("projectionSessionBoundAt")
            .putString("projectionStatus", projectionStatus)
            .putString("projectionError", error == null ? "" : error)
            .putBoolean("projectionReauthRequired", true)
            .putBoolean("projectionReauthAutoAllowed", true)
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
            showStatusChip("A autorização de leitura não foi concluída. Abra a bolinha NVU e tente novamente.", 4200L);
        }
    }

    public static boolean requestProjectionPermissionIfRunning() {
        GtoObserverService live = instance;
        if (live == null || !running) return false;
        live.mainHandler.post(live::requestProjectionPermission);
        return true;
    }

    /**
     * Consumes the MediaProjection grant immediately while the transparent consent host
     * is still alive. The observer service is already running whenever this activity can
     * be launched, so keeping the grant in-process removes the asynchronous service-intent
     * race that could briefly make an accepted grant look unauthorized again.
     */
    // HF10 deliberately has no direct/static RESULT_OK binding path. The permission
    // Activity always dispatches ACTION_START_PROJECTION so Android can recreate the
    // service/process and the documented foreground-service ordering remains intact.

    private boolean acceptProjectionGrantOnMainThread(int resultCode, Intent resultData) {
        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) return false;
        if (projectionActive || (projectionSurfacePending && mediaProjection != null)) {
            projectionPermissionInFlight = false;
            prefs.edit()
                .putBoolean("projectionPermissionInFlight", false)
                .putBoolean("projectionGrantValidated", true)
                .putLong("projectionGrantValidatedAt", System.currentTimeMillis())
                .apply();
            return true;
        }
        projectionPermissionAfterGtoOpenPending = false;
        projectionPermissionAfterGtoOpenArmedAt = 0L;
        projectionPermissionInFlight = true;
        suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
        prefs.edit()
            .putBoolean("projectionPermissionInFlight", true)
            .remove("projectionPermissionAfterGtoOpenPending")
            .remove("projectionPermissionAfterGtoOpenArmedAt")
            .putLong("projectionGrantReceivedAt", System.currentTimeMillis())
            .putInt("projectionGrantResultCode", resultCode)
            .putString("projectionStatus", "CONSENT_GRANTED_BINDING")
            .putString("lastEvent", "Compartilhamento aceito · validando token no observador")
            .apply();
        return startProjection(resultCode, resultData);
    }

    public static boolean markProjectionPermissionInFlightIfRunning() {
        GtoObserverService live = instance;
        if (live == null || !running) return false;
        live.mainHandler.post(() -> {
            live.projectionPermissionInFlight = true;
            live.suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
            live.projectionStatus = "REQUESTING_PERMISSION_APP";
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

    private void recordNeutralScreenObservation(String kind, String normalizedText) {
        if (prefs == null) return;
        prefs.edit()
            .putString("lastNeutralScreenKind", kind == null ? "OTHER" : kind)
            .putString("lastNeutralScreenText", truncate(normalizedText == null ? "" : normalizedText, 240))
            .putLong("lastNeutralScreenAt", System.currentTimeMillis())
            .apply();
    }

    private void reportFrameProcessingError(String area, Throwable error) {
        String detail = describeError(error);
        long now = System.currentTimeMillis();
        prefs.edit()
            .putString("frameProcessingErrorArea", area == null ? "captura" : area)
            .putString("frameProcessingError", detail)
            .putLong("frameProcessingErrorAt", now)
            .putString("lastEvent", "Falha em " + (area == null ? "captura" : area) + ": " + detail)
            .apply();
        recordObserverIncident("FRAME_PROCESSING", (area == null ? "captura" : area) + " · " + detail);
        if (gtoForeground && !screenAnalysisPausedOutsideGto
            && now - lastDriverErrorNoticeAt >= DRIVER_ERROR_NOTICE_THROTTLE_MS) {
            lastDriverErrorNoticeAt = now;
            showStatusChip("Falha na leitura: " + detail, 4200L);
        }
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
        projectionPermissionAfterGtoOpenPending = prefs.getBoolean("projectionPermissionAfterGtoOpenPending", false);
        projectionPermissionAfterGtoOpenArmedAt = prefs.getLong("projectionPermissionAfterGtoOpenArmedAt", 0L);
        long serviceNow = System.currentTimeMillis();
        prefs.edit()
            .putBoolean("projectionActive", false)
            .putBoolean("projectionPermissionInFlight", false)
            .putBoolean("projectionSessionBound", false)
            .putBoolean("projectionSurfacePending", false)
            .putBoolean("projectionGrantValidated", false)
            .remove("projectionSessionBoundAt")
            .remove("projectionConsentResultAt")
            .remove("projectionGrantReceivedAt")
            .putBoolean("overlayVisible", false)
            .putLong("serviceStartedAt", serviceNow)
            .putLong("serviceHeartbeatAt", serviceNow)
            .remove("startError")
            .apply();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        selectionTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recordObserverEvent("SERVICE_STARTED", "Observador inicializado");
        sanitizeLegacyUntrustedPendingSelectionOnStartup();
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
            // Short-lived frame references do not survive process death. However, when
            // selection identity was already CONFIRMED and only a specific field is under
            // REVIEW_REQUIRED, that durable review is restored exactly. An unconfirmed
            // candidate safely falls back to WAITING_FREIGHT. Locked trips remain untouched.
            String recoverableState = GtoSessionRecoveryPolicy.restoredState(
                restoredState,
                prefs.getString("selectionIdentityStatus", ""),
                prefs.getString("selectionConfirmationStatus", ""),
                prefs.getString("reviewRequiredField", ""),
                prefs.getString("selectionIdentitySource", "")
            );
            prefs.edit()
                .putString("tripState", recoverableState)
                .putString("projectionStatus", "REAUTH_REQUIRED_AFTER_RESTART")
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthAutoAllowed", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putString("lastEvent", STATE_TRIP_IN_PROGRESS.equals(recoverableState)
                    ? "Viagem GTO preservada após reinício · autorize a leitura da tela para finalizar"
                    : "Sessão GTO preservada após reinício · autorize a leitura da tela")
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
            projectionPermissionAfterGtoOpenPending = false;
            projectionPermissionAfterGtoOpenArmedAt = 0L;
            projectionPermissionInFlight = false;
            suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
            projectionActive = false;
            projectionSurfacePending = false;
            projectionSessionBoundAt = 0L;
            resetPendingProjectionSurfaceStability();
            projectionStatus = "DENIED";
            prefs.edit()
                .putBoolean("projectionActive", false)
                .putBoolean("projectionSessionBound", false)
                .putBoolean("projectionSurfacePending", false)
                .putBoolean("projectionGrantValidated", false)
                .putString("projectionStatus", projectionStatus)
                .putBoolean("projectionPermissionInFlight", false)
                .remove("projectionPermissionAfterGtoOpenPending")
                .remove("projectionPermissionAfterGtoOpenArmedAt")
                .putString("screenState", "CAPTURE_DENIED")
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthAutoAllowed", false)
                .putBoolean("projectionReauthNoticeShown", false)
                .putBoolean("touchCaptureNeeded", false)
                .putString("lastEvent", "Captura de tela não autorizada")
                .apply();
            if (STATE_WAITING_FREIGHT.equals(getTripState())) {
                showToast("A leitura da tela é necessária. Abra a bolinha NVU e toque em Autorizar leitura da tela.");
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
            // The RESULT_OK handoff is the exact point where Android allows the
            // mediaProjection foreground-service type on Android 14+. Promote the
            // already-running observer *before* getMediaProjection(), and never
            // downgrade while a projection is bound/active.
            boolean projectionGrantAction = ACTION_START_PROJECTION.equals(action);
            startForegroundForTypes(
                projectionGrantAction || projectionActive || projectionSurfacePending || mediaProjection != null
            );
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
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = readProjectionData(intent);
            boolean accepted = resultCode == android.app.Activity.RESULT_OK
                && resultData != null
                && acceptProjectionGrantOnMainThread(resultCode, resultData);
            if (!accepted) {
                projectionPermissionInFlight = false;
                projectionActive = false;
                projectionSurfacePending = false;
                projectionSessionBoundAt = 0L;
                projectionStatus = "GRANT_DATA_INVALID";
                prefs.edit()
                    .putBoolean("projectionPermissionInFlight", false)
                    .putBoolean("projectionActive", false)
                    .putBoolean("projectionSessionBound", false)
                    .putBoolean("projectionSurfacePending", false)
                    .putBoolean("projectionGrantValidated", false)
                    .putString("projectionStatus", projectionStatus)
                    .putString("projectionError", "O Android retornou autorização sem um token de captura válido.")
                    .putBoolean("projectionReauthRequired", true)
                    .putBoolean("projectionReauthAutoAllowed", true)
                    .putBoolean("projectionReauthNoticeShown", false)
                    .putString("lastEvent", "Autorização recebida sem token de captura válido")
                    .apply();
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

            boolean transientSurface = isTransientForegroundEvent(packageName, className);
            if (transientSurface) {
                if (enteredForeground && at >= transientForegroundSurfaceAt) {
                    transientForegroundSurfaceActive = true;
                    transientForegroundSurfaceAt = at;
                    transientForegroundPackage = packageName == null ? "SYSTEM_UI" : packageName;
                    sawTransientForeground = true;
                }
                if (enteredBackground
                    && transientForegroundSurfaceActive
                    && at >= transientForegroundSurfaceAt
                    && (packageName == null || packageName.equals(transientForegroundPackage))) {
                    transientForegroundSurfaceActive = false;
                    transientForegroundSurfaceAt = at;
                    transientForegroundPackage = "";
                }
            }

            if (GTO_PACKAGE.equals(packageName)) {
                if (enteredForeground) {
                    lastGtoForegroundEventAt = Math.max(lastGtoForegroundEventAt, at);
                    transientForegroundSurfaceActive = false;
                    transientForegroundPackage = "";
                }
                if (enteredBackground) lastGtoBackgroundEventAt = Math.max(lastGtoBackgroundEventAt, at);
            }

            if (enteredForeground && at >= newestForeground) {
                if (transientSurface) continue;
                newestForeground = at;
                latestPackage = packageName;
                transientForegroundSurfaceActive = false;
                transientForegroundPackage = "";
            }
        }

        boolean visualGtoProofFresh = lastVisualGtoForegroundEvidenceAt > 0L
            && now >= lastVisualGtoForegroundEvidenceAt
            && now - lastVisualGtoForegroundEvidenceAt <= VISUAL_GTO_EVIDENCE_FRESH_MS;
        boolean permissionReturnGrace = now < suppressForegroundHideUntil;
        if (latestPackage != null) {
            // A visual list may bridge only our own permission-return activity. It may
            // never override a positively identified third-party foreground app.
            boolean ownPermissionReturn = getPackageName().equals(latestPackage) && permissionReturnGrace;
            foregroundPackage = ownPermissionReturn
                    && visualGtoProofFresh
                    && lastVisualGtoForegroundEvidenceAt >= newestForeground
                ? GTO_PACKAGE
                : latestPackage;
        } else if (!sawTransientForeground
            && GTO_PACKAGE.equals(foregroundPackage)
            && lastGtoBackgroundEventAt > lastGtoForegroundEventAt
            && !visualGtoProofFresh) {
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
        int safeCount = Math.max(0, freightCount);
        String previousState = lastPersistedFreightRuntimeState;
        int previousCount = lastPersistedFreightCount;
        boolean changed = !safeState.equals(previousState)
            || safeCount != previousCount;
        if (!changed && now - lastFreightRuntimePersistAt < FREIGHT_RUNTIME_PERSIST_INTERVAL_MS) return;
        lastFreightRuntimePersistAt = now;
        lastPersistedFreightRuntimeState = safeState;
        lastPersistedFreightCount = safeCount;
        SharedPreferences.Editor editor = prefs.edit()
            .putString("screenState", safeState)
            .putInt("freightCount", safeCount);
        if ("FREIGHT_LIST".equals(safeState)) {
            editor.putLong("freightStructureAt", now)
                .putLong("freightFrameSequence", sequence);
        }
        editor.apply();

        if (changed) {
            mainHandler.post(this::refreshMenuContents);
            // HF16: raw button geometry updates the internal screen state immediately,
            // but the driver-facing "lista detectada" message is emitted only after OCR
            // confirms at least one same-row monetary freight value.
        }
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
            || freightTouchPulseView != null) {
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

    private boolean isFreightReviewPending() {
        boolean pending = STATE_CONFIRMING_FREIGHT.equals(getTripState())
            && "CONFIRMED".equals(prefs == null ? "" : prefs.getString("selectionIdentityStatus", ""))
            && "REVIEW_REQUIRED".equals(prefs == null ? "" : prefs.getString("selectionConfirmationStatus", ""));
        if (pending && GtoFreightReviewPolicy.DESTINATION_COMPANY.equals(prefs.getString("reviewRequiredField", ""))) {
            // HF14 migration: destinationCompany was incorrectly made driver-required in
            // HF12/HF13. Recompute the next genuinely required field without losing the
            // selected row or any already-read metadata.
            FreightOption current = freightReviewFromPrefs();
            String next = firstReviewField(current);
            prefs.edit()
                .putString("reviewRequiredField", next)
                .putString("lastEvent", next.isEmpty()
                    ? "Empresa de destino opcional · concluindo confirmação do frete"
                    : "Empresa de destino opcional · revisando somente o dado necessário")
                .apply();
            if (next.isEmpty()) {
                mainHandler.post(() -> commitReviewedFreight(current));
                return false;
            }
        }
        return pending;
    }

    private boolean isResultTrackingState(String state) {
        return STATE_TRIP_IN_PROGRESS.equals(state)
            || (STATE_CONFIRMING_FREIGHT.equals(state) && isFreightReviewPending());
    }

    private boolean resultActionCanBeObserved(String state) {
        if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) return true;
        return STATE_CONFIRMING_FREIGHT.equals(state)
            && isFreightReviewPending()
            && ("RESULT_SCREEN".equals(prefs.getString("completionStatus", ""))
                || prefs.getBoolean("pendingResultDuringFreightReview", false));
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
        if (projectionActive || projectionPermissionInFlight || projectionSurfacePending || !captureIsNeededForCurrentState()) return;
        if (!prefs.getBoolean("projectionReauthRequired", false)) return;
        if (prefs.getBoolean("projectionReauthNoticeShown", false)) return;
        prefs.edit()
            .putBoolean("projectionReauthNoticeShown", true)
            .putString("lastEvent", "Leitura da tela precisa ser autorizada novamente pela bolinha NVU")
            .apply();
        showStatusChip("Leitura da tela foi encerrada pelo Android. Abra a bolinha NVU e toque em Autorizar leitura da tela.", 5200L);
        updateNotification();
    }

    private void ensureProjectionAuthorizationIfNeeded(long now) {
        if (!prefs.getBoolean("projectionReauthRequired", false) || !captureIsNeededForCurrentState()) return;
        maybeNotifyProjectionReauthorization();

        DisplayMetrics metrics = realDisplayMetrics();
        boolean exactGto = gtoForeground && GTO_PACKAGE.equals(foregroundPackage);
        boolean landscape = metrics.widthPixels > metrics.heightPixels && metrics.heightPixels > 0;
        boolean bubbleAttached = bubbleView != null && bubbleView.isAttachedToWindow();
        boolean autoAllowed = prefs.getBoolean("projectionReauthAutoAllowed", true);
        if (!GtoProjectionRecoveryPolicy.shouldAutoRequest(
            true,
            autoAllowed,
            gtoForeground,
            exactGto,
            landscape,
            bubbleAttached,
            true,
            projectionActive,
            projectionSurfacePending,
            projectionPermissionInFlight,
            now,
            lastProjectionAutoReauthAt,
            PROJECTION_AUTO_REAUTH_COOLDOWN_MS
        )) return;

        lastProjectionAutoReauthAt = now;
        prefs.edit()
            .putLong("projectionAutoReauthRequestedAt", now)
            .putString("lastEvent", "Detecção indisponível · solicitando nova autorização automaticamente")
            .apply();
        announceDriverStage(
            "CAPTURE_REAUTH_REQUIRED",
            "A leitura da tela precisa ser reativada. Confirme a autorização do Android.",
            4200L,
            true
        );
        launchProjectionPermissionActivityOnlyWhenGtoLandscape("AUTO_REAUTH_AFTER_CAPTURE_LOSS");
    }

    private void escalateProjectionToFreshAuthorization(String reason) {
        if (destroying || !running || !captureIsNeededForCurrentState()) return;
        String safeReason = reason == null || reason.trim().isEmpty()
            ? "Captura permaneceu sem quadros após recuperação da superfície"
            : reason.trim();
        stopProjection();
        projectionStatus = "REAUTH_REQUIRED_AFTER_STALL";
        prefs.edit()
            .putString("projectionStatus", projectionStatus)
            .putBoolean("projectionReauthRequired", true)
            .putBoolean("projectionReauthAutoAllowed", true)
            .putBoolean("projectionReauthNoticeShown", false)
            .putString("projectionError", safeReason)
            .putString("captureReadiness", "REAUTH_REQUIRED")
            .putBoolean("captureReadyForAnalysis", false)
            .putString("lastEvent", safeReason + " · solicitando nova autorização")
            .apply();
        updateNotification();
        if (gtoForeground) {
            mainHandler.postDelayed(() -> ensureProjectionAuthorizationIfNeeded(System.currentTimeMillis()), 320L);
        }
    }

    private void reconcileProjectionPermissionLifecycle(long now) {
        // A real bound/active MediaProjection is authoritative. If a stale UI latch
        // survived a lifecycle edge, clear only the latch; never discard the grant.
        if (projectionActive || projectionSurfacePending || mediaProjection != null) {
            if (projectionPermissionInFlight) {
                projectionPermissionInFlight = false;
                prefs.edit()
                    .putBoolean("projectionPermissionInFlight", false)
                    .putString("lastEvent", projectionActive
                        ? "Compartilhamento ativo · autorização confirmada"
                        : "Compartilhamento validado · preparando captura no GTO")
                    .apply();
                if (menuView != null) mainHandler.post(this::refreshMenuContents);
            }
            return;
        }
        if (!projectionPermissionInFlight) return;

        long requestAt = Math.max(
            prefs.getLong("projectionConsentGtoVerifiedAt", 0L),
            prefs.getLong("projectionConsentVisibleAt", 0L)
        );
        long resultAt = prefs.getLong("projectionConsentResultAt", 0L);
        if (resultAt > 0L && resultAt >= requestAt) {
            // RESULT_OK/RESULT_CANCELED should immediately transition through the host.
            // Reaching this branch without a bound token means the callback path did not
            // finish cleanly; expose a terminal state instead of silently requesting again.
            projectionPermissionInFlight = false;
            projectionStatus = "CONSENT_RESULT_UNBOUND";
            prefs.edit()
                .putBoolean("projectionPermissionInFlight", false)
                .putString("projectionStatus", projectionStatus)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthAutoAllowed", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putString("projectionError", "O Android devolveu o resultado da autorização, mas nenhuma sessão de captura ficou vinculada.")
                .putString("lastEvent", "Resultado de autorização sem sessão vinculada · nova tentativa manual necessária")
                .apply();
            if (menuView != null) mainHandler.post(this::refreshMenuContents);
            return;
        }

        if (requestAt <= 0L || now < requestAt || now - requestAt < PROJECTION_PERMISSION_RESULT_WATCHDOG_MS) return;
        projectionPermissionInFlight = false;
        projectionStatus = "CONSENT_RESULT_TIMEOUT";
        prefs.edit()
            .putBoolean("projectionPermissionInFlight", false)
            .putString("projectionStatus", projectionStatus)
            .putBoolean("projectionReauthRequired", true)
            .putBoolean("projectionReauthAutoAllowed", true)
            .putBoolean("projectionReauthNoticeShown", false)
            .putString("projectionError", "A tela de autorização não devolveu um resultado ao NVU dentro do tempo esperado.")
            .putString("lastEvent", "Autorização do Android sem retorno · fluxo desbloqueado com segurança")
            .apply();
        if (menuView != null) mainHandler.post(this::refreshMenuContents);
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
            || p.contains("packageinstaller")
            || p.contains("gametime")
            || p.contains("gameassistant")
            || p.contains("gamebooster")
            || p.contains("gameoverlay")) {
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
        if (STATE_WAITING_FREIGHT.equals(state)
            && "FAILED".equals(prefs.getString("selectionConfirmationStatus", ""))) {
            return "Frete não confirmado · abra a bolinha NVU";
        }
        if (STATE_WAITING_FREIGHT.equals(state)) return "Etapa 1/4 · escolha seu frete";
        if (STATE_CONFIRMING_FREIGHT.equals(state)) {
            if (isFreightReviewPending()) {
                return "Frete selecionado · revise " + reviewFieldLabel(prefs.getString("reviewRequiredField", ""));
            }
            return "Etapa 1/4 · confirmando frete…";
        }
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

        final int buttonSize = dp(56);
        final int healthDotSize = dp(8);
        final int healthGap = dp(5);
        final int bubbleWidth = buttonSize + healthGap + healthDotSize;
        bubbleView = new FrameLayout(this);
        bubbleView.setBackgroundColor(Color.TRANSPARENT);
        bubbleView.setElevation(dp(6));

        TextView label = new TextView(this);
        label.setText("NVU");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13f);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setBackground(makeRoundedBackground(Color.rgb(59, 168, 176), dp(18)));
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        labelParams.gravity = Gravity.START | Gravity.TOP;
        bubbleView.addView(label, labelParams);

        captureHealthDotView = new View(this);
        captureHealthDotView.setBackground(makeRoundedBackground(Color.rgb(82, 88, 96), healthDotSize / 2));
        captureHealthDotView.setAlpha(0.55f);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(healthDotSize, healthDotSize);
        dotParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        bubbleView.addView(captureHealthDotView, dotParams);
        lastCaptureHealthIndicatorState = null;

        bubbleParams = new WindowManager.LayoutParams(
            bubbleWidth,
            buttonSize,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics metrics = realDisplayMetrics();
        int savedX = prefs.getInt("bubbleX", Math.max(dp(8), metrics.widthPixels - bubbleWidth - dp(18)));
        int savedY = prefs.getInt("bubbleY", Math.max(dp(80), metrics.heightPixels / 3));
        int bubbleSafeLeft = dp(8);
        int safeBubbleMaxX = Math.min(
            Math.max(bubbleSafeLeft, metrics.widthPixels - bubbleWidth - dp(8)),
            Math.max(bubbleSafeLeft, freightOverlaySafeRight(metrics) - bubbleWidth - dp(8))
        );
        int bubbleSafeTop = safeTopInsetPx() + dp(8);
        int bubbleSafeBottom = safeBottomInsetPx() + dp(8);
        bubbleParams.x = clamp(savedX, bubbleSafeLeft, safeBubbleMaxX);
        bubbleParams.y = clamp(
            savedY,
            bubbleSafeTop,
            Math.max(bubbleSafeTop, metrics.heightPixels - bubbleSafeBottom - buttonSize)
        );

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
                        closeMenu(false);
                        if (GtoBubbleDismissPolicy.shouldShowRemoveTarget(gtoForeground, true)) {
                            showBubbleRemoveTarget();
                        }
                    }
                    if (!dragging[0]) return true;
                    DisplayMetrics screen = realDisplayMetrics();
                    int dragSafeLeft = dp(8);
                    int dragMaxX = Math.min(
                        Math.max(dragSafeLeft, screen.widthPixels - bubbleWidth - dp(8)),
                        Math.max(dragSafeLeft, freightOverlaySafeRight(screen) - bubbleWidth - dp(8))
                    );
                    int dragSafeTop = safeTopInsetPx() + dp(8);
                    int dragSafeBottom = safeBottomInsetPx() + dp(8);
                    bubbleParams.x = clamp(startX[0] + Math.round(dx), dragSafeLeft, dragMaxX);
                    bubbleParams.y = clamp(
                        startY[0] + Math.round(dy),
                        dragSafeTop,
                        Math.max(dragSafeTop, screen.heightPixels - dragSafeBottom - buttonSize)
                    );
                    try {
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        updateBubbleRemoveTargetHighlight();
                    } catch (Exception ex) {
                        recordOverlayFailure(ex);
                        hideBubbleRemoveTarget();
                        try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
                        bubbleView = null;
                        captureHealthDotView = null;
                        lastCaptureHealthIndicatorState = null;
                        bubbleParams = null;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    // updateViewLayout can fail on an OEM while the finger is still down.
                    // In that case ACTION_MOVE already detached the broken overlay and
                    // cleared bubbleParams. Never dereference that stale LayoutParams on
                    // ACTION_UP; let the foreground self-heal recreate the bubble instead.
                    if (dragging[0] && isBubbleDroppedOnRemoveTarget()) {
                        hideBubbleRemoveTarget();
                        stopObserverFromFloatingBubble();
                        return true;
                    }
                    hideBubbleRemoveTarget();
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
                    hideBubbleRemoveTarget();
                    return true;
                default:
                    return false;
            }
        });

        try {
            int previousFailures = prefs.getInt("overlayFailureCount", 0);
            windowManager.addView(bubbleView, bubbleParams);
            updateCaptureHealthIndicator(System.currentTimeMillis());
            SharedPreferences.Editor editor = prefs.edit()
                .putBoolean("overlayVisible", true)
                .putBoolean("floatingButtonActivationArmed", false)
                .putLong("floatingButtonActivatedAt", System.currentTimeMillis())
                .remove("overlayError")
                .remove("overlayErrorAt")
                .putInt("overlayFailureCount", 0);
            if (previousFailures > 0) {
                editor.putString("lastEvent", "Botão flutuante restaurado no GTO");
            }
            editor.apply();
        } catch (Exception ex) {
            bubbleView = null;
            captureHealthDotView = null;
            lastCaptureHealthIndicatorState = null;
            bubbleParams = null;
            recordOverlayFailure(ex);
        }
    }

    private void showBubbleRemoveTarget() {
        if (gtoForeground || windowManager == null || bubbleRemoveTargetView != null) return;
        DisplayMetrics screen = realDisplayMetrics();
        final int width = dp(184);
        final int height = dp(52);
        final int bottomMargin = safeBottomInsetPx() + dp(20);

        TextView target = new TextView(this);
        target.setText("Remover e parar NVU");
        target.setTextColor(Color.WHITE);
        target.setTextSize(14f);
        target.setGravity(Gravity.CENTER);
        target.setTypeface(target.getTypeface(), android.graphics.Typeface.BOLD);
        target.setPadding(dp(14), 0, dp(14), 0);
        target.setBackground(makeRoundedBackground(Color.rgb(92, 45, 48), dp(18)));
        target.setElevation(dp(8));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(dp(8), (screen.widthPixels - width) / 2);
        params.y = Math.max(
            safeTopInsetPx() + dp(8),
            screen.heightPixels - bottomMargin - height
        );

        try {
            windowManager.addView(target, params);
            bubbleRemoveTargetView = target;
            bubbleRemoveTargetParams = params;
            bubbleRemoveTargetHighlighted = false;
            prefs.edit()
                .putBoolean("bubbleRemoveTargetVisible", true)
                .putString("lastEvent", "Arraste a bolinha até Remover e parar NVU para encerrar o observador")
                .apply();
        } catch (Exception ex) {
            bubbleRemoveTargetView = null;
            bubbleRemoveTargetParams = null;
            bubbleRemoveTargetHighlighted = false;
            // The optional remove target is not the primary NVU overlay. A failure to
            // attach this helper must never mark the main bubble as unavailable or
            // interfere with capture/observer health.
            if (prefs != null) {
                prefs.edit()
                    .putBoolean("bubbleRemoveTargetVisible", false)
                    .putString("bubbleRemoveTargetError", describeError(ex))
                    .putLong("bubbleRemoveTargetErrorAt", System.currentTimeMillis())
                    .apply();
            }
        }
    }

    private void updateBubbleRemoveTargetHighlight() {
        if (bubbleRemoveTargetView == null || bubbleRemoveTargetParams == null || bubbleParams == null || bubbleView == null) return;
        int bubbleWidth = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : dp(69);
        int bubbleHeight = bubbleView.getHeight() > 0 ? bubbleView.getHeight() : dp(56);
        boolean inside = GtoBubbleDismissPolicy.isDropInside(
            bubbleParams.x, bubbleParams.y, bubbleWidth, bubbleHeight,
            bubbleRemoveTargetParams.x, bubbleRemoveTargetParams.y,
            bubbleRemoveTargetParams.width, bubbleRemoveTargetParams.height
        );
        if (inside == bubbleRemoveTargetHighlighted) return;
        bubbleRemoveTargetHighlighted = inside;
        bubbleRemoveTargetView.setBackground(makeRoundedBackground(
            inside ? Color.rgb(177, 47, 55) : Color.rgb(92, 45, 48),
            dp(18)
        ));
        bubbleRemoveTargetView.setText(inside ? "Solte para remover" : "Remover e parar NVU");
    }

    private boolean isBubbleDroppedOnRemoveTarget() {
        if (gtoForeground || bubbleRemoveTargetView == null || bubbleRemoveTargetParams == null
            || bubbleParams == null || bubbleView == null) return false;
        int bubbleWidth = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : dp(69);
        int bubbleHeight = bubbleView.getHeight() > 0 ? bubbleView.getHeight() : dp(56);
        return GtoBubbleDismissPolicy.isDropInside(
            bubbleParams.x, bubbleParams.y, bubbleWidth, bubbleHeight,
            bubbleRemoveTargetParams.x, bubbleRemoveTargetParams.y,
            bubbleRemoveTargetParams.width, bubbleRemoveTargetParams.height
        );
    }

    private void hideBubbleRemoveTarget() {
        if (bubbleRemoveTargetView != null && windowManager != null) {
            try { windowManager.removeView(bubbleRemoveTargetView); } catch (Exception ignored) {}
        }
        bubbleRemoveTargetView = null;
        bubbleRemoveTargetParams = null;
        bubbleRemoveTargetHighlighted = false;
        if (prefs != null) prefs.edit().putBoolean("bubbleRemoveTargetVisible", false).apply();
    }

    private void stopObserverFromFloatingBubble() {
        if (prefs != null) {
            prefs.edit()
                .putString("lastEvent", "Observador GTO encerrado pelo gesto de remover da bolinha")
                .putLong("observerStoppedFromBubbleAt", System.currentTimeMillis())
                .apply();
        }
        Intent stopIntent = new Intent(this, GtoObserverService.class).setAction(ACTION_STOP);
        try {
            startService(stopIntent);
        } catch (Exception ex) {
            // Same-process fallback. The gesture must never leave a half-running observer.
            if (prefs != null) prefs.edit().putBoolean("enabled", false).apply();
            stopProjection();
            hideOverlays();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
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
        boolean resultActionArmed = resultActionCanBeObserved(state)
            && !replacementSelectionArmed;
        boolean shouldShow = gtoForeground
            && projectionActive
            && captureStabilityGate.isReady()
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
            boolean resultActionArmed = resultActionCanBeObserved(state)
                && !replacementSelectionArmed;
            if (!gtoForeground || !captureStabilityGate.isReady()) return false;
            if (selectionArmed) {
                // The menu is intentionally NOT a blocker. It is non-modal and the GTO
                // Aceitar column remains touchable beside it. R3.21 discarded exactly
                // that ACTION_OUTSIDE event, leaving the accepted trip in WAITING_FREIGHT.
                // Ignore only a coordinate that provably belongs to the NVU menu itself;
                // redacted OEM coordinates still use the visual correlation path.
                if (isTouchInsideOpenMenu(event)) return false;
                queueFreightTouchMarker(event);
            } else if (resultActionArmed) {
                // Preserve the result-screen rule: actions taken while the NVU menu is
                // open belong to that menu and must not be interpreted as Receber/ADS.
                if (menuView != null) return false;
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
            boolean resultState = resultActionCanBeObserved(getTripState());
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
            boolean resultState = resultActionCanBeObserved(getTripState());
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

    private boolean isTouchInsideOpenMenu(MotionEvent event) {
        if (event == null || menuView == null || menuParams == null) return false;
        float x = event.getRawX();
        float y = event.getRawY();
        if (!Float.isFinite(x) || !Float.isFinite(y)
            || Math.abs(x) <= 1f && Math.abs(y) <= 1f) return false;
        int width = menuView.getWidth();
        int height = menuView.getHeight();
        if (width <= 0 || height <= 0) return false;
        return x >= menuParams.x && x < menuParams.x + width
            && y >= menuParams.y && y < menuParams.y + height;
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
        if (menuView != null) refreshMenuContents();
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
        if (!resultActionCanBeObserved(state)) return;
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
        if (!resultActionCanBeObserved(state)) return;
        boolean reviewPending = STATE_CONFIRMING_FREIGHT.equals(state) && isFreightReviewPending();
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
        // Exact Receber is durable evidence. During field review, defer completion until
        // the selected freight is fully locked so the trip cannot be created without it.
        if (reviewPending) {
            prefs.edit()
                .putBoolean("pendingResultDuringFreightReview", true)
                .putString("lastEvent", "Receber confirmado · aguardando apenas a revisão do frete")
                .apply();
            updateNotification();
            return;
        }
        confirmNormalResultAutomatically();
    }

    private void latchExactAdsTouch(long now, String source) {
        String state = getTripState();
        if (!resultActionCanBeObserved(state)) return;
        boolean reviewPending = STATE_CONFIRMING_FREIGHT.equals(state) && isFreightReviewPending();
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
        if (reviewPending) {
            prefs.edit()
                .putBoolean("pendingBonusDuringFreightReview", true)
                .putBoolean("pendingResultDuringFreightReview", true)
                .putString("lastEvent", "ADS/bônus detectado · aguardando revisão do frete sem perder o resultado")
                .apply();
            updateNotification();
            return;
        }
        setTripState(STATE_AWAITING_BONUS, "Opção de dobrar valor detectada com toque preciso");
    }

    private void queueFreightTouchMarker(MotionEvent sourceEvent) {
        if (captureHandler == null
            || !captureStabilityGate.isReady()
            || fastTouchMarkerQueued
            || selectionCoordinator.isCriticalWindow()) return;
        final float rawX = sourceEvent == null ? -1f : sourceEvent.getRawX();
        final float rawY = sourceEvent == null ? -1f : sourceEvent.getRawY();
        final float localX = sourceEvent == null ? -1f : sourceEvent.getX();
        final float localY = sourceEvent == null ? -1f : sourceEvent.getY();
        fastTouchMarkerQueued = true;
        captureHandler.post(() -> {
            fastTouchMarkerQueued = false;
            if (!gtoForeground || !captureStabilityGate.isReady()) return;

            boolean touchArmedDuringPromotion = false;

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
                // Initial automatic bootstrap must not lose a fast Aceitar tap. On a
                // fresh/idle session one structurally valid freight frame plus a real
                // ACTION_OUTSIDE is enough to enter WAITING_FREIGHT; the row still has
                // to be proven by an exact Aceitar coordinate or a post-touch visual
                // transition before any freight can be committed.
                int exactReplacementRow = exactConsistentRowForTouch(
                    rawX, rawY, localX, localY, replacementFreightButtons
                );
                if (exactReplacementRow >= 0) {
                    if (replacementFreightPressedRow >= 0
                        && replacementFreightPressedRow != exactReplacementRow) {
                        prefs.edit()
                            .putString("lastEvent", "Toque e quadro pressionado apontaram linhas diferentes; seleção descartada")
                            .putString("selectionConfirmationStatus", "FAILED")
                            .putString("selectionFailureReason", "A linha tocada não coincidiu com a transição visual do botão Aceitar.")
                            .putLong("selectionFailureAt", System.currentTimeMillis())
                            .apply();
                        return;
                    }
                    replacementFreightPressedRow = exactReplacementRow;
                    replacementFreightPressedScore = 1f;
                }
                if (GtoFreightBootstrapPolicy.shouldAwaitSecondListFrame(
                    replacementState, activeTripFreightListFrames, replacementFreightPressedRow
                )) {
                    replacementFreightTouchPending = true;
                    replacementFreightTouchAt = System.currentTimeMillis();
                    prefs.edit().putString("lastEvent", "Novo frete tocado · aguardando segundo quadro da lista").apply();
                    return;
                }
                if (!promoteReplacementFreightCandidateToWaiting(
                    true, rawX, rawY, localX, localY
                )) return;
                touchArmedDuringPromotion = true;
            }

            if (!STATE_WAITING_FREIGHT.equals(getTripState())) return;

            long touchAt = System.currentTimeMillis();
            mainHandler.post(() -> armSelectionProbe(touchAt));

            if (!touchArmedDuringPromotion) {
                armFastTouchPulseOnCaptureThread(rawX, rawY, localX, localY);
            }
        });
    }

    private void toggleMenu() {
        long now = System.currentTimeMillis();
        if (now - lastBubbleTapAt < BUBBLE_TAP_DEBOUNCE_MS) return;
        lastBubbleTapAt = now;
        // The bubble is outside the card window. Android may deliver ACTION_OUTSIDE to
        // the card before ACTION_UP reaches the bubble. Never let that same gesture close
        // and immediately reopen the panel.
        if (now - lastMenuOutsideTouchAt < OUTSIDE_SAME_GESTURE_GUARD_MS) {
            // ACTION_OUTSIDE may precede this bubble ACTION_UP for the same physical
            // gesture. Suppress only that tiny same-gesture window; an intentional quick
            // second tap must remain responsive.
            if (menuView != null) closeMenu();
            return;
        }
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

        // HF19: the card has a stable width and a measured maximum height. Long review
        // content scrolls inside the card instead of being clipped outside the screen.
        menuScrollView = new ScrollView(this);
        menuScrollView.setFillViewport(false);
        menuContentView = new LinearLayout(this);
        menuContentView.setOrientation(LinearLayout.VERTICAL);
        menuScrollView.addView(menuContentView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        menuView.addView(menuScrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        menuView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                mainHandler.post(this::adjustOpenMenuLayoutAfterMeasure);
            }
        });

        menuOpenedAt = System.currentTimeMillis();
        populateMenuContents(menuContentView);
        lastMenuRenderSignature = menuRenderSignature();

        int menuFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if (!isFreightReviewPending()) menuFlags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        menuParams = new WindowManager.LayoutParams(
            dp(256),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            menuFlags,
            PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;
        if (isFreightReviewPending()) {
            menuParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        }

        DisplayMetrics screen = realDisplayMetrics();
        int menuWidth = dp(256);
        int bubbleWidth = bubbleView != null && bubbleView.getWidth() > 0 ? bubbleView.getWidth() : dp(69);
        int bubbleHeight = bubbleView != null && bubbleView.getHeight() > 0 ? bubbleView.getHeight() : dp(56);
        int margin = dp(8);
        int gap = dp(8);
        int safeLeft = margin;
        int menuRightLimit = Math.min(screen.widthPixels, freightOverlaySafeRight(screen));
        int safeRight = Math.max(safeLeft, menuRightLimit - margin);
        if (safeRight - safeLeft < menuWidth) {
            menuView = null;
            menuScrollView = null;
            menuContentView = null;
            menuParams = null;
            showStatusChip(
                "Painel NVU não aberto para não encobrir os fretes. Feche a lista ou use uma área maior da tela.",
                4200L
            );
            return;
        }

        bubbleXBeforeMenuOpen = bubbleParams.x;
        bubbleYBeforeMenuOpen = bubbleParams.y;
        bubbleAutoDockedForMenu = false;
        int side = GtoOverlayLayoutPolicy.chooseMenuSideForBubble(
            bubbleParams.x, bubbleWidth, menuWidth, safeLeft, safeRight, gap
        );
        int initialBubbleX = GtoOverlayLayoutPolicy.bubbleXForMenuSide(
            side, bubbleParams.x, bubbleWidth, menuWidth, safeLeft, safeRight, gap
        );
        if (GtoOverlayLayoutPolicy.horizontalPairFits(safeLeft, safeRight, bubbleWidth, menuWidth, gap)
            && initialBubbleX != bubbleParams.x) {
            bubbleParams.x = initialBubbleX;
            bubbleAutoDockedForMenu = true;
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            } catch (Exception ex) {
                recordOverlayFailure(ex);
            }
        }
        menuParams.x = GtoOverlayLayoutPolicy.menuXBesideBubble(
            side, bubbleParams.x, bubbleWidth, menuWidth, safeLeft, safeRight, gap
        );
        int initialSafeTop = safeTopInsetPx() + margin;
        int initialSafeBottom = safeBottomInsetPx() + margin;
        menuParams.y = GtoOverlayLayoutPolicy.centeredMenuYBesideBubble(
            bubbleParams.y,
            bubbleHeight,
            dp(220),
            initialSafeTop,
            screen.heightPixels - initialSafeBottom
        );

        // HF17: an outside touch minimizes only the card. FLAG_NOT_TOUCH_MODAL lets the
        // same touch continue to the GTO while FLAG_WATCH_OUTSIDE_TOUCH gives this window
        // a single ACTION_OUTSIDE notification. The bubble/service/capture stay alive.
        menuView.setOnTouchListener((view, event) -> {
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                lastMenuOutsideTouchAt = System.currentTimeMillis();
                mainHandler.post(this::closeMenu);
                return false;
            }
            return false;
        });

        try {
            windowManager.addView(menuView, menuParams);
            prefs.edit()
                .remove("menuOverlayError")
                .remove("menuOverlayErrorAt")
                .apply();
            // Layout is only authoritative after WindowManager measures WRAP_CONTENT.
            // If the bubble ended up underneath the card, dock it beside the measured card
            // on the side with the most free room.
            menuView.post(this::adjustOpenMenuLayoutAfterMeasure);
        } catch (Exception ex) {
            String detail = describeError(ex);
            prefs.edit()
                .putString("menuOverlayError", detail)
                .putLong("menuOverlayErrorAt", System.currentTimeMillis())
                .putString("lastEvent", "Falha ao abrir painel flutuante: " + detail)
                .apply();
            // If opening failed after an automatic bubble nudge, return the bubble to the
            // driver's position instead of leaving it stranded at a system-chosen point.
            closeMenu();
            showStatusChip("Não foi possível abrir o painel NVU. Toque novamente.", 2200L);
        }
    }

    private void populateMenuContents(LinearLayout target) {
        if (target == null) return;
        target.removeAllViews();
        activeReviewInput = null;

        TextView title = new TextView(this);
        title.setText(menuTitle());
        title.setTextColor(Color.WHITE);
        title.setTextSize(13f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(2), dp(4), dp(9));
        target.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        String state = getTripState();
        String guideText = currentJourneyGuide(state);
        if (!guideText.isEmpty()) {
            TextView journeyGuide = new TextView(this);
            journeyGuide.setText(guideText);
            journeyGuide.setTextColor(Color.rgb(154, 164, 178));
            journeyGuide.setTextSize(10.5f);
            journeyGuide.setPadding(dp(6), 0, dp(6), dp(8));
            target.addView(journeyGuide);
        }

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
            String statusText = statusLabel(state);
            if (!statusText.isEmpty()) {
                TextView status = new TextView(this);
                status.setText(statusText);
                status.setTextColor(Color.rgb(210, 216, 224));
                status.setTextSize(12f);
                status.setPadding(dp(6), dp(5), dp(6), dp(8));
                target.addView(status);
            }

            if (STATE_WAITING_FREIGHT.equals(state)) {
                TextView helper = new TextView(this);
                int detected = prefs.getInt("freightCount", 0);
                String selectionFailure = prefs.getString("selectionFailureReason", "").trim();
                boolean failedSelection = "FAILED".equals(
                    prefs.getString("selectionConfirmationStatus", "")
                );
                if (failedSelection) {
                    helper.setText("Frete não confirmado · "
                        + (selectionFailure.isEmpty()
                            ? "linha ilegível, encoberta ou leituras divergentes."
                            : selectionFailure)
                        + "\nReabra a lista e selecione novamente.");
                } else if (!projectionActive) {
                    String pStatus = prefs.getString("projectionStatus", "");
                    String pError = prefs.getString("projectionError", "").trim();
                    if (projectionPermissionInFlight) {
                        helper.setText("Autorização em andamento · conclua a confirmação do Android. O botão ficará bloqueado até o resultado ser validado.");
                    } else if (projectionSurfacePending || "WAITING_GTO_GEOMETRY".equals(pStatus)
                        || "WAITING_GTO_LANDSCAPE".equals(pStatus)) {
                        helper.setText("Compartilhamento aceito e validado · ativando a captura no GTO. Não autorize novamente.");
                    } else if ("STOPPED_BEFORE_SURFACE".equals(pStatus)) {
                        helper.setText("O Android encerrou o compartilhamento antes de a captura iniciar. Verifique se outro gravador/espelhamento está ativo e autorize novamente.");
                    } else if ("STOPPED_EARLY".equals(pStatus)) {
                        helper.setText("Leitura foi encerrada logo após iniciar. Feche outro gravador/compartilhamento de tela, se houver, e toque em Autorizar novamente.");
                    } else if ("START_FAILED".equals(pStatus) || "GRANT_DATA_INVALID".equals(pStatus)) {
                        helper.setText("Leitura não iniciou" + (pError.isEmpty() ? "." : " · " + pError) + "\nToque em Autorizar novamente.");
                    } else if ("CONSENT_RESULT_TIMEOUT".equals(pStatus)
                        || "CONSENT_RESULT_UNBOUND".equals(pStatus)
                        || "CONSENT_HOST_FINISHED_WITHOUT_RESULT".equals(pStatus)) {
                        helper.setText("A autorização anterior não foi concluída pelo Android"
                            + (pError.isEmpty() ? "." : " · " + pError)
                            + "\nToque em Autorizar somente uma vez para iniciar uma nova sessão.");
                    } else if (pStatus.startsWith("REQUESTING_") || pStatus.startsWith("CONSENT_")) {
                        helper.setText("Autorização em andamento · conclua a confirmação do Android.");
                    } else {
                        helper.setText("Leitura da tela não está ativa · toque em Autorizar.");
                    }
                } else if (detected > 0) {
                    helper.setText("Lista detectada · " + detected + " frete" + (detected == 1 ? "" : "s")
                        + ". Selecione um.");
                } else {
                    String runtimeError = prefs.getString("runtimePermissionError", "").trim();
                    String readiness = prefs.getString("captureReadiness", "");
                    if (!runtimeError.isEmpty()) {
                        helper.setText("Leitura bloqueada · " + runtimeError);
                    } else if (GtoCaptureStabilityGate.CAPTURE_WAITING_GTO_FOREGROUND.equals(readiness)) {
                        helper.setText("Aguardando o GTO voltar ao primeiro plano.");
                    } else if (GtoCaptureStabilityGate.CAPTURE_WAITING_ORIENTATION.equals(readiness)
                        || GtoCaptureStabilityGate.CAPTURE_WAITING_STABLE_FRAMES.equals(readiness)) {
                        helper.setText("Aguardando a tela do GTO estabilizar.");
                    } else {
                        helper.setText("Abra a lista de fretes. A detecção é automática.");
                    }
                }
                helper.setTextColor(failedSelection ? Color.rgb(245, 190, 86) : Color.rgb(154, 164, 178));
                helper.setTextSize(10.5f);
                helper.setPadding(dp(6), 0, dp(6), dp(6));
                target.addView(helper);

                if (!projectionActive && !projectionPermissionInFlight && !projectionSurfacePending) {
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
                if (isFreightReviewPending()) {
                    String field = prefs.getString("reviewRequiredField", "");
                    if (GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(field)) {
                        helper.setText("Origem não confirmada. Informe a origem do frete.");
                    } else {
                        helper.setText("Confirme somente " + reviewFieldLabel(field) + ".");
                    }
                    helper.setTextColor(Color.rgb(245, 190, 86));
                    helper.setTextSize(10.5f);
                    helper.setPadding(dp(6), 0, dp(6), dp(7));
                    target.addView(helper);

                    FreightOption draft = freightReviewFromPrefs();
                    TextView known = new TextView(this);
                    StringBuilder details = new StringBuilder();
                    if (!draft.cargo.isEmpty()) details.append("Carga: ").append(draft.cargo).append('\n');
                    if (!draft.originCompany.isEmpty()) details.append("Origem: ").append(draft.originCompany).append('\n');
                    if (!draft.destination.isEmpty()) details.append("Destino: ").append(draft.destination).append('\n');
                    if (!draft.km.isEmpty()) details.append("Distância: ").append(draft.km).append('\n');
                    if (!draft.offeredValue.isEmpty()) details.append("Valor: ").append(draft.offeredValue);
                    if (details.length() > 0) {
                        known.setText(details.toString().trim());
                        known.setTextColor(Color.rgb(154, 164, 178));
                        known.setTextSize(9.8f);
                        known.setPadding(dp(6), 0, dp(6), dp(7));
                        target.addView(known);
                    }

                    if (GtoFreightReviewPolicy.LOCAL_INTEGRITY.equals(field)) {
                        Button retry = menuButton("Tentar confirmar integridade");
                        retry.setOnClickListener(v -> commitReviewedFreight(freightReviewFromPrefs()));
                        target.addView(retry);
                    } else {
                        EditText input = new EditText(this);
                        input.setSingleLine(true);
                        input.setTextColor(Color.WHITE);
                        input.setHintTextColor(Color.rgb(130, 138, 149));
                        input.setTextSize(11f);
                        input.setHint(GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(field)
                            ? "Origem do frete"
                            : "Digite " + reviewFieldLabel(field) + " exatamente como aparece no GTO");
                        if (GtoFreightReviewPolicy.DISTANCE.equals(field)) {
                            input.setInputType(InputType.TYPE_CLASS_NUMBER);
                        } else if (GtoFreightReviewPolicy.VALUE.equals(field)) {
                            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                        } else {
                            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                        }
                        if (!field.equals(activeReviewInputField)) {
                            activeReviewInputField = field;
                            activeReviewInputDraft = "";
                        }
                        if (!activeReviewInputDraft.isEmpty()) {
                            input.setText(activeReviewInputDraft);
                            input.setSelection(input.getText().length());
                        }
                        activeReviewInput = input;
                        target.addView(input, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ));
                        Button save = menuButton(GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(field)
                            ? "Salvar origem"
                            : "Salvar " + reviewFieldLabel(field));
                        save.setEnabled(false);
                        input.addTextChangedListener(new TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                                String typed = s == null ? "" : s.toString();
                                activeReviewInputDraft = typed;
                                activeReviewInputField = field;
                                save.setEnabled(GtoFreightReviewPolicy.isManualValueValid(field, typed));
                            }
                            @Override public void afterTextChanged(Editable s) {}
                        });
                        save.setOnClickListener(v -> applyManualFreightReviewField(field, input.getText() == null ? "" : input.getText().toString()));
                        target.addView(save);
                    }
                } else {
                    helper.setText("Frete identificado. Conferindo os dados…");
                    helper.setTextColor(Color.rgb(154, 164, 178));
                    helper.setTextSize(10.5f);
                    helper.setPadding(dp(6), 0, dp(6), dp(6));
                    target.addView(helper);
                }
                if (!projectionActive && !projectionPermissionInFlight && !projectionSurfacePending) {
                    Button authorize = menuButton("Autorizar leitura da tela");
                    authorize.setOnClickListener(v -> {
                        closeMenu();
                        requestProjectionPermission();
                    });
                    target.addView(authorize);
                }
            }

            if (STATE_TRIP_IN_PROGRESS.equals(state)) {
                String selectedSummary = prefs.getString("selectedFreightSummary", "");
                TextView freightHeading = new TextView(this);
                freightHeading.setText("Frete atual em andamento");
                freightHeading.setTextColor(Color.WHITE);
                freightHeading.setTypeface(freightHeading.getTypeface(), android.graphics.Typeface.BOLD);
                freightHeading.setTextSize(11.5f);
                freightHeading.setPadding(dp(6), 0, dp(6), dp(4));
                target.addView(freightHeading);
                if (!selectedSummary.isEmpty()) {
                    TextView selectedInfo = new TextView(this);
                    String destination = prefs.getString("selectedDestination", "");
                    String originCompany = prefs.getString("selectedOriginCompany", "");
                    String cargo = prefs.getString("selectedCargo", "");
                    String km = prefs.getString("selectedKm", "");
                    String value = prefs.getString("selectedValue", "");
                    StringBuilder details = new StringBuilder();
                    if (!cargo.isEmpty()) details.append("Carga: ").append(cargo).append('\n');
                    details.append("Origem: ").append(originCompany.isEmpty() ? "—" : originCompany);
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
                    if (projectionPermissionInFlight) {
                        captureHelper.setText("Autorização em andamento. Conclua a confirmação do Android; não toque novamente em Autorizar.");
                    } else if (projectionSurfacePending) {
                        captureHelper.setText("Compartilhamento aceito e validado. Aguardando o GTO em paisagem para concluir a ativação da leitura; não autorize novamente.");
                    } else {
                        captureHelper.setText("A viagem foi preservada. Abra a bolinha NVU e autorize a leitura sem sair do GTO.");
                    }
                    captureHelper.setTextColor(Color.rgb(245, 190, 86));
                    captureHelper.setTextSize(10.5f);
                    captureHelper.setPadding(dp(6), 0, dp(6), dp(6));
                    target.addView(captureHelper);

                    if (!projectionPermissionInFlight && !projectionSurfacePending) {
                        Button authorize = menuButton("Autorizar leitura da tela");
                        authorize.setOnClickListener(v -> {
                            closeMenu();
                            requestProjectionPermission();
                        });
                        target.addView(authorize);
                    }
                }


            }

            if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) {
                TextView helper = new TextView(this);
                String resultValue = prefs.getString("resultValue", "");
                boolean valuePendingConsensus = resultValue.isEmpty()
                    && prefs.getInt("resultValueEvidenceCount", 0) > 0;
                boolean fallbackRequired = resultTouchFallbackRequired || prefs.getBoolean("resultTouchFallbackRequired", false);
                boolean fallbackReady = resultTouchFallbackReady || prefs.getBoolean("resultTouchFallbackReady", false);
                if (fallbackReady) {
                    helper.setText(resultValue.isEmpty()
                        ? (valuePendingConsensus
                            ? "O toque foi detectado, mas o valor ainda está sendo confirmado. A entrega permanece preservada."
                            : "A tela Concluído fechou, mas este Android não informou o toque. Confirme abaixo somente se você tocou em Receber.")
                        : "Resultado identificado · " + resultValue + "\nA tela Concluído fechou sem coordenadas de toque. Confirme abaixo somente se você tocou em Receber.");
                } else if (fallbackRequired) {
                    helper.setText(resultValue.isEmpty()
                        ? (valuePendingConsensus
                            ? "Resultado identificado. Mantenha a tela Concluído visível e toque em Receber."
                            : "Resultado identificado. Toque em Receber no GTO. Este Android usa uma contingência segura caso o toque não seja informado.")
                        : "Resultado identificado · " + resultValue + "\nToque em Receber no GTO. A NVU acompanhará a saída da tela para a contingência segura.");
                } else {
                    helper.setText("Toque em Receber para concluir a viagem.");
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
                if (!projectionActive && !projectionPermissionInFlight && !projectionSurfacePending) {
                    Button authorize = menuButton("Autorizar leitura da tela");
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

        Button operation = menuButton("Operação");
        operation.setOnClickListener(v -> {
            operationSummaryExpanded = !operationSummaryExpanded;
            refreshMenuContents();
        });
        target.addView(operation);
        if (operationSummaryExpanded) {
            TextView operationSummary = new TextView(this);
            operationSummary.setText(operationSummaryText());
            operationSummary.setTextColor(Color.rgb(210, 216, 224));
            operationSummary.setTextSize(10.5f);
            operationSummary.setPadding(dp(10), dp(7), dp(10), dp(5));
            operationSummary.setBackground(makeRoundedBackground(Color.rgb(35, 39, 46), dp(9)));
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            summaryParams.gravity = Gravity.CENTER_HORIZONTAL;
            summaryParams.topMargin = dp(5);
            operationSummary.setLayoutParams(summaryParams);
            target.addView(operationSummary);
        }
    }

    private String menuRenderSignature() {
        if (prefs == null) return getTripState() + "|" + operationSummaryExpanded;
        return getTripState()
            + "|" + operationSummaryExpanded
            + "|" + projectionActive
            + "|" + projectionPermissionInFlight
            + "|" + projectionSurfacePending
            + "|" + prefs.getInt("freightCount", 0)
            + "|" + prefs.getString("selectionConfirmationStatus", "")
            + "|" + prefs.getString("reviewRequiredField", "")
            + "|" + prefs.getString("reviewCargo", "")
            + "|" + prefs.getString("reviewOriginCompany", "")
            + "|" + prefs.getString("reviewDestination", "")
            + "|" + prefs.getString("reviewKm", "")
            + "|" + prefs.getString("reviewValue", "")
            + "|" + prefs.getString("selectedCargo", "")
            + "|" + prefs.getString("selectedOriginCompany", "")
            + "|" + prefs.getString("selectedDestination", "")
            + "|" + prefs.getString("selectedKm", "")
            + "|" + prefs.getString("selectedValue", "")
            + "|" + prefs.getString("resultValue", "")
            + "|" + prefs.getString("gtoTripSyncStatus", "");
    }

    private void refreshMenuContents() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::refreshMenuContents);
            return;
        }
        if (menuView == null || menuContentView == null) return;
        String nextSignature = menuRenderSignature();
        if (nextSignature.equals(lastMenuRenderSignature)) return;

        // HF19: never destroy/recreate an EditText while the driver is typing the same
        // review field. Capture/OCR updates may continue in the background, but the form
        // remains visually stable until the user saves or the required field changes.
        String requiredField = prefs.getString("reviewRequiredField", "");
        if (activeReviewInput != null
            && activeReviewInput.hasFocus()
            && isFreightReviewPending()
            && requiredField.equals(activeReviewInputField)) {
            lastMenuRenderSignature = nextSignature;
            menuView.post(this::adjustOpenMenuLayoutAfterMeasure);
            return;
        }

        populateMenuContents(menuContentView);
        lastMenuRenderSignature = nextSignature;
        try {
            if (windowManager != null && menuParams != null) {
                windowManager.updateViewLayout(menuView, menuParams);
                menuView.post(this::adjustOpenMenuLayoutAfterMeasure);
            }
        } catch (Exception ex) {
            prefs.edit()
                .putString("menuOverlayError", describeError(ex))
                .putLong("menuOverlayErrorAt", System.currentTimeMillis())
                .apply();
        }
    }

    private void showStatusChip(String text, long durationMs) {
        showStatusChip(text, durationMs, null, false, "");
    }

    private void showStatusChip(String text, long durationMs, @Nullable Runnable onShown) {
        showStatusChip(text, durationMs, onShown, false, "");
    }

    private void showDriverStageChip(String text, long durationMs, String stageKey, @Nullable Runnable onShown) {
        showStatusChip(text, durationMs, onShown, true, stageKey == null ? "" : stageKey);
    }

    private void showStatusChip(
        String text,
        long durationMs,
        @Nullable Runnable onShown,
        boolean driverStage,
        String driverStageKey
    ) {
        mainHandler.post(() -> {
            long now = System.currentTimeMillis();
            // HF19: an important journey message must remain visible long enough to be
            // readable before another stage is allowed to replace it.
            int incomingStagePriority = driverStage
                ? GtoDriverMessagePriorityPolicy.priorityFor(driverStageKey)
                : GtoDriverMessagePriorityPolicy.INFO;
            if (driverStage
                && statusChipIsDriverStage
                && statusChipView != null
                && !driverStageKey.equals(statusChipDriverStageKey)) {
                long visibleFor = Math.max(0L, now - statusChipShownAt);
                boolean queueUntilReadable = GtoDriverMessagePriorityPolicy.shouldQueueUntilReadable(
                    statusChipDriverStagePriority, incomingStagePriority, visibleFor, DRIVER_STAGE_MIN_VISIBLE_MS
                );
                long remaining = DRIVER_STAGE_MIN_VISIBLE_MS - visibleFor;
                if (queueUntilReadable && remaining > 0L) {
                    if (pendingDriverStageReplacementRunnable != null) {
                        mainHandler.removeCallbacks(pendingDriverStageReplacementRunnable);
                    }
                    pendingDriverStageReplacementRunnable = () -> {
                        pendingDriverStageReplacementRunnable = null;
                        if (prefs == null) return;
                        if (!gtoForeground && !transientForegroundSurfaceActive) return;
                        String pendingKey = prefs.getString("driverStagePendingKey", "");
                        if (!driverStageKey.equals(pendingKey)) return;
                        showStatusChip(text, durationMs, onShown, true, driverStageKey);
                    };
                    mainHandler.postDelayed(pendingDriverStageReplacementRunnable, remaining);
                    return;
                }
            }
            // Driver-stage banners are authoritative while visible. A lower-priority
            // diagnostic/toast-style chip must never erase the current journey message.
            if (!driverStage && statusChipIsDriverStage && statusChipView != null) return;
            // HF15 root-cause fix: every previous chip used to leave an uncancelled
            // postDelayed(this::hideStatusChip). That old callback could later remove a
            // brand-new stage message such as "Tudo preparado, podemos partir!".
            cancelScheduledStatusChipHide();
            hideStatusChipViewOnly();
            if (windowManager == null || !Settings.canDrawOverlays(this)) return;
            if (!driverStage && bubbleParams == null) return;

            TextView chip = new TextView(this);
            chip.setText(text);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(driverStage ? 12f : 11f);
            chip.setGravity(driverStage ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
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
            DisplayMetrics screen = realDisplayMetrics();
            if (driverStage) {
                // Driver-stage messages are intentionally independent from the bubble and
                // always occupy the same stable top-centre location.
                params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                params.x = 0;
                params.y = safeTopInsetPx() + dp(6);
                chip.setMaxWidth(Math.max(dp(220), Math.round(screen.widthPixels * 0.72f)));
            } else {
                params.gravity = Gravity.TOP | Gravity.START;
                int chipRightLimit = Math.min(screen.widthPixels, freightOverlaySafeRight(screen));
                chip.setMaxWidth(Math.max(dp(120), chipRightLimit - dp(16)));
                int desiredX = bubbleParams.x - dp(250);
                if (desiredX < dp(8)) desiredX = bubbleParams.x + dp(64);
                params.x = clamp(desiredX, dp(8), Math.max(dp(8), chipRightLimit - chip.getMaxWidth()));
                params.y = clamp(bubbleParams.y + dp(4), dp(8), Math.max(dp(8), screen.heightPixels - dp(64)));
            }

            try {
                windowManager.addView(chip, params);
                statusChipView = chip;
                statusChipParams = params;
                statusChipIsDriverStage = driverStage;
                statusChipDriverStageKey = driverStage ? driverStageKey : "";
                statusChipDriverStagePriority = driverStage ? incomingStagePriority : GtoDriverMessagePriorityPolicy.INFO;
                statusChipShownAt = System.currentTimeMillis();
                final TextView shownChip = chip;
                if (onShown != null) {
                    long acknowledgementDelay = driverStage ? DRIVER_STAGE_MIN_VISIBLE_MS : 0L;
                    mainHandler.postDelayed(() -> {
                        if (statusChipView != shownChip) return;
                        if (driverStage && !driverStageKey.equals(statusChipDriverStageKey)) return;
                        onShown.run();
                    }, acknowledgementDelay);
                }
                statusChipHideRunnable = () -> {
                    if (statusChipView != shownChip) return;
                    hideStatusChip();
                };
                long visibleFor = driverStage
                    ? Math.max(DRIVER_STAGE_MIN_VISIBLE_MS + 250L, durationMs)
                    : Math.max(900L, durationMs);
                mainHandler.postDelayed(statusChipHideRunnable, visibleFor);
            } catch (Exception ex) {
                prefs.edit()
                    .putString("statusOverlayError", describeError(ex))
                    .putLong("statusOverlayErrorAt", System.currentTimeMillis())
                    .apply();
                statusChipView = null;
                statusChipParams = null;
                statusChipIsDriverStage = false;
                statusChipDriverStageKey = "";
                statusChipDriverStagePriority = GtoDriverMessagePriorityPolicy.INFO;
                statusChipShownAt = 0L;
                statusChipHideRunnable = null;
            }
        });
    }

    private void announceDriverStage(String code, String message, long durationMs, boolean force) {
        String sessionId = prefs.getString("gtoTripSessionId", "");
        String key = sessionId + "|" + (code == null ? "" : code);
        String previousKey = prefs.getString("driverStageShownKey", "");
        long now = System.currentTimeMillis();
        prefs.edit()
            .putString("driverStageCode", code == null ? "" : code)
            .putString("driverStageMessage", message == null ? "" : message)
            .putLong("driverStageAt", now)
            .putString("driverStagePendingKey", key)
            .putLong("driverStagePendingDurationMs", Math.max(900L, durationMs))
            .putLong("driverStagePendingAt", now)
            .apply();
        if (force || !key.equals(previousKey)) {
            recordObserverEvent("STAGE_" + (code == null ? "" : code), message);
            showDriverStageChip(message, durationMs, key, () -> acknowledgeDriverStageShown(key));
        }
    }

    private void acknowledgeDriverStageShown(String key) {
        if (prefs == null || key == null) return;
        if (!key.equals(prefs.getString("driverStagePendingKey", ""))) return;
        prefs.edit()
            .putString("driverStageShownKey", key)
            .remove("driverStagePendingKey")
            .remove("driverStagePendingDurationMs")
            .remove("driverStagePendingAt")
            .apply();
    }

    private void retryPendingDriverStageIfNeeded(long now) {
        if (prefs == null || statusChipView != null) return;
        String pendingKey = prefs.getString("driverStagePendingKey", "");
        if (pendingKey.isEmpty() || pendingKey.equals(prefs.getString("driverStageShownKey", ""))) return;
        if (now - lastDriverStageRetryAt < 750L) return;
        lastDriverStageRetryAt = now;
        String message = prefs.getString("driverStageMessage", "");
        if (message.isEmpty()) return;
        long duration = prefs.getLong("driverStagePendingDurationMs", 2600L);
        showDriverStageChip(message, duration, pendingKey, () -> acknowledgeDriverStageShown(pendingKey));
    }

    private String currentJourneyGuide(String state) {
        // HF19: the card owns persistent state/actions; top-centre banners own stage
        // transitions. Do not repeat the same driver instruction in two or three places.
        if (STATE_IDLE.equals(state) || STATE_CANCELLED.equals(state)) return "Pronto para iniciar.";
        return "";
    }

    private void recordObserverEvent(String code, String detail) {
        if (prefs == null) return;
        GtoObserverDiagnostics.record(
            prefs, code, detail, getTripState(), prefs.getString("gtoTripSessionId", "")
        );
    }

    private void recordObserverIncident(String type, String detail) {
        if (prefs == null) return;
        GtoObserverDiagnostics.incident(prefs, type, detail);
    }

    private void persistFreightFieldStatuses(FreightOption freight, String pendingField) {
        if (prefs == null) return;
        FreightOption f = freight == null ? new FreightOption() : freight;
        prefs.edit()
            .putString("fieldStatusCargo", GtoFreightFieldStatusPolicy.required(f.cargo, pendingField, GtoFreightReviewPolicy.CARGO))
            .putString("fieldStatusOrigin", GtoFreightFieldStatusPolicy.required(f.originCompany, pendingField, GtoFreightReviewPolicy.ORIGIN_COMPANY))
            .putString("fieldStatusDestination", GtoFreightFieldStatusPolicy.required(f.destination, pendingField, GtoFreightReviewPolicy.DESTINATION))
            .putString("fieldStatusDistance", GtoFreightFieldStatusPolicy.required(f.km, pendingField, GtoFreightReviewPolicy.DISTANCE))
            .putString("fieldStatusValue", GtoFreightFieldStatusPolicy.required(f.offeredValue, pendingField, GtoFreightReviewPolicy.VALUE))
            .putString("fieldStatusDestinationCompany", GtoFreightFieldStatusPolicy.optional(f.destinationCompany))
            .apply();
    }

    private void cancelScheduledStatusChipHide() {
        if (statusChipHideRunnable != null) {
            mainHandler.removeCallbacks(statusChipHideRunnable);
            statusChipHideRunnable = null;
        }
    }

    private void hideStatusChipViewOnly() {
        if (statusChipView != null && windowManager != null) {
            try {
                windowManager.removeView(statusChipView);
            } catch (Exception ignored) {}
        }
        statusChipView = null;
        statusChipParams = null;
        statusChipIsDriverStage = false;
        statusChipDriverStageKey = "";
        statusChipDriverStagePriority = GtoDriverMessagePriorityPolicy.INFO;
        statusChipShownAt = 0L;
    }

    private void hideStatusChip() {
        cancelScheduledStatusChipHide();
        hideStatusChipViewOnly();
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

    private String operationSummaryText() {
        String operation = prefs.getString("contractName", "").trim();
        if (operation.isEmpty()) operation = prefs.getString("jobId", "").trim();
        if (operation.isEmpty()) operation = "atual";
        if (!operation.toLowerCase(Locale.ROOT).startsWith("operação")
            && !operation.toLowerCase(Locale.ROOT).startsWith("operacao")) {
            operation = "Operação " + operation;
        }

        int progress = Math.max(prefs.getInt("jobProgress", 0), prefs.getInt("gtoJobProgress", 0));
        int total = prefs.getInt("jobTotalDeliveries", 0);
        String trips;
        if (total > 0) {
            trips = total >= 10
                ? String.format(Locale.ROOT, "%02d/%02d", Math.max(0, progress), total)
                : String.format(Locale.ROOT, "%d/%d", Math.max(0, progress), total);
        } else {
            trips = "—";
        }

        String vehicle = prefs.getString("vehicleName", "").trim();
        String trailer = prefs.getString("trailerName", "").trim();
        String health = prefs.getString("captureHealth", "");
        String observerStatus = "HEALTHY_REAL_FRAMES".equals(health)
            ? "Ativo"
            : (projectionActive ? "Recuperando leitura" : "Aguardando leitura");
        String syncStatus = prefs.getString("gtoTripSyncStatus", "");
        String syncLabel = GtoAutoTripSync.STATUS_SYNCED.equals(syncStatus)
            ? "Sincronizada ✓"
            : (GtoAutoTripSync.STATUS_PENDING.equals(syncStatus) ? "Envio pendente" : "—");
        return operation
            + "\nViagens: " + trips
            + "\nVeículo: " + (vehicle.isEmpty() ? "—" : vehicle)
            + "\nReboque: " + (trailer.isEmpty() ? "—" : trailer)
            + "\nObservador: " + observerStatus
            + "\nSincronização: " + syncLabel;
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

    private void pauseScreenAnalysisOutsideGto(String reason) {
        if (screenAnalysisPausedOutsideGto && screenAnalysisPausedAt > 0L) return;
        screenAnalysisPausedOutsideGto = true;
        tripStateWhenAnalysisPaused = getTripState();
        screenAnalysisPausedAt = System.currentTimeMillis();
        // Any whole-screen OCR scheduled before this foreground edge becomes stale.
        // The frozen selected-row OCR is allowed to finish internally, but its state
        // transition is deferred until GTO is foreground again.
        analysisOcrGeneration++;
        if (STATE_CONFIRMING_FREIGHT.equals(tripStateWhenAnalysisPaused)) {
            freightConfirmationWatchdogGeneration++;
        }
        prefs.edit()
            .putBoolean("screenAnalysisPaused", true)
            .putString("screenAnalysisPauseReason", reason == null ? "GTO fora do primeiro plano" : reason)
            .putString("tripStateWhenAnalysisPaused", tripStateWhenAnalysisPaused)
            .putLong("screenAnalysisPausedAt", screenAnalysisPausedAt)
            .putString("gtoBackgroundClassification", "MINIMIZED_OR_COVERED")
            .putString("lastEvent", "Leitura pausada · estado da viagem preservado")
            .apply();
        hideFreightTouchPulseSensor();
    }

    private void resumeScreenAnalysisInSameState(long absenceMs) {
        if (!screenAnalysisPausedOutsideGto) return;
        String currentState = getTripState();
        String preservedState = tripStateWhenAnalysisPaused == null ? "" : tripStateWhenAnalysisPaused;
        long pausedAt = screenAnalysisPausedAt;
        boolean hadExplicitPause = pausedAt > 0L;
        // Returning to GTO is never a journey transition. The canonical trip state in
        // SharedPreferences remains authoritative; no reset/cancellation is inferred
        // from time spent in another app or behind a system surface.
        screenAnalysisPausedOutsideGto = false;
        screenAnalysisPausedAt = 0L;
        prefs.edit()
            .putBoolean("screenAnalysisPaused", false)
            .remove("screenAnalysisPauseReason")
            .putString("gtoBackgroundClassification", "FOREGROUND_ACTIVE")
            .putString("lastEvent", "Leitura retomada no GTO · estado preservado: " + currentState)
            .apply();
        repairTripStateFromDurableFreightIfNeeded();
        currentState = getTripState();
        if (hadExplicitPause && !preservedState.isEmpty() && !preservedState.equals(currentState)) {
            boolean expectedLaunchPreparation = STATE_IDLE.equals(preservedState)
                && STATE_WAITING_FREIGHT.equals(currentState)
                && "WAITING_FREIGHT".equals(prefs.getString("gtoWorkLaunchPreparedState", ""))
                && prefs.getLong("gtoWorkLaunchPreparedAt", 0L) >= pausedAt;
            boolean expectedPostSyncNextTrip = STATE_RESULT_CONFIRMED.equals(preservedState)
                && STATE_WAITING_FREIGHT.equals(currentState)
                && prefs.getLong("gtoAutoNextTripPreparedAt", 0L) >= pausedAt;
            if (expectedLaunchPreparation || expectedPostSyncNextTrip) {
                prefs.edit()
                    .putString("gtoTripIntegrityStatus", "CONTEXT_LOCKED")
                    .remove("gtoTripIntegrityError")
                    .remove("gtoAutoNextTripPreparedAt")
                    .remove("gtoAutoNextTripFromSession")
                    .apply();
            } else {
                prefs.edit()
                    .putString("gtoTripIntegrityStatus", "STATE_PRESERVED_ON_RETURN")
                    .putString("gtoTripIntegrityError", "Estado mudou enquanto a leitura estava pausada: " + preservedState + " -> " + currentState)
                    .apply();
            }
        }
        tripStateWhenAnalysisPaused = currentState;
        // A notification shade, system overlay or app switch may leave one partially
        // composited frame in MediaProjection. Re-arm the existing geometry gate so
        // critical decisions resume only after three fresh, stable GTO frames.
        if (projectionActive && captureWidth > 0 && captureHeight > 0) {
            captureStabilityGate.reset(
                GtoCaptureStabilityGate.CAPTURE_WAITING_STABLE_FRAMES,
                captureWidth,
                captureHeight,
                System.currentTimeMillis()
            );
            prefs.edit()
                .putString("captureReadiness", GtoCaptureStabilityGate.CAPTURE_WAITING_STABLE_FRAMES)
                .putString("captureResumeBarrier", "VISIBILITY_RETURN_3_FRAMES")
                .apply();
        }
        refreshTransientVisualContextAfterGtoReturn(currentState, absenceMs);
        ensureCaptureContinuityAfterGtoReturn();
        updateFreightTouchPulseSensor();

        // Resume pending decisions from captures that were already frozen before the
        // app switch. No decision is allowed to advance the journey while another app
        // or a system surface is in front of GTO.
        if (STATE_CONFIRMING_FREIGHT.equals(currentState)) {
            if (deferredPreciseFreightCommit != null) {
                FreightOption pending = deferredPreciseFreightCommit;
                deferredPreciseFreightCommit = null;
                deferredSelectionFailureRow = -1;
                deferredSelectionFailureReason = "";
                commitPreciseFreight(pending);
            } else if (deferredSelectionFailureRow >= 0) {
                int row = deferredSelectionFailureRow;
                String reason = deferredSelectionFailureReason;
                deferredSelectionFailureRow = -1;
                deferredSelectionFailureReason = "";
                restoreWaitingAfterSelectionFailure(row, reason);
            } else {
                armFreightConfirmationWatchdog();
            }
        }
        if (deferredNormalResultConfirmation
            && (STATE_RESULT_DETECTED.equals(getTripState()) || STATE_AWAITING_BONUS.equals(getTripState()))) {
            deferredNormalResultConfirmation = false;
            confirmNormalResultAutomatically();
        }
        if (menuView != null) mainHandler.post(this::refreshMenuContents);
    }

    private void ensureCaptureContinuityAfterGtoReturn() {
        if (!captureIsNeededForCurrentState() || projectionPermissionInFlight) return;
        long now = System.currentTimeMillis();
        if (projectionSurfacePending && mediaProjection != null) {
            prefs.edit()
                .putString("captureContinuityStatus", "WAITING_GTO_GEOMETRY")
                .putLong("captureContinuityCheckedAt", now)
                .remove("captureContinuityError")
                .apply();
            maybeStartPendingProjectionSurface(now);
            return;
        }
        // The initial consent is deliberately delayed until GTO is already visible. Do
        // not convert that normal first-use path into a false reauthorization warning.
        if (!projectionActive && projectionPermissionAfterGtoOpenPending) return;

        boolean coreCaptureBound = projectionActive
            && mediaProjection != null
            && imageReader != null
            && virtualDisplay != null
            && captureHandler != null;
        if (coreCaptureBound) {
            boolean captureHealthy = isCapturePipelineHealthy(now);
            prefs.edit()
                .putString("captureContinuityStatus", captureHealthy
                    ? "RESUMED_AUTOMATICALLY"
                    : "RECOVERING_REAL_FRAMES")
                .putLong("captureContinuityCheckedAt", now)
                .remove("captureContinuityError")
                .apply();
            // A bound MediaProjection token must never be discarded merely because the
            // first frames after app/SystemUI return are stale. The watchdog repairs the
            // ImageReader surface in-place while preserving the trip state.
            maybeRecoverProjectionFrameDelivery(now);
            return;
        }

        // Reauthorization is only necessary when the actual bound capture resources are
        // gone. A mere app switch/frame stall is handled above without resetting state.
        if (projectionActive || mediaProjection != null || imageReader != null || virtualDisplay != null) {
            projectionGeneration++;
            projectionActive = false;
            releaseCaptureResources(true);
        }
        projectionStatus = "REAUTH_REQUIRED_ON_RETURN";
        prefs.edit()
            .putBoolean("projectionActive", false)
            .putBoolean("projectionSessionBound", false)
            .putBoolean("projectionSurfacePending", false)
            .putBoolean("projectionGrantValidated", false)
            .remove("projectionSessionBoundAt")
            .putBoolean("captureSurfaceReady", false)
            .putString("projectionStatus", projectionStatus)
            .putBoolean("projectionReauthRequired", true)
            .putBoolean("projectionReauthAutoAllowed", true)
            .putBoolean("projectionReauthNoticeShown", false)
            .putString("captureContinuityStatus", "REAUTH_REQUIRED")
            .putString("captureContinuityError", "MediaProjection indisponível ao retornar ao GTO")
            .putLong("captureContinuityCheckedAt", System.currentTimeMillis())
            .putString("lastEvent", "Viagem preservada · leitura da tela precisa ser reativada")
            .apply();
        ensureProjectionAuthorizationIfNeeded(System.currentTimeMillis());
    }

    private boolean isCapturePipelineHealthy(long now) {
        return GtoCaptureHealthPolicy.isHealthy(
            projectionActive,
            mediaProjection != null,
            virtualDisplay != null,
            imageReader != null,
            captureHandler != null,
            gtoForeground,
            screenAnalysisPausedOutsideGto,
            captureStabilityGate.isReady(),
            now,
            lastProjectionFrameAt,
            lastProjectionAnalyzedFrameAt
        );
    }

    private void markProjectionFrameAnalyzed(long now) {
        if (now <= 0L) return;
        lastProjectionAnalyzedFrameAt = now;
    }

    private void updateCaptureHealthIndicator(long now) {
        boolean healthy = isCapturePipelineHealthy(now);
        if (captureHealthDotView != null
            && (lastCaptureHealthIndicatorState == null
                || lastCaptureHealthIndicatorState.booleanValue() != healthy)) {
            captureHealthDotView.setBackground(makeRoundedBackground(
                healthy ? Color.WHITE : Color.rgb(82, 88, 96),
                dp(4)
            ));
            captureHealthDotView.setAlpha(healthy ? 1f : 0.55f);
            lastCaptureHealthIndicatorState = healthy;
        }
        String previous = prefs == null ? "" : prefs.getString("captureHealth", "");
        String current = healthy ? "HEALTHY_REAL_FRAMES" : "NOT_HEALTHY";
        if (prefs != null && !current.equals(previous)) {
            prefs.edit()
                .putString("captureHealth", current)
                .putLong("captureHealthChangedAt", now)
                .putLong("captureLastFrameAt", lastProjectionFrameAt)
                .putLong("captureLastAnalyzedFrameAt", lastProjectionAnalyzedFrameAt)
                .apply();
            if (healthy) {
                recordObserverEvent("CAPTURE_HEALTHY", "Frames reais recebidos e analisados");
            } else if (projectionActive) {
                recordObserverIncident("CAPTURE_UNHEALTHY", "Pipeline ativo sem saúde real de frames/análise");
            }
        }
    }

    private void refreshTransientVisualContextAfterGtoReturn(String state, long absenceMs) {
        if (!GtoDeterministicFlowPolicy.shouldRefreshTransientVisualContextAfterReturn(state)) return;

        // App switching preserves the journey but invalidates screen-local evidence.
        // Anything tied to a previous frame, button geometry or OCR crop must be rebuilt
        // from fresh GTO pixels after return so a short excursion to another app cannot
        // leave the observer visually stale. Durable selected-freight/result data is never
        // cleared here.
        lastOcrAt = 0L;
        lastStructureAt = 0L;
        lastVisualAnalysisAt = 0L;
        lastActiveTripVisualProbeAt = 0L;
        lastActiveTripFallbackOcrAt = 0L;

        if (STATE_WAITING_FREIGHT.equals(state)) {
            clearFastTouchPulse(false);
            clearFastPendingSelection();
            fastPreviousFreightFrame = null;
            fastPreviousFreightSequence = 0L;
            fastMissingListFrames = 0;
            lastFreightListSeenAt = 0L;
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
            freightListCycleSeen = false;
            freightListCycleClosed = false;
            freightListReopenPending = false;
            synchronized (freightOptions) { freightOptions.clear(); }
            freightHistory.clear();
            freightHistoryPage = -1;
            freightHistoryUpdatedAt = 0L;
            freightPageGeneration++;
            lastFreightPageOcrAt = 0L;
            synchronized (freightFrameLock) {
                realtimeAcceptRects.clear();
                fastFrameHistory.clear();
                if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                    latestFreightPanelFrame.recycle();
                }
                latestFreightPanelFrame = null;
                latestFreightPanelAt = 0L;
            }
            prefs.edit()
                .putInt("freightCount", 0)
                .remove("freightOptions")
                .remove("freightTextGeneration")
                .remove("freightTextAt")
                .putString("screenState", "OTHER")
                .putString("lastEvent", "Leitura retomada · aguardando pixels atuais da lista de fretes")
                .apply();
            return;
        }

        if (STATE_CONFIRMING_FREIGHT.equals(state) && isFreightReviewPending()) {
            receiveRect = null;
            doubleValueRect = null;
            prefs.edit()
                .putString("screenState", prefs.getBoolean("pendingResultDuringFreightReview", false) ? "RESULT_REVALIDATING" : "TRIP_REVIEW")
                .putString("lastEvent", "Leitura retomada · linha selecionada e campos já confirmados foram preservados")
                .apply();
            return;
        }

        if (STATE_TRIP_IN_PROGRESS.equals(state)) {
            clearReplacementFreightCandidate();
            clearActiveTripFreightListRuntime();
            clearExplicitFreightReplacement();
            prefs.edit()
                .putInt("freightCount", 0)
                .putBoolean("activeTripFreightListVisible", false)
                .putString("screenState", "TRIP")
                .putString("lastEvent", "Leitura retomada · viagem preservada e detector de resultado rearmado")
                .apply();
            return;
        }

        // Result states keep the durable result snapshot and Receive latch, but screen
        // coordinates from before the app switch are no longer trusted. Fresh OCR will
        // rebuild them immediately after the return.
        receiveRect = null;
        doubleValueRect = null;
        prefs.edit()
            .putString("lastEvent", "Leitura retomada · resultado preservado e controles visuais revalidados")
            .apply();
    }

    private void reconcileSessionAfterGtoReturn(long absenceMs) {
        // Kept for compatibility with older call sites/tests. R3.26 makes app switching
        // orthogonal to the trip state, so a return never resets a valid journey.
        resumeScreenAnalysisInSameState(absenceMs);
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
        boolean hasSnapshot = GtoAutoTripSync.hasRecoverableSessionSnapshot(this, sessionId, requireFreight);
        boolean keep = GtoSessionRecoveryPolicy.keepDurableSession(hasSnapshot, ageMs, ACTIVE_SESSION_STALE_MS);
        if (keep && GtoSessionRecoveryPolicy.isLongRunning(ageMs, ACTIVE_SESSION_STALE_MS)) {
            long lastLogged = prefs.getLong("longRunningSessionDiagnosticAt", 0L);
            long now = System.currentTimeMillis();
            if (now - lastLogged >= 60L * 60L * 1000L) {
                prefs.edit().putLong("longRunningSessionDiagnosticAt", now).apply();
                recordObserverEvent("LONG_RUNNING_SESSION", "Sessão durável preservada além de 12h");
            }
        }
        return keep;
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

    private boolean canPrepareNextFreightFromSealedQueue() {
        if (isOperationClosedForNewTrip()) return false;
        int total = prefs.getInt("jobTotalDeliveries", 0);
        if (total <= 0) return false;
        int completedBeforeThisTrip = Math.max(
            prefs.getInt("jobProgress", 0),
            prefs.getInt("gtoJobProgress", 0)
        );
        // Only bypass network latency when local operation metadata proves this is not
        // the final scheduled delivery. The last delivery still waits for backend ACK.
        return GtoDeterministicFlowPolicy.mayPrepareNextFreightAfterSealedQueue(
            getTripState(), true, false, completedBeforeThisTrip, total
        );
    }

    private boolean prepareNextFreightFromSealedQueue(String completedSessionId) {
        String completedSession = completedSessionId == null ? "" : completedSessionId.trim();
        if (!STATE_RESULT_CONFIRMED.equals(getTripState())
            || completedSession.isEmpty()
            || !GtoAutoTripSync.hasPendingSession(this, completedSession)
            || !canPrepareNextFreightFromSealedQueue()) return false;

        clearTripAnalysis();
        String nextSessionId = GtoAutoTripSync.newSessionId();
        long now = System.currentTimeMillis();
        boolean persisted = prefs.edit()
            .putString("gtoTripSessionId", nextSessionId)
            .putLong("gtoTripSessionStartedAt", now)
            .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_IN_PROGRESS)
            .putString("gtoTripIntegrityStatus", "CREATING_SNAPSHOT")
            .putString("gtoPreviousQueuedSessionId", completedSession)
            .putLong("gtoAutoNextTripPreparedAt", now)
            .remove("gtoRegisteredTripId")
            .remove("gtoTripSyncError")
            .remove("gtoTripIntegrityError")
            .commit();
        if (!persisted || !GtoAutoTripSync.beginSessionSnapshot(this, prefs, nextSessionId)) {
            prefs.edit()
                .remove("gtoTripSessionId")
                .remove("gtoTripSessionStartedAt")
                .putString("gtoTripSyncStatus", GtoAutoTripSync.STATUS_REJECTED)
                .putString("lastEvent", "Entrega anterior está segura na fila, mas a próxima sessão não pôde ser preparada")
                .apply();
            setTripState(STATE_IDLE, "Revise a operação NVU antes do próximo frete");
            return false;
        }
        setTripState(STATE_WAITING_FREIGHT, "Pronto para o próximo frete; envio anterior continua em segundo plano");
        prefs.edit()
            .putString("lastEvent", "Próximo frete liberado · entrega anterior selada e sincronizando em segundo plano")
            .apply();
        return true;
    }

    private void beginTrip() {
        beginTrip(true, true);
    }

    private void beginTrip(boolean announceStage) {
        beginTrip(announceStage, true);
    }

    private void beginTrip(boolean announceStage, boolean requestProjectionImmediately) {
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
                "Abra a lista e escolha um frete.",
                3000L,
                false
            );
        }

        if (!projectionActive) {
            if (requestProjectionImmediately) {
                requestProjectionPermission();
            } else {
                armProjectionPermissionAfterGtoOpen();
            }
            return;
        }

    }

    private void armProjectionPermissionAfterGtoOpen() {
        if (projectionActive || projectionPermissionInFlight || projectionSurfacePending) return;
        projectionPermissionAfterGtoOpenPending = true;
        projectionPermissionLandscapeStableSince = 0L;
        projectionPermissionLandscapeWidth = 0;
        projectionPermissionLandscapeHeight = 0;
        projectionPermissionAfterGtoOpenArmedAt = System.currentTimeMillis();
        projectionStatus = "WAITING_GTO_FOR_PERMISSION";
        prefs.edit()
            .putBoolean("projectionPermissionAfterGtoOpenPending", true)
            .putLong("projectionPermissionAfterGtoOpenArmedAt", projectionPermissionAfterGtoOpenArmedAt)
            .putString("projectionStatus", projectionStatus)
            .putString("lastEvent", "Abra o GTO · a autorização de leitura aparecerá com o simulador aberto")
            .apply();
    }

    private boolean isLandscapeStableForProjectionConsent(long now) {
        DisplayMetrics metrics = realDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        if (width <= 0 || height <= 0 || width <= height) {
            projectionPermissionLandscapeStableSince = 0L;
            projectionPermissionLandscapeWidth = 0;
            projectionPermissionLandscapeHeight = 0;
            return false;
        }
        if (projectionPermissionLandscapeWidth != width
            || projectionPermissionLandscapeHeight != height) {
            projectionPermissionLandscapeWidth = width;
            projectionPermissionLandscapeHeight = height;
            projectionPermissionLandscapeStableSince = now;
            return false;
        }
        return projectionPermissionLandscapeStableSince > 0L
            && now >= projectionPermissionLandscapeStableSince
            && now - projectionPermissionLandscapeStableSince >= PROJECTION_PERMISSION_LANDSCAPE_SETTLE_MS;
    }

    private void maybeLaunchInitialProjectionPermissionOverGto(long now) {
        if (!projectionPermissionAfterGtoOpenPending
            || projectionActive
            || projectionPermissionInFlight
            || transientForegroundSurfaceActive
            || screenAnalysisPausedOutsideGto
            || !gtoForeground
            || !GTO_PACKAGE.equals(foregroundPackage)) return;
        if (!captureIsNeededForCurrentState()) {
            projectionPermissionAfterGtoOpenPending = false;
            projectionPermissionAfterGtoOpenArmedAt = 0L;
            prefs.edit()
                .remove("projectionPermissionAfterGtoOpenPending")
                .remove("projectionPermissionAfterGtoOpenArmedAt")
                .apply();
            return;
        }
        if (projectionPermissionAfterGtoOpenArmedAt <= 0L) {
            projectionPermissionAfterGtoOpenArmedAt = now;
        }
        if (now - projectionPermissionAfterGtoOpenArmedAt < INITIAL_PROJECTION_AFTER_GTO_DELAY_MS) return;
        if (!isLandscapeStableForProjectionConsent(now)) {
            prefs.edit()
                .putString("projectionStatus", "WAITING_GTO_LANDSCAPE_FOR_PERMISSION")
                .putString("lastEvent", "GTO aberto · aguardando paisagem estável antes da autorização")
                .apply();
            return;
        }

        // Never let the recorder-consent Activity win the race against the main NVU
        // overlay. On slower/OEM WindowManager implementations addView() can settle a
        // poll later; keep the permission request pending until the bubble has a real,
        // attached window on top of GTO. Starting automatic mode again rearms this same
        // path, so the user always has a deterministic recovery action.
        if (bubbleView == null || !bubbleView.isAttachedToWindow()) {
            if (bubbleView != null) {
                bubbleView = null;
                bubbleParams = null;
            }
            lastBubbleAttemptAt = 0L;
            showBubbleIfAllowed();
            prefs.edit()
                .putBoolean("projectionPermissionAfterGtoOpenPending", true)
                .putString("lastEvent", "GTO aberto · estabilizando botão flutuante antes da leitura")
                .apply();
            return;
        }

        projectionPermissionAfterGtoOpenPending = false;
        prefs.edit()
            .putBoolean("projectionPermissionAfterGtoOpenPending", false)
            .putString("lastEvent", "Botão NVU ativo no GTO · solicitando autorização de leitura")
            .apply();
        launchProjectionPermissionOverGto();
    }

    private void launchProjectionPermissionOverGto() {
        if (projectionActive || projectionPermissionInFlight || projectionSurfacePending) return;
        launchProjectionPermissionActivityOnlyWhenGtoLandscape("REQUESTING_PERMISSION_FROM_GTO");
    }

    private void requestProjectionPermission() {
        if (projectionPermissionInFlight || projectionActive || projectionSurfacePending) return;

        // Manual/legacy requests never open Android consent from NVU. If the exact GTO
        // package or landscape geometry is not confirmed yet, arm the request and let the
        // foreground poll execute it only when those conditions become true.
        refreshForegroundPackage();
        long now = System.currentTimeMillis();
        DisplayMetrics metrics = realDisplayMetrics();
        boolean exactGto = gtoForeground && GTO_PACKAGE.equals(foregroundPackage);
        boolean landscape = metrics.widthPixels > metrics.heightPixels && metrics.heightPixels > 0;
        boolean bubbleAttached = bubbleView != null && bubbleView.isAttachedToWindow();
        if (!exactGto || !landscape || !bubbleAttached || !isLandscapeStableForProjectionConsent(now)) {
            projectionPermissionAfterGtoOpenPending = true;
            projectionPermissionAfterGtoOpenArmedAt = now;
            projectionStatus = "WAITING_GTO_FOR_PERMISSION";
            prefs.edit()
                .putBoolean("projectionPermissionAfterGtoOpenPending", true)
                .putLong("projectionPermissionAfterGtoOpenArmedAt", now)
                .putString("projectionStatus", projectionStatus)
                .putString("lastEvent", "Autorização armada · aguardando GTO real e horizontal")
                .apply();
            if (gtoForeground) {
                showStatusChip("A autorização será exibida somente com o GTO confirmado e a tela horizontal.", 3200L);
            }
            return;
        }
        launchProjectionPermissionActivityOnlyWhenGtoLandscape("REQUESTING_PERMISSION_FROM_GTO");
    }

    private void launchProjectionPermissionActivityOnlyWhenGtoLandscape(String requestedStatus) {
        if (projectionActive || projectionPermissionInFlight || projectionSurfacePending) return;
        refreshForegroundPackage();
        long now = System.currentTimeMillis();
        DisplayMetrics metrics = realDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        // Hard gate: never bring any NVU Activity forward unless the exact simulator
        // package is the foreground owner and the physical display is already landscape.
        // There is intentionally no visual-evidence or NVU-return bridge fallback here.
        if (!gtoForeground
            || !GTO_PACKAGE.equals(foregroundPackage)
            || width <= 0
            || height <= 0
            || width <= height
            || !isLandscapeStableForProjectionConsent(now)
            || bubbleView == null
            || !bubbleView.isAttachedToWindow()) {
            projectionPermissionAfterGtoOpenPending = true;
            projectionPermissionAfterGtoOpenArmedAt = now;
            projectionStatus = "WAITING_GTO_LANDSCAPE_FOR_PERMISSION";
            prefs.edit()
                .putBoolean("projectionPermissionAfterGtoOpenPending", true)
                .putLong("projectionPermissionAfterGtoOpenArmedAt", now)
                .putString("projectionStatus", projectionStatus)
                .putString("lastEvent", "Autorização bloqueada fora do GTO horizontal")
                .apply();
            return;
        }

        projectionPermissionAfterGtoOpenPending = false;
        projectionPermissionInFlight = true;
        suppressForegroundHideUntil = now + 12_000L;
        projectionStatus = requestedStatus == null || requestedStatus.trim().isEmpty()
            ? "REQUESTING_PERMISSION_FROM_GTO"
            : requestedStatus.trim();
        prefs.edit()
            .putBoolean("projectionPermissionAfterGtoOpenPending", false)
            .putBoolean("projectionPermissionInFlight", true)
            .putString("projectionStatus", projectionStatus)
            .putString("screenState", "CAPTURE_PERMISSION_REQUIRED")
            .putLong("projectionConsentGtoVerifiedAt", now)
            .putInt("projectionConsentGtoWidth", width)
            .putInt("projectionConsentGtoHeight", height)
            .putString("lastEvent", "GTO horizontal confirmado · abrindo autorização sem sair do simulador")
            .remove("projectionError")
            .apply();

        // Keep the main bubble token; close only interactive child overlays. The consent
        // host itself is transparent and non-exported, so the already-running GTO remains
        // the visible task underneath until Android displays its own permission surface.
        suspendInteractiveOverlaysKeepBubble();

        try {
            Intent permissionIntent = new Intent(this, GtoProjectionPermissionActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(GtoProjectionPermissionActivity.EXTRA_GTO_VERIFIED_AT, now)
                .putExtra(GtoProjectionPermissionActivity.EXTRA_GTO_VERIFIED_WIDTH, width)
                .putExtra(GtoProjectionPermissionActivity.EXTRA_GTO_VERIFIED_HEIGHT, height);
            startActivity(permissionIntent);
        } catch (Exception ex) {
            projectionPermissionInFlight = false;
            suppressForegroundHideUntil = System.currentTimeMillis() + PERMISSION_RETURN_GRACE_MS;
            projectionStatus = "PERMISSION_ACTIVITY_FAILED";
            String detail = describeError(ex);
            prefs.edit()
                .putBoolean("projectionPermissionInFlight", false)
                .putString("projectionStatus", projectionStatus)
                .putString("projectionError", detail)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthAutoAllowed", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putString("lastEvent", "Falha ao abrir autorização transparente sobre o GTO: " + detail)
                .apply();
            scheduleBubbleRestoreAfterPermission();
            showStatusChip("Não foi possível abrir a autorização dentro do GTO.", 3200L);
        }
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
        boolean visualGtoFresh = lastVisualGtoForegroundEvidenceAt > 0L
            && now - lastVisualGtoForegroundEvidenceAt <= VISUAL_GTO_EVIDENCE_FRESH_MS;
        boolean confirmedGto = GTO_PACKAGE.equals(foregroundPackage) || visualGtoFresh;
        if (!confirmedGto || transientForegroundSurfaceActive || screenAnalysisPausedOutsideGto) return;
        gtoForeground = true;
        prefs.edit().putBoolean("gtoForeground", true).apply();
        showBubbleIfAllowed();
    }

    private void cancelTrip() {
        recordObserverEvent("TRIP_CANCELLED_BY_DRIVER", prefs.getString("selectedFreightSummary", ""));
        GtoAutoTripSync.discardSessionSnapshot(this, prefs.getString("gtoTripSessionId", ""));
        clearTripAnalysis();
        setTripState(STATE_CANCELLED, "Viagem cancelada pelo motorista");
        showToast("Viagem cancelada.");
    }

    private void clearTripAnalysis() {
        activeReviewInputDraft = "";
        activeReviewInputField = "";
        activeReviewInput = null;
        deleteResultSnapshot();
        resultSnapshotRecoveryGeneration++;
        resultSnapshotRecoveryBusy.set(false);
        resultEvidenceSequence = 0L;
        resultTouchFallbackRequired = false;
        resultTouchFallbackReady = false;
        resultTouchFallbackContinuityBroken = false;
        clearReplacementFreightCandidate();
        clearExplicitFreightReplacement();
        clearActiveTripFreightListRuntime();
        deferredPreciseFreightCommit = null;
        deferredSelectionFailureRow = -1;
        deferredSelectionFailureReason = "";
        deferredNormalResultConfirmation = false;
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
        freightSemanticCertifiedGeneration = -1L;
        freightSemanticCertifiedAt = 0L;
        freightSemanticAnchorRows = 0;
        freightEvidenceRetryCount = 0;
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
            .remove("selectedFreightRow")
            .remove("selectedOrigin")
            .remove("selectedOriginSource")
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
            .remove("freightSemanticCertifiedGeneration")
            .remove("freightSemanticConfirmedAt")
            .remove("freightSemanticAnchorRows")
            .remove("freightPanelLeftScreen")
            .remove("freightPanelScreenAt")
            .remove("selectionConfirmationStatus")
            .remove("selectionIdentityStatus")
            .remove("selectionIdentitySource")
            .remove("selectionIdentityAt")
            .remove("selectionFailureReason")
            .remove("selectionFailureAt")
            .remove("pendingFreightReview")
            .remove("reviewRequiredField")
            .remove("reviewReason")
            .remove("reviewCargo")
            .remove("reviewOriginCompany")
            .remove("reviewDestinationCompany")
            .remove("reviewDestination")
            .remove("reviewKm")
            .remove("reviewValue")
            .remove("reviewRawText")
            .remove("reviewCargoSource")
            .remove("reviewOriginCompanySource")
            .remove("reviewDestinationCompanySource")
            .remove("reviewDestinationSource")
            .remove("reviewKmSource")
            .remove("reviewValueSource")
            .remove("fieldStatusCargo")
            .remove("fieldStatusOrigin")
            .remove("fieldStatusDestination")
            .remove("fieldStatusDistance")
            .remove("fieldStatusValue")
            .remove("fieldStatusDestinationCompany")
            .remove("freightReplacementStatus")
            .putInt("freightCount", 0)
            .putInt("outsideTouchCount", 0)
            .remove("resultValue")
            .remove("resultValueEvidence")
            .remove("resultValueConsensusStable")
            .remove("resultValueConsensusVersion")
            .remove("resultValueEvidenceConflict")
            .remove("resultValueEvidenceCount")
            .remove("resultValueConflictNoticeShown")
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
            .remove("gtoWorkLaunchPrepared")
            .remove("gtoWorkLaunchPreparedState")
            .remove("gtoWorkLaunchPreparedAt")
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

    private int safeTopInsetPx() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            try {
                WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
                Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()
                );
                return Math.max(0, insets.top);
            } catch (Exception ignored) {}
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? Math.max(0, getResources().getDimensionPixelSize(id)) : 0;
    }

    private int safeBottomInsetPx() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            try {
                WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
                Insets insets = metrics.getWindowInsets().getInsets(
                    WindowInsets.Type.navigationBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime()
                );
                return Math.max(0, insets.bottom);
            } catch (Exception ignored) {}
        }
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? Math.max(0, getResources().getDimensionPixelSize(id)) : 0;
    }

    private void updateMenuWindowInteractionMode() {
        if (menuParams == null) return;
        boolean review = isFreightReviewPending();
        int desiredFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if (!review) desiredFlags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        menuParams.flags = desiredFlags;
        menuParams.softInputMode = review
            ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
    }

    private void adjustOpenMenuLayoutAfterMeasure() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::adjustOpenMenuLayoutAfterMeasure);
            return;
        }
        if (windowManager == null || menuView == null || menuParams == null
            || bubbleView == null || bubbleParams == null
            || !menuView.isAttachedToWindow() || !bubbleView.isAttachedToWindow()) return;

        int oldFlags = menuParams.flags;
        int oldSoftInputMode = menuParams.softInputMode;
        updateMenuWindowInteractionMode();
        boolean interactionChanged = oldFlags != menuParams.flags || oldSoftInputMode != menuParams.softInputMode;

        DisplayMetrics screen = realDisplayMetrics();
        if (screen.widthPixels <= 0 || screen.heightPixels <= 0) return;
        int margin = dp(8);
        int gap = dp(8);
        int safeTop = safeTopInsetPx() + margin;
        int safeBottomInset = safeBottomInsetPx() + margin;
        int safeBottom = Math.max(safeTop, screen.heightPixels - safeBottomInset);
        int safeLeft = margin;
        int detectedRight = Math.min(screen.widthPixels, freightOverlaySafeRight(screen)) - margin;
        int safeRight = Math.max(safeLeft, detectedRight);

        int usableHeight = Math.max(dp(160), safeBottom - safeTop);
        int maxCardHeight = Math.max(dp(160), Math.min(
            Math.round(usableHeight * 0.80f),
            usableHeight
        ));
        int contentHeight = menuContentView != null && menuContentView.getMeasuredHeight() > 0
            ? menuContentView.getMeasuredHeight() + menuView.getPaddingTop() + menuView.getPaddingBottom()
            : menuView.getMeasuredHeight();
        int effectiveHeight = Math.max(dp(120), Math.min(Math.max(dp(120), contentHeight), maxCardHeight));
        int desiredHeight = contentHeight > maxCardHeight
            ? maxCardHeight
            : WindowManager.LayoutParams.WRAP_CONTENT;

        int menuWidth = dp(256);
        int bubbleWidth = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : dp(69);
        int bubbleHeight = bubbleView.getHeight() > 0 ? bubbleView.getHeight() : dp(56);

        // Never squeeze the card over an actionable freight list. If the real list leaves
        // no room for the full readable card, minimizing the card is safer and clearer than
        // covering an Accept button. In every other state the full screen remains available.
        if (safeRight - safeLeft < menuWidth) {
            String state = getTripState();
            boolean actionableFreightList = STATE_WAITING_FREIGHT.equals(state)
                || (STATE_CONFIRMING_FREIGHT.equals(state) && !isFreightReviewPending());
            if (actionableFreightList) {
                closeMenu();
                prefs.edit()
                    .putLong("menuMinimizedForFreightListAt", System.currentTimeMillis())
                    .putString("lastEvent", "Card NVU minimizado para liberar a lista de fretes")
                    .apply();
                return;
            }
            safeRight = screen.widthPixels - margin;
        }
        safeRight = Math.min(screen.widthPixels - margin, safeRight);

        int side = GtoOverlayLayoutPolicy.chooseMenuSideForBubble(
            bubbleParams.x, bubbleWidth, menuWidth, safeLeft, safeRight, gap
        );
        boolean pairFits = GtoOverlayLayoutPolicy.horizontalPairFits(
            safeLeft, safeRight, bubbleWidth, menuWidth, gap
        );

        int targetBubbleX = bubbleParams.x;
        if (pairFits) {
            targetBubbleX = GtoOverlayLayoutPolicy.bubbleXForMenuSide(
                side, bubbleParams.x, bubbleWidth, menuWidth, safeLeft, safeRight, gap
            );
        } else {
            targetBubbleX = clamp(
                bubbleParams.x,
                safeLeft,
                Math.max(safeLeft, safeRight - bubbleWidth)
            );
        }
        int targetMenuX = GtoOverlayLayoutPolicy.menuXBesideBubble(
            side, targetBubbleX, bubbleWidth, menuWidth, safeLeft, safeRight, gap
        );

        int anchorBubbleY = bubbleYBeforeMenuOpen != Integer.MIN_VALUE
            ? bubbleYBeforeMenuOpen
            : bubbleParams.y;
        int targetMenuY = GtoOverlayLayoutPolicy.centeredMenuYBesideBubble(
            anchorBubbleY, bubbleHeight, effectiveHeight, safeTop, safeBottom
        );
        int targetBubbleY = clamp(
            bubbleParams.y,
            safeTop,
            Math.max(safeTop, safeBottom - bubbleHeight)
        );

        // Very narrow freight-safe corridors may not fit a 256dp card + bubble side by
        // side. In that exceptional case keep the card full-width and dock the bubble just
        // above/below it, choosing the closest valid position instead of overlapping it.
        if (!pairFits && GtoOverlayLayoutPolicy.overlaps(
            targetBubbleX, targetBubbleY, bubbleWidth, bubbleHeight,
            targetMenuX, targetMenuY, menuWidth, effectiveHeight
        )) {
            int aboveY = targetMenuY - gap - bubbleHeight;
            int belowY = targetMenuY + effectiveHeight + gap;
            boolean aboveFits = aboveY >= safeTop;
            boolean belowFits = belowY + bubbleHeight <= safeBottom;
            if (aboveFits && belowFits) {
                targetBubbleY = Math.abs(aboveY - bubbleParams.y) <= Math.abs(belowY - bubbleParams.y)
                    ? aboveY : belowY;
            } else if (belowFits) {
                targetBubbleY = belowY;
            } else if (aboveFits) {
                targetBubbleY = aboveY;
            } else {
                targetBubbleY = Math.abs(safeTop - bubbleParams.y)
                    <= Math.abs((safeBottom - bubbleHeight) - bubbleParams.y)
                    ? safeTop : Math.max(safeTop, safeBottom - bubbleHeight);
            }
        }

        boolean bubbleChanged = bubbleParams.x != targetBubbleX || bubbleParams.y != targetBubbleY;
        if (bubbleChanged) {
            bubbleAutoDockedForMenu = true;
            bubbleParams.x = targetBubbleX;
            bubbleParams.y = targetBubbleY;
            try {
                // Automatic docking is intentionally NOT persisted. Only an explicit user
                // drag updates bubbleX/bubbleY preferences.
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            } catch (Exception ex) {
                recordOverlayFailure(ex);
            }
        }

        boolean menuChanged = interactionChanged
            || menuParams.height != desiredHeight
            || menuParams.y != targetMenuY
            || menuParams.x != targetMenuX
            || menuParams.width != menuWidth;
        menuParams.width = menuWidth;
        menuParams.height = desiredHeight;
        menuParams.x = targetMenuX;
        menuParams.y = targetMenuY;
        if (menuChanged) {
            try {
                windowManager.updateViewLayout(menuView, menuParams);
            } catch (Exception ex) {
                prefs.edit().putString("menuOverlayError", describeError(ex)).apply();
            }
        }

        prefs.edit()
            .putInt("menuMeasuredHeight", effectiveHeight)
            .putInt("menuSafeY", targetMenuY)
            .putString("menuDockSide", side == GtoOverlayLayoutPolicy.SIDE_RIGHT ? "RIGHT" : "LEFT")
            .putBoolean("menuHorizontalPairFits", pairFits)
            .putLong("menuLayoutAdjustedAt", System.currentTimeMillis())
            .apply();
    }

    private boolean shouldMinimizeMenuForConfirmedExternalApp(long now) {
        if (menuView == null || transientForegroundSurfaceActive || nonGtoForegroundSince <= 0L) return false;
        if (now - nonGtoForegroundSince < EXTERNAL_APP_MENU_MINIMIZE_MS) return false;
        String pkg = foregroundPackage == null ? "" : foregroundPackage;
        if (pkg.isEmpty() || GTO_PACKAGE.equals(pkg) || getPackageName().equals(pkg)) return false;
        // Require an actual GTO background event at least as recent as its last foreground
        // event. This prevents UsageStats jitter from resurrecting the old auto-close bug.
        return lastGtoBackgroundEventAt >= lastGtoForegroundEventAt;
    }

    private void closeMenu() {
        closeMenu(true);
    }

    private void closeMenu(boolean restoreAutoDockedBubble) {
        if (menuView != null && windowManager != null) {
            try {
                windowManager.removeView(menuView);
            } catch (Exception ignored) {}
        }
        menuView = null;
        menuScrollView = null;
        menuContentView = null;
        activeReviewInput = null;
        menuParams = null;
        lastMenuRenderSignature = "";
        operationSummaryExpanded = false;

        if (restoreAutoDockedBubble
            && bubbleAutoDockedForMenu
            && bubbleView != null
            && bubbleParams != null
            && bubbleView.isAttachedToWindow()
            && bubbleXBeforeMenuOpen != Integer.MIN_VALUE
            && bubbleYBeforeMenuOpen != Integer.MIN_VALUE) {
            DisplayMetrics screen = realDisplayMetrics();
            int bubbleWidth = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : dp(69);
            int bubbleHeight = bubbleView.getHeight() > 0 ? bubbleView.getHeight() : dp(56);
            int safeLeft = dp(8);
            int safeRight = Math.max(
                safeLeft + bubbleWidth,
                Math.min(screen.widthPixels, freightOverlaySafeRight(screen)) - dp(8)
            );
            int safeTop = safeTopInsetPx() + dp(8);
            int safeBottom = Math.max(safeTop + bubbleHeight, screen.heightPixels - safeBottomInsetPx() - dp(8));
            bubbleParams.x = clamp(
                bubbleXBeforeMenuOpen,
                safeLeft,
                Math.max(safeLeft, safeRight - bubbleWidth)
            );
            bubbleParams.y = clamp(
                bubbleYBeforeMenuOpen,
                safeTop,
                Math.max(safeTop, safeBottom - bubbleHeight)
            );
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            } catch (Exception ex) {
                recordOverlayFailure(ex);
            }
        }

        bubbleAutoDockedForMenu = false;
        bubbleXBeforeMenuOpen = Integer.MIN_VALUE;
        bubbleYBeforeMenuOpen = Integer.MIN_VALUE;
    }

    private void suspendPassiveDetectionOverlaysKeepBubbleAndMenu() {
        // HF15: foreground/UsageStats oscillation must never undo an explicit user open
        // or erase a driver-stage banner that has just been shown. Generic transient
        // status chips may be cleared, but stage messages remain attached at top-centre.
        if (!statusChipIsDriverStage) hideStatusChip();
        hideFreightTouchPulseSensor();
        if (bubbleView != null && !bubbleView.isAttachedToWindow()) {
            bubbleView = null;
            captureHealthDotView = null;
            lastCaptureHealthIndicatorState = null;
            bubbleParams = null;
            lastBubbleAttemptAt = 0L;
        }
        if (prefs != null) {
            prefs.edit().putBoolean("overlayVisible", bubbleView != null).apply();
        }
    }

    private void suspendInteractiveOverlaysKeepBubble() {
        closeMenu();
        hideStatusChip();
        hideFreightTouchPulseSensor();
        if (bubbleView != null && !bubbleView.isAttachedToWindow()) {
            bubbleView = null;
            captureHealthDotView = null;
            lastCaptureHealthIndicatorState = null;
            bubbleParams = null;
            lastBubbleAttemptAt = 0L;
        }
        if (prefs != null) {
            prefs.edit().putBoolean("overlayVisible", bubbleView != null).apply();
        }
    }

    private void hideOverlays() {
        hideBubbleRemoveTarget();
        closeMenu();
        hideStatusChip();
        hideFreightTouchPulseSensor();
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
        }
        bubbleView = null;
        captureHealthDotView = null;
        lastCaptureHealthIndicatorState = null;
        bubbleParams = null;
        if (prefs != null) prefs.edit().putBoolean("overlayVisible", false).apply();
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void prepareCaptureForGtoLaunch() {
        long now = System.currentTimeMillis();
        if (projectionSurfacePending && mediaProjection != null) {
            // Consent was already granted. Do not arm a second permission request just
            // because projectionActive is still false: HF5 intentionally waits to create
            // the VirtualDisplay until the returned GTO landscape is stable.
            suppressForegroundHideUntil = Math.max(
                suppressForegroundHideUntil,
                now + PERMISSION_RETURN_GRACE_MS
            );
            prefs.edit()
                .putLong("gtoLaunchRequestedAt", now)
                .putLong("gtoLaunchVisualBridgeUntil", suppressForegroundHideUntil)
                .putString("captureReadiness", "WAITING_GTO_GEOMETRY")
                .putBoolean("captureReadyForAnalysis", false)
                .putString("lastEvent", "Autorização aceita · retornando ao GTO para criar a captura em paisagem")
                .apply();
            resetPendingProjectionSurfaceStability();
            return;
        }
        if (!projectionActive || captureHandler == null) {
            armProjectionPermissionAfterGtoOpen();
            prefs.edit()
                .putLong("gtoLaunchRequestedAt", now)
                .putString("captureReadiness", "WAITING_GTO_FOR_PERMISSION")
                .putBoolean("captureReadyForAnalysis", false)
                .putString("lastEvent", "GTO aberto · aguardando primeiro plano para autorizar leitura")
                .apply();
            return;
        }
        // R3.27: opening GTO is an explicit action initiated by NVU. Keep a bounded
        // NVU->GTO bridge while UsageStats catches up so the strict freight-list pixels
        // can qualify the first GTO frames. This never overrides a known third-party app:
        // refreshForegroundPackage()/canUseFreightListAsVisualGtoProof() only accept the
        // bridge while Android still reports NVU (or no package) and the real freight
        // geometry is present. Without this window an OEM that keeps NVU as the latest
        // UsageEvent can pause ImageReader before the detector ever sees the list.
        suppressForegroundHideUntil = Math.max(
            suppressForegroundHideUntil,
            now + PERMISSION_RETURN_GRACE_MS
        );
        prefs.edit()
            .putLong("gtoLaunchRequestedAt", now)
            .putLong("gtoLaunchVisualBridgeUntil", suppressForegroundHideUntil)
            .apply();
        resetCaptureStabilityBarrier(
            GtoCaptureStabilityGate.CAPTURE_WAITING_GTO_FOREGROUND,
            captureWidth,
            captureHeight,
            "GTO será aberto · aguardando primeiro plano e geometria final"
        );
        postCaptureGeometryInvalidation("GTO_LAUNCH");
    }

    private void markCaptureWaitingForGtoForeground(String event) {
        if (!projectionActive) return;
        resetCaptureStabilityBarrier(
            GtoCaptureStabilityGate.CAPTURE_WAITING_GTO_FOREGROUND,
            captureWidth,
            captureHeight,
            event
        );
        postCaptureGeometryInvalidation("GTO_BACKGROUND");
    }

    private void resetCaptureStabilityBarrier(
        String phase,
        int expectedWidth,
        int expectedHeight,
        String event
    ) {
        GtoCaptureStabilityGate.Snapshot snapshot = captureStabilityGate.reset(
            phase,
            expectedWidth,
            expectedHeight,
            System.currentTimeMillis()
        );
        lastCaptureGeometryPollAt = 0L;
        lastCaptureGeometryMatched = false;
        persistCaptureStability(snapshot, event);
        mainHandler.post(this::updateFreightTouchPulseSensor);
    }

    private void persistCaptureStability(
        GtoCaptureStabilityGate.Snapshot snapshot,
        @Nullable String event
    ) {
        if (prefs == null || snapshot == null) return;
        SharedPreferences.Editor editor = prefs.edit()
            .putString("captureReadiness", snapshot.phase)
            .putBoolean("captureReadyForAnalysis", snapshot.ready)
            .putInt("captureStableFrames", snapshot.stableFrames)
            .putInt("captureExpectedWidth", snapshot.expectedWidth)
            .putInt("captureExpectedHeight", snapshot.expectedHeight)
            .putLong("captureStabilityGeneration", snapshot.generation)
            .putLong("captureStabilityStartedAt", snapshot.startedAt)
            .putBoolean(
                "captureSurfaceReady",
                projectionActive && imageReader != null && virtualDisplay != null
            );
        if (event != null && !event.trim().isEmpty()) editor.putString("lastEvent", event);
        editor.apply();
    }

    private void postCaptureGeometryInvalidation(String reason) {
        Handler handler = captureHandler;
        if (handler == null) return;
        long barrierGeneration = captureStabilityGate.current().generation;
        Runnable invalidate = () -> {
            if (captureStabilityGate.current().generation != barrierGeneration) return;
            invalidateCaptureBoundAnalysis(reason);
        };
        if (Looper.myLooper() == handler.getLooper()) invalidate.run();
        else handler.post(invalidate);
    }

    /**
     * Clears only information whose meaning depends on the old screen geometry. Durable
     * operation/session snapshots, selectedFreight, freightFingerprint, result latch and
     * the automatic-sync queue are intentionally outside this method.
     */
    private void invalidateCaptureBoundAnalysis(String reason) {
        analysisOcrGeneration++;
        lastOcrAt = 0L;
        lastActiveTripVisualProbeAt = 0L;
        activeTripFreightListSeenSince = 0L;
        activeTripFreightListFrames = 0;
        clearReplacementFreightCandidate();

        String state = getTripState();
        boolean liveSelectionGeometry = STATE_WAITING_FREIGHT.equals(state)
            || STATE_IDLE.equals(state)
            || STATE_CANCELLED.equals(state);

        fastPreviousFreightFrame = null;
        fastPreviousFreightSequence = 0L;
        fastLastSnapshotFrame = null;
        lastFastPanelSnapshotAt = 0L;
        fastMissingListFrames = 0;
        synchronized (freightFrameLock) {
            fastFrameHistory.clear();
        }

        if (liveSelectionGeometry) {
            preciseSelectionOcrGeneration++;
            preciseSelectedRow = -1;
            preciseSelectedTouchAt = 0L;
            clearFastPendingSelection();
            clearSelectionProbe();
            selectionCoordinator.reset();
            pendingFreightSelection = null;
            pendingFreightTouchAt = 0L;
            pendingSelectionSource = "";
            visualSelectionUntil = 0L;
            visualFreightSelection = null;
            visualSelectionConfidence = 0f;
            visualSelectionSource = "";
            lastFreightListSeenAt = 0L;
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
            freightHistory.clear();
            freightHistoryPage = -1;
            freightHistoryUpdatedAt = 0L;
            freightPageGeneration++;
            lastFreightPageOcrAt = 0L;
            synchronized (freightOptions) {
                freightOptions.clear();
            }
            synchronized (freightFrameLock) {
                realtimeAcceptRects.clear();
                if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                    latestFreightPanelFrame.recycle();
                }
                latestFreightPanelFrame = null;
                latestFreightPanelAt = 0L;
            }
            lastScreenState = "UNKNOWN";
            prefs.edit()
                .remove("pendingFreight")
                .remove("pendingSelectionSource")
                .remove("freightOptions")
                .remove("freightTextGeneration")
                .remove("freightTextAt")
                .putInt("freightCount", 0)
                .putString("captureGeometryInvalidationReason", reason == null ? "GEOMETRY_CHANGE" : reason)
                .putLong("captureGeometryInvalidatedAt", System.currentTimeMillis())
                .apply();
        }
    }

    private boolean canUseFreightListAsVisualGtoProof(long now, GtoFastVisualDetector.Frame frame) {
        if (!projectionActive || frame == null || !frame.hasFreightList()) return false;
        boolean waitingForFreight = STATE_WAITING_FREIGHT.equals(getTripState());
        boolean returnGrace = now < suppressForegroundHideUntil;
        boolean packageMatchesGto = GTO_PACKAGE.equals(foregroundPackage);
        boolean packageUnknown = foregroundPackage == null || foregroundPackage.isEmpty();
        boolean permissionReturnFromNvu = returnGrace && getPackageName().equals(foregroundPackage);
        if (transientForegroundSurfaceActive) return false;
        return GtoVisualForegroundPolicy.allowFreightListProof(
            waitingForFreight,
            projectionActive,
            packageMatchesGto,
            packageUnknown,
            permissionReturnFromNvu,
            frame.buttons.size()
        );
    }

    private void recordVisualGtoForegroundEvidence(long now, int freightCount, String source) {
        lastVisualGtoForegroundEvidenceAt = now;
        lastGtoForegroundEvidenceAt = Math.max(lastGtoForegroundEvidenceAt, now);
        foregroundPackage = GTO_PACKAGE;
        if (!gtoForeground) gtoForeground = true;
        if (screenAnalysisPausedOutsideGto) {
            resumeScreenAnalysisInSameState(
                nonGtoForegroundSince > 0L ? Math.max(0L, now - nonGtoForegroundSince) : 0L
            );
            nonGtoForegroundSince = 0L;
        }
        prefs.edit()
            .putBoolean("gtoForeground", true)
            .putString("foregroundPackage", GTO_PACKAGE)
            .putLong("lastVisualGtoForegroundEvidenceAt", now)
            .putInt("lastVisualGtoFreightCount", Math.max(0, freightCount))
            .putString("lastVisualGtoEvidenceSource", source == null ? "freight-list" : source)
            .apply();
    }

    private boolean hasFreshGtoForegroundEvidence(long now) {
        long gateStartedAt = captureStabilityGate.startedAt();
        long freshnessWindow = Math.max(2100L, FOREGROUND_POLL_INTERVAL_MS * 6L);
        boolean visualFresh = lastVisualGtoForegroundEvidenceAt >= gateStartedAt
            && now >= lastVisualGtoForegroundEvidenceAt
            && now - lastVisualGtoForegroundEvidenceAt <= VISUAL_GTO_EVIDENCE_FRESH_MS;
        long freshestEvidence = Math.max(lastGtoForegroundEvidenceAt, lastVisualGtoForegroundEvidenceAt);
        return projectionActive
            && gtoForeground
            && !screenAnalysisPausedOutsideGto
            && !transientForegroundSurfaceActive
            && (GTO_PACKAGE.equals(foregroundPackage) || visualFresh)
            && freshestEvidence >= gateStartedAt
            && now >= freshestEvidence
            && (visualFresh || now - freshestEvidence <= freshnessWindow);
    }

    private boolean captureGeometryMatchesCurrentDisplay(long now) {
        if (now - lastCaptureGeometryPollAt < CAPTURE_GEOMETRY_POLL_INTERVAL_MS) {
            return lastCaptureGeometryMatched;
        }
        lastCaptureGeometryPollAt = now;
        DisplayMetrics metrics = realDisplayMetrics();
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            lastCaptureGeometryMatched = true;
        } else {
            // MediaProjection content can legitimately exclude system bars or use a
            // scaled buffer. Exact pixel equality with DisplayMetrics would therefore
            // create a resize loop on some OEMs. Orientation plus a bounded aspect-ratio
            // envelope is sufficient here; exact width/height stability is already
            // enforced independently across ImageReader frames by the gate.
            boolean captureLandscape = captureWidth >= captureHeight;
            boolean displayLandscape = metrics.widthPixels >= metrics.heightPixels;
            float captureRatio = Math.max(captureWidth, captureHeight)
                / (float) Math.max(1, Math.min(captureWidth, captureHeight));
            float displayRatio = Math.max(metrics.widthPixels, metrics.heightPixels)
                / (float) Math.max(1, Math.min(metrics.widthPixels, metrics.heightPixels));
            float ratioDrift = Math.abs(captureRatio - displayRatio) / Math.max(0.01f, displayRatio);
            lastCaptureGeometryMatched = captureLandscape == displayLandscape
                && ratioDrift <= 0.12f;
        }
        return lastCaptureGeometryMatched;
    }

    private boolean isCaptureReadyForAnalysis(long now) {
        return captureStabilityGate.isReady()
            && hasFreshGtoForegroundEvidence(now)
            && captureGeometryMatchesCurrentDisplay(now);
    }

    private void consumeCaptureStabilityFrame(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || reader != imageReader || !projectionActive) return;

            long now = System.currentTimeMillis();
            boolean freshGto = hasFreshGtoForegroundEvidence(now);
            if (!freshGto && STATE_WAITING_FREIGHT.equals(getTripState())) {
                // Critical OEM fallback: direct pixels are authoritative when UsageStats
                // fails to emit the resumed GTO event after MediaProjection permission.
                // The strict freight-list geometry prevents an arbitrary frame from
                // qualifying the capture gate.
                GtoFastVisualDetector.Frame visualProof = fastVisualDetector.analyze(
                    image, image.getWidth(), image.getHeight(), now
                );
                if (canUseFreightListAsVisualGtoProof(now, visualProof)) {
                    recordVisualGtoForegroundEvidence(
                        now, visualProof.buttons.size(), "capture-gate-freight-list"
                    );
                    freshGto = true;
                }
            }
            if (freshGto) {
                DisplayMetrics metrics = realDisplayMetrics();
                int displayWidth = metrics.widthPixels;
                int displayHeight = metrics.heightPixels;
                if (displayWidth > 0 && displayHeight > 0
                    && !captureGeometryMatchesCurrentDisplay(now)) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        pendingCapturedWidth = displayWidth;
                        pendingCapturedHeight = displayHeight;
                        resizeProjectionSurface(displayWidth, displayHeight);
                    } else {
                        GtoCaptureStabilityGate.Snapshot orientationWait = captureStabilityGate.current();
                        if (!GtoCaptureStabilityGate.CAPTURE_WAITING_ORIENTATION.equals(orientationWait.phase)) {
                            // Give Android 14+ a short chance to provide its authoritative
                            // content-size callback first.
                            resetCaptureStabilityBarrier(
                                GtoCaptureStabilityGate.CAPTURE_WAITING_ORIENTATION,
                                captureWidth,
                                captureHeight,
                                "Aguardando o Android confirmar a orientação final do GTO"
                            );
                            invalidateCaptureBoundAnalysis("DISPLAY_ORIENTATION_MISMATCH");
                        } else if (now - orientationWait.startedAt >= CAPTURE_RESIZE_CALLBACK_GRACE_MS) {
                            // Some OEMs never send onCapturedContentResize after switching
                            // from NVU portrait to GTO landscape. The real display metrics
                            // are then the safest bounded fallback; otherwise the gate stays
                            // at 0/3 forever and no freight frame reaches the detector.
                            pendingCapturedWidth = displayWidth;
                            pendingCapturedHeight = displayHeight;
                            resizeProjectionSurface(displayWidth, displayHeight);
                        }
                    }
                    return;
                }
            }

            GtoCaptureStabilityGate.Snapshot before = captureStabilityGate.current();
            GtoCaptureStabilityGate.Snapshot observed = captureStabilityGate.observeFrame(
                image.getWidth(),
                image.getHeight(),
                now,
                freshGto
            );

            if (observed.becameUnready) {
                invalidateCaptureBoundAnalysis("FOREGROUND_OR_GEOMETRY_LOST");
            }
            if (observed.becameReady) {
                // Reset once more *after* the third stable frame. The next frame becomes
                // the first legal detector baseline in the final GTO coordinate space.
                invalidateCaptureBoundAnalysis("GTO_GEOMETRY_STABLE");
                persistCaptureStability(
                    observed,
                    "GTO em primeiro plano · captura estável em "
                        + observed.expectedWidth + "x" + observed.expectedHeight
                );
                mainHandler.post(this::updateFreightTouchPulseSensor);
            } else if (!before.phase.equals(observed.phase)
                || before.stableFrames != observed.stableFrames
                || before.ready != observed.ready) {
                persistCaptureStability(observed, null);
            }
        } catch (IllegalStateException ignored) {
            // ImageReader can be replaced between the callback and acquireLatestImage().
            // The replacement reader will immediately continue the stability sequence.
        } catch (Exception ex) {
            prefs.edit()
                .putString("projectionError", "Stability gate: " + describeError(ex))
                .putLong("projectionErrorAt", System.currentTimeMillis())
                .apply();
        } finally {
            if (image != null) image.close();
        }
    }

    private void resetPendingProjectionSurfaceStability() {
        projectionSurfaceStableSince = 0L;
        projectionSurfaceStablePolls = 0;
        projectionSurfaceStableWidth = 0;
        projectionSurfaceStableHeight = 0;
    }

    private void maybeStartPendingProjectionSurface(long now) {
        if (!projectionSurfacePending
            || projectionActive
            || projectionPermissionInFlight
            || mediaProjection == null
            || !captureIsNeededForCurrentState()) return;

        DisplayMetrics metrics = realDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        // The first/only VirtualDisplay may consume a one-use MediaProjection session.
        // Never spend it while MainActivity is still foreground, even during the bounded
        // return bridge. Wait for positive UsageStats evidence that the real GTO package
        // is foreground. This removes the last race where a landscape NVU host could be
        // captured instead of the simulator and then force another consent.
        boolean packageMatchesGto = GTO_PACKAGE.equals(foregroundPackage);

        if (!packageMatchesGto || width <= 0 || height <= 0 || width <= height) {
            resetPendingProjectionSurfaceStability();
            if (width > 0 && height > 0 && width <= height) {
                projectionStatus = "WAITING_GTO_LANDSCAPE";
                prefs.edit()
                    .putString("projectionStatus", projectionStatus)
                    .putString("captureReadiness", "WAITING_GTO_LANDSCAPE")
                    .putBoolean("captureReadyForAnalysis", false)
                    .putString("lastEvent", "Autorização aceita · aguardando o GTO estabilizar em paisagem")
                    .apply();
            }
            return;
        }

        if (projectionSurfaceStableWidth != width || projectionSurfaceStableHeight != height) {
            projectionSurfaceStableWidth = width;
            projectionSurfaceStableHeight = height;
            projectionSurfaceStableSince = now;
            projectionSurfaceStablePolls = 1;
            projectionStatus = "WAITING_GTO_GEOMETRY";
            prefs.edit()
                .putString("projectionStatus", projectionStatus)
                .putString("captureReadiness", "WAITING_GTO_GEOMETRY")
                .putInt("pendingProjectionWidth", width)
                .putInt("pendingProjectionHeight", height)
                .putBoolean("captureReadyForAnalysis", false)
                .putString("lastEvent", "GTO em paisagem · confirmando geometria antes de iniciar a captura")
                .apply();
            return;
        }

        if (projectionSurfaceStablePolls < PROJECTION_SURFACE_STABLE_POLLS) {
            projectionSurfaceStablePolls++;
        }
        boolean settled = projectionSurfaceStableSince > 0L
            && now >= projectionSurfaceStableSince
            && now - projectionSurfaceStableSince >= PROJECTION_SURFACE_LANDSCAPE_SETTLE_MS;
        if (projectionSurfaceStablePolls < PROJECTION_SURFACE_STABLE_POLLS || !settled) return;

        createProjectionSurface(width, height);
    }

    private void createProjectionSurface(int width, int height) {
        if (!projectionSurfacePending || projectionActive || mediaProjection == null) return;
        final MediaProjection projection = mediaProjection;
        final long generation = projectionGeneration;
        try {
            captureWidth = Math.max(1, width);
            captureHeight = Math.max(1, height);
            captureDensityDpi = Math.max(1, getResources().getConfiguration().densityDpi);

            captureThread = new HandlerThread("NVU-GTO-Capture");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());
            invalidateCaptureBoundAnalysis("PROJECTION_START_LANDSCAPE");

            imageReader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                3
            );
            imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

            VirtualDisplay createdDisplay = projection.createVirtualDisplay(
                "NVU-GTO-Observer",
                captureWidth,
                captureHeight,
                captureDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
            );
            if (createdDisplay == null) {
                throw new IllegalStateException("VirtualDisplay não foi criado pelo Android");
            }
            if (generation != projectionGeneration || mediaProjection != projection) {
                try { createdDisplay.release(); } catch (Exception ignored) {}
                throw new IllegalStateException("Sessão de captura mudou durante a criação da superfície");
            }
            virtualDisplay = createdDisplay;
            projectionActive = true;
            projectionSurfacePending = false;
            projectionStartedAt = System.currentTimeMillis();
            captureResizeRetryCount = 0;
            projectionSurfaceRebindAttempts = 0;
            lastProjectionSurfaceRecoveryAt = 0L;
            lastProjectionFrameAt = 0L;
            lastProjectionAnalyzedFrameAt = 0L;
            resetPendingProjectionSurfaceStability();
            resetCaptureStabilityBarrier(
                GtoCaptureStabilityGate.CAPTURE_WAITING_GTO_FOREGROUND,
                captureWidth,
                captureHeight,
                "Captura criada em paisagem · aguardando quadros estáveis do GTO"
            );
            freightListMissingSince = 0L;
            freightListMissingFrames = 0;
            lastFreightListSeenAt = 0L;
            projectionStatus = "ACTIVE_WAITING_FIRST_FRAME";
            prefs.edit()
                .putBoolean("projectionActive", true)
                .putBoolean("projectionSessionBound", true)
                .putBoolean("projectionSurfacePending", false)
                .putBoolean("projectionGrantValidated", true)
                .putString("projectionStatus", projectionStatus)
                .putInt("captureWidth", captureWidth)
                .putInt("captureHeight", captureHeight)
                .putInt("captureDensityDpi", captureDensityDpi)
                .putInt("captureAndroidApi", Build.VERSION.SDK_INT)
                .putLong("projectionStartSucceededAt", projectionStartedAt)
                .remove("projectionFirstFrameAt")
                .putString("screenState", "CAPTURE_STABILIZING")
                .remove("projectionError")
                .remove("projectionReauthRequired")
                .remove("projectionReauthNoticeShown")
                .putString("lastEvent", "Compartilhamento aceito · VirtualDisplay ativa, validando primeiro quadro")
                .apply();
            updateNotification();
            updateFreightTouchPulseSensor();
            if (menuView != null) mainHandler.post(this::refreshMenuContents);
        } catch (Exception ex) {
            projectionGeneration++;
            projectionActive = false;
            projectionSurfacePending = false;
            projectionSessionBoundAt = 0L;
            resetPendingProjectionSurfaceStability();
            projectionStatus = "START_FAILED";
            String detail = describeError(ex);
            resetCaptureStabilityBarrier(
                GtoCaptureStabilityGate.INACTIVE,
                0,
                0,
                "Falha ao criar captura em paisagem"
            );
            prefs.edit()
                .putBoolean("projectionActive", false)
                .putBoolean("projectionSessionBound", false)
                .putBoolean("projectionSurfacePending", false)
                .putBoolean("projectionGrantValidated", false)
                .putBoolean("captureSurfaceReady", false)
                .putString("projectionStatus", projectionStatus)
                .putString("screenState", "CAPTURE_START_FAILED")
                .putString("projectionError", detail)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthAutoAllowed", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putBoolean("touchCaptureNeeded", false)
                .putString("lastEvent", "Falha ao criar captura em paisagem: " + detail)
                .apply();
            releaseCaptureResources(true);
            try {
                startForegroundForTypes(false);
            } catch (Exception ignored) {}
            updateNotification();
            showStatusChip("A leitura não iniciou corretamente. Abra a bolinha NVU, toque em Autorizar leitura da tela e tente novamente.", 4200L);
        }
    }

    private void maybeRecoverProjectionFrameDelivery(long now) {
        if (!GtoCaptureHealthPolicy.shouldRecoverSurface(
            projectionActive,
            mediaProjection != null,
            virtualDisplay != null,
            imageReader != null,
            captureHandler != null,
            gtoForeground,
            screenAnalysisPausedOutsideGto,
            projectionSurfacePending,
            projectionPermissionInFlight,
            now,
            lastProjectionFrameAt,
            lastProjectionAnalyzedFrameAt,
            projectionStartedAt,
            lastProjectionSurfaceRecoveryAt,
            PROJECTION_FIRST_FRAME_WATCHDOG_MS,
            PROJECTION_STALE_FRAME_WATCHDOG_MS,
            PROJECTION_STALE_ANALYSIS_WATCHDOG_MS,
            PROJECTION_SURFACE_REBIND_COOLDOWN_MS
        )) return;

        // Never enter a terminal ACTIVE_NO_FRAMES state while the MediaProjection token
        // remains valid. Rebind the ImageReader surface repeatedly, with a small cooldown,
        // until real frames resume or Android explicitly stops the projection token.
        lastProjectionSurfaceRecoveryAt = now;
        projectionSurfaceRebindAttempts = projectionSurfaceRebindAttempts == Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : projectionSurfaceRebindAttempts + 1;
        prefs.edit()
            .putInt("projectionSurfaceRebindAttempts", projectionSurfaceRebindAttempts)
            .putLong("projectionSurfaceRecoveryAt", now)
            .putString("captureReadiness", "RECOVERING_SURFACE")
            .putBoolean("captureReadyForAnalysis", false)
            .putString("lastEvent", "Captura sem quadros reais · reconectando superfície sem perder a viagem")
            .apply();
        rebindProjectionSurfaceWithoutReauthorization();

        final int scheduledAttempts = projectionSurfaceRebindAttempts;
        if (scheduledAttempts >= PROJECTION_SURFACE_REAUTH_ESCALATION_ATTEMPTS) {
            mainHandler.postDelayed(() -> {
                long checkAt = System.currentTimeMillis();
                if (projectionSurfaceRebindAttempts < scheduledAttempts) return;
                if (!GtoProjectionRecoveryPolicy.shouldEscalateSurfaceRecovery(
                    projectionSurfaceRebindAttempts,
                    gtoForeground,
                    captureIsNeededForCurrentState(),
                    projectionPermissionInFlight,
                    checkAt,
                    lastProjectionFrameAt,
                    lastProjectionAnalyzedFrameAt,
                    projectionStartedAt,
                    Math.max(PROJECTION_STALE_FRAME_WATCHDOG_MS, PROJECTION_STALE_ANALYSIS_WATCHDOG_MS)
                )) return;
                boolean healthy = GtoCaptureHealthPolicy.isHealthy(
                    projectionActive,
                    mediaProjection != null,
                    virtualDisplay != null,
                    imageReader != null,
                    captureHandler != null,
                    gtoForeground,
                    screenAnalysisPausedOutsideGto,
                    captureStabilityGate.isReady(),
                    checkAt,
                    lastProjectionFrameAt,
                    lastProjectionAnalyzedFrameAt
                );
                if (!healthy) {
                    escalateProjectionToFreshAuthorization(
                        "A leitura continuou sem quadros válidos após " + projectionSurfaceRebindAttempts + " recuperações"
                    );
                }
            }, 1300L);
        }
    }

    private void rebindProjectionSurfaceWithoutReauthorization() {
        final Handler handler = captureHandler;
        final VirtualDisplay expectedDisplay = virtualDisplay;
        final ImageReader expectedReader = imageReader;
        final long expectedGeneration = projectionGeneration;
        final int width = captureWidth;
        final int height = captureHeight;
        if (handler == null || expectedDisplay == null || expectedReader == null || width <= 0 || height <= 0) return;

        handler.post(() -> {
            ImageReader replacement = null;
            try {
                replacement = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
                replacement.setOnImageAvailableListener(this::onImageAvailable, handler);
                expectedDisplay.setSurface(replacement.getSurface());
                if (!projectionActive
                    || expectedGeneration != projectionGeneration
                    || virtualDisplay != expectedDisplay
                    || imageReader != expectedReader) {
                    try { replacement.close(); } catch (Exception ignored) {}
                    return;
                }
                imageReader = replacement;
                replacement = null;
                try { expectedReader.close(); } catch (Exception ignored) {}
                lastProjectionFrameAt = 0L;
                lastProjectionAnalyzedFrameAt = 0L;
                resetCaptureStabilityBarrier(
                    GtoCaptureStabilityGate.CAPTURE_WAITING_STABLE_FRAMES,
                    width,
                    height,
                    "Superfície reconectada · validando quadros do GTO"
                );
                prefs.edit()
                    .remove("projectionFirstFrameAt")
                    .putString("captureReadiness", GtoCaptureStabilityGate.CAPTURE_WAITING_STABLE_FRAMES)
                    .putBoolean("captureReadyForAnalysis", false)
                    .putString("lastEvent", "Superfície da captura reconectada sem nova autorização")
                    .apply();
            } catch (Exception ex) {
                if (replacement != null) {
                    try { replacement.close(); } catch (Exception ignored) {}
                }
                prefs.edit()
                    .putString("projectionError", "Rebind: " + describeError(ex))
                    .putLong("projectionErrorAt", System.currentTimeMillis())
                    .putString("lastEvent", "Falha ao reconectar superfície; sessão de compartilhamento preservada")
                    .apply();
            }
        });
    }

    private boolean startProjection(int resultCode, Intent resultData) {
        long boundAt = System.currentTimeMillis();
        prefs.edit()
            .putLong("projectionStartAttemptAt", boundAt)
            .remove("projectionError")
            .apply();
        stopProjection();
        resetCaptureStabilityBarrier(
            GtoCaptureStabilityGate.CAPTURE_STARTING,
            0,
            0,
            "Vinculando autorização de leitura"
        );

        try {
            // Android 14+ requires the mediaProjection foreground-service type before
            // getMediaProjection(). Do this immediately after the visible consent result.
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
                    // Before the first VirtualDisplay exists there is nothing to resize.
                    // The pending surface will use the stable live GTO geometry instead.
                    if (!projectionActive || virtualDisplay == null) return;
                    resizeProjectionSurface(width, height);
                }

                @Override
                public void onStop() {
                    mainHandler.post(() -> {
                        // Never let that stale callback release a newer capture session.
                        if (generation != projectionGeneration || mediaProjection != projection) return;
                        boolean wasPending = projectionSurfacePending;
                        projectionGeneration++;
                        long stoppedAt = System.currentTimeMillis();
                        long activeForMs = projectionStartedAt > 0L
                            ? Math.max(0L, stoppedAt - projectionStartedAt)
                            : -1L;
                        projectionActive = false;
                        projectionSurfacePending = false;
                        projectionSessionBoundAt = 0L;
                        resetPendingProjectionSurfaceStability();
                        projectionStatus = wasPending && activeForMs < 0L
                            ? "STOPPED_BEFORE_SURFACE"
                            : activeForMs >= 0L && activeForMs < 3000L
                                ? "STOPPED_EARLY"
                                : "STOPPED";
                        resetCaptureStabilityBarrier(
                            GtoCaptureStabilityGate.INACTIVE,
                            0,
                            0,
                            "Leitura da tela foi encerrada pelo Android"
                        );
                        android.content.SharedPreferences.Editor stoppedEditor = prefs.edit()
                            .putBoolean("projectionActive", false)
                            .putBoolean("projectionSessionBound", false)
                            .putBoolean("projectionSurfacePending", false)
                            .putBoolean("projectionGrantValidated", false)
                            .remove("projectionSessionBoundAt")
                            .putBoolean("captureSurfaceReady", false)
                            .putString("projectionStatus", projectionStatus)
                            .putString("screenState", "CAPTURE_STOPPED")
                            .putLong("projectionStoppedAt", stoppedAt)
                            .putLong("projectionActiveForMs", activeForMs)
                            .putBoolean("projectionReauthRequired", true)
                            .putBoolean("projectionReauthAutoAllowed", true)
                            .putBoolean("projectionReauthNoticeShown", false)
                            .putBoolean("touchCaptureNeeded", false);
                        if ("STOPPED_BEFORE_SURFACE".equals(projectionStatus)) {
                            stoppedEditor
                                .putString("projectionError", "O Android encerrou a autorização antes de a captura em paisagem ser criada.")
                                .putString("lastEvent", "Autorização encerrada antes da captura · verifique outro gravador/compartilhamento");
                        } else if ("STOPPED_EARLY".equals(projectionStatus)) {
                            stoppedEditor
                                .putString("projectionError", "O Android encerrou a leitura logo após iniciar. Isso pode ocorrer se outra gravação/compartilhamento de tela iniciar ou se o sistema invalidar a sessão.")
                                .putString("lastEvent", "Leitura encerrada logo após iniciar · verifique outro gravador/compartilhamento");
                        } else {
                            stoppedEditor.putString("lastEvent", "Leitura da tela foi encerrada pelo Android");
                        }
                        stoppedEditor.apply();
                        projectionStartedAt = 0L;
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
                                ensureProjectionAuthorizationIfNeeded(System.currentTimeMillis());
                            }
                        }
                    });
                }
            }, mainHandler);

            // HF10: consume the one-use grant exactly once and create its only
            // VirtualDisplay immediately, while the consent host is still guaranteed
            // landscape. Delaying createVirtualDisplay() until a later UsageStats poll
            // left a real MediaProjection token idle and exposed it to OEM/process/task
            // lifecycle races before the capture session had actually begun.
            projectionActive = false;
            projectionSurfacePending = true;
            projectionPermissionInFlight = false;
            projectionSessionBoundAt = System.currentTimeMillis();
            resetPendingProjectionSurfaceStability();
            projectionStartedAt = 0L;
            pendingCapturedWidth = 0;
            pendingCapturedHeight = 0;
            projectionStatus = "GRANT_VALIDATED_STARTING_SURFACE";
            resetCaptureStabilityBarrier(
                GtoCaptureStabilityGate.CAPTURE_WAITING_GTO_FOREGROUND,
                0,
                0,
                "Autorização aceita · iniciando captura imediatamente"
            );
            prefs.edit()
                .putBoolean("projectionActive", false)
                .putBoolean("projectionPermissionInFlight", false)
                .putBoolean("projectionSessionBound", true)
                .putBoolean("projectionSurfacePending", true)
                .putBoolean("projectionGrantValidated", true)
                .putLong("projectionGrantValidatedAt", projectionSessionBoundAt)
                .putLong("projectionSessionBoundAt", projectionSessionBoundAt)
                .putString("projectionStatus", projectionStatus)
                .putString("captureReadiness", "STARTING_SURFACE")
                .putBoolean("captureReadyForAnalysis", false)
                .putBoolean("captureSurfaceReady", false)
                .putString("screenState", "CAPTURE_STARTING_SURFACE")
                .remove("projectionError")
                .remove("projectionReauthRequired")
                .remove("projectionReauthNoticeShown")
                .putBoolean("projectionReauthAutoAllowed", false)
                .putString("lastEvent", "Autorização validada · criando captura antes de fechar a confirmação")
                .apply();

            DisplayMetrics liveMetrics = realDisplayMetrics();
            int initialWidth = prefs.getInt("projectionConsentHostWidth", 0);
            int initialHeight = prefs.getInt("projectionConsentHostHeight", 0);
            if (initialWidth <= initialHeight || initialHeight <= 0) {
                initialWidth = liveMetrics.widthPixels;
                initialHeight = liveMetrics.heightPixels;
            }
            // The consent Activity is itself pinned to landscape. If an OEM reports a
            // transient portrait metric here, use the already-verified landscape handoff
            // dimensions rather than abandoning a valid one-use grant.
            if (initialWidth <= initialHeight || initialHeight <= 0) {
                int verifiedWidth = prefs.getInt("projectionConsentGtoWidth", 0);
                int verifiedHeight = prefs.getInt("projectionConsentGtoHeight", 0);
                if (verifiedWidth > verifiedHeight && verifiedHeight > 0) {
                    initialWidth = verifiedWidth;
                    initialHeight = verifiedHeight;
                }
            }
            if (initialWidth <= initialHeight || initialHeight <= 0) {
                throw new IllegalStateException("Geometria horizontal indisponível após autorização");
            }

            createProjectionSurface(initialWidth, initialHeight);
            boolean started = projectionActive
                && mediaProjection == projection
                && virtualDisplay != null
                && imageReader != null;
            if (!started) {
                throw new IllegalStateException("VirtualDisplay não ficou ativa após autorização");
            }
            updateNotification();
            if (menuView != null) mainHandler.post(this::refreshMenuContents);
            return true;

        } catch (Exception ex) {
            projectionGeneration++;
            projectionPermissionInFlight = false;
            projectionActive = false;
            projectionSurfacePending = false;
            projectionSessionBoundAt = 0L;
            resetPendingProjectionSurfaceStability();
            projectionStatus = "START_FAILED";
            resetCaptureStabilityBarrier(
                GtoCaptureStabilityGate.INACTIVE,
                0,
                0,
                "Falha ao vincular autorização de leitura"
            );
            String detail = describeError(ex);
            prefs.edit()
                .putBoolean("projectionPermissionInFlight", false)
                .putBoolean("projectionActive", false)
                .putBoolean("projectionSessionBound", false)
                .putBoolean("projectionSurfacePending", false)
                .putBoolean("projectionGrantValidated", false)
                .putString("projectionStatus", projectionStatus)
                .putString("screenState", "CAPTURE_START_FAILED")
                .putString("projectionError", detail)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthAutoAllowed", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putBoolean("touchCaptureNeeded", false)
                .putString("lastEvent", "Falha ao vincular autorização de leitura: " + detail)
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
            showToast("Falha ao vincular a autorização da tela. Abra a bolinha NVU, toque em Autorizar leitura da tela e tente novamente.");
            if (menuView != null) mainHandler.post(this::refreshMenuContents);
            return false;
        }
    }

    private void resizeProjectionSurface(int width, int height) {
        if (width <= 0 || height <= 0 || width == captureWidth && height == captureHeight) return;
        resetCaptureStabilityBarrier(
            GtoCaptureStabilityGate.CAPTURE_WAITING_ORIENTATION,
            width,
            height,
            "Mudança de orientação detectada · pausando análise"
        );
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
                invalidateCaptureBoundAnalysis("CAPTURE_RESIZE");
                // A duplicate Android resize callback may have been queued before the
                // first one updated captureWidth/captureHeight. Re-arm stability without
                // rebuilding the reader a second time.
                if (width == captureWidth && height == captureHeight) {
                    resetCaptureStabilityBarrier(
                        GtoCaptureStabilityGate.CAPTURE_WAITING_STABLE_FRAMES,
                        captureWidth,
                        captureHeight,
                        "Geometria ajustada · conferindo quadros estáveis"
                    );
                    return;
                }
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
                captureResizeRetryCount = 0;
                pendingCapturedWidth = width;
                pendingCapturedHeight = height;
                lastCaptureGeometryPollAt = 0L;
                lastCaptureGeometryMatched = false;
                if (previous != null) {
                    try { previous.close(); } catch (Exception ignored) {}
                }
                resetCaptureStabilityBarrier(
                    GtoCaptureStabilityGate.CAPTURE_WAITING_STABLE_FRAMES,
                    captureWidth,
                    captureHeight,
                    "Captura redimensionada · validando três quadros no GTO"
                );
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
                // A resize failure is not proof that the MediaProjection token died.
                // Preserve the existing session/trip and keep retrying the geometry change
                // with a bounded delay. Only MediaProjection.Callback.onStop() is allowed
                // to declare the token invalid and request a new authorization.
                if (projectionActive
                    && expectedGeneration == projectionGeneration
                    && virtualDisplay == expectedDisplay
                    && mediaProjection != null) {
                    captureResizeRetryCount = captureResizeRetryCount == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : captureResizeRetryCount + 1;
                    long retryDelay = captureResizeRetryCount <= 1 ? 180L : 1200L;
                    resetCaptureStabilityBarrier(
                        GtoCaptureStabilityGate.CAPTURE_WAITING_ORIENTATION,
                        width,
                        height,
                        "Falha transitória no resize · mantendo sessão e tentando novamente"
                    );
                    prefs.edit()
                        .putString("captureReadiness", "RECOVERING_RESIZE")
                        .putBoolean("captureReadyForAnalysis", false)
                        .putInt("captureResizeRetryCount", captureResizeRetryCount)
                        .putString("lastEvent", "Falha transitória no resize · sessão preservada, nova tentativa automática")
                        .apply();
                    handler.postDelayed(() -> resizeProjectionSurface(width, height), retryDelay);
                }
            }
        });
    }

    private void onImageAvailable(ImageReader reader) {
        if (reader == null || reader != imageReader) {
            return;
        }
        long callbackAt = System.currentTimeMillis();
        lastProjectionFrameAt = callbackAt;
        projectionSurfaceRebindAttempts = 0;
        if (prefs.getLong("projectionFirstFrameAt", 0L) <= 0L) {
            projectionStatus = "ACTIVE_FRAMES_VALIDATED";
            prefs.edit()
                .putLong("projectionFirstFrameAt", callbackAt)
                .putBoolean("projectionActive", true)
                .putBoolean("projectionGrantValidated", true)
                .putBoolean("projectionSessionBound", true)
                .putBoolean("projectionSurfacePending", false)
                .putBoolean("projectionPermissionInFlight", false)
                .putString("projectionStatus", projectionStatus)
                .remove("projectionError")
                .remove("projectionReauthRequired")
                .remove("projectionReauthNoticeShown")
                .putBoolean("projectionReauthAutoAllowed", false)
                .putString("lastEvent", "Leitura funcional · primeiro quadro recebido, detecção liberada")
                .apply();
            updateNotification();
            if (menuView != null) mainHandler.post(this::refreshMenuContents);
        }
        if (screenAnalysisPausedOutsideGto || !gtoForeground) {
            boolean foregroundOwnerAllowsVisualProbe = foregroundPackage == null
                || foregroundPackage.isEmpty()
                || GTO_PACKAGE.equals(foregroundPackage)
                || getPackageName().equals(foregroundPackage);
            // Permission return and an explicit NVU->GTO launch share the same bounded
            // bridge. Do not require screenAnalysisPausedOutsideGto here: the first
            // ImageReader callback can beat the 350 ms foreground poll. A strict real
            // freight list is still required inside consumeCaptureStabilityFrame(), and
            // a known third-party foreground owner is never eligible for this probe.
            boolean trustedWaitingFreightProbe = projectionActive
                && STATE_WAITING_FREIGHT.equals(getTripState())
                && callbackAt < suppressForegroundHideUntil
                && !transientForegroundSurfaceActive
                && foregroundOwnerAllowsVisualProbe;
            if (trustedWaitingFreightProbe) {
                consumeCaptureStabilityFrame(reader);
                return;
            }
            Image pausedImage = null;
            try {
                pausedImage = reader.acquireLatestImage();
            } catch (Exception ignored) {
            } finally {
                if (pausedImage != null) pausedImage.close();
            }
            return;
        }
        if (!isCaptureReadyForAnalysis(callbackAt)) {
            consumeCaptureStabilityFrame(reader);
            return;
        }
        // A locked immutable freight is stronger than a stale WAITING_FREIGHT runtime
        // flag. Repair that invariant before routing the frame so a real result screen
        // cannot be misclassified as "choose freight" after an app switch/restart race.
        repairTripStateFromDurableFreightIfNeeded();

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
            if (!GtoFrameFreshnessPolicy.shouldConsume(System.nanoTime(), image.getTimestamp(), false)) {
                prefs.edit().putLong("staleAnalysisFrameDroppedAt", System.currentTimeMillis()).apply();
                return;
            }

            String state = getTripState();
            if (!gtoForeground) return;

            long now = System.currentTimeMillis();
            boolean resultProbeOccludedByNvuMenu = false;
            boolean resultTrackingState = isResultTrackingState(state);

            // Freight-list interpretation is state-scoped. During a confirmed route,
            // orange scenery/HUD fragments must never become a jobs list. The freight
            // detector is allowed to run in TRIP_IN_PROGRESS only after the driver has
            // explicitly armed "Trocar frete atual" from the NVU overlay. Result-screen
            // probing remains independent so completion detection stays automatic.
            if ((isReplaceableActiveSessionState(state) || resultTrackingState)
                && now - lastActiveTripVisualProbeAt >= ACTIVE_TRIP_VISUAL_PROBE_MS) {
                lastActiveTripVisualProbeAt = now;
                if (isReplaceableActiveSessionState(state)) {
                    boolean explicitReplacement = isExplicitFreightReplacementActive(now);
                    boolean mayProbeFreightList = GtoDeterministicFlowPolicy.mayProbeFreightListForCurrentState(
                        state, explicitReplacement
                    );
                    // Recognition priority is broader than replacement permission. While a
                    // trip is active we may observe a genuine list as informational context,
                    // but the existing deterministic policy still blocks selection/replacement
                    // unless the driver explicitly armed it.
                    boolean informationalListProbe = STATE_TRIP_IN_PROGRESS.equals(state)
                        && !explicitReplacement;
                    if (mayProbeFreightList || informationalListProbe) {
                        GtoFastVisualDetector.Frame activeFrame = fastVisualDetector.analyze(
                            image, captureWidth, captureHeight, now
                        );
                        // Mark health only after a real state-scoped detector completed.
                        markProjectionFrameAnalyzed(now);
                        if (handleActiveTripFreightListEvidence(image, activeFrame, now)) return;
                    } else if (STATE_TRIP_IN_PROGRESS.equals(state)) {
                        clearActiveTripFreightListRuntime();
                        prefs.edit()
                            .putBoolean("activeTripFreightListVisible", false)
                            .putInt("freightCount", 0)
                            .putString("screenState", "TRIP")
                            .apply();
                    }
                }

                if (resultTrackingState) {
                    // HF16: result detection no longer depends on dark-pixel ratios, exact
                    // modal colors or a structural prefilter. During an active trip we only
                    // need fresh frames plus semantic OCR (Concluído + monetary value).
                    // The NVU card is still kept out of the central OCR region, but it can
                    // never manufacture or suppress a result state by itself.
                    resultProbeOccludedByNvuMenu = ownMenuOccludesResultProbe();
                    if (resultProbeOccludedByNvuMenu) {
                        // HF20: opening the NVU card must never pause completion detection.
                        // Result recognition is semantic (Concluído + monetary value), so the
                        // old dark-layout self-interference no longer applies. Keep the card
                        // where the driver opened it and continue OCR on fresh frames.
                        prefs.edit()
                            .putLong("resultOwnOverlayPresentAt", now)
                            .putString("lastEvent", "Card NVU aberto sem suspender leitura do resultado")
                            .apply();
                    }
                    // HF21: no pixel/color signature is allowed to wake or confirm the
                    // result screen. The route state simply performs a low-frequency OCR
                    // fallback and parseResultScreen() remains the sole authority through
                    // the semantic pair Concluído + monetary value. This avoids reviving
                    // the old self-interference class caused by our own dark overlay.
                    // A state-scoped result monitor touched this live frame.
                    markProjectionFrameAnalyzed(now);
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
            boolean tripFallbackOcrDue = resultTrackingState
                && !manualFinishCapturePending
                && now - lastActiveTripFallbackOcrAt >= ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS;
            boolean tripCandidateOcrDue = false; // Semantic fallback only; no pixel/color result gate.
            boolean ocrDue = manualFinishReady && (
                (resultTrackingState
                    ? (STATE_TRIP_IN_PROGRESS.equals(state) && manualFinishCapturePending
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
            if (resultTrackingState && !manualFinishCapturePending) {
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
            } else if (resultTrackingState) {
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
                    reportFrameProcessingError("OCR local", error);
                })
                .addOnCompleteListener(task -> {
                    if (!bitmapForOcr.isRecycled()) bitmapForOcr.recycle();
                    if (fullFrameForGeometry != bitmapForOcr && !fullFrameForGeometry.isRecycled()) fullFrameForGeometry.recycle();
                    ocrBusy.set(false);
                });
        } catch (Exception ex) {
            ocrBusy.set(false);
            reportFrameProcessingError("processamento do quadro", ex);
        } finally {
            if (image != null) image.close();
        }
    }

    private boolean isCurrentAnalysisOcr(long generation, String sessionId) {
        if (screenAnalysisPausedOutsideGto || !gtoForeground) return false;
        if (generation != analysisOcrGeneration) return false;
        String currentSessionId = prefs == null ? "" : prefs.getString("gtoTripSessionId", "");
        if (!(sessionId == null ? "" : sessionId).equals(currentSessionId)) return false;
        String state = getTripState();
        return isResultTrackingState(state)
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

    private void armFastTouchPulseOnCaptureThread(float rawX, float rawY, float localX, float localY) {
        if (!STATE_WAITING_FREIGHT.equals(getTripState())) return;

        long markerSequence = selectionCoordinator.markTouch();
        if (Float.isFinite(rawX) || Float.isFinite(rawY) || Float.isFinite(localX) || Float.isFinite(localY)) {
            fastTouchRawX = rawX;
            fastTouchRawY = rawY;
            fastTouchLocalX = localX;
            fastTouchLocalY = localY;
        } else {
            fastTouchRawX = -1f;
            fastTouchRawY = -1f;
            fastTouchLocalX = -1f;
            fastTouchLocalY = -1f;
        }
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

        // ACTION_OUTSIDE coordinates are not trusted by themselves. When at least one
        // coordinate representation lands inside exactly one frozen Aceitar box and all
        // mapped representations agree, preserve that exact row as a candidate. The GTO
        // list must still close before finalization, so a tap that does not accept a job
        // expires without locking freight data. OEMs that report (0,0) continue through
        // the pressed-frame detector unchanged.
        if (fastTouchBaseline != null) {
            int exactTouchRow = exactConsistentRowFromOutsideTouch(fastTouchBaseline.buttons);
            if (exactTouchRow >= 0) {
                FreightSelectionTransaction transaction = buildSelectionTransaction(
                    exactTouchRow,
                    "exact-outside-touch+frame-lock"
                );
                if (transaction != null) {
                    fastPendingFromTouchPulse = true;
                    fastPendingSelectedRow = exactTouchRow;
                    fastPendingSelectedAt = fastTouchPulseAt;
                    fastPendingSelectedScore = 1f;
                    preciseSelectedRow = exactTouchRow;
                    preciseSelectedTouchAt = fastTouchPulseAt;
                    replacePendingSelectionTransaction(transaction);
                    prefs.edit()
                        .putString("pendingSelectionSource", "exact-touch-row-" + (exactTouchRow + 1))
                        .putString("lastEvent", "Frete tocado na linha " + (exactTouchRow + 1) + " · aguardando fechamento da lista")
                        .apply();
                }
            }
        }
    }

    private void clearFastTouchPulse(boolean preserveFrozenSnapshot) {
        fastTouchPulseActive = false;
        fastTouchPulseAt = 0L;
        fastTouchMarkerSequence = -1L;
        fastTouchBaseline = null;
        fastTouchBaselineSequence = -1L;
        fastTouchRawX = -1f;
        fastTouchRawY = -1f;
        fastTouchLocalX = -1f;
        fastTouchLocalY = -1f;
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
        // Page identity is defined by the cargo/text panel, not by the number of orange
        // Aceitar pixels. A pressed button can temporarily disappear from the orange mask
        // (N -> N-1) while the page itself is unchanged. The previous implementation used
        // samePage(), whose cardinality check misclassified that press animation as page
        // navigation and could overwrite the clean pre-touch snapshot with the pressed frame.
        float panelDistance = noSnapshot ? 1f : fastVisualDetector.pageDistance(fastLastSnapshotFrame, current);
        boolean pageChanged = !noSnapshot && panelDistance >= 0.024f;
        boolean transientPressedCardinality = !noSnapshot
            && fastVisualDetector.detectTemporarilyMissingPressedRow(
                fastLastSnapshotFrame, current, captureHeight
            ) != null;
        boolean refresh = now - lastFastPanelSnapshotAt >= 900L && !transientPressedCardinality;
        if (!noSnapshot && !pageChanged && !refresh) return;

        // The button strip is excluded from panelSignature. Therefore pageChanged means
        // the freight content really changed, while N -> N-1 press animation preserves
        // the clean page snapshot and its complete row geometry.
        Bitmap full = imageToBitmap(image, captureWidth, captureHeight);
        if (full == null) return;
        int left = freightPanelLeftForButtons(full.getWidth(), current.buttons);
        keepOverlaysClearOfFreightPanel(left, full.getWidth());
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
        scheduleFreightPageOcr(generation, panelSnapshot, panelOffsetX, buttons, now, false);
    }

    private void scheduleFreightPageOcr(
        long generation,
        Bitmap panelSnapshot,
        int panelOffsetX,
        List<Rect> buttons,
        long now,
        boolean selectionCritical
    ) {
        if (panelSnapshot == null || panelSnapshot.isRecycled() || textRecognizer == null) return;
        if (generation != freightPageGeneration) return;
        // A newly selected row must never fail merely because an OCR refresh from the
        // previous page ran less than 220 ms ago. The selection-critical path uses the
        // immutable pre-touch panel and bypasses only the time throttle; ocrBusy still
        // serializes ML Kit so weak devices never run both page recognizers concurrently.
        if (!selectionCritical && now - lastFreightPageOcrAt < 220L) return;
        if (!ocrBusy.compareAndSet(false, true)) return;
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
                String state = getTripState();
                if ((!STATE_WAITING_FREIGHT.equals(state) && !STATE_CONFIRMING_FREIGHT.equals(state))
                    || generation != freightPageGeneration) return;
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
                int semanticAnchors = semanticFreightAnchorRows(parsed);
                boolean semanticCertified = GtoFreightSemanticCertificationPolicy.isCertifiedPage(
                    buttonCopy.size(), parsed.size(), semanticAnchors
                );
                if (semanticCertified) {
                    markFreightPageSemanticallyCertified(generation, semanticAnchors, parsed.size());
                } else {
                    recordObserverEvent(
                        "FREIGHT_LIST_CANDIDATE_REJECTED",
                        "generation=" + generation + " anchors=" + semanticAnchors + " rows=" + parsed.size()
                    );
                    return;
                }
                // The generation is an immutable page identity from the visual detector.
                // Stabilize this lightweight list OCR exactly like the older whole-frame
                // path so a one-frame hallucination can never become a silent fallback.
                int pageKey = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, generation));
                List<FreightOption> stable = stabilizeFreightOptions(pageKey, parsed);
                for (FreightOption option : stable) {
                    option.origin = option.originCompany == null ? "" : option.originCompany.trim();
                }
                synchronized (freightOptions) {
                    freightOptions.clear();
                    for (FreightOption option : stable) freightOptions.add(copyFreightOption(option));
                }
                prefs.edit()
                    .putString("freightOptions", freightOptionsToJson(stable))
                    .putInt("freightCount", stable.size())
                    .putLong("freightTextGeneration", generation)
                    .putLong("freightTextAt", System.currentTimeMillis())
                    .putLong("freightStableAt", freightHistoryUpdatedAt)
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

    private int stableFreightRuntimeCount(GtoFastVisualDetector.Frame current) {
        if (current == null || current.buttons == null) return 0;
        int currentCount = current.buttons.size();
        GtoFastVisualDetector.Frame baseline = fastTouchBaseline != null
            ? fastTouchBaseline
            : fastPreviousFreightFrame;
        if (baseline == null || !baseline.hasFreightList()) return currentCount;
        if (baseline.buttons.size() != currentCount + 1) return currentCount;

        GtoFastVisualDetector.PressCandidate missing = fastTouchPulseActive
            ? fastVisualDetector.detectPressedRowAfterTouch(baseline, current, captureHeight)
            : fastVisualDetector.detectTemporarilyMissingPressedRow(baseline, current, captureHeight);
        return GtoDeterministicFlowPolicy.stabilizeVisibleFreightCount(
            baseline.buttons.size(), currentCount, missing != null
        );
    }

    private void onFreightFrameAvailable(ImageReader reader) {
        if (reader == null || reader != imageReader) {
            return;
        }
        Image image = null;
        try {
            // R3.27: freight selection is a temporal gesture, not a static screenshot.
            // Always consume WAITING_FREIGHT frames in order. acquireLatestImage() can
            // legally discard the sub-frame in which a single Aceitar button is pressed,
            // especially on OEMs that do not deliver ACTION_OUTSIDE. Missing that frame
            // makes visual row selection impossible even though list detection works.
            // The fast detector is OCR-free and this path is active only while waiting
            // for a freight, so the ordered queue remains bounded by ImageReader(3).
            image = GtoDeterministicFlowPolicy.useOrderedFreightFrames(getTripState())
                ? reader.acquireNextImage()
                : reader.acquireLatestImage();
            if (image == null) return;
            boolean criticalTouchFrame = fastTouchPulseActive || selectionCoordinator.isCriticalWindow();
            if (!GtoFrameFreshnessPolicy.shouldConsume(System.nanoTime(), image.getTimestamp(), criticalTouchFrame)) {
                prefs.edit()
                    .putLong("staleFreightFrameDroppedAt", System.currentTimeMillis())
                    .putBoolean("staleFreightFrameCriticalWindow", criticalTouchFrame)
                    .apply();
                return;
            }
            if (screenAnalysisPausedOutsideGto || !gtoForeground) return;
            if (image.getWidth() != captureWidth || image.getHeight() != captureHeight) {
                return;
            }
            if (!STATE_WAITING_FREIGHT.equals(getTripState())) return;

            long now = System.currentTimeMillis();
            GtoFastVisualDetector.Frame current = fastVisualDetector.analyze(image, captureWidth, captureHeight, now);
            markProjectionFrameAnalyzed(now);
            boolean hasList = current != null && current.hasFreightList();
            int runtimeFreightCount = hasList ? stableFreightRuntimeCount(current) : 0;
            if (hasList && canUseFreightListAsVisualGtoProof(now, current)) {
                // Keep capture qualification alive even when an OEM never emits a fresh
                // UsageEvent after returning from the projection permission activity.
                recordVisualGtoForegroundEvidence(now, runtimeFreightCount, "live-freight-list");
            }
            if (!gtoForeground || !isCaptureReadyForAnalysis(now)) return;
            long sequence = selectionCoordinator.onFrameProcessed();

            if (hasList) {
                boolean semanticList = isFreightPageSemanticallyCertified(freightPageGeneration);
                if (semanticList) onFreightListVisibleAgain(now);
                lastFreightListSeenAt = now;
                fastMissingListFrames = 0;
                lastScreenState = semanticList ? "FREIGHT_LIST" : "FREIGHT_LIST_CANDIDATE";
                persistFreightRuntimeStatus(lastScreenState, semanticList ? runtimeFreightCount : 0, now, sequence);

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
                boolean candidateFromTouch = false;
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
                        candidateFromTouch = candidate != null;
                    }
                }

                // HF26: visual differences without a real touch are diagnostic only.
                // They can no longer create a pending freight selection.

                if (candidate != null && fastPendingSelectedRow < 0 && candidateFromTouch
                    && !coordinateEvidenceAgreesWithRow(candidate.row, fastTouchBaseline.buttons)) {
                    candidate = null;
                    prefs.edit()
                        .putString("lastEvent", "Toque detectado, mas a coordenada não correspondeu à linha visual; seleção bloqueada")
                        .apply();
                }

                if (candidate != null && fastPendingSelectedRow < 0) {
                    fastPendingFromTouchPulse = candidateFromTouch;
                    GtoFastVisualDetector.Frame baseline = candidateFromTouch && fastTouchBaseline != null
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

                // HF26: a visually missing/dark row is not a user action. Only an
                // active touch pulse may correlate the disappearing row below.

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

                int missingRequired = 1;
                if (fastPendingSelectedRow >= 0 && fastPendingFromTouchPulse
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
                markFreightListClosed(now);
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
            // An ImageReader queue fault must be visible and must never silently promote
            // or replace a row. Drop only transient selection evidence; the canonical
            // WAITING_FREIGHT state and durable operation context remain intact.
            clearFastPendingSelection();
            reportFrameProcessingError("fila ordenada da lista de fretes", queueError);
        } catch (Exception ex) {
            clearFastPendingSelection();
            reportFrameProcessingError("detector visual de fretes", ex);
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
            FreightOption frozenBaseline = stableFreightForRow(rowIndex);
            if (frozenBaseline != null) {
                frozenBaseline = copyFreightOption(frozenBaseline);
                markFrozenTouchBaselineEvidence(frozenBaseline);
            }
            return new FreightSelectionTransaction(
                rowIndex,
                panelCopy,
                offset,
                buttons,
                source == null ? "frame-lock" : source,
                selectionCoordinator.touchMarkerSequence(),
                prefs.getString("gtoTripSessionId", ""),
                preciseSelectionOcrGeneration,
                freightPageGeneration,
                frozenBaseline
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
        if (!fastTouchPulseActive && !fastPendingFromTouchPulse) {
            recordObserverEvent("VISUAL_PRESS_IGNORED", "row=" + (candidate.row + 1) + " · sem ação humana");
            return;
        }

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
                (fastPendingFromTouchPulse ? "touch-pulse-row-" : "visual-press-row-") + (candidate.row + 1))
            .putString("lastEvent", fastPendingFromTouchPulse
                ? "Frete selecionado na linha " + (candidate.row + 1) + " · aguardando fechamento da lista"
                : "Pressão visual detectada na linha " + (candidate.row + 1) + " · aguardando fechamento da lista")
            .apply();
    }

    private void finalizeFastVisualSelection() {
        int row = fastPendingSelectedRow;
        if (row < 0) return;
        boolean fromPulse = fastPendingFromTouchPulse;
        if (!fromPulse) {
            recordObserverIncident("SELECTION_BLOCKED_NO_HUMAN_ACTION", "fast row=" + (row + 1));
            clearFastPendingSelection();
            return;
        }

        FreightSelectionTransaction transaction = takePendingSelectionTransaction();
        if (transaction == null) {
            transaction = buildSelectionTransaction(
                row,
                fromPulse ? "touch-marker+frame-lock" : "frame-lock"
            );
        }
        if (transaction == null) {
            FreightOption stable = stableFreightForRow(row);
            String source = "touch-marker+frame-lock";
            clearFastPendingSelection();
            if (!ensureHumanSelectionConfirmedForFreight(row, source, freightPageGeneration, stable)) {
                rejectUncertifiedSelection(
                    row,
                    "O toque foi observado, mas não havia snapshot/lista certificada suficiente; nenhum frete foi presumido."
                );
                return;
            }
            enterFreightReview(stable, row, "O frete foi tocado, mas a página congelada ficou indisponível.", "");
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
        // HF26: list exit confirms the driver's action, but semantic freight evidence
        // still has to certify the frozen row before identity becomes CONFIRMED.
        persistSelectionIdentity(row, "TOUCH_LOCKED", transaction.source);
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
        if (STATE_CONFIRMING_FREIGHT.equals(state)) {
            return isFreightReviewPending() ? ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS : 80L;
        }
        if (STATE_TRIP_IN_PROGRESS.equals(state)) {
            return manualFinishCapturePending ? 120L : ACTIVE_TRIP_RESULT_FALLBACK_OCR_MS;
        }
        if (STATE_RESULT_DETECTED.equals(state) || STATE_AWAITING_BONUS.equals(state)) return 90L;
        return 1000L;
    }

    private int stableActiveTripFreightCount(GtoFastVisualDetector.Frame frame) {
        if (frame == null || frame.buttons == null || !frame.hasFreightList()) return 0;
        int count = frame.buttons.size();
        if (activeTripFreightListBaseline == null || !activeTripFreightListBaseline.hasFreightList()) {
            activeTripFreightListBaseline = frame;
            activeTripFreightListStableCount = count;
            return count;
        }

        float pageDistance = fastVisualDetector.pageDistance(activeTripFreightListBaseline, frame);
        if (pageDistance >= 0.024f) {
            activeTripFreightListBaseline = frame;
            activeTripFreightListStableCount = count;
            return count;
        }

        if (activeTripFreightListBaseline.buttons.size() == count + 1) {
            boolean isolatedMissingRow = fastVisualDetector.detectTemporarilyMissingPressedRow(
                activeTripFreightListBaseline, frame, captureHeight
            ) != null;
            int stabilized = GtoDeterministicFlowPolicy.stabilizeVisibleFreightCount(
                activeTripFreightListBaseline.buttons.size(), count, isolatedMissingRow
            );
            if (stabilized != count) {
                return Math.max(activeTripFreightListStableCount, stabilized);
            }
        }

        if (activeTripFreightListBaseline.buttons.size() == count) {
            activeTripFreightListBaseline = frame;
            activeTripFreightListStableCount = count;
        }
        return count;
    }

    private void clearActiveTripFreightListRuntime() {
        activeTripFreightListVisible = false;
        activeTripFreightListLastSeenAt = 0L;
        activeTripFreightListBaseline = null;
        activeTripFreightListStableCount = 0;
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
        boolean explicitReplacement = isExplicitFreightReplacementActive(now);

        // HF16 cancellation/reselection rule: when a real freight list reappears
        // during TRIP_IN_PROGRESS, treat it as a possible in-simulator cancellation.
        // The current freight remains durable until the driver actually touches a new
        // Aceitar row. This prevents a false list candidate from erasing a valid trip,
        // while allowing the next accepted freight to replace the cancelled one.
        if (STATE_TRIP_IN_PROGRESS.equals(activeState) && !explicitReplacement) {
            if (!freightList) {
                if (replacementFreightPressedRow >= 0
                    && replacementFreightTouchPending
                    && now - replacementFreightTouchAt <= CRITICAL_TOUCH_WINDOW_MS + 260L) {
                    return promoteReplacementFreightCandidateToWaiting(true);
                }
                if (replacementFreightCandidateArmed
                    && now - replacementFreightCandidateAt <= CRITICAL_TOUCH_WINDOW_MS + 260L) {
                    return false;
                }
                clearActiveTripFreightListRuntime();
                clearReplacementFreightCandidate();
                prefs.edit()
                    .putBoolean("activeTripFreightListVisible", false)
                    .putInt("freightCount", 0)
                    .putString("screenState", "TRIP")
                    .apply();
                return false;
            }

            armOrRefreshReplacementFreightCandidate(image, frame, now);
            if (activeTripFreightListSeenSince == 0L) activeTripFreightListSeenSince = now;
            activeTripFreightListFrames++;
            int count = frame.buttons == null ? 0 : frame.buttons.size();
            boolean stableList = GtoSimpleScreenDetectionPolicy.isStableFreightListReturn(
                activeState, true, activeTripFreightListFrames, now - activeTripFreightListSeenSince
            );
            activeTripFreightListVisible = stableList;
            // Visual geometry alone is only a candidate while a trip is active. Do not
            // announce or expose a freight list until a human action starts replacement;
            // the normal page OCR will then semantically certify Aceitar + value.
            prefs.edit()
                .putBoolean("activeTripFreightListVisible", stableList)
                .putInt("freightCount", 0)
                .putString("screenState", stableList ? "FREIGHT_LIST_CANDIDATE_AFTER_TRIP" : "TRIP")
                .apply();
            if (stableList) mainHandler.post(this::updateFreightTouchPulseSensor);

            if (replacementFreightTouchPending
                && now - replacementFreightTouchAt <= CRITICAL_TOUCH_WINDOW_MS + 260L
                && (stableList || replacementFreightPressedRow >= 0)) {
                return promoteReplacementFreightCandidateToWaiting(true);
            }
            // Detection has priority, but state replacement waits for the driver's new
            // Aceitar. Merely reopening the list never discards the previous freight.
            return stableList;
        }

        // A result already detected cannot be discarded by merely seeing the freight
        // list. Only an observed Receber action may finalize it; otherwise the result
        // remains preserved for explicit recovery.
        if (unresolvedResult && freightList) {
            int count = frame.buttons == null ? 0 : frame.buttons.size();
            prefs.edit()
                .putString("screenState", "FREIGHT_LIST_AFTER_RESULT")
                .putInt("freightCount", Math.max(0, count))
                .putString("lastEvent", "Lista detectada após resultado · entrega anterior preservada")
                .apply();
            if (hasRecentNormalResultActionEvidence(now)) {
                mainHandler.post(this::confirmNormalResultAutomatically);
            } else if ((resultTouchFallbackRequired || prefs.getBoolean("resultTouchFallbackRequired", false))
                && !(resultTouchFallbackContinuityBroken || prefs.getBoolean("resultTouchFallbackContinuityBroken", false))) {
                armResultTouchFallbackReady("FREIGHT_LIST_AFTER_RESULT");
            }
            return true;
        }
        if (unresolvedResult && !freightList) {
            activeTripFreightListSeenSince = 0L;
            activeTripFreightListFrames = 0;
            clearReplacementFreightCandidate();
            return false;
        }

        if (!freightList) {
            // If a real touch was already observed and the row-specific pressed state was
            // captured just before the list closed, that pair is stronger than waiting
            // for four static frames. Promote now and let the normal confirmation path
            // consume the preserved pre-touch snapshot.
            if (replacementFreightPressedRow >= 0
                && replacementFreightTouchPending
                && now - replacementFreightTouchAt <= CRITICAL_TOUCH_WINDOW_MS + 260L) {
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
        int observedFreightCount = STATE_TRIP_IN_PROGRESS.equals(activeState)
            ? stableActiveTripFreightCount(frame)
            : (frame.buttons == null ? 0 : frame.buttons.size());
        prefs.edit()
            .putString("screenState", "FREIGHT_LIST_DURING_TRIP")
            .putInt("freightCount", Math.max(0, observedFreightCount))
            .putInt("activeTripFreightListEvidenceFrames", activeTripFreightListFrames)
            .putLong("activeTripFreightListEvidenceSince", activeTripFreightListSeenSince)
            .putBoolean("replacementFreightCandidateArmed", replacementFreightCandidateArmed)
            .apply();

        if (replacementFreightTouchPending
            && now - replacementFreightTouchAt <= CRITICAL_TOUCH_WINDOW_MS + 260L
            && (activeTripFreightListFrames >= 2 || replacementFreightTouchPending)) {
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

        // Fresh IDLE/CANCELLED sessions may bootstrap from a stable list. A confirmed
        // active route is stricter: even after the explicit "Trocar frete atual" arm,
        // the old immutable freight is not discarded until the new Aceitar action is
        // evidenced by a touch marker or an isolated pressed-row transition.
        if (STATE_TRIP_IN_PROGRESS.equals(activeState)) {
            if (!explicitReplacement) return true;
            boolean selectedNewRow = replacementFreightTouchPending;
            if (!selectedNewRow) return true;
            return promoteReplacementFreightCandidateToWaiting(true);
        }
        boolean touchEvidence = replacementFreightTouchPending;
        return promoteReplacementFreightCandidateToWaiting(touchEvidence);
    }

    private boolean isReplaceableActiveSessionState(String state) {
        // Historical method name retained to avoid widening the patch surface. R3.26
        // intentionally excludes CONFIRMING_FREIGHT: confirmation owns its frozen
        // transaction and no live list frame may restart or replace it.
        return GtoDeterministicFlowPolicy.mayObserveFreightListOutsideWaiting(state);
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

    private boolean isExplicitFreightReplacementActive(long now) {
        if (!freightReplacementExplicitlyArmed) return false;
        if (!STATE_TRIP_IN_PROGRESS.equals(getTripState())) {
            clearExplicitFreightReplacement();
            return false;
        }
        if (freightReplacementExplicitlyArmedAt <= 0L
            || now - freightReplacementExplicitlyArmedAt > EXPLICIT_FREIGHT_REPLACEMENT_TIMEOUT_MS) {
            clearExplicitFreightReplacement();
            return false;
        }
        return true;
    }

    private void armExplicitFreightReplacement() {
        if (!STATE_TRIP_IN_PROGRESS.equals(getTripState())) return;
        // Arm intent first, then allow the detector to observe a jobs list. Requiring a
        // detected list before arming would reintroduce the same false-positive path this
        // guard is designed to eliminate.
        freightReplacementExplicitlyArmed = true;
        freightReplacementExplicitlyArmedAt = System.currentTimeMillis();
        clearReplacementFreightCandidate();
        prefs.edit()
            .putBoolean("freightReplacementExplicitlyArmed", true)
            .putLong("freightReplacementExplicitlyArmedAt", freightReplacementExplicitlyArmedAt)
            .putString("freightReplacementStatus", "PENDING")
            .putString("lastEvent", "Troca de frete autorizada explicitamente pelo motorista")
            .apply();
        recordObserverEvent("FREIGHT_REPLACEMENT_PENDING", "Aguardando novo toque em Aceitar");
        closeMenu();
        announceDriverStage(
            "FREIGHT_REPLACEMENT_ARMED",
            "Troca de frete autorizada. Abra a lista e selecione o novo frete.",
            3200L,
            true
        );
    }

    private void clearExplicitFreightReplacement() {
        freightReplacementExplicitlyArmed = false;
        freightReplacementExplicitlyArmedAt = 0L;
        if (prefs != null) {
            prefs.edit()
                .putBoolean("freightReplacementExplicitlyArmed", false)
                .remove("freightReplacementExplicitlyArmedAt")
                .remove("freightReplacementStatus")
                .apply();
        }
    }

    private boolean promoteReplacementFreightCandidateToWaiting(boolean fromTouch) {
        return promoteReplacementFreightCandidateToWaiting(fromTouch, -1f, -1f, -1f, -1f);
    }

    private boolean promoteReplacementFreightCandidateToWaiting(
        boolean fromTouch,
        float rawX,
        float rawY,
        float localX,
        float localY
    ) {
        String replacedState = getTripState();
        if (!isReplaceableActiveSessionState(replacedState) || !replacementFreightCandidateArmed) return false;
        if (STATE_TRIP_IN_PROGRESS.equals(replacedState)
            && !isExplicitFreightReplacementActive(System.currentTimeMillis())) {
            long now = System.currentTimeMillis();
            boolean stableReturnedList = GtoSimpleScreenDetectionPolicy.isStableFreightListReturn(
                replacedState,
                replacementFreightCandidateArmed,
                activeTripFreightListFrames,
                activeTripFreightListSeenSince > 0L ? now - activeTripFreightListSeenSince : 0L
            );
            boolean exactNewAccept = fromTouch && replacementFreightPressedRow >= 0;
            boolean newAcceptEvidence = fromTouch || replacementFreightTouchPending;
            if (!GtoSimpleScreenDetectionPolicy.mayReplaceCancelledTripOnNewAccept(
                replacedState, replacementFreightCandidateArmed, stableReturnedList, newAcceptEvidence, exactNewAccept
            )) {
                return false;
            }
        }

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
        if (STATE_TRIP_IN_PROGRESS.equals(replacedState)) clearExplicitFreightReplacement();

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
        recordObserverEvent("FREIGHT_REPLACEMENT_COMMITTED", "discardedSession=" + cancelledSessionId + " source=" + (fromTouch ? "touch" : "list-return"));

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
                .putString("screenState", "FREIGHT_LIST_CANDIDATE")
                .putInt("freightCount", 0)
                .putLong("freightStructureAt", savedAt)
                .apply();
        } else if (savedPanel != null && !savedPanel.isRecycled()) {
            savedPanel.recycle();
        }

        if (fromTouch) {
            // Arm the critical sequence exactly once. A direct queueFreightTouchMarker
            // call passes the real ACTION_OUTSIDE coordinates here; deferred promotions
            // use redacted coordinates and rely on the already observed pressed row.
            armFastTouchPulseOnCaptureThread(rawX, rawY, localX, localY);
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
                    ? "Entrega anterior não confirmada · nova lista detectada."
                    : (hadActiveSession
                        ? "Lista reaberta · selecione o novo frete."
                        : "Lista de fretes detectada · selecione um frete."),
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
        boolean freightList = buttons.size() >= 1 && buttons.size() <= 6;

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
        // HF26: geometry is only a freight-list candidate. Lifecycle/driver messaging
        // starts after semantic OCR certifies at least one same-row freight value.
        recordButtonFrame(frame, buttons, now);
        if (selectionProbeActive) evaluateSelectionProbe(frame, buttons, now);
        synchronized (freightFrameLock) {
            realtimeAcceptRects.clear();
            for (Rect rect : buttons) realtimeAcceptRects.add(new Rect(rect));
        }

        lastFreightListSeenAt = now;
        lastScreenState = isFreightPageSemanticallyCertified(freightPageGeneration)
            ? "FREIGHT_LIST" : "FREIGHT_LIST_CANDIDATE";
        prefs.edit()
            .putString("screenState", lastScreenState)
            .putInt("freightCount", "FREIGHT_LIST".equals(lastScreenState) ? buttons.size() : 0)
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
            // Never synthesize a pre-touch baseline from an arbitrary latest frame.
            // Without a frame that is provably pre-touch there is no before/after
            // evidence, so the correct result is to reject the selection.
            if (baseline == null || baseline.buttons.isEmpty()) return;

            selectionProbeBaseline = baseline;
            selectionProbeStartedAt = touchAt;
            selectionProbeActive = true;
            selectionProbeHumanActionObserved = true;
            selectionProbeHumanActionSource = "outside-touch+visual-buffer";
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
            .putString("selectionSource", "outside-touch+visual-buffer")
            .putString("lastEvent", "Toque detectado na lista; confirmando visualmente o frete selecionado.")
            .apply();
    }

    private void armPreciseTouchAcceptanceProbe(int row, long touchAt) {
        if (!STATE_WAITING_FREIGHT.equals(getTripState()) || row < 0) return;
        preciseTouchAcceptancePending = true;
        selectionProbeStartedAt = touchAt > 0L ? touchAt : System.currentTimeMillis();
        selectionProbeActive = true;
        selectionProbeHumanActionObserved = true;
        selectionProbeHumanActionSource = "precise-touch+visual-buffer";
        selectionProbeBestRow = -1;
        selectionProbeBestScore = 0f;
        selectionProbeBestMargin = 0f;
        selectionProbeEvidenceFrames = 0;
        selectionProbeLastEvidenceRow = -1;

        synchronized (freightFrameLock) {
            ButtonFrameSample baseline = null;
            for (int i = buttonFrameHistory.size() - 1; i >= 0; i--) {
                ButtonFrameSample candidate = buttonFrameHistory.get(i);
                if (candidate == null || candidate.buttons.isEmpty()) continue;
                if (selectionProbeStartedAt - candidate.at > 420L) break;
                if (candidate.at <= selectionProbeStartedAt + 40L) {
                    baseline = candidate.copy();
                    break;
                }
            }
            selectionProbeBaseline = baseline;

            recycleFrozenSelectionPanel();
            if (latestFreightPanelFrame != null && !latestFreightPanelFrame.isRecycled()) {
                frozenSelectionPanelFrame = latestFreightPanelFrame.copy(Bitmap.Config.ARGB_8888, false);
                frozenSelectionPanelOffsetX = latestFreightPanelOffsetX;
            }
            frozenSelectionButtons.clear();
            if (baseline != null && !baseline.buttons.isEmpty()) {
                for (Rect rect : baseline.buttons) frozenSelectionButtons.add(new Rect(rect));
            } else {
                for (Rect rect : realtimeAcceptRects) frozenSelectionButtons.add(new Rect(rect));
            }
        }
    }

    private boolean confirmPreciseTouchCandidateOnListExit(long now, boolean listDisappeared) {
        if (!preciseTouchAcceptancePending || !listDisappeared || !selectionProbeActive) return false;
        int candidateRow = preciseSelectedRow;
        if (candidateRow < 0) return false;
        long touchAt = preciseSelectedTouchAt > 0L ? preciseSelectedTouchAt : selectionProbeStartedAt;
        if (touchAt <= 0L || now - touchAt > SELECTION_PROBE_TIMEOUT_MS + 320L) return false;

        // One transient detector miss must not confirm a freight. Require the list to
        // have remained absent for a short stable window after the exact Accept touch.
        if (lastFreightListSeenAt > 0L && now - lastFreightListSeenAt < 72L) return false;

        int resolved = GtoSelectionIdentityPolicy.resolveExactTouchAfterTransition(
            candidateRow,
            Math.max(frozenSelectionButtons.size(), candidateRow + 1),
            true,
            selectionProbeBestRow
        );
        if (resolved != candidateRow) return false;
        commitVisualSelectedRow(candidateRow, 1f, 1f);
        return hasConfirmedSelectionIdentity();
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

        // HF26: a visually changed button without a touch is never a selection. Keep
        // this detector only as throttled diagnostics so scenery/camera changes cannot
        // create or restore a freight identity.
        long now = System.currentTimeMillis();
        if (now - lastVisualOnlyPressIgnoredAt >= 1200L) {
            lastVisualOnlyPressIgnoredAt = now;
            recordObserverEvent(
                "VISUAL_PRESS_IGNORED",
                "row=" + (bestRow + 1) + " score=" + best + " margin=" + margin + " · sem toque"
            );
        }
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
        if (confirmPreciseTouchCandidateOnListExit(now, listDisappeared)) return true;
        boolean enough = selectionProbeBestRow >= 0
            && selectionProbeBestScore >= 0.055f
            && selectionProbeBestMargin >= 0.018f;
        if (enough) {
            int row = selectionProbeBestRow;
            float score = selectionProbeBestScore;
            float margin = selectionProbeBestMargin;
            if (preciseTouchAcceptancePending && preciseSelectedRow >= 0 && row != preciseSelectedRow) {
                prefs.edit()
                    .putString("lastEvent", "Mudança visual em linha diferente do toque; seleção rejeitada.")
                    .apply();
            } else {
                commitVisualSelectedRow(row, score, margin);
                return hasConfirmedSelectionIdentity() || preciseSelectionOcrBusy;
            }
        }
        if (now - selectionProbeStartedAt >= SELECTION_PROBE_TIMEOUT_MS || listDisappeared) {
            // On a page arrow all Accept buttons remain visually unchanged. Do not guess.
            clearSelectionProbe();
            prefs.edit().putString("lastEvent", "Ação na lista sem confirmação compatível; nenhum frete foi presumido.").apply();
        }
        return false;
    }

    private void commitVisualSelectedRow(int row, float score, float margin) {
        if (!selectionProbeActive || row < 0) return;
        if (!GtoSelectionEvidencePolicy.mayConfirmSelection(
            selectionProbeHumanActionObserved || preciseTouchAcceptancePending,
            row,
            Math.max(1, frozenSelectionButtons.size())
        )) {
            recordObserverIncident("SELECTION_BLOCKED_NO_HUMAN_ACTION", "visual-buffer row=" + (row + 1));
            clearSelectionProbe();
            return;
        }
        if (preciseTouchAcceptancePending && preciseSelectedRow >= 0 && row != preciseSelectedRow) {
            prefs.edit()
                .putString("lastEvent", "Linha visual divergente do toque exato; seleção não confirmada.")
                .apply();
            return;
        }
        preciseTouchAcceptancePending = false;
        preciseSelectedRow = row;
        preciseSelectedTouchAt = System.currentTimeMillis();
        String humanSource = selectionProbeHumanActionSource == null || selectionProbeHumanActionSource.isEmpty()
            ? "touch-probe+visual-buffer" : selectionProbeHumanActionSource;
        selectionProbeActive = false;
        prefs.edit()
            .putInt("preciseSelectedRow", row)
            .putString("selectionSource", humanSource)
            .putString("lastEvent", "Ação em Aceitar confirmada · validando a linha " + (row + 1))
            .putBoolean("touchCaptureNeeded", false)
            .apply();
        // Keep identity TOUCH_LOCKED until the frozen row/page also proves this was a
        // semantic freight list. OCR/review cannot fabricate confirmation by itself.
        persistSelectionIdentity(row, "TOUCH_LOCKED", humanSource);
        FreightSelectionTransaction transaction = buildSelectionTransaction(row, humanSource);
        if (transaction != null) runPreciseSelectedRowOcr(transaction);
        else runPreciseSelectedRowOcr(row);
    }

    private void clearSelectionProbe() {
        boolean clearUnconfirmedTouch = preciseTouchAcceptancePending
            && prefs != null
            && "TOUCH_LOCKED".equals(prefs.getString("selectionIdentityStatus", ""));
        selectionProbeActive = false;
        selectionProbeHumanActionObserved = false;
        selectionProbeHumanActionSource = "";
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
        if (clearUnconfirmedTouch) {
            preciseTouchAcceptancePending = false;
            preciseSelectedRow = -1;
            preciseSelectedTouchAt = 0L;
            prefs.edit()
                .remove("selectionIdentityStatus")
                .remove("selectionIdentitySource")
                .remove("selectionIdentityAt")
                .remove("selectedFreightRow")
                .remove("preciseSelectedRow")
                .apply();
        }
    }

    private void recycleFrozenSelectionPanel() {
        if (frozenSelectionPanelFrame != null && !frozenSelectionPanelFrame.isRecycled()) {
            frozenSelectionPanelFrame.recycle();
        }
        frozenSelectionPanelFrame = null;
        frozenSelectionPanelOffsetX = 0;
    }

    private void persistSelectionIdentity(int row, String status, String source) {
        if (prefs == null || row < 0) return;
        String safeStatus = status == null ? "" : status.trim();
        String safeSource = source == null ? "" : source.trim();
        if ("CONFIRMED".equals(safeStatus) && !GtoSelectionEvidencePolicy.isHumanBackedSource(safeSource)) {
            recordObserverIncident(
                "SELECTION_CONFIRMATION_REJECTED",
                "row=" + (row + 1) + " source=" + safeSource + " · sem ação humana"
            );
            return;
        }
        prefs.edit()
            .putInt("preciseSelectedRow", row)
            .putInt("selectedFreightRow", row)
            .putString("selectionIdentityStatus", safeStatus)
            .putString("selectionIdentitySource", safeSource)
            .putLong("selectionIdentityAt", System.currentTimeMillis())
            .apply();
        recordObserverEvent("SELECTION_" + safeStatus, "row=" + (row + 1) + " source=" + safeSource);
    }

    private boolean hasConfirmedSelectionIdentity() {
        return prefs != null
            && "CONFIRMED".equals(prefs.getString("selectionIdentityStatus", ""))
            && prefs.getInt("selectedFreightRow", -1) >= 0
            && GtoSelectionEvidencePolicy.isHumanBackedSource(
                prefs.getString("selectionIdentitySource", prefs.getString("selectionSource", ""))
            );
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

            int hit = exactUniqueRowForTouch(x, y, buttons);
            if (hit < 0) return; // Require the touch to land in exactly one detected Aceitar box.

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
            persistSelectionIdentity(hit, "TOUCH_LOCKED", "precise-touch");
            // Touch identifies the candidate row but does not prove the GTO accepted it.
            // Stay in WAITING_FREIGHT long enough to observe either a same-row visual
            // change or the freight list disappearing. Only then can OCR/review begin.
            armPreciseTouchAcceptanceProbe(hit, eventTime);
            prefs.edit()
                .putString("lastEvent", "Toque em Aceitar registrado · aguardando confirmação da tela")
                .apply();
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

    /**
     * Returns a row only when the coordinate is inside exactly one real detected
     * Aceitar bounding box. No expansion and no nearest-row fallback are permitted.
     */
    private int exactUniqueRowForTouch(float x, float y, List<Rect> buttons) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || buttons == null || buttons.isEmpty()) return -1;
        int hit = -1;
        int px = Math.round(x);
        int py = Math.round(y);
        for (int i = 0; i < buttons.size(); i++) {
            Rect rect = buttons.get(i);
            if (rect != null && rect.contains(px, py)) {
                if (hit >= 0) return -1;
                hit = i;
            }
        }
        return hit;
    }

    /**
     * Coordinate evidence is advisory because Android may expose ACTION_OUTSIDE in a
     * different window/display space. If a candidate coordinate maps cleanly to one and
     * only one row, it must agree with the visual row. If no mapping is reliable, the
     * visual before/after path remains authoritative.
     */
    private boolean hasReliableOutsideTouchCoordinate() {
        DisplayMetrics screen = realDisplayMetrics();
        float sx = screen.widthPixels > 0 ? captureWidth / (float) screen.widthPixels : 1f;
        float sy = screen.heightPixels > 0 ? captureHeight / (float) screen.heightPixels : 1f;
        float[][] candidates = new float[][] {
            { fastTouchRawX, fastTouchRawY },
            { fastTouchLocalX, fastTouchLocalY },
            { fastTouchRawX * sx, fastTouchRawY * sy },
            { fastTouchLocalX * sx, fastTouchLocalY * sy }
        };
        for (float[] c : candidates) {
            if (isReliableOutsideCoordinate(c[0], c[1])) return true;
        }
        return false;
    }

    private boolean coordinateEvidenceAgreesWithRow(int row, List<Rect> buttons) {
        if (row < 0 || buttons == null || row >= buttons.size()) return false;
        boolean usable = hasReliableOutsideTouchCoordinate();
        int touchedRow = exactConsistentRowFromOutsideTouch(buttons);
        return GtoSelectionIdentityPolicy.resolveRow(
            touchedRow, usable, row, buttons.size()
        ) == row;
    }

    private int exactConsistentRowForTouch(
        float rawX,
        float rawY,
        float localX,
        float localY,
        List<Rect> buttons
    ) {
        if (buttons == null || buttons.isEmpty()) return -1;
        DisplayMetrics screen = realDisplayMetrics();
        float sx = screen.widthPixels > 0 ? captureWidth / (float) screen.widthPixels : 1f;
        float sy = screen.heightPixels > 0 ? captureHeight / (float) screen.heightPixels : 1f;
        float[][] candidates = new float[][] {
            { rawX, rawY },
            { localX, localY },
            { rawX * sx, rawY * sy },
            { localX * sx, localY * sy }
        };

        int exactRow = -1;
        for (float[] candidate : candidates) {
            if (!isReliableOutsideCoordinate(candidate[0], candidate[1])) continue;
            int hit = exactUniqueRowForTouch(candidate[0], candidate[1], buttons);
            if (hit < 0) continue;
            if (exactRow >= 0 && exactRow != hit) return -1;
            exactRow = hit;
        }
        return exactRow;
    }

    private int exactConsistentRowFromOutsideTouch(List<Rect> buttons) {
        return exactConsistentRowForTouch(
            fastTouchRawX, fastTouchRawY, fastTouchLocalX, fastTouchLocalY, buttons
        );
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

    private int semanticFreightAnchorRows(List<FreightOption> options) {
        if (options == null || options.isEmpty()) return 0;
        int anchors = 0;
        for (FreightOption option : options) {
            if (option == null || option.acceptRect == null || !option.acceptTextEvidence) continue;
            if (GtoFreightReviewPolicy.isManualValueValid(
                GtoFreightReviewPolicy.VALUE, option.offeredValue
            )) {
                anchors++;
            }
        }
        return anchors;
    }

    private void markFreightPageSemanticallyCertified(long generation, int anchorRows, int rowCount) {
        if (generation <= 0L || generation != freightPageGeneration) return;
        boolean firstCertificationForGeneration = !isFreightPageSemanticallyCertified(generation);
        freightSemanticCertifiedGeneration = generation;
        freightSemanticCertifiedAt = System.currentTimeMillis();
        freightSemanticAnchorRows = Math.max(0, anchorRows);
        prefs.edit()
            .putLong("freightSemanticCertifiedGeneration", generation)
            .putLong("freightSemanticConfirmedAt", freightSemanticCertifiedAt)
            .putInt("freightSemanticAnchorRows", freightSemanticAnchorRows)
            .putInt("freightCount", Math.max(0, rowCount))
            .putString("screenState", "FREIGHT_LIST")
            .apply();
        if (!firstCertificationForGeneration) return;
        onFreightListVisibleAgain(freightSemanticCertifiedAt);
        announceDriverStage(
            "FREIGHT_LIST_DETECTED",
            "Lista de fretes detectada · " + Math.max(1, rowCount) + " opção" + (rowCount == 1 ? "" : "ões") + ".",
            2600L,
            false
        );
    }

    private boolean isFreightPageSemanticallyCertified(long generation) {
        if (generation <= 0L) return false;
        long persistedGeneration = prefs == null ? -1L : prefs.getLong("freightSemanticCertifiedGeneration", -1L);
        return freightSemanticCertifiedGeneration == generation || persistedGeneration == generation;
    }

    private boolean selectedRowSemanticallyCertifiesFreight(FreightOption option) {
        if (option == null) return false;
        return GtoFreightSemanticCertificationPolicy.selectedRowCanCertify(
            option.acceptRect != null,
            option.acceptTextEvidence,
            option.cargo, option.originCompany, option.destination, option.km, option.offeredValue
        );
    }

    private boolean ensureHumanSelectionConfirmedForFreight(
        int row, String source, long pageGeneration, FreightOption evidence
    ) {
        String safeSource = source == null ? "" : source.trim();
        if (!GtoSelectionEvidencePolicy.isHumanBackedSource(safeSource)) {
            recordObserverIncident(
                "SELECTION_BLOCKED_NO_HUMAN_ACTION",
                "row=" + (row + 1) + " source=" + safeSource
            );
            return false;
        }
        boolean semantic = isFreightPageSemanticallyCertified(pageGeneration)
            || selectedRowSemanticallyCertifiesFreight(evidence);
        if (!semantic) {
            recordObserverIncident(
                "SELECTION_BLOCKED_UNCERTIFIED_LIST",
                "row=" + (row + 1) + " pageGeneration=" + pageGeneration
            );
            return false;
        }
        persistSelectionIdentity(row, "CONFIRMED", safeSource);
        if (!STATE_CONFIRMING_FREIGHT.equals(getTripState())) {
            setTripState(STATE_CONFIRMING_FREIGHT, "Frete identificado · validando dados");
        }
        return true;
    }

    private void rejectUncertifiedSelection(int row, String reason) {
        String safe = reason == null ? "Seleção sem evidência suficiente." : reason.trim();
        prefs.edit()
            .remove("selectionIdentityStatus")
            .remove("selectionIdentitySource")
            .remove("selectionIdentityAt")
            .remove("selectedFreightRow")
            .remove("preciseSelectedRow")
            .remove("selectionConfirmationStatus")
            .remove("pendingFreightReview")
            .remove("reviewRequiredField")
            .putBoolean("touchCaptureNeeded", true)
            .putString("lastEvent", safe)
            .apply();
        preciseSelectedRow = -1;
        preciseSelectedTouchAt = 0L;
        preciseTouchAcceptancePending = false;
        clearFastPendingSelection();
        clearSelectionProbe();
        if (!STATE_WAITING_FREIGHT.equals(getTripState())) {
            setTripState(STATE_WAITING_FREIGHT, "Aguardando uma lista de fretes confirmada");
        }
        recordObserverIncident("SELECTION_REJECTED", "row=" + (row + 1) + " · " + safe);
    }

    private void restoreWaitingAfterSelectionFailure(int rowIndex, String reason) {
        if (screenAnalysisPausedOutsideGto || !gtoForeground) {
            deferredPreciseFreightCommit = null;
            deferredSelectionFailureRow = rowIndex;
            deferredSelectionFailureReason = reason == null ? "" : reason.trim();
            prefs.edit()
                .putString("lastEvent", "Confirmação do frete pausada fora do GTO · estado preservado")
                .apply();
            return;
        }
        long now = System.currentTimeMillis();
        String safeReason = reason == null || reason.trim().isEmpty()
            ? "A linha selecionada ficou ilegível, encoberta ou as leituras divergiram."
            : reason.trim();
        prefs.edit()
            .putString("lastEvent", safeReason)
            .putString("selectionConfirmationStatus", "FAILED")
            .putString("selectionFailureReason", safeReason)
            .putLong("selectionFailureAt", now)
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
        recordObserverIncident("SELECTION_NOT_CONFIRMED", "row=" + (rowIndex + 1) + " · " + safeReason);
        setTripState(STATE_WAITING_FREIGHT, "Não foi possível confirmar todos os dados. Abra a lista e selecione novamente.");
        armFreightListReopenAfterSelectionFailure(now, safeReason);
        String driverMessage = "Frete não confirmado · " + safeReason
            + " Reabra a lista e selecione novamente.";
        announceDriverStage("FREIGHT_CONFIRMATION_FAILED", driverMessage, 7200L, true);
        updateNotification();
    }

    private FreightOption trustedReviewDraft(FreightOption option, int rowIndex) {
        FreightOption draft = new FreightOption();
        draft.rowIndex = rowIndex;
        if (option == null) return draft;
        // HF18: each field carries its own evidence. A global two-frame count must
        // never promote a field that was actually read only once.
        if (GtoFreightFieldEvidencePolicy.text(option.cargo, option.cargoVotes, option.cargoSelectedRowEvidence)) {
            draft.cargo = option.cargo.trim();
        }
        if (GtoFreightFieldEvidencePolicy.text(option.originCompany, option.originCompanyVotes, option.originCompanySelectedRowEvidence)) {
            draft.originCompany = option.originCompany.trim();
            draft.originCompanyEvidenceSource = option.originCompanyEvidenceSource;
        }
        // destinationCompany is optional metadata only; retain it when plausible but
        // never turn it into a review requirement.
        if (GtoFreightFieldEvidencePolicy.optionalMetadata(option.destinationCompany, option.destinationCompanyVotes, option.destinationCompanySelectedRowEvidence)) {
            draft.destinationCompany = option.destinationCompany == null ? "" : option.destinationCompany.trim();
        }
        if (GtoFreightFieldEvidencePolicy.text(option.destination, option.destinationVotes, option.destinationSelectedRowEvidence)) {
            draft.destination = option.destination.trim();
        }
        if (GtoFreightFieldEvidencePolicy.distance(option.km, option.kmVotes, option.kmSelectedRowEvidence)) {
            draft.km = canonicalKm(option.km);
        }
        if (GtoFreightFieldEvidencePolicy.money(option.offeredValue, option.valueVotes, option.valueSelectedRowEvidence)) {
            draft.offeredValue = canonicalMoney(option.offeredValue);
        }
        draft.origin = draft.originCompany;
        draft.companyRoute = draft.originCompany
            + (draft.destinationCompany.isEmpty() ? "" : " > " + draft.destinationCompany);
        draft.rawText = option.rawText == null ? "" : option.rawText;
        draft.acceptRect = option.acceptRect == null ? null : new Rect(option.acceptRect);
        return draft;
    }

    private void clearReviewField(FreightOption draft, String field) {
        if (draft == null || field == null) return;
        if (GtoFreightReviewPolicy.CARGO.equals(field)) draft.cargo = "";
        else if (GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(field)) draft.originCompany = "";
        else if (GtoFreightReviewPolicy.DESTINATION_COMPANY.equals(field)) draft.destinationCompany = "";
        else if (GtoFreightReviewPolicy.DESTINATION.equals(field)) draft.destination = "";
        else if (GtoFreightReviewPolicy.DISTANCE.equals(field)) draft.km = "";
        else if (GtoFreightReviewPolicy.VALUE.equals(field)) draft.offeredValue = "";
        draft.origin = draft.originCompany;
    }

    private String firstReviewField(FreightOption draft) {
        if (draft == null) return GtoFreightReviewPolicy.CARGO;
        return GtoFreightReviewPolicy.firstRequiredField(
            draft.cargo, draft.originCompany, draft.destinationCompany,
            draft.destination, draft.km, draft.offeredValue
        );
    }

    private String reviewFieldLabel(String field) {
        if (GtoFreightReviewPolicy.CARGO.equals(field)) return "carga";
        if (GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(field)) return "origem";
        if (GtoFreightReviewPolicy.DESTINATION_COMPANY.equals(field)) return "empresa de destino";
        if (GtoFreightReviewPolicy.DESTINATION.equals(field)) return "destino";
        if (GtoFreightReviewPolicy.DISTANCE.equals(field)) return "distância";
        if (GtoFreightReviewPolicy.VALUE.equals(field)) return "valor";
        if (GtoFreightReviewPolicy.LOCAL_INTEGRITY.equals(field)) return "integridade local";
        return "campo";
    }

    private void enterFreightReview(FreightOption candidate, int rowIndex, String reason, String forcedField) {
        if (rowIndex < 0) {
            restoreWaitingAfterSelectionFailure(rowIndex, reason);
            return;
        }
        // A touch identifies a candidate row; only the compatible visual transition
        // confirms that the GTO actually accepted it. OCR/review can never promote a
        // TOUCH_LOCKED candidate into a confirmed freight by itself.
        if (!hasConfirmedSelectionIdentity()) {
            prefs.edit()
                .putString("lastEvent", "Toque no frete preservado · aguardando confirmação visual da seleção")
                .apply();
            return;
        }
        if (!STATE_CONFIRMING_FREIGHT.equals(getTripState())) {
            setTripState(STATE_CONFIRMING_FREIGHT, "Frete selecionado · revisando dado pendente");
        }
        FreightOption draft = trustedReviewDraft(candidate, rowIndex);
        if (forcedField != null && !forcedField.isEmpty()) clearReviewField(draft, forcedField);
        String required = forcedField != null && !forcedField.isEmpty() ? forcedField : firstReviewField(draft);

        // HF26: REVIEW_REQUIRED is a last-mile correction path, never a way to build an
        // almost-empty freight by hand. If more than two operational fields are still
        // missing, retry the immutable selected row automatically before involving the
        // driver. Persistent low evidence invalidates the candidate instead of asking
        // cargo -> origin -> destination -> distance -> value sequentially.
        if (!required.isEmpty() && !GtoFreightReviewPolicy.LOCAL_INTEGRITY.equals(required)
            && !GtoFreightReviewEligibilityPolicy.mayAskDriver(
                draft.cargo, draft.originCompany, draft.destination, draft.km, draft.offeredValue
            )) {
            String source = prefs.getString("selectionIdentitySource", prefs.getString("selectionSource", ""));
            if (freightEvidenceRetryCount < 2 && GtoSelectionEvidencePolicy.isHumanBackedSource(source)) {
                FreightSelectionTransaction retry = buildSelectionTransaction(rowIndex, source + "+evidence-retry");
                if (retry != null) {
                    freightEvidenceRetryCount++;
                    recordObserverEvent(
                        "FREIGHT_EVIDENCE_RETRY",
                        "row=" + (rowIndex + 1) + " attempt=" + freightEvidenceRetryCount
                    );
                    mainHandler.postDelayed(() -> runPreciseSelectedRowOcr(retry), 260L);
                    return;
                }
            }
            prefs.edit()
                .remove("selectionIdentityStatus")
                .remove("selectionIdentitySource")
                .remove("selectionIdentityAt")
                .remove("selectedFreightRow")
                .remove("preciseSelectedRow")
                .putString("lastEvent", "Seleção descartada: dados automáticos insuficientes para revisão segura")
                .apply();
            recordObserverIncident(
                "FREIGHT_REVIEW_BLOCKED_LOW_EVIDENCE",
                "row=" + (rowIndex + 1) + " automaticFields="
                    + GtoFreightReviewEligibilityPolicy.automaticFieldCount(
                        draft.cargo, draft.originCompany, draft.destination, draft.km, draft.offeredValue
                    )
            );
            restoreWaitingAfterSelectionFailure(
                rowIndex,
                "Não foi possível ler dados suficientes do frete com segurança; nenhum frete foi presumido."
            );
            return;
        }
        freightEvidenceRetryCount = 0;
        freightConfirmationWatchdogGeneration++;
        preciseSelectedRow = rowIndex;
        preciseSelectionOcrBusy = false;
        String safeReason = reason == null ? "" : reason.trim();
        SharedPreferences.Editor e = prefs.edit()
            .putInt("preciseSelectedRow", rowIndex)
            .putInt("selectedFreightRow", rowIndex)
            .putString("selectionConfirmationStatus", "REVIEW_REQUIRED")
            .putBoolean("pendingFreightReview", true)
            .putString("reviewRequiredField", required)
            .putString("reviewReason", safeReason)
            .putString("reviewCargo", draft.cargo)
            .putString("reviewOriginCompany", draft.originCompany)
            .putString("reviewDestinationCompany", draft.destinationCompany)
            .putString("reviewDestination", draft.destination)
            .putString("reviewKm", draft.km)
            .putString("reviewValue", draft.offeredValue)
            .putString("reviewRawText", draft.rawText == null ? "" : draft.rawText)
            .putString("lastEvent", "Frete preservado · falta revisar " + reviewFieldLabel(required));
        if (!draft.cargo.isEmpty()) e.putString("reviewCargoSource", "OCR");
        if (!draft.originCompany.isEmpty()) e.putString("reviewOriginCompanySource",
            draft.originCompanyEvidenceSource == null || draft.originCompanyEvidenceSource.isEmpty() ? "OCR" : draft.originCompanyEvidenceSource);
        if (!draft.destinationCompany.isEmpty()) e.putString("reviewDestinationCompanySource", "OCR");
        if (!draft.destination.isEmpty()) e.putString("reviewDestinationSource", "OCR");
        if (!draft.km.isEmpty()) e.putString("reviewKmSource", "OCR");
        if (!draft.offeredValue.isEmpty()) e.putString("reviewValueSource", "OCR");
        e.apply();
        persistFreightFieldStatuses(draft, required);
        if (!required.isEmpty()) {
            recordObserverIncident(
                "FREIGHT_FIELD_REVIEW",
                "row=" + (rowIndex + 1) + " pending=" + required + (safeReason.isEmpty() ? "" : " · " + safeReason)
            );
        }
        if (required.isEmpty()) {
            // All fields are independently usable. Keep the selected-row identity
            // preserved and perform the durable lock through the review commit path
            // instead of recursively re-entering precise OCR confirmation.
            commitReviewedFreight(draft);
            return;
        }
        String reviewMessage = GtoFreightReviewPolicy.LOCAL_INTEGRITY.equals(required)
            ? "Frete preservado. Falta apenas confirmar a integridade local."
            : (GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(required)
                ? "Origem não confirmada. Informe a origem do frete."
                : "Frete preservado. Confirme apenas " + reviewFieldLabel(required) + " na bolinha NVU.");
        announceDriverStage(
            "FREIGHT_REVIEW_REQUIRED",
            reviewMessage,
            6800L,
            true
        );
        updateNotification();
        mainHandler.post(this::updateFreightTouchPulseSensor);
        if (menuView != null) mainHandler.post(this::refreshMenuContents);
    }

    private FreightOption freightReviewFromPrefs() {
        FreightOption option = new FreightOption();
        option.rowIndex = prefs.getInt("selectedFreightRow", preciseSelectedRow);
        option.cargo = prefs.getString("reviewCargo", "").trim();
        option.originCompany = prefs.getString("reviewOriginCompany", "").trim();
        option.destinationCompany = prefs.getString("reviewDestinationCompany", "").trim();
        option.destination = prefs.getString("reviewDestination", "").trim();
        option.km = prefs.getString("reviewKm", "").trim();
        option.offeredValue = prefs.getString("reviewValue", "").trim();
        option.rawText = prefs.getString("reviewRawText", "");
        option.origin = option.originCompany;
        option.companyRoute = option.originCompany
            + (option.destinationCompany.isEmpty() ? "" : " > " + option.destinationCompany);
        return option;
    }

    private void applyManualFreightReviewField(String field, String rawValue) {
        if (!isFreightReviewPending()) return;
        String value = GtoFreightReviewPolicy.preserveLiteralManualText(rawValue);
        if (!GtoFreightReviewPolicy.isManualValueValid(field, value)) {
            showStatusChip("Valor inválido para " + reviewFieldLabel(field) + ". O frete continua preservado.", 3800L);
            return;
        }
        activeReviewInputDraft = "";
        activeReviewInputField = "";
        activeReviewInput = null;
        SharedPreferences.Editor e = prefs.edit();
        if (GtoFreightReviewPolicy.CARGO.equals(field)) e.putString("reviewCargo", value).putString("reviewCargoSource", "MANUAL_DRIVER");
        else if (GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(field)) e.putString("reviewOriginCompany", value).putString("reviewOriginCompanySource", "MANUAL_DRIVER");
        else if (GtoFreightReviewPolicy.DESTINATION_COMPANY.equals(field)) e.putString("reviewDestinationCompany", value).putString("reviewDestinationCompanySource", "MANUAL_DRIVER");
        else if (GtoFreightReviewPolicy.DESTINATION.equals(field)) e.putString("reviewDestination", value).putString("reviewDestinationSource", "MANUAL_DRIVER");
        else if (GtoFreightReviewPolicy.DISTANCE.equals(field)) e.putString("reviewKm", canonicalKm(value)).putString("reviewKmSource", "MANUAL_DRIVER");
        else if (GtoFreightReviewPolicy.VALUE.equals(field)) e.putString("reviewValue", canonicalMoney(value)).putString("reviewValueSource", "MANUAL_DRIVER");
        e.apply();

        FreightOption current = freightReviewFromPrefs();
        String next = firstReviewField(current);
        if (next.isEmpty()) {
            commitReviewedFreight(current);
            return;
        }
        prefs.edit()
            .putString("reviewRequiredField", next)
            .putString("lastEvent", "Campo confirmado · falta revisar " + reviewFieldLabel(next))
            .apply();
        persistFreightFieldStatuses(current, next);
        recordObserverEvent("FIELD_CONFIRMED", reviewFieldLabel(field) + " source=MANUAL_DRIVER");
        showStatusChip("Campo salvo. Agora confirme " + reviewFieldLabel(next) + ".", 2800L);
        if (menuView != null) refreshMenuContents();
        updateNotification();
    }

    private void commitReviewedFreight(FreightOption selected) {
        if (selected == null || !STATE_CONFIRMING_FREIGHT.equals(getTripState())) return;
        if (!hasConfirmedSelectionIdentity()) {
            rejectUncertifiedSelection(
                selected.rowIndex,
                "Revisão bloqueada: a seleção não possui evidência humana válida."
            );
            return;
        }
        String missing = firstReviewField(selected);
        if (!missing.isEmpty()) {
            prefs.edit().putString("reviewRequiredField", missing).apply();
            return;
        }
        selected.origin = selected.originCompany;
        selected.km = canonicalKm(selected.km);
        selected.offeredValue = canonicalMoney(selected.offeredValue);
        // Manual review records provenance in SharedPreferences. Never manufacture OCR
        // votes/consensus to satisfy an automatic confidence gate.
        String identitySource = prefs.getString("selectionIdentitySource", prefs.getString("selectionSource", ""));
        if (!GtoSelectionEvidencePolicy.isHumanBackedSource(identitySource)) {
            rejectUncertifiedSelection(selected.rowIndex, "Revisão bloqueada: origem da seleção não é humana.");
            return;
        }
        String reviewedIdentitySource = identitySource + "+field-review";
        String json = freightOptionToJson(selected).toString();
        prefs.edit()
            .putString("selectedFreight", json)
            .putString("selectedFreightSummary", selected.summary())
            .putInt("selectedFreightRow", selected.rowIndex)
            .putString("selectedOrigin", selected.origin)
            .putString("selectedOriginSource", prefs.getString("reviewOriginCompanySource", "OCR"))
            .putString("selectedDestination", selected.destination)
            .putString("selectedOriginCompany", selected.originCompany)
            .putString("selectedDestinationCompany", selected.destinationCompany)
            .putString("selectedCargo", selected.cargo)
            .putString("selectedKm", selected.km)
            .putString("selectedValue", selected.offeredValue)
            .putString("selectedCargoSource", prefs.getString("reviewCargoSource", "OCR"))
            .putString("selectedOriginCompanySource", prefs.getString("reviewOriginCompanySource", "OCR"))
            .putString("selectedDestinationCompanySource", prefs.getString("reviewDestinationCompanySource", "OCR"))
            .putString("selectedDestinationSource", prefs.getString("reviewDestinationSource", "OCR"))
            .putString("selectedKmSource", prefs.getString("reviewKmSource", "OCR"))
            .putString("selectedValueSource", prefs.getString("reviewValueSource", "OCR"))
            .putString("selectionConfirmationStatus", "CONFIRMED")
            .putString("selectionSource", reviewedIdentitySource)
            .putBoolean("touchCaptureNeeded", false)
            .apply();
        persistSelectionIdentity(selected.rowIndex, "CONFIRMED", reviewedIdentitySource);
        persistFreightFieldStatuses(selected, "");
        recordObserverEvent("FREIGHT_FIELDS_CONFIRMED", "row=" + (selected.rowIndex + 1) + " source=review");
        if (!GtoAutoTripSync.lockSelectedFreight(this, prefs)) {
            prefs.edit()
                .putString("selectionConfirmationStatus", "REVIEW_REQUIRED")
                .putBoolean("pendingFreightReview", true)
                .putString("reviewRequiredField", GtoFreightReviewPolicy.LOCAL_INTEGRITY)
                .putString("lastEvent", "Frete preservado · falha ao bloquear integridade local")
                .apply();
            recordObserverIncident("LOCAL_INTEGRITY_LOCK", "Falha ao bloquear snapshot do frete revisado");
            showStatusChip("Os dados foram preservados, mas a integridade local ainda não foi bloqueada. Tente salvar novamente.", 5200L);
            return;
        }
        transitionConfirmedFreightToTripInProgress();
    }

    private void transitionConfirmedFreightToTripInProgress() {
        if (!hasConfirmedSelectionIdentity()) {
            int row = prefs == null ? -1 : prefs.getInt("selectedFreightRow", -1);
            rejectUncertifiedSelection(row, "Transição para viagem bloqueada: seleção sem prova humana confirmada.");
            return;
        }
        activeReviewInputDraft = "";
        activeReviewInputField = "";
        activeReviewInput = null;
        prefs.edit()
            .putBoolean("pendingFreightReview", false)
            .remove("reviewRequiredField")
            .remove("reviewReason")
            .remove("reviewCargo")
            .remove("reviewOriginCompany")
            .remove("reviewDestinationCompany")
            .remove("reviewDestination")
            .remove("reviewKm")
            .remove("reviewValue")
            .remove("reviewRawText")
            .remove("reviewCargoSource")
            .remove("reviewOriginCompanySource")
            .remove("reviewDestinationCompanySource")
            .remove("reviewDestinationSource")
            .remove("reviewKmSource")
            .remove("reviewValueSource")
            .putString("selectionConfirmationStatus", "CONFIRMED")
            .apply();
        pendingFreightSelection = null;
        visualFreightSelection = null;
        visualSelectionUntil = 0L;
        freightListReopenPending = false;
        freightListCycleClosed = false;
        prefs.edit().putBoolean("freightListReopenPending", false).apply();
        setTripState(STATE_TRIP_IN_PROGRESS, "Frete confirmado com sucesso");
        announceDriverStage(
            "TRIP_IN_PROGRESS",
            "Frete identificado. Tudo preparado, podemos partir!",
            4200L,
            true
        );
        promotePendingResultAfterFreightReview();
    }

    private void promotePendingResultAfterFreightReview() {
        if (!STATE_TRIP_IN_PROGRESS.equals(getTripState())) return;
        if (prefs.getBoolean("pendingBonusDuringFreightReview", false)) {
            prefs.edit().putBoolean("pendingBonusDuringFreightReview", false).apply();
            setTripState(STATE_REJECTED_BONUS, "Entrega com anúncio/bônus detectada");
            return;
        }
        if (!prefs.getBoolean("pendingResultDuringFreightReview", false)) return;
        prefs.edit().putBoolean("pendingResultDuringFreightReview", false).apply();
        setTripState(STATE_RESULT_DETECTED, "Entrega detectada durante revisão do frete");
        if (prefs.getBoolean("resultReceiveLatched", false)) confirmNormalResultAutomatically();
    }

    private void runPreciseSelectedRowOcr(int rowIndex) {
        String source = prefs == null ? "" : prefs.getString("selectionSource", "");
        if (!GtoSelectionEvidencePolicy.isHumanBackedSource(source)) {
            rejectUncertifiedSelection(rowIndex, "Leitura de frete bloqueada: não existe ação humana vinculada à seleção.");
            return;
        }
        FreightSelectionTransaction transaction = buildSelectionTransaction(rowIndex, source);
        if (transaction == null) {
            FreightOption stable = stableFreightForRow(rowIndex);
            if (!ensureHumanSelectionConfirmedForFreight(rowIndex, source, freightPageGeneration, stable)) {
                rejectUncertifiedSelection(rowIndex, "A seleção não pôde ser associada a uma lista de fretes certificada.");
                return;
            }
            if (isStableFreightSafeToCommit(stable)) commitPreciseFreight(stable);
            else enterFreightReview(stable, rowIndex, freightSafetyFailure(stable,
                "Frete selecionado; faltam dados legíveis para concluir a confirmação."), "");
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
        final String transactionSource = transaction.source == null ? "" : transaction.source;
        final long transactionPageGeneration = transaction.pageGeneration;
        if (selectionTextRecognizer == null) {
            int reviewRow = transaction.rowIndex;
            FreightOption frozen = transaction.baselineOption == null
                ? null : copyFreightOption(transaction.baselineOption);
            transaction.close();
            if (!ensureHumanSelectionConfirmedForFreight(reviewRow, transactionSource, transactionPageGeneration, frozen)) {
                rejectUncertifiedSelection(reviewRow, "OCR indisponível e a lista não estava semanticamente certificada; nenhum frete foi presumido.");
                return;
            }
            enterFreightReview(frozen, reviewRow, "OCR local indisponível; a linha selecionada foi preservada.", "");
            return;
        }
        if (transaction.pageGeneration != freightPageGeneration) {
            // The immutable pre-touch snapshot remains authoritative even if the live
            // list page advanced after Aceitar. Never discard the selected row because
            // a later frame belongs to another page.
            prefs.edit()
                .putBoolean("selectionSnapshotPageAdvanced", true)
                .putLong("selectionSnapshotPageAdvancedAt", System.currentTimeMillis())
                .apply();
        }
        // A very fast Aceitar can arrive before the independent page OCR has been
        // committed. A page read may still run as supporting evidence, but the immutable
        // pre-touch selected-row snapshot remains primary and is never overwritten by a
        // later live page or by page-majority text.
        FreightOption canonicalBeforeSelection = transaction.baselineOption == null
            ? null : copyFreightOption(transaction.baselineOption);
        if (!isStableFreightSafeToCommit(canonicalBeforeSelection) && !ocrBusy.get()) {
            scheduleFreightPageOcr(
                transaction.pageGeneration,
                transaction.panelFrame,
                transaction.panelOffsetX,
                transaction.buttons,
                System.currentTimeMillis(),
                true
            );
        }
        // ML Kit can run two recognizers in parallel, but that creates memory pressure
        // and a race between page stabilization and row confirmation on weak devices.
        // Serialize both passes; the frozen selection bitmap remains owned by the
        // transaction while we wait.
        if (preciseSelectionOcrBusy || focusedFreightConflictRetryBusy || ocrBusy.get()) {
            long waitedMs = System.currentTimeMillis() - transaction.createdAt;
            if (waitedMs >= PRECISE_OCR_BUSY_WAIT_TIMEOUT_MS) {
                int row = transaction.rowIndex;
                FreightOption frozen = transaction.baselineOption == null
                    ? null : copyFreightOption(transaction.baselineOption);
                transaction.close();
                // Do not commit a live page-history row after a timeout. The frozen
                // pre-touch page may seed review only when the human action and the
                // freight page were independently certified.
                if (!ensureHumanSelectionConfirmedForFreight(row, transactionSource, transactionPageGeneration, frozen)) {
                    rejectUncertifiedSelection(row, "Timeout de OCR sem evidência semântica suficiente; nenhum frete foi presumido.");
                    return;
                }
                enterFreightReview(frozen, row, freightSafetyFailure(frozen,
                    "A leitura da linha selecionada demorou além do seguro; confirme somente o campo necessário."), "");
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
            FreightOption frozen = transaction.baselineOption == null
                ? null : copyFreightOption(transaction.baselineOption);
            transaction.close();
            if (!ensureHumanSelectionConfirmedForFreight(rowIndex, transactionSource, transactionPageGeneration, frozen)) {
                rejectUncertifiedSelection(rowIndex, "Imagem congelada indisponível e lista não certificada; nenhum frete foi presumido.");
                return;
            }
            enterFreightReview(frozen, rowIndex, freightSafetyFailure(frozen,
                "Frete selecionado, mas a imagem congelada não pôde ser copiada."), "");
            return;
        }
        buttons.sort(Comparator.comparingInt(Rect::centerY));
        if (rowIndex < 0 || rowIndex >= buttons.size()) {
            panelCopy.recycle();
            FreightOption frozen = transaction.baselineOption == null
                ? null : copyFreightOption(transaction.baselineOption);
            transaction.close();
            if (!ensureHumanSelectionConfirmedForFreight(rowIndex, transactionSource, transactionPageGeneration, frozen)) {
                rejectUncertifiedSelection(rowIndex, "Geometria da linha inválida e lista não certificada; nenhum frete foi presumido.");
                return;
            }
            enterFreightReview(frozen, rowIndex, freightSafetyFailure(frozen,
                "Frete selecionado, mas os campos da linha precisam de revisão."), "");
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
        final FreightOption frozenSelectedPageBaseline = transaction.baselineOption == null
            ? null : copyFreightOption(transaction.baselineOption);
        final long scheduledSelectionGeneration = transaction.generation;
        final String scheduledSelectionSessionId = transaction.sessionId;

        selectionTextRecognizer.process(InputImage.fromBitmap(bitmapForOcr, 0))
            .addOnSuccessListener(text -> {
                if (!isCurrentPreciseSelectionOcr(scheduledSelectionGeneration, scheduledSelectionSessionId)) return;
                List<OcrLine> lines = new ArrayList<>();
                FreightOption stableOriginHint = frozenSelectedPageBaseline == null
                    ? null : copyFreightOption(frozenSelectedPageBaseline);
                String destinationCompanyHint = stableOriginHint == null
                    ? ""
                    : stableOriginHint.destinationCompany;
                GtoOriginGeometryPolicy.Result geometricOriginFallback = GtoOriginGeometryPolicy.Result.none();
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
                        if (!geometricOriginFallback.strong) {
                            float relY = (mapped.centerY() - top) / (float) Math.max(1, bottom - top);
                            if (relY >= 0.30f && relY <= 0.68f
                                && extractKmDigits(value).isEmpty()
                                && extractMoneyValue(value).isEmpty()
                                && !normalize(value).contains("aceitar")) {
                                GtoOriginGeometryPolicy.Result inferred = inferOriginCompanyFromMlLine(
                                    line, destinationCompanyHint
                                );
                                if (inferred.strong && looksLikeEntityName(inferred.value)) {
                                    geometricOriginFallback = inferred;
                                }
                            }
                        }
                    }
                }
                if (!geometricOriginFallback.strong) {
                    geometricOriginFallback = inferOriginCompanyFromSelectedRowLines(
                        lines, destinationCompanyHint, top, bottom
                    );
                }
                List<FreightOption> parsed = parseFreightOptions(lines, Collections.singletonList(exactButton));
                FreightOption selected = parsed.isEmpty() ? null : parsed.get(0);
                if (selected == null) {
                    FreightOption frozen = frozenSelectedPageBaseline == null
                        ? null : copyFreightOption(frozenSelectedPageBaseline);
                    if (!ensureHumanSelectionConfirmedForFreight(
                        exactRow, transactionSource, transactionPageGeneration, frozen
                    )) {
                        rejectUncertifiedSelection(
                            exactRow,
                            "A ação do motorista foi detectada, mas a imagem não comprovou uma lista de fretes; nenhum frete foi presumido."
                        );
                        return;
                    }
                    enterFreightReview(frozen, exactRow,
                        "A linha selecionada ficou parcialmente ilegível ou encoberta; a seleção foi preservada.", "");
                    return;
                } else {
                    selected.rowIndex = exactRow;
                    selected.acceptRect = new Rect(exactButton);
                    selected.acceptCenterY = exactButton.centerY();
                    selected.rowTop = top;
                    selected.rowBottom = bottom;
                    refinePreciseRowFields(selected, lines, top, bottom);
                    markSelectedRowFieldEvidence(selected);
                    if (geometricOriginFallback.strong
                        && looksLikeEntityName(geometricOriginFallback.value)
                        && (selected.originCompany.isEmpty()
                            || GtoFreightTextGuard.sameVisibleText(
                                selected.originCompany, geometricOriginFallback.value
                            ))) {
                        if (selected.originCompany.isEmpty()) {
                            selected.originCompany = geometricOriginFallback.value;
                        }
                        // Independent evidence channel on the immutable selected-row
                        // snapshot. Do not manufacture a second OCR vote.
                        selected.originCompanySelectedRowEvidence = true;
                        selected.originCompanyEvidenceSource = geometricOriginFallback.source;
                        selected.origin = selected.originCompany;
                        selected.companyRoute = selected.originCompany
                            + (selected.destinationCompany.isEmpty() ? "" : " > " + selected.destinationCompany);
                        prefs.edit()
                            .putString("lastOriginExtractionSource", geometricOriginFallback.source)
                            .putLong("lastOriginExtractionAt", System.currentTimeMillis())
                            .apply();
                    }

                    if (!ensureHumanSelectionConfirmedForFreight(
                        exactRow, transactionSource, transactionPageGeneration, selected
                    )) {
                        FreightOption semanticFallback = frozenSelectedPageBaseline == null
                            ? null : copyFreightOption(frozenSelectedPageBaseline);
                        if (!ensureHumanSelectionConfirmedForFreight(
                            exactRow, transactionSource, transactionPageGeneration, semanticFallback
                        )) {
                            rejectUncertifiedSelection(
                                exactRow,
                                "A linha tocada não apresentou evidência semântica suficiente de frete; nenhum frete foi presumido."
                            );
                            return;
                        }
                    }

                    FreightOption stableSamePage = frozenSelectedPageBaseline == null
                        ? null : copyFreightOption(frozenSelectedPageBaseline);
                    if (stableSamePage == null && isStableFreightSafeToCommit(selected)) {
                        commitPreciseFreight(selected);
                        return;
                    }
                    FreightOption canonicalCandidate = mergeVerifiedPreciseWithStable(selected, stableSamePage);
                    boolean initialConflict = stableSamePage != null
                        && (hasUnresolvedDestinationOneEditConflict(selected, stableSamePage)
                            || hasCriticalFreightConflict(selected, stableSamePage));
                    if (initialConflict) {
                        // HF25: a disagreement no longer jumps straight to driver review.
                        // Re-read the immutable selected-row ROI (up to two image scales),
                        // and accept a literal field only when the retry agrees with one of
                        // the two initial reads. This applies equally to cargo, origin,
                        // destination, distance and value; no fuzzy correction is introduced.
                        prefs.edit()
                            .putString("lastFreightSecondaryReadDiff", freightConflictSummary(selected, stableSamePage))
                            .putLong("lastFreightSecondaryReadDiffAt", System.currentTimeMillis())
                            .apply();
                        scheduleFocusedFreightConflictRetry(
                            bitmapForOcr, scale, screenLeft, screenTop, exactButton, exactRow,
                            top, bottom, selected, stableSamePage,
                            scheduledSelectionGeneration, scheduledSelectionSessionId
                        );
                        return;
                    }

                    boolean canonicalSafe = isStableFreightSafeToCommit(canonicalCandidate);
                    if (!GtoFreightSelectionPolicy.canCommitCanonicalRow(
                        exactRow,
                        canonicalCandidate.rowIndex,
                        canonicalSafe,
                        selected.km,
                        selected.offeredValue,
                        canonicalCandidate.km,
                        canonicalCandidate.offeredValue
                    )) {
                        prefs.edit()
                            .putString("lastFreightConflict", freightConflictSummary(selected, canonicalCandidate))
                            .putLong("lastFreightConflictAt", System.currentTimeMillis())
                            .apply();
                        String forcedField = differentNumericValue(selected.km, canonicalCandidate.km)
                            ? GtoFreightReviewPolicy.DISTANCE
                            : (differentMoneyValue(selected.offeredValue, canonicalCandidate.offeredValue)
                                ? GtoFreightReviewPolicy.VALUE : "");
                        enterFreightReview(canonicalCandidate, exactRow,
                            "A linha selecionada foi preservada; confirme somente o campo que permaneceu sem evidência suficiente.",
                            forcedField);
                        return;
                    }
                    selected = canonicalCandidate;
                }

                if (isStableFreightSafeToCommit(selected)) {
                    commitPreciseFreight(selected);
                } else {
                    // Never replace the selected-row snapshot with whatever happens to be
                    // the live page history now. Missing fields become REVIEW_REQUIRED.
                    enterFreightReview(selected, exactRow,
                        freightSafetyFailure(selected,
                            "Frete da linha " + (exactRow + 1) + " detectado; faltam apenas os campos que não ficaram legíveis."), "");
                }
            })
            .addOnFailureListener(error -> {
                if (!isCurrentPreciseSelectionOcr(scheduledSelectionGeneration, scheduledSelectionSessionId)) return;
                FreightOption frozen = frozenSelectedPageBaseline == null
                    ? null : copyFreightOption(frozenSelectedPageBaseline);
                // A failed selected-row OCR must never auto-commit page history. Review is
                // allowed only when the human action and the frozen freight page were
                // already semantically certified.
                if (!ensureHumanSelectionConfirmedForFreight(
                    exactRow, transactionSource, transactionPageGeneration, frozen
                )) {
                    rejectUncertifiedSelection(
                        exactRow,
                        "Falha de OCR sem certificação semântica da lista; nenhum frete foi presumido."
                    );
                    return;
                }
                enterFreightReview(frozen, exactRow,
                    "Falha temporária de OCR (" + error.getClass().getSimpleName() + "); a linha selecionada foi preservada.", "");
            })
            .addOnCompleteListener(task -> {
                if (!bitmapForOcr.isRecycled()) bitmapForOcr.recycle();
                if (isCurrentPreciseSelectionOcr(scheduledSelectionGeneration, scheduledSelectionSessionId)) {
                    preciseSelectionOcrBusy = false;
                    clearSelectionProbe();
                }
            });
    }

    private GtoOriginGeometryPolicy.Result inferOriginCompanyFromSelectedRowLines(
        List<OcrLine> lines,
        String destinationCompanyHint,
        int rowTop,
        int rowBottom
    ) {
        if (lines == null || lines.isEmpty()) return GtoOriginGeometryPolicy.Result.none();
        List<GtoOriginGeometryPolicy.RowLine> rowLines = new ArrayList<>();
        for (OcrLine line : lines) {
            if (line == null || line.rect == null || line.text == null) continue;
            rowLines.add(new GtoOriginGeometryPolicy.RowLine(
                cleanOcrLabel(line.text),
                line.rect.top,
                line.rect.bottom,
                line.rect.left,
                line.rect.right
            ));
        }
        GtoOriginGeometryPolicy.Result result = GtoOriginGeometryPolicy.inferFromRowLines(
            rowLines, destinationCompanyHint, rowTop, rowBottom
        );
        return result.strong && looksLikeEntityName(result.value)
            ? result
            : GtoOriginGeometryPolicy.Result.none();
    }

    private void markFrozenTouchBaselineEvidence(FreightOption option) {
        if (option == null) return;
        // HF25: once the button row is frozen at the touch boundary, every literal valid
        // field already present in that immutable row is selected-row evidence. This does
        // not manufacture an OCR vote and prevents timeout/error paths from asking the
        // driver to retype a value that was already captured from the exact touched row.
        markSelectedRowFieldEvidence(option);
        if (option.originCompanySelectedRowEvidence
            && (option.originCompanyEvidenceSource == null || option.originCompanyEvidenceSource.isEmpty()
                || "SELECTED_ROW_OCR".equals(option.originCompanyEvidenceSource))) {
            option.originCompanyEvidenceSource = "FROZEN_TOUCH_BASELINE";
        }
    }

    private void markSelectedRowFieldEvidence(FreightOption option) {
        if (option == null) return;
        if (looksLikeEntityName(option.cargo)) option.cargoSelectedRowEvidence = true;
        if (looksLikeEntityName(option.originCompany)) {
            option.originCompanySelectedRowEvidence = true;
            if (option.originCompanyEvidenceSource == null || option.originCompanyEvidenceSource.isEmpty()) {
                option.originCompanyEvidenceSource = "SELECTED_ROW_OCR";
            }
        }
        if (looksLikeEntityName(option.destinationCompany)) option.destinationCompanySelectedRowEvidence = true;
        if (looksLikePlaceName(option.destination)) option.destinationSelectedRowEvidence = true;
        if (GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.DISTANCE, option.km)) {
            option.kmSelectedRowEvidence = true;
        }
        if (GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.VALUE, option.offeredValue)) {
            option.valueSelectedRowEvidence = true;
        }
    }

    private GtoOriginGeometryPolicy.Result inferOriginCompanyFromMlLine(
        Text.Line line,
        String destinationCompanyHint
    ) {
        if (line == null || line.getText() == null) return GtoOriginGeometryPolicy.Result.none();
        String raw = line.getText().trim();
        if (raw.isEmpty()) return GtoOriginGeometryPolicy.Result.none();

        List<GtoOriginGeometryPolicy.Token> tokens = new ArrayList<>();
        for (Text.Element element : line.getElements()) {
            if (element == null || element.getText() == null || element.getBoundingBox() == null) continue;
            Rect box = element.getBoundingBox();
            tokens.add(new GtoOriginGeometryPolicy.Token(
                cleanOcrLabel(element.getText()), box.left, box.right
            ));
        }
        Rect lineBox = line.getBoundingBox();
        int lineHeight = lineBox == null ? 0 : lineBox.height();
        GtoOriginGeometryPolicy.Result geometric = GtoOriginGeometryPolicy.infer(
            raw, tokens, destinationCompanyHint, lineHeight
        );
        if (geometric.strong && looksLikeEntityName(geometric.value)) return geometric;

        // Exact-prefix reuse from an earlier high-confidence card remains safe: it does
        // not fuzzy-correct or invent text and is only used when the current OCR line
        // literally begins with the already confirmed origin.
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
        if (!bestKnown.isEmpty()) {
            return new GtoOriginGeometryPolicy.Result(bestKnown, true, "KNOWN_EXACT_PREFIX");
        }
        return GtoOriginGeometryPolicy.Result.none();
    }

    private void refinePreciseRowFields(FreightOption option, List<OcrLine> lines, int top, int bottom) {
        if (option == null || lines == null || lines.isEmpty()) return;
        List<OcrLine> plain = new ArrayList<>();
        for (OcrLine line : lines) {
            if (line.confidence > 0f && line.confidence < 0.34f) continue;
            String n = normalize(line.text);
            if (n.contains("aceitar")) continue;
            if (!extractKmDigits(line.text).isEmpty() || !extractMoneyValue(line.text).isEmpty()) continue;
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

        // Refinement is fill-only. parseFreightOptions already understands wrapped GTO
        // route labels; an auxiliary crop must never shorten "Cooper Log" to "Cooper"
        // or otherwise overwrite a more complete canonical parse.
        if ((option.cargo == null || option.cargo.trim().isEmpty()) && cargoLine != null) {
            option.cargo = cargoLine.text;
        }
        if ((option.destination == null || option.destination.trim().isEmpty())
            && destinationLine != null && (cargoLine == null || destinationLine != cargoLine)) {
            option.destination = destinationLine.text;
        }

        int routeIndex = -1;
        int separatorIndex = -1;
        for (int i = 0; i < plain.size(); i++) {
            separatorIndex = routeSeparatorIndex(plain.get(i).text);
            if (separatorIndex > 0) {
                routeIndex = i;
                break;
            }
        }
        if (routeIndex >= 0) {
            OcrLine routeLine = plain.get(routeIndex);
            int sep = routeSeparatorIndex(routeLine.text);
            int destinationIndex = destinationLine == null ? plain.size() : plain.indexOf(destinationLine);
            if (destinationIndex < 0) destinationIndex = plain.size();

            String origin = cleanOcrLabel(routeLine.text.substring(0, sep));
            int sepLength = routeSeparatorLength(routeLine.text, sep);
            StringBuilder destinationCompany = new StringBuilder(cleanOcrLabel(
                routeLine.text.substring(Math.min(routeLine.text.length(), sep + sepLength))
            ));
            for (int i = routeIndex + 1; i < destinationIndex; i++) {
                OcrLine continuationLine = plain.get(i);
                if (continuationLine == cargoLine) continue;
                String continuation = cleanOcrLabel(continuationLine.text);
                if (continuation.isEmpty()) continue;
                if (destinationCompany.length() > 0) destinationCompany.append(' ');
                destinationCompany.append(continuation);
            }
            String destinationCompanyText = destinationCompany.toString().replaceAll("\\s+", " ").trim();

            if ((option.originCompany == null || option.originCompany.trim().isEmpty())
                && looksLikeEntityName(origin)) {
                option.originCompany = origin;
            }
            if ((option.destinationCompany == null || option.destinationCompany.trim().isEmpty())
                && looksLikeEntityName(destinationCompanyText)) {
                option.destinationCompany = destinationCompanyText;
            }
        }
        option.companyRoute = option.originCompany
            + (option.destinationCompany.isEmpty() ? "" : " > " + option.destinationCompany);
    }

    private FreightOption mergeVerifiedPreciseWithStable(FreightOption exact, FreightOption stable) {
        // HF24: the immutable pre-touch row is a first-class evidence source. The precise
        // selected-row OCR remains primary, but if it misses a field completely we may fill
        // that field from the frozen same-row/same-page baseline even when it only has one
        // OCR observation. This is not a vote boost: the value is literal, tied to the exact
        // touched row, and can never overwrite a valid precise read.
        FreightOption canonical = exact == null ? new FreightOption() : copyFreightOption(exact);
        if (exact == null) return stable == null ? canonical : copyFreightOption(stable);

        int recoveredFromFrozenBaseline = 0;
        if (stable != null) {
            if (!GtoFreightFieldEvidencePolicy.text(
                    canonical.cargo, canonical.cargoVotes, canonical.cargoSelectedRowEvidence)
                && GtoFrozenFreightFallbackPolicy.canUse(GtoFreightReviewPolicy.CARGO, stable.cargo)) {
                canonical.cargo = stable.cargo.trim();
                canonical.cargoVotes = Math.max(1, stable.cargoVotes);
                canonical.cargoSelectedRowEvidence = true;
                recoveredFromFrozenBaseline++;
            }
            if (!GtoFreightFieldEvidencePolicy.text(
                    canonical.originCompany, canonical.originCompanyVotes, canonical.originCompanySelectedRowEvidence)
                && GtoFrozenFreightFallbackPolicy.canUse(GtoFreightReviewPolicy.ORIGIN_COMPANY, stable.originCompany)) {
                canonical.originCompany = stable.originCompany.trim();
                canonical.originCompanyVotes = Math.max(1, stable.originCompanyVotes);
                canonical.originCompanySelectedRowEvidence = true;
                canonical.originCompanyEvidenceSource = "FROZEN_TOUCH_BASELINE";
                recoveredFromFrozenBaseline++;
            }
            if (!GtoFreightFieldEvidencePolicy.text(
                    canonical.destination, canonical.destinationVotes, canonical.destinationSelectedRowEvidence)
                && GtoFrozenFreightFallbackPolicy.canUse(GtoFreightReviewPolicy.DESTINATION, stable.destination)) {
                canonical.destination = stable.destination.trim();
                canonical.destinationVotes = Math.max(1, stable.destinationVotes);
                canonical.destinationSelectedRowEvidence = true;
                canonical.destinationOcrConfidence = stable.destinationOcrConfidence;
                recoveredFromFrozenBaseline++;
            }
            if (!GtoFreightFieldEvidencePolicy.distance(
                    canonical.km, canonical.kmVotes, canonical.kmSelectedRowEvidence)
                && GtoFrozenFreightFallbackPolicy.canUse(GtoFreightReviewPolicy.DISTANCE, stable.km)) {
                canonical.km = stable.km;
                canonical.kmVotes = Math.max(1, stable.kmVotes);
                canonical.kmSelectedRowEvidence = true;
                recoveredFromFrozenBaseline++;
            }
            if (!GtoFreightFieldEvidencePolicy.money(
                    canonical.offeredValue, canonical.valueVotes, canonical.valueSelectedRowEvidence)
                && GtoFrozenFreightFallbackPolicy.canUse(GtoFreightReviewPolicy.VALUE, stable.offeredValue)) {
                canonical.offeredValue = stable.offeredValue;
                canonical.valueVotes = Math.max(1, stable.valueVotes);
                canonical.valueSelectedRowEvidence = true;
                recoveredFromFrozenBaseline++;
            }
            // destinationCompany remains optional metadata and never creates REVIEW_REQUIRED.
            if ((canonical.destinationCompany == null || canonical.destinationCompany.trim().isEmpty())
                && stable.destinationCompany != null && looksLikeEntityName(stable.destinationCompany)) {
                canonical.destinationCompany = stable.destinationCompany.trim();
                canonical.destinationCompanyVotes = Math.max(1, stable.destinationCompanyVotes);
            }
        }

        canonical.rowIndex = exact.rowIndex;
        canonical.acceptRect = exact.acceptRect == null ? canonical.acceptRect : new Rect(exact.acceptRect);
        canonical.acceptCenterY = exact.acceptCenterY > 0 ? exact.acceptCenterY : canonical.acceptCenterY;
        canonical.rowTop = exact.rowTop > 0 ? exact.rowTop : canonical.rowTop;
        canonical.rowBottom = exact.rowBottom > 0 ? exact.rowBottom : canonical.rowBottom;
        canonical.km = canonicalKm(canonical.km);
        canonical.offeredValue = canonicalMoney(canonical.offeredValue);
        canonical.origin = canonical.originCompany == null ? "" : canonical.originCompany.trim();
        canonical.companyRoute = canonical.originCompany
            + (canonical.destinationCompany == null || canonical.destinationCompany.isEmpty() ? "" : " > " + canonical.destinationCompany);
        if (recoveredFromFrozenBaseline > 0 && prefs != null) {
            prefs.edit()
                .putInt("lastFrozenBaselineRecoveredFieldCount", recoveredFromFrozenBaseline)
                .putLong("lastFrozenBaselineRecoveredAt", System.currentTimeMillis())
                .putString("lastEvent", "Campos ilegíveis recuperados do snapshot congelado da própria linha selecionada")
                .apply();
        }
        return canonical;
    }

    private void scheduleFocusedFreightConflictRetry(
        Bitmap sourceRow,
        float baseScale,
        int screenLeft,
        int screenTop,
        Rect exactButton,
        int exactRow,
        int rowTop,
        int rowBottom,
        FreightOption exact,
        FreightOption frozen,
        long selectionGeneration,
        String selectionSessionId
    ) {
        if (sourceRow == null || sourceRow.isRecycled() || selectionTextRecognizer == null) {
            FreightOption draft = mergeVerifiedPreciseWithStable(exact, frozen);
            clearConflictingFreightFields(draft, exact, frozen);
            enterFreightReview(draft, exactRow,
                "As leituras do frete divergiram e a releitura focalizada ficou indisponível.", firstReviewField(draft));
            return;
        }
        Bitmap retryBase = sourceRow.copy(Bitmap.Config.ARGB_8888, false);
        if (retryBase == null) {
            FreightOption draft = mergeVerifiedPreciseWithStable(exact, frozen);
            clearConflictingFreightFields(draft, exact, frozen);
            enterFreightReview(draft, exactRow,
                "As leituras do frete divergiram e a imagem de confirmação ficou indisponível.", firstReviewField(draft));
            return;
        }
        focusedFreightConflictRetryBusy = true;
        prefs.edit()
            .putString("lastEvent", "Campo divergente · executando releitura focalizada da linha selecionada")
            .putLong("lastFocusedFreightRetryAt", System.currentTimeMillis())
            .apply();
        mainHandler.post(() -> runFocusedFreightConflictRetry(
            retryBase, baseScale, screenLeft, screenTop, exactButton, exactRow,
            rowTop, rowBottom, copyFreightOption(exact), copyFreightOption(frozen),
            selectionGeneration, selectionSessionId, 1
        ));
    }

    private void runFocusedFreightConflictRetry(
        Bitmap retryBase,
        float baseScale,
        int screenLeft,
        int screenTop,
        Rect exactButton,
        int exactRow,
        int rowTop,
        int rowBottom,
        FreightOption exact,
        FreightOption frozen,
        long selectionGeneration,
        String selectionSessionId,
        int attempt
    ) {
        if (retryBase == null || retryBase.isRecycled()) {
            finishFocusedFreightRetry();
            return;
        }
        if (!isCurrentPreciseSelectionOcr(selectionGeneration, selectionSessionId)
            || !hasConfirmedSelectionIdentity()
            || prefs.getInt("selectedFreightRow", -1) != exactRow
            || !STATE_CONFIRMING_FREIGHT.equals(getTripState())) {
            retryBase.recycle();
            finishFocusedFreightRetry();
            return;
        }

        float extraScale = attempt <= 1 ? 1.18f : 1.42f;
        Bitmap attemptBitmap = Bitmap.createScaledBitmap(
            retryBase,
            Math.max(1, Math.round(retryBase.getWidth() * extraScale)),
            Math.max(1, Math.round(retryBase.getHeight() * extraScale)),
            true
        );
        float mappedScale = baseScale * extraScale;
        selectionTextRecognizer.process(InputImage.fromBitmap(attemptBitmap, 0))
            .addOnSuccessListener(text -> {
                if (!isCurrentPreciseSelectionOcr(selectionGeneration, selectionSessionId)) {
                    if (!retryBase.isRecycled()) retryBase.recycle();
                    finishFocusedFreightRetry();
                    return;
                }
                FreightOption retry = buildFocusedRetryFreight(
                    text, mappedScale, screenLeft, screenTop, exactButton, exactRow,
                    rowTop, rowBottom, frozen == null ? "" : frozen.destinationCompany
                );
                FreightOption resolved = resolveFreightConflictsAfterRetry(exact, frozen, retry);
                String unresolved = firstReviewField(resolved);
                boolean safe = isStableFreightSafeToCommit(resolved);
                if (unresolved.isEmpty() && safe) {
                    prefs.edit()
                        .putInt("lastFocusedFreightRetryAttempt", attempt)
                        .putString("lastEvent", "Releitura focalizada confirmou os campos do frete selecionado")
                        .apply();
                    retryBase.recycle();
                    finishFocusedFreightRetry();
                    commitPreciseFreight(resolved);
                    return;
                }

                if (attempt < 2 && hasCriticalFreightConflict(exact, frozen)) {
                    mainHandler.post(() -> runFocusedFreightConflictRetry(
                        retryBase, baseScale, screenLeft, screenTop, exactButton, exactRow,
                        rowTop, rowBottom, exact, frozen, selectionGeneration, selectionSessionId, attempt + 1
                    ));
                    return;
                }

                retryBase.recycle();
                finishFocusedFreightRetry();
                prefs.edit()
                    .putInt("lastFocusedFreightRetryAttempt", attempt)
                    .putString("lastEvent", "Releitura focalizada não resolveu todos os campos divergentes")
                    .apply();
                enterFreightReview(
                    resolved, exactRow,
                    "A releitura focalizada não confirmou um campo com segurança. Confirme somente o campo pendente.",
                    unresolved
                );
            })
            .addOnFailureListener(error -> {
                if (!isCurrentPreciseSelectionOcr(selectionGeneration, selectionSessionId)) {
                    if (!retryBase.isRecycled()) retryBase.recycle();
                    finishFocusedFreightRetry();
                    return;
                }
                if (attempt < 2) {
                    mainHandler.post(() -> runFocusedFreightConflictRetry(
                        retryBase, baseScale, screenLeft, screenTop, exactButton, exactRow,
                        rowTop, rowBottom, exact, frozen, selectionGeneration, selectionSessionId, attempt + 1
                    ));
                    return;
                }
                FreightOption draft = mergeVerifiedPreciseWithStable(exact, frozen);
                clearConflictingFreightFields(draft, exact, frozen);
                String required = firstReviewField(draft);
                retryBase.recycle();
                finishFocusedFreightRetry();
                enterFreightReview(
                    draft, exactRow,
                    "Falha temporária na releitura focalizada; os demais campos continuam preservados.",
                    required
                );
            })
            .addOnCompleteListener(task -> {
                if (attemptBitmap != retryBase && !attemptBitmap.isRecycled()) attemptBitmap.recycle();
            });
    }

    private FreightOption buildFocusedRetryFreight(
        Text text,
        float mappedScale,
        int screenLeft,
        int screenTop,
        Rect exactButton,
        int exactRow,
        int rowTop,
        int rowBottom,
        String destinationCompanyHint
    ) {
        List<OcrLine> lines = new ArrayList<>();
        if (text != null) {
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    Rect box = line.getBoundingBox();
                    if (box == null || line.getText() == null) continue;
                    String value = line.getText().trim();
                    if (value.isEmpty()) continue;
                    Rect mapped = new Rect(
                        screenLeft + Math.round(box.left / mappedScale),
                        screenTop + Math.round(box.top / mappedScale),
                        screenLeft + Math.round(box.right / mappedScale),
                        screenTop + Math.round(box.bottom / mappedScale)
                    );
                    lines.add(new OcrLine(value, mapped, line.getConfidence()));
                }
            }
        }

        FreightOption retry = new FreightOption();
        retry.rowIndex = exactRow;
        retry.acceptRect = exactButton == null ? null : new Rect(exactButton);
        retry.acceptCenterY = exactButton == null ? 0 : exactButton.centerY();
        retry.rowTop = rowTop;
        retry.rowBottom = rowBottom;
        retry.rawText = text == null ? "" : text.getText();

        for (OcrLine line : lines) {
            if (retry.km.isEmpty()) {
                String km = extractKmDigits(line.text);
                if (!km.isEmpty()) retry.km = km + "Km";
            }
            if (retry.offeredValue.isEmpty()) {
                String money = extractMoneyValue(line.text);
                if (!money.isEmpty()) retry.offeredValue = money;
            }
        }
        refinePreciseRowFields(retry, lines, rowTop, rowBottom);
        GtoOriginGeometryPolicy.Result origin = inferOriginCompanyFromSelectedRowLines(
            lines, destinationCompanyHint, rowTop, rowBottom
        );
        if (origin.strong && looksLikeEntityName(origin.value)) {
            retry.originCompany = origin.value;
            retry.origin = origin.value;
            retry.originCompanyEvidenceSource = "FOCUSED_RETRY_GEOMETRY";
        }
        retry.companyRoute = retry.originCompany
            + (retry.destinationCompany == null || retry.destinationCompany.isEmpty()
                ? "" : " > " + retry.destinationCompany);
        markSelectedRowFieldEvidence(retry);
        return retry;
    }

    private FreightOption resolveFreightConflictsAfterRetry(
        FreightOption exact,
        FreightOption frozen,
        FreightOption retry
    ) {
        FreightOption resolved = mergeVerifiedPreciseWithStable(exact, frozen);
        resolveFreightFieldAfterRetry(resolved, GtoFreightReviewPolicy.CARGO,
            exact == null ? "" : exact.cargo,
            frozen == null ? "" : frozen.cargo,
            retry == null ? "" : retry.cargo);
        resolveFreightFieldAfterRetry(resolved, GtoFreightReviewPolicy.ORIGIN_COMPANY,
            exact == null ? "" : exact.originCompany,
            frozen == null ? "" : frozen.originCompany,
            retry == null ? "" : retry.originCompany);
        resolveFreightFieldAfterRetry(resolved, GtoFreightReviewPolicy.DESTINATION,
            exact == null ? "" : exact.destination,
            frozen == null ? "" : frozen.destination,
            retry == null ? "" : retry.destination);
        resolveFreightFieldAfterRetry(resolved, GtoFreightReviewPolicy.DISTANCE,
            exact == null ? "" : exact.km,
            frozen == null ? "" : frozen.km,
            retry == null ? "" : retry.km);
        resolveFreightFieldAfterRetry(resolved, GtoFreightReviewPolicy.VALUE,
            exact == null ? "" : exact.offeredValue,
            frozen == null ? "" : frozen.offeredValue,
            retry == null ? "" : retry.offeredValue);
        resolved.origin = resolved.originCompany == null ? "" : resolved.originCompany.trim();
        resolved.companyRoute = resolved.originCompany
            + (resolved.destinationCompany == null || resolved.destinationCompany.isEmpty()
                ? "" : " > " + resolved.destinationCompany);
        resolved.km = canonicalKm(resolved.km);
        resolved.offeredValue = canonicalMoney(resolved.offeredValue);
        return resolved;
    }

    private void resolveFreightFieldAfterRetry(
        FreightOption target,
        String field,
        String exact,
        String frozen,
        String retry
    ) {
        if (target == null || !GtoFreightFieldConflictPolicy.needsRetry(field, exact, frozen)) return;
        GtoFreightFieldConflictPolicy.Resolution resolution =
            GtoFreightFieldConflictPolicy.resolve(field, exact, frozen, retry);
        if (!resolution.resolved) {
            clearReviewField(target, field);
            return;
        }
        String value = resolution.value;
        if (GtoFreightReviewPolicy.CARGO.equals(field)) {
            target.cargo = value;
            target.cargoSelectedRowEvidence = true;
        } else if (GtoFreightReviewPolicy.ORIGIN_COMPANY.equals(field)) {
            target.originCompany = value;
            target.originCompanySelectedRowEvidence = true;
            target.originCompanyEvidenceSource = resolution.source;
        } else if (GtoFreightReviewPolicy.DESTINATION.equals(field)) {
            target.destination = value;
            target.destinationSelectedRowEvidence = true;
        } else if (GtoFreightReviewPolicy.DISTANCE.equals(field)) {
            target.km = canonicalKm(value);
            target.kmSelectedRowEvidence = true;
        } else if (GtoFreightReviewPolicy.VALUE.equals(field)) {
            target.offeredValue = canonicalMoney(value);
            target.valueSelectedRowEvidence = true;
        }
    }

    private void clearConflictingFreightFields(FreightOption draft, FreightOption exact, FreightOption frozen) {
        if (draft == null) return;
        if (exact == null || frozen == null) return;
        if (GtoFreightFieldConflictPolicy.needsRetry(GtoFreightReviewPolicy.CARGO, exact.cargo, frozen.cargo))
            clearReviewField(draft, GtoFreightReviewPolicy.CARGO);
        if (GtoFreightFieldConflictPolicy.needsRetry(GtoFreightReviewPolicy.ORIGIN_COMPANY, exact.originCompany, frozen.originCompany))
            clearReviewField(draft, GtoFreightReviewPolicy.ORIGIN_COMPANY);
        if (GtoFreightFieldConflictPolicy.needsRetry(GtoFreightReviewPolicy.DESTINATION, exact.destination, frozen.destination))
            clearReviewField(draft, GtoFreightReviewPolicy.DESTINATION);
        if (GtoFreightFieldConflictPolicy.needsRetry(GtoFreightReviewPolicy.DISTANCE, exact.km, frozen.km))
            clearReviewField(draft, GtoFreightReviewPolicy.DISTANCE);
        if (GtoFreightFieldConflictPolicy.needsRetry(GtoFreightReviewPolicy.VALUE, exact.offeredValue, frozen.offeredValue))
            clearReviewField(draft, GtoFreightReviewPolicy.VALUE);
    }

    private void finishFocusedFreightRetry() {
        focusedFreightConflictRetryBusy = false;
    }

    private boolean hasUnresolvedDestinationOneEditConflict(FreightOption exact, FreightOption stable) {
        if (exact == null || stable == null) return false;
        String exactDestination = exact.destination == null ? "" : exact.destination.trim();
        String stableDestination = stable.destination == null ? "" : stable.destination.trim();
        if (exactDestination.isEmpty() || stableDestination.isEmpty()) return false;
        if (GtoFreightTextGuard.sameVisibleText(exactDestination, stableDestination)) return false;
        // A one-character disagreement is never silently normalized to a known city.
        return GtoCityTextResolver.isSafeOneEditVariant(exactDestination, stableDestination);
    }

    private boolean hasCriticalFreightConflict(FreightOption exact, FreightOption stable) {
        if (exact == null || stable == null) return false;
        if (differentVisibleText(exact.cargo, stable.cargo)) return true;
        if (differentVisibleText(exact.originCompany, stable.originCompany)) return true;
        // Optional route metadata must never force driver review.
        if (differentVisibleText(exact.destination, stable.destination)) return true;
        if (differentNumericValue(exact.km, stable.km)) return true;
        return differentMoneyValue(exact.offeredValue, stable.offeredValue);
    }

    private boolean hasIndependentVisibleAgreement(FreightOption exact, FreightOption stable) {
        if (exact == null || stable == null || exact.rowIndex != stable.rowIndex) return false;
        return GtoFreightTextGuard.sameVisibleText(exact.cargo, stable.cargo)
            && GtoFreightTextGuard.sameVisibleText(exact.originCompany, stable.originCompany)
            && GtoFreightTextGuard.sameVisibleText(exact.destination, stable.destination)
            && GtoFreightTextGuard.sameNumericValue(exact.km, stable.km)
            && sameMoneyValue(exact.offeredValue, stable.offeredValue);
    }

    private boolean differentVisibleText(String first, String second) {
        return first != null && !first.trim().isEmpty()
            && second != null && !second.trim().isEmpty()
            && !GtoFreightTextGuard.sameVisibleText(first, second);
    }

    private boolean differentNumericValue(String first, String second) {
        return !digitsOnly(first).isEmpty() && !digitsOnly(second).isEmpty()
            && !GtoFreightTextGuard.sameNumericValue(first, second);
    }

    private boolean sameMoneyValue(String first, String second) {
        Long a = GtoMoneyValue.parseCents(first);
        Long b = GtoMoneyValue.parseCents(second);
        return a != null && b != null && a.longValue() == b.longValue();
    }

    private boolean differentMoneyValue(String first, String second) {
        Long a = GtoMoneyValue.parseCents(first);
        Long b = GtoMoneyValue.parseCents(second);
        return a != null && b != null && a.longValue() != b.longValue();
    }

    private String freightConflictSummary(FreightOption exact, FreightOption stable) {
        return "exact[cargo=" + (exact == null ? "" : exact.cargo)
            + ",originCompany=" + (exact == null ? "" : exact.originCompany)
            + ",destinationCompany=" + (exact == null ? "" : exact.destinationCompany)
            + ",destination=" + (exact == null ? "" : exact.destination)
            + ",km=" + digitsOnly(exact == null ? "" : exact.km)
            + ",value=" + digitsOnly(exact == null ? "" : exact.offeredValue)
            + "] stable[cargo=" + (stable == null ? "" : stable.cargo)
            + ",originCompany=" + (stable == null ? "" : stable.originCompany)
            + ",destinationCompany=" + (stable == null ? "" : stable.destinationCompany)
            + ",destination=" + (stable == null ? "" : stable.destination)
            + ",km=" + digitsOnly(stable == null ? "" : stable.km)
            + ",value=" + digitsOnly(stable == null ? "" : stable.offeredValue) + "]";
    }

    private boolean isExactFreightDataValid(FreightOption option) {
        if (option == null) return false;
        if (!looksLikeEntityName(option.cargo) || textQuality(option.cargo) < 0.68f) return false;
        if (!looksLikeEntityName(option.originCompany) || textQuality(option.originCompany) < 0.68f) return false;
        // destinationCompany is optional metadata. Even noisy/missing metadata cannot
        // block the five operational fields required for a trip.
        String canonicalOrigin = option.originCompany == null ? "" : option.originCompany.trim();
        if (!GtoFreightTextGuard.sameVisibleText(option.origin, canonicalOrigin)) return false;
        if (!looksLikePlaceName(option.destination) || textQuality(option.destination) < 0.68f) return false;
        String kmDigits = digitsOnly(option.km);
        Double moneyValue = GtoMoneyValue.parseReais(option.offeredValue);
        try {
            int km = Integer.parseInt(kmDigits);
            return km >= 10 && km <= 10000
                && moneyValue != null && moneyValue >= 100d && moneyValue <= 100000000d;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isStableFreightSafeToCommit(FreightOption option) {
        return isExactFreightDataValid(option) && isFreightDataReliable(option);
    }

    private String freightSafetyFailure(FreightOption option, String fallback) {
        if (option != null && !GtoFreightFieldEvidencePolicy.text(
            option.originCompany, option.originCompanyVotes, option.originCompanySelectedRowEvidence)) {
            return "A origem do frete não pôde ser confirmada. A linha selecionada foi preservada.";
        }
        return fallback == null || fallback.trim().isEmpty()
            ? "Um ou mais campos do frete ainda precisam de confirmação. A linha selecionada foi preservada."
            : fallback;
    }

    private void commitPreciseFreight(FreightOption selected) {
        if (selected == null) return;
        String state = getTripState();
        if (screenAnalysisPausedOutsideGto || !gtoForeground) {
            if (STATE_WAITING_FREIGHT.equals(state) || STATE_CONFIRMING_FREIGHT.equals(state)) {
                deferredPreciseFreightCommit = copyFreightOption(selected);
                deferredSelectionFailureRow = -1;
                deferredSelectionFailureReason = "";
                prefs.edit()
                    .putString("lastEvent", "Frete validado internamente · aguardando retorno ao GTO para avançar")
                    .apply();
            }
            return;
        }
        if (!STATE_WAITING_FREIGHT.equals(state) && !STATE_CONFIRMING_FREIGHT.equals(state)) return;
        if (!hasConfirmedSelectionIdentity()) {
            deferredPreciseFreightCommit = copyFreightOption(selected);
            prefs.edit()
                .putString("lastEvent", "Dados da linha lidos · aguardando confirmação visual do toque")
                .apply();
            return;
        }
        selected.origin = selected.originCompany == null ? "" : selected.originCompany.trim();
        if (!isStableFreightSafeToCommit(selected)) {
            enterFreightReview(
                selected,
                selected.rowIndex,
                "Os campos do frete ainda não alcançaram confiança suficiente; a linha selecionada foi preservada.",
                ""
            );
            return;
        }
        selected.km = canonicalKm(selected.km);
        selected.offeredValue = canonicalMoney(selected.offeredValue);
        String json = freightOptionToJson(selected).toString();
        java.util.Set<String> knownOrigins = new java.util.HashSet<>(prefs.getStringSet("knownGtoOriginCompanies", java.util.Collections.emptySet()));
        if (!selected.originCompany.isEmpty()) knownOrigins.add(selected.originCompany);
        prefs.edit()
            .putStringSet("knownGtoOriginCompanies", knownOrigins)
            .putString("selectedFreight", json)
            .putString("selectedFreightSummary", selected.summary())
            .putInt("selectedFreightRow", selected.rowIndex)
            .putString("selectedOrigin", selected.origin)
            .putString("selectedOriginSource", selected.originCompanyEvidenceSource == null || selected.originCompanyEvidenceSource.isEmpty() ? "OCR" : selected.originCompanyEvidenceSource)
            .putString("selectedDestination", selected.destination)
            .putString("selectedOriginCompany", selected.originCompany)
            .putString("selectedDestinationCompany", selected.destinationCompany)
            .putString("selectedCargo", selected.cargo)
            .putString("selectedKm", selected.km)
            .putString("selectedValue", selected.offeredValue)
            .putString("selectionSource", prefs.getString("selectionIdentitySource", prefs.getString("selectionSource", "")) + "+row-ocr")
            .putString("selectionConfirmationStatus", "CONFIRMED")
            .putBoolean("touchCaptureNeeded", false)
            .remove("pendingFreight")
            .remove("pendingSelectionSource")
            .remove("selectionFailureReason")
            .remove("selectionFailureAt")
            .apply();
        persistFreightFieldStatuses(selected, "");
        recordObserverEvent("FREIGHT_FIELDS_CONFIRMED", "row=" + (selected.rowIndex + 1) + " source=selected-row");
        if (!GtoAutoTripSync.lockSelectedFreight(this, prefs)) {
            enterFreightReview(
                selected,
                selected.rowIndex,
                "A integridade local do frete não pôde ser bloqueada; a linha selecionada permanece preservada.",
                GtoFreightReviewPolicy.LOCAL_INTEGRITY
            );
            return;
        }
        transitionConfirmedFreightToTripInProgress();
    }

    private void clearUncommittedSelectedFreight() {
        prefs.edit()
            .remove("selectedFreight")
            .remove("selectedFreightSummary")
            .remove("selectedFreightRow")
            .remove("selectedOrigin")
            .remove("selectedOriginSource")
            .remove("selectedDestination")
            .remove("selectedOriginCompany")
            .remove("selectedDestinationCompany")
            .remove("selectedCargo")
            .remove("selectedKm")
            .remove("selectedValue")
            .putString("selectionConfirmationStatus", "FAILED")
            .putString("selectionFailureReason", "A integridade local do frete não pôde ser bloqueada.")
            .putLong("selectionFailureAt", System.currentTimeMillis())
            .apply();
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
        String flowState = getTripState();
        boolean freightReviewPending = STATE_CONFIRMING_FREIGHT.equals(flowState) && isFreightReviewPending();

        if (containsBonusVideo(normalized)) {
            if (freightReviewPending) {
                lastScreenState = "BONUS_VIDEO";
                prefs.edit()
                    .putString("screenState", lastScreenState)
                    .putBoolean("pendingBonusDuringFreightReview", true)
                    .putBoolean("pendingResultDuringFreightReview", true)
                    .putString("completionStatus", "REJECTED_BONUS_PENDING_FREIGHT_REVIEW")
                    .putLong("completionDetectedAt", System.currentTimeMillis())
                    .putString("lastEvent", "ADS/bônus detectado · seleção preservada até concluir a revisão do frete")
                    .apply();
                updateNotification();
                return;
            }
            if (!GtoDeterministicFlowPolicy.mayInterpretBonusOrAds(flowState)) {
                recordNeutralScreenObservation("BONUS_OR_AD_OUTSIDE_RESULT_FLOW", normalized);
                return;
            }
            lastScreenState = "BONUS_VIDEO";
            prefs.edit().putString("screenState", lastScreenState).apply();
            persistArrivalCityFromSelectedFreight();
            prefs.edit()
                .putString("completionStatus", "REJECTED_BONUS")
                .putLong("completionDetectedAt", System.currentTimeMillis())
                .apply();
            setTripState(STATE_REJECTED_BONUS, "Bônus de vídeo detectado; viagem bloqueada");
            announceDriverStage(
                "REJECTED_BONUS",
                "Viagem não registrada · bônus/ADS detectado.",
                3600L,
                true
            );
            return;
        }

        ResultScreen resultScreen = (GtoDeterministicFlowPolicy.mayInterpretResultScreen(flowState) || freightReviewPending)
            ? parseResultScreen(lines, normalized)
            : null;
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
            String previouslyStableValue = prefs.getString("resultValueConsensusStable", "");
            detectedResultValue = observeResultValueCandidate(
                resultScreen.value,
                "live-" + System.currentTimeMillis() + "-" + (++resultEvidenceSequence)
            );
            resultScreenLastSeenAt = System.currentTimeMillis();
            resultExitSeenAt = 0L;
            if (detectedResultValue == null || detectedResultValue.trim().isEmpty()) {
                if (prefs.getString("resultSnapshotPath", "").isEmpty()) {
                    persistResultSnapshot(fullFrame);
                }
            } else {
                deleteResultSnapshot();
                if (previouslyStableValue.isEmpty()) {
                }
            }
            gameplayFramesAfterResult = 0;
            String latchedResultAction = prefs.getString("resultAction", "");
            boolean receiveAlreadyLatched = prefs.getBoolean("resultReceiveLatched", false)
                && latchedResultAction != null
                && latchedResultAction.startsWith("RECEIVE");
            SharedPreferences.Editor resultEditor = prefs.edit()
                .putString("screenState", lastScreenState)
                .putString("resultValue", detectedResultValue)
                .putString("resultRecognitionStatus", detectedResultValue == null || detectedResultValue.trim().isEmpty()
                    ? "REVIEW_REQUIRED" : "CONFIRMED")
                .putString("resultReviewRequiredField", detectedResultValue == null || detectedResultValue.trim().isEmpty()
                    ? GtoFreightReviewPolicy.VALUE : "")
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
                if (freightReviewPending) {
                    prefs.edit()
                        .putBoolean("pendingResultDuringFreightReview", true)
                        .putString("lastEvent", "Resultado e Receber preservados · aguardando somente a revisão do frete")
                        .apply();
                    mainHandler.post(this::updateFreightTouchPulseSensor);
                    return;
                }
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

            if (freightReviewPending) {
                prefs.edit()
                    .putBoolean("pendingResultDuringFreightReview", true)
                    .putString("lastEvent", "Entrega detectada · frete preservado; falta revisar "
                        + reviewFieldLabel(prefs.getString("reviewRequiredField", "")))
                    .apply();
                mainHandler.post(this::updateFreightTouchPulseSensor);
                announceDriverStage(
                    "RESULT_DURING_FREIGHT_REVIEW",
                    "Entrega detectada. O frete continua preservado; confirme somente o campo pendente na bolinha NVU.",
                    4200L,
                    false
                );
                return;
            }

            if (STATE_TRIP_IN_PROGRESS.equals(getTripState())) {
                setTripState(STATE_RESULT_DETECTED, "Entrega concluída detectada: " + detectedResultValue);
                mainHandler.post(this::updateFreightTouchPulseSensor);
                announceDriverStage(
                    "RESULT_DETECTED",
                    detectedResultValue.isEmpty()
                        ? "Entrega detectada · toque em Receber."
                        : "Entrega detectada · " + detectedResultValue + ". Toque em Receber.",
                    3600L,
                    false
                );
            }
            return;
        }

        if (isResultTrackingState(getTripState()) && !manualFinishCapturePending) {
            // GtoResultVisualGate is intentionally permissive and exists only to wake OCR.
            // A gameplay scene can occasionally resemble the dark/gold completion dialog,
            // so a visual candidate by itself must never produce a driver-facing failure.
            // Only repeated OCR with semantic evidence from the real result dialog may
            // expose the manual confirmation fallback.
            if (hasPartialResultSemanticEvidence(normalized)) {
                automaticResultCandidateMisses++;
                // Partial semantic evidence is not an error and never creates a manual
                // completion path. Keep the trip intact and retry OCR on a clean frame.
                lastOcrAt = 0L;
                lastActiveTripFallbackOcrAt = 0L;
                prefs.edit()
                    .putBoolean("resultConfirmationFallbackNeeded", false)
                    .putString("screenState", "RESULT_PARTIAL")
                    .putString("lastEvent", "Resultado parcialmente reconhecido · nova leitura automática agendada")
                    .apply();
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
        boolean pendingReviewedResult = STATE_CONFIRMING_FREIGHT.equals(stateAfterResult)
            && isFreightReviewPending()
            && prefs.getBoolean("pendingResultDuringFreightReview", false);
        if ((STATE_RESULT_DETECTED.equals(stateAfterResult) || STATE_AWAITING_BONUS.equals(stateAfterResult) || pendingReviewedResult)
            && System.currentTimeMillis() - resultScreenLastSeenAt >= 90L) {
            long now = System.currentTimeMillis();
            if (containsPostResultAdEvidence(normalized)) {
                if (pendingReviewedResult) {
                    prefs.edit()
                        .putBoolean("pendingBonusDuringFreightReview", true)
                        .putString("completionStatus", "REJECTED_BONUS_PENDING_FREIGHT_REVIEW")
                        .putLong("completionDetectedAt", now)
                        .putString("lastEvent", "Fluxo ADS/bônus detectado · frete preservado até concluir revisão")
                        .apply();
                    return;
                }
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
                    if (pendingReviewedResult) {
                        prefs.edit()
                            .putBoolean("resultReceiveLatched", true)
                            .putString("resultAction", "RECEIVE_TRANSITION_CONFIRMED")
                            .putString("completionStatus", "RECEIVE_LATCHED")
                            .putString("lastEvent", "Retorno ao jogo confirmou recebimento · aguardando revisão do frete")
                            .apply();
                        return;
                    }
                    confirmNormalResultAutomatically();
                    return;
                }
            } else {
                gameplayFramesAfterResult = 0;
                // Unknown/post-result intermediary screens are neutral. They may be a
                // loading screen, menu, notification, animation or a future GTO screen.
                // No state transition is inferred until a recognized action/screen appears.
                recordNeutralScreenObservation("UNKNOWN_AFTER_RESULT", normalized);
            }
        }

        if (!STATE_WAITING_FREIGHT.equals(getTripState())) {
            recordNeutralScreenObservation("UNRECOGNIZED_FOR_" + getTripState(), normalized);
            return;
        }

        boolean waitingForFreight = true;
        List<Rect> visualButtons = waitingForFreight ? detectAcceptButtonRects(fullFrame) : Collections.emptyList();
        int freightPage = waitingForFreight ? parseFreightPageNumber(lines) : -1;
        List<FreightOption> parsedOptions = waitingForFreight
            ? parseFreightOptions(lines, visualButtons)
            : Collections.emptyList();
        if (!parsedOptions.isEmpty()) {
            int semanticAnchors = semanticFreightAnchorRows(parsedOptions);
            boolean semanticCertified = GtoFreightSemanticCertificationPolicy.isCertifiedPage(
                visualButtons.size(), parsedOptions.size(), semanticAnchors
            );
            if (!semanticCertified) {
                lastScreenState = "FREIGHT_LIST_CANDIDATE";
                prefs.edit()
                    .putString("screenState", lastScreenState)
                    .putInt("freightCount", 0)
                    .putString("lastEvent", "Estrutura semelhante à lista detectada, mas sem evidência semântica suficiente")
                    .apply();
                return;
            }
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
            for (FreightOption option : stableOptions) {
                option.origin = option.originCompany == null ? "" : option.originCompany.trim();
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
            markFreightPageSemanticallyCertified(
                freightPageGeneration > 0L ? freightPageGeneration : Math.max(1, freightPage),
                semanticAnchors,
                stableOptions.size()
            );
            prefs.edit()
                .putString("screenState", lastScreenState)
                .putString("freightOptions", freightOptionsToJson(stableOptions))
                .putInt("freightCount", stableOptions.size())
                .putInt("freightPage", freightPage)
                .putLong("freightStableAt", freightHistoryUpdatedAt)
                .apply();
            return;
        }

        // Geometry alone is only a candidate. It may keep the low-cost touch sensor
        // armed, but it must never be exposed as a certified list or create a selection.
        if (waitingForFreight && visualButtons != null && !visualButtons.isEmpty()) {
            lastScreenState = "FREIGHT_LIST_CANDIDATE";
            prefs.edit()
                .putString("screenState", lastScreenState)
                .putInt("freightCount", 0)
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
                boolean money = !extractMoneyValue(line.text).isEmpty();
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
            for (OcrLine line : cardLines) {
                if (normalize(line.text).contains("aceitar")) {
                    option.acceptTextEvidence = true;
                    break;
                }
            }

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

                String moneyValue = extractMoneyValue(line.text);
                if (!moneyValue.isEmpty()) {
                    int d = Math.abs(line.rect.centerY() - centerY);
                    if (d < bestMoneyDistance) {
                        bestMoneyDistance = d;
                        bestMoney = line;
                        // Preserve numeric semantics at ingestion time. Flattening
                        // punctuation turned R$ 5.300,00 into R$ 530000 and later made
                        // the selected-row read conflict with the stable list.
                        option.offeredValue = moneyValue;
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
                if (!extractMoneyValue(line.text).isEmpty()) continue;
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
                    option.destinationOcrConfidence = plain.get(destinationIndex).confidence;
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
                if (looksLikePlaceName(destinationCandidate)) {
                    option.destination = destinationCandidate;
                    option.destinationOcrConfidence = plain.get(plain.size() - 1).confidence;
                }
            }

            GtoCityTextResolver.Resolution destinationResolution = resolveTrustedDestination(option.destination);
            // HF23: destination OCR stays literal. Resolver output is advisory/diagnostic
            // only and can never silently rewrite the selected freight.
            if (!destinationResolution.source.isEmpty() && prefs != null) {
                prefs.edit().putString("lastDestinationLiteralEvidence", destinationResolution.source).apply();
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
            // The coarse band scan is intentionally permissive so a pressed Aceitar can
            // still be correlated. Before exposing a list to OCR/state, however, require
            // the same strict card-stack signature as GtoFastVisualDetector.Frame.
            if (!plausibleRefinedAcceptStack(bitmap, best)) return Collections.emptyList();
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

    private boolean plausibleRefinedAcceptStack(Bitmap bitmap, List<Rect> buttons) {
        if (bitmap == null || bitmap.isRecycled() || buttons == null || buttons.isEmpty() || buttons.size() > 6) return false;
        int minHeight = Math.max(10, Math.round(captureHeight * 0.014f));
        int maxHeight = Math.max(minHeight + 1, Math.round(captureHeight * 0.160f));
        int minWidth = Math.max(10, Math.round(captureWidth * 0.025f));
        int maxWidth = Math.max(minWidth + 1, Math.round(captureWidth * 0.130f));
        int smallestHeight = Integer.MAX_VALUE;
        int largestHeight = 0;
        int smallestWidth = Integer.MAX_VALUE;
        int largestWidth = 0;
        int minCenterX = Integer.MAX_VALUE;
        int maxCenterX = 0;
        for (Rect rect : buttons) {
            if (rect.height() < minHeight || rect.height() > maxHeight) return false;
            if (rect.width() < minWidth || rect.width() > maxWidth) return false;
            if (orangeRatio(bitmap, rect, 1f) < 0.14f) return false;
            smallestHeight = Math.min(smallestHeight, rect.height());
            largestHeight = Math.max(largestHeight, rect.height());
            smallestWidth = Math.min(smallestWidth, rect.width());
            largestWidth = Math.max(largestWidth, rect.width());
            minCenterX = Math.min(minCenterX, ((rect.left + rect.right) / 2));
            maxCenterX = Math.max(maxCenterX, ((rect.left + rect.right) / 2));
        }
        if (buttons.get(0).centerY() > Math.round(captureHeight * 0.20f)) return false;
        if (buttons.size() == 1) return true;
        if (smallestHeight <= 0 || largestHeight > Math.round(smallestHeight * 1.75f)) return false;
        if (smallestWidth <= 0 || largestWidth > Math.round(smallestWidth * 1.70f)) return false;
        if (maxCenterX - minCenterX > Math.round(captureWidth * 0.070f)) return false;
        int minGap = Math.round(captureHeight * 0.100f);
        int maxGap = Math.round(captureHeight * 0.235f);
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
            if (!destination.value.isEmpty()) {
                base.destination = destination.value;
                base.destinationOcrConfidence = maxDestinationConfidenceForValue(candidates, destination.value);
            }
            if (!km.value.isEmpty()) base.km = canonicalKm(km.value);
            if (!value.value.isEmpty()) base.offeredValue = canonicalMoney(value.value);
            base.companyRoute = base.originCompany + (base.destinationCompany.isEmpty() ? "" : " > " + base.destinationCompany);

            base.cargoVotes = cargo.count;
            base.originCompanyVotes = company.count;
            base.destinationCompanyVotes = destinationCompany.count;
            base.destinationVotes = destination.count;
            base.kmVotes = km.count;
            base.valueVotes = value.count;

            float evidence = 0f;
            evidence += voteEvidence(cargo, base.cargo);
            evidence += voteEvidence(company, base.originCompany);
            evidence += voteEvidence(destination, base.destination);
            evidence += voteEvidence(km, base.km);
            evidence += voteEvidence(value, base.offeredValue);
            // destinationCompany is optional metadata and is excluded from operational confidence.
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
        dst.destinationOcrConfidence = src.destinationOcrConfidence;
        dst.km = src.km;
        dst.offeredValue = src.offeredValue;
        dst.rawText = src.rawText;
        dst.acceptTextEvidence = src.acceptTextEvidence;
        dst.dataConfidence = src.dataConfidence;
        dst.consensusFrames = src.consensusFrames;
        dst.cargoVotes = src.cargoVotes;
        dst.originCompanyVotes = src.originCompanyVotes;
        dst.destinationCompanyVotes = src.destinationCompanyVotes;
        dst.destinationVotes = src.destinationVotes;
        dst.kmVotes = src.kmVotes;
        dst.valueVotes = src.valueVotes;
        dst.cargoSelectedRowEvidence = src.cargoSelectedRowEvidence;
        dst.originCompanySelectedRowEvidence = src.originCompanySelectedRowEvidence;
        dst.destinationCompanySelectedRowEvidence = src.destinationCompanySelectedRowEvidence;
        dst.destinationSelectedRowEvidence = src.destinationSelectedRowEvidence;
        dst.kmSelectedRowEvidence = src.kmSelectedRowEvidence;
        dst.valueSelectedRowEvidence = src.valueSelectedRowEvidence;
        dst.originCompanyEvidenceSource = src.originCompanyEvidenceSource;
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
        return GtoMoneyValue.canonical(value);
    }

    private boolean isFreightDataReliable(FreightOption option) {
        if (option == null) return false;
        // HF18: each operational field is validated independently; optional metadata and
        // aggregate history cannot block an otherwise valid selected freight.
        return GtoFreightFieldEvidencePolicy.text(option.cargo, option.cargoVotes, option.cargoSelectedRowEvidence)
            && GtoFreightFieldEvidencePolicy.text(option.originCompany, option.originCompanyVotes, option.originCompanySelectedRowEvidence)
            && GtoFreightFieldEvidencePolicy.text(option.destination, option.destinationVotes, option.destinationSelectedRowEvidence)
            && GtoFreightFieldEvidencePolicy.distance(option.km, option.kmVotes, option.kmSelectedRowEvidence)
            && GtoFreightFieldEvidencePolicy.money(option.offeredValue, option.valueVotes, option.valueSelectedRowEvidence);
    }

    private boolean hasPartialResultSemanticEvidence(String normalizedAll) {
        if (normalizedAll == null || normalizedAll.isEmpty()) return false;
        // HF16: a partial result is simply one half of the semantic pair. It never
        // advances state; it only asks the next fresh frame to be read again.
        return normalizedAll.contains("concluido") || normalizedAll.contains("valor a receber");
    }

    private ResultScreen parseResultScreen(List<OcrLine> lines, String normalizedAll) {
        boolean completionWord = normalizedAll.contains("concluido");
        boolean receiveAction = normalizedAll.contains("receber") && !normalizedAll.equals("valor a receber");
        boolean resultButtons = normalizedAll.contains("dobrar valor") || normalizedAll.contains("ads");

        ResultScreen result = new ResultScreen();
        String all = normalizedAll.replace('\n', ' ');
        Matcher valueMatcher = Pattern.compile("valor\\s*a\\s*receber[^0-9]*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE).matcher(all);
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

        // HF16 simple result rule: while the state machine is following a real trip,
        // the semantic pair Concluído + monetary value is sufficient. Colors, dark
        // ratios, exact modal shape and secondary labels are no longer prerequisites.
        boolean simpleResult = GtoSimpleScreenDetectionPolicy.isCompletedResult(
            completionWord, !GtoMoneyValue.canonical(result.value).isEmpty()
        );
        if (!simpleResult) return null;

        // Receber geometry is intentionally optional on the first result frame. Once the
        // state enters RESULT_DETECTED the OCR cadence becomes fast and the button can be
        // mapped on a following frame without ever losing the captured result/value.
        return result;
    }

    private String observeResultValueCandidate(String rawValue, String sourceId) {
        if (prefs.getInt("resultValueConsensusVersion", 0) != GtoResultValueConsensus.SCHEMA_VERSION) {
            // R3.34: discard pre-fix monetary evidence. Older releases flattened
            // separators (e.g. 5.300,00 -> 530000), so that evidence is not safe
            // to reinterpret under the corrected money semantics.
            prefs.edit()
                .remove("resultValueEvidence")
                .remove("resultValueConsensusStable")
                .remove("resultValue")
                .remove("resultValueEvidenceCount")
                .remove("resultValueEvidenceConflict")
                .putInt("resultValueConsensusVersion", GtoResultValueConsensus.SCHEMA_VERSION)
                .commit();
        }
        String evidence = prefs.getString("resultValueEvidence", "");
        String alreadyStable = prefs.getString("resultValueConsensusStable", "");
        GtoResultValueConsensus.Decision decision = GtoResultValueConsensus.observe(
            evidence,
            sourceId,
            rawValue,
            alreadyStable
        );
        SharedPreferences.Editor editor = prefs.edit()
            .putInt("resultValueConsensusVersion", GtoResultValueConsensus.SCHEMA_VERSION)
            .putString("resultValueEvidence", decision.evidence)
            .putInt("resultValueEvidenceCount", decision.sampleCount)
            .putBoolean("resultValueEvidenceConflict", decision.conflict);
        if (!decision.stableValue.isEmpty()) {
            editor
                .putString("resultValueConsensusStable", decision.stableValue)
                .putString("resultValue", decision.stableValue)
                .putString("resultRecognitionStatus", "CONFIRMED")
                .remove("resultReviewRequiredField")
                .remove("resultValueConflictNoticeShown");
        } else {
            editor
                .remove("resultValue")
                .putString("resultRecognitionStatus", "REVIEW_REQUIRED")
                .putString("resultReviewRequiredField", GtoFreightReviewPolicy.VALUE);
        }
        if (!editor.commit()) {
            prefs.edit()
                .putString("gtoTripIntegrityError", "Falha ao persistir as evidências do valor final.")
                .putString("lastEvent", "Valor final lido, mas as evidências não puderam ser persistidas")
                .apply();
            return GtoResultValueConsensus.canonical(alreadyStable);
        }
        if (decision.conflict && decision.stableValue.isEmpty()
            && !prefs.getBoolean("resultValueConflictNoticeShown", false)) {
            prefs.edit().putBoolean("resultValueConflictNoticeShown", true).apply();
            showStatusChip(
                "Valor final ainda não confirmado: duas leituras divergiram. Mantenha a tela Concluído visível.",
                5200L
            );
        }
        return decision.stableValue;
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
            // Selection is owned exclusively by the native capture-thread coordinator.
            // OCR may describe the list, but it may NEVER promote a row by itself.
            prefs.edit()
                .putString("lastEvent", "Toque na lista recebido pelo OCR; aguardando confirmação nativa da linha selecionada")
                .apply();
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

        if (resultActionCanBeObserved(state)) {
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

    private boolean confirmFreightAfterListExit() {
        // The legacy list-close selector is intentionally disabled. Closing the freight
        // list is only a lifecycle edge; it is not proof of which row was selected.
        // Only the native touch-correlated selection transaction may promote a freight.
        prefs.edit()
            .putString("lastEvent", "Lista de fretes fechada sem transação nativa confirmada; nenhum frete foi presumido")
            .apply();
        return false;
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

    private String extractMoneyValue(String value) {
        if (value == null) return "";
        String tolerant = value.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1');
        Matcher matcher = Pattern.compile("[rR]\\s*[$sS]\\s*([0-9][0-9.,]*)").matcher(tolerant);
        if (!matcher.find()) return "";

        // Keep decimal/thousands punctuation until GtoMoneyValue has interpreted it.
        // This makes 5.300,00 / 5.300 / 5300,00 / 5300.00 converge to the same cents.
        return GtoMoneyValue.canonical("R$ " + matcher.group(1));
    }


    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private float maxDestinationConfidenceForValue(List<FreightOption> options, String destination) {
        if (options == null || destination == null || destination.trim().isEmpty()) return 0f;
        String target = GtoCityTextResolver.normalize(destination);
        float best = 0f;
        for (FreightOption option : options) {
            if (option == null) continue;
            if (target.equals(GtoCityTextResolver.normalize(option.destination))) {
                best = Math.max(best, option.destinationOcrConfidence);
            }
        }
        return best;
    }

    private GtoCityTextResolver.Resolution resolveTrustedDestination(String destination) {
        String expected = prefs == null ? "" : prefs.getString("expectedGtoDestination", "").trim();
        String json = prefs == null ? "" : prefs.getString("trustedGtoCitiesJson", "");
        if (!json.equals(trustedGtoCitiesCacheJson)) {
            List<String> parsed = new ArrayList<>();
            try {
                org.json.JSONArray array = new org.json.JSONArray(json == null || json.trim().isEmpty() ? "[]" : json);
                for (int i = 0; i < array.length() && parsed.size() < 120; i++) {
                    String city = array.optString(i, "").trim();
                    if (!city.isEmpty()) parsed.add(city);
                }
            } catch (Exception ignored) {
                parsed.clear();
            }
            trustedGtoCitiesCacheJson = json == null ? "" : json;
            trustedGtoCitiesCache = parsed;
        }
        GtoCityTextResolver.Resolution resolution = GtoCityTextResolver.resolveTrusted(
            destination,
            expected,
            trustedGtoCitiesCache
        );
        if (prefs != null && !resolution.source.isEmpty()) {
            prefs.edit()
                .putString("lastDestinationLiteralValue", destination == null ? "" : destination)
                .putString("lastDestinationLiteralEvidence", resolution.source)
                .putLong("lastDestinationLiteralEvidenceAt", System.currentTimeMillis())
                .apply();
        }
        return resolution;
    }

    private void persistArrivalCityFromSelectedFreight() {
        // Legacy R3.34-HF1 continuity is intentionally retired. The destination of a
        // completed trip must never become the next trip's origin. Origem is always the
        // source company read from the selected GTO freight card.
        prefs.edit()
            .remove("currentGtoCity")
            .remove("currentGtoCitySource")
            .remove("companyCityMap")
            .apply();
    }

    private boolean containsBonusVideo(String normalized) {
        boolean bonus = normalized.contains("bonus") || normalized.contains("bonificacao") || normalized.contains("recompensa");
        boolean video = normalized.contains("video") || normalized.contains("anuncio") || normalized.contains("assistiu");
        return bonus && video;
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

    private boolean isAllowedTripTransition(String from, String to) {
        return GtoDeterministicFlowPolicy.isAllowedTripTransition(from, to);
    }

    private boolean repairTripStateFromDurableFreightIfNeeded() {
        if (!STATE_WAITING_FREIGHT.equals(getTripState())) return false;
        String sessionId = prefs.getString("gtoTripSessionId", "").trim();
        if (sessionId.isEmpty()) return false;
        boolean lockedSnapshot = GtoAutoTripSync.hasRecoverableSessionSnapshot(this, sessionId, true);
        if (!lockedSnapshot) return false;
        boolean restored = GtoAutoTripSync.restoreLockedFreightToPrefs(this, prefs, sessionId);
        if (!GtoDeterministicFlowPolicy.mayRepairWaitingToTrip(getTripState(), lockedSnapshot, restored)) return false;
        setTripStateInternal(STATE_TRIP_IN_PROGRESS,
            "Estado corrigido a partir do snapshot imutável do frete confirmado", true);
        prefs.edit()
            .putString("gtoTripIntegrityStatus", "DURABLE_FREIGHT_STATE_REPAIRED")
            .remove("gtoTripIntegrityError")
            .putLong("gtoDurableStateRepairAt", System.currentTimeMillis())
            .apply();
        return STATE_TRIP_IN_PROGRESS.equals(getTripState());
    }

    private void setTripState(String state, String event) {
        setTripStateInternal(state, event, false);
    }

    private void setTripStateInternal(String state, String event, boolean durableRecovery) {
        String previous = getTripState();
        boolean normalTransition = isAllowedTripTransition(previous, state);
        boolean recoveryTransition = durableRecovery
            && GtoDeterministicFlowPolicy.mayRepairWaitingToTrip(previous, true, true)
            && STATE_TRIP_IN_PROGRESS.equals(state);
        if (!normalTransition && !recoveryTransition) {
            prefs.edit()
                .putString("gtoTripIntegrityStatus", "STATE_CONFLICT")
                .putString("gtoTripIntegrityError", "Transição GTO inválida bloqueada: " + previous + " -> " + state)
                .putString("lastEvent", "Transição de estado inválida bloqueada")
                .apply();
            recordObserverIncident("STATE_CONFLICT", previous + " -> " + state);
            return;
        }
        lastStateChangeAt = System.currentTimeMillis();
        prefs.edit()
            .putString("tripState", state)
            .putString("lastEvent", event)
            .putLong("tripStateChangedAt", lastStateChangeAt)
            .putBoolean("gtoCanonicalStatePending", true)
            .putString("gtoCanonicalPendingState", state)
            .putString("gtoCanonicalPendingFrom", previous)
            .apply();
        recordObserverEvent("STATE_" + state, previous + " -> " + state + (event == null || event.isEmpty() ? "" : " · " + event));
        if (STATE_CONFIRMING_FREIGHT.equals(state)) {
            prefs.edit()
                .remove("selectionConfirmationStatus")
                .remove("selectionFailureReason")
                .remove("selectionFailureAt")
                .apply();
            if (!STATE_CONFIRMING_FREIGHT.equals(previous)) armFreightConfirmationWatchdog();
        } else if (STATE_CONFIRMING_FREIGHT.equals(previous)) {
            freightConfirmationWatchdogGeneration++;
        }
        final String canonicalSyncSessionId = prefs.getString("gtoTripSessionId", "");
        final String canonicalSyncState = state;
        GtoAutoTripSync.syncCanonicalState(this, prefs, previous, state, event)
            .addOnSuccessListener(ignored -> {
                if (!canonicalSyncCallbackIsCurrent(canonicalSyncSessionId, canonicalSyncState)) return;
                prefs.edit()
                    .putBoolean("gtoCanonicalStatePending", false)
                    .putString("gtoCanonicalStateLastSynced", canonicalSyncState)
                    .putLong("gtoCanonicalStateLastSyncedAt", System.currentTimeMillis())
                    .remove("gtoCanonicalStateError")
                    .apply();
            })
            .addOnFailureListener(error -> {
                if (!canonicalSyncCallbackIsCurrent(canonicalSyncSessionId, canonicalSyncState)) return;
                prefs.edit()
                    .putBoolean("gtoCanonicalStatePending", true)
                    .putString("gtoCanonicalStateError", error == null ? "Falha desconhecida" : String.valueOf(error.getMessage()))
                    .apply();
                mainHandler.postDelayed(() -> retryCanonicalStateSync(), 2500L);
            });
        updateNotification();
        mainHandler.post(this::updateFreightTouchPulseSensor);
        if (menuView != null) mainHandler.post(this::refreshMenuContents);
    }

    private void armFreightConfirmationWatchdog() {
        final long generation = ++freightConfirmationWatchdogGeneration;
        mainHandler.postDelayed(() -> {
            if (generation != freightConfirmationWatchdogGeneration) return;
            if (!STATE_CONFIRMING_FREIGHT.equals(getTripState())) return;
            // Once the GTO transition confirms the touched row identity, OCR timeout is
            // field-level only. Never send the driver back to select the freight again.
            if (isFreightReviewPending()) return;
            if (screenAnalysisPausedOutsideGto || !gtoForeground) return;
            int row = preciseSelectedRow >= 0
                ? preciseSelectedRow
                : prefs.getInt("preciseSelectedRow", -1);
            preciseSelectionOcrGeneration++;
            preciseSelectionOcrBusy = false;
            if (hasConfirmedSelectionIdentity() && row >= 0) {
                FreightOption stable = stableFreightForRow(row);
                enterFreightReview(
                    stable, row,
                    "A leitura demorou além do esperado. A linha selecionada continua confirmada; revise apenas o campo ausente.",
                    ""
                );
                return;
            }
            // No compatible transition occurred after the touch: fail closed without
            // inventing an accepted freight.
            restoreWaitingAfterSelectionFailure(
                row,
                "O toque não teve confirmação visual de aceite. Nenhum frete foi confirmado. Nenhum dado foi registrado."
            );
        }, FREIGHT_CONFIRMATION_WATCHDOG_MS);
    }

    private boolean canonicalSyncCallbackIsCurrent(String sessionId, String state) {
        if (prefs == null) return false;
        String currentSessionId = prefs.getString("gtoTripSessionId", "");
        String currentPendingState = prefs.getString("gtoCanonicalPendingState", "");
        return sessionId != null && state != null
            && sessionId.equals(currentSessionId)
            && state.equals(currentPendingState);
    }

    private void retryCanonicalStateSync() {
        if (!running || prefs == null || !prefs.getBoolean("gtoCanonicalStatePending", false)) return;
        final String sessionId = prefs.getString("gtoTripSessionId", "");
        final String state = prefs.getString("gtoCanonicalPendingState", "");
        String from = prefs.getString("gtoCanonicalPendingFrom", "");
        if (sessionId.isEmpty() || state.isEmpty()) return;
        GtoAutoTripSync.syncCanonicalState(this, prefs, from, state, prefs.getString("lastEvent", ""))
            .addOnSuccessListener(ignored -> {
                if (!canonicalSyncCallbackIsCurrent(sessionId, state)) return;
                prefs.edit()
                    .putBoolean("gtoCanonicalStatePending", false)
                    .putString("gtoCanonicalStateLastSynced", state)
                    .putLong("gtoCanonicalStateLastSyncedAt", System.currentTimeMillis())
                    .remove("gtoCanonicalStateError")
                    .apply();
            })
            .addOnFailureListener(error -> {
                if (!canonicalSyncCallbackIsCurrent(sessionId, state)) return;
                prefs.edit().putString("gtoCanonicalStateError", error == null ? "Falha desconhecida" : String.valueOf(error.getMessage())).apply();
                mainHandler.postDelayed(this::retryCanonicalStateSync, 5000L);
            });
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
        String readiness = prefs.getString("captureReadiness", "");
        String visualSource = prefs.getString("lastVisualGtoEvidenceSource", "");
        String line = "Leitura: " + screenState + " · fretes: " + count
            + "\nCaptura: " + savedProjection + (readiness.isEmpty() ? "" : " · " + readiness) + " · " + captureWidth + "x" + captureHeight
            + " / tela " + screen.widthPixels + "x" + screen.heightPixels
            + "\nToques externos: " + touches
            + " · último " + Math.round(prefs.getFloat("lastOutsideTouchX", -1f)) + "," + Math.round(prefs.getFloat("lastOutsideTouchY", -1f));
        String pending = prefs.getString("pendingSelectionSource", "");
        if (!pending.isEmpty()) line += "\nCandidato: " + truncate(pending, 58);
        if (visualFreightSelection != null) {
            line += "\nVisual: linha " + (visualFreightSelection.rowIndex + 1)
                + " · conf " + Math.round(visualSelectionConfidence * 100f) + "%";
        }
        if (!visualSource.isEmpty()) line += "\nProva visual GTO: " + truncate(visualSource, 42);
        if (!projectionError.isEmpty()) line += "\nErro: " + truncate(projectionError, 74);
        return line;
    }

    private String statusLabel(String state) {
        if (STATE_WAITING_FREIGHT.equals(state)) {
            int detected = prefs == null ? 0 : prefs.getInt("freightCount", 0);
            return detected > 0 ? "Lista de fretes detectada" : "Escolha seu frete";
        }
        if (STATE_CONFIRMING_FREIGHT.equals(state)) {
            return isFreightReviewPending() ? "Frete selecionado · revisão necessária" : "Confirmando frete";
        }
        if (STATE_TRIP_IN_PROGRESS.equals(state)) return "";
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
            json.put("rowId", "row-" + (option.rowIndex + 1));
            JSONObject bounds = new JSONObject();
            bounds.put("top", option.rowTop);
            bounds.put("bottom", option.rowBottom);
            json.put("bounds", bounds);
            if (option.acceptRect != null) {
                JSONObject acceptRect = new JSONObject();
                acceptRect.put("left", option.acceptRect.left);
                acceptRect.put("top", option.acceptRect.top);
                acceptRect.put("right", option.acceptRect.right);
                acceptRect.put("bottom", option.acceptRect.bottom);
                json.put("acceptRect", acceptRect);
            }
            // Keep Web-facing legacy aliases while also writing the canonical durable
            // contract consumed by GtoAutoTripSync.
            json.put("rowIndex", option.rowIndex);
            json.put("selectedRow", option.rowIndex);
            json.put("cargo", option.cargo);
            json.put("companyRoute", option.companyRoute);
            json.put("originCompany", option.originCompany);
            json.put("destinationCompany", option.destinationCompany);
            json.put("origin", option.origin);
            json.put("destination", option.destination);
            json.put("km", option.km);
            json.put("distanceKm", option.km);
            json.put("offeredValue", option.offeredValue);
            json.put("rawText", option.rawText);
            json.put("confidence", Math.round(option.dataConfidence * 100f));
            json.put("consensusFrames", option.consensusFrames);
            json.put("cargoVotes", option.cargoVotes);
            json.put("originCompanyVotes", option.originCompanyVotes);
            json.put("destinationCompanyVotes", option.destinationCompanyVotes);
            json.put("destinationVotes", option.destinationVotes);
            json.put("kmVotes", option.kmVotes);
            json.put("valueVotes", option.valueVotes);
            JSONObject fieldConfidence = new JSONObject();
            fieldConfidence.put("cargo", fieldConfidencePercent(option.cargo, option.cargoVotes, option.cargoSelectedRowEvidence));
            fieldConfidence.put("originCompany", fieldConfidencePercent(option.originCompany, option.originCompanyVotes, option.originCompanySelectedRowEvidence));
            fieldConfidence.put("destinationCompany", fieldConfidencePercent(option.destinationCompany, option.destinationCompanyVotes, option.destinationCompanySelectedRowEvidence));
            fieldConfidence.put("destination", fieldConfidencePercent(option.destination, option.destinationVotes, option.destinationSelectedRowEvidence));
            fieldConfidence.put("distance", fieldConfidencePercent(option.km, option.kmVotes, option.kmSelectedRowEvidence));
            fieldConfidence.put("value", fieldConfidencePercent(option.offeredValue, option.valueVotes, option.valueSelectedRowEvidence));
            json.put("fieldConfidence", fieldConfidence);
            JSONObject fieldStatus = new JSONObject();
            fieldStatus.put("cargo", fieldStatus(option.cargo, option.cargoVotes, option.cargoSelectedRowEvidence));
            fieldStatus.put("originCompany", fieldStatus(option.originCompany, option.originCompanyVotes, option.originCompanySelectedRowEvidence));
            fieldStatus.put("destinationCompany", fieldStatus(option.destinationCompany, option.destinationCompanyVotes, option.destinationCompanySelectedRowEvidence));
            fieldStatus.put("destination", fieldStatus(option.destination, option.destinationVotes, option.destinationSelectedRowEvidence));
            fieldStatus.put("distance", fieldStatus(option.km, option.kmVotes, option.kmSelectedRowEvidence));
            fieldStatus.put("value", fieldStatus(option.offeredValue, option.valueVotes, option.valueSelectedRowEvidence));
            json.put("fieldStatus", fieldStatus);
        } catch (Exception ignored) {}
        return json;
    }

    private int fieldConfidencePercent(String value, int votes, boolean selectedRowEvidence) {
        if (value == null || value.trim().isEmpty()) return 0;
        if (votes >= 2 || selectedRowEvidence) return 100;
        if (votes == 1) return 60;
        return 35;
    }

    private String fieldStatus(String value, int votes, boolean selectedRowEvidence) {
        if (value == null || value.trim().isEmpty()) return "UNKNOWN";
        return votes >= 2 || selectedRowEvidence ? "CONFIRMED" : "UNCERTAIN";
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
                Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
                metrics.widthPixels = Math.max(1, bounds.width());
                metrics.heightPixels = Math.max(1, bounds.height());
                metrics.density = getResources().getDisplayMetrics().density;
                metrics.densityDpi = getResources().getConfiguration().densityDpi;
                return metrics;
            }
        } catch (Exception ignored) {
        }
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        return metrics;
    }

    private int freightOverlaySafeRight(DisplayMetrics screen) {
        if (screen == null || screen.widthPixels <= 0 || prefs == null) return Integer.MAX_VALUE;
        String state = getTripState();
        if ((!STATE_WAITING_FREIGHT.equals(state) && !STATE_CONFIRMING_FREIGHT.equals(state))
            || isFreightReviewPending()) {
            return screen.widthPixels;
        }
        long panelAt = prefs.getLong("freightPanelScreenAt", 0L);
        int panelLeft = prefs.getInt("freightPanelLeftScreen", 0);
        if (panelLeft <= dp(72) || System.currentTimeMillis() - panelAt > 3200L) {
            return screen.widthPixels;
        }
        return clamp(panelLeft - dp(8), dp(72), screen.widthPixels);
    }

    private void keepOverlaysClearOfFreightPanel(int capturePanelLeft, int capturedWidth) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> keepOverlaysClearOfFreightPanel(capturePanelLeft, capturedWidth));
            return;
        }
        if (capturedWidth <= 0 || windowManager == null) return;
        String state = getTripState();
        boolean freightListInteractionActive = STATE_WAITING_FREIGHT.equals(state)
            || (STATE_CONFIRMING_FREIGHT.equals(state) && !isFreightReviewPending());
        // HF14 root-cause fix: cacheFastFreightPanel() can enqueue this UI callback from
        // the last list frame. Once row identity is confirmed/review is open, that stale
        // callback must not close the menu to protect a list that is no longer actionable.
        if (!freightListInteractionActive) return;
        DisplayMetrics screen = realDisplayMetrics();
        if (screen.widthPixels <= 0) return;
        int panelLeft = clamp(
            Math.round(screen.widthPixels * capturePanelLeft / (float) capturedWidth),
            0,
            screen.widthPixels
        );
        prefs.edit()
            .putInt("freightPanelLeftScreen", panelLeft)
            .putLong("freightPanelScreenAt", System.currentTimeMillis())
            .apply();

        int safeRight = Math.max(dp(72), panelLeft - dp(8));
        boolean moved = false;
        if (menuView != null && menuParams != null && menuView.isAttachedToWindow()) {
            int menuWidth = Math.max(dp(256), menuView.getWidth());
            int availableSafeWidth = Math.max(0, safeRight - dp(16));
            if (availableSafeWidth < menuWidth) {
                // A real actionable freight list has priority over an open informational
                // card. If the safe strip cannot contain the full readable card, minimize
                // only the card instead of shrinking it or blocking an Accept button.
                closeMenu();
                prefs.edit()
                    .putLong("menuMinimizedForFreightListAt", System.currentTimeMillis())
                    .putString("lastEvent", "Card NVU minimizado para liberar a lista de fretes")
                    .apply();
                moved = true;
            }
        }
        if (bubbleView != null && bubbleParams != null && bubbleView.isAttachedToWindow()) {
            int protectedBubbleWidth = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : dp(69);
            int maxBubbleX = Math.max(dp(8), safeRight - protectedBubbleWidth - dp(8));
            if (bubbleParams.x > maxBubbleX) {
                bubbleParams.x = maxBubbleX;
                try {
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                    moved = true;
                } catch (Exception ex) {
                    recordOverlayFailure(ex);
                }
            }
        }
        if (menuView != null && menuParams != null && menuView.isAttachedToWindow()) {
            int menuWidth = Math.max(dp(256), menuView.getWidth());
            int maxMenuX = Math.max(dp(8), safeRight - menuWidth - dp(8));
            if (menuParams.x > maxMenuX) {
                // HF15: detector protection may move a user-opened card, never close it.
                // Closing here races with row confirmation callbacks and looks exactly like
                // a broken bubble tap on slower devices.
                menuParams.x = maxMenuX;
                try {
                    windowManager.updateViewLayout(menuView, menuParams);
                    moved = true;
                } catch (Exception ex) {
                    prefs.edit().putString("menuOverlayError", describeError(ex)).apply();
                }
            }
        }
        if (!statusChipIsDriverStage
            && statusChipView != null && statusChipParams != null && statusChipView.isAttachedToWindow()) {
            int chipWidth = Math.min(dp(300), Math.max(dp(120), safeRight - dp(16)));
            statusChipView.setMaxWidth(chipWidth);
            int maxChipX = Math.max(dp(8), safeRight - chipWidth - dp(8));
            if (statusChipParams.x > maxChipX) {
                statusChipParams.x = maxChipX;
                try {
                    windowManager.updateViewLayout(statusChipView, statusChipParams);
                    moved = true;
                } catch (Exception ex) {
                    hideStatusChip();
                }
            }
        }
        if (moved) {
            prefs.edit()
                .putLong("overlayOcclusionPreventedAt", System.currentTimeMillis())
                .putString("lastEvent", "Painel NVU reposicionado para não encobrir a lista de fretes")
                .apply();
            if (menuView != null) menuView.post(this::adjustOpenMenuLayoutAfterMeasure);
        }
    }

    private Rect resultProbeRegionOnScreen(DisplayMetrics screen) {
        if (screen == null || screen.widthPixels <= 0 || screen.heightPixels <= 0) return new Rect();
        return new Rect(
            Math.round(screen.widthPixels * 0.34f),
            Math.round(screen.heightPixels * 0.31f),
            Math.round(screen.widthPixels * 0.66f),
            Math.round(screen.heightPixels * 0.69f)
        );
    }

    private boolean ownMenuOccludesResultProbe() {
        if (menuView == null || menuParams == null || !menuView.isAttachedToWindow()) return false;
        DisplayMetrics screen = realDisplayMetrics();
        Rect probe = resultProbeRegionOnScreen(screen);
        int width = menuView.getWidth() > 0 ? menuView.getWidth()
            : (menuParams.width > 0 ? menuParams.width : dp(256));
        int height = menuView.getHeight() > 0 ? menuView.getHeight() : dp(180);
        Rect menu = new Rect(menuParams.x, menuParams.y, menuParams.x + width, menuParams.y + height);
        return Rect.intersects(menu, probe);
    }

    private void keepOverlaysClearOfResultRegion() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::keepOverlaysClearOfResultRegion);
            return;
        }
        if (windowManager == null) return;
        DisplayMetrics screen = realDisplayMetrics();
        if (screen.widthPixels <= 0 || screen.heightPixels <= 0) return;
        Rect protectedRegion = resultProbeRegionOnScreen(screen);
        boolean moved = false;

        if (menuView != null && menuParams != null && menuView.isAttachedToWindow()) {
            int width = menuView.getWidth() > 0 ? menuView.getWidth()
                : (menuParams.width > 0 ? menuParams.width : dp(256));
            int height = menuView.getHeight() > 0 ? menuView.getHeight() : dp(180);
            Rect menu = new Rect(menuParams.x, menuParams.y, menuParams.x + width, menuParams.y + height);
            if (Rect.intersects(menu, protectedRegion)) {
                int margin = dp(8);
                // HF19: never compress a 256dp card into a narrow side column. Fixed
                // child widths and review controls made that visually unstable. Move the
                // full-width card only when one side can contain it; otherwise preserve
                // its width and let result analysis mask the known NVU overlay pixels.
                int leftX = margin;
                int rightX = Math.max(margin, screen.widthPixels - width - margin);
                Rect leftCandidate = new Rect(leftX, menuParams.y, leftX + width, menuParams.y + height);
                Rect rightCandidate = new Rect(rightX, menuParams.y, rightX + width, menuParams.y + height);
                boolean leftClear = !Rect.intersects(leftCandidate, protectedRegion);
                boolean rightClear = !Rect.intersects(rightCandidate, protectedRegion);
                if (leftClear || rightClear) {
                    if (leftClear && rightClear) {
                        int leftRoom = protectedRegion.left - margin;
                        int rightRoom = screen.widthPixels - protectedRegion.right - margin;
                        menuParams.x = rightRoom >= leftRoom ? rightX : leftX;
                    } else {
                        menuParams.x = rightClear ? rightX : leftX;
                    }
                    try {
                        windowManager.updateViewLayout(menuView, menuParams);
                        moved = true;
                    } catch (Exception ex) {
                        prefs.edit().putString("menuOverlayError", describeError(ex)).apply();
                    }
                }
            }
        }

        if (bubbleView != null && bubbleParams != null && bubbleView.isAttachedToWindow()) {
            int size = Math.max(dp(56), bubbleView.getWidth());
            Rect bubble = new Rect(bubbleParams.x, bubbleParams.y, bubbleParams.x + size, bubbleParams.y + size);
            if (Rect.intersects(bubble, protectedRegion)) {
                bubbleParams.x = dp(8);
                bubbleParams.y = clamp(bubbleParams.y, dp(8), Math.max(dp(8), screen.heightPixels - size - dp(8)));
                try {
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                    moved = true;
                } catch (Exception ex) {
                    recordOverlayFailure(ex);
                }
            }
        }
        if (!statusChipIsDriverStage
            && statusChipView != null
            && statusChipParams != null
            && statusChipView.isAttachedToWindow()) {
            int width = Math.max(dp(140), statusChipView.getWidth());
            int height = Math.max(dp(44), statusChipView.getHeight());
            Rect chip = new Rect(statusChipParams.x, statusChipParams.y, statusChipParams.x + width, statusChipParams.y + height);
            if (Rect.intersects(chip, protectedRegion)) {
                hideStatusChip();
                moved = true;
            }
        }
        if (moved) {
            prefs.edit()
                .putLong("overlayOcclusionPreventedAt", System.currentTimeMillis())
                .putString("overlayProtectedRegion", "RESULT")
                .putString("lastEvent", "Overlay NVU reposicionado sem fechar o card")
                .apply();
            if (menuView != null) menuView.post(this::adjustOpenMenuLayoutAfterMeasure);
        }
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
        projectionSurfacePending = false;
        projectionSessionBoundAt = 0L;
        resetPendingProjectionSurfaceStability();
        projectionStartedAt = 0L;
        lastProjectionSurfaceRecoveryAt = 0L;
        lastProjectionFrameAt = 0L;
        lastProjectionAnalyzedFrameAt = 0L;
        projectionSurfaceRebindAttempts = 0;
        pendingCapturedWidth = 0;
        pendingCapturedHeight = 0;
        lastCaptureGeometryPollAt = 0L;
        lastCaptureGeometryMatched = false;
        if (wasActive) projectionStatus = "STOPPED";
        resetCaptureStabilityBarrier(
            GtoCaptureStabilityGate.INACTIVE,
            0,
            0,
            wasActive ? "Leitura da tela interrompida" : null
        );
        prefs.edit()
            .putBoolean("projectionActive", false)
            .putBoolean("projectionSessionBound", false)
            .putBoolean("projectionSurfacePending", false)
            .putBoolean("projectionGrantValidated", false)
            .remove("projectionSessionBoundAt")
            .putBoolean("captureSurfaceReady", false)
            .remove("projectionFirstFrameAt")
            .remove("projectionSurfaceRebindAttempts")
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
                        detectedResultValue = observeResultValueCandidate(
                            recovered,
                            "snapshot-" + prefs.getLong("resultSnapshotAt", 0L)
                        );
                        if (!detectedResultValue.isEmpty()) {
                            prefs.edit()
                                .remove("gtoTripIntegrityError")
                                .putString("lastEvent", "Valor final confirmado pela captura local preservada")
                                .apply();
                            deleteResultSnapshot();
                            mainHandler.post(this::confirmNormalResultAutomatically);
                        } else {
                            prefs.edit()
                                .putString("gtoTripIntegrityError", "A captura foi relida, mas o valor final ainda não alcançou consenso.")
                                .putString("lastEvent", "Valor recuperado como candidato; aguardando segunda evidência concordante")
                                .apply();
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
        if (screenAnalysisPausedOutsideGto || !gtoForeground) {
            if (STATE_RESULT_DETECTED.equals(currentState) || STATE_AWAITING_BONUS.equals(currentState)) {
                deferredNormalResultConfirmation = true;
                prefs.edit()
                    .putString("lastEvent", "Conclusão preservada · aguardando retorno ao GTO para finalizar")
                    .apply();
            }
            return;
        }
        if (STATE_RESULT_CONFIRMED.equals(currentState)) return;
        if (!STATE_RESULT_DETECTED.equals(currentState) && !STATE_AWAITING_BONUS.equals(currentState)) return;
        if (detectedResultValue == null || detectedResultValue.trim().isEmpty()) {
            // resultValue is display data; only the dedicated immutable consensus key
            // is authoritative for completing/syncing a trip.
            detectedResultValue = prefs.getInt("resultValueConsensusVersion", 0) == GtoResultValueConsensus.SCHEMA_VERSION
                ? GtoResultValueConsensus.canonical(prefs.getString("resultValueConsensusStable", ""))
                : "";
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
                .putString("resultRecognitionStatus", "REVIEW_REQUIRED")
                .putString("resultReviewRequiredField", GtoFreightReviewPolicy.VALUE)
                .putString("gtoTripIntegrityError", "Receber confirmado, mas o valor final ainda não alcançou duas leituras concordantes.")
                .putString("lastEvent", "Receber confirmado; aguardando consenso do valor final")
                .apply();
            showStatusChip("Receber confirmado · finalizando a leitura do resultado. A viagem permanece preservada.", 4600L);
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
            "Enviando viagem automaticamente...",
            4200L,
            false
        );
        String completedSessionId = prefs.getString("gtoTripSessionId", "");
        boolean queued = GtoAutoTripSync.enqueueConfirmedTrip(this, prefs, automaticTripSyncListener());
        if (queued && prepareNextFreightFromSealedQueue(completedSessionId)) {
            announceDriverStage(
                "QUEUED_NEXT_READY",
                "Enviando viagem automaticamente... Próximo frete liberado.",
                4200L,
                true
            );
            updateNotification();
            if (menuView != null) refreshMenuContents();
        }
    }

    private GtoAutoTripSync.Listener automaticTripSyncListener() {
        return new GtoAutoTripSync.Listener() {
            @Override
            public void onSynced(String sessionId, String tripId) {
                mainHandler.post(() -> {
                    recordObserverEvent("SYNCED", "session=" + sessionId + " tripId=" + (tripId == null ? "" : tripId));
                    String currentSession = prefs.getString("gtoTripSessionId", "");
                    if (!sessionId.equals(currentSession)) {
                        String previousQueuedSession = prefs.getString("gtoPreviousQueuedSessionId", "");
                        if (!sessionId.isEmpty() && sessionId.equals(previousQueuedSession)) {
                            prefs.edit()
                                .putString("backgroundSyncLastSessionId", sessionId)
                                .putString("backgroundSyncLastTripId", tripId == null ? "" : tripId)
                                .putLong("backgroundSyncLastAckAt", System.currentTimeMillis())
                                .remove("gtoPreviousQueuedSessionId")
                                .apply();
                            announceDriverStage(
                                "SYNCED_BACKGROUND",
                                "Viagem enviada com sucesso!",
                                3600L,
                                true
                            );
                            updateNotification();
                            if (menuView != null) refreshMenuContents();
                        }
                        return;
                    }
                    boolean operationClosed = isOperationClosedForNewTrip();
                    if (operationClosed) {
                        announceDriverStage(
                            "SYNCED",
                            "Viagem enviada com sucesso!",
                            4400L,
                            true
                        );
                    } else if (GtoDeterministicFlowPolicy.shouldAutoPrepareNextFreightAfterSync(
                        getTripState(),
                        GtoAutoTripSync.STATUS_SYNCED.equals(prefs.getString("gtoTripSyncStatus", "")),
                        false
                    )) {
                        // ACK of the completed delivery is the only automatic boundary into
                        // the next trip. Start a fresh durable session immediately so the
                        // next real jobs list can be detected without reopening NVU.
                        String completedSession = sessionId;
                        beginTrip(false, false);
                        if (STATE_WAITING_FREIGHT.equals(getTripState())) {
                            prefs.edit()
                                .putLong("gtoAutoNextTripPreparedAt", System.currentTimeMillis())
                                .putString("gtoAutoNextTripFromSession", completedSession)
                                .apply();
                            announceDriverStage(
                                "SYNCED_NEXT_READY",
                                "Viagem enviada com sucesso!",
                                4200L,
                                true
                            );
                        } else {
                            announceDriverStage(
                                "SYNCED",
                                "Viagem enviada com sucesso!",
                                3600L,
                                true
                            );
                        }
                    } else {
                        announceDriverStage(
                            "SYNCED",
                            "Viagem enviada com sucesso!",
                            3600L,
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
                    recordObserverEvent("SYNC_PENDING", message == null ? "" : message);
                    String currentSession = prefs.getString("gtoTripSessionId", "");
                    if (!sessionId.isEmpty() && !sessionId.equals(currentSession)) {
                        prefs.edit()
                            .putString("backgroundSyncPendingSessionId", sessionId)
                            .putString("backgroundSyncPendingDetail", message == null ? "" : message.trim())
                            .putLong("backgroundSyncPendingAt", System.currentTimeMillis())
                            .apply();
                        return;
                    }
                    String detail = message == null ? "" : message.trim();
                    String lowerDetail = detail.toLowerCase(Locale.ROOT);
                    boolean networkLikely = lowerDetail.contains("network")
                        || lowerDetail.contains("offline")
                        || lowerDetail.contains("conex")
                        || lowerDetail.contains("unavailable")
                        || lowerDetail.contains("timeout");
                    String stageMessage = networkLikely
                        ? "Sem conexão. Viagem salva e aguardando envio."
                        : "Viagem salva e aguardando envio.";
                    prefs.edit()
                        .putString("gtoTripSyncError", detail)
                        .apply();
                    announceDriverStage("SYNC_PENDING", stageMessage, 3600L, false);
                    updateNotification();
                    if (menuView != null) refreshMenuContents();
                });
            }
        };
    }

    private void flushAutomaticTripQueue() {
        GtoAutoTripSync.flushPending(this, prefs, automaticTripSyncListener());
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
        final long pageGeneration;
        final FreightOption baselineOption;
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
            long generation,
            long pageGeneration,
            FreightOption baselineOption
        ) {
            this.rowIndex = rowIndex;
            this.panelFrame = panelFrame;
            this.panelOffsetX = panelOffsetX;
            this.buttons = buttons;
            this.source = source;
            this.touchSequence = touchSequence;
            this.sessionId = sessionId == null ? "" : sessionId;
            this.generation = generation;
            this.pageGeneration = pageGeneration;
            this.baselineOption = baselineOption;
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
        float destinationOcrConfidence = 0f;
        String km = "";
        String offeredValue = "";
        String rawText = "";
        boolean acceptTextEvidence = false;
        float dataConfidence = 0f;
        int consensusFrames = 0;
        int cargoVotes = 0;
        int originCompanyVotes = 0;
        int destinationCompanyVotes = 0;
        int destinationVotes = 0;
        int kmVotes = 0;
        int valueVotes = 0;
        // Independent evidence from the immutable selected-row snapshot. These flags
        // never choose a row; they only allow a valid field from the already-touched
        // row to stand on its own without manufacturing a second OCR vote.
        boolean cargoSelectedRowEvidence = false;
        boolean originCompanySelectedRowEvidence = false;
        boolean destinationCompanySelectedRowEvidence = false;
        boolean destinationSelectedRowEvidence = false;
        boolean kmSelectedRowEvidence = false;
        boolean valueSelectedRowEvidence = false;
        String originCompanyEvidenceSource = "";

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
