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
        // Root: vertical LinearLayout (NOT ScrollView as root)
        // This avoids weight issues inside ScrollView
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080F);
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        // ── HEADER ──────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF0D0D1A);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        // Title col
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView appName = new TextView(this);
        appName.setText("💳 Payment SMS Gateway");
        appName.setTextSize(14); appName.setTextColor(0xFFEEEEFF);
        appName.setTypeface(null, Typeface.BOLD);

        tvStatus = new TextView(this);
        tvStatus.setText("● Inactive");
        tvStatus.setTextSize(10); tvStatus.setTextColor(0xFFFF5252);
        tvStatus.setPadding(0, dp(2), 0, 0);

        titleCol.addView(appName);
        titleCol.addView(tvStatus);

        // Counters
        LinearLayout counters = new LinearLayout(this);
        counters.setOrientation(LinearLayout.HORIZONTAL);
        counters.setGravity(Gravity.CENTER_VERTICAL);
        counters.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));

        LinearLayout box1 = makeBadge(0xFF111128, 0xFF8866FF, "SMS");
        tvSmsCount = (TextView) box1.getChildAt(0);
        LinearLayout box2 = makeBadge(0xFF110A0A, 0xFFFF5252, "ERR");
        tvFailCount = (TextView) box2.getChildAt(0);

        counters.addView(box1);
        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(dp(5), 1));
        counters.addView(sp);
        counters.addView(box2);

        header.addView(titleCol);
        header.addView(counters);
        root.addView(header);

        // ── SCROLLABLE CONTENT ───────────────────────────
        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        sv.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        content.setPadding(dp(10), dp(10), dp(10), dp(20));

        // ── ACTIVE CARD ──────────────────────────────────
        layoutActive = makeCard(0xFF0D0D1A);
        layoutActive.setVisibility(View.GONE);

        LinearLayout activeRow = new LinearLayout(this);
        activeRow.setOrientation(LinearLayout.HORIZONTAL);
        activeRow.setGravity(Gravity.CENTER_VERTICAL);
        activeRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        LinearLayout activeInfo = new LinearLayout(this);
        activeInfo.setOrientation(LinearLayout.VERTICAL);
        activeInfo.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        tvClient = new TextView(this);
        tvClient.setTextSize(13); tvClient.setTextColor(0xFF8866FF);
        tvClient.setTypeface(null, Typeface.BOLD);

        tvTarget = new TextView(this);
        tvTarget.setTextSize(10); tvTarget.setTextColor(0xFF444466);
        tvTarget.setPadding(0, dp(1), 0, 0);

        activeInfo.addView(tvClient);
        activeInfo.addView(tvTarget);

        Button btnReset = makeMiniBtn("Reset", 0xFF1A1A28, 0xFF555577);
        btnReset.setOnClickListener(v -> resetLicense());

        activeRow.addView(activeInfo);
        activeRow.addView(btnReset);
        layoutActive.addView(activeRow);
        content.addView(layoutActive);
        content.addView(makeGap(8));

        // ── CONFIG CARD ──────────────────────────────────
        layoutConfig = makeCard(0xFF0D0D1A);

        TextView cfgTitle = new TextView(this);
        cfgTitle.setText("⚙  License Setup");
        cfgTitle.setTextSize(11); cfgTitle.setTextColor(0xFF8866FF);
        cfgTitle.setTypeface(null, Typeface.BOLD);
        layoutConfig.addView(cfgTitle);
        layoutConfig.addView(makeGap(8));

        layoutConfig.addView(makeLabel("Server URL"));
        layoutConfig.addView(makeGap(3));
        etServerUrl = makeInput("https://your-server.com/api/validate.php");
        layoutConfig.addView(etServerUrl);
        layoutConfig.addView(makeGap(6));

        layoutConfig.addView(makeLabel("Secret Key"));
        layoutConfig.addView(makeGap(3));
        etSecret = makeInput("Enter secret key...");
        layoutConfig.addView(etSecret);
        layoutConfig.addView(makeGap(8));

        Button btnActivate = makeMainBtn("ACTIVATE LICENSE", 0xFF6644CC, 0xFFFFFFFF);
        btnActivate.setOnClickListener(v -> activate());
        layoutConfig.addView(btnActivate);

        content.addView(layoutConfig);
        content.addView(makeGap(8));

        // ── MONITOR + QUEUE ROW ──────────────────────────
        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        LinearLayout monCard = makeCard(0xFF0D0D1A);
        LinearLayout.LayoutParams monLp = new LinearLayout.LayoutParams(0, -2, 1f);
        monLp.rightMargin = dp(4);
        monCard.setLayoutParams(monLp);

        TextView monTitle = new TextView(this);
        monTitle.setText("▶  Monitor");
        monTitle.setTextSize(11); monTitle.setTextColor(0xFF8866FF);
        monTitle.setTypeface(null, Typeface.BOLD);
        monCard.addView(monTitle);
        monCard.addView(makeGap(8));

        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        TextView swLbl = new TextView(this);
        swLbl.setText("SMS");
        swLbl.setTextSize(12); swLbl.setTextColor(0xFF888899);
        swLbl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        swService = new SwitchCompat(this);
        swRow.addView(swLbl);
        swRow.addView(swService);
        monCard.addView(swRow);
        midRow.addView(monCard);

        LinearLayout qCard = makeCard(0xFF0D0D1A);
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(0, -2, 1f);
        qLp.leftMargin = dp(4);
        qCard.setLayoutParams(qLp);

        TextView qTitle = new TextView(this);
        qTitle.setText("📤  Queue");
        qTitle.setTextSize(11); qTitle.setTextColor(0xFF8866FF);
        qTitle.setTypeface(null, Typeface.BOLD);
        qCard.addView(qTitle);
        qCard.addView(makeGap(6));

        tvQueue = new TextView(this);
        tvQueue.setText("✅ Empty");
        tvQueue.setTextSize(11); tvQueue.setTextColor(0xFF00C853);
        qCard.addView(tvQueue);
        qCard.addView(makeGap(6));

        Button btnFlush = makeMiniBtn("Flush", 0xFF1A1A28, 0xFF8866FF);
        btnFlush.setOnClickListener(v -> flush());
        qCard.addView(btnFlush);
        midRow.addView(qCard);

        content.addView(midRow);
        content.addView(makeGap(8));

        // ── LOG CARD ─────────────────────────────────────
        LinearLayout logCard = makeCard(0xFF0D0D1A);

        LinearLayout logHead = new LinearLayout(this);
        logHead.setOrientation(LinearLayout.HORIZONTAL);
        logHead.setGravity(Gravity.CENTER_VERTICAL);
        logHead.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        TextView logTitle = new TextView(this);
        logTitle.setText("📜  Activity Log");
        logTitle.setTextSize(11); logTitle.setTextColor(0xFF8866FF);
        logTitle.setTypeface(null, Typeface.BOLD);
        logTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        Button btnClear = makeMiniBtn("Clear", 0xFF1A1A28, 0xFF444466);
        btnClear.setOnClickListener(v -> clearLog());
        logHead.addView(logTitle);
        logHead.addView(btnClear);
        logCard.addView(logHead);
        logCard.addView(makeGap(6));

        scrollLog = new ScrollView(this);
        scrollLog.setBackgroundColor(0xFF050509);
        scrollLog.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(260)));

        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF00E676);
        tvLog.setTextSize(10);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvLog.setLineSpacing(dp(1), 1f);
        tvLog.setText("কোনো লগ নেই।");
        scrollLog.addView(tvLog);
        logCard.addView(scrollLog);

        content.addView(logCard);
        sv.addView(content);
        root.addView(sv);

        setContentView(root);
    }

    // ══════════════════════════════════════════════
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
    private LinearLayout makeCard(int bg) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(bg);
        l.setPadding(dp(12), dp(10), dp(12), dp(10));
        l.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return l;
    }
    private LinearLayout makeBadge(int bg, int color, String label) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(bg);
        l.setPadding(dp(8), dp(4), dp(8), dp(4));
        l.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        TextView num = new TextView(this);
        num.setText("0"); num.setTextSize(16); num.setTextColor(color);
        num.setTypeface(null, Typeface.BOLD); num.setGravity(Gravity.CENTER);
        TextView lbl = new TextView(this);
        lbl.setText(label); lbl.setTextSize(8); lbl.setTextColor(0xFF444466);
        lbl.setGravity(Gravity.CENTER);
        l.addView(num); l.addView(lbl);
        return l;
    }
    private TextView makeLabel(String t) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(10); v.setTextColor(0xFF555577);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return v;
    }
    private EditText makeInput(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(0xFF2A2A3A);
        e.setTextColor(0xFFDDDDFF); e.setBackgroundColor(0xFF0A0A16);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setSingleLine(true); e.setTypeface(Typeface.MONOSPACE); e.setTextSize(11);
        e.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return e;
    }
    private Button makeMainBtn(String t, int bg, int fg) {
        Button b = new Button(this);
        b.setText(t); b.setBackgroundColor(bg); b.setTextColor(fg);
        b.setTextSize(13); b.setTypeface(null, Typeface.BOLD); b.setAllCaps(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44)));
        return b;
    }
    private Button makeMiniBtn(String t, int bg, int fg) {
        Button b = new Button(this);
        b.setText(t); b.setBackgroundColor(bg); b.setTextColor(fg);
        b.setTextSize(10); b.setTypeface(null, Typeface.BOLD);
        b.setPadding(dp(10), dp(2), dp(10), dp(2));
        b.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(30)));
        return b;
    }
    private View makeGap(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(d)));
        return v;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
