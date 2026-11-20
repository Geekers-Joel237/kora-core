package com.geekersjoel237.koracore.shared.adapters.in.rest.mapping;

import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class RequestConversions {

    public static final String CORRELATION_ID = "correlationId";

    public Id id(String value) {
        return new Id(value);
    }

    @Named(CORRELATION_ID)
    public Id correlationId(String header) {
        return CorrelationId.fromHeaderOrNew(header);
    }

    public Pin pin(String raw) {
        return Pin.of(raw);
    }
}
