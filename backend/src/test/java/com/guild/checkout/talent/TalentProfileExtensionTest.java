package com.guild.checkout.talent;

import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.talent.admin.TalentAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 主播扩展资料（tasks 1.2 - 1.5）：性别、年龄、地区与两个宣传语标签。
 */
@SpringBootTest
@Transactional
class TalentProfileExtensionTest {

    @Autowired private TalentAdminService adminService;
    @Autowired private TalentService talentService;

    private static TalentAdminService.ProfileForm form(String nickname,
                                                       String gender,
                                                       Integer age,
                                                       String region,
                                                       List<String> promoTags) {
        return new TalentAdminService.ProfileForm(
                nickname, null, null, 0, gender, age, region, promoTags);
    }

    @Test
    @DisplayName("1.2 已有档案不受影响：不填扩展资料时五个字段均为 null")
    void 不填扩展资料时五个字段均为null() {
        Talent t = adminService.create(form("未填资料的主播", null, null, null, null));

        Talent loaded = talentService.require(t.getId());
        assertThat(loaded.getGender()).isNull();
        assertThat(loaded.getAge()).isNull();
        assertThat(loaded.getRegion()).isNull();
        assertThat(loaded.getPromoTag1()).isNull();
        assertThat(loaded.getPromoTag2()).isNull();
    }

    @Test
    @DisplayName("1.3 填写后能正确回显，列名映射无误")
    void 填写后五个字段均正确回显() {
        Talent t = adminService.create(
                form("小雨", "FEMALE", 22, "北京", List.of("声音温柔", "擅长哄睡")));

        // 从库里重新读，而不是信任 create 返回的内存对象——
        // promo_tag_1 这类列名映射错了，只有重新查询才暴露得出来。
        Talent loaded = talentService.require(t.getId());
        assertThat(loaded.getGender()).isEqualTo("FEMALE");
        assertThat(loaded.getAge()).isEqualTo(22);
        assertThat(loaded.getRegion()).isEqualTo("北京");
        assertThat(loaded.getPromoTag1()).isEqualTo("声音温柔");
        assertThat(loaded.getPromoTag2()).isEqualTo("擅长哄睡");
    }

    @Test
    @DisplayName("1.4 宣传语标签超过两个被拒绝，且提示上限为两个")
    void 宣传语标签超过两个被拒绝() {
        assertThatThrownBy(() -> adminService.create(
                form("贪心的主播", null, null, null, List.of("一", "二", "三"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多 2 个");
    }

    @Test
    @DisplayName("1.4 空白标签不计入数量，留空输入框不会被误判超限")
    void 空白标签不计入数量() {
        Talent t = adminService.create(
                form("留白的主播", null, null, null, List.of("声音温柔", "   ", "")));

        Talent loaded = talentService.require(t.getId());
        assertThat(loaded.getPromoTag1()).isEqualTo("声音温柔");
        assertThat(loaded.getPromoTag2()).isNull();
    }

    @Test
    @DisplayName("1.4 单个标签超长被拒绝")
    void 单个标签超长被拒绝() {
        assertThatThrownBy(() -> adminService.create(
                form("话多的主播", null, null, null, List.of("这个标签明显超过了十六个字符的长度上限"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不超过 16 字符");
    }

    @Test
    @DisplayName("1.3 性别只接受 FEMALE 与 MALE，大小写不敏感")
    void 性别取值校验() {
        Talent t = adminService.create(form("小写性别", "female", null, null, null));
        assertThat(talentService.require(t.getId()).getGender()).isEqualTo("FEMALE");

        assertThatThrownBy(() -> adminService.create(form("非法性别", "OTHER", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FEMALE 或 MALE");
    }

    @Test
    @DisplayName("1.3 年龄越界被拒绝")
    void 年龄越界被拒绝() {
        assertThatThrownBy(() -> adminService.create(form("超龄", null, 200, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("年龄需在");

        assertThatThrownBy(() -> adminService.create(form("零岁", null, 0, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("年龄需在");
    }

    @Test
    @DisplayName("1.3 传空列表可把宣传语标签清空")
    void 传空列表可清空宣传语标签() {
        Talent t = adminService.create(
                form("先填后清", null, null, null, List.of("甲", "乙")));

        adminService.update(t.getId(), new TalentAdminService.ProfileForm(
                null, null, null, null, null, null, null, List.of()));

        // MyBatis-Plus 的 updateById 会跳过 null 字段，清空必须走显式 set；
        // 这条用例就是守住那个坑。
        Talent loaded = talentService.require(t.getId());
        assertThat(loaded.getPromoTag1()).isNull();
        assertThat(loaded.getPromoTag2()).isNull();
    }

    @Test
    @DisplayName("1.3 只改其中一项时，其余扩展资料保持不变")
    void 单项更新不影响其余字段() {
        Talent t = adminService.create(
                form("小雨", "FEMALE", 22, "北京", List.of("声音温柔", "擅长哄睡")));

        adminService.update(t.getId(), new TalentAdminService.ProfileForm(
                null, null, null, null, null, null, "上海", null));

        Talent loaded = talentService.require(t.getId());
        assertThat(loaded.getRegion()).isEqualTo("上海");
        assertThat(loaded.getGender()).isEqualTo("FEMALE");
        assertThat(loaded.getAge()).isEqualTo(22);
        assertThat(loaded.getPromoTag1()).isEqualTo("声音温柔");
        assertThat(loaded.getPromoTag2()).isEqualTo("擅长哄睡");
    }
}
