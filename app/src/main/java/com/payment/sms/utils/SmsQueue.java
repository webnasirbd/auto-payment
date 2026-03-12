package com.payment.sms.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SmsQueue {

    private static final String TAG  = "SmsQueue";
    private static final String PREF = "sms_queue";
    private static final String KEY  = "queue";

    public static void add(Context ctx, ParsedPayment p) {
        try {
            JSONArray q = get(ctx);
            JSONObject o = new JSONObject();
            o.put("method",         p.method);
            o.put("transaction_id", p.transactionId);
            o.put("amount",         p.amount);
            o.put("sender",         p.sender);
            o.put("raw_sms",        p.rawSms);
            o.put("timestamp",      p.timestamp);
            q.put(o);
            save(ctx, q);
            Log.d(TAG, "Queued: " + p.transactionId + " | total: " + q.length());
        } catch (Exception e) {
            Log.e(TAG, "Add: " + e.getMessage());
        }
    }

    public static List<ParsedPayment> getAll(Context ctx) {
        List<ParsedPayment> list = new ArrayList<>();
        try {
            JSONArray q = get(ctx);
            for (int i = 0; i < q.length(); i++) {
                JSONObject o = q.getJSONObject(i);
                ParsedPayment p = new ParsedPayment(
                    o.getString("method"), o.getString("transaction_id"),
                    o.getString("amount"), o.getString("sender"), o.getString("raw_sms"));
                p.timestamp = o.getLong("timestamp");
                list.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "GetAll: " + e.getMessage());
        }
        return list;
    }

    public static void clear(Context ctx) { save(ctx, new JSONArray()); }
    public static int  size(Context ctx)  { return get(ctx).length(); }
    public static boolean isEmpty(Context ctx) { return size(ctx) == 0; }

    private static JSONArray get(Context ctx) {
        String raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]");
        try { return new JSONArray(raw); } catch (Exception e) { return new JSONArray(); }
    }
    private static void save(Context ctx, JSONArray q) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, q.toString()).apply();
    }
}
