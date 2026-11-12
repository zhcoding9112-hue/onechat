package com.guild.checkout.pricing.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceItemMapper;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.ServiceSpecMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 运营侧的服务目录维护。
 *
 * 停用采用软开关而非删除：历史订单引用了项目与档位的 id 与名称，删除会让历史订单无从追溯。
 * 停用只影响粉丝端的可售范围，既有订单不受任何影响。
 */
@Service
public class ServiceCatalogAdminService {

    private final ServiceItemMapper itemMapper;
    private final ServiceSpecMapper specMapper;

    public ServiceCatalogAdminService(ServiceItemMapper itemMapper, ServiceSpecMapper specMapper) {
        this.itemMapper = itemMapper;
        this.specMapper = specMapper;
    }

    // ---------- 服务项目 ----------

    /** 全部项目，含停用的。运营需要看到停用项目才能重新启用。 */
    public List<ServiceItem> listItems() {
        return itemMapper.selectList(Wrappers.<ServiceItem>lambdaQuery()
                .orderByAsc(ServiceItem::getSortOrder)
                .orderByAsc(ServiceItem::getId));
    }

    @Transactional
    public ServiceItem createItem(String code, String name, String description, Integer sortOrder) {
        requireText(code, "项目编码");
        requireText(name, "项目名称");

        ServiceItem item = new ServiceItem();
        item.setCode(code.trim());
        item.setName(name.trim());
        item.setDescription(trimToNull(description));
        item.setSortOrder(sortOrder == null ? 0 : sortOrder);
        item.setEnabled(true);
        try {
            itemMapper.insert(item);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("项目编码已存在: " + code);
        }
        return item;
    }

    /**
     * 修改项目。编码不可改——它是埋点与报表的归类依据，改了会让历史数据对不上。
     */
    @Transactional
    public ServiceItem updateItem(long itemId, String name, String description, Integer sortOrder) {
        ServiceItem item = requireItem(itemId);
        if (name != null) {
            requireText(name, "项目名称");
            item.setName(name.trim());
        }
        if (description != null) {
            item.setDescription(trimToNull(description));
        }
        if (sortOrder != null) {
            item.setSortOrder(sortOrder);
        }
        itemMapper.updateById(item);
        return item;
    }

    @Transactional
    public ServiceItem setItemEnabled(long itemId, boolean enabled) {
        ServiceItem item = requireItem(itemId);
        if (Boolean.valueOf(enabled).equals(item.getEnabled())) {
            return item;
        }
        item.setEnabled(enabled);
        itemMapper.updateById(item);
        return item;
    }

    // ---------- 档位 ----------

    public List<ServiceSpec> listSpecs(long itemId) {
        requireItem(itemId);
        return specMapper.selectList(Wrappers.<ServiceSpec>lambdaQuery()
                .eq(ServiceSpec::getServiceItemId, itemId)
                .orderByAsc(ServiceSpec::getSortOrder)
                .orderByAsc(ServiceSpec::getId));
    }

    @Transactional
    public ServiceSpec createSpec(long itemId, String code, String name,
                                  Integer durationMinutes, Integer sortOrder) {
        requireItem(itemId);
        requireText(code, "档位编码");
        requireText(name, "档位名称");
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new BusinessException("服务时长必须大于零");
        }

        ServiceSpec spec = new ServiceSpec();
        spec.setServiceItemId(itemId);
        spec.setCode(code.trim());
        spec.setName(name.trim());
        spec.setDurationMinutes(durationMinutes);
        spec.setSortOrder(sortOrder == null ? (durationMinutes == null ? 0 : durationMinutes) : sortOrder);
        spec.setEnabled(true);
        try {
            specMapper.insert(spec);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("该项目下档位编码已存在: " + code);
        }
        return spec;
    }

    @Transactional
    public ServiceSpec updateSpec(long specId, String name, Integer durationMinutes, Integer sortOrder) {
        ServiceSpec spec = requireSpec(specId);
        if (name != null) {
            requireText(name, "档位名称");
            spec.setName(name.trim());
        }
        if (durationMinutes != null) {
            if (durationMinutes <= 0) {
                throw new BusinessException("服务时长必须大于零");
            }
            spec.setDurationMinutes(durationMinutes);
        }
        if (sortOrder != null) {
            spec.setSortOrder(sortOrder);
        }
        specMapper.updateById(spec);
        return spec;
    }

    @Transactional
    public ServiceSpec setSpecEnabled(long specId, boolean enabled) {
        ServiceSpec spec = requireSpec(specId);
        if (Boolean.valueOf(enabled).equals(spec.getEnabled())) {
            return spec;
        }
        spec.setEnabled(enabled);
        specMapper.updateById(spec);
        return spec;
    }

    // ---------- 辅助 ----------

    public ServiceItem requireItem(long itemId) {
        ServiceItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new NotFoundException("服务项目不存在: " + itemId);
        }
        return item;
    }

    public ServiceSpec requireSpec(long specId) {
        ServiceSpec spec = specMapper.selectById(specId);
        if (spec == null) {
            throw new NotFoundException("档位不存在: " + specId);
        }
        return spec;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + "不能为空");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
