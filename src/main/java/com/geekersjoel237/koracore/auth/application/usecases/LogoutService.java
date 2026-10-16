package com.geekersjoel237.koracore.auth.application.usecases;

import com.geekersjoel237.koracore.auth.application.command.LogoutCommand;
import com.geekersjoel237.koracore.auth.ports.in.LogoutCommandHandler;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

public final class LogoutService implements LogoutCommandHandler {

    private final TransactionBoundary boundary;
    private final TokenIssuer tokenIssuer;

    public LogoutService(TransactionBoundary boundary, TokenIssuer tokenIssuer) {
        this.boundary = boundary;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public Void execute(LogoutCommand cmd) {
        return boundary.execute(() -> {
            tokenIssuer.revoke(cmd.refreshToken());
            return null;
        });
    }
}
