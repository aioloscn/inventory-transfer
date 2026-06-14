package com.sits.admin.service;

import com.sits.admin.dto.WarehouseCreateDTO;
import com.sits.admin.dto.WarehouseUpdateDTO;
import com.sits.admin.entity.Warehouse;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;

/**
 * Warehouse service interface.
 */
public interface WarehouseService {

    Warehouse create(WarehouseCreateDTO dto);

    Warehouse update(Long id, WarehouseUpdateDTO dto);

    void disable(Long id);

    Warehouse getById(Long id);

    Warehouse getByCode(String warehouseCode);

    PageResult<Warehouse> page(PageQuery query);
}
