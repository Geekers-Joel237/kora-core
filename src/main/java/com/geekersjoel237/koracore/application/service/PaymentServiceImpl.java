package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.port.in.AuthService;
import com.geekersjoel237.koracore.application.port.in.PaymentService;
import com.geekersjoel237.koracore.domain.enums.UserStatus;
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
public class PaymentServiceImpl implements PaymentService {

    private static final Id SYSTEM_PROVIDER_ID = new Id("provider-system-001");

    private final AuthService authService;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository historicRepo;
    private final ProviderPort provider;
    private final LedgerRepository ledgerRepository;

    public PaymentServiceImpl(AuthService authService,
                              AccountRepository accountRepository,
                              CustomerRepository customerRepository,
                              TransactionRepository transactionRepository,
                              TrxHistoricStatesRepository historicRepo,
                              ProviderPort provider,
                              LedgerRepository ledgerRepository) {
        this.authService = authService;
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


    private Transaction executePayment(Transaction tx, Ledger ledger, Runnable providerAction, Runnable onSuccess) {
        tx.markPending();
        persistTransactionState(tx);

        try {
            providerAction.run();

            tx.markCompleted();
            persistTransactionState(tx);
            onSuccess.run();

        } catch (ProviderException e) {
            tx.markFailed();
            persistTransactionState(tx);
            var reverseTx = ledger.reverse(tx);
            transactionRepository.save(reverseTx);
        }

        return tx;
    }

    private Account validatePayerAndGetAccount(Id customerId, String pin) {
        authService.validatePin(customerId, pin);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Customer not found: " + customerId.value()));

        if (customer.snapshot().user().status() == UserStatus.SUSPENDED) {
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

    private Account getSystemFloatAccount() {
        return accountRepository.findFloatByProviderId(SYSTEM_PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException("Float account not found for provider: " + SYSTEM_PROVIDER_ID.value()));
    }

    private void persistTransactionState(Transaction tx) {
        transactionRepository.save(tx);
        historicRepo.save(tx.history().getLast());
    }
}
