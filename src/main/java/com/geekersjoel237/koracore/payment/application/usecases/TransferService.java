package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.payment.application.command.TransferCommand;
import com.geekersjoel237.koracore.payment.ports.in.TransferCommandHandler;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Msisdn;


public final class TransferService implements TransferCommandHandler {

    private final TransactionBoundary boundary;
    private final RetryPolicy retry;
    private final PinVerifier pinVerifier;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final TrxHistoricStatesRepository history;
    private final LedgerRepository ledgers;

    public TransferService(TransactionBoundary boundary, RetryPolicy retry, PinVerifier pinVerifier,
                           AccountRepository accounts, CustomerRepository customers,
                           TransactionRepository transactions, TrxHistoricStatesRepository history,
                           LedgerRepository ledgers) {
        this.boundary = boundary;
        this.retry = retry;
        this.pinVerifier = pinVerifier;
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.history = history;
        this.ledgers = ledgers;
    }

    @Override
    public PaymentResult execute(TransferCommand cmd) {
        pinVerifier.verify(cmd.customerId(), cmd.pin());

        return retry.execute(() -> boundary.execute(() -> move(cmd)));
    }

    private PaymentResult move(TransferCommand cmd) {
        Account payerWallet = lockPayerWallet(cmd.customerId());
        Account payeeWallet = lockPayeeWallet(cmd.recipient());

        Ledger ledger = ledgers.findFirst();
        Transaction tx = ledger.initiate(payerWallet, payeeWallet,
                TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, cmd.amount());

        // Persist INITIALIZED first, mirroring TX-1 of the provider-backed sagas, so
        // the state history reads the same whichever use case produced it.
        transactions.save(tx);
        history.save(tx.history().getFirst());

        tx.authorize();
        ledger.writeEntries(tx, payerWallet, payeeWallet, cmd.amount());
        applyBalanceUpdate(payerWallet, payeeWallet, cmd.amount());
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();

        // One flush at the end: five history rows and one final save, instead of six.
        tx.history().subList(1, tx.history().size()).forEach(history::save);
        transactions.save(tx);
        return PaymentResult.of(tx);
    }

    private Account lockPayerWallet(com.geekersjoel237.koracore.shared.domain.vo.Id customerId) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + customerId.value()));
        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Account suspended for customer: " + customerId.value());

        return accounts.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + customerId.value()));
    }

    /**
     * Messages carry the masked number, never the number. They reach the caller and
     * the log, and "no account found for +237600000123" is a lookup oracle as much as
     * it is a leak.
     */
    private Account lockPayeeWallet(Msisdn recipient) {
        Customer customer = customers.findByPhoneNumber(recipient.value())
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found for phone: " + recipient.masked()));
        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Recipient account is suspended: " + recipient.masked());

        Account wallet = accounts.findByCustomerIdForUpdate(customer.snapshot().customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for recipient: " + customer.snapshot().customerId().value()));
        if (wallet.snapshot().isBlocked())
            throw new AccountBlockedException(
                    "Recipient account is blocked: " + wallet.snapshot().accountId().value());

        return wallet;
    }

    /**
     * ADR-001, rule 9: the balance cache moves with the ledger entries, or not at all.
     */
    private void applyBalanceUpdate(Account from, Account to, Amount amount) {
        if (from.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            from.debit(amount);
            accounts.save(from);
        }
        if (to.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            to.credit(amount);
            accounts.save(to);
        }
    }
}
