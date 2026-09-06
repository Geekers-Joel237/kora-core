package com.geekersjoel237.koracore.payment.adapters.in.scheduler;

import com.geekersjoel237.koracore.payment.application.command.ExpireAuthorizationsCommand;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;


@Component
public class AuthorizationTtlExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationTtlExpiryJob.class);

    private final CommandBus bus;

    public AuthorizationTtlExpiryJob(CommandBus bus) {
        this.bus = bus;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleAuthorizations() {
        Instant now = Instant.now();
        log.info("TTL expiry job running at {}", now);
        // A fresh id per run: two sweeps are two events, never a replay of one.
        bus.dispatch(new ExpireAuthorizationsCommand(Id.generate(), now));
    }
}