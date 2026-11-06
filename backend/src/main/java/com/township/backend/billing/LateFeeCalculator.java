package com.township.backend.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class LateFeeCalculator {

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

    private LateFeeCalculator() {
    }

    public static BigDecimal calculate(BigDecimal outstanding, LocalDate dueDate,
                                       LocalDate asOf, BigDecimal annualRate) {
        long daysLate = ChronoUnit.DAYS.between(dueDate, asOf);
        if (outstanding.signum() <= 0 || daysLate <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return outstanding
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(daysLate))
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

}
