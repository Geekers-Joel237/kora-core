package com.geekersjoel237.koracore.web.api.payment.capture;

import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public interface CaptureApi {

    @Operation(summary = "Capture an authorized payment — debit funds and write ledger entries")
    @ApiResponse(responseCode = "200", description = "Capture completed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping("/{txId}/capture")
    ResponseEntity<TransactionResponse> capture(
            @PathVariable("txId") String txId,
            @RequestAttribute("customerId") String customerId,
            @RequestBody CaptureRequest request);
}