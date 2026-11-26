package com.guild.checkout.feed;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 动态广场。
 *
 * 下架是软的：内容原样保留，另记下架人、时间与原因。删行就查不到「这条曾经发过什么」，
 * 纠纷与申诉时无从追溯，而规格明确要求保留记录。
 *
 * 粉丝侧的每个读取路径都只认 VISIBLE，下架即刻生效；运营侧读的是全集，含已下架的。
 */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_COMMENT_LENGTH = 500;

    private final FeedPostMapper postMapper;
    private final FeedPostImageMapper imageMapper;
    private final FeedLikeMapper likeMapper;
    private final FeedCommentMapper commentMapper;
    private final FeedCountMapper countMapper;
    private final FeedSettingMapper settingMapper;
    private final TalentService talentService;

    public FeedService(FeedPostMapper postMapper,
                       FeedPostImageMapper imageMapper,
                       FeedLikeMapper likeMapper,
                       FeedCommentMapper commentMapper,
                       FeedCountMapper countMapper,
                       FeedSettingMapper settingMapper,
                       TalentService talentService) {
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.likeMapper = likeMapper;
        this.commentMapper = commentMapper;
        this.countMapper = countMapper;
        this.settingMapper = settingMapper;
        this.talentService = talentService;
    }

    /** 一条动态连同它的图、计数与「我点过赞吗」。 */
    public record PostDetail(FeedPost post, Talent talent, List<String> images,
                             long likeCount, long commentCount, boolean likedByMe) {
    }

    // ---------- 配置 ----------

    public FeedSetting setting() {
        FeedSetting setting = settingMapper.selectOne(Wrappers.<FeedSetting>lambdaQuery()
                .eq(FeedSetting::getSingletonKey, 1));
        if (setting == null) {
            throw new IllegalStateException("动态配置未初始化");
        }
        return setting;
    }

    @Transactional
    public FeedSetting updateMaxImages(int maxImages) {
        if (maxImages < 1 || maxImages > 20) {
            throw new BusinessException("图片数量上限需在 1 到 20 之间");
        }
        FeedSetting setting = setting();
        setting.setMaxImages(maxImages);
        settingMapper.updateById(setting);
        return setting;
    }

    // ---------- 发布 ----------

    /**
     * 主播发布动态。
     *
     * @param imageUrls 图片地址，按给定顺序展示；可为空
     */
    @Transactional
    public FeedPost publish(long talentId, String content, List<String> imageUrls) {
        talentService.require(talentId);

        String text = content == null ? "" : content.trim();
        List<String> images = imageUrls == null ? List.of()
                : imageUrls.stream().filter(u -> u != null && !u.isBlank()).map(String::trim).toList();

        if (text.isEmpty() && images.isEmpty()) {
            throw new BusinessException("动态内容不能为空");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("正文不超过 %d 字符".formatted(MAX_CONTENT_LENGTH));
        }
        int max = setting().getMaxImages();
        if (images.size() > max) {
            throw new BusinessException("图片最多 %d 张".formatted(max));
        }

        FeedPost post = new FeedPost();
        post.setTalentId(talentId);
        post.setContent(text.isEmpty() ? null : text);
        post.setStatus(FeedPost.VISIBLE);
        postMapper.insert(post);

        for (int i = 0; i < images.size(); i++) {
            FeedPostImage image = new FeedPostImage();
            image.setPostId(post.getId());
            image.setUrl(images.get(i));
            image.setSortOrder(i);
            imageMapper.insert(image);
        }
        log.info("主播 {} 发布动态 {}，{} 张图", talentId, post.getId(), images.size());
        return post;
    }

    // ---------- 浏览 ----------

    /** 广场：按发布时间倒序分页。只返回可见的。 */
    public List<PostDetail> square(Long viewerUserId, int page, int size) {
        return square(viewerUserId, page, size, false);
    }

    public List<PostDetail> square(Long viewerUserId, int page, int size, boolean followed) {
        int limit = Math.min(Math.max(size, 1), 50);
        int offset = Math.max(page, 0) * limit;
        List<FeedPost> posts = postMapper.selectList(visibleQuery(viewerUserId, followed)
                // 同一毫秒内多条由自增 id 兜定顺序，否则翻页会漏条或重条
                .orderByDesc(FeedPost::getId)
                .last("LIMIT " + limit + " OFFSET " + offset));
        return assemble(posts, viewerUserId);
    }

    public long countVisible() { return countVisible(null, false); }

    public long countVisible(Long viewerUserId, boolean followed) {
        return postMapper.selectCount(visibleQuery(viewerUserId, followed));
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FeedPost> visibleQuery(Long viewerUserId, boolean followed) {
        var query = Wrappers.<FeedPost>lambdaQuery().eq(FeedPost::getStatus, FeedPost.VISIBLE);
        if (followed) {
            // 参数绑定当前会话用户，客户端不能指定其他人的关注列表。
            query.apply("talent_id IN (SELECT talent_id FROM talent_favorite WHERE user_id = {0})",
                    viewerUserId == null ? -1L : viewerUserId);
        }
        return query;
    }

    /** 某位主播的动态，用于详情页。 */
    public List<PostDetail> byTalent(long talentId, Long viewerUserId, int limit) {
        List<FeedPost> posts = postMapper.selectList(Wrappers.<FeedPost>lambdaQuery()
                .eq(FeedPost::getTalentId, talentId)
                .eq(FeedPost::getStatus, FeedPost.VISIBLE)
                .orderByDesc(FeedPost::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
        return assemble(posts, viewerUserId);
    }

    public PostDetail detail(long postId, Long viewerUserId) {
        FeedPost post = requireVisible(postId);
        return assemble(List.of(post), viewerUserId).get(0);
    }

    private List<PostDetail> assemble(List<FeedPost> posts, Long viewerUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(FeedPost::getId).toList();

        Map<Long, List<String>> images = new HashMap<>();
        for (FeedPostImage image : imageMapper.selectList(Wrappers.<FeedPostImage>lambdaQuery()
                .in(FeedPostImage::getPostId, postIds)
                .orderByAsc(FeedPostImage::getSortOrder)
                .orderByAsc(FeedPostImage::getId))) {
            images.computeIfAbsent(image.getPostId(), k -> new java.util.ArrayList<>()).add(image.getUrl());
        }

        Map<Long, Long> likes = toCountMap(countMapper.likeCounts(postIds));
        Map<Long, Long> comments = toCountMap(countMapper.commentCounts(postIds));
        Set<Long> likedByMe = viewerUserId == null ? Set.of()
                : Set.copyOf(countMapper.likedPostIds(viewerUserId, postIds));

        Map<Long, Talent> talents = talentService.findByIds(
                posts.stream().map(FeedPost::getTalentId).toList());

        return posts.stream().map(p -> new PostDetail(
                p, talents.get(p.getTalentId()),
                images.getOrDefault(p.getId(), List.of()),
                likes.getOrDefault(p.getId(), 0L),
                comments.getOrDefault(p.getId(), 0L),
                likedByMe.contains(p.getId()))).toList();
    }

    private static Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            out.put(((Number) row.get("postId")).longValue(), ((Number) row.get("c")).longValue());
        }
        return out;
    }

    // ---------- 点赞 ----------

    /**
     * 点赞。幂等：已赞过再点返回成功，不重复计数。
     *
     * 幂等靠唯一索引而不是「先查后插」——先查后插在并发下会插出两行，
     * 计数就多出一个。
     */
    @Transactional
    public void like(long userId, long postId) {
        requireVisible(postId);
        FeedLike like = new FeedLike();
        like.setPostId(postId);
        like.setUserId(userId);
        try {
            likeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            // 已经赞过了，正是想要的结果
        }
    }

    /** 取消点赞。没赞过也返回成功。 */
    @Transactional
    public void unlike(long userId, long postId) {
        likeMapper.delete(Wrappers.<FeedLike>lambdaQuery()
                .eq(FeedLike::getPostId, postId)
                .eq(FeedLike::getUserId, userId));
    }

    public long likeCount(long postId) {
        return likeMapper.selectCount(Wrappers.<FeedLike>lambdaQuery()
                .eq(FeedLike::getPostId, postId));
    }

    // ---------- 评论 ----------

    @Transactional
    public FeedComment comment(long userId, long postId, String content) {
        requireVisible(postId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException("评论内容不能为空");
        }
        if (text.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException("评论不超过 %d 字符".formatted(MAX_COMMENT_LENGTH));
        }

        FeedComment comment = new FeedComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(text);
        comment.setStatus(FeedComment.VISIBLE);
        commentMapper.insert(comment);
        return comment;
    }

    /** 某条动态的可见评论，按时间正序。 */
    public List<FeedComment> comments(long postId) {
        return commentMapper.selectList(Wrappers.<FeedComment>lambdaQuery()
                .eq(FeedComment::getPostId, postId)
                .eq(FeedComment::getStatus, FeedComment.VISIBLE)
                .orderByAsc(FeedComment::getId));
    }

    // ---------- 下架（运营） ----------

    /** 下架动态。内容保留，只是对粉丝不可见。重复下架不覆盖首次的留痕。 */
    @Transactional
    public FeedPost removePost(long postId, long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("下架原因为必填");
        }
        FeedPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new NotFoundException("动态不存在: " + postId);
        }
        if (FeedPost.REMOVED.equals(post.getStatus())) {
            return post;
        }
        post.setStatus(FeedPost.REMOVED);
        post.setRemovedBy(operatorId);
        post.setRemovedAt(LocalDateTime.now());
        post.setRemoveReason(reason.trim());
        postMapper.updateById(post);
        log.info("动态 {} 已下架，操作者 {}，原因：{}", postId, operatorId, reason);
        return post;
    }

    @Transactional
    public FeedComment removeComment(long commentId, long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("下架原因为必填");
        }
        FeedComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new NotFoundException("评论不存在: " + commentId);
        }
        if (FeedComment.REMOVED.equals(comment.getStatus())) {
            return comment;
        }
        comment.setStatus(FeedComment.REMOVED);
        comment.setRemovedBy(operatorId);
        comment.setRemovedAt(LocalDateTime.now());
        comment.setRemoveReason(reason.trim());
        commentMapper.updateById(comment);
        return comment;
    }

    /** 运营侧：读全集，含已下架的。粉丝侧没有任何路径能走到这里。 */
    public List<FeedPost> listAllForOperator(String status, int limit) {
        var query = Wrappers.<FeedPost>lambdaQuery().orderByDesc(FeedPost::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200));
        if (status != null && !status.isBlank()) {
            query.eq(FeedPost::getStatus, status);
        }
        return postMapper.selectList(query);
    }

    /** 运营侧：读一条动态的原始内容，哪怕它已下架。 */
    public PostDetail detailForOperator(long postId) {
        FeedPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new NotFoundException("动态不存在: " + postId);
        }
        return assemble(List.of(post), null).get(0);
    }

    /** 运营侧：一条动态的全部评论，含已下架的。 */
    public List<FeedComment> allCommentsForOperator(long postId) {
        return commentMapper.selectList(Wrappers.<FeedComment>lambdaQuery()
                .eq(FeedComment::getPostId, postId)
                .orderByAsc(FeedComment::getId));
    }

    public FeedPost requireVisible(long postId) {
        FeedPost post = postMapper.selectById(postId);
        if (post == null || !FeedPost.VISIBLE.equals(post.getStatus())) {
            // 已下架的动态对粉丝就是「不存在」，不透出「它被下架了」这件事
            throw new NotFoundException("动态不存在或已下架");
        }
        return post;
    }

    public FeedComment requireComment(long commentId) {
        FeedComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new NotFoundException("评论不存在: " + commentId);
        }
        return comment;
    }

    public boolean isOwnedBy(FeedPost post, long talentId) {
        return Objects.equals(post.getTalentId(), talentId);
    }
}
