package com.nvu.operacional;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    public static final String EXTRA_NATIVE_ROUTE = "nvuNativeRoute";
    public static final String EXTRA_NATIVE_PROFILE_TAB = "nvuNativeProfileTab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(GtoObserverPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        GtoObserverService.reportMainActivityForeground(true);
    }

    @Override
    public void onPause() {
        GtoObserverService.reportMainActivityForeground(false);
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        android.content.SharedPreferences gtoPrefs = getSharedPreferences(GtoObserverService.PREFS_NAME, MODE_PRIVATE);
        // HF54: recover HF51/HF52 legacy queue state before the observer/menu can render
        // a sticky previous-sync status. The recovery never deletes a sealed trip.
        GtoAutoTripSync.recoverLegacyPendingStateOnAuthenticatedStart(this, gtoPrefs);
        GtoObserverService.recoverIfEnabled(this);
        // Durable completed deliveries must be retried even when the driver has
        // temporarily disabled the floating observer. Authentication is checked
        // before touching the queue so the login screen does not inherit another
        // user's pending status on shared devices.
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            GtoAutoTripSync.flushPending(this, gtoPrefs, null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatchNativeNavigation(intent);
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
