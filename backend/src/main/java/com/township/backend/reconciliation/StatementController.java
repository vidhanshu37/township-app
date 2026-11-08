package com.township.backend.reconciliation;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
public class StatementController {

    private final ReconciliationService service;

    public StatementController(ReconciliationService service) {
        this.service = service;
    }

    @PostMapping("/api/helpdesk/statements")
    public ReconciliationService.Summary upload(@RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal Jwt jwt) {
        try {
            String name = file.getOriginalFilename() == null ? "statement.csv" : file.getOriginalFilename();
            return service.upload(name, file.getBytes(), Long.parseLong(jwt.getSubject()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
    }
}