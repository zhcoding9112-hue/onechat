package com.guild.checkout.credit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 元子记账。
 *
 * 本阶段「只记账」：余额只由运营手工发放或调整产生，没有粉丝充值、没有提现、
 * 没有转让，也没有任何业务链路会消耗它。所以这里只暴露一个变动入口
 * {@link #apply}，不提供 topUp / withdraw / transfer 之类的方法——缺口不该先
 * 开在代码里再靠接口层去堵。
 *
 * 余额与流水在同一个事务里写：先原子变动余额，再按变动后的真实余额记流水。
 * 顺序反过来（先记流水再改余额）会在余额变动被拒时留下一条假流水。
 */
@Service
public class CreditService {

    private static final int MAX_REASON_LENGTH = 255;

    private final CreditAccountMapper accountMapper;
    private final CreditTransactionMapper transactionMapper;

    public CreditService(CreditAccountMapper accountMapper,
                         CreditTransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
    }

    /** 余额。没开过户的粉丝返回 0，不为只读查询建行。 */
    public long balanceOf(long userId) {
        CreditAccount account = findAccount(userId);
        return account == null ? 0L : account.getBalance();
    }

    /**
     * 变动余额并记流水。
     *
     * @param delta    变动数量，正为增、负为减，不允许为 0
     * @param reason   变动原因，必填
     * @param operator 操作者 app_user.id，系统自动变动时可为 null
     * @throws BusinessException 原因为空、delta 为 0、或扣减会使余额为负
     */
    @Transactional
    public CreditTransaction apply(long userId, long delta, CreditType type,
                                   String reason, Long operator) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写变动原因");
        }
        if (text.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("变动原因不超过 %d 字符".formatted(MAX_REASON_LENGTH));
        }
        if (delta == 0) {
            throw new BusinessException("变动数量不能为 0");
        }

        // 开户与变动分成两条语句，但都不含「读出来判断再写回」，所以并发安全
        accountMapper.openIfAbsent(userId);
        if (accountMapper.applyDelta(userId, delta) == 0) {
            // 条件 UPDATE 没命中，只可能是余额不足：账户刚才已确保存在
            throw new BusinessException("元子余额不足，当前余额 " + balanceOf(userId));
        }

        // 读的是本事务内刚写下的值，所以流水记录的「变动后余额」与账户余额必然一致
        CreditAccount account = findAccount(userId);

        CreditTransaction tx = new CreditTransaction();
        tx.setAccountId(account.getId());
        tx.setUserId(userId);
        tx.setDelta(delta);
        tx.setBalanceAfter(account.getBalance());
        tx.setType(type.name());
        tx.setReason(text);
        tx.setOperatorUserId(operator);
        transactionMapper.insert(tx);
        return tx;
    }

    /** 流水，按时间倒序。同一毫秒内多笔靠自增 id 定序。 */
    public List<CreditTransaction> listTransactions(long userId, int page, int size) {
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * limit;
        return transactionMapper.selectList(Wrappers.<CreditTransaction>lambdaQuery()
                .eq(CreditTransaction::getUserId, userId)
                .orderByDesc(CreditTransaction::getId)
                .last("LIMIT " + limit + " OFFSET " + offset));
    }

    public long countTransactions(long userId) {
        return transactionMapper.selectCount(Wrappers.<CreditTransaction>lambdaQuery()
                .eq(CreditTransaction::getUserId, userId));
    }

    private CreditAccount findAccount(long userId) {
        return accountMapper.selectOne(Wrappers.<CreditAccount>lambdaQuery()
                .eq(CreditAccount::getUserId, userId));
    }
}
