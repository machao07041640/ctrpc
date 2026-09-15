package com.ctrpc.rpc.serialize;

import java.lang.reflect.Method;

/**
 * RPC payload codec abstraction.
 *
 * <p>The transport layer must not depend on a concrete serialization library.
 * Implementations are responsible only for encoding/decoding RPC arguments
 * and return values.</p>
 */
public interface RpcCodec {
    String encodeArgs(Object[] args);
    Object[] decodeArgs(String payload, Class<?>[] parameterTypes);
    String encodeResult(Object result);
    Object decodeResult(String payload, Method method);
}
