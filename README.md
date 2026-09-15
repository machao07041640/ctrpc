# ctrpc — 注解式 gRPC 微服务脚手架

底层 **gRPC**，上层 **@RpcService / @RpcReference**，业务只依赖 Java iface，无需手写 Stub。

## 模块

```
ctrpc/
├── ctrpc-proto/     # 通用 Invoke 协议（业务无感知）
├── ctrpc-rpc/       # 注解 RPC 框架
├── ctrpc-iface/     # 共享业务接口与 DTO
├── ctrpc-common/    # MySQL / Redis / 日志
├── user-service/    # Provider 示例
└── order-service/   # Provider + Consumer 示例
```

## 怎么用（核心三步）

### Provider：暴露接口

```java
@RpcService
public class UserRpcService implements UserIface {
    public UserDTO getUser(Long userId) { ... }
}
```

```yaml
ctrpc:
  rpc:
    server:
      port: 9091
```

### Consumer：配置依赖

```yaml
ctrpc:
  rpc:
    dependencies:
      user-service:
        address: static://localhost:9091
        interfaces:
          - com.ctrpc.iface.user.UserIface
```

### Consumer：注入调用

```java
@RpcReference
private UserIface userIface;

userIface.getUser(1L);  // 直接调用
```

更完整说明见 [docs/rpc-usage.md](docs/rpc-usage.md)。

## 快速启动

```bash
docker compose up -d

export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # 或 JDK 21+
mvn clean package -DskipTests

java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar
```

HTTP 验证（order-service 会 RPC 调用 user-service）：

```bash
curl -X POST http://localhost:8082/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"productName":"MacBook","quantity":1,"amountCents":99900}'
```

## 高 QPS（1k+）默认能力

- gRPC 长连接 + Keep-Alive，按服务名复用 `ManagedChannel`
- Java 21 虚拟线程（`spring.threads.virtual.enabled=true`）
- HikariCP / Lettuce 连接池（默认面向千级 QPS）
- Redis cache-aside 热点缓存
- 调用链路 `traceId` 透传与耗时日志

## 端口

| 服务 | HTTP | RPC(gRPC) |
|------|------|-----------|
| user-service | 8081 | 9091 |
| order-service | 8082 | 9092 |
| MySQL | 3306 | — |
| Redis | 6379 | — |
