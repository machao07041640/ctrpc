package com.ctrpc.rpc.integration;

import com.ctrpc.proto.invoke.CtrpcInvokerGrpc;
import com.ctrpc.proto.invoke.InvokeRequest;
import com.ctrpc.proto.invoke.InvokeResponse;
import com.ctrpc.rpc.metrics.RpcMetrics;
import com.ctrpc.rpc.serialize.Fastjson2RpcCodec;
import com.ctrpc.rpc.server.GenericRpcInvoker;
import com.ctrpc.rpc.server.RpcServiceRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RpcInProcessIntegrationTest {
    private Server server;
    private ManagedChannel channel;
    private ExecutorService executor;

    public interface GreetingService {
        String hello(String name);
    }

    public static class GreetingServiceImpl implements GreetingService {
        @Override public String hello(String name) { return "hello " + name; }
    }

    @BeforeEach
    void setUp() throws Exception {
        String serverName = "ctrpc-test-" + System.nanoTime();
        RpcServiceRegistry registry = new RpcServiceRegistry();
        registry.register(GreetingService.class, new GreetingServiceImpl());
        executor = Executors.newFixedThreadPool(2);
        GenericRpcInvoker invoker = new GenericRpcInvoker(registry, new Fastjson2RpcCodec(), executor,
                new RpcMetrics(new SimpleMeterRegistry()));
        server = InProcessServerBuilder.forName(serverName).directExecutor().addService(invoker).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void shouldInvokeRpcThroughRealGrpcStub() {
        InvokeRequest request = InvokeRequest.newBuilder()
                .setInterfaceName(GreetingService.class.getName())
                .setMethodName("hello")
                .setParameterTypes(String.class.getName())
                .setArgsJson("[\"world\"]")
                .setTraceId("integration-test")
                .build();

        InvokeResponse response = CtrpcInvokerGrpc.newBlockingStub(channel).invoke(request);

        assertEquals(0, response.getCode());
        assertEquals("\"hello world\"", response.getDataJson());
    }
}
