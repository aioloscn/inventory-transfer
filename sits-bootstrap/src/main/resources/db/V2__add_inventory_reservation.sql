-- ============================================================
-- V2: 库存预占记录表
-- 审批通过后锁定库存的预占凭证，保证幂等
-- ============================================================

CREATE TABLE inventory_reservation (
    id                  BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    reservation_no      VARCHAR(64)     NOT NULL                   COMMENT '预占编号',
    transfer_order_id   BIGINT          NOT NULL                   COMMENT '调拨单ID',
    sku_id              BIGINT          NOT NULL                   COMMENT 'SKU ID',
    warehouse_id        BIGINT          NOT NULL                   COMMENT '仓库ID',
    quantity            INT             NOT NULL                   COMMENT '预占数量',
    status              VARCHAR(32)     NOT NULL DEFAULT 'RESERVED' COMMENT '状态: RESERVED/WRITTEN_OFF/RELEASED',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_no (reservation_no),
    UNIQUE KEY uk_order_sku_wh (transfer_order_id, sku_id, warehouse_id),
    KEY idx_transfer_order_id (transfer_order_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存预占记录表';
