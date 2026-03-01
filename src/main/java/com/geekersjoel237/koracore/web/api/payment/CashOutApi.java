package com.geekersjoel237.koracore.web.api.payment;

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
public interface CashOutApi {

    @Operation(summary = "Cash out from customer wallet")
    @ApiResponse(responseCode = "200", description = "Cash-out completed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping("/cash-out")
    ResponseEntity<TransactionResponse> cashOut(
            @RequestAttribute("customerId") String customerId,
            @RequestBody CashOutRequest request);
}