package com.ctrpc.rpc.server;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 已注册的 RPC 方法处理器。
 */
public final class RpcMethodHandler {

    private final String interfaceName;
    private final Object bean;
    private final Method method;
    private final Class<?>[] parameterTypes;

    public RpcMethodHandler(String interfaceName, Object bean, Method method) {
        this.interfaceName = interfaceName;
        this.bean = bean;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.method.setAccessible(true);
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public Object getBean() {
        return bean;
    }

    public Method getMethod() {
        return method;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public Object invoke(Object[] args) throws Exception {
        return method.invoke(bean, args);
    }

    public String methodKey() {
        return method.getName() + "#" + joinTypes(parameterTypes);
    }

    public static String methodKey(String methodName, String parameterTypes) {
        return methodName + "#" + (parameterTypes == null ? "" : parameterTypes);
    }

    private static String joinTypes(Class<?>[] types) {
        if (types.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(types[i].getName());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RpcMethodHandler)) {
            return false;
        }
        RpcMethodHandler that = (RpcMethodHandler) o;
        return Objects.equals(interfaceName, that.interfaceName)
                && Objects.equals(methodKey(), that.methodKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(interfaceName, methodKey());
    }
}
