package com.township.backend.matching;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class BankCsvParser {

    public record RowError(int lineNo, String message) {}
    public record Result(List<BankRow> credits, int debitsIgnored, List<RowError> errors) {}

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private BankCsvParser() {}

    public static Result parse(String content) {
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int h = 0;
        while (h < lines.length && lines[h].isBlank()) {
            h++;
        }
        if (h == lines.length) {
            throw new IllegalArgumentException("CSV is empty");
        }
        List<String> header = splitLine(lines[h]).stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
        int iDate = header.indexOf("date");
        int iNarr = header.indexOf("narration");
        int iCredit = header.indexOf("credit");
        int iDebit = header.indexOf("debit");
        int iUtr = header.indexOf("utr");
        if (iDate < 0 || iNarr < 0 || iCredit < 0) {
            throw new IllegalArgumentException("CSV header must contain: date, narration, credit");
        }

        List<BankRow> credits = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int debits = 0;
        for (int i = h + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            int lineNo = i + 1;
            List<String> c = splitLine(lines[i]);
            try {
                BigDecimal credit = amount(get(c, iCredit));
                BigDecimal debit = iDebit < 0 ? BigDecimal.ZERO : amount(get(c, iDebit));
                if (credit.signum() == 0) {
                    if (debit.signum() > 0) {
                        debits++;
                        continue;
                    }
                    throw new IllegalArgumentException("no credit or debit amount");
                }
                String narration = get(c, iNarr).trim();
                String utr = iUtr < 0 ? "" : get(c, iUtr).trim();
                credits.add(new BankRow(lineNo, date(get(c, iDate)), narration, credit, utr.isEmpty() ? null : utr));
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(lineNo, e.getMessage()));
            }
        }
        return new Result(credits, debits, errors);
    }

    private static String get(List<String> cols, int idx) {
        return idx >= 0 && idx < cols.size() ? cols.get(idx) : "";
    }

    static BigDecimal amount(String raw) {
        String s = raw.replace("₹", "").replace("Rs.", "").replace("Rs", "").replace(",", "").trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal v = new BigDecimal(s);
            if (v.signum() < 0 || v.scale() > 2) {
                throw new IllegalArgumentException("invalid amount: " + raw);
            }
            return v.setScale(2);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount: " + raw);
        }
    }

    static LocalDate date(String raw) {
        String s = raw.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException("invalid date: " + raw);
    }

    static List<String> splitLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
}