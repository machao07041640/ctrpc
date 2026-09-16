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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 通用 gRPC Invoker：请求投递到独立有界业务线程池，再分发到本地 @RpcService 实现。 */
public class GenericRpcInvoker extends CtrpcInvokerGrpc.CtrpcInvokerImplBase {
    private static final Logger log = LoggerFactory.getLogger(GenericRpcInvoker.class);
    private final RpcServiceRegistry registry;
    private final RpcCodec serializer;
    private final Executor businessExecutor;
    private final RpcMetrics metrics;

    public GenericRpcInvoker(RpcServiceRegistry registry, RpcCodec serializer,
                             Executor businessExecutor, RpcMetrics metrics) {
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
            businessExecutor.execute(() -> invokeOnBusinessThread(request, responseObserver, traceId, rpcIface, rpcMethod));
        } catch (RejectedExecutionException e) {
            log.warn("RPC business executor saturated iface={} method={}", rpcIface, rpcMethod);
            metrics.increment("server", rpcIface, rpcMethod, "rejected");
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("RPC business executor is saturated")
                    .asRuntimeException());
        }
    }

    private void invokeOnBusinessThread(InvokeRequest request,
                                        StreamObserver<InvokeResponse> responseObserver,
                                        String traceId,
                                        String rpcIface,
                                        String rpcMethod) {
        Timer.Sample sample = metrics.start();
        long start = System.currentTimeMillis();
        boolean success = false;
        if (traceId != null && !StringUtils.isEmpty(traceId)) MDC.put("traceId", traceId);
        MDC.put("rpcIface", rpcIface);
        MDC.put("rpcMethod", rpcMethod);
        try {
            RpcMethodHandler handler = registry.lookup(rpcIface, rpcMethod, request.getParameterTypes());
            Object[] args = serializer.decodeArgs(request.getArgsJson(), handler.getParameterTypes());
            Object result = handler.invoke(args);
            responseObserver.onNext(InvokeResponse.newBuilder().setCode(0).setMessage("OK")
                    .setDataJson(serializer.encodeResult(result)).build());
            responseObserver.onCompleted();
            success = true;
            log.info("RPC invoke ok iface={} method={} elapsedMs={}", rpcIface, rpcMethod,
                    System.currentTimeMillis() - start);
        } catch (RpcException e) {
            log.warn("RPC invoke business error iface={} method={} code={} msg={}", rpcIface, rpcMethod,
                    e.getCode(), e.getMessage());
            responseObserver.onNext(InvokeResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("RPC invoke failed iface={} method={}", rpcIface, rpcMethod, cause);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("RPC invocation failed")
                    .withCause(cause)
                    .asRuntimeException());
        } finally {
            metrics.recordServer(sample, rpcIface, rpcMethod, success);
            MDC.remove("traceId");
            MDC.remove("rpcIface");
            MDC.remove("rpcMethod");
        }
    }
}
