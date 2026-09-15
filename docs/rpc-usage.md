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
      user-service:
        address: static://localhost:9091
        timeout: 3s
        interfaces:
          - com.ctrpc.iface.user.UserIface
```

### 代码：注入 iface

```java
public class OrderRpcService implements OrderIface {

    @RpcReference // 也可写 @RpcReference(service = "user-service")
    private UserIface userIface;

    public OrderDTO createOrder(CreateOrderRequest req) {
        userIface.getUser(req.getUserId());
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

RPC 参数与返回值默认使用 **Fastjson2** 做 JSON 编解码，支持泛型返回类型（如 `List<UserDTO>`）。核心依赖 `RpcCodec` 抽象；应用可以提供自己的 `RpcCodec` Bean，而无需修改传输和调用层。

## Server business executor

RPC 业务方法运行在独立的有界线程池中，而不是 gRPC transport executor。默认核心线程 16、最大线程 64、队列 1000；线程池满时拒绝新任务，避免在 transport 线程上执行耗时业务。

```yaml
ctrpc:
  rpc:
    server:
      executor-core-threads: 16
      executor-max-threads: 64
      executor-queue-capacity: 1000
      executor-keep-alive-seconds: 60
```

## Transport errors

业务错误仍通过 `RpcException` 返回；gRPC transport 层错误通过 `RpcTransportException` 暴露，并保留原始 `Status.Code`（例如 `DEADLINE_EXCEEDED`、`UNAVAILABLE`、`CANCELLED`）。这样网络/超时错误不会被错误地当成业务 500，也为后续重试策略保留了判断依据。
