package com.geekersjoel237.koracore.payment.config;

import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.CashOutCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.ExpireAuthorizationsCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.ports.in.ReversePaymentCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.TransferCommandHandler;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.payment.application.usecases.CashInService;
import com.geekersjoel237.koracore.payment.application.usecases.CashOutService;
import com.geekersjoel237.koracore.payment.application.usecases.ExpireAuthorizationsService;
import com.geekersjoel237.koracore.payment.application.usecases.BalanceService;
import com.geekersjoel237.koracore.payment.application.usecases.ReversePaymentService;
import com.geekersjoel237.koracore.payment.application.usecases.TransferService;
import com.geekersjoel237.koracore.payment.ports.out.query.ReadAccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.query.ReadTransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.payment.ports.out.provider.PaymentProviderPort;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.payment.application.usecases.TransactionHistoryService;


@Configuration
public class UseCaseConfiguration {

    @Bean
    RetryPolicy retryPolicy() {
        return RetryPolicy.defaults();
    }

    @Bean
    CashInCommandHandler cashInCommandHandler(TransactionBoundary boundary, RetryPolicy retry, PinVerifier pinVerifier,
                                              AccountRepository accounts, CustomerRepository customers,
                                              TransactionRepository transactions, TrxHistoricStatesRepository history,
                                              PaymentProviderPort provider, LedgerRepository ledgers,
                                              AuthorizationRecordRepository authorizations) {
        return new CashInService(boundary, retry, pinVerifier, accounts, customers,
                transactions, history, provider, ledgers, authorizations);
    }

    @Bean
    CashOutCommandHandler cashOutCommandHandler(TransactionBoundary boundary, RetryPolicy retry, PinVerifier pinVerifier,
                                                AccountRepository accounts, CustomerRepository customers,
                                                TransactionRepository transactions, TrxHistoricStatesRepository history,
                                                PaymentProviderPort provider, LedgerRepository ledgers,
                                                AuthorizationRecordRepository authorizations) {
        return new CashOutService(boundary, retry, pinVerifier, accounts, customers,
                transactions, history, provider, ledgers, authorizations);
    }

    @Bean
    TransferCommandHandler transferCommandHandler(TransactionBoundary boundary, RetryPolicy retry, PinVerifier pinVerifier,
                                    AccountRepository accounts, CustomerRepository customers,
                                    TransactionRepository transactions, TrxHistoricStatesRepository history,
                                    LedgerRepository ledgers) {
        return new TransferService(boundary, retry, pinVerifier, accounts, customers,
                transactions, history, ledgers);
    }

    @Bean
    ReversePaymentCommandHandler reversePaymentCommandHandler(TransactionBoundary boundary, RetryPolicy retry,
                                               AccountRepository accounts, TransactionRepository transactions,
                                               TrxHistoricStatesRepository history, PaymentProviderPort provider,
                                               LedgerRepository ledgers,
                                               AuthorizationRecordRepository authorizations) {
        return new ReversePaymentService(boundary, retry, accounts, transactions,
                history, provider, ledgers, authorizations);
    }

    @Bean
    BalanceQueryHandler getBalanceQueryHandler(ReadAccountRepository accounts) {
        return new BalanceService(accounts);
    }

    @Bean
    TransactionHistoryQueryHandler transactionHistoryQueryHandler(ReadAccountRepository accounts,
                                                                  ReadTransactionRepository transactions,
                                                                  @Value("${kora.time-zone}") ZoneId customerZone) {
        return new TransactionHistoryService(accounts, transactions, customerZone);
    }

    @Bean
    ExpireAuthorizationsCommandHandler expireAuthorizationsCommandHandler(TransactionBoundary boundary,
                                                            TransactionRepository transactions,
                                                            TrxHistoricStatesRepository history,
                                                            AuthorizationRecordRepository authorizations) {
        return new ExpireAuthorizationsService(boundary, transactions, history, authorizations);
    }
}
