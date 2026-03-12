package com.payment.sms.utils;

public class ParsedPayment {
    public String method;
    public String transactionId;
    public String amount;
    public String sender;
    public String rawSms;
    public long   timestamp;

    public ParsedPayment(String method, String transactionId,
                         String amount, String sender, String rawSms) {
        this.method        = method;
        this.transactionId = transactionId;
        this.amount        = amount;
        this.sender        = sender;
        this.rawSms        = rawSms;
        this.timestamp     = System.currentTimeMillis();
    }
}
