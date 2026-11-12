package com.guild.checkout.order;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 订单号生成器。
 *
 * 格式：yyMMddHHmmss + 6 位数字，共 18 位（design D8）。
 * 时间前缀便于运营在对公账户流水与订单之间人工比对——这是上线期每天都要做的动作。
 *
 * 后缀不是纯随机。纯随机的 6 位空间只有 100 万，同一秒内生成一万个的碰撞期望约 50 个
 * （生日问题，n²/2×10⁶），无法满足"并发一万个无重复"。这里改为：
 *   每当时间前缀变化，重新取一个随机起点；后缀 = (起点 + 序号 × 步长) mod 10⁶。
 * 步长 499979 是质数、与 10⁶ 互质，因此序号在走满 100 万次之前不会产生重复值，
 * 同一秒内的唯一性由此保证；而乘以大步长又让相邻订单号在数值上相隔很远，
 * 不会暴露"这一秒成交了几单"。
 *
 * 跨进程的唯一性仍由 order_ref 的唯一索引兜底，创建订单时遇冲突重试即可。
 */
@Component
public class OrderRefGenerator {

    private static final DateTimeFormatter PREFIX = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final int SUFFIX_SPACE = 1_000_000;
    /** 与 10^6 互质的质数步长，保证遍历不重复且分布分散。 */
    private static final long STRIDE = 499_979L;
    public static final int LENGTH = 18;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 当前时间前缀及其随机起点。前缀变化时整体替换。 */
    private final AtomicReference<Window> window = new AtomicReference<>();

    private record Window(String prefix, long origin, AtomicLong sequence) {
    }

    public OrderRefGenerator() {
        this(Clock.systemDefaultZone());
    }

    public OrderRefGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        String prefix = LocalDateTime.now(clock).format(PREFIX);
        Window current = window.get();

        if (current == null || !current.prefix().equals(prefix)) {
            Window fresh = new Window(prefix, random.nextInt(SUFFIX_SPACE), new AtomicLong());
            // 并发下只有一个线程能换窗口，其余沿用换好的那个
            current = window.compareAndSet(current, fresh) ? fresh : window.get();
            // 极端情况下别的线程可能又换成了更新的前缀，直接用它即可
            if (!current.prefix().equals(prefix)) {
                prefix = current.prefix();
            }
        }

        long index = current.sequence().getAndIncrement();
        long suffix = Math.floorMod(current.origin() + index * STRIDE, SUFFIX_SPACE);
        return prefix + String.format("%06d", suffix);
    }

    /** 格式校验，供接口层拒绝明显非法的单号，避免白白打一次数据库。 */
    public static boolean isWellFormed(String orderRef) {
        if (orderRef == null || orderRef.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            if (!Character.isDigit(orderRef.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
