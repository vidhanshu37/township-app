package com.township.backend.matching;

import java.util.List;

public final class Matcher {

    public static final int AUTO_MIN = 60;
    public static final int REVIEW_MIN = 30;
    public static final int AMBIGUITY_GAP = 15;

    public enum Status { AUTO_MATCHED, NEEDS_REVIEW, UNMATCHED }

    public record Decision(Status status, Long flatId, int score, List<String> reasons, String note) {}

    private record Scored(FlatSnapshot flat, MatchScorer.Score score) {}

    private Matcher() {}

    public static Decision decide(BankRow row, List<FlatSnapshot> flats) {
        List<Scored> ranked = flats.stream()
                .map(f -> new Scored(f, MatchScorer.score(row, f)))
                .sorted((a, b) -> Integer.compare(b.score().total(), a.score().total()))
                .toList();
        if (ranked.isEmpty() || ranked.get(0).score().total() < REVIEW_MIN) {
            int best = ranked.isEmpty() ? 0 : ranked.get(0).score().total();
            return new Decision(Status.UNMATCHED, null, best, List.of(), "score below " + REVIEW_MIN);
        }
        Scored best = ranked.get(0);
        int bestScore = best.score().total();
        int second = ranked.size() > 1 ? ranked.get(1).score().total() : 0;
        long id = best.flat().flatId();

        if (bestScore >= AUTO_MIN && bestScore - second >= AMBIGUITY_GAP) {
            return new Decision(Status.AUTO_MATCHED, id, bestScore, best.score().reasons(), null);
        }
        String note = bestScore >= AUTO_MIN
                ? "ambiguous: another flat scored " + second
                : "score " + bestScore + " is below auto threshold " + AUTO_MIN;
        return new Decision(Status.NEEDS_REVIEW, id, bestScore, best.score().reasons(), note);
    }
}