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
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository trxHistoricStatesRepository;
    private final AuthorizationRecordRepository authorizationRecordRepository;

    public ExpireAuthorizationsService(TransactionBoundary boundary,
                                       TransactionRepository transactionRepository,
                                       TrxHistoricStatesRepository trxHistoricStatesRepository,
                                       AuthorizationRecordRepository authorizationRecordRepository) {
        this.boundary = boundary;
        this.transactionRepository = transactionRepository;
        this.trxHistoricStatesRepository = trxHistoricStatesRepository;
        this.authorizationRecordRepository = authorizationRecordRepository;
    }

    @Override
    public Void execute(ExpireAuthorizationsCommand cmd) {
        return boundary.execute(() -> {
            for (AuthorizationRecord record : authorizationRecordRepository.findExpiredActive(cmd.now())) {
                record.expire();
                authorizationRecordRepository.save(record);

                transactionRepository.findById(record.snapshot().transactionId()).ifPresent(tx -> {
                    tx.failAuthorization();
                    trxHistoricStatesRepository.save(TrxStateHistoric.of(
                            tx.snapshot().transactionId(),
                            TransactionState.AUTHORIZED, TransactionState.AUTHORIZATION_FAILED,
                            TriggerSource.SYSTEM_JOB, null, null,
                            SYSTEM_ACTOR, "Authorization TTL expired"));
                    transactionRepository.save(tx);
                });
            }
            return null;
        });
    }
}
