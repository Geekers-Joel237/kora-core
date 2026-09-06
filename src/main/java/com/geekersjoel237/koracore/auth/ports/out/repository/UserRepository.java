package com.geekersjoel237.koracore.auth.ports.out.repository;

import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;

public interface UserRepository {
    void save(User user);
    Optional<User> findById(Id id);
}