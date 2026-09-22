package com.ctrpc.rpc.client;

import com.ctrpc.proto.invoke.CtrpcInvokerGrpc;
import com.ctrpc.proto.invoke.InvokeRequest;
import com.ctrpc.proto.invoke.InvokeResponse;
import com.ctrpc.rpc.config.RpcProperties;
import com.ctrpc.rpc.exception.RemoteRpcException;
import com.ctrpc.rpc.exception.RpcException;
import com.ctrpc.rpc.exception.RpcTransportException;
import com.ctrpc.rpc.serialize.Fastjson2RpcCodec;
import com.ctrpc.rpc.serialize.RpcCodec;
import com.ctrpc.rpc.registry.ServiceMeta;
import com.ctrpc.rpc.registry.ServiceRegistry;
import com.ctrpc.rpc.loadbalance.LoadBalancer;
import com.ctrpc.rpc.governance.CircuitBreaker;
import com.ctrpc.rpc.metrics.RpcMetrics;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
    private final RpcMetrics metrics;
    private final Executor asyncExecutor;
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public RpcInvocationHandler(String serviceName, Class<?> interfaceClass, long timeoutMs,
                                RpcChannelManager channelManager, RpcCodec serializer, RpcProperties properties,
                                ServiceRegistry registry, LoadBalancer loadBalancer, RpcMetrics metrics,
                                Executor asyncExecutor) {
        this.serviceName = serviceName;
        this.interfaceClass = interfaceClass;
        this.timeoutMs = timeoutMs;
        this.channelManager = channelManager;
        this.serializer = serializer;
        this.properties = properties;
        this.registry = registry;
        this.loadBalancer = loadBalancer;
        this.metrics = metrics;
        this.asyncExecutor = asyncExecutor;
    }

    public RpcInvocationHandler(String serviceName, Class<?> interfaceClass, long timeoutMs,
                                RpcChannelManager channelManager, RpcCodec serializer, RpcProperties properties,
                                ServiceRegistry registry, LoadBalancer loadBalancer) {
        this(serviceName, interfaceClass, timeoutMs, channelManager, serializer, properties,
                registry, loadBalancer, new RpcMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                Runnable::run);
    }

    public RpcInvocationHandler(String serviceName, Class<?> interfaceClass, long timeoutMs,
                                RpcChannelManager channelManager, RpcCodec serializer, RpcProperties properties) {
        this(serviceName, interfaceClass, timeoutMs, channelManager, serializer, properties,
                null, null);
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
        if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) return invokeAsync(method, args);
        return invokeSync(method, args);
    }

    private Object invokeSync(Method method, Object[] args) throws Throwable {
        long start = System.currentTimeMillis();
        Timer.Sample sample = metrics.start();
        String previousTraceId = MDC.get("traceId");
        String traceId = previousTraceId;
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put("traceId", traceId);
        }
        long deadline = resolveTimeoutMs();
        boolean success = false;
        try {
            InvokeRequest request = buildRequest(method, args, traceId);
            ServiceMeta target = selectTarget();
            CircuitBreaker breaker = breakerFor(target);
            InvokeResponse response = breaker.execute(() -> CtrpcInvokerGrpc
                    .newBlockingStub(channelManager.getChannel(serviceName, target.getAddress()))
                    .withDeadlineAfter(deadline, TimeUnit.MILLISECONDS)
                    .invoke(request));
            if (response.getCode() != 0) {
                throw toRemoteException(response);
            }
            Object result = serializer.decodeResult(response.getDataJson(), method);
            success = true;
            log.info("RPC client ok service={} iface={} method={} target={} elapsedMs={}",
                    serviceName, interfaceClass.getSimpleName(), method.getName(), target.getAddress(),
                    System.currentTimeMillis() - start);
            return result;
        } catch (RpcException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.warn("RPC transport failed service={} iface={} method={} status={}", serviceName, interfaceClass.getName(), method.getName(), code);
            throw new RpcTransportException(code, "RPC transport failed: " + code, e);
        } catch (Exception e) {
            log.error("RPC client failed service={} iface={} method={}", serviceName, interfaceClass.getName(), method.getName(), e);
            throw new RpcTransportException(Status.Code.UNKNOWN, "RPC call failed", e);
        } finally {
            metrics.recordClient(sample, serviceName, method.getName(), success);
            restoreTrace(previousTraceId);
        }
    }

    private CompletableFuture<Object> invokeAsync(Method method, Object[] args) {
        Timer.Sample sample = metrics.start();
        String previousTraceId = MDC.get("traceId");
        String traceId = previousTraceId;
        if (!StringUtils.hasText(traceId)) traceId = UUID.randomUUID().toString().replace("-", "");
        InvokeRequest request = buildRequest(method, args, traceId);
        ServiceMeta target;
        try {
            target = selectTarget();
        } catch (Exception e) {
            metrics.recordClient(sample, serviceName, method.getName(), false);
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        CircuitBreaker breaker = breakerFor(target);
        try {
            breaker.tryAcquireForAsync();
        } catch (Exception e) {
            metrics.recordClient(sample, serviceName, method.getName(), false);
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        long deadline = resolveTimeoutMs();
        ListenableFuture<InvokeResponse> grpcFuture = CtrpcInvokerGrpc
                .newFutureStub(channelManager.getChannel(serviceName, target.getAddress()))
                .withDeadlineAfter(deadline, TimeUnit.MILLISECONDS)
                .invoke(request);
        CompletableFuture<Object> result = new CompletableFuture<>();
        Futures.addCallback(grpcFuture, new com.google.common.util.concurrent.FutureCallback<InvokeResponse>() {
            @Override public void onSuccess(InvokeResponse response) {
                breaker.onAsyncSuccess();
                try {
                    if (response.getCode() != 0) {
                        throw toRemoteException(response);
                    }
                    result.complete(serializer.decodeResult(response.getDataJson(), method));
                    metrics.recordClient(sample, serviceName, method.getName(), true);
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                    metrics.recordClient(sample, serviceName, method.getName(), false);
                } finally { restoreTrace(previousTraceId); }
            }

            @Override public void onFailure(Throwable t) {
                breaker.onAsyncFailure();
                Throwable cause = t instanceof StatusRuntimeException
                        ? new RpcTransportException(((StatusRuntimeException) t).getStatus().getCode(), "RPC transport failed", t) : t;
                result.completeExceptionally(cause);
                metrics.recordClient(sample, serviceName, method.getName(), false);
                restoreTrace(previousTraceId);
            }
        }, asyncExecutor);
        return result;
    }

    private RuntimeException toRemoteException(InvokeResponse response) {
        if (StringUtils.hasText(response.getExceptionClass())) {
            return new RemoteRpcException(
                    response.getCode(),
                    response.getExceptionClass(),
                    response.getExceptionMessage(),
                    response.getExceptionStackTrace(),
                    response.getExceptionDeclared());
        }
        return new RpcException(response.getCode(), response.getMessage());
    }

    private InvokeRequest buildRequest(Method method, Object[] args, String traceId) {
        String parameterTypes = Fastjson2RpcCodec.parameterTypesKey(method.getParameterTypes());
        return InvokeRequest.newBuilder().setInterfaceName(interfaceClass.getName()).setMethodName(method.getName())
                .setParameterTypes(parameterTypes).setArgsJson(serializer.encodeArgs(args)).setTraceId(traceId).build();
    }

    private ServiceMeta selectTarget() {
        if (registry == null || loadBalancer == null) {
            RpcProperties.ServiceDependency dep = properties.getDependencies().get(serviceName);
            if (dep == null || !StringUtils.hasText(dep.getAddress())) throw new IllegalStateException("No RPC dependency address for service: " + serviceName);
            return new ServiceMeta(serviceName, dep.getAddress());
        }
        List<ServiceMeta> instances = registry.discover(serviceName);
        if (instances == null || instances.isEmpty()) throw new IllegalStateException("No available RPC instances for service: " + serviceName);
        ServiceMeta target = loadBalancer.select(instances);
        if (target == null || !StringUtils.hasText(target.getAddress())) throw new IllegalStateException("Load balancer returned an invalid RPC instance for service: " + serviceName);
        return target;
    }

    private CircuitBreaker breakerFor(ServiceMeta target) {
        return circuitBreakers.computeIfAbsent(target.getAddress(), key -> new CircuitBreaker());
    }

    private long resolveTimeoutMs() {
        if (timeoutMs > 0) return timeoutMs;
        RpcProperties.ServiceDependency dep = properties.getDependencies().get(serviceName);
        if (dep != null && dep.getTimeout() != null) return dep.getTimeout().toMillis();
        return properties.getClient().getDefaultTimeout().toMillis();
    }

    private void restoreTrace(String previousTraceId) {
        if (previousTraceId == null) MDC.remove("traceId");
        else MDC.put("traceId", previousTraceId);
    }
}
