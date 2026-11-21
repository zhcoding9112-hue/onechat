-- 新人券：注册即发。
--
-- 此前发券只有 grantToAll，是一次性快照——执行那一刻库里有谁就发给谁，
-- 之后注册的人拿不到。于是首页「新人首单立减」的横幅对真正的新人并不兑现。
--
-- 加一个标记，而不是在代码里写死某个券 id：哪张券算「新人券」是运营的决定，
-- 而且以后可能同时有多张（比如新人券 + 节日券）。
ALTER TABLE coupon
    ADD COLUMN auto_grant_on_register TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '注册时自动发放给新用户' AFTER enabled;

-- 查「有哪些要自动发」的条件是 enabled + 本列，走同一个索引
CREATE INDEX idx_coupon_auto_grant ON coupon (auto_grant_on_register, enabled);
