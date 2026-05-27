package com.geekersjoel237.koracore.infrastructure.mail;

import com.geekersjoel237.koracore.domain.exception.MailProviderException;
import com.geekersjoel237.koracore.domain.port.MailPort;
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
    public void sendOtp(String toEmail, String otpCode, String subject) throws MailProviderException {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText("Your one-time code is valid for 5 minutes.\n\nDo not share it with anyone.");

            mailSender.send(message);
        } catch (Throwable e) {
            throw new MailProviderException(e);
        } finally {
            // otpCode intentionally excluded from logs
            log.info("Sending OTP mail to {} [subject={}]", toEmail, subject);
        }
    }
}