package com.township.backend.complaints;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ComplaintController {

    public record CreateComplaint(@NotBlank @Size(min = 10, max = 1000) String text) {
    }

    public record ChangeStatus(@NotBlank String status, @Size(max = 200) String note) {
    }

    public record ChangeCategory(@NotBlank String category) {
    }

    public record Rate(@Min(1) @Max(5) int rating, @Size(max = 300) String comment) {
    }

    private final ComplaintService complaints;

    public ComplaintController(ComplaintService complaints) {
        this.complaints = complaints;
    }

    @PostMapping("/api/resident/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintService.ComplaintView create(@Valid @RequestBody CreateComplaint body,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return complaints.create(flatId(jwt), Long.parseLong(jwt.getSubject()), body.text());
    }

    @GetMapping("/api/resident/complaints")
    public List<ComplaintService.ComplaintView> mine(@AuthenticationPrincipal Jwt jwt) {
        return complaints.listForFlat(flatId(jwt));
    }

    @GetMapping("/api/resident/complaints/{id}")
    public ComplaintService.Detail mineDetail(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return complaints.detailForFlat(id, flatId(jwt));
    }

    @PostMapping("/api/resident/complaints/{id}/rating")
    public ComplaintService.ComplaintView rate(@PathVariable long id, @Valid @RequestBody Rate body,
                                               @AuthenticationPrincipal Jwt jwt) {
        return complaints.rate(id, flatId(jwt), Long.parseLong(jwt.getSubject()), body.rating(), body.comment());
    }

    @GetMapping("/api/helpdesk/complaints")
    public List<ComplaintService.ComplaintView> queue(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String priority,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(defaultValue = "50") int limit,
                                                      @RequestParam(defaultValue = "0") int offset) {
        return complaints.listAll(status, priority, category, limit, offset);
    }

    @GetMapping("/api/helpdesk/complaints/{id}")
    public ComplaintService.Detail detail(@PathVariable long id) {
        return complaints.detail(id);
    }

    @PostMapping("/api/helpdesk/complaints/{id}/status")
    public ComplaintService.ComplaintView changeStatus(@PathVariable long id, @Valid @RequestBody ChangeStatus body,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return complaints.changeStatus(id, body.status(), body.note(), Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/api/helpdesk/complaints/{id}/category")
    public ComplaintService.ComplaintView changeCategory(@PathVariable long id,
                                                         @Valid @RequestBody ChangeCategory body,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return complaints.changeCategory(id, body.category(), Long.parseLong(jwt.getSubject()));
    }

    private long flatId(Jwt jwt) {
        return ((Number) jwt.getClaim("flat_id")).longValue();
    }
}