package com.geekersjoel237.koracore.infrastructure.mail;

import com.geekersjoel237.koracore.domain.port.MailPort;
import com.geekersjoel237.koracore.domain.port.OtpMailContext;
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
    public void sendOtp(String toEmail, String otpCode, OtpMailContext context) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject(context));
        message.setText("Your one-time code is valid for 5 minutes.\n\nDo not share it with anyone.");
        // otpCode intentionally excluded from logs
        log.info("Sending OTP mail to {} [context={}]", toEmail, context);
        mailSender.send(message);
    }

    private String subject(OtpMailContext context) {
        return context == OtpMailContext.REGISTRATION
                ? "Kora — verify your account"
                : "Kora — login verification";
    }
}