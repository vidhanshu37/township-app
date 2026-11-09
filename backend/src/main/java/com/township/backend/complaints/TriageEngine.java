package com.township.backend.complaints;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class TriageEngine {

    public enum Category { PLUMBING, ELECTRICAL, LIFT, SECURITY, SAFETY, HOUSEKEEPING, PARKING, NOISE, OTHER }

    public enum Priority { HIGH, MEDIUM, LOW }

    public enum Source { SAFETY_RULE, KEYWORD, FALLBACK, LLM }

    public record Result(Category category, Priority priority, Source source, String reason) {}

    private record SafetyRule(Pattern pattern, Category category, String label) {}

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    private static final List<SafetyRule> SAFETY = List.of(
            new SafetyRule(p("\\b(gas (leak\\w*|smell|odou?r)|smell (of|like) gas|gas (ki|ka) (smell|badbu|boo)|cylinder (leak\\w*|burst)|lpg leak\\w*)\\b"),
                    Category.SAFETY, "gas leak"),
            new SafetyRule(p("\\b(fire|aag|smoke|dhuan|dhua)\\b"), Category.SAFETY, "fire or smoke"),
            new SafetyRule(p("\\b(unconscious|heart attack|ambulance|medical emergency|not breathing|collapsed)\\b"),
                    Category.SAFETY, "medical emergency"),
            new SafetyRule(p("\\b(short ?circuit|sparks?|sparking|electric(al)? shock|shock lag\\w*|current lag\\w*|exposed wires?|live wire)\\b"),
                    Category.ELECTRICAL, "electrical hazard"),
            new SafetyRule(p("\\b((stuck|trapped)( \\w+){0,3} (in|inside) (the )?lifts?|lifts? (me|mein|mai)( \\w+){0,3} (fas(a|e|s)\\w*|phans\\w*|atak\\w*)|lifts? (stuck|trapped|fell|free ?fall))\\b"),
                    Category.LIFT, "person stuck in lift"),
            new SafetyRule(p("\\b(flood\\w*|pipe burst|burst pipe|water (is )?(entering|coming) (in|into) (the )?(house|flat|room)|paani (bhar|ghus)\\w*)\\b"),
                    Category.PLUMBING, "flooding"),
            new SafetyRule(p("\\b(burglar\\w*|robber\\w*|robbed|theft|thief|thieves|chori|intruder\\w*|broke into)\\b"),
                    Category.SECURITY, "theft or intruder"));

    private static final Map<Category, Pattern> KEYWORDS = new EnumMap<>(Category.class);

    static {
        KEYWORDS.put(Category.PLUMBING, p("\\b(leak\\w*|tap|taps|pipe\\w*|drain\\w*|sewage|water|toilet|flush|bathroom|seepage|paani|nal|naali)\\b"));
        KEYWORDS.put(Category.ELECTRICAL, p("\\b(electric\\w*|power|lights?|bulb|switch\\w*|wiring|wires?|fuse|mcb|voltage|inverter|bijli|current)\\b"));
        KEYWORDS.put(Category.LIFT, p("\\b(lifts?|elevators?)\\b"));
        KEYWORDS.put(Category.SECURITY, p("\\b(guards?|security|gate|cctv|stranger|visitors?|watchman|camera)\\b"));
        KEYWORDS.put(Category.HOUSEKEEPING, p("\\b(garbage|trash|dustbin|cleaning|dirty|sweep\\w*|kachra|safai|cockroach\\w*|pests?|mosquito\\w*)\\b"));
        KEYWORDS.put(Category.PARKING, p("\\b(parking|parked|vehicles?|car|bike|scooter)\\b"));
        KEYWORDS.put(Category.NOISE, p("\\b(noise|noisy|loud|music|party|shout\\w*|construction|drilling|shor)\\b"));
    }

    private static final Pattern URGENT = p("\\b(urgent\\w*|immediately|asap|emergency|serious)\\b");

    private TriageEngine() {}

    public static Result classify(String rawText) {
        String text = normalize(rawText);
        if (text.isEmpty()) {
            return new Result(Category.OTHER, Priority.MEDIUM, Source.FALLBACK, "empty text");
        }

        for (SafetyRule rule : SAFETY) {
            if (rule.pattern().matcher(text).find()) {
                return new Result(rule.category(), Priority.HIGH, Source.SAFETY_RULE,
                        "safety rule: " + rule.label());
            }
        }

        Category best = null;
        int bestCount = 0;
        List<String> matched = new ArrayList<>();
        for (Map.Entry<Category, Pattern> e : KEYWORDS.entrySet()) {
            List<String> hits = new ArrayList<>();
            var m = e.getValue().matcher(text);
            while (m.find()) {
                if (!hits.contains(m.group())) {
                    hits.add(m.group());
                }
            }
            if (hits.size() > bestCount) {
                best = e.getKey();
                bestCount = hits.size();
                matched = hits;
            }
        }

        if (best == null) {
            return new Result(Category.OTHER, Priority.MEDIUM, Source.FALLBACK, "no keyword matched");
        }
        Priority base = switch (best) {
            case HOUSEKEEPING, PARKING, NOISE -> Priority.LOW;
            default -> Priority.MEDIUM;
        };
        boolean urgent = URGENT.matcher(text).find();
        Priority priority = urgent ? bump(base) : base;
        return new Result(best, priority, Source.KEYWORD,
                "keywords: " + String.join(", ", matched) + (urgent ? "; urgency words raised priority" : ""));
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static Priority bump(Priority p) {
        return p == Priority.LOW ? Priority.MEDIUM : Priority.HIGH;
    }
}