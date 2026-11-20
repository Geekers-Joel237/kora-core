package com.geekersjoel237.koracore.auth.ports.in;

import com.geekersjoel237.koracore.auth.application.command.register.RegisterCommand;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface RegisterCommandHandler extends CommandHandler<RegisterCommand, Void> {
}
