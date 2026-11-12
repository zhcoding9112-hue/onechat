package com.guild.checkout.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 时限取值。
 *
 * 读库而不是读配置文件：运营要在后台随时调，首周看真实数据后多半要改，
 * 改一次就重启一次服务是不可接受的。
 *
 * 取到的值只用于「新建订单时折算绝对时间戳」，已在计时中的订单沿用其创建时的时限。
 */
@Service
public class OrderDeadlineProvider {

    private static final Logger log = LoggerFactory.getLogger(OrderDeadlineProvider.class);

    /** 库中配置缺失时的兜底，与迁移里的初值一致。 */
    private final OrderDeadlineProperties fallback;
    private final OrderDeadlineSettingMapper mapper;

    public OrderDeadlineProvider(OrderDeadlineProperties fallback,
                                 OrderDeadlineSettingMapper mapper) {
        this.fallback = fallback;
        this.mapper = mapper;
    }

    public int payMinutes() {
        OrderDeadlineSetting setting = find();
        return setting == null || setting.getPayMinutes() == null
                ? fallback.payMinutes() : setting.getPayMinutes();
    }

    public int acceptMinutes() {
        OrderDeadlineSetting setting = find();
        return setting == null || setting.getAcceptMinutes() == null
                ? fallback.acceptMinutes() : setting.getAcceptMinutes();
    }

    @Transactional
    public OrderDeadlineSetting update(Integer payMinutes, Integer acceptMinutes) {
        OrderDeadlineSetting setting = find();
        if (setting == null) {
            throw new BusinessException("时限配置缺失，请联系技术排查");
        }
        if (payMinutes != null) {
            requirePositive(payMinutes, "待支付有效期");
            setting.setPayMinutes(payMinutes);
        }
        if (acceptMinutes != null) {
            requirePositive(acceptMinutes, "接单时限");
            setting.setAcceptMinutes(acceptMinutes);
        }
        mapper.updateById(setting);
        log.info("订单时限已更新：待支付 {} 分钟，接单 {} 分钟",
                setting.getPayMinutes(), setting.getAcceptMinutes());
        return setting;
    }

    public OrderDeadlineSetting current() {
        OrderDeadlineSetting setting = find();
        if (setting != null) {
            return setting;
        }
        OrderDeadlineSetting defaults = new OrderDeadlineSetting();
        defaults.setPayMinutes(fallback.payMinutes());
        defaults.setAcceptMinutes(fallback.acceptMinutes());
        return defaults;
    }

    private OrderDeadlineSetting find() {
        return mapper.selectOne(Wrappers.<OrderDeadlineSetting>lambdaQuery()
                .eq(OrderDeadlineSetting::getSingletonKey, 1));
    }

    private static void requirePositive(int minutes, String label) {
        if (minutes <= 0) {
            throw new BusinessException(label + "必须大于零");
        }
        if (minutes > 60 * 24 * 7) {
            throw new BusinessException(label + "过长，请确认是否填错");
        }
    }
}
