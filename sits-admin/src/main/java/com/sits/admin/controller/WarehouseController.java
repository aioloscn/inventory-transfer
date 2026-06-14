package com.sits.admin.controller;

import com.sits.admin.dto.WarehouseCreateDTO;
import com.sits.admin.dto.WarehouseUpdateDTO;
import com.sits.admin.entity.Warehouse;
import com.sits.admin.service.WarehouseService;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.base.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Warehouse management API.
 */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @PostMapping
    public Result<Warehouse> create(@Valid @RequestBody WarehouseCreateDTO dto) {
        return Result.success(warehouseService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Warehouse> update(@PathVariable Long id, @RequestBody WarehouseUpdateDTO dto) {
        return Result.success(warehouseService.update(id, dto));
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        warehouseService.disable(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Warehouse> getById(@PathVariable Long id) {
        return Result.success(warehouseService.getById(id));
    }

    @GetMapping
    public Result<PageResult<Warehouse>> page(PageQuery query) {
        return Result.success(warehouseService.page(query));
    }
}
