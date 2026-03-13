package com.payment.sms.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.payment.sms.utils.AppConfig;
import com.payment.sms.utils.LicenseValidator;
import com.payment.sms.utils.ParsedPayment;
import com.payment.sms.utils.ServerSender;
import com.payment.sms.utils.SmsParser;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;
        Object[] pdus   = (Object[]) bundle.get("pdus");
        String   format = bundle.getString("format");
        if (pdus == null) return;

        for (Object pdu : pdus) {
            try {
                SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
                if (msg == null) continue;

                String sender = msg.getOriginatingAddress();
                String body   = msg.getMessageBody();
                if (sender == null || body == null || body.trim().isEmpty()) continue;

                // সব SMS log করো
                broadcastLog(ctx, "📩", sender, body, "received");

                if (!AppConfig.isSetupDone(ctx) || !AppConfig.isActive(ctx)) {
                    broadcastLog(ctx, "⚠️", sender, "Service inactive", "skip");
                    continue;
                }

                if (AppConfig.needsSync(ctx)) LicenseValidator.reValidate(ctx);

                // Sender allowed check
                if (!AppConfig.isSenderAllowed(ctx, sender)) {
                    broadcastLog(ctx, "🚫", sender, "Sender not allowed", "fail");
                    continue;
                }

                // Payment SMS check (sender name দিয়ে)
                if (!AppConfig.isPaymentSms(ctx, sender, body)) {
                    broadcastLog(ctx, "❌", sender, "Not a payment SMS", "fail");
                    continue;
                }

                // Parse
                ParsedPayment payment = new SmsParser().parse(sender, body);
                if (payment == null) {
                    broadcastLog(ctx, "⚠️", sender, "Parse failed — TrxID not found", "fail");
                    continue;
                }

                broadcastLog(ctx, "✅", sender, payment.method.toUpperCase() + " | TrxID: " + payment.transactionId + " | ৳" + payment.amount, "success");

                // Server এ পাঠাও
                new ServerSender(ctx).send(payment, log -> {
                    Log.d(TAG, log);
                    ctx.sendBroadcast(new Intent("com.payment.sms.SMS_LOG")
                        .putExtra("icon", "📤")
                        .putExtra("tag", sender)
                        .putExtra("msg", log)
                        .putExtra("type", "sent"));
                });

                ctx.sendBroadcast(new Intent("com.payment.sms.NEW_PAYMENT")
                    .putExtra("method",         payment.method)
                    .putExtra("transaction_id", payment.transactionId)
                    .putExtra("amount",         payment.amount)
                    .putExtra("status",         "success"));

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }
    }

    private void broadcastLog(Context ctx, String icon, String tag, String msg, String type) {
        ctx.sendBroadcast(new Intent("com.payment.sms.SMS_LOG")
            .putExtra("icon", icon)
            .putExtra("tag",  tag)
            .putExtra("msg",  msg)
            .putExtra("type", type));
    }
}
