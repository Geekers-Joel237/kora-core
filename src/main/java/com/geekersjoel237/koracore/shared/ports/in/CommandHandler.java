package com.geekersjoel237.koracore.shared.ports.in;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;


public interface CommandHandler<C extends Command<R>, R> {

    R execute(C command);
}
