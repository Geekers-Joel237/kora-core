package com.geekersjoel237.koracore.payment.adapters.out.query;

import com.geekersjoel237.koracore.payment.application.query.history.HistoryCriteria;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.payment.ports.out.query.ReadTransactionRepository;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Component
public class JdbcReadTransactionQueryAdapter implements ReadTransactionRepository {

    private static final String SELECT_PAGE = """
            SELECT t.id, t.transaction_number, t.type, t.state, t.payment_method,
                   t.amount, t.currency, t.from_account_id, t.to_account_id, t.occurred_at,
                   counterpart.phone_prefix AS counterpart_prefix,
                   counterpart.phone_number AS counterpart_number
            FROM transactions t
            LEFT JOIN accounts ca
                   ON ca.id = CASE WHEN t.from_account_id = :walletId
                                   THEN t.to_account_id ELSE t.from_account_id END
                  AND ca.resource_type = 'CUSTOMER_ACCOUNT'
                  AND ca.deleted_at IS NULL
            LEFT JOIN customers counterpart
                   ON counterpart.id = ca.resource_id
                  AND counterpart.deleted_at IS NULL
            WHERE %s
            ORDER BY t.occurred_at DESC, t.id DESC
            LIMIT :fetchSize
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReadTransactionQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<TransactionRow> findPage(Id walletId, HistoryCriteria criteria, Pagination pagination) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("walletId", walletId.value())
                .addValue("fetchSize", pagination.fetchSize());

        List<TransactionRow> fetched = jdbc.query(
                SELECT_PAGE.formatted(whereClause(criteria, params)), params,
                (rs, rowNum) -> toRow(rs));

        return PageResult.fromFetched(fetched, pagination);
    }

    private static String whereClause(HistoryCriteria criteria, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        conditions.add(walletSide(criteria.kind()));
        conditions.add("t.deleted_at IS NULL");

        if (!criteria.states().isEmpty()) {
            conditions.add("t.state IN (:states)");
            params.addValue("states", criteria.states().stream().map(TransactionState::name).toList());
        }
        if (criteria.from() != null) {
            conditions.add("t.occurred_at >= :from");
            params.addValue("from", Timestamp.from(criteria.from()));
        }
        if (criteria.until() != null) {
            conditions.add("t.occurred_at < :until");
            params.addValue("until", Timestamp.from(criteria.until()));
        }
        if (criteria.after() != null) {
            conditions.add("(t.occurred_at, t.id) < (:afterAt, :afterId)");
            params.addValue("afterAt", Timestamp.from(criteria.after().occurredAt()));
            params.addValue("afterId", criteria.after().transactionId().value());
        }
        return String.join(" AND ", conditions);
    }

    private static String walletSide(TransactionKind kind) {
        if (kind == null) return "(t.from_account_id = :walletId OR t.to_account_id = :walletId)";
        return switch (kind) {
            case DEPOSIT -> "t.type = 'CASH_IN' AND t.to_account_id = :walletId";
            case WITHDRAWAL -> "t.type = 'CASH_OUT' AND t.from_account_id = :walletId";
            case TRANSFER_SENT -> "t.type = 'P2P_TRANSFER' AND t.from_account_id = :walletId";
            case TRANSFER_RECEIVED -> "t.type = 'P2P_TRANSFER' AND t.to_account_id = :walletId";
        };
    }

    private static TransactionRow toRow(ResultSet rs) throws SQLException {
        return new TransactionRow(
                rs.getString("id"),
                rs.getString("transaction_number"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getString("state"),
                new Amount(rs.getBigDecimal("amount"), rs.getString("currency")),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getString("from_account_id"),
                rs.getString("to_account_id"),
                rs.getString("counterpart_prefix"),
                rs.getString("counterpart_number"),
                rs.getTimestamp("occurred_at").toInstant());
    }
}
