package com.township.backend.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
public class BillingController {
    public record GenerateRequest(
            @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String period) {}

    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    @PostMapping("/api/helpdesk/bills/generate")
    public Map<String, Object> generate(@Valid @RequestBody GenerateRequest req,
                                        @AuthenticationPrincipal Jwt jwt) {
        int created = billing.generate(YearMonth.parse(req.period()),
                Long.parseLong(jwt.getSubject()));
        return Map.of("period", req.period(), "billsCreated", created);
    }

    @GetMapping("/api/resident/dues")
    public BillingService.Dues dues(@AuthenticationPrincipal Jwt jwt) {
        return billing.dues(flatId(jwt));
    }

    @GetMapping("/api/resident/ledger")
    public List<BillingService.LedgerRow> ledger(@AuthenticationPrincipal Jwt jwt) {
        return billing.ledger(flatId(jwt));
    }

    @PostMapping("/api/helpdesk/late-fees/apply")
    public Map<String, Object> applyLateFees(@AuthenticationPrincipal Jwt jwt) {
        int posted = billing.applyLateFees(Long.parseLong(jwt.getSubject()));
        return Map.of("lateFeesPosted", posted);
    }

    private long flatId(Jwt jwt) {
        return ((Number) jwt.getClaim("flat_id")).longValue();
    }
}
