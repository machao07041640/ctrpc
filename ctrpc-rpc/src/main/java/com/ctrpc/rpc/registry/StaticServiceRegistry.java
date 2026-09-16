package com.ctrpc.rpc.registry;

import com.ctrpc.rpc.config.RpcProperties;

import java.util.*;

public class StaticServiceRegistry implements ServiceRegistry {
    private final RpcProperties properties;

    public StaticServiceRegistry(RpcProperties properties) {
        this.properties = properties;
    }

    public List<ServiceMeta> discover(String serviceName) {
        RpcProperties.ServiceDependency d = properties.getDependencies().get(serviceName);
        if (d == null || d.getAddress() == null) return Collections.emptyList();
        return Collections.singletonList(new ServiceMeta(serviceName, d.getAddress()));
    }
}
