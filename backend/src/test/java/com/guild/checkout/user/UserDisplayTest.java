package com.guild.checkout.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserDisplayTest {

    @Test
    void 有昵称时原样返回() {
        assertThat(UserDisplay.nickname("小张", 7L)).isEqualTo("小张");
        assertThat(UserDisplay.isPlaceholderNickname("小张")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void 昵称缺失时用占位且带id后缀(String nickname) {
        assertThat(UserDisplay.nickname(nickname, 7L)).isEqualTo("微信用户0007");
        assertThat(UserDisplay.isPlaceholderNickname(nickname)).isTrue();
    }

    @Test
    void 占位后缀取id后四位且补零() {
        assertThat(UserDisplay.nickname(null, 1L)).isEqualTo("微信用户0001");
        assertThat(UserDisplay.nickname(null, 1234L)).isEqualTo("微信用户1234");
        assertThat(UserDisplay.nickname(null, 51234L)).isEqualTo("微信用户1234");
    }

    @Test
    void id缺失时占位不带后缀() {
        assertThat(UserDisplay.nickname(null, null)).isEqualTo("微信用户");
    }

    @Test
    void 有头像时原样返回() {
        assertThat(UserDisplay.avatarUrl("https://img/1.png")).isEqualTo("https://img/1.png");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void 头像缺失时用本地占位而非外部图床(String avatar) {
        assertThat(UserDisplay.avatarUrl(avatar))
                .isEqualTo("/images/avatar-placeholder.svg")
                .doesNotStartWith("http");
    }
}
