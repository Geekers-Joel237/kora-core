package com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Component;

@Component
public class IdMapper {

    public String toValue(Id id) {
        return id == null ? null : id.value();
    }

    public Id toId(String value) {
        return value == null ? null : new Id(value);
    }
}
