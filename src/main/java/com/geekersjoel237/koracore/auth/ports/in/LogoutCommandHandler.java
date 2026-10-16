package com.geekersjoel237.koracore.auth.ports.in;

import com.geekersjoel237.koracore.auth.application.command.LogoutCommand;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface LogoutCommandHandler extends CommandHandler<LogoutCommand, Void> {
}
