package com.sits.admin.controller;

import com.sits.admin.dto.SkuCreateDTO;
import com.sits.admin.dto.SkuUpdateDTO;
import com.sits.admin.entity.Sku;
import com.sits.admin.service.SkuService;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.base.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * SKU management API.
 */
@RestController
@RequestMapping("/api/skus")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    @PostMapping
    public Result<Sku> create(@Valid @RequestBody SkuCreateDTO dto) {
        return Result.success(skuService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Sku> update(@PathVariable Long id, @RequestBody SkuUpdateDTO dto) {
        return Result.success(skuService.update(id, dto));
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        skuService.disable(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Sku> getById(@PathVariable Long id) {
        return Result.success(skuService.getById(id));
    }

    @GetMapping
    public Result<PageResult<Sku>> page(PageQuery query) {
        return Result.success(skuService.page(query));
    }
}
