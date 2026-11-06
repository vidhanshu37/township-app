package com.township.backend.billing;

import com.township.backend.billing.PaymentAllocator.Line;
import com.township.backend.billing.PaymentAllocator.OpenBill;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentAllocatorTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }

    @Test
    void partialPaymentStaysOnOldestBill() {
        List<Line> lines = PaymentAllocator.allocate(
                List.of(new OpenBill(1, bd("6517.64")), new OpenBill(2, bd("6370.00"))), bd("5000.00"));
        assertEquals(1, lines.size());
        assertEquals(1L, lines.get(0).billId());
        assertAmount("5000.00", lines.get(0).amount());
    }

    @Test
    void oneTransactionCoversTwoBills() {
        List<Line> lines = PaymentAllocator.allocate(
                List.of(new OpenBill(1, bd("6517.64")), new OpenBill(2, bd("6370.00"))), bd("12887.64"));
        assertEquals(2, lines.size());
        assertAmount("6517.64", lines.get(0).amount());
        assertAmount("6370.00", lines.get(1).amount());
    }

    @Test
    void excessBecomesAdvanceCredit() {
        List<Line> lines = PaymentAllocator.allocate(List.of(new OpenBill(1, bd("100.00"))), bd("150.00"));
        assertEquals(2, lines.size());
        assertAmount("100.00", lines.get(0).amount());
        assertNull(lines.get(1).billId());
        assertAmount("50.00", lines.get(1).amount());
    }

    @Test
    void noOpenBillsMeansEverythingIsAdvance() {
        List<Line> lines = PaymentAllocator.allocate(List.of(), bd("1000.00"));
        assertEquals(1, lines.size());
        assertNull(lines.get(0).billId());
    }
}