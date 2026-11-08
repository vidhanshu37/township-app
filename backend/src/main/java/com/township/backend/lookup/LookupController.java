package com.township.backend.lookup;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LookupController {

    private final LookupService lookup;

    public LookupController(LookupService lookup) {
        this.lookup = lookup;
    }

    @GetMapping("/api/helpdesk/flats")
    public List<LookupService.FlatOption> flats() {
        return lookup.flats();
    }

    @GetMapping("/api/helpdesk/payments")
    public List<LookupService.PaymentOption> payments(@RequestParam(defaultValue = "50") int limit) {
        return lookup.recentPayments(limit);
    }
}