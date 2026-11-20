package com.geekersjoel237.koracore.payment.contract;

import java.util.function.Function;

abstract class ProgrammableQuery<Q, R> {

    private Function<Q, R> answer;
    private Q lastQuery;

    void answer(Function<Q, R> answer) {
        this.answer = answer;
    }

    void fail(RuntimeException failure) {
        this.answer = query -> {
            throw failure;
        };
    }

    Q lastQuery() {
        return lastQuery;
    }

    void reset() {
        answer = null;
        lastQuery = null;
    }

    public R execute(Q query) {
        lastQuery = query;
        if (answer == null)
            throw new AssertionError("No answer programmed for " + query.getClass().getSimpleName());
        return answer.apply(query);
    }
}
