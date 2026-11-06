package com.township.backend.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class PaymentAllocator {

    public record OpenBill(long billId, BigDecimal outstanding) {}
    public record Line(Long billId, BigDecimal amount) {}

    private PaymentAllocator() {}

    public static List<Line> allocate(List<OpenBill> oldestFirst, BigDecimal amount) {
        List<Line> lines = new ArrayList<>();
        BigDecimal left = amount;
        for (OpenBill bill : oldestFirst) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal pay = left.min(bill.outstanding());
            lines.add(new Line(bill.billId(), pay));
            left = left.subtract(pay);
        }
        if (left.signum() > 0) {
            lines.add(new Line(null, left));
        }
        return lines;
    }
}