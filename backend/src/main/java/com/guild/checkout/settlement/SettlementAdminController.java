package com.guild.checkout.settlement;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 运营侧的结算报表。整类要求运营角色。 */
@RestController
@RequestMapping("/api/admin/settlement")
@RequireRole(UserRole.OPERATOR)
public class SettlementAdminController {

    private final SettlementService settlementService;
    private final SettlementReportService reportService;

    public SettlementAdminController(SettlementService settlementService,
                                     SettlementReportService reportService) {
        this.settlementService = settlementService;
        this.reportService = reportService;
    }

    public record MarkRequest(@Size(max = 255) String note) {
    }

    /** 月度结算报表，按主播逐行。周期内无流水的主播以零值成行。 */
    @GetMapping("/report")
    public ApiResponse<List<SettlementService.TalentStatement>> report(@RequestParam String period) {
        return ApiResponse.ok(reportService.monthly(period));
    }

    /** 某主播某周期的逐笔明细，含已冲销的。 */
    @GetMapping("/entries")
    public ApiResponse<List<SettlementEntry>> entries(@RequestParam String period,
                                                      @RequestParam long talentId) {
        return ApiResponse.ok(settlementService.allEntries(period, talentId));
    }

    /** 导出报表。CSV 带 BOM，Excel 直接打开不乱码。 */
    @GetMapping("/report/export")
    public ResponseEntity<Resource> export(@RequestParam String period) {
        byte[] body = reportService.exportCsv(period);
        return download(body, "settlement-" + period + ".csv");
    }

    @GetMapping("/entries/export")
    public ResponseEntity<Resource> exportDetail(@RequestParam String period,
                                                 @RequestParam long talentId) {
        byte[] body = reportService.exportDetailCsv(period, talentId);
        return download(body, "settlement-%s-talent-%d.csv".formatted(period, talentId));
    }

    /** 标记已线下结清。只记录标记与时间，不发生任何资金动作。 */
    @PutMapping("/marks/{talentId}/{period}")
    public ApiResponse<SettlementMark> mark(@PathVariable long talentId,
                                            @PathVariable String period,
                                            @Valid @RequestBody(required = false) MarkRequest req) {
        Long operatorId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(settlementService.markSettled(
                talentId, period, operatorId, req == null ? null : req.note()));
    }

    /** 撤销结清标记。 */
    @DeleteMapping("/marks/{talentId}/{period}")
    public ApiResponse<Void> unmark(@PathVariable long talentId, @PathVariable String period) {
        settlementService.unmarkSettled(talentId, period);
        return ApiResponse.ok();
    }

    private static ResponseEntity<Resource> download(byte[] body, String filename) {
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s\"; filename*=UTF-8''%s".formatted(filename, encoded))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(new ByteArrayResource(body));
    }
}
