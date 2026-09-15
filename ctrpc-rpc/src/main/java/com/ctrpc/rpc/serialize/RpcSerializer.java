package com.ctrpc.rpc.serialize;

import java.lang.reflect.Method;

/**
 * @deprecated use {@link RpcCodec}. Kept as a source-compatible facade for 1.x users.
 */
@Deprecated
public final class RpcSerializer implements RpcCodec {
    private final Fastjson2RpcCodec delegate = new Fastjson2RpcCodec();

    @Override public String encodeArgs(Object[] args) { return delegate.encodeArgs(args); }
    @Override public Object[] decodeArgs(String payload, Class<?>[] parameterTypes) { return delegate.decodeArgs(payload, parameterTypes); }
    @Override public String encodeResult(Object result) { return delegate.encodeResult(result); }
    @Override public Object decodeResult(String payload, Method method) { return delegate.decodeResult(payload, method); }

    public String writeArgs(Object[] args) { return encodeArgs(args); }
    public Object[] readArgs(String payload, Class<?>[] parameterTypes) { return decodeArgs(payload, parameterTypes); }
    public String writeResult(Object result) { return encodeResult(result); }
    public Object readResult(String payload, Method method) { return decodeResult(payload, method); }
    public static String parameterTypesKey(Class<?>[] types) { return Fastjson2RpcCodec.parameterTypesKey(types); }
    public static Class<?>[] parseParameterTypes(String types) { return Fastjson2RpcCodec.parseParameterTypes(types); }
}
