package com.geekersjoel237.koracore.auth.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.auth.adapters.out.persistence.entities.UserEntity;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping.PersistenceMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = PersistenceMapperConfig.class)
public interface UserPersistenceMapper {

    UserEntity toEntity(User.Snapshot snapshot);

    User.Snapshot toSnapshot(UserEntity entity);
}
