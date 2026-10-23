package com.geekersjoel237.koracore.shared.adapters.in.cqrs;


public interface CommandRegistrar {

    void registerInto(CommandRegistry registry);
}
