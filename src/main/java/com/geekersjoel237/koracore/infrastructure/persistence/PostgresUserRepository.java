package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.port.UserRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.UserEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Created on 28/02/2026
 *
 * @author Geekers_Joel237
 **/
@Repository
public class PostgresUserRepository implements UserRepository {
    private JpaUserRepository userRepository;
    public PostgresUserRepository(JpaUserRepository userRepository) {
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
