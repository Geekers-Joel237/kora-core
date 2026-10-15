package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.application.query.history.HistoryFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

public record HistoryRequest(String status, String kind, String from, String to, String cursor, Integer limit) {

    public TransactionHistoryQuery toQuery(Id customerId) {
        HistoryFilter filter = new HistoryFilter(
                enumOf(CustomerStatus.class, "status", status),
                enumOf(TransactionKind.class, "kind", kind),
                dayOf("from", from),
                dayOf("to", to));
        return new TransactionHistoryQuery(customerId, filter,
                cursor == null ? null : HistoryCursorToken.decode(cursor),
                Pagination.of(limit));
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + name + " " + value + ", expected one of "
                    + Arrays.toString(type.getEnumConstants()));
        }
    }

    private static LocalDate dayOf(String name, String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date for " + name + ": expected YYYY-MM-DD, got: " + value);
        }
    }
}
