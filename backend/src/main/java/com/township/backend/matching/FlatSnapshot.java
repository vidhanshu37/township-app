package com.township.backend.matching;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FlatSnapshot(long flatId, String flatNo, String payRef, String residentName,
                           List<BigDecimal> openBills, LocalDate windowStart, List<String> upiIds) {

    public FlatSnapshot(long flatId, String flatNo, String payRef, String residentName,
                        List<BigDecimal> openBills, LocalDate windowStart) {
        this(flatId, flatNo, payRef, residentName, openBills, windowStart, List.of());
    }
}