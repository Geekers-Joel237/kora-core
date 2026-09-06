package com.geekersjoel237.koracore.shared.application.transaction;


import java.util.function.Supplier;


public final class RetryPolicy {

    private final int maxAttempts;
    private final long baseBackoffMillis;

    public RetryPolicy(int maxAttempts, long baseBackoffMillis) {
        if (maxAttempts < 1)
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        this.maxAttempts = maxAttempts;
        this.baseBackoffMillis = baseBackoffMillis;
    }

    /**
     * Five attempts, 50 ms of backoff per attempt plus jitter.
     */
    public static RetryPolicy defaults() {
        return new RetryPolicy(5, 50L);
    }

    public <T> T execute(Supplier<T> work) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return work.get();
            } catch (ConcurrentUpdateException e) {
                if (attempt == maxAttempts)
                    throw new RetryExhaustedException(
                            "Concurrent update failed after " + maxAttempts + " attempts", e);
                backoff(attempt);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * Jittered so that two transactions that collided do not collide again on the
     * same schedule.
     */
    private void backoff(int attempt) {
        long base = baseBackoffMillis * attempt;
        if (base == 0) return;
        try {
            Thread.sleep(base + (long) (base * 0.5 * Math.random()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryExhaustedException("Interrupted during retry", e);
        }
    }
}
