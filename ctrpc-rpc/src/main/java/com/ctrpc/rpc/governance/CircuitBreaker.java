package com.ctrpc.rpc.governance;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small client-side circuit breaker.
 *
 * <p>The breaker never holds a monitor while the remote RPC is executing.
 * After the open timeout expires, exactly one probe is allowed through;
 * concurrent callers fail fast until that probe succeeds or fails.</p>
 */
public class CircuitBreaker {
    private final int failureThreshold;
    private final long openDurationMs;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    private volatile long openUntilMs;
    private boolean halfOpenProbeInFlight;

    public CircuitBreaker() {
        this(5, 10_000);
    }

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be positive");
        if (openDurationMs <= 0) throw new IllegalArgumentException("openDurationMs must be positive");
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    public <T> T execute(Callable<T> call) throws Exception {
        boolean probe = tryAcquire();
        try {
            T value = call.call();
            onSuccess(probe);
            return value;
        } catch (Exception e) {
            onFailure(probe);
            throw e;
        }
    }

    private synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        if (openUntilMs == 0) return false;

        if (now < openUntilMs) {
            throw new IllegalStateException("circuit open");
        }

        if (halfOpenProbeInFlight) {
            throw new IllegalStateException("circuit half-open");
        }
        halfOpenProbeInFlight = true;
        return true;
    }

    private synchronized void onSuccess(boolean probe) {
        consecutiveFailures.set(0);
        openUntilMs = 0;
        if (probe) halfOpenProbeInFlight = false;
    }

    private synchronized void onFailure(boolean probe) {
        halfOpenProbeInFlight = false;
        if (probe || consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntilMs = System.currentTimeMillis() + openDurationMs;
            consecutiveFailures.set(0);
        }
    }
}
