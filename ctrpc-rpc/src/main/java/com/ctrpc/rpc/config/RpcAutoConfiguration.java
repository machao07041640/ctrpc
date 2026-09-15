package com.ctrpc.rpc.config;

import com.ctrpc.rpc.client.RpcChannelManager;
import com.ctrpc.rpc.client.RpcReferenceBeanPostProcessor;
import com.ctrpc.rpc.serialize.Fastjson2RpcCodec;
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

@AutoConfiguration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RpcAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(RpcCodec.class)
    public RpcCodec rpcCodec() { return new Fastjson2RpcCodec(); }

    @Bean
    public static RpcServiceRegistry rpcServiceRegistry() { return new RpcServiceRegistry(); }

    @Bean
    public GenericRpcInvoker genericRpcInvoker(RpcServiceRegistry registry, RpcCodec serializer) {
        return new GenericRpcInvoker(registry, serializer);
    }

    @Bean
    public RpcServerBootstrap rpcServerBootstrap(RpcProperties properties, GenericRpcInvoker invoker) {
        return new RpcServerBootstrap(properties, invoker);
    }

    @Bean
    public RpcChannelManager rpcChannelManager(RpcProperties properties) { return new RpcChannelManager(properties); }

    @Bean
    public static RpcReferenceBeanPostProcessor rpcReferenceBeanPostProcessor(
            RpcProperties properties, ObjectProvider<RpcChannelManager> channelManagerProvider,
            ObjectProvider<RpcCodec> serializerProvider) {
        return new RpcReferenceBeanPostProcessor(properties, channelManagerProvider, serializerProvider);
    }

    @Bean
    public RpcDependencyLogger rpcDependencyLogger(RpcProperties properties) { return new RpcDependencyLogger(properties); }

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
