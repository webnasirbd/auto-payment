package com.payment.sms.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.payment.sms.services.SmsMonitorService;
import com.payment.sms.utils.AppConfig;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            if (AppConfig.isSetupDone(ctx) && AppConfig.isActive(ctx)) {
                Intent svc = new Intent(ctx, SmsMonitorService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ctx.startForegroundService(svc);
                else
                    ctx.startService(svc);
            }
        }
    }
}
