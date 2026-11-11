package com.guild.checkout.common.api;

import org.springframework.http.HttpStatus;

/**
 * 全站错误码。code 为业务错误码，status 为对应的 HTTP 状态码。
 * 粉丝端据 code 决定行为：UNAUTHENTICATED 触发重新授权，而非展示错误页。
 */
public enum ErrorCode {

    /** 未认证：无会话或会话已过期，客户端应重新发起微信授权。 */
    UNAUTHENTICATED("UNAUTHENTICATED", "登录状态已失效，请重新授权", HttpStatus.UNAUTHORIZED),

    /** 无权限：已登录但角色或归属不满足。 */
    FORBIDDEN("FORBIDDEN", "没有访问权限", HttpStatus.FORBIDDEN),

    /** 参数校验失败。 */
    INVALID_PARAM("INVALID_PARAM", "请求参数不合法", HttpStatus.BAD_REQUEST),

    /** 业务规则拒绝。 */
    BUSINESS_REJECTED("BUSINESS_REJECTED", "操作不被允许", HttpStatus.CONFLICT),

    /** 资源不存在。 */
    NOT_FOUND("NOT_FOUND", "请求的资源不存在", HttpStatus.NOT_FOUND),

    /** 路由与鉴权已就位，但功能尚未实现。 */
    NOT_IMPLEMENTED("NOT_IMPLEMENTED", "该功能尚未实现", HttpStatus.NOT_IMPLEMENTED),

    /** 兜底的服务端错误。 */
    INTERNAL_ERROR("INTERNAL_ERROR", "服务暂时不可用，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(String code, String defaultMessage, HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }
}
