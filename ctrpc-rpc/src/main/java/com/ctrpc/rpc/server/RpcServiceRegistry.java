package com.ctrpc.rpc.server;

import com.ctrpc.rpc.annotation.RpcService;
import com.ctrpc.rpc.exception.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫描 @RpcService 实现类，按接口全路径注册方法。
 */
public class RpcServiceRegistry implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RpcServiceRegistry.class);

    /**
     * interfaceName -> (methodKey -> handler)
     */
    private final Map<String, Map<String, RpcMethodHandler>> handlers = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        RpcService rpcService = AnnotationUtils.findAnnotation(targetClass, RpcService.class);
        if (rpcService == null) {
            return bean;
        }

        Class<?> iface = resolveInterface(targetClass, rpcService);
        register(iface, bean);
        return bean;
    }

    public void register(Class<?> iface, Object bean) {
        String interfaceName = iface.getName();
        Map<String, RpcMethodHandler> methodMap = handlers.computeIfAbsent(interfaceName, k -> new ConcurrentHashMap<>());

        for (Method method : iface.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.getDeclaringClass() == Object.class) {
                continue;
            }
            RpcMethodHandler handler = new RpcMethodHandler(interfaceName, bean, method);
            RpcMethodHandler previous = methodMap.put(handler.methodKey(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate RPC method: " + interfaceName + "#" + handler.methodKey());
            }
            log.info("RPC service registered: {}.{}({})", interfaceName, method.getName(), handler.methodKey());
        }
        log.info("RPC interface exposed: {} methods={}", interfaceName, methodMap.size());
    }

    public RpcMethodHandler lookup(String interfaceName, String methodName, String parameterTypes) {
        Map<String, RpcMethodHandler> methodMap = handlers.get(interfaceName);
        if (methodMap == null) {
            throw new RpcException(404, "RPC interface not found: " + interfaceName);
        }
        String key = RpcMethodHandler.methodKey(methodName, parameterTypes);
        RpcMethodHandler handler = methodMap.get(key);
        if (handler == null) {
            throw new RpcException(404, "RPC method not found: " + interfaceName + "#" + key);
        }
        return handler;
    }

    public Map<String, Map<String, RpcMethodHandler>> getHandlers() {
        return handlers;
    }

    private Class<?> resolveInterface(Class<?> targetClass, RpcService rpcService) {
        if (rpcService.interfaceClass() != void.class && rpcService.interfaceClass() != Void.class) {
            if (!rpcService.interfaceClass().isAssignableFrom(targetClass)) {
                throw new IllegalStateException(targetClass.getName() + " does not implement " + rpcService.interfaceClass().getName());
            }
            return rpcService.interfaceClass();
        }
        Class<?>[] interfaces = targetClass.getInterfaces();
        for (Class<?> iface : interfaces) {
            String name = iface.getName();
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")
                    || name.startsWith("org.springframework.")) {
                continue;
            }
            return iface;
        }
        throw new IllegalStateException("@RpcService on " + targetClass.getName() + " requires a business interface");
    }
}
