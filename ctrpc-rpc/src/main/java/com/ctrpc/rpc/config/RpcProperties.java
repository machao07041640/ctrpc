package com.ctrpc.rpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "ctrpc.rpc")
@Data
public class RpcProperties {
    private Server server = new Server();
    private Client client = new Client();
    private Map<String, ServiceDependency> dependencies = new LinkedHashMap<>();

    @Data
    public static class Server {
        private int port = 9090;
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int maxInboundMessageSize = 4 * 1024 * 1024;
        private boolean keepAlive = true;
        private int executorCoreThreads = 16;
        private int executorMaxThreads = 64;
        private int executorQueueCapacity = 1000;
        private long executorKeepAliveSeconds = 60;
    }

    @Data
    public static class Client {
        private Duration defaultTimeout = Duration.ofSeconds(3);
        private boolean keepAlive = true;
        private Duration keepAliveTime = Duration.ofSeconds(30);
        private int maxInboundMessageSize = 4 * 1024 * 1024;
    }

    @Data
    public static class ServiceDependency {
        private String address;
        private List<String> interfaces = new ArrayList<>();
        private Duration timeout;
    }
}
