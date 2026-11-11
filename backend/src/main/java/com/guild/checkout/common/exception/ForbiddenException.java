package com.guild.checkout.common.exception;

import com.guild.checkout.common.api.ErrorCode;

/** 已登录但角色或资源归属不满足。 */
public class ForbiddenException extends AppException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
