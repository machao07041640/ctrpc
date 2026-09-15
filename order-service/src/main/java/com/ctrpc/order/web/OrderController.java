package com.ctrpc.order.web;

import com.ctrpc.iface.order.CreateOrderRequest;
import com.ctrpc.iface.order.OrderDTO;
import com.ctrpc.iface.order.OrderIface;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP 演示入口。createOrder 内部会通过 @RpcReference 的 UserIface 远程校验用户。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderIface orderIface;

    public OrderController(OrderIface orderIface) {
        this.orderIface = orderIface;
    }

    @GetMapping("/{id}")
    public OrderDTO get(@PathVariable Long id) {
        return orderIface.getOrder(id);
    }

    @PostMapping
    public OrderDTO create(@RequestBody CreateOrderRequest request) {
        return orderIface.createOrder(request);
    }
}
