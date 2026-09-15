package com.ctrpc.rpc.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 标注在 RPC 接口实现类上，自动注册到本微服务并对外暴露。
 * <p>
 * 实现类必须实现至少一个业务 iface；默认取第一个非 JDK/Spring 接口。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RpcService {

    /**
     * Spring Bean 名称，透传给 @Component。
     */
    @AliasFor(annotation = Component.class)
    String value() default "";

    /**
     * 显式指定暴露的接口；为空则自动推断。
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 服务分组，预留。
     */
    String group() default "default";
}
