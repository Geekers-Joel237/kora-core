package com.geekersjoel237.koracore.auth.application.usecases;

import com.geekersjoel237.koracore.auth.application.command.RefreshTokensCommand;
import com.geekersjoel237.koracore.auth.ports.in.RefreshTokensCommandHandler;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.repository.UserRepository;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public final class RefreshTokensService implements RefreshTokensCommandHandler {

    private final TransactionBoundary boundary;
    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;

    public RefreshTokensService(TransactionBoundary boundary, UserRepository userRepository,
                                TokenIssuer tokenIssuer) {
        this.boundary = boundary;
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public Tokens execute(RefreshTokensCommand cmd) {
        Id userId = tokenIssuer.subjectOf(cmd.refreshToken());

        return boundary.execute(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomerNotFoundException(
                            "User not found: " + userId.value()));
            return tokenIssuer.issue(user);
        });
    }
}
