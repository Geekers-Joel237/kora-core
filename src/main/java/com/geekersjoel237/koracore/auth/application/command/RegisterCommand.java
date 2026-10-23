package com.geekersjoel237.koracore.auth.application.command;

import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record RegisterCommand(
        Id correlationId,
        String fullName,
        String email,
        String phonePrefix,
        String phoneNumber,
        Pin pin
) implements Command<Void> {
}