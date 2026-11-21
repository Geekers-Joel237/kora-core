package com.geekersjoel237.koracore.shared.unit.doubles;

import com.geekersjoel237.koracore.shared.domain.exception.MailProviderException;
import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;

public class FailingMailPort implements MailPort {

    private int attempts;

    @Override
    public void send(Mail mail) {
        attempts++;
        throw new MailProviderException(new IllegalStateException("simulated SMTP outage"));
    }

    public int attempts() {
        return attempts;
    }
}
