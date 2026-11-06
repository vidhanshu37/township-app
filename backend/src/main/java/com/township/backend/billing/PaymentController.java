package com.township.backend.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
public class PaymentController {

    public record CreateOrder(@DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}
    public record Webhook(@NotBlank String orderId, @NotBlank String paymentId, @NotBlank String signature) {}

    private final PaymentService payments;
    private final boolean demoMode;

    public PaymentController(PaymentService payments, @Value("${app.demo-mode:false}") boolean demoMode) {
        this.payments = payments;
        this.demoMode = demoMode;
    }

    @PostMapping("/api/resident/payments/orders")
    public PaymentService.Order createOrder(@Valid @RequestBody CreateOrder req,
                                            @AuthenticationPrincipal Jwt jwt) {
        return payments.createOrder(flatId(jwt), Long.parseLong(jwt.getSubject()), req.amount());
    }

    @PostMapping("/api/resident/payments/orders/{orderId}/simulate")
    public PaymentService.Result simulate(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        if (!demoMode) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        payments.assertOwnedBy(orderId, flatId(jwt));
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return payments.handlePaid(orderId, paymentId, payments.sign(orderId, paymentId));
    }

    @PostMapping("/api/payments/webhook")
    public PaymentService.Result webhook(@Valid @RequestBody Webhook body) {
        return payments.handlePaid(body.orderId(), body.paymentId(), body.signature());
    }

    private long flatId(Jwt jwt) {
        return ((Number) jwt.getClaim("flat_id")).longValue();
    }
}