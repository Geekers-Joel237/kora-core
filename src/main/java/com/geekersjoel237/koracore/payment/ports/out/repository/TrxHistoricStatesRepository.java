package com.geekersjoel237.koracore.payment.ports.out.repository;

import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.List;

public interface TrxHistoricStatesRepository {
    void save(TrxStateHistoric historic);
    List<TrxStateHistoric> findByTransactionId(Id transactionId);
}