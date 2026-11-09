package com.township.backend.complaints;

public final class ComplaintPolicy {

    private ComplaintPolicy() {}

    public static int responseHours(TriageEngine.Priority priority) {
        return switch (priority) {
            case HIGH -> 2;
            case MEDIUM -> 24;
            case LOW -> 72;
        };
    }

    public static boolean validRating(int rating) {
        return rating >= 1 && rating <= 5;
    }
}