package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.domain.enums.Direction;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionFilter;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@Validated
public class HistoryAction implements HistoryApi {

    private final TransactionHistoryQueryHandler history;

    public HistoryAction(TransactionHistoryQueryHandler history) {
        this.history = history;
    }

    @Override
    public ResponseEntity<TransactionHistoryResponse> getHistory(
            String customerId,
            String type,
            String state,
            String from,
            String to,
            String direction,
            Integer page,
            Integer size,
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
                history.execute(new TransactionHistoryQuery(
                        new Id(customerId), filter, Pagination.of(page, size)));

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