package com.ctrpc.iface.order;

/**
 * 订单服务 RPC 接口。
 */
public interface OrderIface {

    OrderDTO getOrder(Long orderId);

    OrderDTO createOrder(CreateOrderRequest request);
}
