package com.ctrpc.rpc.annotation;

import java.lang.annotation.*;

/**
 * 标注在需要注入的远程 iface 字段上，框架会创建 JDK 代理并走 gRPC 调用。
 * <p>
 * 需在配置文件中声明服务名与接口全路径依赖，例如：
 * <pre>
 * ctrpc.rpc.dependencies:
 *   user-service:
 *     address: static://localhost:9091
 *     interfaces:
 *       - com.ctrpc.iface.user.UserIface
 * </pre>
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcReference {

    /**
     * 依赖的服务名；为空则从配置中按接口全路径自动匹配。
     */
    String service() default "";

    /**
     * 调用超时毫秒；0 表示使用配置默认值。
     */
    long timeoutMs() default 0;
}
