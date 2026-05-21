package com.geekersjoel237.koracore.infrastructure.provider;

import com.geekersjoel237.koracore.domain.exception.ProviderException;
import com.geekersjoel237.koracore.domain.port.ProviderPort;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.domain.vo.ReverseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Configurable simulation adapter for the Mobile Money provider.
 * Behavior and latency are controlled by {@code kora.provider.*} properties,
 * so E2E and k6 tests can exercise failure paths without any mocking.
 *
 * <p>Replace with a real HTTP client in a later roadmap stage.
 *
 * <h3>Behaviors</h3>
 * <ul>
 *   <li>{@code SUCCESS}          — every call succeeds (default)</li>
 *   <li>{@code SLOW}             — succeeds but with 2× base latency</li>
 *   <li>{@code FAIL_ON_AUTHORIZE}— throws {@link ProviderException} on authorize, credit, debit, send</li>
 *   <li>{@code FAIL_ON_CAPTURE}  — authorizes OK, throws during capture</li>
 *   <li>{@code TIMEOUT}          — simulates a network timeout on every call</li>
 * </ul>
 */
@Component
public class MobileMoneyProviderAdapter implements ProviderPort {

    private static final Logger log = LoggerFactory.getLogger(MobileMoneyProviderAdapter.class);
    private final ProviderBehavior behavior;
    private final boolean simulateLatency;
    private final long authorizeMs;
    private final long captureMs;
    private final long timeoutThresholdMs;
    public MobileMoneyProviderAdapter(
            @Value("${kora.provider.behavior:SUCCESS}") String behavior,
            @Value("${kora.provider.simulate-latency:true}") boolean simulateLatency,
            @Value("${kora.provider.latency.authorize-ms:200}") long authorizeMs,
            @Value("${kora.provider.latency.capture-ms:150}") long captureMs,
            @Value("${kora.provider.latency.timeout-threshold-ms:3000}") long timeoutThresholdMs) {
        this.behavior = ProviderBehavior.valueOf(behavior.toUpperCase());
        this.simulateLatency = simulateLatency;
        this.authorizeMs = authorizeMs;
        this.captureMs = captureMs;
        this.timeoutThresholdMs = timeoutThresholdMs;
        log.info("MobileMoneyProviderAdapter initialized — behavior={}, simulateLatency={}",
                this.behavior, simulateLatency);
    }

    @Override
    public AuthorizationResult authorize(Amount amount, String paymentMethod, String correlationId) {
        log.debug("authorize — amount={} method={} correlationId={}",
                amount.value(), paymentMethod, correlationId);
        checkBehavior(Checkpoint.AUTHORIZE);
        simulateLatency(authorizeMs);
        return new AuthorizationResult(
                UUID.randomUUID().toString(),
                Instant.now().plus(15, ChronoUnit.MINUTES),
                true);
    }

    @Override
    public CaptureResult capture(String authorizationReference, String correlationId) {
        log.debug("capture — authRef={} correlationId={}", authorizationReference, correlationId);
        checkBehavior(Checkpoint.CAPTURE);
        simulateLatency(captureMs);
        return new CaptureResult(UUID.randomUUID().toString(), true);
    }


    // ── Authorize ─────────────────────────────────────────────────────────────

    @Override
    public ReverseResult reverse(String reference, Amount amount, String correlationId) {
        log.debug("reverse — ref={} amount={} correlationId={}", reference, amount.value(), correlationId);
        checkBehavior(Checkpoint.REVERSE);
        simulateLatency(captureMs);
        return new ReverseResult(UUID.randomUUID().toString(), true);
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    private void checkBehavior(Checkpoint checkpoint) {
        switch (behavior) {
            case FAIL_ON_AUTHORIZE -> {
                if (checkpoint == Checkpoint.AUTHORIZE
                        || checkpoint == Checkpoint.CREDIT
                        || checkpoint == Checkpoint.DEBIT
                        || checkpoint == Checkpoint.SEND) {
                    log.warn("Provider simulation: FAIL_ON_AUTHORIZE — checkpoint={}", checkpoint);
                    throw new ProviderException(
                            "Provider refused operation — account blocked or regulatory limit exceeded");
                }
            }
            case FAIL_ON_CAPTURE -> {
                if (checkpoint == Checkpoint.CAPTURE) {
                    log.warn("Provider simulation: FAIL_ON_CAPTURE — checkpoint={}", checkpoint);
                    throw new ProviderException(
                            "Provider capture failed — post-authorization incident");
                }
            }
            case TIMEOUT -> {
                log.warn("Provider simulation: TIMEOUT — checkpoint={}", checkpoint);
                simulateLatency(timeoutThresholdMs + 500);
                throw new ProviderException(
                        "Provider timeout — no response after " + timeoutThresholdMs + "ms");
            }
            default -> { /* SUCCESS or SLOW — no error */ }
        }
    }

    // ── Reverse ───────────────────────────────────────────────────────────────

    private void simulateLatency(long baseMs) {
        if (!simulateLatency) return;
        long jitter = (long) (baseMs * 0.4 * Math.random());
        long sleepMs = baseMs + jitter;
        if (behavior == ProviderBehavior.SLOW) sleepMs *= 2;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    public enum ProviderBehavior {
        SUCCESS,
        SLOW,
        FAIL_ON_AUTHORIZE,
        FAIL_ON_CAPTURE,
        TIMEOUT
    }

    private enum Checkpoint {AUTHORIZE, CAPTURE, REVERSE, CREDIT, DEBIT, SEND}
}