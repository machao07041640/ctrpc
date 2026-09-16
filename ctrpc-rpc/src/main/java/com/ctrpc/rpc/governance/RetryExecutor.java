package com.ctrpc.rpc.governance;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Small synchronous retry primitive used by the RPC client. */
public final class RetryExecutor {
    private RetryExecutor() {}

    public static <T> T execute(Callable<T> call, int attempts) throws Exception {
        return execute(call, attempts, RetryExecutor::isRetryable);
    }

    public static <T> T execute(Callable<T> call, int attempts, Predicate<Exception> retryable) throws Exception {
        int maxAttempts = Math.max(1, Math.min(attempts, 3));
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.call();
            } catch (Exception e) {
                last = e;
                if (attempt >= maxAttempts || !retryable.test(e)) throw e;
                sleepBackoff(attempt);
            }
        }
        throw last;
    }

    private static boolean isRetryable(Exception e) {
        if (!(e instanceof StatusRuntimeException)) return false;
        Status.Code code = ((StatusRuntimeException) e).getStatus().getCode();
        return code == Status.Code.UNAVAILABLE;
    }

    private static void sleepBackoff(int attempt) throws InterruptedException {
        long base = Math.min(500L, 50L * (1L << Math.max(0, attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, base / 5));
        Thread.sleep(base - base / 10 + jitter);
    }
}
