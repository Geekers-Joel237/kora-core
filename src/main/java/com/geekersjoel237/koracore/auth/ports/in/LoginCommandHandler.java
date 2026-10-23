package com.geekersjoel237.koracore.auth.ports.in;

import com.geekersjoel237.koracore.auth.application.command.LoginCommand;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;


public interface LoginCommandHandler extends CommandHandler<LoginCommand, Void> {
}
