package com.guild.checkout.analytics;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.PublicApi;
import com.guild.checkout.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前端埋点上报。
 *
 * 前端只能上报「浏览」类节点（打开 H5、看列表、看详情、点下单）。
 * 涉及钱与状态的节点一律由后端在状态流转处自己记——那些事件是对账依据，
 * 不能让客户端有伪造的余地。
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsTrackController {

    /** 允许前端上报的事件白名单。不在其中的一律拒绝。 */
    private static final List<EventKey> CLIENT_ALLOWED = List.of(
            EventKey.OPEN_H5,
            EventKey.VIEW_TALENT_LIST,
            EventKey.VIEW_TALENT_DETAIL,
            EventKey.CLICK_ORDER);

    private final AnalyticsService analytics;

    public AnalyticsTrackController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    public record TrackRequest(
            @NotBlank(message = "事件标识不能为空")
            @Size(max = 48) String eventKey,
            /** 发现入口。认不出来或不传的一律按直接访问记。 */
            @Size(max = 32) String entrySource,
            Long talentId,
            Long serviceItemId,
            Map<String, Object> props) {
    }

    /**
     * 上报一条前端事件。
     *
     * 无论成功与否都返回成功：埋点失败不该让前端看到报错，更不该触发重试风暴。
     */
    @PostMapping("/track")
    public ApiResponse<Void> track(@Valid @RequestBody TrackRequest req) {
        // 入口点击事件单独一条路径：它的 key 不在漏斗枚举里（见 AnalyticsService#trackEntryClick）
        EntrySource entryClick = EntrySource.byEventKey(req.eventKey());
        if (entryClick != null) {
            analytics.trackEntryClick(entryClick, CurrentUserHolder.requireUserId(), req.talentId());
            return ApiResponse.ok();
        }

        EventKey key = EventKey.of(req.eventKey());
        if (key == null || !CLIENT_ALLOWED.contains(key)) {
            // 不是报错，是静默忽略：前端版本比后端新时会出现未知事件，
            // 那种情况不该让用户看到任何异常
            return ApiResponse.ok();
        }
        analytics.track(AnalyticsService.Track.of(key)
                .from(EntrySource.of(req.entrySource()))
                .user(CurrentUserHolder.requireUserId())
                .talent(req.talentId())
                .serviceItem(req.serviceItemId())
                .props(req.props()));
        return ApiResponse.ok();
    }

    public record FollowRequest(@NotBlank @Size(max = 64) String openId) {
    }

    /**
     * 关注服务号事件。
     *
     * 由微信的事件推送触发，此时用户多半还没打开过 H5、还没建号，
     * 因此只记 openId，由查询侧用 COALESCE 把它和后续的 user_id 归成同一个人。
     *
     * 标为 PublicApi：微信服务器的回调不带会话。真实接入时这里需要校验微信签名，
     * 当前先留路由与口径。
     */
    @PostMapping("/follow")
    @PublicApi
    public ApiResponse<Void> follow(@Valid @RequestBody FollowRequest req) {
        analytics.track(AnalyticsService.Track.of(EventKey.FOLLOW_ACCOUNT).openId(req.openId()));
        return ApiResponse.ok();
    }
}
