-- ============================================================
-- 13. rule_config — 规则配置表（系统级参数）
-- ============================================================
CREATE TABLE rule_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    config_key      VARCHAR(64)     NOT NULL                       COMMENT '配置键',
    config_value    VARCHAR(255)    NOT NULL                       COMMENT '配置值',
    description     VARCHAR(255)    DEFAULT NULL                   COMMENT '配置说明',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则配置表';

-- 初始化默认配置值
INSERT INTO rule_config (config_key, config_value, description) VALUES
('shortage_high_days',       '3',    '高风险缺货阈值：可支撑天数 <= 该值'),
('shortage_medium_days',     '7',    '中风险缺货阈值：可支撑天数 <= 该值'),
('overstock_days',           '30',   '积压阈值：可支撑天数 >= 该值'),
('overstock_ratio',          '0.8',  '积压库存比例：可用库存 > 最大库存 * 该比例'),
('avg_sales_lookback_days',  '30',   '日均销量计算回溯天数'),
('suggestion_expire_days',   '7',    '调拨建议过期天数'),
('cost_weight',              '0.4',  '调拨评分 - 成本权重'),
('time_weight',              '0.3',  '调拨评分 - 时效权重'),
('stock_health_weight',      '0.3',  '调拨评分 - 库存健康度权重');
