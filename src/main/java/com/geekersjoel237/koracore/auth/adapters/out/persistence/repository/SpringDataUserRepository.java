package com.geekersjoel237.koracore.auth.adapters.out.persistence.repository;

import com.geekersjoel237.koracore.auth.adapters.out.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, String> {
}
