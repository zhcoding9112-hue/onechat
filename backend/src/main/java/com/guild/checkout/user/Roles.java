package com.guild.checkout.user;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 角色位的读写。所有对 {@code app_user.roles} 的操作都走这里，
 * 避免位运算散落在业务代码中。
 */
public final class Roles {

    /** 新建用户的默认角色：仅粉丝。 */
    public static final int DEFAULT = UserRole.FAN.bit();

    /** 全部合法位的并集，用于剔除脏数据中的未知位。 */
    private static final int ALL_BITS =
            UserRole.FAN.bit() | UserRole.TALENT.bit() | UserRole.OPERATOR.bit();

    private Roles() {
    }

    /** 由若干角色组成位值。 */
    public static int of(UserRole... roles) {
        int value = 0;
        for (UserRole role : roles) {
            value |= role.bit();
        }
        return value;
    }

    /** 由角色集合组成位值。 */
    public static int of(Collection<UserRole> roles) {
        int value = 0;
        for (UserRole role : roles) {
            value |= role.bit();
        }
        return value;
    }

    /** 判断位值是否包含某角色。 */
    public static boolean has(int value, UserRole role) {
        return (value & role.bit()) != 0;
    }

    /** 判断位值是否同时包含全部给定角色。 */
    public static boolean hasAll(int value, UserRole... roles) {
        for (UserRole role : roles) {
            if (!has(value, role)) {
                return false;
            }
        }
        return true;
    }

    /** 判断位值是否包含任一给定角色。 */
    public static boolean hasAny(int value, UserRole... roles) {
        for (UserRole role : roles) {
            if (has(value, role)) {
                return true;
            }
        }
        return false;
    }

    /** 增加角色，已有则原样返回。 */
    public static int add(int value, UserRole role) {
        return value | role.bit();
    }

    /** 移除角色，没有则原样返回。 */
    public static int remove(int value, UserRole role) {
        return value & ~role.bit();
    }

    /** 展开为角色集合，未知位被忽略。 */
    public static Set<UserRole> toSet(int value) {
        EnumSet<UserRole> set = EnumSet.noneOf(UserRole.class);
        for (UserRole role : UserRole.values()) {
            if (has(value, role)) {
                set.add(role);
            }
        }
        return set;
    }

    /** 剔除未知位，用于防御库中的脏数据。 */
    public static int sanitize(int value) {
        return value & ALL_BITS;
    }
}
