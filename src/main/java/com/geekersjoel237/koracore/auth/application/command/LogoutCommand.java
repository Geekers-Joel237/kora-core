package com.geekersjoel237.koracore.auth.application.command;

import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record LogoutCommand(Id correlationId, RefreshToken refreshToken)
        implements Command<Void> {
}
