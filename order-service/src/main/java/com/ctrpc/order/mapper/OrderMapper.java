package com.ctrpc.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ctrpc.order.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
}
