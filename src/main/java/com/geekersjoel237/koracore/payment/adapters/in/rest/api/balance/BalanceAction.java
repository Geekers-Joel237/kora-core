package com.geekersjoel237.koracore.payment.adapters.in.rest.api.balance;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceQuery;

@RestController
public class BalanceAction implements BalanceApi {

    private final BalanceQueryHandler getBalance;

    public BalanceAction(BalanceQueryHandler getBalance) {
        this.getBalance = getBalance;
    }

    @Override
    public ResponseEntity<BalanceResponse> getBalance(String customerId) {
        var balance = getBalance.execute(new BalanceQuery(new Id(customerId)));
        return ResponseEntity.ok(BalanceResponse.from(balance));
    }
}