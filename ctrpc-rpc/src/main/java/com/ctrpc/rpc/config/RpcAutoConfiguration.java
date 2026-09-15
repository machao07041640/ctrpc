package com.ctrpc.rpc.config;

import com.ctrpc.rpc.client.RpcChannelManager;
import com.ctrpc.rpc.client.RpcReferenceBeanPostProcessor;
import com.ctrpc.rpc.serialize.Fastjson2RpcCodec;
import com.ctrpc.rpc.registry.ServiceRegistry;
import com.ctrpc.rpc.registry.StaticServiceRegistry;
import com.ctrpc.rpc.loadbalance.LoadBalancer;
import com.ctrpc.rpc.loadbalance.RoundRobinLoadBalancer;
import com.ctrpc.rpc.serialize.RpcCodec;
import com.ctrpc.rpc.server.GenericRpcInvoker;
import com.ctrpc.rpc.server.RpcServerBootstrap;
import com.ctrpc.rpc.server.RpcServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfiguration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RpcAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(RpcCodec.class)
    public RpcCodec rpcCodec() { return new Fastjson2RpcCodec(); }

    @Bean
    public static RpcServiceRegistry rpcServiceRegistry() { return new RpcServiceRegistry(); }

    /**
     * Dedicated bounded executor for RPC business work. gRPC transport callbacks
     * remain on gRPC-managed threads and never run user service methods directly.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService rpcBusinessExecutor(RpcProperties properties) {
        RpcProperties.Server cfg = properties.getServer();
        validateExecutorConfig(cfg);
        return new ThreadPoolExecutor(
                cfg.getExecutorCoreThreads(),
                cfg.getExecutorMaxThreads(),
                cfg.getExecutorKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(cfg.getExecutorQueueCapacity()),
                new RpcBusinessThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public GenericRpcInvoker genericRpcInvoker(RpcServiceRegistry registry, RpcCodec serializer,
                                               ExecutorService rpcBusinessExecutor) {
        return new GenericRpcInvoker(registry, serializer, rpcBusinessExecutor);
    }

    @Bean
    public RpcServerBootstrap rpcServerBootstrap(RpcProperties properties, GenericRpcInvoker invoker) {
        return new RpcServerBootstrap(properties, invoker);
    }

    @Bean
    public RpcChannelManager rpcChannelManager(RpcProperties properties) { return new RpcChannelManager(properties); }

    @Bean
    @ConditionalOnMissingBean(ServiceRegistry.class)
    public ServiceRegistry serviceRegistry(RpcProperties properties) { return new StaticServiceRegistry(properties); }

    @Bean
    @ConditionalOnMissingBean(LoadBalancer.class)
    public LoadBalancer loadBalancer() { return new RoundRobinLoadBalancer(); }

    @Bean
    public static RpcReferenceBeanPostProcessor rpcReferenceBeanPostProcessor(
            RpcProperties properties, ObjectProvider<RpcChannelManager> channelManagerProvider,
            ObjectProvider<RpcCodec> serializerProvider) {
        return new RpcReferenceBeanPostProcessor(properties, channelManagerProvider, serializerProvider);
    }

    @Bean
    public RpcDependencyLogger rpcDependencyLogger(RpcProperties properties) { return new RpcDependencyLogger(properties); }

    private static void validateExecutorConfig(RpcProperties.Server cfg) {
        if (cfg.getExecutorCoreThreads() <= 0 || cfg.getExecutorMaxThreads() < cfg.getExecutorCoreThreads()
                || cfg.getExecutorQueueCapacity() <= 0 || cfg.getExecutorKeepAliveSeconds() < 0) {
            throw new IllegalArgumentException("Invalid ctrpc.rpc.server executor configuration");
        }
    }

    private static final class RpcBusinessThreadFactory implements ThreadFactory {
        private final AtomicInteger index = new AtomicInteger(1);
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ctrpc-business-" + index.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }

    public static class RpcDependencyLogger {
        private final RpcProperties properties;
        public RpcDependencyLogger(RpcProperties properties) { this.properties = properties; }
        @EventListener(ApplicationReadyEvent.class)
        public void logDependencies() {
            if (properties.getDependencies().isEmpty()) {
                log.info("No RPC service dependencies configured (ctrpc.rpc.dependencies)");
                return;
            }
            properties.getDependencies().forEach((name, dep) ->
                    log.info("RPC dependency: service={} address={} interfaces={}", name, dep.getAddress(), dep.getInterfaces()));
        }
    }
}
