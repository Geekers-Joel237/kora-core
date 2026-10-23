package com.geekersjoel237.koracore.auth.unit.doubles;

import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.repository.UserRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

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