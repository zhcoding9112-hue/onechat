package com.guild.checkout.auth;

import com.guild.checkout.common.exception.UnauthenticatedException;

/**
 * 当前请求的用户上下文。拦截器写入、在请求结束时清理。
 *
 * 用 ThreadLocal 而非把用户塞进方法签名：鉴权发生在拦截器，业务层只在需要时取，
 * 不必让每个 service 方法都多带一个参数。异步线程不继承本上下文，需要时显式传递。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    static void clear() {
        HOLDER.remove();
    }

    /** 当前用户，未登录时为 null。 */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 当前用户，未登录直接抛未认证。 */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new UnauthenticatedException();
        }
        return user;
    }

    /** 当前用户 id，未登录直接抛未认证。 */
    public static Long requireUserId() {
        return require().userId();
    }
}
