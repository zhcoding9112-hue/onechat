package com.guild.checkout.pricing;

import com.guild.checkout.common.exception.BusinessException;

/** 取价失败：档位不可购、项目已停用等。 */
public class PricingException extends BusinessException {

    public PricingException(String message) {
        super(message);
    }
}
