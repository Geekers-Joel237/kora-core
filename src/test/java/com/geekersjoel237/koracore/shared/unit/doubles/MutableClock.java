package com.geekersjoel237.koracore.shared.unit.doubles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock a test can push forward.
 *
 * <p>Anything with a lifetime needs one. The alternative is two objects holding two
 * fixed clocks and disagreeing about the present, which is how the old OTP store double
 * ended up checking expiry against {@code Clock.systemUTC()} while production checked
 * it against the injected one — a double that answered differently from the thing it
 * stood in for.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant now;

    private MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public static MutableClock at(Instant instant) {
        return new MutableClock(instant, ZoneId.of("UTC"));
    }

    public void advance(Duration amount) {
        now = now.plus(amount);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new MutableClock(now, otherZone);
    }
}
