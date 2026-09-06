package com.geekersjoel237.koracore.payment.adapters.out.query;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.ports.out.query.AccountQueryPort;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Component
public class JdbcAccountQueryAdapter implements AccountQueryPort {

    private static final String BALANCE = """
            SELECT id, account_number, balance_amount, balance_currency
            FROM accounts
            WHERE resource_type = 'CUSTOMER_ACCOUNT'
              AND resource_id = :customerId
              AND deleted_at IS NULL
            """;

    private static final String WALLET_ID = """
            SELECT id
            FROM accounts
            WHERE resource_type = 'CUSTOMER_ACCOUNT'
              AND resource_id = :customerId
              AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccountQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BalanceResult> findBalance(Id customerId) {
        List<BalanceResult> found = jdbc.query(BALANCE,
                Map.of("customerId", customerId.value()),
                (rs, row) -> new BalanceResult(
                        rs.getString("id"),
                        rs.getString("account_number"),
                        new Amount(rs.getBigDecimal("balance_amount"),
                                rs.getString("balance_currency"))));

        return found.stream().findFirst();
    }

    @Override
    public Optional<Id> findWalletId(Id customerId) {
        List<Id> found = jdbc.query(WALLET_ID,
                Map.of("customerId", customerId.value()),
                (rs, row) -> new Id(rs.getString("id")));

        return found.stream().findFirst();
    }
}
