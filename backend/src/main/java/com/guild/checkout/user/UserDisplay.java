package com.guild.checkout.user;

/**
 * 用户展示资料的占位处理。
 *
 * 微信资料接口失败、或用户以 snsapi_base 授权时拿不到昵称头像。此时不能展示空白，
 * 也不能把登录拦下来——占位在服务端统一生成，避免粉丝端、主播端、运营后台各写一套。
 */
public final class UserDisplay {

    /** 昵称占位。带 id 后缀，便于运营在后台区分不同的无昵称用户。 */
    public static final String NICKNAME_PLACEHOLDER_PREFIX = "微信用户";

    /** 头像占位。前端静态资源，不依赖外部图床。 */
    public static final String AVATAR_PLACEHOLDER = "/images/avatar-placeholder.svg";

    private UserDisplay() {
    }

    /** 昵称为空时返回占位昵称。 */
    public static String nickname(String nickname, Long userId) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return userId == null
                ? NICKNAME_PLACEHOLDER_PREFIX
                : NICKNAME_PLACEHOLDER_PREFIX + suffix(userId);
    }

    /** 头像为空时返回占位头像。 */
    public static String avatarUrl(String avatarUrl) {
        return avatarUrl != null && !avatarUrl.isBlank() ? avatarUrl : AVATAR_PLACEHOLDER;
    }

    /** 是否用的是占位昵称。运营后台据此提示资料缺失。 */
    public static boolean isPlaceholderNickname(String nickname) {
        return nickname == null || nickname.isBlank();
    }

    /** 取 id 后四位作后缀，位数不足则补零，保证长度一致便于列表对齐。 */
    private static String suffix(long userId) {
        return String.format("%04d", Math.floorMod(userId, 10000L));
    }
}
