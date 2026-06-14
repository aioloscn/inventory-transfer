package com.sits.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.dto.WarehouseCreateDTO;
import com.sits.admin.dto.WarehouseUpdateDTO;
import com.sits.admin.entity.Warehouse;
import com.sits.admin.mapper.WarehouseMapper;
import com.sits.admin.service.WarehouseService;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.enums.WarehouseStatus;
import com.sits.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warehouse service implementation.
 */
@Service
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;

    public WarehouseServiceImpl(WarehouseMapper warehouseMapper) {
        this.warehouseMapper = warehouseMapper;
    }

    @Override
    @Transactional
    public Warehouse create(WarehouseCreateDTO dto) {
        // Check code uniqueness
        Warehouse existing = getByCode(dto.getWarehouseCode());
        if (existing != null) {
            throw new BusinessException("仓库编码已存在: " + dto.getWarehouseCode());
        }

        Warehouse warehouse = new Warehouse();
        BeanUtil.copyProperties(dto, warehouse);
        warehouse.setStatus(WarehouseStatus.NORMAL.getCode());
        warehouseMapper.insert(warehouse);
        return warehouse;
    }

    @Override
    @Transactional
    public Warehouse update(Long id, WarehouseUpdateDTO dto) {
        Warehouse warehouse = getById(id);
        if (warehouse == null) {
            throw new BusinessException("仓库不存在: " + id);
        }
        BeanUtil.copyProperties(dto, warehouse, "id", "warehouseCode", "status");
        warehouseMapper.updateById(warehouse);
        return warehouse;
    }

    @Override
    @Transactional
    public void disable(Long id) {
        Warehouse warehouse = getById(id);
        if (warehouse == null) {
            throw new BusinessException("仓库不存在: " + id);
        }
        warehouse.setStatus(WarehouseStatus.DISABLED.getCode());
        warehouseMapper.updateById(warehouse);
    }

    @Override
    public Warehouse getById(Long id) {
        return warehouseMapper.selectById(id);
    }

    @Override
    public Warehouse getByCode(String warehouseCode) {
        return warehouseMapper.selectOne(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getWarehouseCode, warehouseCode)
        );
    }

    @Override
    public PageResult<Warehouse> page(PageQuery query) {
        Page<Warehouse> page = warehouseMapper.selectPage(
                query.toPage(),
                new LambdaQueryWrapper<Warehouse>().orderByDesc(Warehouse::getUpdateTime)
        );
        return PageResult.of(page);
    }
}
