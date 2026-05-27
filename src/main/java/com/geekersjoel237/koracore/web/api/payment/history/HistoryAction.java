package com.geekersjoel237.koracore.web.api.payment.history;

import com.geekersjoel237.koracore.application.port.in.TransactionHistoryUseCase;
import com.geekersjoel237.koracore.application.query.TransactionSummary;
import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.query.PageResult;
import com.geekersjoel237.koracore.domain.query.TransactionFilter;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@Validated
public class HistoryAction implements HistoryApi {

    private final TransactionHistoryUseCase historyUseCase;

    public HistoryAction(TransactionHistoryUseCase historyUseCase) {
        this.historyUseCase = historyUseCase;
    }

    @Override
    public ResponseEntity<TransactionHistoryResponse> getHistory(
            String customerId,
            String type,
            String state,
            String from,
            String to,
            String direction,
            int page,
            int size,
            boolean detail
    ) {
        TransactionFilter filter = new TransactionFilter(
                type      != null ? TransactionType.valueOf(type)   : null,
                state,
                from      != null ? parseInstant(from, "from")      : null,
                to        != null ? parseInstant(to,   "to")        : null,
                direction != null ? Direction.valueOf(direction)     : null
        );

        PageResult<TransactionSummary> result =
                historyUseCase.execute(new Id(customerId), filter, page, size);

        return ResponseEntity.ok(TransactionHistoryResponse.from(result, detail));
    }

    private Instant parseInstant(String value, String paramName) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date format for '" + paramName + "': expected ISO-8601 UTC " +
                    "(e.g. 2026-01-15T00:00:00Z), got: " + value);
        }
    }
}