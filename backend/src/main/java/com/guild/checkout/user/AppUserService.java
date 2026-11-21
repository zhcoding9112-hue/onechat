package com.guild.checkout.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.guild.checkout.user.event.UserRegistered;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户的建立与资料维护。
 */
@Service
public class AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    private final AppUserMapper userMapper;
    private final ApplicationEventPublisher events;

    public AppUserService(AppUserMapper userMapper, ApplicationEventPublisher events) {
        this.userMapper = userMapper;
        this.events = events;
    }

    public AppUser findByOpenId(String openId) {
        return userMapper.selectOne(Wrappers.<AppUser>lambdaQuery().eq(AppUser::getOpenId, openId));
    }

    public AppUser requireById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 按 openid 建立或更新用户。首次授权建号并置为粉丝角色；再次授权只刷新资料，不动角色。
     * 资料为空时保留原值，避免微信资料接口失败把已有昵称头像清掉。
     */
    @Transactional
    public AppUser registerOrUpdate(String openId, String unionId, String nickname, String avatarUrl) {
        AppUser existing = findByOpenId(openId);
        if (existing != null) {
            boolean changed = false;
            if (unionId != null && !unionId.equals(existing.getUnionId())) {
                existing.setUnionId(unionId);
                changed = true;
            }
            if (nickname != null && !nickname.isBlank() && !nickname.equals(existing.getNickname())) {
                existing.setNickname(nickname);
                changed = true;
            }
            if (avatarUrl != null && !avatarUrl.isBlank() && !avatarUrl.equals(existing.getAvatarUrl())) {
                existing.setAvatarUrl(avatarUrl);
                changed = true;
            }
            if (changed) {
                userMapper.updateById(existing);
            }
            return existing;
        }

        AppUser user = new AppUser();
        user.setOpenId(openId);
        user.setUnionId(unionId);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setRoles(Roles.DEFAULT);
        try {
            userMapper.insert(user);
            log.info("新建用户 id={} openId={}", user.getId(), mask(openId));
            // 只有真的插进去了才发事件；下面并发撞车的那一侧不发
            events.publishEvent(new UserRegistered(user.getId(), openId));
            return user;
        } catch (DuplicateKeyException ex) {
            // 同一用户并发首次授权：唯一索引挡住第二条，回读即可。
            AppUser raced = findByOpenId(openId);
            if (raced == null) {
                throw ex;
            }
            return raced;
        }
    }

    /** 授予角色。已有则不变。 */
    @Transactional
    public AppUser grantRole(Long userId, UserRole role) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        int before = user.getRoles() == null ? 0 : user.getRoles();
        user.grant(role);
        if (user.getRoles() != before) {
            userMapper.updateById(user);
        }
        return user;
    }

    /** 回收角色。粉丝角色不可回收。 */
    @Transactional
    public AppUser revokeRole(Long userId, UserRole role) {
        if (role == UserRole.FAN) {
            throw new IllegalArgumentException("粉丝角色不可回收");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        int before = user.getRoles() == null ? 0 : user.getRoles();
        user.revoke(role);
        if (user.getRoles() != before) {
            userMapper.updateById(user);
        }
        return user;
    }

    private static String mask(String openId) {
        if (openId == null || openId.length() <= 6) {
            return "***";
        }
        return openId.substring(0, 3) + "***" + openId.substring(openId.length() - 3);
    }
}
