package com.township.backend.complaints;

import jakarta.validation.Valid;
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

    public record CreateComplaint(@NotBlank @Size(min = 10, max = 1000) String text) {}
    public record ChangeStatus(@NotBlank String status, @Size(max = 200) String note) {}

    private final ComplaintService complaints;
    private final TriageService triage;

    public ComplaintController(ComplaintService complaints, TriageService triage) {
        this.complaints = complaints;
        this.triage = triage;
    }

    @PostMapping("/api/resident/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintService.ComplaintView create(@Valid @RequestBody CreateComplaint body,
                                                 @AuthenticationPrincipal Jwt jwt) {
        TriageEngine.Result result = triage.classify(body.text().trim());
        return complaints.create(flatId(jwt), Long.parseLong(jwt.getSubject()), body.text(), result);
    }

    @GetMapping("/api/resident/complaints")
    public List<ComplaintService.ComplaintView> mine(@AuthenticationPrincipal Jwt jwt) {
        return complaints.listForFlat(flatId(jwt));
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

    private long flatId(Jwt jwt) {
        return ((Number) jwt.getClaim("flat_id")).longValue();
    }
}