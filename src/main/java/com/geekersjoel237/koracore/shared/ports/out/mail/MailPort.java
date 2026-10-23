package com.geekersjoel237.koracore.shared.ports.out.mail;

import com.geekersjoel237.koracore.shared.domain.exception.MailProviderException;


public interface MailPort {


    void send(Mail mail) throws MailProviderException;
}
