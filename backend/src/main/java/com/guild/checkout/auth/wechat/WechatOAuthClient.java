package com.guild.checkout.auth.wechat;

/**
 * 微信网页授权端口。订单与会话逻辑只依赖本接口，不依赖具体是真实微信还是本机桩。
 */
public interface WechatOAuthClient {

    /**
     * 构造微信授权页地址。
     *
     * @param redirectUri 授权后微信回跳的完整地址
     * @param state       防重放串，回调时原样带回
     */
    String buildAuthorizeUrl(String redirectUri, String state);

    /**
     * 用授权码换取用户标识与资料。
     *
     * @throws WechatOAuthException 换取失败
     */
    WechatUserInfo exchangeCode(String code);
}
