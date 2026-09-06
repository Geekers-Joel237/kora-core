package com.geekersjoel237.koracore.auth.adapters.out.persistence.repository;

import com.geekersjoel237.koracore.auth.adapters.out.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Created on 28/02/2026
 *
 * @author Geekers_Joel237
 **/
public interface SpringDataUserRepository extends JpaRepository<UserEntity, String> {
}
