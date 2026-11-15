package com.guild.checkout.analytics;

import java.util.List;

/**
 * 漏斗节点。
 *
 * 顺序即漏斗顺序，前端与查询都按这个数组推进，不要在别处重复定义一遍。
 * 新增节点时只需在这里加一项并在触发处上报——看板会自动把它算进去。
 */
public enum EventKey {

    /** 关注服务号。来自微信推送的关注事件。 */
    FOLLOW_ACCOUNT("关注服务号"),
    /** 在微信内打开 H5。 */
    OPEN_H5("打开 H5"),
    /** 浏览主播列表。 */
    VIEW_TALENT_LIST("浏览主播列表"),
    /** 进入某位主播的详情页。 */
    VIEW_TALENT_DETAIL("进入主播详情"),
    /** 点击下单按钮，进入下单页。 */
    CLICK_ORDER("点击下单"),
    /** 提交订单成功。 */
    SUBMIT_ORDER("提交订单"),
    /** 看到收款信息。 */
    VIEW_PAYMENT_INFO("展示收款信息"),
    /** 上传支付凭证。 */
    UPLOAD_PROOF("上传支付凭证"),
    /** 运营确认收款。 */
    CONFIRM_PAYMENT("确认收款"),
    /** 主播接单。 */
    ACCEPT_ORDER("主播接单"),
    /** 服务完成。 */
    COMPLETE_ORDER("服务完成"),
    /** 7 日内复购。 */
    REPURCHASE_7D("7 日内复购");

    private final String label;

    EventKey(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 漏斗顺序。 */
    public static List<EventKey> funnel() {
        return List.of(values());
    }

    public static EventKey of(String key) {
        for (EventKey k : values()) {
            if (k.name().equals(key)) {
                return k;
            }
        }
        return null;
    }
}
