package com.guild.checkout.survey;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 满意度回访。
 *
 * 三个入口对应三种可见范围，刻意分成三个路径而不是一个带参数的接口——
 * 路径不同才能各自挂不同的角色守卫，少一层出错的可能。
 */
@RestController
public class SurveyController {

    private final SurveyService surveyService;
    private final com.guild.checkout.talent.TalentService talentService;

    public SurveyController(SurveyService surveyService,
                            com.guild.checkout.talent.TalentService talentService) {
        this.surveyService = surveyService;
        this.talentService = talentService;
    }

    public record SubmitRequest(
            @Min(value = 1, message = "评分需为 1 到 5 分")
            @Max(value = 5, message = "评分需为 1 到 5 分") int score) {
    }

    public record MySurveyView(Long orderId, int score, java.time.LocalDateTime createdAt) {
    }

    // ---------- 粉丝提交 ----------

    @PostMapping("/api/orders/{orderId}/survey")
    public ApiResponse<MySurveyView> submit(@PathVariable long orderId,
                                            @Valid @RequestBody SubmitRequest req) {
        long userId = CurrentUserHolder.requireUserId();
        SatisfactionSurvey survey = surveyService.submit(orderId, userId, req.score());
        return ApiResponse.ok(new MySurveyView(
                survey.getOrderId(), survey.getScore(), survey.getCreatedAt()));
    }

    /** 我给这笔订单打过几分。只有下单本人能查自己的那一条。 */
    @GetMapping("/api/orders/{orderId}/survey")
    public ApiResponse<MySurveyView> mySurvey(@PathVariable long orderId) {
        long userId = CurrentUserHolder.requireUserId();
        SatisfactionSurvey survey = surveyService.findByOrder(orderId);
        if (survey == null || !java.util.Objects.equals(survey.getFanUserId(), userId)) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(new MySurveyView(
                survey.getOrderId(), survey.getScore(), survey.getCreatedAt()));
    }

    // ---------- 运营逐单明细 ----------

    public record TalentDetailView(
            SurveyService.Aggregate aggregate,
            List<SurveyService.DetailEntry> details) {
    }

    @GetMapping("/api/admin/talents/{talentId}/survey")
    @RequireRole(UserRole.OPERATOR)
    public ApiResponse<TalentDetailView> adminDetail(@PathVariable long talentId) {
        return ApiResponse.ok(new TalentDetailView(
                surveyService.aggregateOf(talentId),
                surveyService.detailsOf(talentId)));
    }

    /** 全部主播的聚合评分，运营用来横向比较。 */
    @GetMapping("/api/admin/survey/overview")
    @RequireRole(UserRole.OPERATOR)
    public ApiResponse<Map<Long, SurveyService.Aggregate>> overview() {
        // 体量很小，逐个聚合即可；真要优化也该等有数据之后
        Map<Long, SurveyService.Aggregate> result = new java.util.LinkedHashMap<>();
        for (com.guild.checkout.talent.Talent t : talentService.listAll()) {
            result.put(t.getId(), surveyService.aggregateOf(t.getId()));
        }
        return ApiResponse.ok(result);
    }
}
