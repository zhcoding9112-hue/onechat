package com.guild.checkout.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单状态机。非法跃迁必须一律被拒绝——状态错一次，钱与履约就对不上。
 */
class OrderStatusTest {

    // ---------- 正常路径 ----------

    @Test
    void 正常路径逐级可推进() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PENDING_CONFIRM)).isTrue();
        assertThat(OrderStatus.PENDING_CONFIRM.canTransitionTo(OrderStatus.PENDING_ACCEPT)).isTrue();
        assertThat(OrderStatus.PENDING_ACCEPT.canTransitionTo(OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void 未上传凭证也可直接确认收款() {
        // 运营核对到账后可直接确认，不强制粉丝必须先传凭证
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PENDING_ACCEPT)).isTrue();
    }

    @Test
    void 驳回凭证可退回待支付() {
        assertThat(OrderStatus.PENDING_CONFIRM.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isTrue();
    }

    @Test
    void 等待态可被取消() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING_CONFIRM.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING_ACCEPT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void 待接单超时进入已超时() {
        assertThat(OrderStatus.PENDING_ACCEPT.canTransitionTo(OrderStatus.TIMEOUT)).isTrue();
    }

    @Test
    void 已收款的终止态可退款() {
        assertThat(OrderStatus.TIMEOUT.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
    }

    // ---------- 非法跃迁 ----------

    @ParameterizedTest
    @CsvSource({
            // 跳过付款直接接单或完成
            "PENDING_PAYMENT, ACCEPTED",
            "PENDING_PAYMENT, COMPLETED",
            "PENDING_PAYMENT, TIMEOUT",
            "PENDING_PAYMENT, REFUNDED",
            "PENDING_CONFIRM, ACCEPTED",
            "PENDING_CONFIRM, COMPLETED",
            // 跳过接单直接完成
            "PENDING_ACCEPT, COMPLETED",
            // 回退
            "ACCEPTED, PENDING_ACCEPT",
            "ACCEPTED, PENDING_PAYMENT",
            "COMPLETED, ACCEPTED",
            "COMPLETED, PENDING_ACCEPT",
            // 终止态复活
            "TIMEOUT, ACCEPTED",
            "TIMEOUT, PENDING_ACCEPT",
            "TIMEOUT, COMPLETED",
            "CANCELLED, PENDING_PAYMENT",
            "CANCELLED, ACCEPTED",
            "CANCELLED, COMPLETED",
            "REFUNDED, PENDING_PAYMENT",
            "REFUNDED, ACCEPTED",
            "REFUNDED, COMPLETED",
            "REFUNDED, CANCELLED",
    })
    void 非法跃迁一律被拒绝(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to))
                .as("%s → %s 应被拒绝", from, to)
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void 任何状态都不能跃迁到自身(OrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void 跃迁到空状态被拒绝(OrderStatus status) {
        assertThat(status.canTransitionTo(null)).isFalse();
    }

    @Test
    void 穷举全部状态对只有白名单内的组合被允许() {
        List<String> allowed = new ArrayList<>();
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                if (from.canTransitionTo(to)) {
                    allowed.add(from + "->" + to);
                }
            }
        }
        assertThat(allowed).containsExactlyInAnyOrder(
                "PENDING_PAYMENT->PENDING_CONFIRM",
                "PENDING_PAYMENT->PENDING_ACCEPT",
                "PENDING_PAYMENT->CANCELLED",
                "PENDING_CONFIRM->PENDING_PAYMENT",
                "PENDING_CONFIRM->PENDING_ACCEPT",
                "PENDING_CONFIRM->CANCELLED",
                "PENDING_ACCEPT->ACCEPTED",
                "PENDING_ACCEPT->TIMEOUT",
                "PENDING_ACCEPT->CANCELLED",
                "ACCEPTED->COMPLETED",
                "ACCEPTED->CANCELLED",
                "COMPLETED->REFUNDED",
                "TIMEOUT->REFUNDED",
                "CANCELLED->REFUNDED");
    }

    // ---------- 状态分类 ----------

    @Test
    void 终止态判定() {
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(OrderStatus.TIMEOUT.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();

        assertThat(OrderStatus.PENDING_PAYMENT.isTerminal()).isFalse();
        assertThat(OrderStatus.PENDING_CONFIRM.isTerminal()).isFalse();
        assertThat(OrderStatus.PENDING_ACCEPT.isTerminal()).isFalse();
        assertThat(OrderStatus.ACCEPTED.isTerminal()).isFalse();
    }

    @Test
    void 等待态判定决定哪些订单需要超时调度() {
        assertThat(OrderStatus.PENDING_PAYMENT.isWaiting()).isTrue();
        assertThat(OrderStatus.PENDING_CONFIRM.isWaiting()).isTrue();
        assertThat(OrderStatus.PENDING_ACCEPT.isWaiting()).isTrue();

        assertThat(OrderStatus.ACCEPTED.isWaiting()).isFalse();
        assertThat(OrderStatus.COMPLETED.isWaiting()).isFalse();
    }

    @Test
    void 已收款判定决定终止时是否需要退款() {
        assertThat(OrderStatus.PENDING_ACCEPT.isPaid()).isTrue();
        assertThat(OrderStatus.ACCEPTED.isPaid()).isTrue();
        assertThat(OrderStatus.COMPLETED.isPaid()).isTrue();
        assertThat(OrderStatus.TIMEOUT.isPaid()).isTrue();

        // 这两个状态钱还没到账，关闭无需退款
        assertThat(OrderStatus.PENDING_PAYMENT.isPaid()).isFalse();
        assertThat(OrderStatus.PENDING_CONFIRM.isPaid()).isFalse();
    }

    @Test
    void 每个状态都有中文文案供前端直接展示() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.label()).isNotBlank();
        }
        assertThat(OrderStatus.PENDING_ACCEPT.label()).isEqualTo("待接单");
    }

    @Test
    void 已退款是绝对终点() {
        assertThat(OrderStatus.REFUNDED.allowedTargets()).isEmpty();
    }
}
