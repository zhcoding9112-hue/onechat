package com.guild.checkout.feed;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容举报。
 *
 * 同一个人对同一对象只记一条，靠唯一索引挡住——否则一个人连点十次就能把
 * 待处理队列淹掉，而运营看到的「十条举报」其实只有一个来源。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final int MAX_REASON_LENGTH = 255;

    private final ContentReportMapper reportMapper;
    private final FeedService feedService;

    public ReportService(ContentReportMapper reportMapper, FeedService feedService) {
        this.reportMapper = reportMapper;
        this.feedService = feedService;
    }

    /** 举报一条动态或评论。重复举报返回既有那条，不报错——对举报人来说目的已经达到。 */
    @Transactional
    public ContentReport report(long reporterUserId, String targetType, long targetId, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new BusinessException("举报理由为必填");
        }
        if (text.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("举报理由不超过 %d 字符".formatted(MAX_REASON_LENGTH));
        }
        if (!ContentReport.TARGET_POST.equals(targetType)
                && !ContentReport.TARGET_COMMENT.equals(targetType)) {
            throw new BusinessException("举报对象类型不正确: " + targetType);
        }

        // 对象必须存在，否则队列里会堆出一批指向空的举报
        if (ContentReport.TARGET_POST.equals(targetType)) {
            feedService.requireVisible(targetId);
        } else {
            feedService.requireComment(targetId);
        }

        ContentReport report = new ContentReport();
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReporterUserId(reporterUserId);
        report.setReason(text);
        report.setStatus(ContentReport.PENDING);
        try {
            reportMapper.insert(report);
            log.info("收到举报：{} {}，举报人 {}", targetType, targetId, reporterUserId);
            return report;
        } catch (DuplicateKeyException e) {
            return findExisting(targetType, targetId, reporterUserId);
        }
    }

    private ContentReport findExisting(String targetType, long targetId, long reporterUserId) {
        return reportMapper.selectOne(Wrappers.<ContentReport>lambdaQuery()
                .eq(ContentReport::getTargetType, targetType)
                .eq(ContentReport::getTargetId, targetId)
                .eq(ContentReport::getReporterUserId, reporterUserId));
    }

    /** 待处理队列。 */
    public List<ContentReport> pending(int limit) {
        return reportMapper.selectList(Wrappers.<ContentReport>lambdaQuery()
                .eq(ContentReport::getStatus, ContentReport.PENDING)
                .orderByAsc(ContentReport::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
    }

    /** 某个对象上的全部举报，供运营判断严重程度。 */
    public List<ContentReport> onTarget(String targetType, long targetId) {
        return reportMapper.selectList(Wrappers.<ContentReport>lambdaQuery()
                .eq(ContentReport::getTargetType, targetType)
                .eq(ContentReport::getTargetId, targetId)
                .orderByAsc(ContentReport::getId));
    }

    /**
     * 运营处理举报：下架内容，并把该对象上的全部待处理举报一并标记为已处理。
     *
     * 一并标记而不是只标记当前这条：同一个对象被十个人举报，运营下架一次就够了，
     * 让另外九条继续挂在队列里只会让人重复点开同一条内容。
     */
    @Transactional
    public int handleByRemoving(long reportId, long operatorId, String reason) {
        ContentReport report = require(reportId);
        if (ContentReport.TARGET_POST.equals(report.getTargetType())) {
            feedService.removePost(report.getTargetId(), operatorId, reason);
        } else {
            feedService.removeComment(report.getTargetId(), operatorId, reason);
        }
        return closeAllOn(report, operatorId, ContentReport.HANDLED, reason);
    }

    /** 驳回：内容没问题。同样把该对象上的待处理举报一并关掉。 */
    @Transactional
    public int dismiss(long reportId, long operatorId, String note) {
        ContentReport report = require(reportId);
        return closeAllOn(report, operatorId, ContentReport.DISMISSED, note);
    }

    private int closeAllOn(ContentReport report, long operatorId, String status, String note) {
        return reportMapper.update(null, Wrappers.<ContentReport>lambdaUpdate()
                .eq(ContentReport::getTargetType, report.getTargetType())
                .eq(ContentReport::getTargetId, report.getTargetId())
                .eq(ContentReport::getStatus, ContentReport.PENDING)
                .set(ContentReport::getStatus, status)
                .set(ContentReport::getHandledBy, operatorId)
                .set(ContentReport::getHandledAt, LocalDateTime.now())
                .set(ContentReport::getHandleNote, note == null || note.isBlank() ? null : note.trim()));
    }

    public ContentReport require(long reportId) {
        ContentReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new NotFoundException("举报不存在: " + reportId);
        }
        return report;
    }
}
