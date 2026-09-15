package com.ctrpc.rpc.server;

import com.ctrpc.proto.invoke.CtrpcInvokerGrpc;
import com.ctrpc.proto.invoke.InvokeRequest;
import com.ctrpc.proto.invoke.InvokeResponse;
import com.ctrpc.rpc.exception.RpcException;
import com.ctrpc.rpc.serialize.RpcSerializer;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 通用 gRPC Invoker：根据 interface + method 分发到本地 @RpcService 实现。
 */
public class GenericRpcInvoker extends CtrpcInvokerGrpc.CtrpcInvokerImplBase {

    private static final Logger log = LoggerFactory.getLogger(GenericRpcInvoker.class);

    private final RpcServiceRegistry registry;
    private final RpcSerializer serializer;

    public GenericRpcInvoker(RpcServiceRegistry registry, RpcSerializer serializer) {
        this.registry = registry;
        this.serializer = serializer;
    }

    @Override
    public void invoke(InvokeRequest request, StreamObserver<InvokeResponse> responseObserver) {
        long start = System.currentTimeMillis();
        String traceId = request.getTraceId();
        if (traceId != null && !StringUtils.isEmpty(traceId)) {
            MDC.put("traceId", traceId);
        }
        MDC.put("rpcIface", request.getInterfaceName());
        MDC.put("rpcMethod", request.getMethodName());

        try {
            RpcMethodHandler handler = registry.lookup(
                    request.getInterfaceName(),
                    request.getMethodName(),
                    request.getParameterTypes()
            );
            Object[] args = serializer.readArgs(request.getArgsJson(), handler.getParameterTypes());
            Object result = handler.invoke(args);

            InvokeResponse response = InvokeResponse.newBuilder()
                    .setCode(0)
                    .setMessage("OK")
                    .setDataJson(serializer.writeResult(result))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("RPC invoke ok iface={} method={} elapsedMs={}",
                    request.getInterfaceName(), request.getMethodName(), System.currentTimeMillis() - start);
        } catch (RpcException e) {
            log.warn("RPC invoke business error iface={} method={} code={} msg={}",
                    request.getInterfaceName(), request.getMethodName(), e.getCode(), e.getMessage());
            responseObserver.onNext(InvokeResponse.newBuilder()
                    .setCode(e.getCode())
                    .setMessage(e.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("RPC invoke failed iface={} method={}", request.getInterfaceName(), request.getMethodName(), cause);
            responseObserver.onNext(InvokeResponse.newBuilder()
                    .setCode(500)
                    .setMessage(cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage())
                    .build());
            responseObserver.onCompleted();
        } finally {
            MDC.clear();
        }
    }
}
