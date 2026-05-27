package com.geekersjoel237.koracore.infrastructure.bootstrap;

import com.geekersjoel237.koracore.domain.SystemConstants;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.LedgerEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataLedgerRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    private final SpringDataLedgerRepository springDataLedgerRepository;
    private final AccountRepository accountRepository;

    public DataInitializer(SpringDataLedgerRepository springDataLedgerRepository,
                           AccountRepository accountRepository) {
        this.springDataLedgerRepository = springDataLedgerRepository;
        this.accountRepository = accountRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        ensureLedgerExists();
        ensureFloatAccountExists();
    }

    private void ensureLedgerExists() {
        if (springDataLedgerRepository.findFirstBy().isEmpty()) {
            LedgerEntity entity = new LedgerEntity();
            entity.setId(Id.generate().value());
            springDataLedgerRepository.save(entity);
        }
    }

    private void ensureFloatAccountExists() {
        if (accountRepository.findFloatByProviderId(SystemConstants.PROVIDER_ID).isEmpty()) {
            accountRepository.save(
                    Account.createFloatAccount(Id.generate(), SystemConstants.PROVIDER_ID));
        }
    }
}