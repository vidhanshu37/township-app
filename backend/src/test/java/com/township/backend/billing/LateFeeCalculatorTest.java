package com.township.backend.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LateFeeCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private static final BigDecimal RATE = new BigDecimal("0.18");

    private BigDecimal fee(String outstanding, int daysLate) {
        return LateFeeCalculator.calculate(new BigDecimal(outstanding),
                TODAY.minusDays(daysLate), TODAY, RATE);
    }

    @Test
    void briefSampleSwati47Days() {
        assertEquals(new BigDecimal("147.64"), fee("6370", 47));
    }

    @Test
    void partialPaymentChargesOnlyOutstanding() {
        assertEquals(new BigDecimal("50.24"), fee("5660", 18));
    }

    @Test
    void oneDayLate() {
        assertEquals(new BigDecimal("3.69"), fee("7475", 1));
    }

    @Test
    void noFeeBeforeOrOnDueDate() {
        assertEquals(new BigDecimal("0.00"), fee("7475", 0));
        assertEquals(new BigDecimal("0.00"), fee("7475", -5));
    }

    @Test
    void noFeeWhenFullyPaidOrOverpaid() {
        assertEquals(new BigDecimal("0.00"), fee("0", 30));
        assertEquals(new BigDecimal("0.00"), fee("-500", 30));
    }
}
