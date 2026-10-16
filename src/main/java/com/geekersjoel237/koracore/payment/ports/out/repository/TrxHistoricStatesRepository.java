package com.geekersjoel237.koracore.payment.ports.out.repository;

import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;

public interface TrxHistoricStatesRepository {
    void save(TrxStateHistoric historic);
}
