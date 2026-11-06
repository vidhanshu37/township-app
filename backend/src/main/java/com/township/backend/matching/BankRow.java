package com.township.backend.matching;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

public record BankRow(int lineNo, LocalDate date, String narration, BigDecimal credit, String utr) {

    public String fingerprint() {
        String text = date + "|" + credit.setScale(2) + "|"
                + (utr == null ? "" : utr.trim().toUpperCase(Locale.ROOT)) + "|"
                + narration.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}