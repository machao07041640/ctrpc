package com.ctrpc.rpc.loadbalance;
import com.ctrpc.rpc.registry.ServiceMeta;
import java.util.List;
public interface LoadBalancer { ServiceMeta select(List<ServiceMeta> nodes); }
