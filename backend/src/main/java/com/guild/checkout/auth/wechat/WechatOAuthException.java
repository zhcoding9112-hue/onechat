package com.guild.checkout.auth.wechat;

/** 微信网页授权调用失败。 */
public class WechatOAuthException extends RuntimeException {

    public WechatOAuthException(String message) {
        super(message);
    }

    public WechatOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
