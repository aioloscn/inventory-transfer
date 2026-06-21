-- V6: 库存风险扫描优化
-- 1. 增加 scan_batch_no / latest_scan_time / risk_fingerprint 字段
-- 2. 增加业务唯一键，支持幂等写入
-- 3. 补充历史数据指纹

ALTER TABLE inventory_risk
    ADD COLUMN scan_batch_no      VARCHAR(64)   DEFAULT NULL                COMMENT '扫描批次号',
    ADD COLUMN latest_scan_time   DATETIME      DEFAULT NULL                COMMENT '最新扫描时间',
    ADD COLUMN risk_fingerprint   VARCHAR(128)  DEFAULT NULL                COMMENT '风险指纹(skuId_warehouseId_riskType)';

-- 补充历史数据的指纹
UPDATE inventory_risk
SET risk_fingerprint = CONCAT(sku_id, '_', warehouse_id, '_', risk_type)
WHERE risk_fingerprint IS NULL;

-- 业务唯一键：同一 SKU + 仓库 + 风险类型 + 指纹 唯一
ALTER TABLE inventory_risk
    ADD UNIQUE KEY uk_sku_wh_risk_fp (sku_id, warehouse_id, risk_type, risk_fingerprint);

-- 扫描批次号索引
ALTER TABLE inventory_risk
    ADD KEY idx_scan_batch_no (scan_batch_no);
