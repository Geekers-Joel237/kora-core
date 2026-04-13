package com.geekersjoel237.koracore.web.api.payment.authorize;

import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
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
public interface AuthorizeApi {

    @Operation(summary = "Authorize a payment — reserve funds without debiting")
    @ApiResponse(responseCode = "200", description = "Authorization created")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping("/authorize")
    ResponseEntity<TransactionResponse> authorize(
            @RequestAttribute("customerId") String customerId,
            @RequestBody AuthorizeRequest request);
}