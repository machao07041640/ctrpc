package com.ctrpc.rpc.registry;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.ctrpc.rpc.config.RpcProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Nacos-backed registry. Ephemeral instances are used so dead providers are removed by Nacos. */
public class NacosServiceRegistry implements ServiceRegistry {
    private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistry.class);

    private final RpcProperties.Registry config;
    private final NamingService namingService;
    private volatile String registeredService;
    private volatile String registeredIp;
    private volatile int registeredPort;

    public NacosServiceRegistry(RpcProperties.Registry config) {
        this.config = config;
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", config.getServerAddr());
            if (config.getNamespace() != null) properties.setProperty("namespace", config.getNamespace());
            if (config.getUsername() != null) properties.setProperty("username", config.getUsername());
            if (config.getPassword() != null) properties.setProperty("password", config.getPassword());
            this.namingService = NacosFactory.createNamingService(properties);
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to create Nacos naming service", e);
        }
    }

    @Override
    public List<ServiceMeta> discover(String serviceName) {
        try {
            List<Instance> instances = namingService.selectInstances(serviceName, config.getGroup(), true, true);
            if (instances == null || instances.isEmpty()) return Collections.emptyList();
            List<ServiceMeta> result = new ArrayList<>(instances.size());
            for (Instance instance : instances) {
                if (!instance.isEnabled() || !instance.isHealthy()) continue;
                ServiceMeta meta = new ServiceMeta(serviceName, instance.getIp() + ":" + instance.getPort());
                meta.setWeight(Math.max(1, (int) Math.round(instance.getWeight())));
                result.add(meta);
            }
            return result;
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to discover RPC service: " + serviceName, e);
        }
    }

    public void register(String serviceName, String ip, int port) {
        try {
            Instance instance = new Instance();
            instance.setIp(ip);
            instance.setPort(port);
            instance.setClusterName(config.getCluster());
            instance.setEphemeral(true);
            instance.setHealthy(true);
            instance.setEnabled(true);
            namingService.registerInstance(serviceName, config.getGroup(), instance);
            registeredService = serviceName;
            registeredIp = ip;
            registeredPort = port;
            log.info("RPC service registered to Nacos: service={} address={}:{}", serviceName, ip, port);
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to register RPC service: " + serviceName, e);
        }
    }

    @PreDestroy
    public void close() {
        if (registeredService != null) {
            try {
                namingService.deregisterInstance(registeredService, config.getGroup(), registeredIp, registeredPort, config.getCluster());
            } catch (Exception e) {
                log.warn("Failed to deregister RPC service from Nacos: service={}", registeredService, e);
            }
        }
        try {
            namingService.shutDown();
        } catch (Exception e) {
            log.debug("Failed to close Nacos naming service", e);
        }
    }
}
