package com.sits.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.dto.SkuCreateDTO;
import com.sits.admin.dto.SkuUpdateDTO;
import com.sits.admin.entity.Sku;
import com.sits.admin.mapper.SkuMapper;
import com.sits.admin.service.SkuService;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.enums.SkuStatus;
import com.sits.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SKU service implementation.
 */
@Service
public class SkuServiceImpl implements SkuService {

    private final SkuMapper skuMapper;

    public SkuServiceImpl(SkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    @Override
    @Transactional
    public Sku create(SkuCreateDTO dto) {
        Sku existing = getByCode(dto.getSkuCode());
        if (existing != null) {
            throw new BusinessException("SKU编码已存在: " + dto.getSkuCode());
        }

        Sku sku = new Sku();
        BeanUtil.copyProperties(dto, sku);
        sku.setStatus(SkuStatus.NORMAL.getCode());
        skuMapper.insert(sku);
        return sku;
    }

    @Override
    @Transactional
    public Sku update(Long id, SkuUpdateDTO dto) {
        Sku sku = getById(id);
        if (sku == null) {
            throw new BusinessException("SKU不存在: " + id);
        }
        BeanUtil.copyProperties(dto, sku, "id", "skuCode", "status");
        skuMapper.updateById(sku);
        return sku;
    }

    @Override
    @Transactional
    public void disable(Long id) {
        Sku sku = getById(id);
        if (sku == null) {
            throw new BusinessException("SKU不存在: " + id);
        }
        sku.setStatus(SkuStatus.DISABLED.getCode());
        skuMapper.updateById(sku);
    }

    @Override
    public Sku getById(Long id) {
        return skuMapper.selectById(id);
    }

    @Override
    public Sku getByCode(String skuCode) {
        return skuMapper.selectOne(
                new LambdaQueryWrapper<Sku>().eq(Sku::getSkuCode, skuCode)
        );
    }

    @Override
    public PageResult<Sku> page(PageQuery query) {
        Page<Sku> page = skuMapper.selectPage(
                query.toPage(),
                new LambdaQueryWrapper<Sku>().orderByDesc(Sku::getUpdateTime)
        );
        return PageResult.of(page);
    }
}
