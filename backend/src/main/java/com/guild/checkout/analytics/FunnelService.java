package com.guild.checkout.analytics;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 漏斗与核心指标。
 *
 * 全部用 SQL 现算，不预聚合（design D6）。月事件量在十万以内，
 * 预聚合带来的复杂度远大于收益，而且预聚合口径一旦写错，错误会被固化下来难以察觉。
 */
@Service
public class FunnelService {

    private final AnalyticsEventMapper eventMapper;
    private final AnalyticsSettingMapper settingMapper;
    private final ServiceOrderMapper orderMapper;

    public FunnelService(AnalyticsEventMapper eventMapper,
                         AnalyticsSettingMapper settingMapper,
                         ServiceOrderMapper orderMapper) {
        this.eventMapper = eventMapper;
        this.settingMapper = settingMapper;
        this.orderMapper = orderMapper;
    }

    /** 漏斗里的一级。 */
    public record FunnelStep(
            String eventKey,
            String label,
            long uv,
            /** 相对上一级的转化率，基点；首级为 null。 */
            Integer conversionBp,
            /** 相对上一级的流失人数；首级为 null。 */
            Long lostUv) {
    }

    /** 四项核心指标。 */
    public record CoreMetrics(
            /** 访问到付费的总转化率，基点。分母是打开 H5 的人数。 */
            Integer visitToPayBp,
            /** 主播接单率，基点。分母是进入待接单的订单数。 */
            Integer acceptRateBp,
            /** 平均接单时长（秒）。 */
            Long avgAcceptSeconds,
            /** 7 日复购率，基点。分母是有过完成订单的人数。 */
            Integer repurchaseBp,
            /** 接单率是否低于阈值。 */
            boolean acceptRateBelowThreshold,
            int acceptRateThresholdBp,
            /**
             * 总转化率是否可信。
             * 接单率偏低时转化率会把「供给没跟上」误记成「产品没转化」，
             * 此时这个数据参考价值有限（proposal 把接单率列为否决指标）。
             */
            boolean conversionTrustworthy) {
    }

    public record Overview(
            String from,
            String to,
            List<FunnelStep> funnel,
            CoreMetrics metrics) {
    }

    // ---------- 漏斗 ----------

    public Overview overview(LocalDate from, LocalDate to) {
        LocalDateTime start = requireRange(from, to);
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        Map<String, Long> uvByKey = toUvMap(eventMapper.countDistinctByKey(start, end));
        List<FunnelStep> funnel = buildFunnel(uvByKey);
        CoreMetrics metrics = metrics(start, end, uvByKey);

        return new Overview(from.toString(), to.toString(), funnel, metrics);
    }

    private List<FunnelStep> buildFunnel(Map<String, Long> uvByKey) {
        List<FunnelStep> steps = new ArrayList<>();
        Long previous = null;
        for (EventKey key : EventKey.funnel()) {
            long uv = uvByKey.getOrDefault(key.name(), 0L);
            Integer conversion = null;
            Long lost = null;
            if (previous != null) {
                // 上一级为 0 时转化率没有意义，给 null 而不是 0 或除零
                conversion = previous == 0 ? null : (int) Math.round(uv * 10000.0 / previous);
                lost = Math.max(0, previous - uv);
            }
            steps.add(new FunnelStep(key.name(), key.label(), uv, conversion, lost));
            previous = uv;
        }
        return steps;
    }

    // ---------- 核心指标 ----------

    private CoreMetrics metrics(LocalDateTime start, LocalDateTime end, Map<String, Long> uvByKey) {
        long opened = uvByKey.getOrDefault(EventKey.OPEN_H5.name(), 0L);
        long paid = uvByKey.getOrDefault(EventKey.CONFIRM_PAYMENT.name(), 0L);
        Integer visitToPay = opened == 0 ? null : (int) Math.round(paid * 10000.0 / opened);

        // 接单率与接单时长从订单表算，不从事件表：订单表才是权威，
        // 事件可能因为埋点失败而缺失，而接单率是否决指标，不能有误差。
        List<ServiceOrder> reached = orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .isNotNull(ServiceOrder::getPaidAt)
                .ge(ServiceOrder::getPaidAt, start)
                .lt(ServiceOrder::getPaidAt, end));
        long accepted = reached.stream().filter(o -> o.getAcceptedAt() != null).count();
        Integer acceptRate = reached.isEmpty()
                ? null : (int) Math.round(accepted * 10000.0 / reached.size());

        Long avgAccept = null;
        List<ServiceOrder> acceptedOrders = reached.stream()
                .filter(o -> o.getAcceptedAt() != null).toList();
        if (!acceptedOrders.isEmpty()) {
            long total = acceptedOrders.stream()
                    .mapToLong(o -> Duration.between(o.getPaidAt(), o.getAcceptedAt()).getSeconds())
                    .sum();
            avgAccept = total / acceptedOrders.size();
        }

        long completedUsers = uvByKey.getOrDefault(EventKey.COMPLETE_ORDER.name(), 0L);
        long repurchased = uvByKey.getOrDefault(EventKey.REPURCHASE_7D.name(), 0L);
        Integer repurchase = completedUsers == 0
                ? null : (int) Math.round(repurchased * 10000.0 / completedUsers);

        int threshold = threshold();
        boolean below = acceptRate != null && acceptRate < threshold;

        return new CoreMetrics(visitToPay, acceptRate, avgAccept, repurchase,
                below, threshold, !below);
    }

    // ---------- 拆分 ----------

    public record Breakdown(Long id, String name, Map<String, Long> uvByKey,
                            Integer acceptRateBp) {
    }

    /** 按主播拆分，附带各自的接单率，用于定位问题集中在谁身上。 */
    public List<Breakdown> byTalent(LocalDate from, LocalDate to,
                                    Map<Long, String> talentNames) {
        LocalDateTime start = requireRange(from, to);
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        Map<Long, Map<String, Long>> grouped = groupBy(
                eventMapper.countByTalent(start, end), "talentId");

        Map<Long, int[]> acceptStats = new LinkedHashMap<>();
        for (ServiceOrder o : orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .isNotNull(ServiceOrder::getPaidAt)
                .ge(ServiceOrder::getPaidAt, start)
                .lt(ServiceOrder::getPaidAt, end))) {
            int[] stat = acceptStats.computeIfAbsent(o.getTalentId(), k -> new int[2]);
            stat[0]++;
            if (o.getAcceptedAt() != null) {
                stat[1]++;
            }
        }

        List<Breakdown> rows = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Long>> e : grouped.entrySet()) {
            int[] stat = acceptStats.get(e.getKey());
            Integer rate = stat == null || stat[0] == 0
                    ? null : (int) Math.round(stat[1] * 10000.0 / stat[0]);
            rows.add(new Breakdown(e.getKey(),
                    talentNames.getOrDefault(e.getKey(), "主播 " + e.getKey()),
                    e.getValue(), rate));
        }
        return rows;
    }

    /** 按服务项目拆分。 */
    public List<Breakdown> byServiceItem(LocalDate from, LocalDate to,
                                         Map<Long, String> itemNames) {
        LocalDateTime start = requireRange(from, to);
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        Map<Long, Map<String, Long>> grouped = groupBy(
                eventMapper.countByServiceItem(start, end), "serviceItemId");

        List<Breakdown> rows = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Long>> e : grouped.entrySet()) {
            rows.add(new Breakdown(e.getKey(),
                    itemNames.getOrDefault(e.getKey(), "项目 " + e.getKey()),
                    e.getValue(), null));
        }
        return rows;
    }

    // ---------- 配置 ----------

    public int threshold() {
        AnalyticsSetting setting = settingMapper.selectOne(
                Wrappers.<AnalyticsSetting>lambdaQuery().eq(AnalyticsSetting::getSingletonKey, 1));
        return setting == null || setting.getAcceptRateThresholdBp() == null
                ? 8000 : setting.getAcceptRateThresholdBp();
    }

    public AnalyticsSetting updateThreshold(int bp) {
        if (bp < 0 || bp > 10000) {
            throw new BusinessException("阈值须在 0 到 10000 基点之间");
        }
        AnalyticsSetting setting = settingMapper.selectOne(
                Wrappers.<AnalyticsSetting>lambdaQuery().eq(AnalyticsSetting::getSingletonKey, 1));
        if (setting == null) {
            throw new BusinessException("看板配置缺失，请联系技术排查");
        }
        setting.setAcceptRateThresholdBp(bp);
        settingMapper.updateById(setting);
        return setting;
    }

    // ---------- 辅助 ----------

    private static LocalDateTime requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("请选择统计时间范围");
        }
        if (to.isBefore(from)) {
            throw new BusinessException("结束日期不得早于开始日期");
        }
        return from.atStartOfDay();
    }

    private static Map<String, Long> toUvMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("eventKey")), toLong(row.get("uv")));
        }
        return map;
    }

    private static Map<Long, Map<String, Long>> groupBy(List<Map<String, Object>> rows, String idKey) {
        Map<Long, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get(idKey));
            grouped.computeIfAbsent(id, k -> new LinkedHashMap<>())
                    .put(String.valueOf(row.get("eventKey")), toLong(row.get("uv")));
        }
        return grouped;
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
