package com.guild.checkout.match;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.talent.Talent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 随机匹配。只读接口，不产生任何账务或订单副作用。 */
@RestController
@RequestMapping("/api/match")
public class RandomMatchController {

    private final RandomMatchService matchService;

    public RandomMatchController(RandomMatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * 匹配结果。
     *
     * matched 为假时 talent 为 null，并给出建议文案——
     * 不返回任何主播，也不退化成「随便给一个」。
     */
    public record MatchResult(boolean matched, MatchedTalent talent, String message) {
    }

    public record MatchedTalent(
            Long id, String nickname, String coverUrl, boolean online,
            String gender, Integer age, String region, String intro,
            String audioUrl, Integer audioSeconds) {
    }

    @GetMapping("/random")
    public ApiResponse<MatchResult> random(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String gender,
            @RequestParam(defaultValue = "false") boolean onlineOnly,
            @RequestParam(defaultValue = "false") boolean excludeOrdered,
            @RequestParam(required = false) List<Long> exclude) {

        long fanUserId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(matchService
                .match(fanUserId, new RandomMatchService.Criteria(
                        category, gender, onlineOnly, excludeOrdered, exclude))
                .map(t -> new MatchResult(true, toView(t), null))
                .orElseGet(() -> new MatchResult(false, null, "暂时没有符合条件的主播，试试放宽条件")));
    }

    private static MatchedTalent toView(Talent t) {
        return new MatchedTalent(
                t.getId(), t.getNickname(), t.getCoverUrl(),
                Boolean.TRUE.equals(t.getOnline()),
                t.getGender(), t.getAge(), t.getRegion(), t.getIntro(),
                t.getAudioUrl(), t.getAudioSeconds());
    }
}
