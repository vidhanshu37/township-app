package com.township.backend.reconciliation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BankTxnController {

    public record Confirm(@NotNull Long flatId, @Size(max = 200) String note) {}
    public record Reject(@NotBlank @Size(max = 200) String reason) {}

    private final ReviewService review;

    public BankTxnController(ReviewService review) {
        this.review = review;
    }

    @GetMapping("/api/helpdesk/bank-txns")
    public List<ReviewService.TxnView> list(@RequestParam(defaultValue = "NEEDS_REVIEW") String status,
                                            @RequestParam(defaultValue = "50") int limit,
                                            @RequestParam(defaultValue = "0") int offset) {
        return review.list(status, limit, offset);
    }

    @PostMapping("/api/helpdesk/bank-txns/{id}/confirm")
    public ReviewService.TxnView confirm(@PathVariable long id, @Valid @RequestBody Confirm body,
                                         @AuthenticationPrincipal Jwt jwt) {
        return review.confirm(id, body.flatId(), body.note(), Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/api/helpdesk/bank-txns/{id}/reject")
    public ReviewService.TxnView reject(@PathVariable long id, @Valid @RequestBody Reject body,
                                        @AuthenticationPrincipal Jwt jwt) {
        return review.reject(id, body.reason(), Long.parseLong(jwt.getSubject()));
    }
}