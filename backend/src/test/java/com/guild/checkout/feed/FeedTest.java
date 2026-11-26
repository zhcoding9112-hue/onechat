package com.guild.checkout.feed;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内容社区（tasks 9.1 - 9.7）。
 *
 * 并发点赞幂等单独放在 {@link FeedConcurrencyTest}：它必须在真实提交下跑。
 */
@SpringBootTest
@Transactional
class FeedTest {

    @Autowired private FeedService feedService;
    @Autowired private ReportService reportService;
    @Autowired private CrushWallService crushWallService;
    @Autowired private FeedPostMapper postMapper;
    @Autowired private FeedLikeMapper likeMapper;
    @Autowired private ContentReportMapper reportMapper;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private AppUserService userService;
    @Autowired private com.guild.checkout.talent.TalentFavoriteService favoriteService;

    private long fanId;
    private long otherFanId;
    private long operatorId;
    private long talentId;

    @BeforeEach
    void setUp() {
        fanId = newUser("ff_").getId();
        otherFanId = newUser("fo_").getId();
        operatorId = newUser("fp_").getId();
        talentId = newTalent("动态主播");
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, "测试用户", null);
    }

    private long newTalent(String nickname) {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        return t.getId();
    }

    private FeedPost publish(String content, List<String> images) {
        return feedService.publish(talentId, content, images);
    }

    @Test
    @DisplayName("关注动态：翻页总数一致、用户隔离、取消关注与下架立即生效")
    void 关注动态按用户隔离且计数一致() {
        FeedPost first = publish("关注第一条", List.of());
        FeedPost second = publish("关注第二条", List.of());
        favoriteService.add(fanId, talentId);
        assertThat(feedService.countVisible(fanId, true)).isEqualTo(2);
        assertThat(feedService.square(fanId, 0, 1, true)).extracting(p -> p.post().getId()).containsExactly(second.getId());
        assertThat(feedService.square(fanId, 1, 1, true)).extracting(p -> p.post().getId()).containsExactly(first.getId());
        assertThat(feedService.square(otherFanId, 0, 10, true)).isEmpty();
        feedService.removePost(second.getId(), operatorId, "验证下架");
        assertThat(feedService.countVisible(fanId, true)).isEqualTo(1);
        favoriteService.remove(fanId, talentId);
        assertThat(feedService.square(fanId, 0, 10, true)).isEmpty();
        assertThat(feedService.countVisible(fanId, true)).isZero();
    }

    // ===== 9.1 发布与图片上限 =====

    @Test
    @DisplayName("9.1 发布后可在广场读到，记录发布者与发布时间")
    void 发布后广场可见() {
        FeedPost post = publish("今天天气不错", List.of("/uploads/a.jpg"));

        assertThat(post.getTalentId()).isEqualTo(talentId);
        assertThat(post.getCreatedAt()).isNotNull();

        List<FeedService.PostDetail> square = feedService.square(fanId, 0, 10);
        FeedService.PostDetail mine = square.stream()
                .filter(d -> d.post().getId().equals(post.getId())).findFirst().orElseThrow();
        assertThat(mine.images()).containsExactly("/uploads/a.jpg");
        assertThat(mine.talent().getId()).isEqualTo(talentId);
    }

    @Test
    @DisplayName("9.1 插入后返回的实体自带发布时间，而不是等下次读库才有")
    void 返回的实体自带发布时间() {
        FeedPost post = publish("刚发的", null);
        FeedComment comment = feedService.comment(fanId, post.getId(), "刚评的");

        // 建表时有 DEFAULT CURRENT_TIMESTAMP，但 MyBatis-Plus 的 insert 不回写数据库默认值；
        // 没有自动填充时，这两个值都会是 null，接口响应里的时间就是空的
        assertThat(post.getCreatedAt()).isNotNull();
        assertThat(comment.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("9.1 超出图片上限时被拒绝并提示上限数量")
    void 超出图片上限被拒绝() {
        int max = feedService.setting().getMaxImages();
        List<String> tooMany = java.util.stream.IntStream.rangeClosed(0, max)
                .mapToObj(i -> "/uploads/" + i + ".jpg").toList();

        assertThatThrownBy(() -> publish("图太多", tooMany))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多 " + max + " 张");
    }

    @Test
    @DisplayName("9.1 恰好等于上限是允许的")
    void 恰好等于上限允许() {
        int max = feedService.setting().getMaxImages();
        List<String> exactly = java.util.stream.IntStream.range(0, max)
                .mapToObj(i -> "/uploads/" + i + ".jpg").toList();

        assertThat(publish(null, exactly).getId()).isNotNull();
    }

    @Test
    @DisplayName("9.1 上限可配：调小后原先允许的张数会被拒绝")
    void 上限可配() {
        feedService.updateMaxImages(2);

        assertThat(publish(null, List.of("/a.jpg", "/b.jpg")).getId()).isNotNull();
        assertThatThrownBy(() -> publish(null, List.of("/a.jpg", "/b.jpg", "/c.jpg")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多 2 张");
    }

    @Test
    @DisplayName("9.1 图片按提交顺序展示")
    void 图片保持顺序() {
        FeedPost post = publish(null, List.of("/1.jpg", "/2.jpg", "/3.jpg"));

        assertThat(feedService.detail(post.getId(), fanId).images())
                .containsExactly("/1.jpg", "/2.jpg", "/3.jpg");
    }

    // ===== 9.2 空内容 =====

    @Test
    @DisplayName("9.2 既无文字也无图片时被拒绝")
    void 空内容被拒绝() {
        assertThatThrownBy(() -> publish(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> publish("   ", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("9.2 只有文字或只有图片都可以发")
    void 单一内容可发() {
        assertThat(publish("只有文字", null).getId()).isNotNull();
        assertThat(publish(null, List.of("/only.jpg")).getId()).isNotNull();
    }

    @Test
    @DisplayName("9.2 全是空白串的图片列表等同于没有图片")
    void 空白图片不算内容() {
        assertThatThrownBy(() -> publish("   ", java.util.Arrays.asList("", "   ", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    // ===== 9.3 广场与点赞 =====

    @Test
    @DisplayName("9.3 广场按发布时间倒序")
    void 广场倒序() {
        FeedPost first = publish("第一条", null);
        FeedPost second = publish("第二条", null);
        FeedPost third = publish("第三条", null);

        List<Long> ids = feedService.square(fanId, 0, 50).stream()
                .map(d -> d.post().getId())
                .filter(id -> id.equals(first.getId()) || id.equals(second.getId())
                        || id.equals(third.getId()))
                .toList();
        assertThat(ids).containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    @DisplayName("9.3 分页不重不漏")
    void 分页不重不漏() {
        for (int i = 0; i < 5; i++) {
            publish("第 " + i + " 条", null);
        }
        List<Long> page0 = feedService.square(fanId, 0, 2).stream()
                .map(d -> d.post().getId()).toList();
        List<Long> page1 = feedService.square(fanId, 1, 2).stream()
                .map(d -> d.post().getId()).toList();

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
        assertThat(page0).doesNotContainAnyElementsOf(page1);
    }

    @Test
    @DisplayName("9.3 重复点赞不重复计数，返回成功")
    void 点赞幂等() {
        FeedPost post = publish("求赞", null);

        feedService.like(fanId, post.getId());
        feedService.like(fanId, post.getId());
        feedService.like(fanId, post.getId());

        assertThat(feedService.likeCount(post.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("9.3 不同粉丝的点赞各自计数")
    void 多人点赞各自计数() {
        FeedPost post = publish("求赞", null);

        feedService.like(fanId, post.getId());
        feedService.like(otherFanId, post.getId());

        assertThat(feedService.likeCount(post.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("9.3 取消点赞后计数回落，再点可以重新点上")
    void 取消点赞() {
        FeedPost post = publish("求赞", null);
        feedService.like(fanId, post.getId());

        feedService.unlike(fanId, post.getId());
        assertThat(feedService.likeCount(post.getId())).isZero();

        feedService.like(fanId, post.getId());
        assertThat(feedService.likeCount(post.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("9.3 唯一索引兜底：绕过服务层直插重复点赞会被数据库拒绝")
    void 点赞唯一索引兜底() {
        FeedPost post = publish("求赞", null);
        feedService.like(fanId, post.getId());

        FeedLike dup = new FeedLike();
        dup.setPostId(post.getId());
        dup.setUserId(fanId);
        assertThatThrownBy(() -> likeMapper.insert(dup))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    @DisplayName("9.3 「我点过赞」只对本人为真")
    void 我点过赞标记() {
        FeedPost post = publish("求赞", null);
        feedService.like(fanId, post.getId());

        assertThat(feedService.detail(post.getId(), fanId).likedByMe()).isTrue();
        assertThat(feedService.detail(post.getId(), otherFanId).likedByMe()).isFalse();
    }

    // ===== 9.4 评论 =====

    @Test
    @DisplayName("9.4 评论后可读回，按时间正序")
    void 评论正序() {
        FeedPost post = publish("聊聊", null);
        feedService.comment(fanId, post.getId(), "第一条评论");
        feedService.comment(otherFanId, post.getId(), "第二条评论");

        assertThat(feedService.comments(post.getId()).stream().map(FeedComment::getContent))
                .containsExactly("第一条评论", "第二条评论");
    }

    @Test
    @DisplayName("9.4 空评论被拒绝")
    void 空评论被拒绝() {
        FeedPost post = publish("聊聊", null);

        assertThatThrownBy(() -> feedService.comment(fanId, post.getId(), "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("9.4 动态里带着主播 id，前端据此跳转详情页")
    void 动态带主播id() {
        FeedPost post = publish("点我头像", null);

        FeedService.PostDetail d = feedService.detail(post.getId(), fanId);
        assertThat(d.post().getTalentId()).isEqualTo(talentId);
        assertThat(d.talent().getNickname()).isNotBlank();
    }

    @Test
    @DisplayName("9.4 评论数只统计可见评论")
    void 评论数不含已下架() {
        FeedPost post = publish("聊聊", null);
        FeedComment keep = feedService.comment(fanId, post.getId(), "留着");
        FeedComment gone = feedService.comment(otherFanId, post.getId(), "要被下架");

        feedService.removeComment(gone.getId(), operatorId, "违规");

        assertThat(feedService.detail(post.getId(), fanId).commentCount()).isEqualTo(1);
        assertThat(feedService.comments(post.getId()).stream().map(FeedComment::getId))
                .containsExactly(keep.getId());
    }

    // ===== 9.5 心动墙 =====

    @Test
    @DisplayName("9.5 运营挑选后粉丝刷新即可见，调整顺序立刻生效")
    void 心动墙即改即生效() {
        long second = newTalent("心动二");
        crushWallService.pick(talentId, 1);
        crushWallService.pick(second, 2);

        assertThat(crushWallService.wall(10).stream().map(Talent::getId))
                .containsSubsequence(talentId, second);

        // 调换顺序，无需任何刷新动作
        crushWallService.pick(second, 0);

        assertThat(crushWallService.wall(10).stream().map(Talent::getId))
                .containsSubsequence(second, talentId);
    }

    @Test
    @DisplayName("9.5 移出心动墙后粉丝不再看到")
    void 移出心动墙() {
        crushWallService.pick(talentId, 0);
        assertThat(crushWallService.wall(10).stream().map(Talent::getId)).contains(talentId);

        crushWallService.unpick(talentId);
        assertThat(crushWallService.wall(10).stream().map(Talent::getId)).doesNotContain(talentId);
    }

    @Test
    @DisplayName("9.5 已下架的主播不出现在心动墙上")
    void 下架主播不上墙() {
        crushWallService.pick(talentId, 0);
        talentAdminService.setListed(talentId, false);

        assertThat(crushWallService.wall(10).stream().map(Talent::getId)).doesNotContain(talentId);
    }

    @Test
    @DisplayName("9.5 重复挑选同一主播不产生第二行")
    void 重复挑选不重复() {
        crushWallService.pick(talentId, 0);
        crushWallService.pick(talentId, 5);

        assertThat(crushWallService.listPicks().stream()
                .filter(p -> p.getTalentId().equals(talentId)).count()).isEqualTo(1);
    }

    // ===== 9.6 举报与下架 =====

    @Test
    @DisplayName("9.6 举报后进入待处理队列，理由被记录")
    void 举报进队列() {
        FeedPost post = publish("有问题的内容", null);

        ContentReport report = reportService.report(
                fanId, ContentReport.TARGET_POST, post.getId(), "涉嫌违规");

        assertThat(report.getStatus()).isEqualTo(ContentReport.PENDING);
        assertThat(report.getReason()).isEqualTo("涉嫌违规");
        assertThat(reportService.pending(100).stream().map(ContentReport::getId))
                .contains(report.getId());
    }

    @Test
    @DisplayName("9.6 举报理由为必填")
    void 举报理由必填() {
        FeedPost post = publish("内容", null);

        assertThatThrownBy(() -> reportService.report(
                fanId, ContentReport.TARGET_POST, post.getId(), "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必填");
    }

    @Test
    @DisplayName("9.6 同一人重复举报同一对象只记一条")
    void 重复举报只记一条() {
        FeedPost post = publish("内容", null);

        reportService.report(fanId, ContentReport.TARGET_POST, post.getId(), "第一次");
        reportService.report(fanId, ContentReport.TARGET_POST, post.getId(), "第二次");

        assertThat(reportMapper.selectCount(Wrappers.<ContentReport>lambdaQuery()
                .eq(ContentReport::getTargetType, ContentReport.TARGET_POST)
                .eq(ContentReport::getTargetId, post.getId()))).isEqualTo(1);
    }

    @Test
    @DisplayName("9.6 下架后该动态对粉丝立即不可见")
    void 下架后粉丝不可见() {
        FeedPost post = publish("要被下架的", null);
        assertThat(feedService.square(fanId, 0, 50).stream()
                .map(d -> d.post().getId())).contains(post.getId());

        feedService.removePost(post.getId(), operatorId, "违规内容");

        assertThat(feedService.square(fanId, 0, 50).stream()
                .map(d -> d.post().getId())).doesNotContain(post.getId());
        assertThatThrownBy(() -> feedService.detail(post.getId(), fanId))
                .isInstanceOf(NotFoundException.class);
        assertThat(feedService.byTalent(talentId, fanId, 50).stream()
                .map(d -> d.post().getId())).doesNotContain(post.getId());
    }

    @Test
    @DisplayName("9.6 已下架的动态不能再被点赞、评论或举报")
    void 下架后不能再互动() {
        FeedPost post = publish("要被下架的", null);
        feedService.removePost(post.getId(), operatorId, "违规内容");

        assertThatThrownBy(() -> feedService.like(fanId, post.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> feedService.comment(fanId, post.getId(), "还能评吗"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> reportService.report(
                fanId, ContentReport.TARGET_POST, post.getId(), "再举报一次"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("9.6 下架原因为必填")
    void 下架原因必填() {
        FeedPost post = publish("内容", null);

        assertThatThrownBy(() -> feedService.removePost(post.getId(), operatorId, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必填");
    }

    @Test
    @DisplayName("9.6 处理一条举报会把同一对象上的全部待处理举报一并关掉")
    void 处理举报连带关闭() {
        FeedPost post = publish("被很多人举报", null);
        ContentReport first = reportService.report(
                fanId, ContentReport.TARGET_POST, post.getId(), "理由一");
        reportService.report(otherFanId, ContentReport.TARGET_POST, post.getId(), "理由二");

        int closed = reportService.handleByRemoving(first.getId(), operatorId, "确认违规");

        assertThat(closed).isEqualTo(2);
        assertThat(reportService.onTarget(ContentReport.TARGET_POST, post.getId()))
                .allMatch(r -> ContentReport.HANDLED.equals(r.getStatus()));
        assertThat(postMapper.selectById(post.getId()).getStatus()).isEqualTo(FeedPost.REMOVED);
    }

    @Test
    @DisplayName("9.6 驳回举报不下架内容")
    void 驳回不下架() {
        FeedPost post = publish("其实没问题", null);
        ContentReport report = reportService.report(
                fanId, ContentReport.TARGET_POST, post.getId(), "看着不顺眼");

        reportService.dismiss(report.getId(), operatorId, "内容无问题");

        assertThat(postMapper.selectById(post.getId()).getStatus()).isEqualTo(FeedPost.VISIBLE);
        assertThat(reportMapper.selectById(report.getId()).getStatus())
                .isEqualTo(ContentReport.DISMISSED);
    }

    // ===== 9.7 软下架可追溯 =====

    @Test
    @DisplayName("9.7 运营能看到已下架动态的原始内容与下架操作者、时间、原因")
    void 下架可追溯() {
        FeedPost post = publish("原始内容在这里", List.of("/evidence.jpg"));
        feedService.removePost(post.getId(), operatorId, "涉嫌违规");

        FeedService.PostDetail audit = feedService.detailForOperator(post.getId());

        // 原始内容仍在
        assertThat(audit.post().getContent()).isEqualTo("原始内容在这里");
        assertThat(audit.images()).containsExactly("/evidence.jpg");
        // 处理记录三件套齐全
        assertThat(audit.post().getStatus()).isEqualTo(FeedPost.REMOVED);
        assertThat(audit.post().getRemovedBy()).isEqualTo(operatorId);
        assertThat(audit.post().getRemovedAt()).isNotNull();
        assertThat(audit.post().getRemoveReason()).isEqualTo("涉嫌违规");
    }

    @Test
    @DisplayName("9.7 已下架的动态仍出现在运营列表里")
    void 运营列表含已下架() {
        FeedPost post = publish("要被下架的", null);
        feedService.removePost(post.getId(), operatorId, "违规");

        assertThat(feedService.listAllForOperator(FeedPost.REMOVED, 100).stream()
                .map(FeedPost::getId)).contains(post.getId());
        assertThat(feedService.listAllForOperator(null, 100).stream()
                .map(FeedPost::getId)).contains(post.getId());
    }

    @Test
    @DisplayName("9.7 重复下架不覆盖首次的留痕")
    void 重复下架不覆盖留痕() {
        FeedPost post = publish("内容", null);
        feedService.removePost(post.getId(), operatorId, "第一次下架");
        var firstAt = postMapper.selectById(post.getId()).getRemovedAt();

        long otherOperator = newUser("fp2_").getId();
        feedService.removePost(post.getId(), otherOperator, "第二次下架");

        FeedPost reloaded = postMapper.selectById(post.getId());
        assertThat(reloaded.getRemovedBy()).isEqualTo(operatorId);
        assertThat(reloaded.getRemoveReason()).isEqualTo("第一次下架");
        assertThat(reloaded.getRemovedAt()).isEqualTo(firstAt);
    }

    @Test
    @DisplayName("9.7 已下架的评论原文与留痕在运营侧仍可读")
    void 评论下架可追溯() {
        FeedPost post = publish("聊聊", null);
        FeedComment comment = feedService.comment(fanId, post.getId(), "评论原文");

        feedService.removeComment(comment.getId(), operatorId, "辱骂");

        List<FeedComment> all = feedService.allCommentsForOperator(post.getId());
        FeedComment audited = all.stream()
                .filter(c -> c.getId().equals(comment.getId())).findFirst().orElseThrow();
        assertThat(audited.getContent()).isEqualTo("评论原文");
        assertThat(audited.getRemovedBy()).isEqualTo(operatorId);
        assertThat(audited.getRemoveReason()).isEqualTo("辱骂");
        assertThat(audited.getRemovedAt()).isNotNull();
    }
}
