package com.geekersjoel237.koracore.payment.adapters.in.rest.api.reverse;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Admin")
@RequestMapping("/admin/payments")
@SecurityRequirement(name = "bearerAuth")
public interface ReverseApi {

    @Operation(summary = "Reverse a payment — undo an authorized or captured transaction (admin only)")
    @ApiResponse(responseCode = "200", description = "Reversal completed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden — admin role required")
    @PostMapping("/{txId}/reverse")
    ResponseEntity<TransactionResponse> reverse(
            @PathVariable("txId") String txId,
            @RequestBody @Valid ReverseRequest request,
            @RequestHeader(name = CorrelationId.HEADER, required = false)
            String correlationId);
}