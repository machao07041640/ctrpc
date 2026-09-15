package com.ctrpc.user.rpc;

import com.alibaba.fastjson2.JSON;
import com.ctrpc.common.cache.RedisCacheService;
import com.ctrpc.iface.user.UserDTO;
import com.ctrpc.iface.user.UserIface;
import com.ctrpc.rpc.annotation.RpcService;
import com.ctrpc.rpc.exception.RpcException;
import com.ctrpc.user.entity.UserEntity;
import com.ctrpc.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户 RPC 实现：加 @RpcService 后自动注册并对外暴露。
 */
@RpcService
public class UserRpcService implements UserIface {

    private static final Logger log = LoggerFactory.getLogger(UserRpcService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_PREFIX = "user:";

    private final UserMapper userMapper;
    private final RedisCacheService cacheService;

    public UserRpcService(UserMapper userMapper, RedisCacheService cacheService) {
        this.userMapper = userMapper;
        this.cacheService = cacheService;
    }

    @Override
    public UserDTO getUser(Long userId) {
        String cacheKey = CACHE_PREFIX + userId;
        String cached = cacheService.getOrLoad(cacheKey, CACHE_TTL, () -> {
            UserEntity entity = userMapper.selectById(userId);
            if (entity == null) {
                return null;
            }
            try {
                return JSON.toJSONString(toDto(entity));
            } catch (Exception e) {
                log.warn("serialize user cache failed userId={}", userId, e);
                return null;
            }
        });

        if (cached == null) {
            throw new RpcException(404, "user not found: " + userId);
        }
        try {
            return JSON.parseObject(cached, UserDTO.class);
        } catch (Exception e) {
            cacheService.delete(cacheKey);
            throw new RpcException(500, "deserialize user failed: " + userId, e);
        }
    }

    @Override
    public List<UserDTO> batchGetUsers(List<Long> userIds) {
        List<UserDTO> result = new ArrayList<>();
        if (userIds == null) {
            return result;
        }
        for (Long userId : userIds) {
            try {
                result.add(getUser(userId));
            } catch (RpcException e) {
                if (e.getCode() != 404) {
                    throw e;
                }
            }
        }
        return result;
    }

    private static UserDTO toDto(UserEntity entity) {
        UserDTO dto = new UserDTO();
        dto.setUserId(entity.getId());
        dto.setUsername(entity.getUsername());
        dto.setEmail(entity.getEmail());
        dto.setCreatedAt(entity.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        return dto;
    }
}
