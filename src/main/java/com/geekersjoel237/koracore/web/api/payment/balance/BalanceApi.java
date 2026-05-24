package com.geekersjoel237.koracore.web.api.payment.balance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public interface BalanceApi {

    @Operation(summary = "Get current wallet balance")
    @ApiResponse(responseCode = "200", description = "Balance returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/balance")
    ResponseEntity<BalanceResponse> getBalance(
            @RequestAttribute("customerId") String customerId);
}