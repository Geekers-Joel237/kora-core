package com.geekersjoel237.koracore.infrastructure.bootstrap;

import com.geekersjoel237.koracore.domain.SystemConstants;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.LedgerEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaLedgerRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    private final JpaLedgerRepository jpaLedgerRepository;
    private final AccountRepository accountRepository;

    public DataInitializer(JpaLedgerRepository jpaLedgerRepository,
                           AccountRepository accountRepository) {
        this.jpaLedgerRepository = jpaLedgerRepository;
        this.accountRepository = accountRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        ensureLedgerExists();
        ensureFloatAccountExists();
    }

    private void ensureLedgerExists() {
        if (jpaLedgerRepository.findFirstBy().isEmpty()) {
            LedgerEntity entity = new LedgerEntity();
            entity.setId(Id.generate().value());
            jpaLedgerRepository.save(entity);
        }
    }

    private void ensureFloatAccountExists() {
        if (accountRepository.findFloatByProviderId(SystemConstants.PROVIDER_ID).isEmpty()) {
            accountRepository.save(
                    Account.createFloatAccount(Id.generate(), SystemConstants.PROVIDER_ID));
        }
    }
}