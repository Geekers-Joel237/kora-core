package com.geekersjoel237.koracore.shared.adapters.out.mail;

import com.geekersjoel237.koracore.shared.domain.exception.MailProviderException;
import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;


@Component
@Profile("!test")
public class SmtpMailAdapter implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailAdapter.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpMailAdapter(JavaMailSender mailSender,
                           @Value("${spring.mail.from:noreply@kora.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(Mail mail) throws MailProviderException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());

        try {
            mailSender.send(message);
        } catch (Throwable e) {
            log.warn("Mail delivery failed for {} [subject={}]", mail.to(), mail.subject(), e);
            throw new MailProviderException(e);
        }

        log.info("Mail sent to {} [subject={}]", mail.to(), mail.subject());
    }
}
