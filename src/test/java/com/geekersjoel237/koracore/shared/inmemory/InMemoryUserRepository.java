package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.port.UserRepository;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new HashMap<>();

    @Override
    public void save(User user) {
        store.put(user.snapshot().id().value(), user);
    }

    @Override
    public Optional<User> findById(Id id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    public void reset() {
        store.clear();
    }
}