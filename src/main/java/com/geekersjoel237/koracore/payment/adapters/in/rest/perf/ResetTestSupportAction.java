package com.geekersjoel237.koracore.payment.adapters.in.rest.perf;

import com.geekersjoel237.koracore.payment.domain.SystemConstants;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@Profile("perf")
@RestController
@RequestMapping("/test")
public class ResetTestSupportAction {

    private final JdbcTemplate jdbcTemplate;
    private final AccountRepository accounts;

    public ResetTestSupportAction(JdbcTemplate jdbcTemplate, AccountRepository accounts) {
        this.jdbcTemplate = jdbcTemplate;
        this.accounts = accounts;
    }

    /**
     * The ledger row survives: it is a singleton created once at startup.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE authorization_records, ledger_entries, trx_state_historics, "
                        + "transactions, accounts, customers, users RESTART IDENTITY CASCADE");

        accounts.save(Account.createFloatAccount(Id.generate(), SystemConstants.PROVIDER_ID));

        return ResponseEntity.ok(Map.of(
                "status", "reset",
                "message", "All test data cleared. Float account recreated."));
    }
}
