package com.geekersjoel237.koracore.payment.adapters.in.rest.perf;

import com.geekersjoel237.koracore.payment.domain.SystemConstants;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@Profile("perf")
@RestController
@RequestMapping("/test")
public class ResetTestSupportAction {

    private final JdbcTemplate owner;
    private final AccountRepository accountRepository;

    public ResetTestSupportAction(@Value("${spring.datasource.url}") String url,
                                  @Value("${spring.flyway.user}") String user,
                                  @Value("${spring.flyway.password}") String password,
                                  AccountRepository accountRepository) {
        this.owner = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        this.accountRepository = accountRepository;
    }

    /**
     * The ledger row survives: it is a singleton created once at startup.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        owner.execute(
                "TRUNCATE TABLE authorization_records, ledger_entries, trx_state_historics, "
                        + "transactions, accounts, customers, users RESTART IDENTITY CASCADE");

        accountRepository.save(Account.createFloatAccount(Id.generate(), SystemConstants.PROVIDER_ID));

        return ResponseEntity.ok(Map.of(
                "status", "reset",
                "message", "All test data cleared. Float account recreated."));
    }
}
