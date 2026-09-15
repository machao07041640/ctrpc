package com.ctrpc.rpc.governance;

import java.util.concurrent.Callable;

public final class RetryExecutor {
    private RetryExecutor() {}
    public static <T> T execute(Callable<T> call, int attempts) throws Exception {
        Exception last=null;
        for(int i=0;i<Math.max(1, attempts);i++){
            try { return call.call(); } catch(Exception e){ last=e; }
        }
        throw last;
    }
}
