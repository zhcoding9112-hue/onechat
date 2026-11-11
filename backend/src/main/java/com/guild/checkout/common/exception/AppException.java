package com.guild.checkout.common.exception;

import com.guild.checkout.common.api.ErrorCode;

/**
 * 业务异常基类。携带错误码，由全局异常处理统一转为响应结构。
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
