package com.ctrpc.rpc.governance;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerTest {
    @Test
    void opensAfterConsecutiveFailures() {
        CircuitBreaker breaker = new CircuitBreaker(3, 1_000);
        AtomicInteger calls = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThrows(Exception.class, () -> breaker.execute(() -> {
                calls.incrementAndGet();
                throw new RuntimeException("down");
            }));
        }

        assertThrows(IllegalStateException.class, () -> breaker.execute(() -> {
            calls.incrementAndGet();
            return "unexpected";
        }));
        assertEquals(3, calls.get());
    }

    @Test
    void allowsOneProbeAfterOpenTimeout() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(1, 20);

        assertThrows(Exception.class, () -> breaker.execute(() -> {
            throw new RuntimeException("down");
        }));
        assertThrows(IllegalStateException.class, () -> breaker.execute(() -> "blocked"));

        Thread.sleep(30);
        assertEquals("ok", breaker.execute(() -> "ok"));
        assertEquals("ok", breaker.execute(() -> "ok"));
    }
}
