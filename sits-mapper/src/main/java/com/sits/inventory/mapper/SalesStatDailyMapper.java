package com.sits.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sits.inventory.entity.SalesStatDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * SalesStatDaily Mapper.
 */
@Mapper
public interface SalesStatDailyMapper extends BaseMapper<SalesStatDaily> {

    /**
     * 批量聚合查询：按 sku_id + warehouse_id 分组查询近 N 天销量合计。
     * 返回 skuId, warehouseId, totalSales 三条数据的 Map 列表。
     */
    @Select("<script>" +
            "SELECT sku_id, warehouse_id, SUM(sales_quantity) AS total_sales " +
            "FROM sales_stat_daily " +
            "WHERE sku_id IN " +
            "<foreach collection='skuIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " AND stat_date BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY sku_id, warehouse_id" +
            "</script>")
    List<java.util.Map<String, Object>> selectAggregatedBySkuIds(
            @Param("skuIds") List<Long> skuIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
