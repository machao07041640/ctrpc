package com.ctrpc.rpc.registry;

public class ServiceMeta {
    private final String serviceName;
    private final String address;
    private int weight=1;
    public ServiceMeta(String serviceName,String address){this.serviceName=serviceName;this.address=address;}
    public String getServiceName(){return serviceName;}
    public String getAddress(){return address;}
    public int getWeight(){return weight;}
    public void setWeight(int weight){this.weight=weight;}
}
