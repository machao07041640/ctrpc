package com.ctrpc.rpc.client;

import com.ctrpc.proto.invoke.CtrpcInvokerGrpc;
import com.ctrpc.proto.invoke.InvokeRequest;
import com.ctrpc.proto.invoke.InvokeResponse;
import com.ctrpc.rpc.config.RpcProperties;
import com.ctrpc.rpc.exception.RpcException;
import com.ctrpc.rpc.exception.RpcTransportException;
import com.ctrpc.rpc.serialize.Fastjson2RpcCodec;
import com.ctrpc.rpc.serialize.RpcCodec;
import com.ctrpc.rpc.registry.ServiceRegistry;
import com.ctrpc.rpc.loadbalance.LoadBalancer;
import com.ctrpc.rpc.governance.RetryExecutor;
import com.ctrpc.rpc.governance.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** JDK 动态代理：把 iface 方法调用转成通用 gRPC Invoke。 */
public class RpcInvocationHandler implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(RpcInvocationHandler.class);
    private final String serviceName;
    private final Class<?> interfaceClass;
    private final long timeoutMs;
    private final RpcChannelManager channelManager;
    private final RpcCodec serializer;
    private final RpcProperties properties;
    private final ServiceRegistry registry;
    private final LoadBalancer loadBalancer;
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    public RpcInvocationHandler(String serviceName, Class<?> interfaceClass, long timeoutMs,
                                RpcChannelManager channelManager, RpcCodec serializer, RpcProperties properties, ServiceRegistry registry, LoadBalancer loadBalancer) {
        this.serviceName = serviceName;
        this.interfaceClass = interfaceClass;
        this.timeoutMs = timeoutMs;
        this.channelManager = channelManager;
        this.serializer = serializer;
        this.properties = properties;
        this.registry = registry;
        this.loadBalancer = loadBalancer;
    }

    public RpcInvocationHandler(String serviceName, Class<?> interfaceClass, long timeoutMs,
                                RpcChannelManager channelManager, RpcCodec serializer, RpcProperties properties) {
        this(serviceName, interfaceClass, timeoutMs, channelManager, serializer, properties, null, null);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "toString": return "RpcProxy(" + interfaceClass.getName() + " -> " + serviceName + ")";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: throw new UnsupportedOperationException(method.getName());
            }
        }

        long start = System.currentTimeMillis();
        String previousTraceId = MDC.get("traceId");
        String traceId = previousTraceId;
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put("traceId", traceId);
        }

        long deadline = resolveTimeoutMs();
        try {
            String parameterTypes = Fastjson2RpcCodec.parameterTypesKey(method.getParameterTypes());
            InvokeRequest request = InvokeRequest.newBuilder()
                    .setInterfaceName(interfaceClass.getName())
                    .setMethodName(method.getName())
                    .setParameterTypes(parameterTypes)
                    .setArgsJson(serializer.encodeArgs(args))
                    .setTraceId(traceId)
                    .build();

            String targetService = serviceName;
            if (registry != null && loadBalancer != null) {
                targetService = loadBalancer.select(registry.discover(serviceName)).getAddress();
            }
            CtrpcInvokerGrpc.CtrpcInvokerBlockingStub stub =
                    CtrpcInvokerGrpc.newBlockingStub(channelManager.getChannel(targetService))
                            .withDeadlineAfter(deadline, TimeUnit.MILLISECONDS);
            InvokeResponse response = circuitBreaker.execute(() ->
                    RetryExecutor.execute(() -> stub.invoke(request), 2));
            if (response.getCode() != 0) throw new RpcException(response.getCode(), response.getMessage());
            Object result = serializer.decodeResult(response.getDataJson(), method);
            log.info("RPC client ok service={} iface={} method={} elapsedMs={}",
                    serviceName, interfaceClass.getSimpleName(), method.getName(), System.currentTimeMillis() - start);
            return result;
        } catch (RpcException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.warn("RPC transport failed service={} iface={} method={} status={}",
                    serviceName, interfaceClass.getName(), method.getName(), code, e);
            throw new RpcTransportException(code, "RPC transport failed: " + code, e);
        } catch (Exception e) {
            log.error("RPC client failed service={} iface={} method={}",
                    serviceName, interfaceClass.getName(), method.getName(), e);
            throw new RpcTransportException(Status.Code.UNKNOWN, "RPC call failed", e);
        } finally {
            if (previousTraceId == null) MDC.remove("traceId");
            else MDC.put("traceId", previousTraceId);
        }
    }

    private long resolveTimeoutMs() {
        if (timeoutMs > 0) return timeoutMs;
        RpcProperties.ServiceDependency dep = properties.getDependencies().get(serviceName);
        if (dep != null && dep.getTimeout() != null) return dep.getTimeout().toMillis();
        return properties.getClient().getDefaultTimeout().toMillis();
    }
}
