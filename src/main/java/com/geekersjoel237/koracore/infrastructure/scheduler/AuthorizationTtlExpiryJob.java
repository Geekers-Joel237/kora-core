package com.geekersjoel237.koracore.infrastructure.scheduler;

import com.geekersjoel237.koracore.application.service.PaymentTransactionalExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scans for expired ACTIVE authorization records every 60 seconds and
 * transitions the owning transaction to AUTHORIZATION_FAILED.
 *
 * <p>Prevents authorization leaks where a transaction is stuck in AUTHORIZED
 * indefinitely, blocking the customer's available balance.
 */
@Component
public class AuthorizationTtlExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationTtlExpiryJob.class);

    private final PaymentTransactionalExecutor executor;

    public AuthorizationTtlExpiryJob(PaymentTransactionalExecutor executor) {
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleAuthorizations() {
        Instant now = Instant.now();
        log.info("TTL expiry job running at {}", now);
        executor.executeExpireAuthorizations(now);
    }
}