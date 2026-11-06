package com.township.backend.matching;

import com.township.backend.matching.Matcher.Status;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatcherTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static final FlatSnapshot SWATI = new FlatSnapshot(2, "T12-0408", "TWN0002", "Swati Rao",
            List.of(bd("6517.64"), bd("6370.00")), LocalDate.of(2026, 9, 1));
    private static final FlatSnapshot AAMIR = new FlatSnapshot(3, "T21-1601", "TWN0003", "Aamir Khan",
            List.of(bd("710.24")), LocalDate.of(2026, 10, 1));
    private static final List<FlatSnapshot> FLATS = List.of(SWATI, AAMIR);

    private static BankRow row(String narration, String credit, String date) {
        return new BankRow(2, LocalDate.parse(date), narration, bd(credit), null);
    }

    @Test
    void referenceExactAmountAndDateIsAutoMatched() {
        var d = Matcher.decide(row("UPI/123/TWN0002 maintenance", "6517.64", "2026-11-05"), FLATS);
        assertEquals(Status.AUTO_MATCHED, d.status());
        assertEquals(2L, d.flatId());
        assertEquals(65, d.score());
    }

    @Test
    void oneTransactionCoveringTwoBillsMatchesByPrefixSum() {
        var d = Matcher.decide(row("NEFT Swati T12 0408 dues", "12887.64", "2026-11-05"), FLATS);
        assertEquals(Status.AUTO_MATCHED, d.status());
        assertEquals(2L, d.flatId());
        assertEquals(60, d.score());
    }

    @Test
    void flatAndAmountWithoutNameGoesToReview() {
        var d = Matcher.decide(row("NEFT T12-0408", "6517.64", "2026-11-05"), FLATS);
        assertEquals(Status.NEEDS_REVIEW, d.status());
        assertEquals(2L, d.flatId());
        assertEquals(55, d.score());
    }

    @Test
    void referenceWithWrongAmountGoesToReview() {
        var d = Matcher.decide(row("UPI TWN0002", "5000.00", "2026-11-05"), FLATS);
        assertEquals(Status.NEEDS_REVIEW, d.status());
        assertEquals(45, d.score());
    }

    @Test
    void narrationMentioningTwoFlatsStillNeedsAClearWinner() {
        var d = Matcher.decide(row("TWN0002 T21-1601 rent", "6517.64", "2026-11-05"), FLATS);
        assertEquals(Status.AUTO_MATCHED, d.status());
        assertEquals(2L, d.flatId());

        var d2 = Matcher.decide(row("TWN0002 T21-1601 rent", "999.00", "2026-11-05"), FLATS);
        assertEquals(Status.NEEDS_REVIEW, d2.status());
    }

    @Test
    void closeScoresAreAmbiguousEvenAboveAutoThreshold() {
        FlatSnapshot twin = new FlatSnapshot(4, "T12-0409", "TWN0004", "Rohan Das",
                List.of(bd("6517.64")), LocalDate.of(2026, 9, 1));
        var d = Matcher.decide(row("TWN0002 TWN0004 split", "6517.64", "2026-11-05"),
                List.of(SWATI, twin));
        assertEquals(Status.NEEDS_REVIEW, d.status());
    }

    @Test
    void amountAndDateAloneAreUnmatched() {
        var d = Matcher.decide(row("CASH DEPOSIT", "6517.64", "2026-11-05"), FLATS);
        assertEquals(Status.UNMATCHED, d.status());
        assertNull(d.flatId());
    }

    @Test
    void flatNumberMustNotMatchInsideALongerNumber() {
        assertEquals(true, MatchScorer.flatNoFound("PAID T12-0408 THANKS", "T12-0408"));
        assertEquals(true, MatchScorer.flatNoFound("PAID T120408", "T12-0408"));
        assertEquals(false, MatchScorer.flatNoFound("REF T1204081", "T12-0408"));
    }

    @Test
    void dateOutsideWindowGivesNoDatePoints() {
        var d = Matcher.decide(row("UPI TWN0002", "6517.64", "2027-06-01"), FLATS);
        assertEquals(60, d.score());
        assertEquals(Status.AUTO_MATCHED, d.status());
    }
}