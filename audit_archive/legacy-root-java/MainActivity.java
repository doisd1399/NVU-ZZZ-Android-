package com.nvu.operacional;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    public static final String EXTRA_NATIVE_ROUTE = "nvuNativeRoute";
    public static final String EXTRA_NATIVE_PROFILE_TAB = "nvuNativeProfileTab";
    public static final String EXTRA_REQUEST_GTO_PROJECTION = "nvuRequestGtoProjection";
    public static final String EXTRA_RETURN_TO_GTO_AFTER_PROJECTION = "nvuReturnToGtoAfterProjection";

    private static final int REQUEST_GTO_CAPTURE = 9108;
    private boolean projectionRequestLaunching = false;
    private boolean returnToGtoAfterProjection = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(GtoObserverPlugin.class);
        super.onCreate(savedInstanceState);
        maybeHandleProjectionRequest(getIntent());
    }

    @Override
    public void onStart() {
        super.onStart();
        GtoObserverService.recoverIfEnabled(this);
        // Durable completed deliveries must be retried even when the driver has
        // temporarily disabled the floating observer. Authentication is checked
        // before touching the queue so the login screen does not inherit another
        // user's pending status on shared devices.
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            GtoAutoTripSync.flushPending(
                this,
                getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE),
                null
            );
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatchNativeNavigation(intent);
        maybeHandleProjectionRequest(intent);
    }

    /**
     * Recovery path used only when Android ends MediaProjection while the driver is
     * already inside GTO. The NVU task intentionally becomes foreground, asks for the
     * Android consent there, then reopens the existing GTO task. This avoids creating a
     * transparent root task above the game, which some OEMs render as a gray/blank screen.
     */
    private void maybeHandleProjectionRequest(Intent intent) {
        if (intent == null || projectionRequestLaunching) return;
        if (!intent.getBooleanExtra(EXTRA_REQUEST_GTO_PROJECTION, false)) return;

        returnToGtoAfterProjection = intent.getBooleanExtra(EXTRA_RETURN_TO_GTO_AFTER_PROJECTION, false);
        intent.removeExtra(EXTRA_REQUEST_GTO_PROJECTION);
        intent.removeExtra(EXTRA_RETURN_TO_GTO_AFTER_PROJECTION);
        projectionRequestLaunching = true;

        SharedPreferences prefs = getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
            .putBoolean("projectionPermissionInFlight", true)
            .putString("projectionStatus", "REQUESTING_PERMISSION_APP")
            .remove("projectionError")
            .putString("lastEvent", "NVU aberta para renovar a autorização de leitura da tela")
            .apply();
        GtoObserverService.markProjectionPermissionInFlightIfRunning();

        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) throw new IllegalStateException("MediaProjectionManager indisponível");

            Intent captureIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = manager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay()
                );
            } else {
                captureIntent = manager.createScreenCaptureIntent();
            }
            prefs.edit()
                .putString("projectionStatus", "CONSENT_VISIBLE_APP")
                .putString("lastEvent", "Android exibiu a autorização de leitura na NVU")
                .apply();
            startActivityForResult(captureIntent, REQUEST_GTO_CAPTURE);
        } catch (Exception ex) {
            projectionRequestLaunching = false;
            String detail = GtoObserverService.describeError(ex);
            GtoObserverService.reportProjectionPermissionTerminalFailure(
                this, "CONSENT_LAUNCH_FAILED_APP", detail
            );
            Toast.makeText(this, "Não foi possível abrir a autorização de leitura da tela.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_GTO_CAPTURE) return;

        projectionRequestLaunching = false;
        boolean granted = resultCode == Activity.RESULT_OK && data != null;
        SharedPreferences prefs = getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
            .putBoolean("projectionPermissionInFlight", false)
            .putString("projectionStatus", granted ? "CONSENT_GRANTED_APP" : "DENIED")
            .putString("lastEvent", granted
                ? "Autorização renovada; iniciando leitura da tela"
                : "Autorização de leitura recusada")
            .apply();

        Intent serviceIntent = new Intent(this, GtoObserverService.class);
        if (granted) {
            serviceIntent.setAction(GtoObserverService.ACTION_START_PROJECTION);
            serviceIntent.putExtra(GtoObserverService.EXTRA_RESULT_CODE, resultCode);
            serviceIntent.putExtra(GtoObserverService.EXTRA_RESULT_DATA, data);
        } else {
            serviceIntent.setAction(GtoObserverService.ACTION_PROJECTION_DENIED);
        }

        try {
            if (GtoObserverService.isRunning()) {
                startService(serviceIntent);
            } else {
                ContextCompat.startForegroundService(this, serviceIntent);
            }
        } catch (Exception ex) {
            GtoObserverService.reportProjectionPermissionTerminalFailure(
                this, "SERVICE_DISPATCH_FAILED_APP", GtoObserverService.describeError(ex)
            );
            Toast.makeText(this, "Não foi possível ativar a leitura da tela.", Toast.LENGTH_LONG).show();
            return;
        }

        if (granted && returnToGtoAfterProjection) {
            reopenGtoWhenProjectionReady(0);
        } else if (!granted) {
            Toast.makeText(
                this,
                "A leitura da tela é necessária para a automação GTO. Autorize e tente novamente.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void reopenGtoWhenProjectionReady(int attempt) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE);
            boolean active = prefs.getBoolean("projectionActive", false);
            String status = prefs.getString("projectionStatus", "");
            boolean failed = "START_FAILED".equals(status)
                || "DENIED".equals(status)
                || status.startsWith("PERMISSION_")
                || status.startsWith("SERVICE_DISPATCH_FAILED");

            if (active) {
                try {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(GtoObserverService.GTO_PACKAGE);
                    if (launchIntent == null) {
                        Toast.makeText(this, "Global Truck Online não foi encontrado neste aparelho.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.edit()
                        .putString("lastEvent", "Leitura autorizada · retornando ao GTO")
                        .apply();
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(launchIntent);
                } catch (Exception ex) {
                    Toast.makeText(this, "Leitura ativada, mas não foi possível retornar ao GTO.", Toast.LENGTH_LONG).show();
                }
                return;
            }

            if (!failed && attempt < 50) {
                reopenGtoWhenProjectionReady(attempt + 1);
                return;
            }

            Toast.makeText(
                this,
                "A leitura da tela não ficou ativa. Tente autorizar novamente antes de voltar ao GTO.",
                Toast.LENGTH_LONG
            ).show();
        }, attempt == 0 ? 180L : 160L);
    }

    private void dispatchNativeNavigation(Intent intent) {
        if (intent == null || bridge == null || bridge.getWebView() == null) return;
        String route = intent.getStringExtra(EXTRA_NATIVE_ROUTE);
        if (route == null || route.trim().isEmpty()) return;

        String profileTab = intent.getStringExtra(EXTRA_NATIVE_PROFILE_TAB);
        String safeRoute = JSONObject.quote(route);
        String safeTab = JSONObject.quote(profileTab == null ? "" : profileTab);

        String script = "window.__NVU_NATIVE_ROUTE__={path:"
            + safeRoute
            + ",profileTab:"
            + safeTab
            + "};window.dispatchEvent(new CustomEvent('nvu:native-navigation',{detail:window.__NVU_NATIVE_ROUTE__}));";

        bridge.getWebView().post(() -> bridge.getWebView().evaluateJavascript(script, null));
        intent.removeExtra(EXTRA_NATIVE_ROUTE);
        intent.removeExtra(EXTRA_NATIVE_PROFILE_TAB);
    }
}
