package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.ExpireAuthorizationsCommand;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;


public interface ExpireAuthorizationsCommandHandler
        extends CommandHandler<ExpireAuthorizationsCommand, Void> {
}
