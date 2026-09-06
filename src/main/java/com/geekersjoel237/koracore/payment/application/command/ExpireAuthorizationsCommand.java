package com.geekersjoel237.koracore.payment.application.command;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

/**
 * Sweep the provider holds that outlived their TTL, as of a given instant.
 *
 * <p>The instant is carried rather than read from a clock inside the use case, so a
 * test can sweep a future the machine has not reached.
 */
public record ExpireAuthorizationsCommand(Id correlationId, java.time.Instant now)
        implements Command<Void> {
}
