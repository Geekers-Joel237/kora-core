package com.geekersjoel237.koracore.auth.adapters.in.rest.api.register;

import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.OtpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Auth")
@RequestMapping("/auth")
public interface RegisterApi {

    @Operation(summary = "Register a new customer")
    @ApiResponse(responseCode = "201", description = "Customer registered, OTP sent")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    @PostMapping("/register")
    ResponseEntity<OtpResponse> register(@RequestBody @Valid RegisterRequest request,
            @RequestHeader(name = CorrelationId.HEADER, required = false)
            String correlationId);
}