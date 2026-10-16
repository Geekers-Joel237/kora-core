package com.geekersjoel237.koracore.auth.adapters.in.rest.api.logout;

import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Auth")
@RequestMapping("/auth")
public interface LogoutApi {

    @Operation(summary = "Log out: end the session the refresh token belongs to")
    @ApiResponse(responseCode = "204", description = "Session ended, or already ended")
    @ApiResponse(responseCode = "401", description = "Not a refresh token issued by this server")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request,
            @RequestHeader(name = CorrelationId.HEADER, required = false)
            String correlationId);
}
