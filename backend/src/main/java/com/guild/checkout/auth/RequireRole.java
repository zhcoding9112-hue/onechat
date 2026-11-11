package com.guild.checkout.auth;

import com.guild.checkout.user.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注接口所需的角色。可加在类上（作用于全部方法）或方法上（覆盖类上的声明）。
 *
 * 不标注本注解的接口默认**要求已登录**，见 {@link PublicApi}。
 * 默认收紧而非默认放开：漏标一个接口的后果是用户多一次授权，而不是数据泄露。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireRole {

    /** 所需角色。 */
    UserRole[] value();

    /** true 表示须同时具备全部角色；默认 false，具备其一即可。 */
    boolean requireAll() default false;
}
