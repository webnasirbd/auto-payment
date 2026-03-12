package com.payment.sms.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LicenseValidator {

    private static final String TAG = "LicenseValidator";
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onResult(boolean success, String message);
    }

    public static void validate(Context ctx, String serverUrl, String secretKey, Callback cb) {
        exec.execute(() -> {
            try {
                URL url = new URL(serverUrl.trim());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-License-Key", secretKey.toUpperCase().trim());
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setDoOutput(true);

                byte[] body = ("{\"secret\":\"" + secretKey.toUpperCase().trim() + "\"}").getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(body);

                int code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                conn.disconnect();

                JSONObject json = new JSONObject(resp);
                String status = json.optString("status", "");

                if (!"ok".equals(status)) {
                    if (cb != null) cb.onResult(false, json.optString("message", "License invalid"));
                    return;
                }

                AppConfig.prefs(ctx).edit()
                    .putString(AppConfig.K_SECRET,     secretKey.toUpperCase().trim())
                    .putString(AppConfig.K_SERVER_URL, serverUrl.trim())
                    .putString(AppConfig.K_TARGET_URL, json.optString("target_url", ""))
                    .putString(AppConfig.K_MODE,       json.optString("sender_mode", "all"))
                    .putString(AppConfig.K_SENDERS,    json.optString("allowed_senders", "[]"))
                    .putBoolean(AppConfig.K_BKASH,     json.optBoolean("bkash_enabled",  true))
                    .putBoolean(AppConfig.K_NAGAD,     json.optBoolean("nagad_enabled",  true))
                    .putBoolean(AppConfig.K_ROCKET,    json.optBoolean("rocket_enabled", false))
                    .putBoolean(AppConfig.K_ACTIVE,    true)
                    .putBoolean(AppConfig.K_SETUP,     true)
                    .putString(AppConfig.K_CLIENT,     json.optString("client_name", ""))
                    .apply();

                AppConfig.markSynced(ctx);
                if (cb != null) cb.onResult(true, "✅ License valid — " + json.optString("client_name"));

            } catch (Exception e) {
                Log.e(TAG, "Validate error: " + e.getMessage());
                if (cb != null) cb.onResult(false, "❌ Connection error: " + e.getMessage());
            }
        });
    }

    public static void reValidate(Context ctx) {
        if (!AppConfig.needsSync(ctx)) return;
        String secret = AppConfig.getSecret(ctx);
        String serverUrl = AppConfig.getServerUrl(ctx);
        if (secret.isEmpty() || serverUrl.isEmpty()) return;
        validate(ctx, serverUrl, secret, (ok, msg) -> {
            if (!ok) {
                AppConfig.prefs(ctx).edit().putBoolean(AppConfig.K_ACTIVE, false).apply();
            }
            Log.d(TAG, "Re-validate: " + msg);
        });
    }
}
