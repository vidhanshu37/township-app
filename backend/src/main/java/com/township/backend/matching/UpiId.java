package com.township.backend.matching;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpiId {

    private static final Pattern VPA = Pattern.compile("[a-z0-9][a-z0-9._-]+@[a-z][a-z0-9]+");

    private UpiId() {}

    public static Set<String> extract(String narration) {
        Set<String> found = new LinkedHashSet<>();
        if (narration == null) {
            return found;
        }
        Matcher m = VPA.matcher(narration.toLowerCase(Locale.ROOT));
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    public static boolean appearsIn(String narration, String upiId) {
        if (narration == null || upiId == null || upiId.isBlank()) {
            return false;
        }
        String re = "(?<![a-z0-9._-])" + Pattern.quote(upiId.toLowerCase(Locale.ROOT)) + "(?![a-z0-9])";
        return Pattern.compile(re).matcher(narration.toLowerCase(Locale.ROOT)).find();
    }
}