package com.geekersjoel237.koracore.application.command;

public record RegisterCommand(
        String fullName,
        String email,
        String phonePrefix,
        String phoneNumber,
        String rawPin
) {}