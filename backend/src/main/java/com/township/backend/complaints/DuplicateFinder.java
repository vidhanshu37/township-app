package com.township.backend.complaints;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DuplicateFinder {

    public record Candidate(long id, String category, String text, Instant createdAt) {}

    public static final double MIN_SIMILARITY = 0.5;
    public static final Duration WINDOW = Duration.ofHours(48);

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "this", "that", "has", "have", "are", "was", "not", "from", "since",
            "please", "kindly", "very", "ho", "hai", "hain", "raha", "rahi", "nahi", "ka", "ki", "ke", "me", "mein");

    private DuplicateFinder() {}

    public static Optional<Candidate> find(String text, String category, List<Candidate> openComplaints, Instant now) {
        Set<String> mine = tokens(text);
        Candidate best = null;
        double bestScore = 0;
        for (Candidate c : openComplaints) {
            boolean inWindow = !c.createdAt().isAfter(now) && Duration.between(c.createdAt(), now).compareTo(WINDOW) <= 0;
            if (!inWindow || !c.category().equals(category)) {
                continue;
            }
            double s = similarity(mine, tokens(c.text()));
            if (s < MIN_SIMILARITY) {
                continue;
            }
            if (best == null || s > bestScore || (s == bestScore && c.createdAt().isBefore(best.createdAt()))) {
                best = c;
                bestScore = s;
            }
        }
        return Optional.ofNullable(best);
    }

    static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String t : TriageEngine.normalize(text).split(" ")) {
            boolean useful = !t.isEmpty() && !STOPWORDS.contains(t) && (t.length() >= 3 || t.chars().allMatch(Character::isDigit));
            if (useful) {
                out.add(t);
            }
        }
        return out;
    }
}