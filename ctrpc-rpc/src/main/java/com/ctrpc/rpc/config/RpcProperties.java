package com.ctrpc.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "ctrpc.rpc")
public class RpcProperties {
    private Server server = new Server();
    private Client client = new Client();
    private Map<String, ServiceDependency> dependencies = new LinkedHashMap<>();
    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public Map<String, ServiceDependency> getDependencies() { return dependencies; }
    public void setDependencies(Map<String, ServiceDependency> dependencies) { this.dependencies = dependencies; }

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
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getBossThreads() { return bossThreads; }
        public void setBossThreads(int bossThreads) { this.bossThreads = bossThreads; }
        public int getWorkerThreads() { return workerThreads; }
        public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
        public int getMaxInboundMessageSize() { return maxInboundMessageSize; }
        public void setMaxInboundMessageSize(int maxInboundMessageSize) { this.maxInboundMessageSize = maxInboundMessageSize; }
        public boolean isKeepAlive() { return keepAlive; }
        public void setKeepAlive(boolean keepAlive) { this.keepAlive = keepAlive; }
        public int getExecutorCoreThreads() { return executorCoreThreads; }
        public void setExecutorCoreThreads(int v) { this.executorCoreThreads = v; }
        public int getExecutorMaxThreads() { return executorMaxThreads; }
        public void setExecutorMaxThreads(int v) { this.executorMaxThreads = v; }
        public int getExecutorQueueCapacity() { return executorQueueCapacity; }
        public void setExecutorQueueCapacity(int v) { this.executorQueueCapacity = v; }
        public long getExecutorKeepAliveSeconds() { return executorKeepAliveSeconds; }
        public void setExecutorKeepAliveSeconds(long v) { this.executorKeepAliveSeconds = v; }
    }
    public static class Client {
        private Duration defaultTimeout = Duration.ofSeconds(3);
        private boolean keepAlive = true;
        private Duration keepAliveTime = Duration.ofSeconds(30);
        private int maxInboundMessageSize = 4 * 1024 * 1024;
        public Duration getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(Duration v) { this.defaultTimeout = v; }
        public boolean isKeepAlive() { return keepAlive; }
        public void setKeepAlive(boolean v) { this.keepAlive = v; }
        public Duration getKeepAliveTime() { return keepAliveTime; }
        public void setKeepAliveTime(Duration v) { this.keepAliveTime = v; }
        public int getMaxInboundMessageSize() { return maxInboundMessageSize; }
        public void setMaxInboundMessageSize(int v) { this.maxInboundMessageSize = v; }
    }
    public static class ServiceDependency {
        private String address;
        private List<String> interfaces = new ArrayList<>();
        private Duration timeout;
        public String getAddress() { return address; }
        public void setAddress(String v) { this.address = v; }
        public List<String> getInterfaces() { return interfaces; }
        public void setInterfaces(List<String> v) { this.interfaces = v; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration v) { this.timeout = v; }
    }
}
