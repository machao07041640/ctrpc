package com.ctrpc.rpc.server;

import com.ctrpc.proto.invoke.CtrpcInvokerGrpc;
import com.ctrpc.proto.invoke.InvokeRequest;
import com.ctrpc.proto.invoke.InvokeResponse;
import com.ctrpc.rpc.exception.RpcException;
import com.ctrpc.rpc.metrics.RpcMetrics;
import com.ctrpc.rpc.serialize.RpcCodec;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 通用 gRPC Invoker：请求投递到独立有界业务线程池，再分发到本地 @RpcService 实现。 */
public class GenericRpcInvoker extends CtrpcInvokerGrpc.CtrpcInvokerImplBase {

    private static final Logger log = LoggerFactory.getLogger(GenericRpcInvoker.class);

    private final RpcServiceRegistry registry;
    private final RpcCodec serializer;
    private final Executor businessExecutor;
    private final RpcMetrics metrics;

    public GenericRpcInvoker(
            RpcServiceRegistry registry,
            RpcCodec serializer,
            Executor businessExecutor,
            RpcMetrics metrics) {
        this.registry = registry;
        this.serializer = serializer;
        this.businessExecutor = businessExecutor;
        this.metrics = metrics;
    }

    @Override
    public void invoke(InvokeRequest request, StreamObserver<InvokeResponse> responseObserver) {
        final String traceId = request.getTraceId();
        final String rpcIface = request.getInterfaceName();
        final String rpcMethod = request.getMethodName();
        try {
            businessExecutor.execute(
                    () -> invokeOnBusinessThread(
                            request, responseObserver, traceId, rpcIface, rpcMethod));
        } catch (RejectedExecutionException e) {
            log.warn("RPC business executor saturated iface={} method={}", rpcIface, rpcMethod);
            metrics.increment("server", rpcIface, rpcMethod, "rejected");
            responseObserver.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription("RPC business executor is saturated")
                            .asRuntimeException());
        }
    }

    private void invokeOnBusinessThread(
            InvokeRequest request,
            StreamObserver<InvokeResponse> responseObserver,
            String traceId,
            String rpcIface,
            String rpcMethod) {
        Timer.Sample sample = metrics.start();
        long start = System.currentTimeMillis();
        boolean success = false;
        if (StringUtils.hasText(traceId)) {
            MDC.put("traceId", traceId);
        }
        MDC.put("rpcIface", rpcIface);
        MDC.put("rpcMethod", rpcMethod);
        try {
            RpcMethodHandler handler =
                    registry.lookup(rpcIface, rpcMethod, request.getParameterTypes());
            Object[] args =
                    serializer.decodeArgs(request.getArgsJson(), handler.getParameterTypes());
            Object result = handler.invoke(args);
            responseObserver.onNext(
                    InvokeResponse.newBuilder()
                            .setCode(0)
                            .setMessage("OK")
                            .setDataJson(serializer.encodeResult(result))
                            .build());
            responseObserver.onCompleted();
            success = true;
            log.info(
                    "RPC invoke ok iface={} method={} elapsedMs={}",
                    rpcIface,
                    rpcMethod,
                    System.currentTimeMillis() - start);
        } catch (Throwable throwable) {
            Throwable cause = unwrap(throwable);
            RpcMethodHandler handler = findHandler(request);
            boolean declared =
                    handler != null && isDeclaredException(handler.getMethod(), cause.getClass());
            int code = cause instanceof RpcException ? ((RpcException) cause).getCode() : 500;
            String stackTrace = stackTrace(cause);

            log.error(
                    "RPC invoke failed iface={} method={} exception={} declared={}",
                    rpcIface,
                    rpcMethod,
                    cause.getClass().getName(),
                    declared,
                    cause);

            responseObserver.onNext(
                    InvokeResponse.newBuilder()
                            .setCode(code)
                            .setMessage(cause.getMessage() == null ? "" : cause.getMessage())
                            .setExceptionClass(cause.getClass().getName())
                            .setExceptionMessage(cause.getMessage() == null ? "" : cause.getMessage())
                            .setExceptionStackTrace(stackTrace)
                            .setExceptionDeclared(declared)
                            .build());
            responseObserver.onCompleted();
        } finally {
            metrics.recordServer(sample, rpcIface, rpcMethod, success);
            MDC.remove("traceId");
            MDC.remove("rpcIface");
            MDC.remove("rpcMethod");
        }
    }

    private RpcMethodHandler findHandler(InvokeRequest request) {
        try {
            return registry.lookup(
                    request.getInterfaceName(),
                    request.getMethodName(),
                    request.getParameterTypes());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isDeclaredException(Method method, Class<?> exceptionClass) {
        for (Class<?> declaredType : method.getExceptionTypes()) {
            if (declaredType.isAssignableFrom(exceptionClass)) {
                return true;
            }
        }
        return false;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.lang.reflect.InvocationTargetException
                        || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
