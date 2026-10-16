package com.geekersjoel237.koracore.auth.domain.vo;

import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Objects;


public record AuthenticatedUser(Id userId, Role role) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }
}
