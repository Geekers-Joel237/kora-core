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
public interface LoginApi {

    @Operation(summary = "Login and receive OTP for 2-FA")
    @ApiResponse(responseCode = "200", description = "PIN valid, OTP sent")
    @ApiResponse(responseCode = "401", description = "Invalid PIN or customer not found")
    @PostMapping("/login")
    ResponseEntity<OtpResponse> login(@RequestBody LoginRequest request);
}