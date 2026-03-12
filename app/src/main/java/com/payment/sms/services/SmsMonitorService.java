package com.payment.sms.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.payment.sms.MainActivity;
import com.payment.sms.R;
import com.payment.sms.utils.AppConfig;
import com.payment.sms.utils.LicenseValidator;
import com.payment.sms.utils.ServerSender;
import com.payment.sms.utils.SmsQueue;

public class SmsMonitorService extends Service {

    private static final String TAG        = "SmsMonitorService";
    private static final String CHANNEL_ID = "paysms_ch";
    private static final int    NOTIF_ID   = 2001;

    private final Handler         handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver     netReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotifChannel();
        registerNetworkReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotif());
        // App start হলে সাথে সাথে re-validate + queue flush
        handler.postDelayed(() -> {
            LicenseValidator.reValidate(this);
            new ServerSender(this).flushQueue(msg -> Log.d(TAG, msg));
        }, 3000);
        return START_STICKY;
    }

    private void registerNetworkReceiver() {
        netReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (new ServerSender(context).isOnline()) {
                    handler.postDelayed(() -> {
                        LicenseValidator.reValidate(context);
                        new ServerSender(context).flushQueue(msg -> Log.d(TAG, msg));
                        startForeground(NOTIF_ID, buildNotif());
                    }, 2000);
                }
            }
        };
        registerReceiver(netReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Payment SMS", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.enableVibration(false);
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotif() {
        Intent i  = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int pending = SmsQueue.size(this);
        String text = pending > 0
            ? "Monitoring | ⏳ " + pending + "টি পাঠানো বাকি"
            : "SMS মনিটরিং চলছে...";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Payment SMS Gateway")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (netReceiver != null) {
            try { unregisterReceiver(netReceiver); } catch (Exception ignored) {}
        }
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
