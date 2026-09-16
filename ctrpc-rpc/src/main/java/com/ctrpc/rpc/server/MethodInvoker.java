package com.ctrpc.rpc.server;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Cached method invoker backed by a MethodHandle. The MethodHandle is created
 * once during service registration so request processing does not repeatedly
 * resolve reflective access.
 */
public final class MethodInvoker {
    private final Object bean;
    private final Method method;
    private final MethodHandle handle;

    public MethodInvoker(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
        try {
            method.setAccessible(true);
            this.handle = MethodHandles.lookup().unreflect(method).bindTo(bean);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot create MethodHandle for " + method, e);
        }
    }

    public Object invoke(Object[] args) throws Exception {
        try {
            return handle.invokeWithArguments(args == null ? new Object[0] : args);
        } catch (Throwable t) {
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new Exception("RPC method invocation failed: " + method, t);
        }
    }
}
