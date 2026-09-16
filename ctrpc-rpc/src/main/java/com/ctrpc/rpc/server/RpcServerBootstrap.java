package com.ctrpc.rpc.server;

import com.ctrpc.rpc.config.RpcProperties;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** 启动本地 gRPC Server，承载通用 Invoker。 */
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
    @Order(0)
    public void start() throws IOException {
        RpcProperties.Server cfg = properties.getServer();
        NettyServerBuilder builder = NettyServerBuilder.forPort(cfg.getPort())
                .maxInboundMessageSize(cfg.getMaxInboundMessageSize())
                .addService(invoker);
        if (cfg.isKeepAlive()) {
            builder.keepAliveTime(30, TimeUnit.SECONDS).keepAliveTimeout(10, TimeUnit.SECONDS)
                    .permitKeepAliveWithoutCalls(true);
        }
        if (cfg.isTlsEnabled()) {
            if (cfg.getTlsCertChainFile() == null || cfg.getTlsPrivateKeyFile() == null) {
                throw new IllegalStateException("TLS is enabled but certificate/key files are not configured");
            }
            try {
                io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder sslBuilder =
                        GrpcSslContexts.forServer(new File(cfg.getTlsCertChainFile()), new File(cfg.getTlsPrivateKeyFile()));
                if (cfg.isRequireClientAuth()) {
                    if (cfg.getTlsTrustCertCollectionFile() == null) {
                        throw new IllegalStateException("mTLS requires ctrpc.rpc.server.tls-trust-cert-collection-file");
                    }
                    sslBuilder.trustManager(new File(cfg.getTlsTrustCertCollectionFile()));
                    sslBuilder.clientAuth(io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth.REQUIRE);
                }
                builder.sslContext(sslBuilder.build());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure RPC TLS", e);
            }
        }
        server = builder.build().start();
        log.info("CTRPC gRPC server started on port {} tls={}", cfg.getPort(), cfg.isTlsEnabled());
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown();
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) server.shutdownNow();
            log.info("CTRPC gRPC server stopped");
        }
    }
}
