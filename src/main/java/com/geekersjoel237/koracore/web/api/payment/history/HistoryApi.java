package com.geekersjoel237.koracore.web.api.payment.history;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public interface HistoryApi {

    @Operation(summary = "Get paginated transaction history")
    @ApiResponse(responseCode = "200", description = "History returned")
    @ApiResponse(responseCode = "400", description = "Invalid parameters")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/history")
    ResponseEntity<TransactionHistoryResponse> getHistory(
            @RequestAttribute("customerId") String customerId,
            @Parameter(description = "Filter by type: CASH_IN, CASH_OUT, P2P_TRANSFER")
            @RequestParam(required = false) String type,
            @Parameter(description = "Filter by state: INITIATED, COMPLETED, FAILED, …")
            @RequestParam(required = false) String state,
            @Parameter(description = "ISO-8601 UTC instant (e.g. 2026-01-15T00:00:00Z)")
            @RequestParam(required = false) String from,
            @Parameter(description = "ISO-8601 UTC instant (e.g. 2026-01-15T00:00:00Z)")
            @RequestParam(required = false) String to,
            @Parameter(description = "INBOUND or OUTBOUND")
            @RequestParam(required = false) String direction,
            @Parameter(description = "0-based page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, max 100")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Include stateHistory in each transaction")
            @RequestParam(defaultValue = "false") boolean detail
    );
}