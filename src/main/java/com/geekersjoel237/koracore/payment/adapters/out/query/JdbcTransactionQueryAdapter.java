package com.geekersjoel237.koracore.payment.adapters.out.query;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.domain.enums.Direction;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.ports.out.query.TransactionQueryPort;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Component
public class JdbcTransactionQueryAdapter implements TransactionQueryPort {

    private static final String SELECT_PAGE = """
            SELECT t.id, t.transaction_number, t.type, t.state, t.payment_method,
                   t.amount, t.currency, t.from_account_id, t.to_account_id, t.occurred_at,
                   counterpart.phone_prefix AS counterpart_prefix,
                   counterpart.phone_number AS counterpart_number,
                   h.old_state, h.new_state, h.occurred_at AS history_occurred_at
            FROM (
                SELECT * FROM transactions t
                WHERE %s
                ORDER BY t.occurred_at DESC, t.id DESC
                LIMIT :limit OFFSET :offset
            ) t
            LEFT JOIN accounts ca
                   ON ca.id = CASE WHEN t.from_account_id = :walletId
                                   THEN t.to_account_id ELSE t.from_account_id END
                  AND ca.resource_type = 'CUSTOMER_ACCOUNT'
                  AND ca.deleted_at IS NULL
            LEFT JOIN customers counterpart
                   ON counterpart.id = ca.resource_id
                  AND counterpart.deleted_at IS NULL
            LEFT JOIN trx_state_historics h
                   ON h.transaction_id = t.id
                  AND h.deleted_at IS NULL
            ORDER BY t.occurred_at DESC, t.id DESC, h.occurred_at ASC
            """;

    private static final String COUNT_PAGE = """
            SELECT COUNT(*) FROM transactions t WHERE %s
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTransactionQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<TransactionRow> findPage(Id walletId, TransactionFilter filter,
                                               Pagination pagination) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("walletId", walletId.value())
                .addValue("limit", pagination.size())
                .addValue("offset", pagination.offset());

        String where = whereClause(filter, params);

        Integer total = jdbc.queryForObject(COUNT_PAGE.formatted(where), params, Integer.class);
        List<TransactionRow> rows = jdbc.query(
                SELECT_PAGE.formatted(where), params, JdbcTransactionQueryAdapter::foldRows);

        return PageResult.of(rows, pagination, total == null ? 0 : total);
    }


    private static String whereClause(TransactionFilter filter, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();

        if (filter.direction() == Direction.OUTBOUND) {
            conditions.add("t.from_account_id = :walletId");
        } else if (filter.direction() == Direction.INBOUND) {
            conditions.add("t.to_account_id = :walletId");
        } else {
            conditions.add("(t.from_account_id = :walletId OR t.to_account_id = :walletId)");
        }
        conditions.add("t.deleted_at IS NULL");

        if (filter.type() != null) {
            conditions.add("t.type = :type");
            params.addValue("type", filter.type().name());
        }
        if (filter.state() != null) {
            conditions.add("t.state = :state");
            params.addValue("state", filter.state());
        }
        if (filter.from() != null) {
            conditions.add("t.occurred_at >= :from");
            params.addValue("from", java.sql.Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add("t.occurred_at <= :to");
            params.addValue("to", java.sql.Timestamp.from(filter.to()));
        }
        return String.join(" AND ", conditions);
    }

    private static List<TransactionRow> foldRows(ResultSet rs) throws SQLException {
        Map<String, List<TransactionSummary.StateEntry>> historyById = new LinkedHashMap<>();
        Map<String, Object[]> headerById = new LinkedHashMap<>();

        while (rs.next()) {
            String id = rs.getString("id");
            headerById.putIfAbsent(id, new Object[]{
                    rs.getString("transaction_number"),
                    TransactionType.valueOf(rs.getString("type")),
                    rs.getString("state"),
                    new Amount(rs.getBigDecimal("amount"), rs.getString("currency")),
                    PaymentMethod.valueOf(rs.getString("payment_method")),
                    rs.getString("from_account_id"),
                    rs.getString("to_account_id"),
                    rs.getString("counterpart_prefix"),
                    rs.getString("counterpart_number"),
                    rs.getTimestamp("occurred_at").toInstant()});

            List<TransactionSummary.StateEntry> history =
                    historyById.computeIfAbsent(id, key -> new ArrayList<>());
            String newState = rs.getString("new_state");
            if (newState != null)
                history.add(new TransactionSummary.StateEntry(
                        rs.getString("old_state"), newState,
                        rs.getTimestamp("history_occurred_at").toInstant()));
        }

        List<TransactionRow> rows = new ArrayList<>();
        headerById.forEach((id, header) -> rows.add(new TransactionRow(
                id, (String) header[0], (TransactionType) header[1], (String) header[2],
                (Amount) header[3], (PaymentMethod) header[4],
                (String) header[5], (String) header[6],
                (String) header[7], (String) header[8],
                (java.time.Instant) header[9],
                List.copyOf(historyById.get(id)))));
        return rows;
    }
}
