package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistoryAction implements HistoryApi {

    private final TransactionHistoryQueryHandler history;

    public HistoryAction(TransactionHistoryQueryHandler history) {
        this.history = history;
    }

    @Override
    public ResponseEntity<TransactionHistoryResponse> getHistory(String customerId, String status, String kind,
                                                                 String from, String to, String cursor,
                                                                 Integer limit) {
        HistoryRequest request = new HistoryRequest(status, kind, from, to, cursor, limit);
        return ResponseEntity.ok(TransactionHistoryResponse.from(history.execute(request.toQuery(new Id(customerId)))));
    }
}
