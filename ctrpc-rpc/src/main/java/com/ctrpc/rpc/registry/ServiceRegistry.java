package com.ctrpc.rpc.registry;
import java.util.List;
public interface ServiceRegistry {
    List<ServiceMeta> discover(String serviceName);
}
