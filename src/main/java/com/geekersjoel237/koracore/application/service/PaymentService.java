package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.domain.exception.TransientPaymentException;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Orchestrates payment use cases with optimistic-lock retry.
 * <p>
 * This class is intentionally <em>non-transactional</em> so that the
 * {@link #withOptimisticRetry} loop can restart a fresh transaction on each
 * attempt. The actual transactional work is delegated to
 * {@link PaymentTransactionalExecutor}, which is a Spring-proxied bean with
 * {@code @Transactional}.
 */
@Service
public class PaymentService implements PaymentUseCase {

    public static final int MAX_RETRY_ATTEMPTS = 3;
    private final PaymentTransactionalExecutor executor;
    private final AccountRepository accountRepository;

    public PaymentService(PaymentTransactionalExecutor executor,
                          AccountRepository accountRepository) {
        this.executor = executor;
        this.accountRepository = accountRepository;
    }

    @Override
    public Transaction cashIn(CashInCommand cmd) {
        return withOptimisticRetry(() -> executor.executeCashIn(cmd));
    }

    @Override
    public Transaction cashOut(CashOutCommand cmd) {
        return withOptimisticRetry(() -> executor.executeCashOut(cmd));
    }

    @Override
    public Transaction transfer(TransferCommand cmd) {
        return withOptimisticRetry(() -> executor.executeTransfer(cmd));
    }

    @Override
    @Transactional(readOnly = true)
    public Account getBalance(Id customerId) {
        return accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + customerId.value()));
    }

    private <T> T withOptimisticRetry(Supplier<T> action) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY_ATTEMPTS)
                    throw new TransientPaymentException("Concurrent update failed after 3 attempts", e);
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TransientPaymentException("Interrupted during retry", ie);
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }
}