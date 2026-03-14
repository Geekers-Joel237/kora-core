package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Created on 28/02/2026
 *
 * @author Geekers_Joel237
 **/
public interface SpringDataUserRepository extends JpaRepository<UserEntity, String> {
}
