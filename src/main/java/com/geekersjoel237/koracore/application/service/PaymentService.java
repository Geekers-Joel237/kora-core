package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
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

@Service
@Transactional
public class PaymentService implements PaymentUseCase {

    private final AuthUseCase authUsecase;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository historicRepo;
    private final ProviderPort provider;
    private final LedgerRepository ledgerRepository;

    public PaymentService(AuthUseCase authUsecase,
                          AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          TransactionRepository transactionRepository,
                          TrxHistoricStatesRepository historicRepo,
                          ProviderPort provider,
                          LedgerRepository ledgerRepository) {
        this.authUsecase = authUsecase;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.historicRepo = historicRepo;
        this.provider = provider;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public Transaction cashIn(CashInCommand cmd) {
        var customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var floatAccount = getSystemFloatAccount();
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.cashIn(customerAccount, floatAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.credit(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    customerAccount.credit(cmd.amount());
                    accountRepository.save(customerAccount);
                });
    }

    @Override
    public Transaction cashOut(CashOutCommand cmd) {
        var customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var floatAccount = getSystemFloatAccount();
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.cashOut(customerAccount, floatAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.debit(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    customerAccount.debit(cmd.amount());
                    accountRepository.save(customerAccount);
                });
    }

    @Override
    public Transaction transfer(TransferCommand cmd) {
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
        // Persist the initial INITIALIZED state before the first transition
        persistTransactionState(tx);

        // AUTHORIZED replaces PENDING as the first active state
        tx.authorize();
        persistTransactionState(tx);

        try {
            providerAction.run();

            // CAPTURED: ledger entries are committed
            tx.capture();
            persistTransactionState(tx);
            onSuccess.run();

            // SETTLEMENT_PENDING: awaiting interbank settlement
            tx.pendSettlement();
            persistTransactionState(tx);

            // SETTLED: settlement confirmed (stub for Step 1)
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
        authUsecase.validatePin(customerId, pin);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Customer not found: " + customerId.value()));

        if (customer.isSuspended()) {
            throw new AccountSuspendedException("Account suspended for customer: " + customerId.value());
        }

        return accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for customer: " + customerId.value()));
    }

    private Account validateRecipientAndGetAccount(String toPhoneNumber) {
        Customer customerTo = customerRepository.findByPhoneNumber(toPhoneNumber)
                .orElseThrow(() -> new AccountNotFoundException("No account found for phone: " + toPhoneNumber));

        if (customerTo.isSuspended()) {
            throw new AccountSuspendedException("Recipient account is suspended: " + toPhoneNumber);
        }

        Account accountTo = accountRepository.findByCustomerId(customerTo.snapshot().customerId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found for recipient: " + customerTo.snapshot().customerId().value()));

        if (accountTo.snapshot().isBlocked()) {
            throw new AccountBlockedException("Recipient account is blocked: " + accountTo.snapshot().accountId().value());
        }

        return accountTo;
    }

    @Override
    @Transactional(readOnly = true)
    public Account getBalance(Id customerId) {
        return accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + customerId.value()));
    }

    private Account getSystemFloatAccount() {
        return accountRepository.findFloatByProviderId(SystemConstants.PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException("Float account not found for provider: " + SystemConstants.PROVIDER_ID.value()));
    }

    private void persistTransactionState(Transaction tx) {
        transactionRepository.save(tx);
        historicRepo.save(tx.history().getLast());
    }
}
