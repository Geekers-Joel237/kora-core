package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.Optional;

public interface UserRepository {
    void save(User user);
    Optional<User> findById(Id id);
}