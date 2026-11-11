package com.guild.checkout.common.exception;

import com.guild.checkout.common.api.ErrorCode;

/** 无有效会话，或会话已过期。 */
public class UnauthenticatedException extends AppException {

    public UnauthenticatedException() {
        super(ErrorCode.UNAUTHENTICATED);
    }

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
