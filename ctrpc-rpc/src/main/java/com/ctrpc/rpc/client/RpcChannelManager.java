package com.ctrpc.rpc.client;

import com.ctrpc.rpc.config.RpcProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** 按服务实例复用 ManagedChannel，支撑多节点高 QPS 长连接调用。 */
public class RpcChannelManager {

    private static final Logger log = LoggerFactory.getLogger(RpcChannelManager.class);

    private final RpcProperties properties;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public RpcChannelManager(RpcProperties properties) {
        this.properties = properties;
    }

    public ManagedChannel getChannel(String serviceName) {
        RpcProperties.ServiceDependency dep = properties.getDependencies().get(serviceName);
        if (dep == null || !StringUtils.hasText(dep.getAddress())) {
            throw new IllegalStateException("No RPC dependency address for service: " + serviceName);
        }
        return getChannel(serviceName, dep.getAddress());
    }

    public ManagedChannel getChannel(String serviceName, String address) {
        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException("RPC target address must not be empty");
        }
        String target = normalizeAddress(address);
        String key = serviceName + "@" + target;
        return channels.computeIfAbsent(key, ignored -> createChannel(serviceName, target));
    }

    private ManagedChannel createChannel(String serviceName, String target) {
        ManagedChannelBuilder<?> builder;
        if (properties.getClient().isTlsEnabled()) {
            try {
                NettyChannelBuilder nettyBuilder = NettyChannelBuilder.forTarget(target)
                        .sslContext(buildClientSslContext())
                        .maxInboundMessageSize(properties.getClient().getMaxInboundMessageSize());
                builder = nettyBuilder;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure RPC TLS for service: " + serviceName, e);
            }
        } else {
            builder = ManagedChannelBuilder.forTarget(target)
                    .usePlaintext()
                    .maxInboundMessageSize(properties.getClient().getMaxInboundMessageSize());
        }

        if (properties.getClient().isKeepAlive()) {
            builder.keepAliveTime(properties.getClient().getKeepAliveTime().getSeconds(), TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true);
        }

        ManagedChannel channel = builder.build();
        log.info("RPC channel created: service={} target={} tls={}", serviceName, target, properties.getClient().isTlsEnabled());
        return channel;
    }

    private io.grpc.netty.shaded.io.netty.handler.ssl.SslContext buildClientSslContext() throws Exception {
        io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder builder = GrpcSslContexts.forClient();
        RpcProperties.Client client = properties.getClient();
        if (StringUtils.hasText(client.getTlsTrustCertCollectionFile())) {
            builder.trustManager(new File(client.getTlsTrustCertCollectionFile()));
        }
        if (StringUtils.hasText(client.getTlsClientCertChainFile()) && StringUtils.hasText(client.getTlsClientPrivateKeyFile())) {
            builder.keyManager(new File(client.getTlsClientCertChainFile()), new File(client.getTlsClientPrivateKeyFile()));
        }
        return builder.build();
    }

    static String normalizeAddress(String address) {
        String value = address.trim();
        if (value.startsWith("static://")) return value.substring("static://".length());
        if (value.startsWith("dns:///")) return value.substring("dns:///".length());
        return value;
    }

    @PreDestroy
    public void shutdown() {
        channels.forEach((name, channel) -> {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(3, TimeUnit.SECONDS)) channel.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
            log.info("RPC channel closed: key={}", name);
        });
        channels.clear();
    }
}
