package com.ctrpc.rpc.client;

import com.ctrpc.proto.invoke.CtrpcInvokerGrpc;
import com.ctrpc.proto.invoke.InvokeRequest;
import com.ctrpc.proto.invoke.InvokeResponse;
import com.ctrpc.rpc.config.RpcProperties;
import com.ctrpc.rpc.exception.RpcException;
import com.ctrpc.rpc.serialize.RpcSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JDK 动态代理：把 iface 方法调用转成通用 gRPC Invoke。
 */
public class RpcInvocationHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(RpcInvocationHandler.class);

    private final String serviceName;
    private final Class<?> interfaceClass;
    private final long timeoutMs;
    private final RpcChannelManager channelManager;
    private final RpcSerializer serializer;
    private final RpcProperties properties;

    public RpcInvocationHandler(String serviceName,
                                Class<?> interfaceClass,
                                long timeoutMs,
                                RpcChannelManager channelManager,
                                RpcSerializer serializer,
                                RpcProperties properties) {
        this.serviceName = serviceName;
        this.interfaceClass = interfaceClass;
        this.timeoutMs = timeoutMs;
        this.channelManager = channelManager;
        this.serializer = serializer;
        this.properties = properties;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "toString":
                    return "RpcProxy(" + interfaceClass.getName() + " -> " + serviceName + ")";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }

        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        if (traceId == null || StringUtils.isEmpty(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put("traceId", traceId);
        }

        String parameterTypes = RpcSerializer.parameterTypesKey(method.getParameterTypes());
        InvokeRequest request = InvokeRequest.newBuilder()
                .setInterfaceName(interfaceClass.getName())
                .setMethodName(method.getName())
                .setParameterTypes(parameterTypes)
                .setArgsJson(serializer.writeArgs(args))
                .setTraceId(traceId)
                .build();

        long deadline = resolveTimeoutMs();
        try {
            CtrpcInvokerGrpc.CtrpcInvokerBlockingStub stub = CtrpcInvokerGrpc.newBlockingStub(channelManager.getChannel(serviceName))
                    .withDeadlineAfter(deadline, TimeUnit.MILLISECONDS);

            InvokeResponse response = stub.invoke(request);
            if (response.getCode() != 0) {
                throw new RpcException(response.getCode(), response.getMessage());
            }
            Object result = serializer.readResult(response.getDataJson(), method);
            log.info("RPC client ok service={} iface={} method={} elapsedMs={}",
                    serviceName, interfaceClass.getSimpleName(), method.getName(), System.currentTimeMillis() - start);
            return result;
        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            log.error("RPC client failed service={} iface={} method={}",
                    serviceName, interfaceClass.getName(), method.getName(), e);
            throw new RpcException(500, "RPC call failed: " + e.getMessage(), e);
        }
    }

    private long resolveTimeoutMs() {
        if (timeoutMs > 0) {
            return timeoutMs;
        }
        RpcProperties.ServiceDependency dep = properties.getDependencies().get(serviceName);
        if (dep != null && dep.getTimeout() != null) {
            return dep.getTimeout().toMillis();
        }
        return properties.getClient().getDefaultTimeout().toMillis();
    }
}
