package com.geekersjoel237.koracore.web.api.payment.reverse;

import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
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

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public interface ReverseApi {

    @Operation(summary = "Reverse a payment — undo an authorized or captured transaction")
    @ApiResponse(responseCode = "200", description = "Reversal completed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping("/{txId}/reverse")
    ResponseEntity<TransactionResponse> reverse(
            @PathVariable("txId") String txId,
            @RequestBody @Valid ReverseRequest request);
}