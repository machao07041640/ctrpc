package com.ctrpc.rpc.governance;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryExecutorTest {
    @Test
    void retriesUnavailableAndEventuallySucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String value = RetryExecutor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw Status.UNAVAILABLE.withDescription("temporary").asRuntimeException();
            }
            return "ok";
        }, 3);

        assertEquals("ok", value);
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryInvalidArgument() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(StatusRuntimeException.class, () -> RetryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        }, 3));
        assertEquals(1, attempts.get());
    }
}
