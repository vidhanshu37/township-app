package com.township.backend.matching;

import com.township.backend.matching.Matcher.Status;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpiMatchingTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static final FlatSnapshot SWATI = new FlatSnapshot(2, "T12-0408", "TWN0002", "Swati Joshi",
            List.of(bd("6517.64"), bd("6370.00")), LocalDate.of(2026, 9, 1), List.of("swati.joshi@oksbi"));
    private static final FlatSnapshot AAMIR = new FlatSnapshot(3, "T21-1601", "TWN0003", "Aamir Shaikh",
            List.of(bd("710.24")), LocalDate.of(2026, 10, 1), List.of("aamir.shaikh@ybl"));

    private static BankRow row(String narration, String credit) {
        return new BankRow(2, LocalDate.parse("2026-11-05"), narration, bd(credit), null);
    }

    @Test
    void extractsUpiIdsFromANarration() {
        assertEquals(Set.of("swati.joshi@oksbi"), UpiId.extract("UPI/402837461920/Swati.Joshi@OKSBI/Payment"));
        assertTrue(UpiId.extract("CASH DEPOSIT").isEmpty());
        assertTrue(UpiId.extract(null).isEmpty());
    }

    @Test
    void aLongerIdIsNotTheSameId() {
        assertTrue(UpiId.appearsIn("UPI/1/swati.joshi@oksbi/x", "swati.joshi@oksbi"));
        assertFalse(UpiId.appearsIn("UPI/1/xswati.joshi@oksbi/x", "swati.joshi@oksbi"));
        assertFalse(UpiId.appearsIn("UPI/1/a.swati.joshi@oksbi/x", "swati.joshi@oksbi"));
        assertFalse(UpiId.appearsIn("UPI/1/swati.joshi@oksbibank/x", "swati.joshi@oksbi"));
    }

    @Test
    void upiIdPlusExactAmountIsAutoMatchedWithoutAnyFlatNumber() {
        var d = Matcher.decide(row("UPI/402837461920/swati.joshi@oksbi/maintenance", "6517.64"), List.of(SWATI, AAMIR));
        assertEquals(Status.AUTO_MATCHED, d.status());
        assertEquals(2L, d.flatId());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("UPI ID swati.joshi@oksbi")));
    }

    @Test
    void upiIdAloneIsOnlyASuggestionForReview() {
        var d = Matcher.decide(row("UPI/402837461920/swati.joshi@oksbi/part payment", "1000.00"), List.of(SWATI, AAMIR));
        assertEquals(Status.NEEDS_REVIEW, d.status());
        assertEquals(2L, d.flatId());
    }

    @Test
    void referenceAndUpiIdTogetherCountOnce() {
        var d = Matcher.decide(row("UPI/1/swati.joshi@oksbi/TWN0002", "1000.00"), List.of(SWATI, AAMIR));
        // 40 once for reference and UPI ID together (not 80) + 5 name + 5 date
        assertEquals(50, d.score());
    }

    @Test
    void oneUpiIdKnownForTwoFlatsIsAmbiguousAndGoesToReview() {
        var shared = new FlatSnapshot(3, "T21-1601", "TWN0003", "Aamir Shaikh",
                List.of(bd("6517.64")), LocalDate.of(2026, 9, 1), List.of("swati.joshi@oksbi"));
        var d = Matcher.decide(row("UPI/1/swati.joshi@oksbi/maintenance", "6517.64"), List.of(SWATI, shared));
        assertEquals(Status.NEEDS_REVIEW, d.status());
    }

    @Test
    void anotherPersonsUpiIdDoesNotMatchTheFlat() {
        var d = Matcher.decide(row("UPI/1/someone.else@oksbi/maintenance", "1000.00"), List.of(SWATI, AAMIR));
        assertEquals(Status.UNMATCHED, d.status());
    }
}