package com.guild.checkout.user;

/**
 * 用户角色。以位标记存入 {@code app_user.roles}，可叠加。
 * 位值一经确定不得更改，数据库中已有数据依赖它。
 */
public enum UserRole {

    /** 粉丝。任何授权登录的用户都具备。 */
    FAN(1),

    /** 主播。由运营把用户关联到主播档案时授予。 */
    TALENT(1 << 1),

    /** 运营。由运营在后台授予。 */
    OPERATOR(1 << 2);

    private final int bit;

    UserRole(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }
}
