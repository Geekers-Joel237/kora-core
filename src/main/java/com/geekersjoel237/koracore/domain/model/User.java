package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.enums.UserStatus;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.regex.Pattern;

public class User {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Id id;
    private final String fullName;
    private final String email;
    private final Role role;
    private UserStatus status;

    private User(Id id, String fullName, String email, Role role, UserStatus status) {
        this.id       = id;
        this.fullName = fullName;
        this.email    = email;
        this.role     = role;
        this.status   = status;
    }

    public static User create(Id id, String fullName, String email, Role role) {
        if (id == null)
            throw new IllegalArgumentException("User id cannot be null");
        if (fullName == null || fullName.isBlank())
            throw new IllegalArgumentException("User fullName cannot be blank");
        if (email == null || !EMAIL_PATTERN.matcher(email).matches())
            throw new IllegalArgumentException("User email is invalid: " + email);
        if (role == null)
            throw new IllegalArgumentException("User role cannot be null");
        return new User(id, fullName, email, role, UserStatus.VERIFIED);
    }

    public static User createFromSnapshot(Snapshot snapshot) {
        return new User(snapshot.id(), snapshot.fullName(),
                snapshot.email(), snapshot.role(), snapshot.status());
    }


    public boolean isActive()    { return status == UserStatus.VERIFIED; }
    public boolean isSuspended() { return status == UserStatus.SUSPENDED; }
    public void suspend()        { this.status = UserStatus.SUSPENDED; }
    public void verify()         { this.status = UserStatus.VERIFIED; }


    public Snapshot snapshot() {
        return new Snapshot(id, fullName, email, role, status);
    }

    public record Snapshot(
            Id id,
            String fullName,
            String email,
            Role role,
            UserStatus status
    ) {}
}