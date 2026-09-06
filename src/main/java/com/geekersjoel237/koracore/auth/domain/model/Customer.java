package com.geekersjoel237.koracore.auth.domain.model;

import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;

public class Customer {

    private final User user;
    private final PhoneNumber phoneNumber;
    private final String hashedPin;

    private Customer(User user, PhoneNumber phoneNumber, String hashedPin) {
        this.user        = user;
        this.phoneNumber = phoneNumber;
        this.hashedPin   = hashedPin;
    }

    public static Customer create(User user, PhoneNumber phoneNumber,
                                  Pin pin, CustomerPinEncoder encoder) {
        if (user == null)
            throw new IllegalArgumentException("Customer user cannot be null");
        if (phoneNumber == null)
            throw new IllegalArgumentException("Customer phoneNumber cannot be null");
        if (pin == null)
            throw new IllegalArgumentException("Customer pin cannot be null");
        return new Customer(user, phoneNumber, encoder.encode(pin));
    }

    public static Customer createFromSnapshot(Snapshot snapshot) {
        User user = User.createFromSnapshot(snapshot.user());
        return new Customer(user, snapshot.phoneNumber(), snapshot.hashedPin());
    }


    public boolean isActive()    { return user.isActive(); }
    public boolean isSuspended() { return user.isSuspended(); }

    public boolean matchesPin(Pin pin, CustomerPinEncoder encoder) {
        return encoder.matches(pin, this.hashedPin);
    }


    public Snapshot snapshot() {
        User.Snapshot userSnap = user.snapshot();
        return new Snapshot(userSnap.id(), userSnap, phoneNumber, hashedPin);
    }

    public record Snapshot(
            Id customerId,
            User.Snapshot user,
            PhoneNumber phoneNumber,
            String hashedPin
    ) {}
}