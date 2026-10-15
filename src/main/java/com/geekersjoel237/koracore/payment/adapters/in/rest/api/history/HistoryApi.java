package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

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

    @Operation(summary = "Get the customer's transaction history, most recent first")
    @ApiResponse(responseCode = "200", description = "History returned")
    @ApiResponse(responseCode = "400", description = "Invalid parameters")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/history")
    ResponseEntity<TransactionHistoryResponse> getHistory(
            @RequestAttribute("customerId") String customerId,
            @Parameter(description = "PENDING, COMPLETED, FAILED or REFUNDED")
            @RequestParam(required = false) String status,
            @Parameter(description = "DEPOSIT, WITHDRAWAL, TRANSFER_SENT or TRANSFER_RECEIVED")
            @RequestParam(required = false) String kind,
            @Parameter(description = "First day included, YYYY-MM-DD")
            @RequestParam(required = false) String from,
            @Parameter(description = "Last day included, YYYY-MM-DD")
            @RequestParam(required = false) String to,
            @Parameter(description = "nextCursor of the previous page; absent for the first page")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Items per page; defaults to 20, max 100")
            @RequestParam(required = false) Integer limit
    );
}
