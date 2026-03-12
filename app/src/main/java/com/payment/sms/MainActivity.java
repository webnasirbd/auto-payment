package com.payment.sms;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

    private EditText etServerUrl;
    private EditText etSecret;
    private androidx.appcompat.widget.SwitchCompat swService;
    private TextView tvStatus;
    private TextView tvClient;
    private TextView tvTarget;
    private TextView tvQueue;
    private TextView tvLog;
    private ScrollView scrollLog;

    private final BroadcastReceiver newPaymentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String method = intent.getStringExtra("method");
            String txId   = intent.getStringExtra("transaction_id");
            String amount = intent.getStringExtra("amount");
            addLog("📨 [" + (method != null ? method.toUpperCase() : "?") + "] "
                + txId + " — ৳" + amount);
            updateQueue();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { registerReceiver(newPaymentReceiver, new IntentFilter("com.payment.sms.NEW_PAYMENT"), Context.RECEIVER_NOT_EXPORTED); } else { registerReceiver(newPaymentReceiver, new IntentFilter("com.payment.sms.NEW_PAYMENT")); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(newPaymentReceiver); } catch (Exception ignored) {}
    }

    private void buildUI() {
        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFF0a0a14);
        root.setFillViewport(true);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(16), dp(24), dp(16), dp(40));

        TextView title = txt("💳 Payment SMS Gateway", 20, 0xFFFFFFFF, true);
        title.setPadding(0, 0, 0, dp(4));
        main.addView(title);

        tvStatus = txt("", 13, 0xFF888888, false);
        tvStatus.setPadding(0, 0, 0, dp(16));
        main.addView(tvStatus);

        main.addView(sectionLabel("📋 License Info"));
        tvClient = txt("", 13, 0xFF69f0ae, false);
        tvClient.setPadding(0, 0, 0, dp(4));
        main.addView(tvClient);
        tvTarget = txt("", 11, 0xFF666666, false);
        tvTarget.setPadding(0, 0, 0, dp(12));
        main.addView(tvTarget);

        main.addView(sectionLabel("🌐 Server URL"));
        etServerUrl = makeInput("https://your-server.com/api/validate.php", "");
        main.addView(etServerUrl);

        main.addView(sectionLabel("🔑 Secret Key"));
        etSecret = makeInput("Secret Key — যেমন: A1B2C3D4...", "");
        main.addView(etSecret);

        Button btnActivate = makeButton("🔑 Activate License", 0xFF7c4dff);
        btnActivate.setOnClickListener(v -> activate());
        main.addView(btnActivate);

        main.addView(sectionLabel("▶ SMS Monitoring"));
        swService = new androidx.appcompat.widget.SwitchCompat(this);
        swService.setText("SMS মনিটরিং চালু রাখুন");
        swService.setTextColor(0xFFCCCCCC);
        swService.setTextSize(14);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, 0, 0, dp(16));
        swService.setLayoutParams(sp);
        main.addView(swService);

        main.addView(sectionLabel("📤 Offline Queue"));
        tvQueue = txt("", 13, 0xFF888888, false);
        tvQueue.setPadding(0, 0, 0, dp(8));
        main.addView(tvQueue);
        Button btnFlush = makeButton("📤 Pending পাঠান", 0xFF37474f);
        btnFlush.setOnClickListener(v -> flush());
        main.addView(btnFlush);

        main.addView(sectionLabel("📜 Log"));
        scrollLog = new ScrollView(this);
        tvLog = new TextView(this);
        tvLog.setText("কোনো লগ নেই।");
        tvLog.setTextColor(0xFF00FF88);
        tvLog.setBackgroundColor(0xFF060610);
        tvLog.setTextSize(11);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        scrollLog.setLayoutParams(lp);
        scrollLog.addView(tvLog);
        main.addView(scrollLog);

        root.addView(main);
        setContentView(root);
    }

    private void loadState() {
        etServerUrl.setText(AppConfig.getServerUrl(this));
        etSecret.setText(AppConfig.getSecret(this));
        boolean on = AppConfig.isActive(this);
        swService.setChecked(on);
        swService.setOnCheckedChangeListener((btn, checked) -> toggleService(checked));
        updateStatus();
        updateQueue();
        updateClientInfo();
    }

    private void activate() {
        String serverUrl = etServerUrl.getText().toString().trim();
        String secret    = etSecret.getText().toString().trim().toUpperCase();
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Server URL দিন!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (secret.isEmpty()) {
            Toast.makeText(this, "Secret Key দিন!", Toast.LENGTH_SHORT).show();
            return;
        }
        addLog("🔄 License validate হচ্ছে...");
        LicenseValidator.validate(this, serverUrl, secret, (ok, msg) -> runOnUiThread(() -> {
            addLog(msg);
            if (ok) {
                updateStatus();
                updateClientInfo();
                updateQueue();
                swService.setChecked(true);
                toggleService(true);
                Toast.makeText(this, "✅ License Valid!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void toggleService(boolean enable) {
        AppConfig.prefs(this).edit().putBoolean(AppConfig.K_ACTIVE, enable).apply();
        Intent svc = new Intent(this, SmsMonitorService.class);
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
            else startService(svc);
        } else {
            stopService(svc);
        }
        updateStatus();
    }

    private void flush() {
        if (SmsQueue.isEmpty(this)) { addLog("📭 Queue খালি"); return; }
        addLog("📤 Queue flush শুরু...");
        new ServerSender(this).flushQueue(msg -> { addLog(msg); updateQueue(); });
    }

    private void updateStatus() {
        boolean setup = AppConfig.isSetupDone(this);
        boolean on    = AppConfig.isActive(this);
        if (!setup) {
            tvStatus.setText("⚠️ License activate করুন");
            tvStatus.setTextColor(0xFFFFAA00);
        } else if (on) {
            tvStatus.setText("● সার্ভিস চলছে — SMS monitoring active");
            tvStatus.setTextColor(0xFF00FF88);
        } else {
            tvStatus.setText("● সার্ভিস বন্ধ");
            tvStatus.setTextColor(0xFFFF4444);
        }
    }

    private void updateClientInfo() {
        String name = AppConfig.getClientName(this);
        String url  = AppConfig.getTargetUrl(this);
        if (!name.isEmpty()) {
            tvClient.setText("Client: " + name);
            tvTarget.setText("Target: " + url);
        } else {
            tvClient.setText("License activate করুন");
            tvClient.setTextColor(0xFF666666);
        }
    }

    private void updateQueue() {
        int n = SmsQueue.size(this);
        if (n > 0) { tvQueue.setText("⏳ " + n + "টি payment পাঠানো বাকি"); tvQueue.setTextColor(0xFFFFAA00); }
        else        { tvQueue.setText("✅ Queue খালি");                      tvQueue.setTextColor(0xFF00FF88); }
    }

    public void addLog(String msg) {
        runOnUiThread(() -> {
            String cur = tvLog.getText().toString();
            if ("কোনো লগ নেই।".equals(cur)) cur = "";
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            tvLog.setText("[" + time + "] " + msg + "\n" + cur);
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_UP));
        });
    }

    private void requestSmsPermission() {
        List<String> need = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                need.add(p);
        }
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ) {
            for (int r : results)
                if (r != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "⚠️ SMS Permission দিন Settings থেকে!", Toast.LENGTH_LONG).show();
                    break;
                }
        }
    }

    private TextView sectionLabel(String t) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextColor(0xFF7c4dff); tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(16), 0, dp(8));
        tv.setLayoutParams(p); return tv;
    }
    private TextView txt(String t, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextSize(sp); tv.setTextColor(color);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }
    private EditText makeInput(String hint, String val) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setHintTextColor(0xFF444466); et.setText(val);
        et.setTextColor(0xFFFFFFFF); et.setBackgroundColor(0xFF13132a);
        et.setPadding(dp(12), dp(10), dp(12), dp(10)); et.setSingleLine(true);
        et.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(10)); et.setLayoutParams(p); return et;
    }
    private Button makeButton(String t, int color) {
        Button btn = new Button(this);
        btn.setText(t); btn.setBackgroundColor(color); btn.setTextColor(0xFFFFFFFF); btn.setTextSize(14);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        p.setMargins(0, 0, 0, dp(8)); btn.setLayoutParams(p); return btn;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
