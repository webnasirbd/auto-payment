package com.payment.sms.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

public class AppConfig {

    private static final String PREF = "paysms_config";

    public static final String K_SECRET      = "secret_key";
    public static final String K_SERVER_URL  = "server_url";
    public static final String K_TARGET_URL  = "target_url";
    public static final String K_SENDERS     = "allowed_senders";
    public static final String K_MODE        = "sender_mode";
    public static final String K_BKASH       = "bkash_on";
    public static final String K_NAGAD       = "nagad_on";
    public static final String K_ROCKET      = "rocket_on";
    public static final String K_ACTIVE      = "service_active";
    public static final String K_SETUP       = "setup_done";
    public static final String K_LAST_SYNC   = "last_sync";
    public static final String K_CLIENT      = "client_name";

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String  getSecret(Context ctx)     { return prefs(ctx).getString(K_SECRET, "").trim(); }
    public static String  getServerUrl(Context ctx)  { return prefs(ctx).getString(K_SERVER_URL, "").trim(); }
    public static String  getTargetUrl(Context ctx)  { return prefs(ctx).getString(K_TARGET_URL, "").trim(); }
    public static boolean isActive(Context ctx)      { return prefs(ctx).getBoolean(K_ACTIVE, false); }
    public static boolean isSetupDone(Context ctx)   { return prefs(ctx).getBoolean(K_SETUP, false); }
    public static String  getClientName(Context ctx) { return prefs(ctx).getString(K_CLIENT, ""); }

    public static boolean needsSync(Context ctx) {
        return System.currentTimeMillis() - prefs(ctx).getLong(K_LAST_SYNC, 0) > 60_000;
    }
    public static void markSynced(Context ctx) {
        prefs(ctx).edit().putLong(K_LAST_SYNC, System.currentTimeMillis()).apply();
    }

    public static boolean isSenderAllowed(Context ctx, String sender) {
        String mode = prefs(ctx).getString(K_MODE, "all");
        if ("none".equals(mode)) return false;
        if ("all".equals(mode))  return true;
        String raw = prefs(ctx).getString(K_SENDERS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            String s = sender.trim();
            for (int i = 0; i < arr.length(); i++) {
                String allowed = arr.getString(i).trim();
                if (!allowed.isEmpty() && (s.equalsIgnoreCase(allowed) || s.contains(allowed) || allowed.contains(s))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isPaymentSms(Context ctx, String sender, String body) {
        String lo = body.toLowerCase();
        String snd = sender.toLowerCase();
        boolean hasBkash  = prefs(ctx).getBoolean(K_BKASH, true)  && (snd.contains("bkash")  || lo.contains("bkash")  || lo.contains("বিকাশ"));
        boolean hasNagad  = prefs(ctx).getBoolean(K_NAGAD, true)  && (snd.contains("nagad")  || lo.contains("nagad")  || lo.contains("নগদ"));
        boolean hasRocket = prefs(ctx).getBoolean(K_ROCKET, false) && (snd.contains("rocket") || lo.contains("rocket") || lo.contains("dbbl") || lo.contains("dutch"));
        return hasBkash || hasNagad || hasRocket;
    }
}
