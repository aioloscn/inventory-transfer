package com.sits.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sits.risk.entity.InventoryRisk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryRiskMapper extends BaseMapper<InventoryRisk> {

    /**
     * 批量 upsert 风险记录。唯一键冲突时更新，保护 PROCESSING/IGNORED 状态。
     */
    int batchUpsert(@Param("list") List<InventoryRisk> risks);

    /**
     * 关闭本轮扫描未命中的 NEW 风险（非当前 scanBatchNo 的 NEW 风险 → RESOLVED）。
     */
    int closeResolvedRisks(@Param("currentScanBatchNo") String currentScanBatchNo);
}
