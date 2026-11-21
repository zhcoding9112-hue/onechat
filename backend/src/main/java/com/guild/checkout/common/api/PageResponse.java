package com.guild.checkout.common.api;

import java.util.List;

/**
 * 分页响应。
 *
 * 形状与 CreditController.TransactionPage 一致（items / total / hasMore），
 * 前端那套翻页逻辑可以照搬。给出 total 而不只是 hasMore，是因为运营后台
 * 要显示「共 N 条」——只有 hasMore 的话那个数就永远算不出来。
 *
 * @param items   当前页的数据
 * @param total   符合条件的总条数，不受分页影响
 * @param hasMore 是否还有下一页
 */
public record PageResponse<T>(List<T> items, long total, boolean hasMore) {

    /** 按页码与页大小算出 hasMore，省得每个调用方自己写一遍这个边界判断。 */
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        long consumed = (long) Math.max(page, 0) * Math.max(size, 1) + items.size();
        return new PageResponse<>(items, total, consumed < total);
    }
}
