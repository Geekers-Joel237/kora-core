package com.geekersjoel237.koracore.shared.unit.doubles;

import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the messages it was handed, so a test can read one the way its recipient would.
 *
 * <p>It records the {@link Mail} itself and not the arguments that produced it. That
 * distinction is not cosmetic: the double used to store the code it was passed
 * alongside the message, so it reported a code the recipient would never have seen —
 * and the production adapter, which dropped that argument on the floor, looked correct.
 */
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
