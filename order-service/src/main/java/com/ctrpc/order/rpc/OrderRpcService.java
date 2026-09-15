package com.ctrpc.order.rpc;

import com.ctrpc.iface.order.CreateOrderRequest;
import com.ctrpc.iface.order.OrderDTO;
import com.ctrpc.iface.order.OrderIface;
import com.ctrpc.iface.user.UserIface;
import com.ctrpc.order.entity.OrderEntity;
import com.ctrpc.order.mapper.OrderMapper;
import com.ctrpc.rpc.annotation.RpcReference;
import com.ctrpc.rpc.annotation.RpcService;
import com.ctrpc.rpc.exception.RpcException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 订单 RPC 实现。
 * <p>
 * 通过 @RpcReference 注入 UserIface，本地像调用普通接口一样调用用户服务。
 */
@RpcService
public class OrderRpcService implements OrderIface {

    private final OrderMapper orderMapper;

    @RpcReference
    private UserIface userIface;

    public OrderRpcService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public OrderDTO getOrder(Long orderId) {
        OrderEntity entity = orderMapper.selectById(orderId);
        if (entity == null) {
            throw new RpcException(404, "order not found: " + orderId);
        }
        return toDto(entity);
    }

    @Override
    @Transactional
    public OrderDTO createOrder(CreateOrderRequest request) {
        // 远程调用用户服务校验用户是否存在
        userIface.getUser(request.getUserId());

        OrderEntity order = new OrderEntity();
        order.setUserId(request.getUserId());
        order.setProductName(request.getProductName());
        order.setQuantity(request.getQuantity());
        order.setAmountCents(request.getAmountCents());
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        return toDto(order);
    }

    private static OrderDTO toDto(OrderEntity entity) {
        OrderDTO dto = new OrderDTO();
        dto.setOrderId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setProductName(entity.getProductName());
        dto.setQuantity(entity.getQuantity());
        dto.setAmountCents(entity.getAmountCents());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        return dto;
    }
}
