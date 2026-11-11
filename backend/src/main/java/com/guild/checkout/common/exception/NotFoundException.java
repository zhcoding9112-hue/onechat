package com.guild.checkout.common.exception;

import com.guild.checkout.common.api.ErrorCode;

/** 请求的资源不存在。 */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
