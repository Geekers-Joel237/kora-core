package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.domain.SystemConstants;
import com.geekersjoel237.koracore.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.domain.exception.ProviderException;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.Ledger;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.port.*;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes one payment attempt inside a single, self-contained transaction.
 * <p>
 * Kept separate from {@link PaymentService} so that the outer retry loop
 * in {@code PaymentService} can call this bean through a Spring proxy,
 * giving each retry attempt a <em>fresh</em> transaction (and a fresh
 * Hibernate session). Retrying inside the same transaction would re-use the
 * corrupted session state produced by the failed optimistic-lock attempt.
 */
@Service
@Transactional
public class PaymentTransactionalExecutor {

    private final AuthUseCase authUseCase;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository historicRepo;
    private final ProviderPort provider;
    private final LedgerRepository ledgerRepository;

    public PaymentTransactionalExecutor(AuthUseCase authUseCase,
                                        AccountRepository accountRepository,
                                        CustomerRepository customerRepository,
                                        TransactionRepository transactionRepository,
                                        TrxHistoricStatesRepository historicRepo,
                                        ProviderPort provider,
                                        LedgerRepository ledgerRepository) {
        this.authUseCase = authUseCase;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.historicRepo = historicRepo;
        this.provider = provider;
        this.ledgerRepository = ledgerRepository;
    }

    public Transaction executeCashIn(CashInCommand cmd) {
        var customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var floatAccount = getSystemFloatAccountForUpdate();
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.cashIn(customerAccount, floatAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.credit(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    customerAccount.credit(cmd.amount());
                    accountRepository.save(customerAccount);
                });
    }

    public Transaction executeCashOut(CashOutCommand cmd) {
        var customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var floatAccount = getSystemFloatAccountForUpdate();
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.cashOut(customerAccount, floatAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.debit(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    customerAccount.debit(cmd.amount());
                    accountRepository.save(customerAccount);
                });
    }

    public Transaction executeTransfer(TransferCommand cmd) {
        var fromAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var toAccount = validateRecipientAndGetAccount(cmd.toPhoneNumber());
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.transfer(fromAccount, toAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.send(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    fromAccount.debit(cmd.amount());
                    toAccount.credit(cmd.amount());
                    accountRepository.save(fromAccount);
                    accountRepository.save(toAccount);
                });
    }

    private Transaction executePayment(Transaction tx, Ledger ledger,
                                       Runnable providerAction, Runnable onSuccess) {
        persistTransactionState(tx);

        tx.authorize();
        persistTransactionState(tx);

        try {
            providerAction.run();

            tx.capture();
            persistTransactionState(tx);
            onSuccess.run();

            tx.pendSettlement();
            persistTransactionState(tx);

            tx.settle();
            persistTransactionState(tx);

            tx.markCompleted();
            persistTransactionState(tx);

        } catch (ProviderException e) {
            tx.markFailed();
            persistTransactionState(tx);
            var reverseTx = ledger.reverse(tx);
            transactionRepository.save(reverseTx);
        }

        return tx;
    }

    private Account validatePayerAndGetAccount(Id customerId, String pin) {
        authUseCase.validatePin(customerId, pin);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Customer not found: " + customerId.value()));

        if (customer.isSuspended()) {
            throw new AccountSuspendedException("Account suspended for customer: " + customerId.value());
        }

        return accountRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for customer: " + customerId.value()));
    }

    private Account validateRecipientAndGetAccount(String toPhoneNumber) {
        Customer customerTo = customerRepository.findByPhoneNumber(toPhoneNumber)
                .orElseThrow(() -> new AccountNotFoundException("No account found for phone: " + toPhoneNumber));

        if (customerTo.isSuspended()) {
            throw new AccountSuspendedException("Recipient account is suspended: " + toPhoneNumber);
        }

        Account accountTo = accountRepository.findByCustomerIdForUpdate(customerTo.snapshot().customerId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found for recipient: " + customerTo.snapshot().customerId().value()));

        if (accountTo.snapshot().isBlocked()) {
            throw new AccountBlockedException("Recipient account is blocked: " + accountTo.snapshot().accountId().value());
        }

        return accountTo;
    }

    private Account getSystemFloatAccountForUpdate() {
        return accountRepository.findFloatByProviderIdForUpdate(SystemConstants.PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException("Float account not found for provider: " + SystemConstants.PROVIDER_ID.value()));
    }

    private void persistTransactionState(Transaction tx) {
        transactionRepository.save(tx);
        historicRepo.save(tx.history().getLast());
    }
}