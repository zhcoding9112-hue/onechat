package com.guild.checkout.common.exception;

import com.guild.checkout.common.api.ErrorCode;

/**
 * 接口已登记路由与鉴权，但功能尚未实现。
 * 用于先立好角色守卫、功能由后续任务填充的接口，避免返回假数据。
 */
public class NotImplementedException extends AppException {

    public NotImplementedException(String message) {
        super(ErrorCode.NOT_IMPLEMENTED, message);
    }
}
