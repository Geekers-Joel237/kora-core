package com.geekersjoel237.koracore.payment.application.command.expireAuthorizations;

import com.geekersjoel237.koracore.payment.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.payment.ports.in.ExpireAuthorizationsCommandHandler;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.FundsHoldRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

import java.util.Optional;


public final class ExpireAuthorizationsService implements ExpireAuthorizationsCommandHandler {

    private static final Id SYSTEM_ACTOR = new Id("system-ttl-job");

    private final TransactionBoundary boundary;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository trxHistoricStatesRepository;
    private final AuthorizationRecordRepository authorizationRecordRepository;
    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final FundsHoldRepository holdRepository;

    public ExpireAuthorizationsService(TransactionBoundary boundary,
                                       TransactionRepository transactionRepository,
                                       TrxHistoricStatesRepository trxHistoricStatesRepository,
                                       AuthorizationRecordRepository authorizationRecordRepository,
                                       AccountRepository accountRepository,
                                       LedgerRepository ledgerRepository,
                                       FundsHoldRepository holdRepository) {
        this.boundary = boundary;
        this.transactionRepository = transactionRepository;
        this.trxHistoricStatesRepository = trxHistoricStatesRepository;
        this.authorizationRecordRepository = authorizationRecordRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.holdRepository = holdRepository;
    }

    @Override
    public Void execute(ExpireAuthorizationsCommand cmd) {
        return boundary.execute(() -> {
            for (AuthorizationRecord record : authorizationRecordRepository.findExpiredActive(cmd.now())) {
                Optional<Transaction> authorized = transactionRepository.findById(record.snapshot().transactionId());
                if (authorized.isPresent() && authorized.get().snapshot().state() != TransactionState.AUTHORIZED)
                    continue;

                record.expire();
                authorizationRecordRepository.save(record);
                authorized.ifPresent(this::failAndGiveTheFundsBack);
            }
            return null;
        });
    }

    private void failAndGiveTheFundsBack(Transaction tx) {
        Id transactionId = tx.snapshot().transactionId();
        tx.failAuthorization();
        trxHistoricStatesRepository.save(TrxStateHistoric.of(
                transactionId,
                TransactionState.AUTHORIZED, TransactionState.AUTHORIZATION_FAILED,
                TriggerSource.SYSTEM_JOB, null, null,
                SYSTEM_ACTOR, "Authorization TTL expired"));
        holdRepository.findActiveByTransactionId(transactionId).ifPresent(hold -> {
            ledgerRepository.findFirst().release(hold,
                    accountRepository.findByIdForUpdate(tx.snapshot().fromAccountId()).orElseThrow());
            holdRepository.save(hold);
        });
        transactionRepository.save(tx);
    }
}
