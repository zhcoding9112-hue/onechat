package com.guild.checkout.common.db;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 插入与更新时自动填充 createdAt / updatedAt。
 *
 * 为什么需要它：建表时这两列都有 DEFAULT CURRENT_TIMESTAMP(3)，库里的值一直是对的，
 * 但 MyBatis-Plus 的 insert 只会把自增主键回写进实体，不会回写数据库默认值。
 * 于是「插入后直接把实体返回给调用方」的接口，响应里 createdAt 是 null——
 * 本项目里至少有五处这样的接口（发布评论、运营发放元子、提交满意度、上传支付凭证、
 * 创建打赏），都是直到有一条用例去断言发布时间才暴露出来的。
 *
 * strictInsertFill 只在字段为 null 时填充，所以显式设过值的地方不受影响；
 * 表上的 DEFAULT 也原样保留，作为绕过 ORM 直接写库时的兜底。
 */
@Component
public class AuditTimeFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
