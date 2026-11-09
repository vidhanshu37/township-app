package com.township.backend.complaints;

import com.township.backend.complaints.TriageEngine.Category;
import com.township.backend.complaints.TriageEngine.Priority;
import com.township.backend.complaints.TriageEngine.Result;
import com.township.backend.complaints.TriageEngine.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriageEngineTest {

    private static void expect(String text, Category c, Priority p, Source s) {
        Result r = TriageEngine.classify(text);
        assertEquals(c, r.category());
        assertEquals(p, r.priority());
        assertEquals(s, r.source());
    }

    @Test
    void gasSmellIsHighSafetyInEnglishAndHinglish() {
        expect("There is a gas smell in the corridor", Category.SAFETY, Priority.HIGH, Source.SAFETY_RULE);
        expect("Gas ki smell aa rahi hai kitchen me", Category.SAFETY, Priority.HIGH, Source.SAFETY_RULE);
    }

    @Test
    void electricalHazardAndLiftEntrapmentAreHigh() {
        expect("Short circuit in the meter box, sparks coming", Category.ELECTRICAL, Priority.HIGH, Source.SAFETY_RULE);
        expect("Someone is stuck in the lift", Category.LIFT, Priority.HIGH, Source.SAFETY_RULE);
        expect("Lift me log phans gaye hain", Category.LIFT, Priority.HIGH, Source.SAFETY_RULE);
        expect("Lift mein bachha fasa hua hai", Category.LIFT, Priority.HIGH, Source.SAFETY_RULE);
        expect("Two elderly people trapped inside lift between floors", Category.LIFT, Priority.HIGH, Source.SAFETY_RULE);
        expect("Lift stuck on 5th floor", Category.LIFT, Priority.HIGH, Source.SAFETY_RULE);
    }

    @Test
    void liftSpeedComplaintIsNotAnEntrapment() {
        expect("Lift mein fast chalti hai, bahut shor", Category.LIFT, Priority.MEDIUM, Source.KEYWORD);
    }

    @Test
    void floodingAndTheftAreHigh() {
        expect("Bathroom pipe burst, water everywhere", Category.PLUMBING, Priority.HIGH, Source.SAFETY_RULE);
        expect("Someone tried theft of my bike", Category.SECURITY, Priority.HIGH, Source.SAFETY_RULE);
    }

    @Test
    void safetyWordBeatsALowPriorityCategory() {
        // noise keywords match too, but smoke must win
        expect("Loud music and smoke from the party hall", Category.SAFETY, Priority.HIGH, Source.SAFETY_RULE);
    }

    @Test
    void ordinaryComplaintsGetKeywordCategoryAndNormalPriority() {
        expect("Lift not working since morning", Category.LIFT, Priority.MEDIUM, Source.KEYWORD);
        expect("Paani leak ho raha hai bathroom me", Category.PLUMBING, Priority.MEDIUM, Source.KEYWORD);
        expect("Garbage not collected from the dustbin", Category.HOUSEKEEPING, Priority.LOW, Source.KEYWORD);
    }

    @Test
    void urgencyWordsRaiseThePriorityByOneLevel() {
        expect("Garbage not collected, urgent please", Category.HOUSEKEEPING, Priority.MEDIUM, Source.KEYWORD);
        expect("Tap leaking in kitchen, urgent", Category.PLUMBING, Priority.HIGH, Source.KEYWORD);
    }

    @Test
    void unknownTextFallsBackToOtherMedium() {
        expect("I want to suggest a new yoga class", Category.OTHER, Priority.MEDIUM, Source.FALLBACK);
        expect("   ", Category.OTHER, Priority.MEDIUM, Source.FALLBACK);
        expect(null, Category.OTHER, Priority.MEDIUM, Source.FALLBACK);
    }

    @Test
    void safetyWordsMatchWholeWordsOnly() {
        // "fireplace" is not fire, "smoking" is a nuisance not smoke
        expect("Fireplace in the lobby looks nice", Category.OTHER, Priority.MEDIUM, Source.FALLBACK);
        expect("Smoking in lift lobby", Category.LIFT, Priority.MEDIUM, Source.KEYWORD);
    }

    @Test
    void reasonExplainsTheDecision() {
        assertTrue(TriageEngine.classify("gas leak near the kitchen").reason().contains("gas leak"));
        assertTrue(TriageEngine.classify("Lift not working").reason().contains("lift"));
    }
}