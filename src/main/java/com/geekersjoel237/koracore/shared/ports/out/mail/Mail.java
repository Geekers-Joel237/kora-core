package com.geekersjoel237.koracore.shared.ports.out.mail;


public record Mail(String to, String subject, String body) {

    public Mail {
        if (to == null || to.isBlank())
            throw new IllegalArgumentException("Mail recipient is required");
        if (subject == null || subject.isBlank())
            throw new IllegalArgumentException("Mail subject is required");
        if (body == null || body.isBlank())
            throw new IllegalArgumentException("Mail body is required");
    }
}
