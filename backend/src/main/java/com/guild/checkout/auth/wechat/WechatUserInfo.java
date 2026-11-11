package com.guild.checkout.auth.wechat;

/**
 * 微信侧返回的用户标识与资料。
 * 昵称与头像允许为空：资料接口失败不得阻断登录。
 */
public record WechatUserInfo(
        String openId,
        String unionId,
        String nickname,
        String avatarUrl) {
}
