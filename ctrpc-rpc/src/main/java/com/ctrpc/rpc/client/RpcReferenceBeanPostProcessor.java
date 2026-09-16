package com.ctrpc.rpc.client;

import com.ctrpc.rpc.annotation.RpcReference;
import com.ctrpc.rpc.config.RpcProperties;
import com.ctrpc.rpc.loadbalance.LoadBalancer;
import com.ctrpc.rpc.metrics.RpcMetrics;
import com.ctrpc.rpc.registry.ServiceRegistry;
import com.ctrpc.rpc.serialize.RpcCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.Executor;

/** 扫描 @RpcReference 字段，按配置注入远程 iface 代理。 */
public class RpcReferenceBeanPostProcessor implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(RpcReferenceBeanPostProcessor.class);
    private final RpcProperties properties;
    private final ObjectProvider<RpcChannelManager> channelManagerProvider;
    private final ObjectProvider<RpcCodec> serializerProvider;
    private final ObjectProvider<ServiceRegistry> registryProvider;
    private final ObjectProvider<LoadBalancer> loadBalancerProvider;
    private final ObjectProvider<RpcMetrics> metricsProvider;
    private final Executor asyncExecutor;

    public RpcReferenceBeanPostProcessor(RpcProperties properties,
                                         ObjectProvider<RpcChannelManager> channelManagerProvider,
                                         ObjectProvider<RpcCodec> serializerProvider,
                                         ObjectProvider<ServiceRegistry> registryProvider,
                                         ObjectProvider<LoadBalancer> loadBalancerProvider,
                                         ObjectProvider<RpcMetrics> metricsProvider,
                                         @Qualifier("rpcAsyncExecutor") Executor asyncExecutor) {
        this.properties = properties;
        this.channelManagerProvider = channelManagerProvider;
        this.serializerProvider = serializerProvider;
        this.registryProvider = registryProvider;
        this.loadBalancerProvider = loadBalancerProvider;
        this.metricsProvider = metricsProvider;
        this.asyncExecutor = asyncExecutor;
    }

    /** Backward-compatible constructor for applications creating the processor directly. */
    public RpcReferenceBeanPostProcessor(RpcProperties properties,
                                         ObjectProvider<RpcChannelManager> channelManagerProvider,
                                         ObjectProvider<RpcCodec> serializerProvider) {
        this.properties = properties;
        this.channelManagerProvider = channelManagerProvider;
        this.serializerProvider = serializerProvider;
        this.registryProvider = null;
        this.loadBalancerProvider = null;
        this.metricsProvider = null;
        this.asyncExecutor = Runnable::run;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            RpcReference reference = field.getAnnotation(RpcReference.class);
            if (reference != null) injectReference(bean, field, reference);
        });
        return bean;
    }

    private void injectReference(Object bean, Field field, RpcReference reference) {
        Class<?> iface = field.getType();
        if (!iface.isInterface()) throw new IllegalStateException("@RpcReference field must be an interface: " + field);
        String serviceName = resolveServiceName(iface, reference);
        ServiceRegistry registry = registryProvider == null ? null : registryProvider.getIfAvailable();
        LoadBalancer loadBalancer = loadBalancerProvider == null ? null : loadBalancerProvider.getIfAvailable();
        RpcMetrics metrics = metricsProvider == null
                ? new RpcMetrics(new SimpleMeterRegistry()) : metricsProvider.getObject();
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                new RpcInvocationHandler(serviceName, iface, reference.timeoutMs(),
                        channelManagerProvider.getObject(), serializerProvider.getObject(), properties,
                        registry, loadBalancer, metrics, asyncExecutor));
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, bean, proxy);
        log.info("RPC reference injected: field={}.{} -> service={} iface={}",
                bean.getClass().getSimpleName(), field.getName(), serviceName, iface.getName());
    }

    private String resolveServiceName(Class<?> iface, RpcReference reference) {
        if (reference.service() != null && !StringUtils.isEmpty(reference.service())) {
            String name = reference.service();
            ensureConfigured(name, iface);
            return name;
        }
        String ifaceName = iface.getName();
        for (Map.Entry<String, RpcProperties.ServiceDependency> entry : properties.getDependencies().entrySet()) {
            if (entry.getValue().getInterfaces() != null && entry.getValue().getInterfaces().contains(ifaceName)) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Cannot resolve RPC service for interface " + ifaceName
                + ". Add it under ctrpc.rpc.dependencies.<service>.interfaces or set @RpcReference(service=...)");
    }

    private void ensureConfigured(String serviceName, Class<?> iface) {
        RpcProperties.ServiceDependency dep = properties.getDependencies().get(serviceName);
        if (dep == null) throw new IllegalStateException("Missing ctrpc.rpc.dependencies." + serviceName);
        if (dep.getInterfaces() != null && !dep.getInterfaces().isEmpty() && !dep.getInterfaces().contains(iface.getName())) {
            throw new IllegalStateException("Interface " + iface.getName()
                    + " is not listed in ctrpc.rpc.dependencies." + serviceName + ".interfaces");
        }
    }
}
