package com.guild.checkout.user.event;

/**
 * 新用户首次建号。
 *
 * 只在真的插入了一条新记录时发布：再次授权刷新资料不发，
 * 并发首次授权被唯一索引挡下的那一侧也不发（赢的那一侧已经发过了）。
 *
 * 用事件而不是在 AppUserService 里直接调发券：注册这件事不该知道有优惠券存在。
 * 以后要在注册时再做点别的（发欢迎语、打埋点），挂监听器即可，不用再改用户代码。
 *
 * @param userId 新用户 id
 * @param openId 微信 openid，供监听方留痕用
 */
public record UserRegistered(long userId, String openId) {
}
