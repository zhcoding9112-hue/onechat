package com.guild.checkout.survey;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 满意度回访。
 *
 * 这个服务存在的全部意义是「拿到真实反馈」，因此可见范围的设计比功能本身更重要：
 * 主播只能看聚合值。如果主播能看到单条评分出自哪位粉丝，粉丝就会怕被针对，
 * 低分不敢打，数据全变 5 分，回访就等于没做。
 */
@Service
public class SurveyService {

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);

    private final SatisfactionSurveyMapper surveyMapper;
    private final OrderService orderService;

    public SurveyService(SatisfactionSurveyMapper surveyMapper, OrderService orderService) {
        this.surveyMapper = surveyMapper;
        this.orderService = orderService;
    }

    /** 给主播看的聚合结果。刻意不含任何可定位到订单或粉丝的字段。 */
    public record Aggregate(
            long count,
            /** 均值，保留两位小数；无评分时为 null 而非 0——0 分和"还没人评"是两回事。 */
            Double average,
            /** 1 到 5 各分值的人数。 */
            Map<Integer, Long> distribution) {
    }

    /** 给运营看的逐单明细。 */
    public record DetailEntry(
            Long orderId,
            String orderRef,
            Long fanUserId,
            int score,
            java.time.LocalDateTime createdAt) {
    }

    // ---------- 提交 ----------

    /**
     * 粉丝提交评分。
     *
     * @throws ForbiddenException 非该单下单人
     * @throws BusinessException  订单未完成，或已评过分
     */
    @Transactional
    public SatisfactionSurvey submit(long orderId, long fanUserId, int score) {
        if (score < 1 || score > 5) {
            throw new BusinessException("评分需为 1 到 5 分");
        }
        ServiceOrder order = orderService.requireOrder(orderId);
        if (!Objects.equals(order.getFanUserId(), fanUserId)) {
            throw new ForbiddenException("只有下单本人可以评分");
        }
        if (order.statusEnum() != OrderStatus.COMPLETED) {
            throw new BusinessException("订单尚未完成，暂不能评分");
        }

        SatisfactionSurvey survey = new SatisfactionSurvey();
        survey.setOrderId(orderId);
        survey.setOrderRef(order.getOrderRef());
        survey.setTalentId(order.getTalentId());
        survey.setFanUserId(fanUserId);
        survey.setScore(score);
        try {
            surveyMapper.insert(survey);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("该订单已经评过分了");
        }
        log.info("订单 {} 收到满意度评分 {} 分", order.getOrderRef(), score);
        return survey;
    }

    public SatisfactionSurvey findByOrder(long orderId) {
        return surveyMapper.selectOne(
                Wrappers.<SatisfactionSurvey>lambdaQuery().eq(SatisfactionSurvey::getOrderId, orderId));
    }

    // ---------- 聚合（主播可见）----------

    /**
     * 某主播的聚合评分。
     *
     * 返回值里没有 orderId、没有 fanUserId、没有逐条记录——这是刻意的，
     * 不是遗漏。未评分的订单不参与均值计算（它们根本不在这张表里）。
     */
    public Aggregate aggregateOf(long talentId) {
        List<SatisfactionSurvey> surveys = surveyMapper.selectList(
                Wrappers.<SatisfactionSurvey>lambdaQuery()
                        .eq(SatisfactionSurvey::getTalentId, talentId));

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        if (surveys.isEmpty()) {
            return new Aggregate(0, null, distribution);
        }

        long total = 0;
        for (SatisfactionSurvey s : surveys) {
            total += s.getScore();
            distribution.merge(s.getScore(), 1L, Long::sum);
        }
        // 两位小数，用整数运算避免浮点误差在均值上放大
        double average = Math.round(total * 100.0 / surveys.size()) / 100.0;
        return new Aggregate(surveys.size(), average, distribution);
    }

    // ---------- 明细（仅运营可见）----------

    /** 逐单明细。只有运营该调这个方法。 */
    public List<DetailEntry> detailsOf(long talentId) {
        return surveyMapper.selectList(Wrappers.<SatisfactionSurvey>lambdaQuery()
                        .eq(SatisfactionSurvey::getTalentId, talentId)
                        .orderByDesc(SatisfactionSurvey::getId))
                .stream()
                .map(s -> new DetailEntry(s.getOrderId(), s.getOrderRef(), s.getFanUserId(),
                        s.getScore(), s.getCreatedAt()))
                .toList();
    }
}
