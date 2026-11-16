package com.guild.checkout.credit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 元子记账（tasks 6.2 - 6.4）。
 *
 * 并发超扣单独放在 {@link CreditConcurrencyTest}：它必须在真实提交下跑，
 * 不能带测试事务。
 */
@SpringBootTest
@Transactional
class CreditLedgerTest {

    private static final long FAN_ID = 999_000_010L;
    private static final long OPERATOR_ID = 999_000_011L;

    @Autowired private CreditService creditService;
    @Autowired private CreditTransactionMapper transactionMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("6.2 连续 10 次变动后末条流水的变动后余额与账户余额一致")
    void 流水变动后余额与账户一致() {
        // 先垫一笔，保证后面的负向变动不会被余额不足挡住
        creditService.apply(FAN_ID, 100, CreditType.GRANT, "期初", OPERATOR_ID);

        long[] deltas = {7, -3, 20, -1, -5, 11, -2, 30, -9, 4};
        for (long d : deltas) {
            creditService.apply(FAN_ID, d, CreditType.ADJUST, "第 " + d + " 次", OPERATOR_ID);
        }

        List<CreditTransaction> rows = creditService.listTransactions(FAN_ID, 0, 20);
        assertThat(rows).hasSize(11);

        long balance = creditService.balanceOf(FAN_ID);
        assertThat(rows.get(0).getBalanceAfter()).isEqualTo(balance);
        // 期初 100 加上 10 次变动
        assertThat(balance).isEqualTo(100 + 7 - 3 + 20 - 1 - 5 + 11 - 2 + 30 - 9 + 4);
    }

    @Test
    @DisplayName("6.2 每一条流水的变动后余额都等于其前所有变动的累计")
    void 每条流水都自洽() {
        creditService.apply(FAN_ID, 50, CreditType.GRANT, "期初", OPERATOR_ID);
        creditService.apply(FAN_ID, -20, CreditType.ADJUST, "扣一点", OPERATOR_ID);
        creditService.apply(FAN_ID, 5, CreditType.ADJUST, "补一点", OPERATOR_ID);

        // 正序逐条推演，任何一条对不上都说明记账链断了
        List<CreditTransaction> rows = transactionMapper.selectList(
                Wrappers.<CreditTransaction>lambdaQuery()
                        .eq(CreditTransaction::getUserId, FAN_ID)
                        .orderByAsc(CreditTransaction::getId));
        long running = 0;
        for (CreditTransaction tx : rows) {
            running += tx.getDelta();
            assertThat(tx.getBalanceAfter()).as("流水 %d", tx.getId()).isEqualTo(running);
        }
        assertThat(running).isEqualTo(creditService.balanceOf(FAN_ID));
    }

    @Test
    @DisplayName("6.2 变动被拒时不留下流水")
    void 被拒绝的变动不留流水() {
        creditService.apply(FAN_ID, 10, CreditType.GRANT, "期初", OPERATOR_ID);
        long before = creditService.countTransactions(FAN_ID);

        assertThatThrownBy(() -> creditService.apply(FAN_ID, -11, CreditType.ADJUST, "扣超", OPERATOR_ID))
                .isInstanceOf(BusinessException.class);

        assertThat(creditService.countTransactions(FAN_ID)).isEqualTo(before);
    }

    @Test
    @DisplayName("6.3 扣减额超过余额时被拒绝且余额不变")
    void 余额不可为负() {
        creditService.apply(FAN_ID, 30, CreditType.GRANT, "期初", OPERATOR_ID);

        assertThatThrownBy(() -> creditService.apply(FAN_ID, -31, CreditType.DEDUCT, "扣 31", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额不足");

        assertThat(creditService.balanceOf(FAN_ID)).isEqualTo(30);
    }

    @Test
    @DisplayName("6.3 刚好扣到 0 是允许的")
    void 扣到零允许() {
        creditService.apply(FAN_ID, 30, CreditType.GRANT, "期初", OPERATOR_ID);
        creditService.apply(FAN_ID, -30, CreditType.DEDUCT, "清零", OPERATOR_ID);

        assertThat(creditService.balanceOf(FAN_ID)).isZero();
    }

    @Test
    @DisplayName("6.3 零余额账户的任何扣减都被拒绝")
    void 零余额不能扣() {
        assertThatThrownBy(() -> creditService.apply(FAN_ID, -1, CreditType.DEDUCT, "扣 1", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额不足");

        assertThat(creditService.balanceOf(FAN_ID)).isZero();
    }

    @Test
    @DisplayName("6.3 数据库约束兜底：绕过服务层直接写负余额被拒绝")
    void 负余额在数据库层也写不进去() {
        creditService.apply(FAN_ID, 5, CreditType.GRANT, "期初", OPERATOR_ID);

        // 余额不可为负的最终保证在表约束上，而不只是服务层的判断
        // MySQL 的 CHECK 违反是 error 3819，Spring 没有把它归到
        // DataIntegrityViolationException，所以这里按 DataAccessException 断言并核对约束名
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE credit_account SET balance = -1 WHERE user_id = ?", FAN_ID))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_credit_account_balance");

        assertThat(creditService.balanceOf(FAN_ID)).isEqualTo(5);
    }

    @Test
    @DisplayName("6.4 未填原因时被拒绝并提示原因为必填")
    void 未填原因被拒绝() {
        assertThatThrownBy(() -> creditService.apply(FAN_ID, 50, CreditType.GRANT, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");

        assertThatThrownBy(() -> creditService.apply(FAN_ID, 50, CreditType.GRANT, "   ", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");

        assertThat(creditService.balanceOf(FAN_ID)).isZero();
        assertThat(creditService.countTransactions(FAN_ID)).isZero();
    }

    @Test
    @DisplayName("6.4 运营发放 50 后余额增加 50，流水记录操作者与原因")
    void 运营发放记录操作者与原因() {
        creditService.apply(FAN_ID, 50, CreditType.GRANT, "活动补偿", OPERATOR_ID);

        assertThat(creditService.balanceOf(FAN_ID)).isEqualTo(50);
        CreditTransaction tx = creditService.listTransactions(FAN_ID, 0, 1).get(0);
        assertThat(tx.getDelta()).isEqualTo(50);
        assertThat(tx.getType()).isEqualTo(CreditType.GRANT.name());
        assertThat(tx.getReason()).isEqualTo("活动补偿");
        assertThat(tx.getOperatorUserId()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("6.4 变动数量为 0 被拒绝")
    void 零变动被拒绝() {
        assertThatThrownBy(() -> creditService.apply(FAN_ID, 0, CreditType.ADJUST, "不动", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为 0");
    }

    @Test
    @DisplayName("6.5 流水按时间倒序返回")
    void 流水倒序() {
        creditService.apply(FAN_ID, 10, CreditType.GRANT, "第一笔", OPERATOR_ID);
        creditService.apply(FAN_ID, 20, CreditType.GRANT, "第二笔", OPERATOR_ID);
        creditService.apply(FAN_ID, 30, CreditType.GRANT, "第三笔", OPERATOR_ID);

        List<String> reasons = creditService.listTransactions(FAN_ID, 0, 10).stream()
                .map(CreditTransaction::getReason).toList();
        assertThat(reasons).containsExactly("第三笔", "第二笔", "第一笔");
    }

    @Test
    @DisplayName("6.5 没开过户的粉丝余额为 0 且流水为空")
    void 未开户返回零() {
        long freshFan = 999_000_019L;
        assertThat(creditService.balanceOf(freshFan)).isZero();
        assertThat(creditService.listTransactions(freshFan, 0, 10)).isEmpty();
    }
}
