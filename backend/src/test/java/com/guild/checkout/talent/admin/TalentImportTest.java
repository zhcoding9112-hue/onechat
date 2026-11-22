package com.guild.checkout.talent.admin;

import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 花名册批量导入。
 *
 * 几十位主播一个个在表单里敲，是几小时的机械劳动。这里验的是：
 * 表格能一次导进去，且导错时不会留下半截数据。
 */
/*
 * 刻意不加 @Transactional：这里要验「导错了整批回滚」，
 * 而服务会加入测试的事务——测试不提交的话，回滚与否都看不出区别，
 * 用例会假通过。代价是自己收尾。
 */
@SpringBootTest
class TalentImportTest {

    @Autowired private TalentImportService importService;
    @Autowired private TalentService talentService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private ServiceCatalogAdminService catalogService;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String specCode;
    /** 同一个项目下的另外两档，用来验一行多档多价。 */
    private String specCode15;
    private String specCode60;
    private long itemId;
    /** 本次用例的昵称后缀。开发库里本来就有「阿澈」，不加后缀会找错人。 */
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime() % 1000000);
        String itemCode = "imp" + suffix;
        ServiceItem item = catalogService.createItem(itemCode, "导入测试项目", null, 0);
        itemId = item.getId();
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 20);
        specCode = itemCode + ":" + spec.getCode();
        specCode15 = itemCode + ":" + catalogService.createSpec(
                item.getId(), "m15", "15分钟", 15, 10).getCode();
        specCode60 = itemCode + ":" + catalogService.createSpec(
                item.getId(), "m60", "60分钟", 60, 30).getCode();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        // 本用例造的主播一律带后缀，照着删
        jdbc.update("DELETE p FROM talent_price p JOIN talent t ON t.id = p.talent_id"
                + " WHERE t.nickname LIKE ?", "%" + suffix);
        jdbc.update("DELETE g FROM talent_tag g JOIN talent t ON t.id = g.talent_id"
                + " WHERE t.nickname LIKE ?", "%" + suffix);
        jdbc.update("DELETE FROM talent WHERE nickname LIKE ?", "%" + suffix);
        jdbc.update("DELETE FROM talent_price WHERE service_spec_id IN"
                + " (SELECT id FROM service_spec WHERE service_item_id = ?)", itemId);
        jdbc.update("DELETE FROM service_spec WHERE service_item_id = ?", itemId);
        jdbc.update("DELETE FROM service_item WHERE id = ?", itemId);
    }

    /** 昵称都带本次后缀，避免撞上开发库里已有的同名主播。 */
    private String name(String base) {
        return base + suffix;
    }

    private static final String HEADER = "昵称,性别,年龄,地区,简介,标签,宣传语,排序,服务档位,价格元\n";

    @Test
    @DisplayName("一次导入多位，档案、标签、价格都落下来")
    void 批量导入() {
        var result = importService.importCsv(HEADER
                + name("阿雨") + ",女,23,杭州,温柔耐心,温柔|耐心,深夜电台,10," + specCode + ",25\n"
                + name("阿澈") + ",男,26,成都,游戏带飞,王者|上分,稳上分,20,,\n", false);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isZero();

        Talent yu = findByNickname(name("阿雨"));
        assertThat(yu.getGender()).isEqualTo("FEMALE");
        assertThat(yu.getAge()).isEqualTo(23);
        assertThat(yu.getRegion()).isEqualTo("杭州");
        assertThat(talentService.listTags(yu.getId()).stream().map(t -> t.getName()))
                .containsExactlyInAnyOrder("温柔", "耐心");
        assertThat(priceService.listByTalent(yu.getId())).hasSize(1);
        assertThat(priceService.listByTalent(yu.getId()).get(0).getUnitPriceCents()).isEqualTo(2500);

        // 第二行没填价格，不该凭空造一个
        assertThat(priceService.listByTalent(findByNickname(name("阿澈")).getId())).isEmpty();
    }

    /**
     * 导进来的一律不上架。
     *
     * 封面与试听只能一个个传，导完就上架等于让粉丝端立刻看到一排留白的卡片。
     */
    @Test
    @DisplayName("导入的主播默认不上架")
    void 导入不自动上架() {
        importService.importCsv(HEADER + name("阿雨") + ",女,23,杭州,温柔,温柔,,10,,\n", false);

        assertThat(findByNickname(name("阿雨")).getListed()).isFalse();
    }

    @Test
    @DisplayName("试算只校验不写库")
    void 试算不写库() {
        assertThatThrownBy(() -> importService.importCsv(
                HEADER + name("试算用") + ",女,23,杭州,简介,标签,,0,,\n", true))
                .isInstanceOf(TalentImportService.DryRunComplete.class)
                .satisfies(ex -> {
                    var r = ((TalentImportService.DryRunComplete) ex).result();
                    assertThat(r.dryRun()).isTrue();
                    assertThat(r.created()).isEqualTo(1);
                });

        assertThat(talentService.listAll().stream().map(Talent::getNickname))
                .as("试算过的昵称不该出现在库里")
                .doesNotContain(name("试算用"));
    }

    /**
     * 一行坏，整批不进。
     *
     * 导到一半最难收拾：运营不知道哪些进去了，重导又会撞昵称。
     */
    @Test
    @DisplayName("中间一行出错，整批回滚，前面成功的也不留")
    void 一行出错整批回滚() {
        assertThatThrownBy(() -> importService.importCsv(HEADER
                + name("好的一行") + ",女,23,杭州,简介,标签,,0,,\n"
                + name("坏的一行") + ",女,999,杭州,简介,标签,,0,,\n", false))
                .isInstanceOf(TalentImportService.ImportFailed.class)
                .hasMessageContaining("第 2 行")
                .hasMessageContaining("年龄");

        assertThat(talentService.listAll().stream().map(Talent::getNickname))
                .as("前一行也不能留下")
                .doesNotContain(name("好的一行"));
    }

    @Test
    @DisplayName("档位编码写错，提示现有的有哪些")
    void 档位写错有提示() {
        assertThatThrownBy(() -> importService.importCsv(
                HEADER + name("阿雨") + ",女,23,杭州,简介,标签,,0,不存在的档位,25\n", false))
                .isInstanceOf(TalentImportService.ImportFailed.class)
                .hasMessageContaining("找不到服务档位");
    }

    @Test
    @DisplayName("只填档位不填价格（或反过来）要报错，不能静默跳过")
    void 档位与价格要成对() {
        assertThatThrownBy(() -> importService.importCsv(
                HEADER + name("阿雨") + ",女,23,杭州,简介,标签,,0," + specCode + ",\n", false))
                .isInstanceOf(TalentImportService.ImportFailed.class)
                .hasMessageContaining("要么都填");
    }

    // ---------- 一行多档多价 ----------

    /**
     * 一位主播通常有好几档价。
     *
     * 两列各只能填一格的时候，三十个人四档就是九十次手工补价；
     * 所以两列都按竖线分隔、按位置对应。
     */
    @Test
    @DisplayName("一行填三个档位三个价，三档都落下来")
    void 一行多档多价() {
        var result = importService.importCsv(HEADER
                + name("阿雨") + ",女,23,杭州,温柔,温柔,,10,"
                + specCode15 + "|" + specCode + "|" + specCode60 + ",9|15|25\n", false);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.rows().get(0).message()).contains("3 档");

        long talentId = findByNickname(name("阿雨")).getId();
        assertThat(priceService.listByTalent(talentId))
                .extracting(p -> p.getUnitPriceCents())
                .containsExactlyInAnyOrder(900L, 1500L, 2500L);
    }

    @Test
    @DisplayName("两列个数不等就报错，不替运营猜哪个价配哪一档")
    void 档位与价格个数要相等() {
        assertThatThrownBy(() -> importService.importCsv(HEADER
                + name("阿雨") + ",女,23,杭州,简介,标签,,0,"
                + specCode15 + "|" + specCode + ",15\n", false))
                .isInstanceOf(TalentImportService.ImportFailed.class)
                .hasMessageContaining("个数要一样");
    }

    /** 后一个会把前一个覆盖掉，静默生效最难查。 */
    @Test
    @DisplayName("同一个档位在一行里填两次要报错")
    void 同行重复档位() {
        assertThatThrownBy(() -> importService.importCsv(HEADER
                + name("阿雨") + ",女,23,杭州,简介,标签,,0,"
                + specCode + "|" + specCode + ",15|25\n", false))
                .isInstanceOf(TalentImportService.ImportFailed.class)
                .hasMessageContaining("填了两次");
    }

    /**
     * 「9||15」若按丢弃空项来切，正好剩两项、和两个档位对上，
     * 价格会静默错位落到别的档位上。所以空项必须报错。
     */
    @Test
    @DisplayName("多打一个竖线不能被悄悄吞掉，否则价格会错位")
    void 空项要报错() {
        assertThatThrownBy(() -> importService.importCsv(HEADER
                + name("阿雨") + ",女,23,杭州,简介,标签,,0,"
                + specCode15 + "|" + specCode + ",9||15\n", false))
                .isInstanceOf(TalentImportService.ImportFailed.class)
                .hasMessageContaining("有空项");
    }

    @Test
    @DisplayName("第一行不是表头就拒绝，免得把第一位主播当表头吞掉")
    void 必须有表头() {
        assertThatThrownBy(() -> importService.importCsv(
                name("阿雨") + ",女,23,杭州,简介,标签,,0,,\n", false))
                .hasMessageContaining("第一行必须是表头");
    }

    // ---------- CSV 解析 ----------

    @Test
    @DisplayName("简介里的逗号被引号保护，不会把一行切散")
    void 引号内的逗号() {
        var rows = TalentImportService.parse("a,\"含有,逗号\",c\n");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactly("a", "含有,逗号", "c");
    }

    @Test
    @DisplayName("引号内的换行与转义引号")
    void 引号内的换行() {
        var rows = TalentImportService.parse("a,\"第一行\n第二行\",\"他说\"\"好\"\"\"\n");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactly("a", "第一行\n第二行", "他说\"好\"");
    }

    @Test
    @DisplayName("Excel 存的 BOM 不该混进第一个字段")
    void 吃掉BOM() {
        var rows = TalentImportService.parse("﻿昵称,性别\n");

        assertThat(rows.get(0)[0]).isEqualTo("昵称");
    }

    @Test
    @DisplayName("空行跳过，不当成一位没名字的主播")
    void 跳过空行() {
        var rows = TalentImportService.parse("a,b\n\n\n c,d\n");

        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("价格元转分，不走浮点")
    void 价格转分() {
        assertThat(TalentImportService.yuanToCents("25")).isEqualTo(2500);
        assertThat(TalentImportService.yuanToCents("25.5")).isEqualTo(2550);
        assertThat(TalentImportService.yuanToCents("25.55")).isEqualTo(2555);
        assertThatThrownBy(() -> TalentImportService.yuanToCents("25.555"))
                .hasMessageContaining("最多两位小数");
        assertThatThrownBy(() -> TalentImportService.yuanToCents("0"))
                .hasMessageContaining("大于零");
    }

    private Talent findByNickname(String nickname) {
        return talentService.listAll().stream()
                .filter(t -> nickname.equals(t.getNickname()))
                .findFirst().orElseThrow(() -> new AssertionError("没找到 " + nickname));
    }
}
