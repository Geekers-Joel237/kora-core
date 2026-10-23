package com.geekersjoel237.koracore.auth.application.command;

import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;

public record VerifyOtpCommand(Id correlationId, String email, OtpCode code)
        implements Command<Tokens> {
}
