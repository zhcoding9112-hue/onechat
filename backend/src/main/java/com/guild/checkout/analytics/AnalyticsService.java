package com.guild.checkout.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 埋点写入。
 *
 * 两条铁律：
 *   1. **异常绝不冒泡**。埋点失败只记日志，用户的下单请求照常成功。
 *      为了这个目的还要自开事务（REQUIRES_NEW）——否则埋点在主事务里抛了异常，
 *      就算这里 catch 住，主事务也已经被标记为 rollback-only，整笔订单照样回滚。
 *      这是 Spring 事务传播最容易踩的坑之一，也是 analytics-funnel 规格里
 *      「采集失败不影响业务」真正的实现要求。
 *   2. 不在埋点里做任何耗时操作。它挂在下单这类关键路径上。
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    public AnalyticsService(AnalyticsEventMapper eventMapper, ObjectMapper objectMapper) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
    }

    /** 一次埋点上报的全部内容。除了 key 其余都可为空。 */
    public record Track(
            EventKey key,
            Long userId,
            String openId,
            Long talentId,
            Long serviceItemId,
            String orderRef,
            Map<String, Object> props,
            LocalDateTime occurredAt) {

        public static Track of(EventKey key) {
            return new Track(key, null, null, null, null, null, null, null);
        }

        public Track user(Long userId) {
            return new Track(key, userId, openId, talentId, serviceItemId, orderRef, props, occurredAt);
        }

        public Track openId(String value) {
            return new Track(key, userId, value, talentId, serviceItemId, orderRef, props, occurredAt);
        }

        public Track talent(Long value) {
            return new Track(key, userId, openId, value, serviceItemId, orderRef, props, occurredAt);
        }

        public Track serviceItem(Long value) {
            return new Track(key, userId, openId, talentId, value, orderRef, props, occurredAt);
        }

        public Track order(String value) {
            return new Track(key, userId, openId, talentId, serviceItemId, value, props, occurredAt);
        }

        public Track props(Map<String, Object> value) {
            return new Track(key, userId, openId, talentId, serviceItemId, orderRef, value, occurredAt);
        }

        public Track at(LocalDateTime value) {
            return new Track(key, userId, openId, talentId, serviceItemId, orderRef, props, value);
        }
    }

    /**
     * 记录一条事件。任何失败都被吞掉，调用方无需 try-catch。
     */
    public void track(Track track) {
        try {
            writeInNewTransaction(track);
        } catch (RuntimeException ex) {
            // 这里是最后一道防线：业务绝不因为埋点失败而失败
            log.error("埋点写入失败，已忽略：key={} user={} order={}",
                    track.key(), track.userId(), track.orderRef(), ex);
        }
    }

    /**
     * 独立事务写入。
     *
     * REQUIRES_NEW 不是性能优化，是正确性要求：若与主事务共用一个事务，
     * 这里抛出的异常会把主事务标记为 rollback-only，外层 catch 住也救不回来，
     * 下单最终仍会失败。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(Track track) {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventKey(track.key().name());
        event.setUserId(track.userId());
        event.setOpenId(track.openId());
        event.setTalentId(track.talentId());
        event.setServiceItemId(track.serviceItemId());
        event.setOrderRef(track.orderRef());
        event.setOccurredAt(track.occurredAt() == null ? LocalDateTime.now() : track.occurredAt());
        if (track.props() != null && !track.props().isEmpty()) {
            try {
                event.setProps(objectMapper.writeValueAsString(track.props()));
            } catch (Exception ex) {
                // 属性序列化失败不该让整条事件丢掉
                log.warn("埋点属性序列化失败，已置空：key={}", track.key());
            }
        }
        eventMapper.insert(event);
    }

    /** 便捷方法：只有 key 和用户。 */
    public void track(EventKey key, Long userId) {
        track(Track.of(key).user(userId));
    }
}
