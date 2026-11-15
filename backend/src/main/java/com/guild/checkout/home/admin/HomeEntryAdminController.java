package com.guild.checkout.home.admin;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.home.HomeEntry;
import com.guild.checkout.home.HomeEntryMapper;
import com.guild.checkout.home.HomeEntryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 运营维护首页的九宫格入口与横幅。 */
@RestController
@RequestMapping("/api/admin/home-entries")
@RequireRole(com.guild.checkout.user.UserRole.OPERATOR)
public class HomeEntryAdminController {

    private final HomeEntryMapper mapper;

    public HomeEntryAdminController(HomeEntryMapper mapper) {
        this.mapper = mapper;
    }

    public record EntryRequest(
            @NotBlank(message = "位置类型不能为空") String entryType,
            @NotBlank(message = "标题不能为空") @Size(max = 32, message = "标题不超过 32 字符") String title,
            @Size(max = 64, message = "副标题不超过 64 字符") String subtitle,
            @Size(max = 512) String imageUrl,
            @Size(max = 512) String targetUrl,
            Integer sortOrder,
            Boolean enabled,
            LocalDateTime startAt,
            LocalDateTime endAt) {
    }

    @GetMapping
    public ApiResponse<List<HomeEntry>> list() {
        return ApiResponse.ok(mapper.selectList(Wrappers.<HomeEntry>lambdaQuery()
                .orderByAsc(HomeEntry::getEntryType)
                .orderByAsc(HomeEntry::getSortOrder)
                .orderByAsc(HomeEntry::getId)));
    }

    @PostMapping
    public ApiResponse<HomeEntry> create(@Valid @RequestBody EntryRequest req) {
        HomeEntry e = new HomeEntry();
        apply(e, req);
        mapper.insert(e);
        return ApiResponse.ok(e);
    }

    @PutMapping("/{id}")
    public ApiResponse<HomeEntry> update(@PathVariable long id, @Valid @RequestBody EntryRequest req) {
        HomeEntry e = mapper.selectById(id);
        if (e == null) {
            throw new BusinessException("首页位不存在: " + id);
        }
        apply(e, req);
        // 生效时间可以被清空（改回长期有效），而 updateById 会跳过 null 字段，
        // 所以这两列必须走显式 set。
        mapper.update(null, Wrappers.<HomeEntry>lambdaUpdate()
                .eq(HomeEntry::getId, id)
                .set(HomeEntry::getEntryType, e.getEntryType())
                .set(HomeEntry::getTitle, e.getTitle())
                .set(HomeEntry::getSubtitle, e.getSubtitle())
                .set(HomeEntry::getImageUrl, e.getImageUrl())
                .set(HomeEntry::getTargetUrl, e.getTargetUrl())
                .set(HomeEntry::getSortOrder, e.getSortOrder())
                .set(HomeEntry::getEnabled, e.getEnabled())
                .set(HomeEntry::getStartAt, e.getStartAt())
                .set(HomeEntry::getEndAt, e.getEndAt()));
        return ApiResponse.ok(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        mapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    private static void apply(HomeEntry e, EntryRequest req) {
        HomeEntryType type;
        try {
            type = HomeEntryType.of(req.entryType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("位置类型只能是 GRID 或 BANNER");
        }
        if (req.startAt() != null && req.endAt() != null && req.endAt().isBefore(req.startAt())) {
            throw new BusinessException("生效结束时间不能早于开始时间");
        }
        e.setEntryType(type.name());
        e.setTitle(req.title().trim());
        e.setSubtitle(trimToNull(req.subtitle()));
        e.setImageUrl(trimToNull(req.imageUrl()));
        e.setTargetUrl(trimToNull(req.targetUrl()));
        e.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        e.setEnabled(req.enabled() == null || req.enabled());
        e.setStartAt(req.startAt());
        e.setEndAt(req.endAt());
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
