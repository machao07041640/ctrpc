package com.ctrpc.rpc.server;

import com.ctrpc.rpc.config.RpcProperties;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 启动本地 gRPC Server，承载通用 Invoker。
 */
public class RpcServerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RpcServerBootstrap.class);

    private final RpcProperties properties;
    private final GenericRpcInvoker invoker;
    private Server server;

    public RpcServerBootstrap(RpcProperties properties, GenericRpcInvoker invoker) {
        this.properties = properties;
        this.invoker = invoker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        RpcProperties.Server cfg = properties.getServer();
        NettyServerBuilder builder = NettyServerBuilder.forPort(cfg.getPort())
                .maxInboundMessageSize(cfg.getMaxInboundMessageSize())
                .addService(invoker);

        if (cfg.isKeepAlive()) {
            builder.keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .permitKeepAliveWithoutCalls(true);
        }

        server = builder.build().start();
        log.info("CTRPC gRPC server started on port {}", cfg.getPort());
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            log.info("CTRPC gRPC server stopped");
        }
    }
}
