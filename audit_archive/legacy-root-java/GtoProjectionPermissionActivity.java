package com.nvu.operacional;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class GtoProjectionPermissionActivity extends Activity {
    private static final int REQUEST_CAPTURE = 9007;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        try { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE); } catch (Exception ignored) {}

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("projectionStatus", "MANAGER_UNAVAILABLE")
                .putString("projectionError", "MediaProjectionManager indisponível")
                .putBoolean("projectionPermissionInFlight", false)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putString("lastEvent", "Android não disponibilizou a autorização de leitura da tela")
                .apply();
            GtoObserverService.reportProjectionPermissionTerminalFailure(
                this, "MANAGER_UNAVAILABLE", "MediaProjectionManager indisponível"
            );
            finishAndRemoveTask();
            return;
        }
        try {
            Intent captureIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // GTO automation needs the whole display. This also avoids the Android 14+
                // single-app chooser selecting the NVU permission activity itself.
                captureIntent = manager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay()
                );
            } else {
                captureIntent = manager.createScreenCaptureIntent();
            }
            getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("projectionStatus", "CONSENT_VISIBLE")
                .remove("projectionError")
                .putString("lastEvent", "Android exibiu a autorização de leitura")
                .apply();
            startActivityForResult(captureIntent, REQUEST_CAPTURE);
        } catch (Exception ex) {
            String detail = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
            getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("projectionStatus", "CONSENT_LAUNCH_FAILED")
                .putString("projectionError", detail)
                .putBoolean("projectionPermissionInFlight", false)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putString("lastEvent", "Falha ao abrir a autorização de leitura da tela")
                .apply();
            GtoObserverService.reportProjectionPermissionTerminalFailure(
                this, "CONSENT_LAUNCH_FAILED", detail
            );
            finishAndRemoveTask();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;

        Intent serviceIntent = new Intent(this, GtoObserverService.class);
        if (resultCode == RESULT_OK && data != null) {
            serviceIntent.setAction(GtoObserverService.ACTION_START_PROJECTION);
            serviceIntent.putExtra(GtoObserverService.EXTRA_RESULT_CODE, resultCode);
            serviceIntent.putExtra(GtoObserverService.EXTRA_RESULT_DATA, data);
        } else {
            serviceIntent.setAction(GtoObserverService.ACTION_PROJECTION_DENIED);
        }

        getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString("projectionStatus", resultCode == RESULT_OK && data != null ? "CONSENT_GRANTED" : "DENIED")
            .putString("lastEvent", resultCode == RESULT_OK && data != null
                ? "Autorização de leitura concedida; iniciando captura"
                : "Autorização de leitura recusada")
            .apply();

        try {
            if (GtoObserverService.isRunning()) {
                startService(serviceIntent);
            } else {
                ContextCompat.startForegroundService(this, serviceIntent);
            }
        } catch (Exception ex) {
            String detail = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
            getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("projectionStatus", "SERVICE_DISPATCH_FAILED")
                .putString("projectionError", detail)
                .putBoolean("projectionPermissionInFlight", false)
                .putBoolean("projectionReauthRequired", true)
                .putBoolean("projectionReauthNoticeShown", false)
                .putString("lastEvent", "Falha ao entregar autorização ao observador")
                .apply();
            GtoObserverService.reportProjectionPermissionTerminalFailure(
                this, "SERVICE_DISPATCH_FAILED", detail
            );
        }

        // This activity lives in an isolated, transparent task excluded from Recents. Finishing it
        // returns to the task that was already visible (the GTO) without launching the
        // NVU MainActivity and without relaunching the simulator.
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

}
