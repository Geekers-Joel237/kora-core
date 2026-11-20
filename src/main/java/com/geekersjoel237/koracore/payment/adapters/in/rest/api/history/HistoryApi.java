package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.BEARER_AUTH;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.JSON;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.PROBLEM_JSON;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.PROBLEM_REF;
import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.SECURITY_ERROR_REF;

@Tag(name = "Payments")
@RequestMapping("/payments")
@SecurityRequirement(name = BEARER_AUTH)
public interface HistoryApi {

    @Operation(summary = "Get the customer's transaction history, most recent first")
    @ApiResponse(responseCode = "200", description = "One page; nextCursor is null on the last page",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = TransactionHistoryResponse.class)))
    @ApiResponse(responseCode = "400", description = "An unknown status or kind, a date that is not YYYY-MM-DD, from after to, a limit out of 1..100, or an unreadable cursor",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(mediaType = JSON, schema = @Schema(ref = SECURITY_ERROR_REF)))
    @ApiResponse(responseCode = "403", description = "The token's role does not grant customer payments",
            content = @Content(mediaType = JSON, schema = @Schema(ref = SECURITY_ERROR_REF)))
    @ApiResponse(responseCode = "404", description = "The caller's wallet does not exist",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @GetMapping("/history")
    ResponseEntity<TransactionHistoryResponse> getHistory(
            @RequestAttribute("customerId") String customerId,
            @Parameter(description = "Customer status to keep", schema = @Schema(implementation = CustomerStatus.class))
            @RequestParam(required = false) String status,
            @Parameter(description = "Kind of operation to keep", schema = @Schema(implementation = TransactionKind.class))
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
