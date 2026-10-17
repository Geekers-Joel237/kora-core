package com.geekersjoel237.koracore.payment.domain.exception;

import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public class HoldNotActiveException extends BusinessException {

    public HoldNotActiveException(Id holdId, HoldStatus status) {
        super("Funds hold " + holdId.value() + " is " + status.name() + ", no longer ACTIVE");
    }
}
