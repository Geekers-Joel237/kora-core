package com.geekersjoel237.koracore.shared.adapters.out.transaction;

import com.geekersjoel237.koracore.shared.application.transaction.ConcurrentUpdateException;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;


@Component
public class SpringTransactionBoundary implements TransactionBoundary {

    private final TransactionTemplate template;

    public SpringTransactionBoundary(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }


    @Override
    public <T> T execute(Supplier<T> work) {
        try {
            return template.execute(status -> work.get());
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(e);
        }
    }
}
