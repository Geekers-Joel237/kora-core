package com.geekersjoel237.koracore.payment.adapters.in.rest.api.balance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.BEARER_AUTH;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.JSON;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.PROBLEM_JSON;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.PROBLEM_REF;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = BEARER_AUTH)
public interface BalanceApi {

    @Operation(summary = "Get current wallet balance")
    @ApiResponse(responseCode = "200", description = "amount is the available balance; ledgerAmount the ledger balance",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = BalanceResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token (UNAUTHENTICATED)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "403", description = "The token's role does not grant customer payments",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "404", description = "The caller's wallet does not exist",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @GetMapping("/balance")
    ResponseEntity<BalanceResponse> getBalance(
            @RequestAttribute("customerId") String customerId);
}
