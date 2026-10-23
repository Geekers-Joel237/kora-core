package com.geekersjoel237.koracore.shared.application.cqrs;

import com.geekersjoel237.koracore.shared.domain.vo.Id;


public interface Command<R> {
    Id correlationId();
}
