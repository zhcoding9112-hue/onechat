package com.guild.checkout.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件。
 *
 * 乐观锁拦截器是必须的：实体上的 @Version 只是声明，真正把 version 条件拼进
 * UPDATE 语句、并在写回后自增的是这个拦截器。不注册它，@Version 形同虚设，
 * 「主播接单」与「超时处置」并发时会互相覆盖。
 *
 * 暂不注册分页拦截器：MyBatis-Plus 3.5.9 起它需要额外的 jsqlparser 构件，
 * 而当前体量（月订单数百笔）的所有列表都可以一次查完，等真的需要分页时再加。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
