package com.ctrpc.iface.user;

import java.util.List;

/**
 * 用户服务 RPC 接口。实现类加 @RpcService 即可对外暴露。
 */
public interface UserIface {

    UserDTO getUser(Long userId);

    List<UserDTO> batchGetUsers(List<Long> userIds);
}
