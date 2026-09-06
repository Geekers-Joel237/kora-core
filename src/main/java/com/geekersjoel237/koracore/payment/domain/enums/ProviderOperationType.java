package com.geekersjoel237.koracore.payment.domain.enums;

public enum ProviderOperationType {
    /** Cash-in: pull money FROM customer external Mobile Money account INTO wallet */
    COLLECTION,
    /** Cash-out: push money FROM wallet TO customer external Mobile Money account */
    DISBURSEMENT
}