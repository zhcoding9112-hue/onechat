package com.guild.checkout.order;

import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单视图装配。按查看者身份决定可见字段与可用操作。
 */
@Component
public class OrderViewAssembler {

    private final TalentService talentService;

    public OrderViewAssembler(TalentService talentService) {
        this.talentService = talentService;
    }

    /** 查看者身份。 */
    public enum Viewer {
        /** 下单粉丝本人。 */
        FAN,
        /** 该订单指定的主播本人。 */
        TALENT,
        /** 运营。 */
        OPERATOR
    }

    public OrderView assemble(ServiceOrder order, Viewer viewer) {
        Talent talent = talentService.find(order.getTalentId());
        OrderStatus status = order.statusEnum();
        boolean canSeeContact = viewer != Viewer.FAN;

        return new OrderView(
                order.getId(),
                order.getOrderRef(),
                order.getTalentId(),
                talent == null ? null : talent.getNickname(),
                order.getServiceItemName(),
                order.getServiceSpecName(),
                order.getUnitPriceCents(),
                order.getQuantity(),
                order.getPayableCents(),
                viewer == Viewer.OPERATOR ? order.getPaidCents() : null,
                status.name(),
                status.label(),
                canSeeContact ? order.getContact() : null,
                order.getRemark(),
                order.getPayDeadline(),
                order.getAcceptDeadline(),
                order.getPaidAt(),
                order.getAcceptedAt(),
                order.getCompletedAt(),
                order.getClosedAt(),
                order.getCloseReason(),
                order.getCreatedAt(),
                actionsFor(order, status, viewer));
    }

    /**
     * 当前查看者在该状态下可以做什么。
     * 服务端算好给前端，避免前端各处重复判断状态而且判断得不一致。
     */
    private static List<String> actionsFor(ServiceOrder order, OrderStatus status, Viewer viewer) {
        List<String> actions = new ArrayList<>();
        switch (viewer) {
            case FAN -> {
                if (status == OrderStatus.PENDING_PAYMENT) {
                    actions.add("UPLOAD_PROOF");
                }
                if (status.isTerminal()) {
                    actions.add("REORDER");
                }
                if (status == OrderStatus.COMPLETED) {
                    actions.add("RATE");
                }
            }
            case TALENT -> {
                if (status == OrderStatus.PENDING_ACCEPT && notExpired(order.getAcceptDeadline())) {
                    actions.add("ACCEPT");
                }
                if (status == OrderStatus.ACCEPTED) {
                    actions.add("COMPLETE");
                }
            }
            case OPERATOR -> {
                if (status == OrderStatus.PENDING_CONFIRM || status == OrderStatus.PENDING_PAYMENT) {
                    actions.add("CONFIRM_PAYMENT");
                }
                if (status == OrderStatus.PENDING_CONFIRM) {
                    actions.add("REJECT_PROOF");
                }
                if (!status.isTerminal()) {
                    actions.add("CANCEL");
                }
                if (status.isTerminal() && status != OrderStatus.REFUNDED && status.isPaid()) {
                    actions.add("MARK_REFUNDED");
                }
            }
        }
        return actions;
    }

    private static boolean notExpired(LocalDateTime deadline) {
        return deadline == null || LocalDateTime.now().isBefore(deadline);
    }
}
