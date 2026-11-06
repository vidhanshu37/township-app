package com.township.backend.matching;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BankCsvParserTest {

    private static final String HEADER = "Date,Narration,Debit,Credit,UTR\n";

    @Test
    void parsesCreditsIgnoresDebitsAndReportsBadRows() {
        String csv = HEADER
                + "2026-11-05,\"UPI/Swati, T12-0408\",,\"6,517.64\",UTR123\n"
                + "05/11/2026,ATM WITHDRAWAL,500.00,,\n"
                + "06-Nov-2026,NEFT TWN0003,,710.24,\n"
                + "not-a-date,BAD,,100.00,\n"
                + "2026-11-07,NO AMOUNT,,,\n";
        var r = BankCsvParser.parse(csv);
        assertEquals(2, r.credits().size());
        assertEquals(1, r.debitsIgnored());
        assertEquals(2, r.errors().size());
        assertEquals(LocalDate.of(2026, 11, 5), r.credits().get(0).date());
        assertEquals("UPI/Swati, T12-0408", r.credits().get(0).narration());
        assertEquals(0, new java.math.BigDecimal("6517.64").compareTo(r.credits().get(0).credit()));
        assertEquals("UTR123", r.credits().get(0).utr());
        assertNull(r.credits().get(1).utr());
        assertEquals(LocalDate.of(2026, 11, 6), r.credits().get(1).date());
        assertEquals(5, r.errors().get(0).lineNo());
    }

    @Test
    void missingRequiredColumnFailsTheWholeFile() {
        assertThrows(IllegalArgumentException.class, () -> BankCsvParser.parse("date,narration\n2026-01-01,x\n"));
        assertThrows(IllegalArgumentException.class, () -> BankCsvParser.parse("  \n"));
    }

    @Test
    void negativeAndThreeDecimalAmountsAreErrors() {
        var r = BankCsvParser.parse("date,narration,credit\n2026-11-05,a,-5.00\n2026-11-05,b,1.234\n");
        assertEquals(0, r.credits().size());
        assertEquals(2, r.errors().size());
    }

    @Test
    void fingerprintIgnoresCaseAndSpacingButNotAmount() {
        var a = BankCsvParser.parse(HEADER + "2026-11-05,UPI  swati,,100.00,u1\n").credits().get(0);
        var b = BankCsvParser.parse(HEADER + "2026-11-05, upi swati ,,100.00,U1\n").credits().get(0);
        var c = BankCsvParser.parse(HEADER + "2026-11-05,UPI swati,,100.01,u1\n").credits().get(0);
        assertEquals(a.fingerprint(), b.fingerprint());
        assertNotEquals(a.fingerprint(), c.fingerprint());
    }
}
