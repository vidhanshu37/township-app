package com.township.backend.matching;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MatchScorer {
    public static final int W_REFERENCE = 40;
    public static final int W_FLAT = 30;
    public static final int W_AMOUNT = 20;
    public static final int W_NAME = 5;
    public static final int W_DATE = 5;
    static final int DATE_WINDOW_DAYS = 120;

    public record Score(int total, List<String> reasons) {}

    private MatchScorer() {}

    public static Score score(BankRow row, FlatSnapshot flat) {
        String text = row.narration().toUpperCase(Locale.ROOT);
        int total = 0;
        List<String> why = new ArrayList<>();

        if (flat.payRef() != null && contains(text, flat.payRef())) {
            total += W_REFERENCE;
            why.add("reference " + flat.payRef() + " found (+" + W_REFERENCE + ")");
        }
        if (flatNoFound(text, flat.flatNo())) {
            total += W_FLAT;
            why.add("flat " + flat.flatNo() + " found (+" + W_FLAT + ")");
        }
        int k = prefixMatch(row.credit(), flat.openBills());
        if (k > 0) {
            total += W_AMOUNT;
            why.add("amount " + row.credit() + " equals oldest " + k + " open bill(s) (+" + W_AMOUNT + ")");
        }
        if (nameFound(text, flat.residentName())) {
            total += W_NAME;
            why.add("resident name found (+" + W_NAME + ")");
        }
        if (flat.windowStart() != null && !row.date().isBefore(flat.windowStart())
                && !row.date().isAfter(flat.windowStart().plusDays(DATE_WINDOW_DAYS))) {
            total += W_DATE;
            why.add("date in expected window (+" + W_DATE + ")");
        }
        return new Score(total, why);
    }

    static int prefixMatch(BigDecimal credit, List<BigDecimal> openBills) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < openBills.size(); i++) {
            sum = sum.add(openBills.get(i));
            if (sum.compareTo(credit) == 0) {
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean contains(String upperText, String token) {
        return Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(token.toUpperCase(Locale.ROOT)) + "(?![A-Z0-9])")
                .matcher(upperText).find();
    }

    static boolean flatNoFound(String upperText, String flatNo) {
        String[] parts = flatNo.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
        StringBuilder re = new StringBuilder("(?<![A-Z0-9])");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                re.append("[^A-Z0-9]*");
            }
            re.append(Pattern.quote(parts[i]));
        }
        re.append("(?![A-Z0-9])");
        return Pattern.compile(re.toString()).matcher(upperText).find();
    }

    private static boolean nameFound(String upperText, String name) {
        if (name == null) {
            return false;
        }
        for (String part : name.toUpperCase(Locale.ROOT).split("[^A-Z]+")) {
            if (part.length() >= 3 && contains(upperText, part)) {
                return true;
            }
        }
        return false;
    }
}