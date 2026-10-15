package com.geekersjoel237.koracore.payment.application.command;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record ExpireAuthorizationsCommand(Id correlationId, java.time.Instant now)
        implements Command<Void> {
}
