package com.geekersjoel237.koracore.web.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Auth")
@RequestMapping("/auth")
public interface VerifyOtpApi {

    @Operation(summary = "Verify OTP and get tokens")
    @ApiResponse(responseCode = "200", description = "OTP verified, tokens issued")
    @ApiResponse(responseCode = "401", description = "Invalid or expired OTP")
    @PostMapping("/verify-otp")
    ResponseEntity<TokensResponse> verifyOtp(@RequestBody VerifyOtpRequest request);
}