package com.geekersjoel237.koracore.web.api.payment.saga;

import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public interface PaymentApi {

    @Operation(summary = "Execute payment — authorize + capture in one call, returns SETTLEMENT_PENDING")
    @ApiResponse(responseCode = "200", description = "Payment reached SETTLEMENT_PENDING")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden — wrong role")
    @ApiResponse(responseCode = "422", description = "Insufficient funds or invalid state")
    @PostMapping
    ResponseEntity<TransactionResponse> pay(
            @RequestAttribute("customerId") String customerId,
            @RequestBody PaymentRequest request);
}