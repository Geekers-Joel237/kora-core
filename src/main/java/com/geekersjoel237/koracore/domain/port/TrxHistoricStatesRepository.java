package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.List;

public interface TrxHistoricStatesRepository {
    void save(TrxStateHistoric historic);
    List<TrxStateHistoric> findByTransactionId(Id transactionId);
}