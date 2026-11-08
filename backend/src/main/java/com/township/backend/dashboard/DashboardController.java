package com.township.backend.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@RestController
public class DashboardController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/committee/dashboard")
    public DashboardService.Dashboard get(@RequestParam(required = false) String period) {
        LocalDate today = LocalDate.now(IST);
        YearMonth month;
        try {
            month = period == null ? YearMonth.from(today) : YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must look like 2026-11");
        }
        return dashboard.build(month, today);
    }
}