package com.guild.checkout.user;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色位读写。位值是落库数据，错一位就会把粉丝读成运营，因此逐条断言。
 */
class RolesTest {

    @Test
    void 位值固定不得变更() {
        assertThat(UserRole.FAN.bit()).isEqualTo(1);
        assertThat(UserRole.TALENT.bit()).isEqualTo(2);
        assertThat(UserRole.OPERATOR.bit()).isEqualTo(4);
    }

    @Test
    void 新建用户默认仅粉丝() {
        assertThat(Roles.DEFAULT).isEqualTo(1);
        assertThat(Roles.toSet(Roles.DEFAULT)).containsExactly(UserRole.FAN);
    }

    @Test
    void 组合与展开互为逆运算() {
        int value = Roles.of(UserRole.FAN, UserRole.TALENT);
        assertThat(value).isEqualTo(3);
        assertThat(Roles.toSet(value)).containsExactlyInAnyOrder(UserRole.FAN, UserRole.TALENT);
        assertThat(Roles.of(Roles.toSet(value))).isEqualTo(value);
    }

    @Test
    void 单用户可同时持有粉丝主播运营三个角色() {
        int value = Roles.of(UserRole.FAN, UserRole.TALENT, UserRole.OPERATOR);
        assertThat(value).isEqualTo(7);
        assertThat(Roles.hasAll(value, UserRole.FAN, UserRole.TALENT, UserRole.OPERATOR)).isTrue();
        assertThat(Roles.toSet(value))
                .containsExactlyInAnyOrder(UserRole.FAN, UserRole.TALENT, UserRole.OPERATOR);
    }

    @Test
    void 增删角色互不影响其他位() {
        int value = Roles.DEFAULT;

        value = Roles.add(value, UserRole.TALENT);
        assertThat(Roles.toSet(value)).containsExactlyInAnyOrder(UserRole.FAN, UserRole.TALENT);

        value = Roles.remove(value, UserRole.TALENT);
        assertThat(Roles.toSet(value)).containsExactly(UserRole.FAN);
        assertThat(Roles.has(value, UserRole.FAN)).isTrue();
    }

    @Test
    void 重复增加与重复移除都幂等() {
        int once = Roles.add(Roles.DEFAULT, UserRole.OPERATOR);
        assertThat(Roles.add(once, UserRole.OPERATOR)).isEqualTo(once);

        int removed = Roles.remove(once, UserRole.OPERATOR);
        assertThat(Roles.remove(removed, UserRole.OPERATOR)).isEqualTo(removed);
    }

    @Test
    void hasAny与hasAll语义正确() {
        int fanOnly = Roles.DEFAULT;
        assertThat(Roles.hasAny(fanOnly, UserRole.TALENT, UserRole.OPERATOR)).isFalse();
        assertThat(Roles.hasAny(fanOnly, UserRole.FAN, UserRole.OPERATOR)).isTrue();
        assertThat(Roles.hasAll(fanOnly, UserRole.FAN, UserRole.TALENT)).isFalse();
    }

    @Test
    void 空位值不含任何角色() {
        assertThat(Roles.toSet(0)).isEmpty();
        assertThat(Roles.has(0, UserRole.FAN)).isFalse();
    }

    @Test
    void 未知位被剔除而不会误判为角色() {
        int dirty = Roles.of(UserRole.FAN) | 0b1000_0000;
        assertThat(Roles.sanitize(dirty)).isEqualTo(UserRole.FAN.bit());
        assertThat(Roles.toSet(dirty)).containsExactly(UserRole.FAN);
    }

    @Test
    void 实体上的角色读写与位运算一致() {
        AppUser user = new AppUser();
        user.setRoles(Roles.DEFAULT);

        assertThat(user.hasRole(UserRole.FAN)).isTrue();
        assertThat(user.hasRole(UserRole.TALENT)).isFalse();

        user.grant(UserRole.TALENT);
        assertThat(user.getRoles()).isEqualTo(3);
        assertThat(user.roleSet()).containsExactlyInAnyOrder(UserRole.FAN, UserRole.TALENT);

        user.revoke(UserRole.FAN);
        assertThat(user.roleSet()).containsExactly(UserRole.TALENT);
    }

    @Test
    void 实体角色为空时不报错且视为无角色() {
        AppUser user = new AppUser();
        assertThat(user.hasRole(UserRole.FAN)).isFalse();
        assertThat(user.roleSet()).isEqualTo(Set.of());

        user.grant(UserRole.OPERATOR);
        assertThat(user.getRoles()).isEqualTo(UserRole.OPERATOR.bit());
    }
}
