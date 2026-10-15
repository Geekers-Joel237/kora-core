package com.geekersjoel237.koracore.shared.unit.doubles;

import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

import java.util.function.Supplier;

public class DirectTransactionBoundary implements TransactionBoundary {

    @Override
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
