package com.geekersjoel237.koracore.auth.adapters.in.rest.api.refreshToken;

import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.TokensResponse;
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
public interface RefreshApi {

    @Operation(summary = "Refresh access token")
    @ApiResponse(responseCode = "200", description = "New token pair issued")
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    @PostMapping("/refresh")
    ResponseEntity<TokensResponse> refresh(@RequestBody @Valid RefreshRequest request,
            @RequestHeader(name = CorrelationId.HEADER, required = false)
            String correlationId);
}