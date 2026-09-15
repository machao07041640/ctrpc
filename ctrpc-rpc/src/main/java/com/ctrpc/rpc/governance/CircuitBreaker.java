package com.ctrpc.rpc.governance;

import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreaker {
    private final AtomicInteger failures=new AtomicInteger();
    private volatile long openUntil=0;
    public synchronized <T> T execute(java.util.concurrent.Callable<T> call) throws Exception {
        if(System.currentTimeMillis()<openUntil) throw new IllegalStateException("circuit open");
        try { T v=call.call(); failures.set(0); return v; }
        catch(Exception e){ if(failures.incrementAndGet()>=5) openUntil=System.currentTimeMillis()+10000; throw e; }
    }
}
