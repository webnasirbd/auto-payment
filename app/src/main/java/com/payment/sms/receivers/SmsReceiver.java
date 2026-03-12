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
        if (!AppConfig.isSetupDone(ctx) || !AppConfig.isActive(ctx)) return;

        // Periodic re-validate
        if (AppConfig.needsSync(ctx)) LicenseValidator.reValidate(ctx);

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

                Log.d(TAG, "SMS from: " + sender);

                // Sender allowed?
                if (!AppConfig.isSenderAllowed(ctx, sender)) {
                    Log.d(TAG, "Sender not allowed: " + sender);
                    continue;
                }

                // Payment SMS?
                if (!AppConfig.isPaymentSms(ctx, body)) {
                    Log.d(TAG, "Not a payment SMS");
                    continue;
                }

                // Parse করো
                ParsedPayment payment = new SmsParser().parse(sender, body);
                if (payment == null) {
                    Log.d(TAG, "Parse failed for: " + body.substring(0, Math.min(50, body.length())));
                    continue;
                }

                Log.d(TAG, "Payment parsed: " + payment.method + " | " + payment.transactionId);

                // Server-এ পাঠাও
                new ServerSender(ctx).send(payment, log -> Log.d(TAG, log));

                // MainActivity update করো
                ctx.sendBroadcast(new Intent("com.payment.sms.NEW_PAYMENT")
                    .putExtra("method",         payment.method)
                    .putExtra("transaction_id", payment.transactionId)
                    .putExtra("amount",         payment.amount));

            } catch (Exception e) {
                Log.e(TAG, "Error processing SMS: " + e.getMessage());
            }
        }
    }
}
