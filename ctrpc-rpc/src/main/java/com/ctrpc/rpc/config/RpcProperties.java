package com.ctrpc.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RPC 本地依赖配置。
 * <p>
 * 调用其他服务时，在 application.yml 添加服务名与接口全路径即可。
 */
@ConfigurationProperties(prefix = "ctrpc.rpc")
public class RpcProperties {

    private Server server = new Server();
    private Client client = new Client();
    /**
     * key = 服务名，如 user-service
     */
    private Map<String, ServiceDependency> dependencies = new LinkedHashMap<>();

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Map<String, ServiceDependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Map<String, ServiceDependency> dependencies) {
        this.dependencies = dependencies;
    }

    public static class Server {
        private int port = 9090;
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int maxInboundMessageSize = 4 * 1024 * 1024;
        private boolean keepAlive = true;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getBossThreads() {
            return bossThreads;
        }

        public void setBossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getMaxInboundMessageSize() {
            return maxInboundMessageSize;
        }

        public void setMaxInboundMessageSize(int maxInboundMessageSize) {
            this.maxInboundMessageSize = maxInboundMessageSize;
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }
    }

    public static class Client {
        private Duration defaultTimeout = Duration.ofSeconds(3);
        private boolean keepAlive = true;
        private Duration keepAliveTime = Duration.ofSeconds(30);
        private int maxInboundMessageSize = 4 * 1024 * 1024;

        public Duration getDefaultTimeout() {
            return defaultTimeout;
        }

        public void setDefaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }

        public Duration getKeepAliveTime() {
            return keepAliveTime;
        }

        public void setKeepAliveTime(Duration keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
        }

        public int getMaxInboundMessageSize() {
            return maxInboundMessageSize;
        }

        public void setMaxInboundMessageSize(int maxInboundMessageSize) {
            this.maxInboundMessageSize = maxInboundMessageSize;
        }
    }

    public static class ServiceDependency {
        /**
         * gRPC 地址，支持 static://host:port
         */
        private String address;
        /**
         * 依赖的接口全路径列表
         */
        private List<String> interfaces = new ArrayList<>();
        private Duration timeout;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public List<String> getInterfaces() {
            return interfaces;
        }

        public void setInterfaces(List<String> interfaces) {
            this.interfaces = interfaces;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
