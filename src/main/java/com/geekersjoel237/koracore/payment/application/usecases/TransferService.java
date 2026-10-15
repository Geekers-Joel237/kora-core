package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.payment.application.command.TransferCommand;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.BalanceCache;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.ports.in.TransferCommandHandler;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

public final class TransferService implements TransferCommandHandler {

    private final TransactionBoundary boundary;
    private final RetryPolicy retry;
    private final PinVerifier pinVerifier;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository trxHistoricStatesRepository;
    private final LedgerRepository ledgerRepository;

    public TransferService(TransactionBoundary boundary, RetryPolicy retry, PinVerifier pinVerifier,
                           AccountRepository accountRepository, CustomerRepository customerRepository,
                           TransactionRepository transactionRepository, TrxHistoricStatesRepository trxHistoricStatesRepository,
                           LedgerRepository ledgerRepository) {
        this.boundary = boundary;
        this.retry = retry;
        this.pinVerifier = pinVerifier;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.trxHistoricStatesRepository = trxHistoricStatesRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public PaymentResult execute(TransferCommand cmd) {
        pinVerifier.verify(cmd.customerId(), cmd.pin());

        return retry.execute(() -> boundary.execute(() -> move(cmd)));
    }

    private PaymentResult move(TransferCommand cmd) {
        Account payerAccount = lockPayerWallet(cmd.customerId());
        Account payeeAccount = lockPayeeWallet(cmd.recipient());

        Ledger ledger = ledgerRepository.findFirst();
        Transaction tx = ledger.initiate(payerAccount, payeeAccount,
                TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, cmd.amount());

        transactionRepository.save(tx);
        trxHistoricStatesRepository.save(tx.history().getFirst());

        tx.authorize();
        ledger.writeEntries(tx, payerAccount, payeeAccount, cmd.amount());
        applyBalanceUpdate(payerAccount, payeeAccount, cmd.amount());
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();

        tx.history().subList(1, tx.history().size()).forEach(trxHistoricStatesRepository::save);
        transactionRepository.save(tx);
        return PaymentResult.of(tx);
    }

    private Account lockPayerWallet(com.geekersjoel237.koracore.shared.domain.vo.Id customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + customerId.value()));
        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Account suspended for customer: " + customerId.value());

        return accountRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + customerId.value()));
    }

    private Account lockPayeeWallet(PhoneNumber recipient) {
        Customer customer = customerRepository.findByPhoneNumber(recipient.fullNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found for phone: " + recipient.masked()));
        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Recipient account is suspended: " + recipient.masked());

        Account account = accountRepository.findByCustomerIdForUpdate(customer.snapshot().customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for recipient: " + customer.snapshot().customerId().value()));
        if (account.snapshot().isBlocked())
            throw new AccountBlockedException(
                    "Recipient account is blocked: " + account.snapshot().accountId().value());

        return account;
    }

    private void applyBalanceUpdate(Account from, Account to, Amount amount) {
        BalanceCache.post(from, to, amount).forEach(accountRepository::save);
    }
}
