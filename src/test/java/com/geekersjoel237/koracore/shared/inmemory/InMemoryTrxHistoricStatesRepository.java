package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.port.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryTrxHistoricStatesRepository implements TrxHistoricStatesRepository {

    private final Map<String, List<TrxStateHistoric>> store = new HashMap<>();

    @Override
    public void save(TrxStateHistoric historic) {
        store.computeIfAbsent(historic.transactionId().value(), k -> new ArrayList<>())
             .add(historic);
    }

    @Override
    public List<TrxStateHistoric> findByTransactionId(Id transactionId) {
        return store.getOrDefault(transactionId.value(), List.of())
                .stream()
                .sorted(Comparator.comparing(TrxStateHistoric::occurredAt))
                .toList();
    }

    public void reset() {
        store.clear();
    }
}