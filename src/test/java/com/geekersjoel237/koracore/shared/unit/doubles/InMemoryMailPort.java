package com.geekersjoel237.koracore.shared.unit.doubles;

import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryMailPort implements MailPort {

    private final List<Mail> sent = new ArrayList<>();

    @Override
    public void send(Mail mail) {
        sent.add(mail);
    }

    public Optional<Mail> lastMailTo(String email) {
        return sent.stream()
                .filter(mail -> mail.to().equals(email))
                .reduce((first, second) -> second);
    }

    public List<Mail> sent() {
        return List.copyOf(sent);
    }

    public void reset() {
        sent.clear();
    }
}
