package com.geekersjoel237.koracore.shared.ports.in;

import com.geekersjoel237.koracore.shared.application.cqrs.Query;


public interface QueryHandler<Q extends Query<R>, R> {

    R execute(Q query);
}
