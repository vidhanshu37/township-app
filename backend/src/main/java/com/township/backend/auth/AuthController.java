package com.township.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RequestOtp(@NotNull @Pattern(regexp = "\\d{10}") String phone) {}
    public record VerifyOtp(@NotNull @Pattern(regexp = "\\d{10}") String phone,
                            @NotNull @Pattern(regexp = "\\d{6}") String otp) {}
    public record RequestOtpResponse(String message, String demoOtp) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/request-otp")
    public RequestOtpResponse requestOtp(@Valid @RequestBody RequestOtp req) {
        String demoOtp = auth.requestOtp(req.phone()).orElse(null);
        return new RequestOtpResponse("If the number is registered, an OTP has been sent", demoOtp);
    }


    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtp req) {
        return auth.verify(req.phone(), req.otp())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid or expired OTP")));
    }}
