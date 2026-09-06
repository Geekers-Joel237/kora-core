package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.payment.application.command.ExpireAuthorizationsCommand;
import com.geekersjoel237.koracore.payment.ports.in.ExpireAuthorizationsCommandHandler;
import com.geekersjoel237.koracore.payment.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.payment.ports.out.repository.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public final class ExpireAuthorizationsService implements ExpireAuthorizationsCommandHandler {

    private static final Id SYSTEM_ACTOR = new Id("system-ttl-job");

    private final TransactionBoundary boundary;
    private final TransactionRepository transactions;
    private final TrxHistoricStatesRepository history;
    private final AuthorizationRecordRepository authorizations;

    public ExpireAuthorizationsService(TransactionBoundary boundary,
                                       TransactionRepository transactions,
                                       TrxHistoricStatesRepository history,
                                       AuthorizationRecordRepository authorizations) {
        this.boundary = boundary;
        this.transactions = transactions;
        this.history = history;
        this.authorizations = authorizations;
    }

    @Override
    public Void execute(ExpireAuthorizationsCommand cmd) {
        return boundary.execute(() -> {
            for (AuthorizationRecord record : authorizations.findExpiredActive(cmd.now())) {
                record.expire();
                authorizations.save(record);

                transactions.findById(record.snapshot().transactionId()).ifPresent(tx -> {
                    tx.failAuthorization();
                    history.save(TrxStateHistoric.of(
                            tx.snapshot().transactionId(),
                            TransactionState.AUTHORIZED, TransactionState.AUTHORIZATION_FAILED,
                            TriggerSource.SYSTEM_JOB, null, null,
                            SYSTEM_ACTOR, "Authorization TTL expired"));
                    transactions.save(tx);
                });
            }
            return null;
        });
    }
}
