package com.guild.checkout.talent.admin;

import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.talent.Talent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 主播花名册批量导入。
 *
 * 为什么要这个：一位主播要填昵称、性别、年龄、地区、简介、标签、宣传语，再去
 * 另一页设价格。几十个人一个个点，是几小时的机械劳动，而且中途错一条很难发现。
 *
 * **刻意不导入封面与试听**：那是文件，只能一个个传（主播管理页的「素材」按钮）。
 * 这里只把能从表格里来的部分搞定，剩下的是绕不开的人工。
 */
@Service
public class TalentImportService {

    private static final Logger log = LoggerFactory.getLogger(TalentImportService.class);

    /** 单次导入上限。再多该怀疑是不是贴错文件了。 */
    private static final int MAX_ROWS = 500;

    private final TalentAdminService talentAdminService;
    private final TalentPriceAdminService priceService;
    private final ServiceCatalogAdminService catalogService;

    public TalentImportService(TalentAdminService talentAdminService,
                               TalentPriceAdminService priceService,
                               ServiceCatalogAdminService catalogService) {
        this.talentAdminService = talentAdminService;
        this.priceService = priceService;
        this.catalogService = catalogService;
    }

    /**
     * 一行的处理结果。
     *
     * @param line    行号，从 1 开始，不含表头。出错时运营要照着这个号去改表格
     * @param ok      这一行是否成功
     * @param talentId 成功时的主播 id
     * @param message 失败原因，或成功时的说明
     */
    public record RowResult(int line, boolean ok, Long talentId, String nickname, String message) {
    }

    /**
     * @param dryRun     是否只校验不写库
     * @param created    成功条数
     * @param failed     失败条数
     */
    public record ImportResult(boolean dryRun, int created, int failed, List<RowResult> rows) {
    }

    /** CSV 表头。顺序固定，导入时按位置取，不按名字猜。 */
    public static final List<String> HEADERS = List.of(
            "昵称", "性别", "年龄", "地区", "简介", "标签", "宣传语", "排序", "服务档位", "价格元");

    /**
     * 导入。
     *
     * 整批一个事务：一行失败就整批回滚。原因是导到一半的花名册最难收拾——
     * 运营不知道哪些进去了、哪些没有，重导又会撞昵称。
     * 所以先用 dryRun 把表格改干净，再正式导。
     */
    @Transactional
    public ImportResult importCsv(String csv, boolean dryRun) {
        List<String[]> rows = parse(csv);
        if (rows.isEmpty()) {
            throw new BusinessException("表格是空的，至少要有表头和一行数据");
        }
        // 第一行当表头丢掉，但要确认它真的是表头，免得把第一位主播吞了
        String[] header = rows.get(0);
        if (header.length == 0 || !HEADERS.get(0).equals(header[0].trim())) {
            throw new BusinessException("第一行必须是表头，第一列应为「昵称」");
        }
        List<String[]> body = rows.subList(1, rows.size());
        if (body.size() > MAX_ROWS) {
            throw new BusinessException("单次最多导入 %d 行，收到 %d 行".formatted(MAX_ROWS, body.size()));
        }

        Map<String, ServiceSpec> specs = specsByCode();
        List<RowResult> results = new ArrayList<>();
        int created = 0;
        int failed = 0;

        for (int i = 0; i < body.size(); i++) {
            int line = i + 1;
            String[] row = body.get(i);
            try {
                results.add(importRow(line, row, specs));
                created++;
            } catch (BusinessException | IllegalArgumentException ex) {
                results.add(new RowResult(line, false, null, cell(row, 0), ex.getMessage()));
                failed++;
            }
        }

        if (failed > 0) {
            // 抛出去由上层回滚。消息里带上第一条错误，省得运营还要翻结果
            RowResult first = results.stream().filter(r -> !r.ok()).findFirst().orElseThrow();
            throw new ImportFailed("第 %d 行：%s（共 %d 行有问题，整批未导入）"
                    .formatted(first.line(), first.message(), failed),
                    new ImportResult(dryRun, created, failed, results));
        }

        if (dryRun) {
            // 校验通过也要回滚：这次只是试算
            throw new DryRunComplete(new ImportResult(true, created, 0, results));
        }

        log.info("批量导入主播 {} 位", created);
        return new ImportResult(false, created, 0, results);
    }

    private RowResult importRow(int line, String[] row, Map<String, ServiceSpec> specs) {
        String nickname = cell(row, 0);
        if (nickname.isEmpty()) {
            throw new BusinessException("昵称为必填");
        }

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname,
                blankToNull(cell(row, 4)),
                null,                       // 封面只能单独上传
                parseInt(cell(row, 7), "排序", 0),
                gender(cell(row, 1)),
                parseAge(cell(row, 2)),
                blankToNull(cell(row, 3)),
                split(cell(row, 6))));

        List<String> tags = split(cell(row, 5));
        if (!tags.isEmpty()) {
            talentAdminService.replaceTags(talent.getId(), tags);
        }

        int priced = applyPrices(talent.getId(), cell(row, 8), cell(row, 9), specs);
        String note = switch (priced) {
            case 0 -> "已建档";
            case 1 -> "已建档并设价";
            default -> "已建档并设价 %d 档".formatted(priced);
        };

        // 不自动上架：封面与试听还没传，上架就会在粉丝端留白
        return new RowResult(line, true, talent.getId(), nickname, note);
    }

    /**
     * 一行里的多档多价。
     *
     * 「服务档位」与「价格元」两列都用竖线分隔、按位置一一对应，和标签那列一个写法：
     *
     *     listen:m15|listen:m30|listen:m60
     *     9|15|25
     *
     * 为什么不允许「一个价配多个档位」：30 分钟和 120 分钟卖同一个价，
     * 几乎只会是填错，而不是真的想这么定价。个数不等就报错，别替运营猜。
     *
     * @return 这一行实际设了几档价
     */
    private int applyPrices(long talentId, String specCell, String priceCell,
                            Map<String, ServiceSpec> specs) {
        if (specCell.isEmpty() && priceCell.isEmpty()) {
            return 0;
        }
        if (specCell.isEmpty() || priceCell.isEmpty()) {
            throw new BusinessException("「服务档位」与「价格元」要么都填，要么都留空");
        }

        List<String> specCodes = pairCells(specCell, "服务档位");
        List<String> prices = pairCells(priceCell, "价格元");
        if (specCodes.size() != prices.size()) {
            throw new BusinessException(
                    "「服务档位」填了 %d 个、「价格元」填了 %d 个，两列个数要一样（用竖线分隔，按位置一一对应）"
                            .formatted(specCodes.size(), prices.size()));
        }

        // 同一档位在一行里出现两次：后一个会把前一个覆盖掉，静默生效最难查，直接报错
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (int i = 0; i < specCodes.size(); i++) {
            String code = specCodes.get(i).toLowerCase(Locale.ROOT);
            if (!seen.add(code)) {
                throw new BusinessException("服务档位「%s」在同一行里填了两次".formatted(specCodes.get(i)));
            }
            ServiceSpec spec = specs.get(code);
            if (spec == null) {
                throw new BusinessException("找不到服务档位「%s」，现有：%s"
                        .formatted(specCodes.get(i), String.join("、", specs.keySet())));
            }
            priceService.setPrice(talentId, spec.getId(), yuanToCents(prices.get(i)), true);
        }
        return specCodes.size();
    }

    /**
     * 按竖线切成一一对应的若干项。
     *
     * 和 split() 不同：这里**不丢弃空项**。「9||15」丢掉空项就变成两项、
     * 正好和两个档位对上，价格会错位落到别的档位上——宁可报错。
     */
    private static List<String> pairCells(String value, String column) {
        List<String> out = new ArrayList<>();
        for (String part : value.split("\\|", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new BusinessException("「%s」里有空项，检查是不是多打了一个竖线：%s"
                        .formatted(column, value));
            }
            out.add(trimmed);
        }
        return out;
    }

    /** 档位按「项目编码:档位编码」索引，例如 listen:m30。 */
    private Map<String, ServiceSpec> specsByCode() {
        Map<String, ServiceSpec> out = new HashMap<>();
        for (ServiceItem item : catalogService.listItems()) {
            for (ServiceSpec spec : catalogService.listSpecs(item.getId())) {
                out.put((item.getCode() + ":" + spec.getCode()).toLowerCase(Locale.ROOT), spec);
            }
        }
        return out;
    }

    // ---------- CSV ----------

    /**
     * 最小可用的 CSV 解析：支持双引号包裹、引号内的逗号与换行、"" 转义。
     *
     * 不引三方库，是因为这里只读运营自己导出的表格，场景固定；
     * 但引号必须支持——简介里出现逗号太正常了，按逗号裸切会把一行切散。
     */
    static List<String[]> parse(String csv) {
        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        String text = csv.startsWith("﻿") ? csv.substring(1) : csv;  // Excel 存的 BOM

        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(ch);
                }
                continue;
            }
            switch (ch) {
                case '"' -> quoted = true;
                case ',' -> {
                    cells.add(cur.toString());
                    cur.setLength(0);
                }
                case '\r' -> { /* 吃掉，由 \n 收行 */ }
                case '\n' -> {
                    cells.add(cur.toString());
                    cur.setLength(0);
                    if (!isBlankRow(cells)) {
                        rows.add(cells.toArray(new String[0]));
                    }
                    cells = new ArrayList<>();
                }
                default -> cur.append(ch);
            }
        }
        cells.add(cur.toString());
        if (!isBlankRow(cells)) {
            rows.add(cells.toArray(new String[0]));
        }
        return rows;
    }

    private static boolean isBlankRow(List<String> cells) {
        return cells.stream().allMatch(c -> c == null || c.isBlank());
    }

    private static String cell(String[] row, int index) {
        return index < row.length && row[index] != null ? row[index].trim() : "";
    }

    private static String blankToNull(String v) {
        return v.isEmpty() ? null : v;
    }

    /** 标签与宣传语用竖线分隔：逗号已经是 CSV 的分隔符，再用会逼运营到处加引号。 */
    private static List<String> split(String v) {
        if (v.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(v.split("\\|"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String gender(String v) {
        if (v.isEmpty()) {
            return null;
        }
        return switch (v) {
            case "女", "女生", "FEMALE", "female" -> "FEMALE";
            case "男", "男生", "MALE", "male" -> "MALE";
            default -> throw new BusinessException("性别只能填「男」或「女」，收到：" + v);
        };
    }

    private static Integer parseAge(String v) {
        if (v.isEmpty()) {
            return null;
        }
        int age = parseInt(v, "年龄", 0);
        if (age < 1 || age > 120) {
            throw new BusinessException("年龄应在 1 到 120 之间，收到：" + v);
        }
        return age;
    }

    private static int parseInt(String v, String field, int fallback) {
        if (v.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            throw new BusinessException("%s 应为整数，收到：%s".formatted(field, v));
        }
    }

    /** 元 → 分。不用浮点：0.1 + 0.2 的问题在金额上不能忍。 */
    static long yuanToCents(String v) {
        if (!v.matches("\\d+(\\.\\d{1,2})?")) {
            throw new BusinessException("价格应为正数，最多两位小数，收到：" + v);
        }
        String[] parts = v.split("\\.");
        long cents = Long.parseLong(parts[0]) * 100;
        if (parts.length > 1) {
            cents += Long.parseLong((parts[1] + "00").substring(0, 2));
        }
        if (cents <= 0) {
            throw new BusinessException("价格必须大于零");
        }
        return cents;
    }

    /** 校验通过的试算：靠异常回滚，结果照常带出去。 */
    public static class DryRunComplete extends RuntimeException {
        private final transient ImportResult result;

        DryRunComplete(ImportResult result) {
            super("dry run");
            this.result = result;
        }

        public ImportResult result() {
            return result;
        }
    }

    /** 有行出错：整批回滚，但把逐行结果带出去给运营改表。 */
    public static class ImportFailed extends RuntimeException {
        private final transient ImportResult result;

        ImportFailed(String message, ImportResult result) {
            super(message);
            this.result = result;
        }

        public ImportResult result() {
            return result;
        }
    }
}
