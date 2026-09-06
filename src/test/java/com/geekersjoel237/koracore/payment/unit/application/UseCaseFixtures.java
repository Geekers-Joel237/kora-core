package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.CashOutCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.ExpireAuthorizationsCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.ports.in.ReversePaymentCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.TransferCommandHandler;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.auth.adapters.out.security.CustomerPinVerifier;
import com.geekersjoel237.koracore.payment.application.usecases.CashInService;
import com.geekersjoel237.koracore.payment.application.usecases.CashOutService;
import com.geekersjoel237.koracore.payment.application.usecases.ExpireAuthorizationsService;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountQueryPort;
import com.geekersjoel237.koracore.payment.application.usecases.BalanceService;
import com.geekersjoel237.koracore.payment.application.usecases.ReversePaymentService;
import com.geekersjoel237.koracore.payment.application.usecases.TransferService;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.payment.ports.out.provider.ProviderPort;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.payment.config.UseCaseConfiguration;

/**
 * Builds the use cases from their ports, the way {@code UseCaseConfiguration} does in
 * production — by hand, with no container.
 *
 * <p>That the assembly fits here, with no framework and no classpath scan, is the
 * property the hexagonal split is for. A test names what it substitutes and takes the
 * rest as it stands.
 */
public final class UseCaseFixtures {

    /** Retries, but does not sleep: a test that waits is a test nobody runs. */
    public static final RetryPolicy IMMEDIATE_RETRY = new RetryPolicy(5, 0L);

    private UseCaseFixtures() {
    }

    public record UseCases(CashInCommandHandler cashIn,
                           CashOutCommandHandler cashOut,
                           TransferCommandHandler transfer,
                           ReversePaymentCommandHandler reversePayment,
                           BalanceQueryHandler getBalance,
                           ExpireAuthorizationsCommandHandler expireAuthorizations) {
    }

    public static UseCases build(TransactionBoundary boundary,
                                 AccountRepository accounts,
                                 CustomerRepository customers,
                                 ProviderPort provider,
                                 TransactionRepository transactions,
                                 TrxHistoricStatesRepository history,
                                 LedgerRepository ledgers,
                                 AuthorizationRecordRepository authorizations,
                                 CustomerPinEncoder pinEncoder) {
        PinVerifier pinVerifier = new CustomerPinVerifier(customers, pinEncoder);
        RetryPolicy retry = IMMEDIATE_RETRY;

        return new UseCases(
                new CashInService(boundary, retry, pinVerifier, accounts, customers,
                        transactions, history, provider, ledgers, authorizations),
                new CashOutService(boundary, retry, pinVerifier, accounts, customers,
                        transactions, history, provider, ledgers, authorizations),
                new TransferService(boundary, retry, pinVerifier, accounts, customers,
                        transactions, history, ledgers),
                new ReversePaymentService(boundary, retry, accounts, transactions,
                        history, provider, ledgers, authorizations),
                new BalanceService(new InMemoryAccountQueryPort(accounts)),
                new ExpireAuthorizationsService(boundary, transactions, history, authorizations));
    }

    public static CashInCommandHandler cashIn(TransactionBoundary boundary,
                                       AccountRepository accounts,
                                       CustomerRepository customers,
                                       ProviderPort provider,
                                       TransactionRepository transactions,
                                       TrxHistoricStatesRepository history,
                                       LedgerRepository ledgers,
                                       AuthorizationRecordRepository authorizations,
                                       CustomerPinEncoder pinEncoder) {
        return build(boundary, accounts, customers, provider, transactions,
                history, ledgers, authorizations, pinEncoder).cashIn();
    }
}
