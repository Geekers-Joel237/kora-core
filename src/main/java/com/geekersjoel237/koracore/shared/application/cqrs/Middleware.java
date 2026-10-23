package com.geekersjoel237.koracore.shared.application.cqrs;

import java.util.function.Supplier;


public interface Middleware {

    <R> R around(Command<R> command, Supplier<R> next);
}
