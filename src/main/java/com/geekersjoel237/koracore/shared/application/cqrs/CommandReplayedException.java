package com.geekersjoel237.koracore.shared.application.cqrs;

import com.geekersjoel237.koracore.shared.domain.vo.Id;

public class CommandReplayedException extends RuntimeException {

    public CommandReplayedException(Id correlationId) {
        super("Command already received under correlation id: " + correlationId.value());
    }
}
