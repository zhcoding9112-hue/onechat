package com.guild.checkout.talent;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.common.exception.NotImplementedException;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主播工作台。整类接口要求主播角色，粉丝访问一律无权限。
 *
 * 当前只落了路由与角色守卫；待接单列表、接单与标记完成、在线状态、收入与评分
 * 由 talent-workspace 相关任务填充。
 */
@RestController
@RequestMapping("/api/talent/workspace")
@RequireRole(UserRole.TALENT)
public class TalentWorkspaceController {

    private final TalentService talentService;
    private final TalentMapper talentMapper;

    public TalentWorkspaceController(TalentService talentService, TalentMapper talentMapper) {
        this.talentService = talentService;
        this.talentMapper = talentMapper;
    }

    /** 当前登录者对应的主播档案。没有档案说明角色与档案不一致，按无权限处理。 */
    private Talent requireMyTalent() {
        Long userId = CurrentUserHolder.requireUserId();
        Talent talent = talentService.findByUserId(userId);
        if (talent == null) {
            throw new ForbiddenException("当前账号未关联任何主播档案");
        }
        return talent;
    }

    /**
     * 主播自己看到的档案。刻意不复用运营端的视图：
     * userId、排序这类运营才关心的字段不该出现在主播端。
     */
    public record MyProfile(
            Long talentId, String nickname, String coverUrl, String intro,
            java.util.List<String> tags, boolean online, boolean listed,
            String audioUrl, Integer audioSeconds) {
    }

    @GetMapping("/me")
    public ApiResponse<MyProfile> me() {
        return ApiResponse.ok(toProfile(requireMyTalent()));
    }

    /** 切换在线状态。结果即时反映到粉丝端列表与详情。 */
    @PutMapping("/online/{online}")
    public ApiResponse<MyProfile> setOnline(@PathVariable boolean online) {
        Talent talent = requireMyTalent();
        if (!Boolean.valueOf(online).equals(talent.getOnline())) {
            talent.setOnline(online);
            talentMapper.updateById(talent);
        }
        return ApiResponse.ok(toProfile(talent));
    }

    private MyProfile toProfile(Talent talent) {
        return new MyProfile(
                talent.getId(), talent.getNickname(), talent.getCoverUrl(), talent.getIntro(),
                talentService.listTags(talent.getId()).stream().map(TalentTag::getName).toList(),
                Boolean.TRUE.equals(talent.getOnline()),
                Boolean.TRUE.equals(talent.getListed()),
                talent.getAudioUrl(), talent.getAudioSeconds());
    }

    @GetMapping("/pending-orders")
    public ApiResponse<Void> pendingOrders() {
        throw new NotImplementedException("待接单列表尚未实现");
    }
}
