package com.geekersjoel237.koracore.auth.config;

import com.geekersjoel237.koracore.auth.application.command.LoginCommand;
import com.geekersjoel237.koracore.auth.application.command.RefreshTokensCommand;
import com.geekersjoel237.koracore.auth.application.command.RegisterCommand;
import com.geekersjoel237.koracore.auth.application.command.VerifyOtpCommand;
import com.geekersjoel237.koracore.auth.ports.in.LoginCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.RefreshTokensCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.RegisterCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.VerifyOtpCommandHandler;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistrar;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistry;
import org.springframework.stereotype.Component;


@Component
public class AuthCommandRegistrar implements CommandRegistrar {

    private final RegisterCommandHandler register;
    private final LoginCommandHandler login;
    private final VerifyOtpCommandHandler verifyOtp;
    private final RefreshTokensCommandHandler refreshTokens;

    public AuthCommandRegistrar(RegisterCommandHandler register, LoginCommandHandler login,
                                VerifyOtpCommandHandler verifyOtp, RefreshTokensCommandHandler refreshTokens) {
        this.register = register;
        this.login = login;
        this.verifyOtp = verifyOtp;
        this.refreshTokens = refreshTokens;
    }

    @Override
    public void registerInto(CommandRegistry registry) {
        registry.register(RegisterCommand.class, register)
                .register(LoginCommand.class, login)
                .register(VerifyOtpCommand.class, verifyOtp)
                .register(RefreshTokensCommand.class, refreshTokens);
    }
}
