package com.geekersjoel237.koracore.auth.adapters.out.persistence;

import com.geekersjoel237.koracore.auth.adapters.out.persistence.mapping.UserPersistenceMapper;
import com.geekersjoel237.koracore.auth.adapters.out.persistence.repository.SpringDataUserRepository;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.repository.UserRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository userRepository;
    private final UserPersistenceMapper mapper;

    public JpaUserRepository(SpringDataUserRepository userRepository, UserPersistenceMapper mapper) {
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(User user) {
        userRepository.save(mapper.toEntity(user.snapshot()));
    }

    @Override
    public Optional<User> findById(Id id) {
        return userRepository.findById(id.value())
                .map(mapper::toSnapshot)
                .map(User::createFromSnapshot);
    }
}
