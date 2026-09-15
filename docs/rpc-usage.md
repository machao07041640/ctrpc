# CTRPC 注解式 RPC 使用说明

底层是 gRPC，业务侧只感知 Java iface + 注解 + 本地配置。

## 1. 定义接口（ctrpc-iface）

```java
package com.ctrpc.iface.user;

public interface UserIface {
    UserDTO getUser(Long userId);
}
```

## 2. 服务端暴露（加 @RpcService）

```java
@RpcService
public class UserRpcService implements UserIface {
    @Override
    public UserDTO getUser(Long userId) {
        // 业务逻辑
    }
}
```

配置监听端口：

```yaml
ctrpc:
  rpc:
    server:
      port: 9091
```

## 3. 消费端依赖（配置 + @RpcReference）

### 配置文件：添加服务名 + 接口全路径

```yaml
ctrpc:
  rpc:
    dependencies:
      user-service:                          # 服务名
        address: static://localhost:9091     # 目标地址
        timeout: 3s
        interfaces:                          # 接口全路径
          - com.ctrpc.iface.user.UserIface
```

### 代码：注入 iface

```java
public class OrderRpcService implements OrderIface {

    @RpcReference   # 也可写 @RpcReference(service = "user-service")
    private UserIface userIface;

    public OrderDTO createOrder(CreateOrderRequest req) {
        userIface.getUser(req.getUserId());  // 像本地方法一样调用
        // ...
    }
}
```

## 调用链

```
业务代码 userIface.getUser(1)
    → JDK Proxy (@RpcReference)
    → Generic gRPC Invoke(interface, method, argsJson)
    → 对端 GenericRpcInvoker
    → 本地 @RpcService 实现类
```

## 序列化

RPC 参数与返回值使用 **Fastjson2**（`com.alibaba.fastjson2`）做 JSON 编解码，支持泛型返回类型（如 `List<UserDTO>`）。
