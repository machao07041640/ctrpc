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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 启动本地 gRPC Server，承载通用 Invoker。 */
public class RpcServerBootstrap {
    private static final Logger log = LoggerFactory.getLogger(RpcServerBootstrap.class);
    private final RpcProperties properties;
    private final GenericRpcInvoker invoker;
    private Server server;
    private ExecutorService executor;
    public RpcServerBootstrap(RpcProperties properties, GenericRpcInvoker invoker) {
        this.properties = properties;
        this.invoker = invoker;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        RpcProperties.Server cfg = properties.getServer();
        validateExecutorConfig(cfg);
        executor = new ThreadPoolExecutor(cfg.getExecutorCoreThreads(), cfg.getExecutorMaxThreads(),
                cfg.getExecutorKeepAliveSeconds(), TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(cfg.getExecutorQueueCapacity()), new RpcThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        NettyServerBuilder builder = NettyServerBuilder.forPort(cfg.getPort())
                .maxInboundMessageSize(cfg.getMaxInboundMessageSize())
                .executor(executor).addService(invoker);
        if (cfg.isKeepAlive()) {
            builder.keepAliveTime(30, TimeUnit.SECONDS).keepAliveTimeout(10, TimeUnit.SECONDS)
                    .permitKeepAliveWithoutCalls(true);
        }
        server = builder.build().start();
        log.info("CTRPC gRPC server started on port {}", cfg.getPort());
    }
    private static void validateExecutorConfig(RpcProperties.Server cfg) {
        if (cfg.getExecutorCoreThreads() <= 0 || cfg.getExecutorMaxThreads() < cfg.getExecutorCoreThreads()
                || cfg.getExecutorQueueCapacity() <= 0 || cfg.getExecutorKeepAliveSeconds() < 0) {
            throw new IllegalArgumentException("Invalid ctrpc.rpc.server executor configuration");
        }
    }
    private static final class RpcThreadFactory implements ThreadFactory {
        private final AtomicInteger index = new AtomicInteger(1);
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ctrpc-rpc-" + index.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown();
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) server.shutdownNow();
            log.info("CTRPC gRPC server stopped");
        }
        if (executor != null) {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        }
    }
}
