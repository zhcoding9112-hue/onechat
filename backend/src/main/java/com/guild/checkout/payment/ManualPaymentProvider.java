package com.guild.checkout.payment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人工收款：展示公会对公收款信息，粉丝线下转账后回来上传凭证，运营核对到账后确认。
 *
 * 收款信息全部来自 payment_setting，不在代码里出现任何真实账号。
 */
@Component
public class ManualPaymentProvider implements PaymentProvider {

    public static final String CODE = "manual";

    private final PaymentSettingMapper settingMapper;

    public ManualPaymentProvider(PaymentSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "对公转账";
    }

    @Override
    public PaymentInstruction instruct(String orderRef, long payableCents) {
        PaymentSetting setting = settingMapper.selectOne(
                Wrappers.<PaymentSetting>lambdaQuery().eq(PaymentSetting::getProvider, CODE));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderRef", orderRef);
        payload.put("payableCents", payableCents);
        payload.put("accountName", setting == null ? null : setting.getAccountName());
        payload.put("accountNo", setting == null ? null : setting.getAccountNo());
        payload.put("bankName", setting == null ? null : setting.getBankName());
        payload.put("qrUrl", setting == null ? null : setting.getQrUrl());
        payload.put("tips", setting == null ? null : setting.getTips());
        // 收款信息尚未配置时明确告知，而不是展示一片空白让粉丝不知所措
        payload.put("ready", setting != null
                && ((setting.getAccountNo() != null && !setting.getAccountNo().isBlank())
                || (setting.getQrUrl() != null && !setting.getQrUrl().isBlank())));

        return new PaymentInstruction(CODE, PaymentInstruction.Kind.DISPLAY_AND_UPLOAD, payload);
    }
}
