package com.sits.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sits.inventory.entity.WarehouseInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * WarehouseInventory Mapper.
 */
@Mapper
public interface WarehouseInventoryMapper extends BaseMapper<WarehouseInventory> {

    /**
     * 批量查询指定 SKU 的库存。
     */
    @Select("<script>" +
            "SELECT * FROM warehouse_inventory WHERE sku_id IN " +
            "<foreach collection='skuIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<WarehouseInventory> selectBySkuIds(@Param("skuIds") List<Long> skuIds);
}
