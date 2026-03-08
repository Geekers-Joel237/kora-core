package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.UserEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private PostgresUserRepository repository;

    @Autowired
    private JpaUserRepository jpaRepository;

    @Test
    void should_persist_and_return_intact_round_trip() {
        Id id = Id.generate();
        User user = User.create(id, "Grace Hopper", "grace@example.com", Role.CUSTOMER);

        repository.save(user);

        Optional<User> found = repository.findById(id);
        assertThat(found).isPresent();
        User.Snapshot snap = found.get().snapshot();
        assertThat(snap.id()).isEqualTo(id);
        assertThat(snap.fullName()).isEqualTo("Grace Hopper");
        assertThat(snap.email()).isEqualTo("grace@example.com");
        assertThat(snap.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void should_return_empty_when_user_not_found() {
        Optional<User> found = repository.findById(Id.generate());
        assertThat(found).isEmpty();
    }

    @Test
    void should_throw_constraint_violation_when_email_is_duplicated() {
        Id id1 = Id.generate();
        User user1 = User.create(id1, "Alan Turing", "turing@example.com", Role.CUSTOMER);
        repository.save(user1);

        UserEntity duplicate = UserEntity.builder()
                .fullName("Alan Turing Clone")
                .email("turing@example.com")
                .role(Role.CUSTOMER)
                .status(com.geekersjoel237.koracore.domain.enums.UserStatus.VERIFIED)
                .build();
        duplicate.setId(Id.generate().value());

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}