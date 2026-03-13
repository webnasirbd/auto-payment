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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ = 101;

    private LinearLayout layoutConfig, layoutActive;
    private EditText etServerUrl, etSecret;
    private SwitchCompat swService;
    private TextView tvStatus, tvClient, tvTarget;
    private TextView tvQueue, tvLog, tvSmsCount, tvFailCount;
    private ScrollView scrollLog;
    private int totalCount = 0, failCount = 0;

    // SMS log broadcast
    private final BroadcastReceiver smsLogReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
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

    // Payment success broadcast
    private final BroadcastReceiver paymentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
            String method = i.getStringExtra("method");
            String txId   = i.getStringExtra("transaction_id");
            String amount = i.getStringExtra("amount");
            totalCount++;
            runOnUiThread(() -> {
                tvSmsCount.setText(String.valueOf(totalCount));
                addLog("💰", method != null ? method.toUpperCase() : "PAY",
                    "TrxID: " + txId + "  ৳" + amount);
                updateQueue();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        try { unregisterReceiver(smsLogReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(paymentReceiver); } catch (Exception ignored) {}
    }

    // ════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════
    private void buildUI() {
        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFF08080F);
        root.setFillViewport(true);

        LinearLayout main = col();
        main.setPadding(dp(16), dp(20), dp(16), dp(60));

        // ── Header ──────────────────────────────────────
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        LinearLayout headerLeft = col();
        headerLeft.setLayoutParams(weight(1f));
        TextView appName = txt("Payment SMS Gateway", 17, 0xFFFFFFFF, true);
        tvStatus = txt("", 11, 0xFF666688, false);
        tvStatus.setPadding(0, dp(3), 0, 0);
        headerLeft.addView(appName);
        headerLeft.addView(tvStatus);

        // Stats badges
        LinearLayout badges = row();
        badges.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout b1 = badge();
        tvSmsCount = (TextView) b1.getChildAt(0);
        LinearLayout b2 = badge();
        b2.setBackgroundColor(0xFF110A0A);
        tvFailCount = (TextView) b2.getChildAt(0);
        tvFailCount.setTextColor(0xFFFF5252);
        TextView b2label = (TextView) b2.getChildAt(1);
        b2label.setText("ERR");
        badges.addView(b1);
        badges.addView(gap(8));
        badges.addView(b2);

        header.addView(headerLeft);
        header.addView(badges);
        main.addView(header);

        // ── Active Info Card (visible after license) ───
        layoutActive = card(0xFF0F0F1E);
        layoutActive.setVisibility(View.GONE);

        LinearLayout activeRow = row();
        activeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout activeInfo = col();
        activeInfo.setLayoutParams(weight(1f));
        tvClient = txt("", 13, 0xFF7C4DFF, true);
        tvTarget = txt("", 10, 0xFF333355, false);
        tvTarget.setPadding(0, dp(2), 0, 0);
        activeInfo.addView(tvClient);
        activeInfo.addView(tvTarget);

        Button btnReset = miniBtn("Reset", 0xFF1A1A2E, 0xFF555577);
        btnReset.setOnClickListener(v -> {
            AppConfig.prefs(this).edit().clear().apply();
            layoutActive.setVisibility(View.GONE);
            layoutConfig.setVisibility(View.VISIBLE);
            etServerUrl.setText("");
            etSecret.setText("");
            updateStatus();
        });
        activeRow.addView(activeInfo);
        activeRow.addView(btnReset);
        layoutActive.addView(activeRow);
        main.addView(layoutActive);
        main.addView(gap(10));

        // ── Config Card ──────────────────────────────────
        layoutConfig = card(0xFF0F0F1E);
        layoutConfig.addView(sectionTitle("⚙  Setup License"));
        layoutConfig.addView(fieldLabel("License Server URL"));
        etServerUrl = inputField("https://your-server.com/api/validate.php");
        layoutConfig.addView(etServerUrl);
        layoutConfig.addView(fieldLabel("Secret Key"));
        etSecret = inputField("Enter your secret key...");
        layoutConfig.addView(etSecret);
        Button btnActivate = mainBtn("ACTIVATE LICENSE", 0xFF7C4DFF, 0xFFFFFFFF);
        btnActivate.setOnClickListener(v -> activate());
        layoutConfig.addView(btnActivate);
        main.addView(layoutConfig);
        main.addView(gap(10));

        // ── Monitor + Queue row ──────────────────────────
        LinearLayout midRow = row();
        midRow.setWeightSum(2f);

        LinearLayout monCard = card(0xFF0F0F1E);
        LinearLayout.LayoutParams monLp = (LinearLayout.LayoutParams) weight(1f);
        monLp.setMarginEnd(dp(5));
        monCard.setLayoutParams(monLp);
        monCard.addView(sectionTitle("▶  Monitor"));
        LinearLayout swRow = row();
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView swLabel = txt("SMS Service", 12, 0xFF888899, false);
        swLabel.setLayoutParams(weight(1f));
        swService = new SwitchCompat(this);
        swRow.addView(swLabel);
        swRow.addView(swService);
        monCard.addView(swRow);
        midRow.addView(monCard);

        LinearLayout qCard = card(0xFF0F0F1E);
        LinearLayout.LayoutParams qLp = (LinearLayout.LayoutParams) weight(1f);
        qLp.setMarginStart(dp(5));
        qCard.setLayoutParams(qLp);
        qCard.addView(sectionTitle("📤  Queue"));
        tvQueue = txt("Empty", 11, 0xFF00C853, false);
        tvQueue.setPadding(0, dp(2), 0, dp(8));
        qCard.addView(tvQueue);
        Button btnFlush = miniBtn("Flush Now", 0xFF1A1A2E, 0xFF7C4DFF);
        btnFlush.setOnClickListener(v -> flush());
        qCard.addView(btnFlush);
        midRow.addView(qCard);

        main.addView(midRow);
        main.addView(gap(10));

        // ── Log Card ─────────────────────────────────────
        LinearLayout logCard = card(0xFF0F0F1E);

        LinearLayout logHead = row();
        logHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView logTitle = txt("Activity Log", 12, 0xFF7C4DFF, true);
        logTitle.setLayoutParams(weight(1f));
        Button btnClear = miniBtn("Clear", 0xFF1A1A2E, 0xFF444466);
        btnClear.setOnClickListener(v -> {
            tvLog.setText("");
            totalCount = 0; failCount = 0;
            tvSmsCount.setText("0");
            tvFailCount.setText("0");
        });
        logHead.addView(logTitle);
        logHead.addView(btnClear);
        logCard.addView(logHead);
        logCard.addView(gap(6));

        scrollLog = new ScrollView(this);
        scrollLog.setBackgroundColor(0xFF050509);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(mp(), dp(300));
        scrollLog.setLayoutParams(slp);
        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF00E676);
        tvLog.setTextSize(10);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setPadding(dp(10), dp(8), dp(10), dp(8));
        tvLog.setLineSpacing(dp(2), 1f);
        tvLog.setText("কোনো লগ নেই।");
        scrollLog.addView(tvLog);
        logCard.addView(scrollLog);
        main.addView(logCard);

        root.addView(main);
        setContentView(root);
    }

    // ════════════════════════════════════════════════════
    //  STATE & ACTIONS
    // ════════════════════════════════════════════════════
    private void loadState() {
        etServerUrl.setText(AppConfig.getServerUrl(this));
        etSecret.setText(AppConfig.getSecret(this));
        boolean on = AppConfig.isActive(this);
        swService.setChecked(on);
        swService.setOnCheckedChangeListener((v, checked) -> toggleService(checked));
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
                layoutConfig.setVisibility(View.GONE);
                layoutActive.setVisibility(View.VISIBLE);
                Toast.makeText(this, "✅ License Valid!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void toggleService(boolean on) {
        AppConfig.prefs(this).edit().putBoolean(AppConfig.K_ACTIVE, on).apply();
        Intent svc = new Intent(this, SmsMonitorService.class);
        if (on) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
            else startService(svc);
            addLog("▶", "SERVICE", "Monitoring চালু হয়েছে");
        } else {
            stopService(svc);
            addLog("⏹", "SERVICE", "Monitoring বন্ধ");
        }
        updateStatus();
    }

    private void flush() {
        if (SmsQueue.isEmpty(this)) { addLog("📭", "QUEUE", "Queue খালি"); return; }
        addLog("📤", "QUEUE", SmsQueue.size(this) + " টি pending flush...");
        new ServerSender(this).flushQueue(msg -> { addLog("→", "QUEUE", msg); updateQueue(); });
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

    public void addLog(String icon, String tag, String msg) {
        runOnUiThread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = "[" + time + "]  " + icon + "  " + tag + "  ›  " + msg + "\n";
            String cur  = tvLog.getText().toString();
            if ("কোনো লগ নেই।".equals(cur.trim())) cur = "";
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
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ) for (int r : results)
            if (r != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "⚠ SMS Permission দিন!", Toast.LENGTH_LONG).show(); break;
            }
    }

    // ════════════════════════════════════════════════════
    //  UI HELPERS
    // ════════════════════════════════════════════════════
    private LinearLayout col() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(mp(), wc()));
        return ll;
    }
    private LinearLayout row() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(mp(), wc()));
        return ll;
    }
    private LinearLayout card(int bg) {
        LinearLayout ll = col();
        ll.setBackgroundColor(bg);
        ll.setPadding(dp(14), dp(12), dp(14), dp(14));
        return ll;
    }
    private LinearLayout badge() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        ll.setBackgroundColor(0xFF111128);
        ll.setPadding(dp(10), dp(6), dp(10), dp(6));
        TextView num = txt("0", 18, 0xFF7C4DFF, true);
        num.setGravity(Gravity.CENTER);
        TextView lbl = txt("SMS", 8, 0xFF444466, false);
        lbl.setGravity(Gravity.CENTER);
        ll.addView(num);
        ll.addView(lbl);
        return ll;
    }
    private TextView sectionTitle(String t) {
        TextView tv = txt(t, 11, 0xFF7C4DFF, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(mp(), wc());
        p.setMargins(0, 0, 0, dp(10));
        tv.setLayoutParams(p);
        return tv;
    }
    private TextView fieldLabel(String t) {
        TextView tv = txt(t, 10, 0xFF555577, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(mp(), wc());
        p.setMargins(0, dp(4), 0, dp(3));
        tv.setLayoutParams(p);
        return tv;
    }
    private TextView txt(String t, int sp, int c, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextSize(sp); tv.setTextColor(c);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }
    private EditText inputField(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setHintTextColor(0xFF2A2A3A);
        et.setTextColor(0xFFDDDDFF); et.setBackgroundColor(0xFF0A0A16);
        et.setPadding(dp(11), dp(10), dp(11), dp(10));
        et.setSingleLine(true);
        et.setTypeface(Typeface.MONOSPACE);
        et.setTextSize(11);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(mp(), wc());
        p.setMargins(0, 0, 0, dp(6));
        et.setLayoutParams(p);
        return et;
    }
    private Button mainBtn(String t, int bg, int fg) {
        Button b = new Button(this);
        b.setText(t); b.setBackgroundColor(bg); b.setTextColor(fg);
        b.setTextSize(13); b.setTypeface(null, Typeface.BOLD);
        b.setAllCaps(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(mp(), dp(46));
        p.setMargins(0, dp(6), 0, 0);
        b.setLayoutParams(p);
        return b;
    }
    private Button miniBtn(String t, int bg, int fg) {
        Button b = new Button(this);
        b.setText(t); b.setBackgroundColor(bg); b.setTextColor(fg);
        b.setTextSize(11); b.setTypeface(null, Typeface.BOLD);
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        b.setLayoutParams(new LinearLayout.LayoutParams(wc(), dp(32)));
        return b;
    }
    private View gap(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(mp(), dp(d)));
        return v;
    }
    private LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, wc(), w);
    }
    private int mp() { return LinearLayout.LayoutParams.MATCH_PARENT; }
    private int wc() { return LinearLayout.LayoutParams.WRAP_CONTENT; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
