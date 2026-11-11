package com.guild.checkout.common.exception;

import com.guild.checkout.common.api.ErrorCode;

/** 业务规则拒绝，例如非法状态跃迁。 */
public class BusinessException extends AppException {

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_REJECTED, message);
    }
}
