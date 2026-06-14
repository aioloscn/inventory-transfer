package com.sits.admin.service;

import com.sits.admin.dto.SkuCreateDTO;
import com.sits.admin.dto.SkuUpdateDTO;
import com.sits.admin.entity.Sku;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;

/**
 * SKU service interface.
 */
public interface SkuService {

    Sku create(SkuCreateDTO dto);

    Sku update(Long id, SkuUpdateDTO dto);

    void disable(Long id);

    Sku getById(Long id);

    Sku getByCode(String skuCode);

    PageResult<Sku> page(PageQuery query);
}
