package com.ctrpc.rpc.exception;

public interface RpcExceptionResolver {

    RpcError resolve(Throwable throwable);
}
