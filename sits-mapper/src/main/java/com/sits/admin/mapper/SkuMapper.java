package com.sits.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sits.admin.entity.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SKU Mapper.
 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /**
     * 游标分页查询 SKU ID 列表。
     */
    @Select("SELECT id FROM sku WHERE id > #{lastSkuId} ORDER BY id ASC LIMIT #{pageSize}")
    List<Long> selectSkuIdsByCursor(@Param("lastSkuId") long lastSkuId, @Param("pageSize") int pageSize);
}
