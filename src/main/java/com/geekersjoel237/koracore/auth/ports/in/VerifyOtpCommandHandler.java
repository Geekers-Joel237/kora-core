package com.geekersjoel237.koracore.auth.ports.in;

import com.geekersjoel237.koracore.auth.application.command.VerifyOtpCommand;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface VerifyOtpCommandHandler extends CommandHandler<VerifyOtpCommand, Tokens> {
}
