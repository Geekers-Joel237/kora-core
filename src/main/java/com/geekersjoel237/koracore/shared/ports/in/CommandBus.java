package com.geekersjoel237.koracore.shared.ports.in;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;


public interface CommandBus {
    <R> R dispatch(Command<R> command);
}
