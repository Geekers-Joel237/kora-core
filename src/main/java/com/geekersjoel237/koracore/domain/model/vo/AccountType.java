package com.geekersjoel237.koracore.domain.model.vo;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.vo.Id;

public record AccountType(Id resourceId, ResourceType resourceType) {

    public AccountType {
        if (resourceId == null)
            throw new IllegalArgumentException("AccountType resourceId cannot be null");
        if (resourceType == null)
            throw new IllegalArgumentException("AccountType resourceType cannot be null");
    }

    public static AccountType customer(Id customerId) {
        return new AccountType(customerId, ResourceType.CUSTOMER_ACCOUNT);
    }

    public static AccountType float_(Id providerId) {
        return new AccountType(providerId, ResourceType.FLOAT_ACCOUNT);
    }
}