package com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public interface CashInApi {

    @Operation(summary = "Cash in to customer wallet")
    @ApiResponse(responseCode = "200", description = "Cash-in completed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping("/cash-in")
    ResponseEntity<TransactionResponse> cashIn(
            @RequestAttribute("customerId") String customerId,
            @RequestBody @Valid CashInRequest request,
            @RequestHeader(name = CorrelationId.HEADER, required = false)
            String correlationId);
}