package com.ctrpc.rpc.loadbalance;
import com.ctrpc.rpc.registry.ServiceMeta;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
public class RoundRobinLoadBalancer implements LoadBalancer {
 private final AtomicInteger index=new AtomicInteger();
 public ServiceMeta select(List<ServiceMeta> nodes){
  if(nodes==null||nodes.isEmpty()) throw new IllegalStateException("No RPC instances");
  return nodes.get(Math.abs(index.getAndIncrement())%nodes.size());
 }
}
