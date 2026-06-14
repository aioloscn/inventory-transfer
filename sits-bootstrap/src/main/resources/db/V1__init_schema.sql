-- ============================================================
-- SITS (Smart Inventory Transfer System) Database Schema
-- Database: sits_db
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS sits_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE sits_db;

-- ============================================================
-- 1. warehouse — 仓库表
-- ============================================================
CREATE TABLE warehouse (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    warehouse_code  VARCHAR(64)     NOT NULL                       COMMENT '仓库编码',
    warehouse_name  VARCHAR(128)    NOT NULL                       COMMENT '仓库名称',
    region          VARCHAR(64)     DEFAULT NULL                   COMMENT '大区',
    province        VARCHAR(64)     DEFAULT NULL                   COMMENT '省份',
    city            VARCHAR(64)     DEFAULT NULL                   COMMENT '城市',
    address         VARCHAR(255)    DEFAULT NULL                   COMMENT '详细地址',
    capacity        INT             NOT NULL DEFAULT 0             COMMENT '仓库容量',
    priority        INT             NOT NULL DEFAULT 0             COMMENT '优先级（越大越高）',
    status          TINYINT         NOT NULL DEFAULT 1             COMMENT '状态: 1=正常, 0=禁用',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_code (warehouse_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库表';


-- ============================================================
-- 2. sku — 商品表
-- ============================================================
CREATE TABLE sku (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    sku_code        VARCHAR(64)     NOT NULL                       COMMENT 'SKU编码',
    sku_name        VARCHAR(128)    NOT NULL                       COMMENT 'SKU名称',
    category_id     BIGINT          DEFAULT NULL                   COMMENT '分类ID',
    brand           VARCHAR(64)     DEFAULT NULL                   COMMENT '品牌',
    unit            VARCHAR(32)     DEFAULT NULL                   COMMENT '单位',
    cost_price      DECIMAL(10,2)   DEFAULT NULL                   COMMENT '成本价',
    sale_price      DECIMAL(10,2)   DEFAULT NULL                   COMMENT '销售价',
    weight          DECIMAL(10,2)   DEFAULT NULL                   COMMENT '重量',
    volume          DECIMAL(10,2)   DEFAULT NULL                   COMMENT '体积',
    status          TINYINT         NOT NULL DEFAULT 1             COMMENT '状态: 1=正常, 0=禁用',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    KEY idx_category_id (category_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU商品表';


-- ============================================================
-- 3. warehouse_inventory — 仓库库存表
-- ============================================================
CREATE TABLE warehouse_inventory (
    id                  BIGINT      NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    sku_id              BIGINT      NOT NULL                       COMMENT 'SKU ID',
    warehouse_id        BIGINT      NOT NULL                       COMMENT '仓库ID',
    available_stock     INT         NOT NULL DEFAULT 0             COMMENT '可用库存',
    locked_stock        INT         NOT NULL DEFAULT 0             COMMENT '锁定库存（调拨中）',
    in_transit_stock    INT         NOT NULL DEFAULT 0             COMMENT '调拨在途库存',
    safety_stock        INT         NOT NULL DEFAULT 0             COMMENT '安全库存',
    max_stock           INT         NOT NULL DEFAULT 0             COMMENT '最大库存',
    version             INT         NOT NULL DEFAULT 0             COMMENT '乐观锁版本号',
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_warehouse (sku_id, warehouse_id),
    KEY idx_warehouse_id (warehouse_id),
    KEY idx_sku_id (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库库存表';


-- ============================================================
-- 4. inventory_flow — 库存流水表
-- ============================================================
CREATE TABLE inventory_flow (
    id                  BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    flow_no             VARCHAR(64)     NOT NULL                   COMMENT '流水号',
    sku_id              BIGINT          NOT NULL                   COMMENT 'SKU ID',
    warehouse_id        BIGINT          NOT NULL                   COMMENT '仓库ID',
    biz_type            VARCHAR(64)     NOT NULL                   COMMENT '业务类型: PURCHASE/SALES/TRANSFER/STOCK_TAKE/COMPENSATION',
    biz_no              VARCHAR(64)     NOT NULL                   COMMENT '业务单号',
    change_type         VARCHAR(64)     NOT NULL                   COMMENT '变更类型: PURCHASE_IN/SALES_OUT/TRANSFER_LOCK/TRANSFER_UNLOCK/TRANSFER_OUTBOUND/TRANSFER_INBOUND/STOCK_ADJUST/COMPENSATION',
    before_available    INT             NOT NULL                   COMMENT '变更前可用库存',
    change_quantity     INT             NOT NULL                   COMMENT '变更数量（正=增加，负=减少）',
    after_available     INT             NOT NULL                   COMMENT '变更后可用库存',
    operator            VARCHAR(64)     DEFAULT NULL               COMMENT '操作人',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_no (flow_no),
    UNIQUE KEY uk_biz_change (biz_type, biz_no, change_type),
    KEY idx_biz_no (biz_no),
    KEY idx_sku_warehouse (sku_id, warehouse_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';


-- ============================================================
-- 5. sales_stat_daily — 销量日统计表
-- ============================================================
CREATE TABLE sales_stat_daily (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    sku_id          BIGINT          NOT NULL                       COMMENT 'SKU ID',
    warehouse_id    BIGINT          NOT NULL                       COMMENT '仓库ID',
    stat_date       DATE            NOT NULL                       COMMENT '统计日期',
    sales_quantity  INT             NOT NULL DEFAULT 0             COMMENT '销量',
    sales_amount    DECIMAL(12,2)   NOT NULL DEFAULT 0.00          COMMENT '销售金额',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_warehouse_date (sku_id, warehouse_id, stat_date),
    KEY idx_stat_date (stat_date),
    KEY idx_sku_warehouse (sku_id, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销量日统计表';


-- ============================================================
-- 6. inventory_risk — 库存风险表
-- ============================================================
CREATE TABLE inventory_risk (
    id                  BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    risk_no             VARCHAR(64)     NOT NULL                   COMMENT '风险编号',
    sku_id              BIGINT          NOT NULL                   COMMENT 'SKU ID',
    warehouse_id        BIGINT          NOT NULL                   COMMENT '仓库ID',
    risk_type           VARCHAR(32)     NOT NULL                   COMMENT '风险类型: SHORTAGE/OVERSTOCK',
    risk_level          VARCHAR(32)     NOT NULL                   COMMENT '风险等级: HIGH/MEDIUM/LOW',
    available_stock     INT             NOT NULL                   COMMENT '当前可用库存',
    avg_daily_sales     DECIMAL(10,2)   NOT NULL                   COMMENT '日均销量',
    support_days        DECIMAL(10,2)   NOT NULL                   COMMENT '可支撑天数',
    status              VARCHAR(32)     NOT NULL DEFAULT 'NEW'     COMMENT '状态: NEW/PROCESSING/RESOLVED/IGNORED',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_no (risk_no),
    KEY idx_sku_warehouse (sku_id, warehouse_id),
    KEY idx_status (status),
    KEY idx_risk_type_level (risk_type, risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存风险表';


-- ============================================================
-- 7. transfer_suggestion — 调拨建议表
-- ============================================================
CREATE TABLE transfer_suggestion (
    id                      BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    suggestion_no           VARCHAR(64)     NOT NULL                   COMMENT '建议编号',
    risk_id                 BIGINT          DEFAULT NULL               COMMENT '关联风险ID',
    sku_id                  BIGINT          NOT NULL                   COMMENT 'SKU ID',
    source_warehouse_id     BIGINT          NOT NULL                   COMMENT '调出仓库ID',
    target_warehouse_id     BIGINT          NOT NULL                   COMMENT '调入仓库ID',
    suggest_quantity        INT             NOT NULL                   COMMENT '建议调拨数量',
    estimated_cost          DECIMAL(10,2)   DEFAULT NULL               COMMENT '预估成本',
    score                   DECIMAL(10,2)   DEFAULT NULL               COMMENT '调拨评分',
    reason                  TEXT            DEFAULT NULL               COMMENT '建议原因',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'GENERATED' COMMENT '状态: GENERATED/CONFIRMED/REJECTED/EXPIRED/CONVERTED',
    expire_time             DATETIME        DEFAULT NULL               COMMENT '过期时间',
    create_time             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_suggestion_no (suggestion_no),
    KEY idx_sku_id (sku_id),
    KEY idx_status (status),
    KEY idx_source_target (source_warehouse_id, target_warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调拨建议表';


-- ============================================================
-- 8. transfer_order — 调拨单表
-- ============================================================
CREATE TABLE transfer_order (
    id                      BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    transfer_no             VARCHAR(64)     NOT NULL                   COMMENT '调拨单号',
    suggestion_id           BIGINT          DEFAULT NULL               COMMENT '关联建议ID',
    sku_id                  BIGINT          NOT NULL                   COMMENT 'SKU ID',
    source_warehouse_id     BIGINT          NOT NULL                   COMMENT '调出仓库ID',
    target_warehouse_id     BIGINT          NOT NULL                   COMMENT '调入仓库ID',
    transfer_quantity       INT             NOT NULL                   COMMENT '调拨数量',
    transfer_amount         DECIMAL(12,2)   DEFAULT NULL               COMMENT '调拨金额',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/STOCK_LOCKED/APPROVING/APPROVED/REJECTED/OUTBOUNDING/OUTBOUNDED/IN_TRANSIT/INBOUNDING/COMPLETED/CANCELLED/FAILED',
    applicant_id            BIGINT          DEFAULT NULL               COMMENT '申请人ID',
    approver_id             BIGINT          DEFAULT NULL               COMMENT '审批人ID',
    version                 INT             NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
    create_time             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_sku_id (sku_id),
    KEY idx_status (status),
    KEY idx_source_target (source_warehouse_id, target_warehouse_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调拨单表';


-- ============================================================
-- 9. transfer_order_log — 调拨单日志表
-- ============================================================
CREATE TABLE transfer_order_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    transfer_no     VARCHAR(64)     NOT NULL                       COMMENT '调拨单号',
    from_status     VARCHAR(32)     DEFAULT NULL                   COMMENT '变更前状态',
    to_status       VARCHAR(32)     DEFAULT NULL                   COMMENT '变更后状态',
    event           VARCHAR(64)     DEFAULT NULL                   COMMENT '触发事件',
    operator        VARCHAR(64)     DEFAULT NULL                   COMMENT '操作人',
    remark          VARCHAR(255)    DEFAULT NULL                   COMMENT '备注',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_transfer_no (transfer_no),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调拨单日志表';


-- ============================================================
-- 10. approval_record — 审批记录表
-- ============================================================
CREATE TABLE approval_record (
    id                  BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    biz_type            VARCHAR(64)     NOT NULL                   COMMENT '业务类型',
    biz_no              VARCHAR(64)     NOT NULL                   COMMENT '业务单号（调拨单号）',
    approver_id         BIGINT          NOT NULL                   COMMENT '审批人ID',
    approve_result      VARCHAR(32)     DEFAULT NULL               COMMENT '审批结果: APPROVED/REJECTED',
    approve_comment     VARCHAR(255)    DEFAULT NULL               COMMENT '审批意见',
    approve_time        DATETIME        DEFAULT NULL               COMMENT '审批时间',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_biz_no (biz_no),
    KEY idx_approver_id (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表';


-- ============================================================
-- 11. compensation_task — 补偿任务表
-- ============================================================
CREATE TABLE compensation_task (
    id                  BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    task_no             VARCHAR(64)     NOT NULL                   COMMENT '任务编号',
    biz_type            VARCHAR(64)     NOT NULL                   COMMENT '业务类型',
    biz_no              VARCHAR(64)     NOT NULL                   COMMENT '业务单号',
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RETRYING/SUCCESS/FAILED/MANUAL',
    retry_count         INT             NOT NULL DEFAULT 0         COMMENT '已重试次数',
    max_retry_count     INT             NOT NULL DEFAULT 5         COMMENT '最大重试次数',
    next_retry_time     DATETIME        DEFAULT NULL               COMMENT '下次重试时间',
    error_message       TEXT            DEFAULT NULL               COMMENT '错误信息',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_status_next_time (status, next_retry_time),
    KEY idx_biz_no (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='补偿任务表';


-- ============================================================
-- 12. mq_consume_record — MQ消费记录表（幂等）
-- ============================================================
CREATE TABLE mq_consume_record (
    id                  BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    event_id            VARCHAR(128)    NOT NULL                   COMMENT '事件ID',
    consumer_group      VARCHAR(64)     NOT NULL                   COMMENT '消费者组',
    biz_no              VARCHAR(64)     NOT NULL                   COMMENT '业务单号',
    event_type          VARCHAR(64)     NOT NULL                   COMMENT '事件类型',
    status              VARCHAR(32)     NOT NULL DEFAULT 'CONSUMED' COMMENT '消费状态',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_consumer (event_id, consumer_group),
    KEY idx_biz_no (biz_no),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费记录表（幂等）';
