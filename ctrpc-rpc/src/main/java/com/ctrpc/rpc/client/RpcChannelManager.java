package com.ctrpc.rpc.client;

import com.ctrpc.rpc.config.RpcProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 按服务名复用 ManagedChannel，支撑高 QPS 长连接调用。
 */
public class RpcChannelManager {

    private static final Logger log = LoggerFactory.getLogger(RpcChannelManager.class);

    private final RpcProperties properties;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public RpcChannelManager(RpcProperties properties) {
        this.properties = properties;
    }

    public ManagedChannel getChannel(String serviceName) {
        return channels.computeIfAbsent(serviceName, this::createChannel);
    }

    private ManagedChannel createChannel(String serviceName) {
        RpcProperties.ServiceDependency dep = properties.getDependencies().get(serviceName);
        if (dep == null || dep.getAddress() == null || StringUtils.isEmpty(dep.getAddress())) {
            throw new IllegalStateException("No RPC dependency address for service: " + serviceName
                    + ". Please configure ctrpc.rpc.dependencies." + serviceName + ".address");
        }
        String target = normalizeAddress(dep.getAddress());
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .maxInboundMessageSize(properties.getClient().getMaxInboundMessageSize());

        if (properties.getClient().isKeepAlive()) {
            builder.keepAliveTime(properties.getClient().getKeepAliveTime().getSeconds(), TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true);
        }

        ManagedChannel channel = builder.build();
        log.info("RPC channel created: service={} target={}", serviceName, target);
        return channel;
    }

    /**
     * 支持 static://host:port 或 host:port
     */
    static String normalizeAddress(String address) {
        String value = address.trim();
        if (value.startsWith("static://")) {
            return value.substring("static://".length());
        }
        if (value.startsWith("dns:///")) {
            return value.substring("dns:///".length());
        }
        return value;
    }

    @PreDestroy
    public void shutdown() {
        channels.forEach((name, channel) -> {
            try {
                channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
            log.info("RPC channel closed: service={}", name);
        });
        channels.clear();
    }
}
