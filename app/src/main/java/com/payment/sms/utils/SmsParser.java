package com.payment.sms.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    public ParsedPayment parse(String sender, String body) {
        String lower = body.toLowerCase();
        if (lower.contains("bkash") || lower.contains("বিকাশ"))
            return parseBkash(sender, body);
        if (lower.contains("nagad") || lower.contains("নগদ"))
            return parseNagad(sender, body);
        if (lower.contains("rocket") || lower.contains("dutch-bangla") || lower.contains("dbbl"))
            return parseRocket(sender, body);
        return null;
    }

    private ParsedPayment parseBkash(String sender, String body) {
        String txId = extract(body,
            "(?:TrxID|TxnID|TxID|Trx\\s*ID|Transaction\\s*ID)[:\\s,]+([A-Z0-9]{8,20})",
            "(?:transaction|trx)[^A-Z0-9]*([A-Z0-9]{8,20})");
        if (txId == null) return null;
        return new ParsedPayment("bkash", txId, extractAmount(body), sender, body);
    }

    private ParsedPayment parseNagad(String sender, String body) {
        String txId = extract(body,
            "(?:TrxID|Trx\\s*ID|TxnID|TxID)[:\\s,]+([A-Z0-9]{8,20})");
        if (txId == null) return null;
        return new ParsedPayment("nagad", txId, extractAmount(body), sender, body);
    }

    private ParsedPayment parseRocket(String sender, String body) {
        String txId = extract(body,
            "(?:TrxID|Trx\\s*ID|TxnID|TransID)[:\\s,]+([A-Z0-9]{8,20})");
        if (txId == null) return null;
        return new ParsedPayment("rocket", txId, extractAmount(body), sender, body);
    }

    private String extract(String text, String... patterns) {
        for (String pat : patterns) {
            Matcher m = Pattern.compile(pat, Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private String extractAmount(String text) {
        Matcher m = Pattern.compile(
            "(?:Tk|BDT|Amount)[.\\s:]+([0-9,]+\\.?[0-9]*)|([0-9,]+\\.?[0-9]*)\\s*(?:Tk|BDT|টাকা)",
            Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            String v = m.group(1) != null ? m.group(1) : m.group(2);
            return v != null ? v.replace(",", "").trim() : "0";
        }
        return "0";
    }
}
