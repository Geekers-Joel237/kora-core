package com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.BEARER_AUTH;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.JSON;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.PROBLEM_JSON;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.PROBLEM_REF;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = BEARER_AUTH)
public interface CashInApi {

    @Operation(summary = "Cash in to customer wallet")
    @ApiResponse(responseCode = "200", description = "Cash-in recorded; state UNKNOWN when the provider outcome is not known",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Request validation failed, or an unknown payment method",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token (UNAUTHENTICATED), or wrong PIN (INVALID_PIN)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "403", description = "The token's role does not grant customer payments",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "404", description = "The caller's wallet does not exist",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "409", description = "A command with this correlation id was already received",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "422", description = "The wallet is blocked or suspended, or the currency does not match",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "502", description = "The provider refused the operation",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "503", description = "Concurrent updates exhausted the retries; nothing was refused",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @PostMapping("/cash-in")
    ResponseEntity<TransactionResponse> cashIn(
            @RequestAttribute("customerId") String customerId,
            @RequestBody @Valid CashInRequest request,
            @RequestHeader(name = CorrelationId.HEADER, required = false)
            String correlationId);
}
