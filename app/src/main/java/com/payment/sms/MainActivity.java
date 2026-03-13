package com.payment.sms;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.payment.sms.services.SmsMonitorService;
import com.payment.sms.utils.AppConfig;
import com.payment.sms.utils.LicenseValidator;
import com.payment.sms.utils.ServerSender;
import com.payment.sms.utils.SmsQueue;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final int REQ = 101;
    private LinearLayout layoutConfig, layoutActive;
    private EditText etServerUrl, etSecret;
    private SwitchCompat swService;
    private TextView tvStatus, tvClient, tvTarget, tvQueue, tvLog, tvSmsCount, tvFailCount;
    private ScrollView scrollLog;
    private int totalCount = 0, failCount = 0;

    private final BroadcastReceiver smsLogReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            String icon = i.getStringExtra("icon");
            String tag  = i.getStringExtra("tag");
            String msg  = i.getStringExtra("msg");
            String type = i.getStringExtra("type");
            totalCount++;
            if ("fail".equals(type)) failCount++;
            runOnUiThread(() -> {
                tvSmsCount.setText(String.valueOf(totalCount));
                tvFailCount.setText(String.valueOf(failCount));
                addLog(icon != null ? icon : "•", tag != null ? tag : "SMS", msg != null ? msg : "");
                updateQueue();
            });
        }
    };

    private final BroadcastReceiver paymentReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            String method = i.getStringExtra("method");
            String txId   = i.getStringExtra("transaction_id");
            String amount = i.getStringExtra("amount");
            totalCount++;
            runOnUiThread(() -> {
                tvSmsCount.setText(String.valueOf(totalCount));
                addLog("💰", method != null ? method.toUpperCase() : "PAY", "TrxID:" + txId + " ৳" + amount);
                updateQueue();
            });
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        buildUI();
        loadState();
        requestSmsPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f1 = new IntentFilter("com.payment.sms.SMS_LOG");
        IntentFilter f2 = new IntentFilter("com.payment.sms.NEW_PAYMENT");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(smsLogReceiver, f1, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(paymentReceiver, f2, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsLogReceiver, f1);
            registerReceiver(paymentReceiver, f2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(smsLogReceiver); } catch (Exception e) {}
        try { unregisterReceiver(paymentReceiver); } catch (Exception e) {}
    }

    private void buildUI() {
        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFF08080F);
        root.setFillViewport(true);

        LinearLayout main = vl();
        main.setPadding(dp(12), dp(12), dp(12), dp(20));

        // ── HEADER ──────────────────────────────────────
        LinearLayout header = hl();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF0F0F1C);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout titleSide = vl();
        titleSide.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        titleSide.addView(tv("💳 Payment SMS Gateway", 14, 0xFFEEEEFF, true));
        tvStatus = tv("● Inactive", 10, 0xFFFF5252, false);
        tvStatus.setPadding(0, dp(2), 0, 0);
        titleSide.addView(tvStatus);

        LinearLayout counters = hl();
        counters.setGravity(Gravity.CENTER_VERTICAL);
        counters.setPadding(dp(6), 0, 0, 0);
        LinearLayout c1 = counter("SMS", 0xFF111128, 0xFF8866FF);
        tvSmsCount = (TextView) c1.getChildAt(0);
        LinearLayout c2 = counter("ERR", 0xFF110A0A, 0xFFFF5252);
        tvFailCount = (TextView) c2.getChildAt(0);
        counters.addView(c1);
        counters.addView(gap(5));
        counters.addView(c2);

        header.addView(titleSide);
        header.addView(counters);
        main.addView(header);
        main.addView(gap(8));

        // ── ACTIVE CARD ──────────────────────────────────
        layoutActive = card(0xFF0F0F1C);
        layoutActive.setVisibility(View.GONE);
        LinearLayout activeRow = hl();
        activeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout activeInfo = vl();
        activeInfo.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        tvClient = tv("", 13, 0xFF8866FF, true);
        tvTarget = tv("", 10, 0xFF444466, false);
        tvTarget.setPadding(0, dp(1), 0, 0);
        activeInfo.addView(tvClient);
        activeInfo.addView(tvTarget);
        Button btnReset = miniBtn("Reset", 0xFF1A1A28, 0xFF555577);
        btnReset.setOnClickListener(v -> resetLicense());
        activeRow.addView(activeInfo);
        activeRow.addView(btnReset);
        layoutActive.addView(activeRow);
        main.addView(layoutActive);
        main.addView(gap(8));

        // ── CONFIG CARD ──────────────────────────────────
        layoutConfig = card(0xFF0F0F1C);
        layoutConfig.addView(tv("⚙  License Setup", 11, 0xFF8866FF, true));
        layoutConfig.addView(gap(8));
        layoutConfig.addView(tv("Server URL", 10, 0xFF555577, false));
        layoutConfig.addView(gap(3));
        etServerUrl = inputFld("https://your-server.com/api/validate.php");
        layoutConfig.addView(etServerUrl);
        layoutConfig.addView(gap(6));
        layoutConfig.addView(tv("Secret Key", 10, 0xFF555577, false));
        layoutConfig.addView(gap(3));
        etSecret = inputFld("Enter secret key...");
        layoutConfig.addView(etSecret);
        layoutConfig.addView(gap(8));
        Button btnActivate = mainBtn("ACTIVATE LICENSE", 0xFF6644CC, 0xFFFFFFFF);
        btnActivate.setOnClickListener(v -> activate());
        layoutConfig.addView(btnActivate);
        main.addView(layoutConfig);
        main.addView(gap(8));

        // ── MONITOR + QUEUE ──────────────────────────────
        LinearLayout midRow = hl();

        LinearLayout monCard = card(0xFF0F0F1C);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
        mlp.setMarginEnd(dp(4));
        monCard.setLayoutParams(mlp);
        monCard.addView(tv("▶  Monitor", 11, 0xFF8866FF, true));
        monCard.addView(gap(8));
        LinearLayout swRow = hl();
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView swLbl = tv("SMS", 12, 0xFF888899, false);
        swLbl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        swService = new SwitchCompat(this);
        swRow.addView(swLbl);
        swRow.addView(swService);
        monCard.addView(swRow);
        midRow.addView(monCard);

        LinearLayout qCard = card(0xFF0F0F1C);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, -2, 1f);
        qlp.setMarginStart(dp(4));
        qCard.setLayoutParams(qlp);
        qCard.addView(tv("📤  Queue", 11, 0xFF8866FF, true));
        qCard.addView(gap(6));
        tvQueue = tv("✅ Empty", 11, 0xFF00C853, false);
        qCard.addView(tvQueue);
        qCard.addView(gap(6));
        Button btnFlush = miniBtn("Flush", 0xFF1A1A28, 0xFF8866FF);
        btnFlush.setOnClickListener(v -> flush());
        qCard.addView(btnFlush);
        midRow.addView(qCard);

        main.addView(midRow);
        main.addView(gap(8));

        // ── LOG CARD ─────────────────────────────────────
        LinearLayout logCard = card(0xFF0F0F1C);
        LinearLayout logHead = hl();
        logHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView logTitle = tv("📜  Activity Log", 11, 0xFF8866FF, true);
        logTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnClear = miniBtn("Clear", 0xFF1A1A28, 0xFF444466);
        btnClear.setOnClickListener(v -> clearLog());
        logHead.addView(logTitle);
        logHead.addView(btnClear);
        logCard.addView(logHead);
        logCard.addView(gap(6));

        scrollLog = new ScrollView(this);
        scrollLog.setBackgroundColor(0xFF050509);
        scrollLog.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(280)));
        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF00E676);
        tvLog.setTextSize(10);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvLog.setLineSpacing(dp(1), 1f);
        tvLog.setText("কোনো লগ নেই।");
        scrollLog.addView(tvLog);
        logCard.addView(scrollLog);
        main.addView(logCard);

        root.addView(main);
        setContentView(root);
    }

    private void loadState() {
        etServerUrl.setText(AppConfig.getServerUrl(this));
        etSecret.setText(AppConfig.getSecret(this));
        boolean on = AppConfig.isActive(this);
        swService.setChecked(on);
        swService.setOnCheckedChangeListener((v, c) -> toggleService(c));
        updateStatus();
        updateQueue();
        updateClientInfo();
    }

    private void activate() {
        String url    = etServerUrl.getText().toString().trim();
        String secret = etSecret.getText().toString().trim().toUpperCase();
        if (url.isEmpty() || secret.isEmpty()) {
            Toast.makeText(this, "URL এবং Secret Key দিন!", Toast.LENGTH_SHORT).show();
            return;
        }
        addLog("🔄", "LICENSE", "Validating...");
        LicenseValidator.validate(this, url, secret, (ok, msg) -> runOnUiThread(() -> {
            addLog(ok ? "✅" : "❌", "LICENSE", msg);
            if (ok) {
                updateStatus(); updateClientInfo(); updateQueue();
                swService.setChecked(true); toggleService(true);
            } else {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void resetLicense() {
        AppConfig.prefs(this).edit().clear().apply();
        layoutActive.setVisibility(View.GONE);
        layoutConfig.setVisibility(View.VISIBLE);
        etServerUrl.setText(""); etSecret.setText("");
        updateStatus();
    }

    private void toggleService(boolean on) {
        AppConfig.prefs(this).edit().putBoolean(AppConfig.K_ACTIVE, on).apply();
        Intent svc = new Intent(this, SmsMonitorService.class);
        if (on) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
            else startService(svc);
            addLog("▶", "SERVICE", "Monitoring চালু");
        } else {
            stopService(svc);
            addLog("⏹", "SERVICE", "Monitoring বন্ধ");
        }
        updateStatus();
    }

    private void flush() {
        if (SmsQueue.isEmpty(this)) { addLog("📭", "QUEUE", "Queue খালি"); return; }
        addLog("📤", "QUEUE", SmsQueue.size(this) + "টি flush...");
        new ServerSender(this).flushQueue(msg -> runOnUiThread(() -> { addLog("→", "Q", msg); updateQueue(); }));
    }

    private void updateStatus() {
        boolean setup = AppConfig.isSetupDone(this);
        boolean on    = AppConfig.isActive(this);
        if (!setup)  { tvStatus.setText("⚠  License activate করুন"); tvStatus.setTextColor(0xFFFFAA00); }
        else if (on) { tvStatus.setText("●  Monitoring active");       tvStatus.setTextColor(0xFF00C853); }
        else         { tvStatus.setText("●  Service inactive");         tvStatus.setTextColor(0xFFFF5252); }
    }

    private void updateClientInfo() {
        String name = AppConfig.getClientName(this);
        String url  = AppConfig.getTargetUrl(this);
        if (!name.isEmpty()) {
            tvClient.setText("Client: " + name);
            tvTarget.setText(url);
            layoutConfig.setVisibility(View.GONE);
            layoutActive.setVisibility(View.VISIBLE);
        } else {
            layoutConfig.setVisibility(View.VISIBLE);
            layoutActive.setVisibility(View.GONE);
        }
    }

    private void updateQueue() {
        int n = SmsQueue.size(this);
        if (n > 0) { tvQueue.setText("⏳ " + n + " pending"); tvQueue.setTextColor(0xFFFFAA00); }
        else       { tvQueue.setText("✅ Empty");              tvQueue.setTextColor(0xFF00C853); }
    }

    private void clearLog() {
        tvLog.setText("কোনো লগ নেই।");
        totalCount = 0; failCount = 0;
        tvSmsCount.setText("0"); tvFailCount.setText("0");
    }

    public void addLog(String icon, String tag, String msg) {
        runOnUiThread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = "[" + time + "] " + icon + " " + tag + " › " + msg + "\n";
            String cur  = tvLog.getText().toString();
            if (cur.contains("কোনো লগ নেই।")) cur = "";
            String[] lines = cur.split("\n");
            if (lines.length > 150) {
                StringBuilder sb = new StringBuilder();
                for (int i = 30; i < lines.length; i++) sb.append(lines[i]).append("\n");
                cur = sb.toString();
            }
            tvLog.setText(line + cur);
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_UP));
        });
    }

    private void requestSmsPermission() {
        List<String> need = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS})
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQ) for (int v : r)
            if (v != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "⚠ SMS Permission দিন!", Toast.LENGTH_LONG).show(); break;
            }
    }

    // ══════════ HELPERS ══════════
    private LinearLayout vl() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return l;
    }
    private LinearLayout hl() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return l;
    }
    private LinearLayout card(int bg) {
        LinearLayout l = vl();
        l.setBackgroundColor(bg);
        l.setPadding(dp(12), dp(10), dp(12), dp(10));
        return l;
    }
    private LinearLayout counter(String label, int bg, int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(bg);
        l.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView num = tv("0", 16, color, true);
        num.setGravity(Gravity.CENTER);
        TextView lbl = tv(label, 8, 0xFF444466, false);
        lbl.setGravity(Gravity.CENTER);
        l.addView(num);
        l.addView(lbl);
        return l;
    }
    private TextView tv(String t, int sp, int c, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(sp); v.setTextColor(c);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }
    private EditText inputFld(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(0xFF2A2A3A);
        e.setTextColor(0xFFDDDDFF); e.setBackgroundColor(0xFF0A0A16);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setSingleLine(true);
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextSize(11);
        e.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return e;
    }
    private Button mainBtn(String t, int bg, int fg) {
        Button b = new Button(this);
        b.setText(t); b.setBackgroundColor(bg); b.setTextColor(fg);
        b.setTextSize(13); b.setTypeface(null, Typeface.BOLD); b.setAllCaps(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44)));
        return b;
    }
    private Button miniBtn(String t, int bg, int fg) {
        Button b = new Button(this);
        b.setText(t); b.setBackgroundColor(bg); b.setTextColor(fg);
        b.setTextSize(10); b.setTypeface(null, Typeface.BOLD);
        b.setPadding(dp(10), dp(2), dp(10), dp(2));
        b.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(30)));
        return b;
    }
    private View gap(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(d)));
        return v;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
