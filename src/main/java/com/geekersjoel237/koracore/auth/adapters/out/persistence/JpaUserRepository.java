package com.geekersjoel237.koracore.auth.adapters.out.persistence;

import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.repository.UserRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.adapters.out.persistence.entities.UserEntity;
import com.geekersjoel237.koracore.auth.adapters.out.persistence.repository.SpringDataUserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Created on 28/02/2026
 *
 * @author Geekers_Joel237
 **/
@Repository
public class JpaUserRepository implements UserRepository {
    private final SpringDataUserRepository userRepository;
    public JpaUserRepository(SpringDataUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void save(User user) {
        User.Snapshot snapshot = user.snapshot();
        UserEntity entity = toEntity(snapshot);
        userRepository.save(entity);

    }

    private static UserEntity toEntity(User.Snapshot snapshot) {
        UserEntity entity =
            UserEntity.builder()
                .fullName(snapshot.fullName())
                .email(snapshot.email())
                .role(snapshot.role())
                .status(snapshot.status())
                .build();
        entity.setId(snapshot.id().value());
        return entity;
    }

    @Override
    public Optional<User> findById(Id id) {
        return userRepository.findById(id.value())
                .map(entity -> User.createFromSnapshot(new User.Snapshot(
                        new Id(entity.getId()),
                        entity.getFullName(),
                        entity.getEmail(),
                        entity.getRole(),
                        entity.getStatus()
                )));

    }
}
