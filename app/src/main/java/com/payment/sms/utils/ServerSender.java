package com.payment.sms.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Buyer-এর server-এ (receive.php) SMS data পাঠায়।
 * Internet না থাকলে Queue-এ রাখে।
 */
public class ServerSender {

    private static final String TAG  = "ServerSender";
    private final Context       ctx;
    private final ExecutorService exec    = Executors.newSingleThreadExecutor();
    private final Handler         handler = new Handler(Looper.getMainLooper());

    public interface Callback { void onLog(String msg); }

    public ServerSender(Context ctx) { this.ctx = ctx; }

    public void send(ParsedPayment p, Callback cb) {
        exec.execute(() -> {
            if (!isOnline()) {
                SmsQueue.add(ctx, p);
                log(cb, "📴 Internet নেই — Queue-এ রাখা হলো (" + SmsQueue.size(ctx) + " pending)");
                return;
            }
            // License re-validate (background)
            LicenseValidator.reValidate(ctx);
            // Queue flush আগে
            flushQueue(cb);
            // তারপর current SMS পাঠাও
            doSend(p, cb);
        });
    }

    public void flushQueue(Callback cb) {
        if (SmsQueue.isEmpty(ctx)) return;
        List<ParsedPayment> pending = SmsQueue.getAll(ctx);
        log(cb, "🔄 Queue flush — " + pending.size() + "টি pending");
        int ok = 0;
        int fail = 0;
        for (ParsedPayment p : pending) {
            if (doSend(p, cb)) ok++; else fail++;
        }
        if (fail == 0) {
            SmsQueue.clear(ctx);
            log(cb, "✅ Queue সম্পূর্ণ পাঠানো হয়েছে (" + ok + "টি)");
        } else {
            log(cb, "⚠️ " + ok + "টি সফল, " + fail + "টি failed");
        }
    }

    private boolean doSend(ParsedPayment p, Callback cb) {
        String targetUrl = AppConfig.getTargetUrl(ctx);
        String secretKey = AppConfig.getSecret(ctx);

        if (targetUrl.isEmpty()) {
            log(cb, "❌ Target URL নেই — License re-activate করুন");
            return false;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("secret",         secretKey);
            json.put("method",         p.method);
            json.put("transaction_id", p.transactionId);
            json.put("amount",         p.amount);
            json.put("sender",         p.sender);
            json.put("timestamp",      p.timestamp);
            json.put("raw_sms",        p.rawSms);

            byte[]            body = json.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) { os.write(body); }

            int    code = conn.getResponseCode();
            String resp = new String(
                (code == 200 ? conn.getInputStream() : conn.getErrorStream()).readAllBytes(),
                StandardCharsets.UTF_8);
            conn.disconnect();

            if (code == 200) {
                log(cb, "✅ [" + p.method.toUpperCase() + "] " + p.transactionId + " → Server OK");
                return true;
            } else {
                log(cb, "⚠️ Server [" + code + "]: " + resp);
                return false;
            }
        } catch (Exception e) {
            log(cb, "❌ Error: " + e.getMessage());
            Log.e(TAG, "Send error", e);
            return false;
        }
    }

    public boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) { return false; }
    }

    private void log(Callback cb, String msg) {
        Log.d(TAG, msg);
        if (cb != null) handler.post(() -> cb.onLog(msg));
    }
}
