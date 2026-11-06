package com.township.backend.matching;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the matcher knows about a flat at upload time.
 * openBills = outstanding amount of each unpaid bill, oldest first.
 * windowStart = period start of the oldest unpaid bill (null if nothing is due).
 */
public record FlatSnapshot(long flatId, String flatNo, String payRef, String residentName,
                           List<BigDecimal> openBills, LocalDate windowStart) {}