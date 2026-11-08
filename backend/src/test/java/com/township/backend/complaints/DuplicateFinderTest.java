package com.township.backend.complaints;

import com.township.backend.complaints.DuplicateFinder.Candidate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateFinderTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    private static Candidate lift(long id, String text, long hoursAgo) {
        return new Candidate(id, "LIFT", text, NOW.minus(Duration.ofHours(hoursAgo)));
    }

    @Test
    void sameProblemReworded_isLinked() {
        var found = DuplicateFinder.find("Lift is not working tower 12", "LIFT",
                List.of(lift(1, "Lift not working in tower 12", 2)), NOW);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().id());
    }

    @Test
    void differentCategoryIsNeverLinked() {
        var found = DuplicateFinder.find("Lift is not working tower 12", "ELECTRICAL",
                List.of(lift(1, "Lift not working in tower 12", 2)), NOW);
        assertTrue(found.isEmpty());
    }

    @Test
    void oldOrFutureComplaintsAreIgnored() {
        assertTrue(DuplicateFinder.find("Lift not working tower 12", "LIFT",
                List.of(lift(1, "Lift not working tower 12", 49)), NOW).isEmpty());
        assertTrue(DuplicateFinder.find("Lift not working tower 12", "LIFT",
                List.of(lift(1, "Lift not working tower 12", -3)), NOW).isEmpty());
    }

    @Test
    void lowWordOverlapIsNotADuplicate() {
        var found = DuplicateFinder.find("Lift button light broken in tower 5", "LIFT",
                List.of(lift(1, "Lift not working in tower 12", 2)), NOW);
        assertTrue(found.isEmpty());
    }

    @Test
    void equalMatchesLinkToTheOlderComplaint() {
        var found = DuplicateFinder.find("Lift not working tower 12", "LIFT",
                List.of(lift(2, "Lift not working tower 12", 2), lift(1, "Lift not working tower 12", 5)), NOW);
        assertEquals(1L, found.get().id());
    }

    @Test
    void betterMatchBeatsAnOlderWeakerMatch() {
        var found = DuplicateFinder.find("Lift not working tower 12", "LIFT",
                List.of(lift(1, "Lift not working tower 7", 6), lift(2, "Lift not working tower 12", 1)), NOW);
        assertEquals(2L, found.get().id());
    }
}